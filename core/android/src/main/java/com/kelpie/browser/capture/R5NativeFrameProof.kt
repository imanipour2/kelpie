package com.kelpie.browser.capture

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import java.security.SecureRandom

/**
 * R5-only compositor freshness nonce rendered by Kelpie itself.
 *
 * P4's example.com DOM marker is never allowed on real customer sites. This
 * tiny, noninteractive native overlay belongs to an app-private Presentation;
 * it cannot read the phone screen or change the visited document.
 *
 * A matching nonce proves the ImageReader frame is new, but it does not on
 * its own prove web-page navigation completion; callers verify page state.
 */
internal class R5NativeFrameProof(
    context: Context,
) {
    private val seed = SecureRandom().nextInt(0xFFFFFF)
    private val marker =
        View(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    private var root: FrameLayout? = null

    fun host(webView: WebView): FrameLayout {
        check(root == null) { "Native frame proof cannot attach twice" }
        return FrameLayout(webView.context).also { frame ->
            frame.addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            frame.addView(
                marker,
                FrameLayout.LayoutParams(MARKER_PX, MARKER_PX, Gravity.TOP or Gravity.LEFT),
            )
            root = frame
        }
    }

    fun expectedRgb(sequence: Int): Int = R5FrameNonce.color(seed, sequence)

    fun repaint(expectedRgb: Int) {
        check(root != null) { "Native proof renderer is not attached" }
        require(expectedRgb in 0..0xFFFFFF)
        marker.setBackgroundColor(Color.rgb(expectedRgb shr 16, (expectedRgb shr 8) and 255, expectedRgb and 255))
        marker.invalidate()
        root?.postInvalidateOnAnimation()
    }

    fun dispose() {
        (marker.parent as? ViewGroup)?.removeView(marker)
        root = null
    }

    companion object {
        const val MAX_FRAMES = R5FrameNonce.MAX_FRAMES
        const val SAMPLE_PX = 12
        private const val MARKER_PX = 24
    }
}
