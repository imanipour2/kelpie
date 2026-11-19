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
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import com.kelpie.browser.agent.contract.R5VisionCaptureRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        val expectedSecondaryRgb: Int?,
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
    private var visualRequestId = 0L

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
            if (stateFlow.value != State.IDLE || isLocked() || !activityVisible ||
                original.width <= 0 || original.height <= 0 || !original.isAttachedToWindow ||
                !readCssViewportReady(original)
            ) {
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
            visualRequestId = 0L
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
     * Rehost a different Kelpie tab on the EXISTING private virtual display.
     * Never start an Activity, capture the Android screen, or adopt a foreign View.
     * The old tab remains alive but detached; no page state is copied across tabs.
     */
    suspend fun switchPrivateWebView(next: WebView): Boolean =
        withContext(Dispatchers.Main.immediate) {
            val old = view ?: return@withContext false
            if (stateFlow.value != State.PRIVATE_DISPLAY ||
                !active ||
                activityVisible ||
                isLocked() ||
                next === old ||
                next.parent != null
            ) {
                return@withContext false
            }
            val proof = nativeFrameProof ?: return@withContext false
            active = false
            pendingImage.getAndSet(null)?.result?.close()
            val swapped = proof.swapWebView(old, next)
            if (!swapped) {
                active = old.isAttachedToWindow && !isLocked()
                return@withContext false
            }
            // Being attached is not sufficient: the Alibaba S24 failure showed DOM access
            // and a logical selected tab while the physical/CSS viewport remained 0x0.
            val attached = waitForRenderableViewport(next)
            if (!attached || isLocked()) {
                proof.swapWebView(next, old)
                active = old.isAttachedToWindow && !isLocked()
                return@withContext false
            }
            view = next
            previousScrollX = next.scrollX
            previousScrollY = next.scrollY
            val css = withTimeoutOrNull(1_200L) { readCssScroll(next) }
            previousCssScrollX = css?.first ?: 0.0
            previousCssScrollY = css?.second ?: 0.0
            active = !isLocked()
            active
        }

    /**
     * Capture exactly one newly rendered frame while the browser is hidden.
     * No last-frame cache fallback: timeout, keyguard, foreground or STOP fails.
     * Caller owns an authenticated, valid and already-consumed frame lease.
     */
    suspend fun captureFreshPng(capture: R5VisionCaptureRequest = R5VisionCaptureRequest.LEGACY): Map<String, Any>? {
        val request =
            withContext(Dispatchers.Main.immediate) {
                if (!active || activityVisible || isLocked() || stateFlow.value != State.PRIVATE_DISPLAY) {
                    return@withContext null
                }
                val images = reader ?: return@withContext null
                val current = view ?: return@withContext null
                if (!useNativeFrameProof && qaFrameSequence >= 20) {
                    return@withContext null
                }
                // Only one pending frame is allowed, including during epoch rollover.
                if (pendingImage.get() != null) return@withContext null
                // Reader timestamps and SystemClock can have different timebases.
                // A matching private native pixel PAIR proves a new compositor
                // frame; a new seed pair is generated every 512 requests without
                // reparenting the real WebView or renewing local consent.
                runCatching { images.acquireLatestImage()?.close() }
                val nativeToken =
                    if (useNativeFrameProof) {
                        runCatching { nativeFrameProof?.nextProof() }.getOrNull() ?: return@withContext null
                    } else {
                        null
                    }
                val next = nativeToken?.sequence ?: qaFrameSequence + 1
                val expectedRgb = nativeToken?.primaryRgb ?: PrivateCaptureQaPixelProof.rgb(next)
                val wait =
                    FrameWait(
                        expectedRgb = expectedRgb,
                        expectedSecondaryRgb = nativeToken?.secondaryRgb,
                        result = OneShotFrameBuffer(Bitmap::recycle),
                    )
                if (!pendingImage.compareAndSet(null, wait)) return@withContext null
                qaFrameSequence = next
                visualRequestId += 1
                lastFrameOutcome = "WAITING_FOR_FRESH_NONCE"
                if (useNativeFrameProof) {
                    // Both markers are painted by our Presentation, never by
                    // JavaScript and never on any other Android application's UI.
                    nativeFrameProof?.repaint(nativeToken ?: return@withContext null)
                    current.postVisualStateCallback(
                        visualRequestId,
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
                        R5VerifiedFrameEncoder(activity).encode(frame, capture)
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
                // In R5 the agent can navigate/scroll after moving to PRIVATE_DISPLAY.
                // The entry snapshot is then stale: restore the latest owned document CSS
                // scroll rather than rewinding its browser actions. P4 is unchanged.
                if (useNativeFrameProof && stateFlow.value == State.PRIVATE_DISPLAY && !isLocked()) {
                    val observed =
                        view?.takeIf { it.isAttachedToWindow }?.let {
                            withTimeoutOrNull(2_500L) { readCssScroll(it) }
                        }
                    val selected = R5CssRestoreTarget.choose(previousCssScrollX, previousCssScrollY, observed)
                    previousCssScrollX = selected.first
                    previousCssScrollY = selected.second
                }
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
                    noteRestore("NO_VIEW_RESTORED")
                    return@withContext true
                }
            val attached =
                withTimeoutOrNull(if (useNativeFrameProof) 5_000L else RELEASE_TIMEOUT_MS) {
                    while (!original.isAttachedToWindow) delay(100)
                    true
                } ?: false
            if (!attached) {
                stateFlow.value = State.FAILED
                noteRestore("WEBVIEW_ATTACH_TIMEOUT")
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
            noteRestore(if (matched) "CSS_RESTORED" else "CSS_STABILITY_FAILED")
            if (matched) view = null
            matched
        }

    /** QA-only lifecycle category; never log URLs, pixels, tokens or user inputs. */
    private fun noteRestore(code: String) {
        if (useNativeFrameProof) Log.i("KelpieR5Restore", code)
    }

    /**
     * A private tab must have native + CSS dimensions and a committed visual state
     * before it can become selected. No screenshots/URLs or credentials are logged.
     */
    private suspend fun waitForRenderableViewport(next: WebView): Boolean {
        val laidOut =
            withTimeoutOrNull(3_000L) {
                while (!isLocked()) {
                    if (next.isAttachedToWindow && next.width > 0 && next.height > 0 &&
                        readCssViewportReady(next)
                    ) {
                        return@withTimeoutOrNull true
                    }
                    delay(80)
                }
                false
            } ?: false
        if (!laidOut || isLocked()) return false
        val committed = CompletableDeferred<Boolean>()
        next.postVisualStateCallback(
            ++visualRequestId,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (!committed.isCompleted) committed.complete(true)
                }
            },
        )
        val visualReady = withTimeoutOrNull(2_000L) { committed.await() } == true
        return visualReady && !isLocked() && next.isAttachedToWindow &&
            next.width > 0 && next.height > 0 && readCssViewportReady(next)
    }

    /** Boolean-only page evidence: never export or persist DOM values. */
    private suspend fun readCssViewportReady(webView: WebView): Boolean {
        val result = CompletableDeferred<String>()
        webView.evaluateJavascript(
            "window.innerWidth > 0 && window.innerHeight > 0 && " +
                "document.documentElement.getBoundingClientRect().width > 0",
        ) { raw ->
            if (!result.isCompleted) result.complete(raw)
        }
        return withTimeoutOrNull(500L) { result.await() } == "true"
    }

    /** Only document-local CSS values are used, never cross-display native pixels. */
    private suspend fun readCssScroll(original: WebView): Pair<Double, Double>? {
        suspend fun coordinate(expression: String): Double? {
            val reply = CompletableDeferred<String>()
            original.evaluateJavascript(expression) { raw -> reply.complete(raw) }
            val value = withTimeoutOrNull(800L) { reply.await() }?.toDoubleOrNull()
            // Horizontal CSS scroll can be negative for an RTL root in Chromium.
            // Validate finiteness here; only vertical scroll must be nonnegative.
            return value?.takeIf { it.isFinite() }
        }
        val x = coordinate("window.scrollX") ?: return null
        val y = coordinate("window.scrollY")?.takeIf { it >= 0.0 } ?: return null
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
            if (useNativeFrameProof) {
                val expectedSecondary = waiting.expectedSecondaryRgb ?: return
                val actualSecondary =
                    runCatching { sampleRgb(image, R5NativeFrameProof.SECOND_SAMPLE_PX) }
                        .getOrNull() ?: return
                if (!R5FrameNonce.matches(expectedSecondary, actualSecondary)) {
                    lastFrameOutcome = "STALE_OR_WRONG_SECOND_PIXEL"
                    return
                }
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

    private fun sampleRgb(
        image: Image,
        nativeSampleY: Int = R5NativeFrameProof.SAMPLE_PX,
    ): Int {
        require(image.width > 12 && image.height > nativeSampleY)
        val plane = image.planes.single()
        require(plane.pixelStride == 4)
        val x = if (useNativeFrameProof) R5NativeFrameProof.SAMPLE_PX else image.width / 2
        val y = if (useNativeFrameProof) nativeSampleY else image.height / 2
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
