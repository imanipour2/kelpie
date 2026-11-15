package com.kelpie.browser.capture

import android.app.Activity
import android.app.KeyguardManager
import android.app.Presentation
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * App-owned display for an already authorized, same-instance WebView.
 *
 * This component never issues capture consent, opens Android activities, uses
 * MediaProjection/accessibility, or reads the remote caller. The caller must
 * validate the authenticated client and locally approved lease BEFORE capture.
 *
 * PRIVATE FEATURE CANDIDATE ONLY: not yet wired to the shipped browser UI.
 */
class PrivateWebViewDisplay(
    private val activity: Activity,
    private val composeRelease: () -> Unit,
    private val composeRestore: () -> Unit,
) {
    enum class State { IDLE, RELEASING_COMPOSE, PRIVATE_DISPLAY, RESTORE_PENDING, FAILED }

    private val stateFlow = MutableStateFlow(State.IDLE)
    val state = stateFlow.asStateFlow()

    private data class FrameWait(
        val expectedRgb: Int,
        val result: CompletableDeferred<Bitmap?>,
    )

    private val pendingImage = AtomicReference<FrameWait?>(null)
    private val callbackCount = AtomicInteger(0)
    private val matchingFrameCount = AtomicInteger(0)

    @Volatile private var lastFrameOutcome = "NOT_REQUESTED"

    /** Counts only; never expose screenshot pixels, URL, token or page content. */
    fun qaDiagnostics(): Map<String, Any> =
        mapOf(
            "imageCallbacks" to callbackCount.get(),
            "matchedNonceFrames" to matchingFrameCount.get(),
            "lastFrameOutcome" to lastFrameOutcome,
        )

    private val main = Handler(Looper.getMainLooper())
    private var view: WebView? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null

    // Only detach a view during cleanup after it entered our private host.
    // Before that, Compose may still own the original view (P3 mount RCA).
    private var attachedToPrivateHost = false
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var previousScrollX = 0
    private var previousScrollY = 0
    private var qaFrameSequence = 0

    @Volatile private var active = false

    @Volatile private var activityVisible = true

    /** Native lifecycle owns this flag; a remote client cannot change it. */
    fun setActivityVisible(visible: Boolean) {
        activityVisible = visible
    }

    /** Re-check hidden/locked/STOP state on Main immediately before frame delivery. */
    fun canDeliverFrame(): Boolean = active && !activityVisible && !isLocked() && stateFlow.value == State.PRIVATE_DISPLAY

    /**
     * Call on explicit local consent with the actual tab's WebView, after the
     * user has seen the client, selected tab, expiry and STOP disclosure.
     */
    suspend fun start(original: WebView): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (stateFlow.value != State.IDLE || isLocked() || !activityVisible) {
                return@withContext false
            }
            stateFlow.value = State.RELEASING_COMPOSE
            view = original
            previousScrollX = original.scrollX
            previousScrollY = original.scrollY
            qaFrameSequence = 0
            callbackCount.set(0)
            matchingFrameCount.set(0)
            lastFrameOutcome = "DISPLAY_INITIALIZING"
            val width = original.width.coerceIn(64, MAX_WIDTH)
            val height = original.height.coerceIn(64, MAX_HEIGHT)
            composeRelease()
            val detached =
                withTimeoutOrNull(RELEASE_TIMEOUT_MS) {
                    while (original.isAttachedToWindow) delay(80)
                    true
                } ?: false
            if (!detached || isLocked()) {
                releaseAndRestore(State.FAILED)
                return@withContext false
            }
            try {
                val backgroundThread = HandlerThread("Kelpie-Private-ImageReader")
                backgroundThread.start()
                worker = backgroundThread
                val images = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                reader = images
                images.setOnImageAvailableListener({ onImage(images) }, Handler(backgroundThread.looper))
                val manager = activity.getSystemService(DisplayManager::class.java)
                val virtual =
                    manager.createVirtualDisplay(
                        "KelpiePrivateWebView",
                        width,
                        height,
                        activity.resources.displayMetrics.densityDpi,
                        images.surface,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                    ) ?: error("Private display unavailable")
                display = virtual
                val host = Presentation(activity, virtual.display)
                presentation = host
                (original.parent as? ViewGroup)?.removeView(original)
                // Compose relinquished ownership. Cleanup must now restore it,
                // even if setContentView/show throws before the host is visible.
                attachedToPrivateHost = true
                host.setContentView(original)
                host.show()
                active = true
                stateFlow.value = State.PRIVATE_DISPLAY
                true
            } catch (_: RuntimeException) {
                releaseAndRestore(State.FAILED)
                false
            }
        }

    /**
     * Capture exactly one newly rendered frame while the browser is hidden.
     * No last-frame cache fallback: timeout, keyguard, foreground or STOP fails.
     * Caller owns an authenticated, valid and already-consumed frame lease.
     */
    suspend fun captureFreshPng(): Map<String, Any>? {
        val request =
            withContext(Dispatchers.Main.immediate) {
                if (!active || activityVisible || isLocked() || stateFlow.value != State.PRIVATE_DISPLAY) {
                    return@withContext null
                }
                val images = reader ?: return@withContext null
                val current = view ?: return@withContext null
                if (qaFrameSequence >= 20) return@withContext null
                // Reader timestamps and SystemClock can have different timebases.
                // A matching pixel nonce is proof of the NEW render, not a cached frame.
                runCatching { images.acquireLatestImage()?.close() }
                val expectedRgb = PrivateCaptureQaPixelProof.rgb(++qaFrameSequence)
                val wait = FrameWait(expectedRgb, CompletableDeferred())
                if (!pendingImage.compareAndSet(null, wait)) return@withContext null
                lastFrameOutcome = "WAITING_FOR_FRESH_NONCE"
                current.evaluateJavascript(PrivateCaptureQaPixelProof.repaintScript(expectedRgb)) { result ->
                    if (result != "true" && pendingImage.compareAndSet(wait, null)) {
                        lastFrameOutcome = "QA_SCRIPT_REFUSED"
                        wait.result.complete(null)
                    }
                }
                current.postInvalidateOnAnimation()
                wait
            } ?: return null

        val image = withTimeoutOrNull(FRAME_TIMEOUT_MS) { request.result.await() }
        pendingImage.compareAndSet(request, null)
        if (image == null) {
            if (lastFrameOutcome == "WAITING_FOR_FRESH_NONCE") lastFrameOutcome = "FRESH_FRAME_TIMEOUT"
            return null
        }
        lastFrameOutcome = "FRESH_NONCE_MATCHED"
        return try {
            if (!active || activityVisible || isLocked() || stateFlow.value != State.PRIVATE_DISPLAY) {
                null
            } else {
                val encoded =
                    withContext(Dispatchers.Default) {
                        val bytes = ByteArrayOutputStream()
                        if (!image.compress(Bitmap.CompressFormat.PNG, 100, bytes)) {
                            null
                        } else {
                            mapOf(
                                "image" to Base64.getEncoder().encodeToString(bytes.toByteArray()),
                                "width" to image.width,
                                "height" to image.height,
                                "format" to "png",
                                "coordinateSpace" to "viewport-css-pixels",
                            )
                        }
                    }
                if (active &&
                    !activityVisible &&
                    !isLocked() &&
                    stateFlow.value == State.PRIVATE_DISPLAY
                ) {
                    encoded
                } else {
                    null
                }
            }
        } finally {
            image.recycle()
        }
    }

    /** Always clean the capture image and surfaces, even if Compose is paused. */
    suspend fun stop() =
        withContext(Dispatchers.Main.immediate) {
            releaseAndRestore(State.RESTORE_PENDING)
        }

    /** Activity destruction must clean up without relying on a future coroutine. */
    fun dispose() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            releaseAndRestore(State.IDLE)
        } else {
            main.post { releaseAndRestore(State.IDLE) }
        }
    }

    /**
     * Native UI calls after it resumes: reattachment must finish before
     * returning PASS. No new WebView instance and no document reload.
     */
    suspend fun verifyRestored(): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (stateFlow.value != State.RESTORE_PENDING) return@withContext false
            val original = view ?: return@withContext false
            val attached =
                withTimeoutOrNull(RELEASE_TIMEOUT_MS) {
                    while (!original.isAttachedToWindow) delay(100)
                    true
                } ?: false
            if (!attached) {
                stateFlow.value = State.FAILED
                return@withContext false
            }
            original.scrollTo(previousScrollX, previousScrollY)
            delay(150)
            val matched =
                original.scrollX == previousScrollX &&
                    original.scrollY == previousScrollY
            stateFlow.value = if (matched) State.IDLE else State.FAILED
            if (matched) view = null
            matched
        }

    private fun releaseAndRestore(result: State) {
        check(Looper.myLooper() == Looper.getMainLooper())
        active = false
        pendingImage.getAndSet(null)?.result?.cancel()
        val original = view
        if (attachedToPrivateHost) {
            original?.evaluateJavascript(PrivateCaptureQaPixelProof.REMOVE_SCRIPT, null)
            (original?.parent as? ViewGroup)?.removeView(original)
            attachedToPrivateHost = false
        }
        presentation?.dismiss()
        presentation = null
        display?.release()
        display = null
        reader?.close()
        reader = null
        worker?.quitSafely()
        worker = null
        composeRestore()
        if (result == State.IDLE) view = null
        stateFlow.value = if (original == null) State.IDLE else result
    }

    private fun onImage(images: ImageReader) {
        val image = runCatching { images.acquireLatestImage() }.getOrNull() ?: return
        callbackCount.incrementAndGet()
        try {
            val waiting = pendingImage.get() ?: return
            if (!active || isLocked() || activityVisible || !waiting.result.isActive) return
            // Ignore every stale/blank/foreign frame. Only this request's
            // actual page-repaint pixel can complete the one-shot promise.
            val rgb = runCatching { sampleRgb(image) }.getOrNull() ?: return
            if (!PrivateCaptureQaPixelProof.matches(waiting.expectedRgb, rgb)) {
                lastFrameOutcome = "STALE_OR_WRONG_PIXEL"
                return
            }
            matchingFrameCount.incrementAndGet()
            if (!pendingImage.compareAndSet(waiting, null)) return
            val bitmap = runCatching { copyImage(image) }.getOrNull()
            if (!waiting.result.complete(bitmap)) bitmap?.recycle()
        } finally {
            image.close()
        }
    }

    private fun sampleRgb(image: Image): Int {
        require(image.width > 12 && image.height > 12)
        val plane = image.planes.single()
        require(plane.pixelStride == 4)
        val offset = 12 * plane.rowStride + 12 * plane.pixelStride
        val buffer = plane.buffer
        require(offset + 2 < buffer.limit())
        return ((buffer.get(offset).toInt() and 255) shl 16) or
            ((buffer.get(offset + 1).toInt() and 255) shl 8) or
            (buffer.get(offset + 2).toInt() and 255)
    }

    private fun copyImage(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val plane = image.planes.single()
        require(plane.pixelStride == 4)
        val paddedWidth = plane.rowStride / plane.pixelStride
        require(paddedWidth >= width)
        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        try {
            plane.buffer.rewind()
            padded.copyPixelsFromBuffer(plane.buffer)
            return Bitmap.createBitmap(padded, 0, 0, width, height)
        } finally {
            padded.recycle()
        }
    }

    private fun isLocked(): Boolean =
        activity.isFinishing ||
            activity.isDestroyed ||
            activity.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    companion object {
        const val RELEASE_TIMEOUT_MS = 2_000L
        const val FRAME_TIMEOUT_MS = 1_800L
        const val MAX_WIDTH = 1080
        const val MAX_HEIGHT = 1920
    }
}
