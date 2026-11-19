package com.kelpie.browser.capture

import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

/** A request-local DOM paint witness. It is removed on success, refusal and cancellation. */
internal class R5PageFrameProof(
    private val token: R5FrameProofEpochSequence.Token,
) {
    private val elementId = "kelpie-frame-" + UUID.randomUUID().toString()

    data class Samples(
        val firstX: Int,
        val secondX: Int,
        val y: Int,
        val region: R5RgbaFrameBuffer.Region,
    )

    suspend fun install(
        view: WebView,
        pixelWidth: Int,
        pixelHeight: Int,
    ): Samples? {
        val reply = CompletableDeferred<String>()
        view.evaluateJavascript(installScript()) { reply.complete(it) }
        val raw = withTimeoutOrNull(500L) { reply.await() } ?: return null
        val geometry = runCatching { Json.decodeFromString<List<Double>>(raw) }.getOrNull() ?: return null
        return samples(geometry, pixelWidth, pixelHeight)
    }

    suspend fun remove(view: WebView): Boolean {
        val reply = CompletableDeferred<String>()
        view.evaluateJavascript(removeScript()) { reply.complete(it) }
        return withTimeoutOrNull(500L) { reply.await() } == "true"
    }

    /** Used from non-suspending native cleanup after cancellation or renderer loss. */
    fun removeNow(view: WebView) {
        view.evaluateJavascript(removeScript(), null)
    }

    private fun removeScript() =
        """
        (() => {
          const marker = document.getElementById('$elementId');
          if (marker) { clearTimeout(marker.__kelpieCaptureTimer); marker.remove(); }
          return true;
        })()
        """.trimIndent()

    private fun installScript(): String {
        val primary = token.primaryRgb.toString(16).padStart(6, '0')
        val secondary = token.secondaryRgb.toString(16).padStart(6, '0')
        return """
            (() => {
              if (!document.documentElement || document.getElementById('$elementId')) return null;
              const marker = document.createElement('div');
              marker.id = '$elementId'; marker.setAttribute('aria-hidden', 'true');
              marker.style.cssText = 'all:initial!important;position:fixed!important;right:0!important;' +
                'top:0!important;width:48px!important;height:24px!important;display:block!important;' +
                'z-index:2147483647!important;pointer-events:none!important;opacity:1!important;' +
                'visibility:visible!important;transform:none!important;transition:none!important;' +
                'animation:none!important;filter:none!important;background:#$primary!important;';
              const second = document.createElement('div');
              second.style.cssText = 'all:initial!important;position:absolute!important;right:0!important;' +
                'top:0!important;width:24px!important;height:24px!important;display:block!important;' +
                'opacity:1!important;visibility:visible!important;background:#$secondary!important;';
              marker.appendChild(second); document.documentElement.appendChild(marker);
              // A surviving peer may not be called during renderer-loss cleanup.
              // This request-local fallback owns no global state or listeners.
              marker.__kelpieCaptureTimer = setTimeout(() => marker.remove(), 10000);
              const r = marker.getBoundingClientRect();
              return [r.left, r.top, r.width, r.height, window.innerWidth, window.innerHeight];
            })()
            """.trimIndent()
    }

    companion object {
        /** CSS/native scale is measured for this request, never assumed to equal devicePixelRatio. */
        fun samples(
            values: List<Double>,
            width: Int,
            height: Int,
        ): Samples? {
            if (values.size != 6 || values.any { !it.isFinite() } || width <= 0 || height <= 0) return null
            val left = values[0]
            val top = values[1]
            val markerWidth = values[2]
            val markerHeight = values[3]
            val cssWidth = values[4]
            val cssHeight = values[5]
            if (left < 0 ||
                top < 0 ||
                markerWidth < 24 ||
                markerHeight < 12 ||
                cssWidth <= 0 ||
                cssHeight <= 0 ||
                left + markerWidth > cssWidth ||
                top + markerHeight > cssHeight
            ) {
                return null
            }
            val sx = width / cssWidth
            val sy = height / cssHeight
            val firstX = ((left + markerWidth * 0.25) * sx).toInt()
            val secondX = ((left + markerWidth * 0.75) * sx).toInt()
            val y = ((top + markerHeight * 0.5) * sy).toInt()
            if (firstX == secondX || firstX !in 0 until width || secondX !in 0 until width || y !in 0 until height) return null
            return Samples(
                firstX,
                secondX,
                y,
                R5RgbaFrameBuffer.Region(
                    floor(left * sx).toInt(),
                    floor(top * sy).toInt(),
                    ceil((left + markerWidth) * sx).toInt().coerceAtMost(width),
                    ceil((top + markerHeight) * sy).toInt().coerceAtMost(height),
                ),
            )
        }
    }
}
