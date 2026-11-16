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
import android.os.PowerManager
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
    private val useNativeFrameProof: Boolean = false,
) {
    enum class State { IDLE, RELEASING_COMPOSE, PRIVATE_DISPLAY, RESTORE_PENDING, FAILED }

    private val stateFlow = MutableStateFlow(State.IDLE)
    val state = stateFlow.asStateFlow()

    private data class FrameWait(
        val expectedRgb: Int,
        val result: OneShotFrameBuffer<Bitmap>,
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
    private var nativeFrameProof: R5NativeFrameProof? = null

    // Only detach a view during cleanup after it entered our private host.
    // Before that, Compose may still own the original view (P3 mount RCA).
    private var attachedToPrivateHost = false
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var previousScrollX = 0
    private var previousScrollY = 0

    // WebView native scroll offsets may use a different scale from CSS pixels
    // after moving between the real Activity and a private virtual display.
    // RCA/rollback: docs/qa/EF-OPS-KELPIE-S24-P4-CSS-SCROLL-REMEDIATION-20261115.md
    private var previousCssScrollX = 0.0
    private var previousCssScrollY = 0.0
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
            // Record real document CSS scroll BEFORE releasing Compose ownership.
            // The native offsets alone did not preserve scroll across displays.
            val cssScroll = readCssScroll(original) ?: return@withContext false
            previousCssScrollX = cssScroll.first
            previousCssScrollY = cssScroll.second
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
                val proof = if (useNativeFrameProof) R5NativeFrameProof(activity).also { nativeFrameProof = it } else null
                host.setContentView(proof?.host(original) ?: original)
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
                if (qaFrameSequence >= if (useNativeFrameProof) R5NativeFrameProof.MAX_FRAMES else 20) {
                    return@withContext null
                }
                // Reader timestamps and SystemClock can have different timebases.
                // A matching pixel nonce is proof of the NEW render, not a cached frame.
                runCatching { images.acquireLatestImage()?.close() }
                val next = qaFrameSequence + 1
                val expectedRgb =
                    if (useNativeFrameProof) {
                        nativeFrameProof?.expectedRgb(next) ?: return@withContext null
                    } else {
                        PrivateCaptureQaPixelProof.rgb(next)
                    }
                val wait = FrameWait(expectedRgb, OneShotFrameBuffer(Bitmap::recycle))
                if (!pendingImage.compareAndSet(null, wait)) return@withContext null
                // The QA nonce must advance only for a newly accepted single flight.
                qaFrameSequence = next
                lastFrameOutcome = "WAITING_FOR_FRESH_NONCE"
                if (useNativeFrameProof) {
                    // The tiny nonce is painted by the app's Presentation, not
                    // injected into the visited website or another Android app.
                    nativeFrameProof?.repaint(expectedRgb)
                    current.postVisualStateCallback(
                        next.toLong(),
                        object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                if (pendingImage.get() === wait && wait.result.isOpen) {
                                    current.postInvalidateOnAnimation()
                                }
                            }
                        },
                    )
                } else {
                    current.evaluateJavascript(PrivateCaptureQaPixelProof.repaintScript(expectedRgb)) { result ->
                        if (result != "true" && pendingImage.compareAndSet(wait, null)) {
                            lastFrameOutcome = "QA_SCRIPT_REFUSED"
                            wait.result.fail()
                        }
                    }
                    current.postInvalidateOnAnimation()
                }
                wait
            } ?: return null

        // Closing the holder in finally also disposes pixels delivered by a
        // callback at the exact cancellation/timeout boundary.
        var image: Bitmap? = null
        return try {
            image = withTimeoutOrNull(FRAME_TIMEOUT_MS) { request.result.awaitTake() }
            val frame = image
            if (frame == null) {
                if (lastFrameOutcome == "WAITING_FOR_FRESH_NONCE") lastFrameOutcome = "FRESH_FRAME_TIMEOUT"
                return null
            }
            lastFrameOutcome = "FRESH_NONCE_MATCHED"
            if (!active || activityVisible || isLocked() || stateFlow.value != State.PRIVATE_DISPLAY) {
                null
            } else {
                val encoded =
                    withContext(Dispatchers.Default) {
                        val bytes = ByteArrayOutputStream()
                        if (!frame.compress(Bitmap.CompressFormat.PNG, 100, bytes)) {
                            null
                        } else {
                            mapOf(
                                "image" to Base64.getEncoder().encodeToString(bytes.toByteArray()),
                                "width" to frame.width,
                                "height" to frame.height,
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
            pendingImage.compareAndSet(request, null)
            request.result.close()
            image?.recycle()
        }
    }

    /** Always clean the capture image and surfaces, even if Compose is paused. */
    suspend fun stop() =
        withContext(Dispatchers.Main.immediate) {
            // Repeated STOP must not reparent the same WebView or replay
            // composeRestore() while an earlier restore is still settling.
            if (stateFlow.value != State.IDLE && stateFlow.value != State.RESTORE_PENDING) {
                releaseAndRestore(State.RESTORE_PENDING)
            }
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
            if (stateFlow.value != State.RESTORE_PENDING && stateFlow.value != State.FAILED) return@withContext false
            val original =
                view ?: run {
                    stateFlow.value = State.IDLE
                    return@withContext true
                }
            val attached =
                withTimeoutOrNull(RELEASE_TIMEOUT_MS) {
                    while (!original.isAttachedToWindow) delay(100)
                    true
                } ?: false
            if (!attached) {
                stateFlow.value = State.FAILED
                return@withContext false
            }
            // WebView can apply a late compositor/layout scroll restore after the
            // initial Compose reattach. Never mark restored on one transient read.
            // Instead, reassert CSS pixels until three spaced observations agree,
            // followed by a delayed stability check. This is same-document only.
            delay(650)
            var stableObservations = 0
            var matched = false
            for (attempt in 0..11) {
                if (!original.isAttachedToWindow || isLocked()) break
                original.evaluateJavascript(
                    "window.scrollTo($previousCssScrollX,$previousCssScrollY)",
                    null,
                )
                delay(250)
                val current = readCssScroll(original)
                val equal =
                    current != null &&
                        kotlin.math.abs(current.first - previousCssScrollX) <= 1.5 &&
                        kotlin.math.abs(current.second - previousCssScrollY) <= 1.5
                stableObservations = if (equal) stableObservations + 1 else 0
                if (stableObservations < 3) continue
                // A scroll that is correct immediately may still reset on the
                // next layout; wait beyond a typical UI resize/settling cycle.
                delay(700)
                val settled = readCssScroll(original)
                if (settled != null &&
                    kotlin.math.abs(settled.first - previousCssScrollX) <= 1.5 &&
                    kotlin.math.abs(settled.second - previousCssScrollY) <= 1.5
                ) {
                    matched = true
                    break
                }
                stableObservations = 0
            }
            stateFlow.value = if (matched) State.IDLE else State.FAILED
            if (matched) view = null
            matched
        }

    /** Only document-local CSS values are used, never cross-display native pixels. */
    private suspend fun readCssScroll(original: WebView): Pair<Double, Double>? {
        suspend fun coordinate(expression: String): Double? {
            val reply = CompletableDeferred<String>()
            original.evaluateJavascript(expression) { raw -> reply.complete(raw) }
            val value = withTimeoutOrNull(800L) { reply.await() }?.toDoubleOrNull()
            return value?.takeIf { it.isFinite() && it >= 0.0 }
        }
        val x = coordinate("window.scrollX") ?: return null
        val y = coordinate("window.scrollY") ?: return null
        return x to y
    }

    private fun releaseAndRestore(result: State) {
        check(Looper.myLooper() == Looper.getMainLooper())
        active = false
        pendingImage.getAndSet(null)?.result?.close()
        val original = view
        val cleanup = PrivateCaptureCleanupSequence()

        // Failure of Presentation.dismiss() must never skip releasing the
        // VirtualDisplay, ImageReader, worker or Compose ownership.
        if (attachedToPrivateHost) {
            if (!useNativeFrameProof) {
                cleanup.release { original?.evaluateJavascript(PrivateCaptureQaPixelProof.REMOVE_SCRIPT, null) }
            }
            cleanup.release { (original?.parent as? ViewGroup)?.removeView(original) }
            attachedToPrivateHost = false
        }
        val oldProof = nativeFrameProof
        nativeFrameProof = null
        cleanup.release { oldProof?.dispose() }
        val oldPresentation = presentation
        presentation = null
        cleanup.release { oldPresentation?.dismiss() }
        val oldDisplay = display
        display = null
        cleanup.release { oldDisplay?.release() }
        val oldReader = reader
        reader = null
        cleanup.release { oldReader?.setOnImageAvailableListener(null, null) }
        cleanup.release { oldReader?.close() }
        val oldWorker = worker
        worker = null
        cleanup.release { oldWorker?.quitSafely() }
        cleanup.release { composeRestore() }
        if (result == State.IDLE && !cleanup.failed) view = null
        stateFlow.value =
            when {
                cleanup.failed -> State.FAILED
                original == null || result == State.IDLE -> State.IDLE
                else -> result
            }
    }

    private fun onImage(images: ImageReader) {
        val image = runCatching { images.acquireLatestImage() }.getOrNull() ?: return
        callbackCount.incrementAndGet()
        try {
            val waiting = pendingImage.get() ?: return
            if (!active || isLocked() || activityVisible || !waiting.result.isOpen) return
            // Ignore every stale/blank/foreign frame. Only this request's
            // actual page-repaint pixel can complete the one-shot promise.
            val rgb = runCatching { sampleRgb(image) }.getOrNull() ?: return
            if (!PrivateCaptureQaPixelProof.matches(waiting.expectedRgb, rgb)) {
                lastFrameOutcome = "STALE_OR_WRONG_PIXEL"
                return
            }
            matchingFrameCount.incrementAndGet()
            // Keep the request published until the awaiting coroutine takes it,
            // or STOP closes its holder. Removing it here creates a leak window:
            // native shutdown could no longer reach an in-flight bitmap.
            if (pendingImage.get() !== waiting || !waiting.result.isOpen) return
            val bitmap = runCatching { copyImage(image) }.getOrNull()
            if (bitmap != null) {
                waiting.result.publish(bitmap)
            } else {
                waiting.result.fail()
            }
        } finally {
            image.close()
        }
    }

    private fun sampleRgb(image: Image): Int {
        require(image.width > 12 && image.height > 12)
        val plane = image.planes.single()
        require(plane.pixelStride == 4)
        val x = if (useNativeFrameProof) R5NativeFrameProof.SAMPLE_PX else image.width / 2
        val y = if (useNativeFrameProof) R5NativeFrameProof.SAMPLE_PX else image.height / 2
        val offset = y * plane.rowStride + x * plane.pixelStride
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
            !activity.getSystemService(PowerManager::class.java).isInteractive ||
            activity.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    companion object {
        const val RELEASE_TIMEOUT_MS = 2_000L
        const val FRAME_TIMEOUT_MS = 1_800L
        const val MAX_WIDTH = 1080
        const val MAX_HEIGHT = 1920
    }
}
