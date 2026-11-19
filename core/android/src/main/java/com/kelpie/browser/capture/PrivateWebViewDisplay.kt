package com.kelpie.browser.capture

import android.app.Activity
import android.app.KeyguardManager
import android.app.Presentation
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import com.kelpie.browser.agent.contract.R5VisionCaptureRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * One app-owned display for the already authorized, same-instance WebView.
 * The caller owns principal/consent/tab/document fences. This class never starts
 * an Activity, issues consent, copies website sessions or captures another app.
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
    private val ownershipGeneration = AtomicLong(0L)
    private val frames = R5PrivateFrameAcquisition { generation -> ownershipGeneration.get() == generation && canDeliverFrame() }
    private val main = Handler(Looper.getMainLooper())
    private val viewportReadiness = R5PrivateViewportReadiness { isLocked() }
    private var view: WebView? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var nativeFrameProof: R5NativeFrameProof? = null

    @Volatile private var pageFrameProof: Pair<WebView, R5PageFrameProof>? = null
    private var attachedToPrivateHost = false
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var previousCssScroll = 0.0 to 0.0
    private var qaFrameSequence = 0
    private var visualRequestId = 0L

    @Volatile private var active = false

    @Volatile private var activityVisible = true

    /** Counts/categories only; never expose pixels, tokens or private page data. */
    fun qaDiagnostics(): Map<String, Any> = frames.diagnostics()

    fun setActivityVisible(visible: Boolean) {
        activityVisible = visible
        if (visible) cancelPendingCapture()
    }

    fun canDeliverFrame(): Boolean = active && !activityVisible && !isLocked() && stateFlow.value == State.PRIVATE_DISPLAY

    /** Native main-thread identity check used by tab/action ingress and egress. */
    fun isOwnedBy(expected: WebView?): Boolean =
        expected != null &&
            view === expected &&
            canDeliverFrame() &&
            expected.isAttachedToWindow &&
            expected.width > 0 &&
            expected.height > 0 &&
            (nativeFrameProof?.owns(expected) ?: (expected.parent != null))

    /** Immediate fence: STOP/transfer cannot wait for a JavaScript or image callback. */
    fun cancelPendingCapture() {
        ownershipGeneration.incrementAndGet()
        frames.cancel()
        val cancelledProof = pageFrameProof ?: return

        fun removeProof() {
            if (pageFrameProof !== cancelledProof) return
            runCatching { cancelledProof.second.removeNow(cancelledProof.first) }
            if (pageFrameProof === cancelledProof) pageFrameProof = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) removeProof() else main.post { removeProof() }
    }

    suspend fun start(original: WebView): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (stateFlow.value != State.IDLE || isLocked() || !activityVisible || !viewportReadiness.cssReady(original)) {
                Log.w("KelpieR5Transfer", "START_PRECONDITION_DENIED")
                return@withContext false
            }
            val cssScroll = R5WebViewScrollRestoration.read(original) ?: run {
                Log.w("KelpieR5Transfer", "CSS_SCROLL_WITNESS_MISSING")
                return@withContext false
            }
            if (stateFlow.value != State.IDLE || isLocked() || !activityVisible) return@withContext false
            val generation = ownershipGeneration.incrementAndGet()
            previousCssScroll = cssScroll
            stateFlow.value = State.RELEASING_COMPOSE
            view = original
            qaFrameSequence = 0
            frames.reset()
            val width = original.width.coerceIn(64, MAX_WIDTH)
            val height = original.height.coerceIn(64, MAX_HEIGHT)

            fun current(): Boolean = ownershipGeneration.get() == generation && view === original && !isLocked()
            try {
                composeRelease()
                val detached =
                    withTimeoutOrNull(RELEASE_TIMEOUT_MS) {
                        while (original.isAttachedToWindow && current()) delay(80)
                        !original.isAttachedToWindow && current()
                    } == true
                if (!detached) {
                    Log.w("KelpieR5Transfer", "COMPOSE_RELEASE_TIMEOUT")
                    if (ownershipGeneration.get() == generation) releaseAndRestore(State.FAILED)
                    return@withContext false
                }
                val backgroundThread = HandlerThread("Kelpie-Private-ImageReader")
                backgroundThread.start()
                worker = backgroundThread
                val images = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                reader = images
                images.setOnImageAvailableListener({ frames.onImage(images) }, Handler(backgroundThread.looper))
                val manager = activity.getSystemService(DisplayManager::class.java)
                val virtual =
                    manager.createVirtualDisplay(
                        "KelpiePrivateWebView",
                        width,
                        height,
                        activity.resources.displayMetrics.densityDpi,
                        images.surface,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                    ) ?: error("Private display unavailable")
                display = virtual
                val host = Presentation(activity, virtual.display)
                presentation = host
                (original.parent as? ViewGroup)?.removeView(original)
                attachedToPrivateHost = true
                val proof = if (useNativeFrameProof) R5NativeFrameProof(host.context).also { nativeFrameProof = it } else null
                host.setContentView(proof?.host(original) ?: original)
                host.show()
                if (!viewportReadiness.awaitCommittedViewport(original, ++visualRequestId, ::current) || !current()) {
                    Log.w("KelpieR5Transfer", "PRIVATE_VIEWPORT_COMMIT_FAILED")
                    if (ownershipGeneration.get() == generation) releaseAndRestore(State.FAILED)
                    return@withContext false
                }
                active = true
                stateFlow.value = State.PRIVATE_DISPLAY
                true
            } catch (error: CancellationException) {
                if (ownershipGeneration.get() == generation) releaseAndRestore(State.FAILED)
                throw error
            } catch (_: RuntimeException) {
                Log.w("KelpieR5Transfer", "PRIVATE_DISPLAY_RUNTIME_FAILURE")
                if (ownershipGeneration.get() == generation) releaseAndRestore(State.FAILED)
                false
            }
        }

    /** The logical active tab is committed by the caller only after this physical transfer succeeds. */
    suspend fun switchPrivateWebView(next: WebView): Boolean =
        withContext(Dispatchers.Main.immediate) {
            val old = view ?: return@withContext false
            if (!isOwnedBy(old) || next === old || next.parent != null) return@withContext false
            val proof = nativeFrameProof ?: return@withContext false
            active = false
            cancelPendingCapture()
            val generation = ownershipGeneration.get()

            fun current(): Boolean =
                ownershipGeneration.get() == generation &&
                    nativeFrameProof === proof &&
                    stateFlow.value == State.PRIVATE_DISPLAY &&
                    !isLocked() &&
                    !activityVisible

            suspend fun rollback() {
                if (ownershipGeneration.get() != generation || nativeFrameProof !== proof) return
                if (!current()) {
                    releaseAndRestore(State.FAILED)
                    return
                }
                val swappedBack = proof.owns(old) || proof.swapWebView(next, old)
                active = swappedBack && viewportReadiness.awaitCommittedViewport(old, ++visualRequestId, ::current) && current()
                if (!active && current()) releaseAndRestore(State.FAILED)
            }
            try {
                if (!proof.swapWebView(old, next)) {
                    rollback()
                    return@withContext false
                }
                val ready = viewportReadiness.awaitCommittedViewport(next, ++visualRequestId, ::current)
                val css = if (ready && current()) R5WebViewScrollRestoration.read(next) else null
                if (!ready || css == null || !current() || !proof.owns(next)) {
                    rollback()
                    return@withContext false
                }
                view = next
                previousCssScroll = css
                active = true
                true
            } catch (error: CancellationException) {
                withContext(NonCancellable) { rollback() }
                throw error
            } catch (_: RuntimeException) {
                rollback()
                false
            }
        }

    /** Native host AND document paint proof, followed by independent privacy/owner checks. */
    suspend fun captureFreshPng(capture: R5VisionCaptureRequest = R5VisionCaptureRequest.LEGACY): Map<String, Any>? =
        withContext(Dispatchers.Main.immediate) {
            val current = view ?: return@withContext null
            val images = reader ?: return@withContext null
            if (!isOwnedBy(current) || frames.hasPending || (!useNativeFrameProof && qaFrameSequence >= 20)) return@withContext null
            val generation = ownershipGeneration.get()

            fun currentOwner(): Boolean = ownershipGeneration.get() == generation && isOwnedBy(current) && reader === images
            val privacy = WebViewCapturePrivacy.inspect(current)
            if (!currentOwner()) return@withContext null
            val token = if (useNativeFrameProof) nativeFrameProof?.nextProof() ?: return@withContext null else null
            val next = token?.sequence ?: qaFrameSequence + 1
            val request =
                R5PrivateFrameAcquisition.Request(
                    images,
                    generation,
                    images.width,
                    images.height,
                    token?.primaryRgb ?: PrivateCaptureQaPixelProof.rgb(next),
                    token,
                    null,
                )
            if (!frames.arm(request)) return@withContext null
            var frame: R5PrivateFrameAcquisition.Frame? = null
            val pageProof = token?.let { R5PageFrameProof(it) }
            try {
                qaFrameSequence = next
                if (pageProof != null) {
                    pageFrameProof = current to pageProof
                    request.page = pageProof.install(current, images.width, images.height) ?: return@withContext null
                } else {
                    current.evaluateJavascript(PrivateCaptureQaPixelProof.repaintScript(request.primaryRgb)) { result ->
                        if (result != "true") frames.reject(request, "QA_SCRIPT_REFUSED")
                    }
                }
                if (!currentOwner()) return@withContext null
                val visualId = ++visualRequestId
                current.postVisualStateCallback(
                    visualId,
                    object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            if (requestId == visualId && frames.isCurrent(request) && currentOwner()) {
                                request.visualReady = true
                                if (token != null) nativeFrameProof?.repaint(token)
                                current.postInvalidateOnAnimation()
                            }
                        }
                    },
                )
                // Timeout is asynchronous: retain ownership inside its body so
                // a timeout after take() cannot discard an unowned Bitmap.
                val acquired =
                    withTimeoutOrNull(FRAME_TIMEOUT_MS) {
                        frame = request.result.awaitTake()
                        true
                    } == true
                if (!acquired) return@withContext null
                val received = frame ?: return@withContext null
                if (!currentOwner()) return@withContext null
                if (pageProof != null && !pageProof.remove(current)) return@withContext null
                privacy.requireSameDocument(WebViewCapturePrivacy.inspect(current))
                if (!currentOwner()) return@withContext null
                val encoded = withContext(Dispatchers.Default) { R5VerifiedFrameEncoder(activity).encode(received.bitmap, capture, privacy) }
                if (encoded == null || !currentOwner()) return@withContext null
                privacy.requireSameDocument(WebViewCapturePrivacy.inspect(current))
                if (!currentOwner()) return@withContext null
                encoded +
                    mapOf(
                        "frameTimestampNanos" to received.acquiredAtNanos,
                        "producerTimestampNanos" to received.producerTimestampNanos,
                        "imageCallbackSequence" to received.sequence,
                        "frameEpoch" to (token?.epoch ?: 0L),
                        "frameSequence" to next,
                        "frameOwnerGeneration" to generation,
                        "frameNonce" to (token?.let { "%06x%06x".format(it.primaryRgb, it.secondaryRgb) } ?: request.primaryRgb.toString(16)),
                        "proofRegionsRedacted" to (token != null),
                        "freshnessProof" to if (token != null) "NATIVE_AND_DOCUMENT_PIXEL_PAIR" else "PUBLIC_QA_PIXEL",
                    )
            } finally {
                frames.finish(request)
                frame?.bitmap?.recycle()
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (pageProof != null && pageFrameProof?.second === pageProof) {
                        runCatching { pageProof.removeNow(current) }
                        pageFrameProof = null
                    }
                }
            }
        }

    /** STOP invalidates capture and transfer before any suspending scroll read. */
    suspend fun stop() =
        withContext(Dispatchers.Main.immediate) {
            active = false
            cancelPendingCapture()
            if (stateFlow.value != State.IDLE && stateFlow.value != State.RESTORE_PENDING) {
                val wasPrivate = stateFlow.value == State.PRIVATE_DISPLAY
                stateFlow.value = State.RESTORE_PENDING
                try {
                    if (useNativeFrameProof && wasPrivate && !isLocked()) {
                        val observed =
                            view?.takeIf { it.isAttachedToWindow }?.let {
                                withTimeoutOrNull(2_500L) { R5WebViewScrollRestoration.read(it) }
                            }
                        previousCssScroll = R5CssRestoreTarget.choose(previousCssScroll.first, previousCssScroll.second, observed)
                    }
                } finally {
                    releaseAndRestore(State.RESTORE_PENDING)
                }
            }
        }

    fun dispose() {
        active = false
        cancelPendingCapture()
        if (Looper.myLooper() == Looper.getMainLooper()) releaseAndRestore(State.IDLE) else main.post { releaseAndRestore(State.IDLE) }
    }

    /** Call on Main BEFORE destroying a renderer-dead WebView; never evaluate it again. */
    fun onRendererGone(deadView: WebView) {
        check(Looper.myLooper() == Looper.getMainLooper())
        // Peer tabs can share the renderer process. Revoke the private capability
        // on any death, even before the owned tab's own callback is delivered.
        active = false
        // A shared renderer can have killed more than the callback's one WebView.
        // Removing DOM witnesses would itself call into that unusable renderer.
        pageFrameProof = null
        cancelPendingCapture()
        releaseAndRestore(State.FAILED, deadView)
    }

    suspend fun verifyRestored(): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (stateFlow.value != State.RESTORE_PENDING && stateFlow.value != State.FAILED) return@withContext false
            val original =
                view ?: run {
                    stateFlow.value = State.IDLE
                    noteRestore("NO_VIEW_RESTORED")
                    return@withContext true
                }
            val generation = ownershipGeneration.get()
            val attached =
                withTimeoutOrNull(if (useNativeFrameProof) 5_000L else RELEASE_TIMEOUT_MS) {
                    while (!original.isAttachedToWindow && ownershipGeneration.get() == generation) delay(100)
                    original.isAttachedToWindow
                } == true
            val matched =
                attached &&
                    R5WebViewScrollRestoration.restore(original, previousCssScroll) {
                        ownershipGeneration.get() == generation && view === original && !isLocked()
                    }
            if (ownershipGeneration.get() != generation) return@withContext false
            stateFlow.value = if (matched) State.IDLE else State.FAILED
            noteRestore(if (matched) "CSS_RESTORED" else "CSS_STABILITY_FAILED")
            if (matched) view = null
            matched
        }

    private fun noteRestore(code: String) {
        if (useNativeFrameProof) Log.i("KelpieR5Restore", code)
    }

    private fun releaseAndRestore(
        result: State,
        deadView: WebView? = null,
    ) {
        check(Looper.myLooper() == Looper.getMainLooper())
        active = false
        cancelPendingCapture()
        val original = view
        val cleanup = PrivateCaptureCleanupSequence()
        val oldPageProof = pageFrameProof
        pageFrameProof = null
        if (deadView == null) cleanup.release { oldPageProof?.let { (owner, proof) -> proof.removeNow(owner) } }
        if (attachedToPrivateHost) {
            if (!useNativeFrameProof && deadView == null) {
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
        cleanup.release { oldReader?.let { frames.closeReader(it) } }
        val oldWorker = worker
        worker = null
        cleanup.release { oldWorker?.quitSafely() }
        cleanup.release { composeRestore() }
        if ((result == State.IDLE && !cleanup.failed) || original === deadView) view = null
        stateFlow.value =
            when {
                cleanup.failed -> State.FAILED
                original == null || result == State.IDLE -> State.IDLE
                else -> result
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
