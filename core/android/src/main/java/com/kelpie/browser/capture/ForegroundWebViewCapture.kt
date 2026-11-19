package com.kelpie.browser.capture

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.webkit.WebView
import com.kelpie.browser.handlers.ScreenshotRefusal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One visible, completely exposed owned WebView; no Canvas or cached-frame fallback. */
internal object ForegroundWebViewCapture {
    data class Frame(
        val bitmap: Bitmap,
        val metadata: Map<String, Any>,
    )

    private data class CopiedPixels(
        val bitmap: Bitmap,
        val acquiredAtNanos: Long,
    )

    private class Attempt(
        val proof: R5PageFrameProof,
    )

    private val main = Handler(Looper.getMainLooper())
    private val captures = Mutex()
    private val proofs = R5FrameProofEpochSequence()
    private var active: Attempt? = null

    /** Any peer may share the terminated renderer; cleanup must not call into either WebView. */
    @Suppress("UNUSED_PARAMETER")
    fun onRendererGone(view: WebView) {
        active = null
    }

    suspend fun copy(
        view: WebView,
        activity: Activity,
        admitted: () -> Boolean,
    ): Frame? {
        var received: Frame? = null
        var transferred = false
        try {
            val result =
                withContext(Dispatchers.Main.immediate) {
                    if (!captures.tryLock()) return@withContext null
                    try {
                        capture(view, activity, admitted).also { received = it }
                    } finally {
                        captures.unlock()
                    }
                }
            transferred = true
            return result
        } finally {
            // withContext may discard a successful result when its caller is cancelled.
            if (!transferred) received?.bitmap?.recycle()
        }
    }

    private suspend fun capture(
        view: WebView,
        activity: Activity,
        admitted: () -> Boolean,
    ): Frame? {
        if (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) {
            throw ScreenshotRefusal("NO_CAPTURE_SECURE_CONTENT", "Protected windows cannot be exported")
        }
        val privacy = WebViewCapturePrivacy.inspect(view)
        val source = bounds(view, activity) ?: return null
        if (!admitted()) return null
        val token = proofs.next()
        val proof = R5PageFrameProof(token)
        val attempt = Attempt(proof)
        active = attempt

        fun current(): Boolean = active === attempt && admitted()
        var bitmap: Bitmap? = null
        try {
            val samples = proof.install(view, view.width, view.height) ?: return null
            if (!current() || !awaitDraw(view) || !current() || bounds(view, activity) != source) return null
            val copied = copyPixels(view, activity, source, ::current) ?: return null
            bitmap = copied.bitmap
            val pixels = copied.bitmap
            if (!current() ||
                bounds(view, activity) != source ||
                !R5FrameProofVerifier.matchesPage(token, samples, pixels::getPixel)
            ) {
                throw ScreenshotRefusal("NO_FRESH_FRAME", "The copied frame does not prove this document paint")
            }
            if (!R5FrameContentCheck.hasContent(pixels.width, pixels.height, samples.region::contains, pixels::getPixel)) {
                throw ScreenshotRefusal("NO_FRESH_FRAME", "Blank, gray or transparent frame was refused")
            }
            if (!current() || !proof.remove(view) || !current()) return null
            privacy.requireSameDocument(WebViewCapturePrivacy.inspect(view))
            if (!current() || bounds(view, activity) != source) return null
            // The witness temporarily covers these public fixture pixels. Mask witness pixels before encoding.
            val region = samples.region
            Canvas(pixels).drawRect(
                region.left.toFloat(),
                region.top.toFloat(),
                region.right.toFloat(),
                region.bottom.toFloat(),
                Paint().apply { color = Color.BLACK },
            )
            val frame =
                Frame(
                    pixels,
                    mapOf(
                        "frameNonce" to "%06x%06x".format(token.primaryRgb, token.secondaryRgb),
                        "frameEpoch" to token.epoch,
                        "frameSequence" to token.sequence,
                        "frameTimestampNanos" to copied.acquiredAtNanos,
                        "freshnessProof" to "DOCUMENT_PIXEL_PAIR",
                        "proofRegionsRedacted" to true,
                    ),
                )
            bitmap = null
            return frame
        } finally {
            bitmap?.recycle()
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                if (active === attempt) {
                    active = null
                    if (!activity.isDestroyed && view.isAttachedToWindow) runCatching { attempt.proof.removeNow(view) }
                }
            }
        }
    }

    private suspend fun copyPixels(
        view: WebView,
        activity: Activity,
        source: Rect,
        admitted: () -> Boolean,
    ): CopiedPixels? {
        val holder = OneShotFrameBuffer<CopiedPixels> { it.bitmap.recycle() }
        val bitmap = runCatching { Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        var received: CopiedPixels? = null
        var transferred = false
        try {
            try {
                PixelCopy.request(activity.window, source, bitmap, { status ->
                    if (status == PixelCopy.SUCCESS && runCatching { admitted() && bounds(view, activity) == source }.getOrDefault(false)) {
                        holder.publish(CopiedPixels(bitmap, SystemClock.elapsedRealtimeNanos()))
                    } else {
                        bitmap.recycle()
                        holder.fail()
                    }
                }, main)
            } catch (_: IllegalArgumentException) {
                bitmap.recycle()
                return null
            }
            val result = withTimeoutOrNull(2500L) { holder.awaitTake().also { received = it } }
            if (result == null) return null
            transferred = true
            return result
        } finally {
            // Timeout may occur after take(), when the holder no longer owns the bitmap.
            if (!transferred) received?.bitmap?.recycle()
            holder.close()
        }
    }

    private fun bounds(
        view: WebView,
        activity: Activity,
    ): Rect? {
        val window = activity.window.decorView
        if (!view.isAttachedToWindow ||
            !view.isShown ||
            !window.isShown ||
            view.rootView !== window ||
            activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 ||
            view.width <= 0 ||
            view.height <= 0 ||
            view.width.toLong() * view.height > 4_194_304L ||
            view.rotation != 0f ||
            view.scaleX != 1f ||
            view.scaleY != 1f
        ) {
            return null
        }
        val local = Rect()
        if (!view.getLocalVisibleRect(local) || local != Rect(0, 0, view.width, view.height)) return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val result = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        if (result.left < 0 || result.top < 0 || result.right > window.width || result.bottom > window.height) return null
        if (nativeSiblingIntersects(view, result)) return null
        return result
    }

    /** Window pixels include native overlays. Uncertain overlap is refused, including transparent hosts. */
    private fun nativeSiblingIntersects(
        view: View,
        source: Rect,
    ): Boolean {
        var current = view
        while (true) {
            val parent = current.parent as? ViewGroup ?: return false
            for (index in 0 until parent.childCount) {
                val sibling = parent.getChildAt(index)
                if (sibling === current || !sibling.isShown || sibling.alpha <= 0f) continue
                val location = IntArray(2)
                sibling.getLocationInWindow(location)
                val siblingBounds = Rect(location[0], location[1], location[0] + sibling.width, location[1] + sibling.height)
                if (Rect.intersects(source, siblingBounds)) return true
            }
            current = parent
        }
    }

    private suspend fun awaitDraw(view: WebView): Boolean {
        val visual =
            withTimeoutOrNull(2500L) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    view.postVisualStateCallback(
                        SystemClock.elapsedRealtimeNanos(),
                        object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                if (continuation.isActive) continuation.resume(true)
                            }
                        },
                    )
                }
            } ?: false
        if (!visual) return false
        var listener: ViewTreeObserver.OnDrawListener? = null
        return try {
            withTimeoutOrNull(2500L) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    val next =
                        ViewTreeObserver.OnDrawListener {
                            view.post {
                                listener?.let { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(it) }
                                listener = null
                                if (continuation.isActive) continuation.resume(true)
                            }
                        }
                    listener = next
                    view.viewTreeObserver.addOnDrawListener(next)
                    view.postInvalidateOnAnimation()
                }
            } ?: false
        } finally {
            listener?.let { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(it) }
        }
    }
}

internal fun webViewActivity(view: WebView): Activity? {
    var context = view.context
    repeat(16) {
        if (context is Activity) return context as Activity
        context = (context as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}
