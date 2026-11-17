package com.kelpie.browser.capture

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * R5 compositor freshness: two independent native 24-bit markers are painted
 * outside the web page. Neither the visited DOM nor another Android app is
 * touched. Verified 48-bit marker pairs rotate on 512-frame epoch boundaries.
 *
 * This is image freshness evidence, NOT permission to access the browser.
 */
internal class R5NativeFrameProof(
    context: Context,
) {
    private val sequence = R5FrameProofEpochSequence()
    private val primaryMarker =
        View(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    private val secondaryMarker =
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
                primaryMarker,
                FrameLayout.LayoutParams(MARKER_PX, MARKER_PX, Gravity.TOP or Gravity.LEFT),
            )
            frame.addView(
                secondaryMarker,
                FrameLayout.LayoutParams(MARKER_PX, MARKER_PX, Gravity.TOP or Gravity.LEFT).apply {
                    topMargin = MARKER_PX
                },
            )
            root = frame
        }
    }

    /** Swap only the app-owned WebView; retain the independent native proof markers. */
    fun swapWebView(
        oldView: WebView,
        nextView: WebView,
    ): Boolean {
        val frame = root ?: return false
        if (oldView.parent !== frame || nextView.parent != null || oldView === nextView) return false
        return try {
            frame.removeView(oldView)
            frame.addView(
                nextView,
                0,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            nextView.parent === frame
        } catch (_: RuntimeException) {
            (nextView.parent as? ViewGroup)?.removeView(nextView)
            if (oldView.parent == null) {
                frame.addView(
                    oldView,
                    0,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
            false
        }
    }

    fun nextProof(): R5FrameProofEpochSequence.Token {
        check(root != null) { "Native frame proof renderer is not attached" }
        return sequence.next()
    }

    fun repaint(proof: R5FrameProofEpochSequence.Token) {
        check(root != null) { "Native frame proof renderer is not attached" }
        setRgb(primaryMarker, proof.primaryRgb)
        setRgb(secondaryMarker, proof.secondaryRgb)
        primaryMarker.invalidate()
        secondaryMarker.invalidate()
        root?.postInvalidateOnAnimation()
    }

    private fun setRgb(
        view: View,
        rgb: Int,
    ) {
        require(rgb in 0..0xFFFFFF)
        view.setBackgroundColor(Color.rgb(rgb shr 16, (rgb shr 8) and 255, rgb and 255))
    }

    fun dispose() {
        (primaryMarker.parent as? ViewGroup)?.removeView(primaryMarker)
        (secondaryMarker.parent as? ViewGroup)?.removeView(secondaryMarker)
        root = null
    }

    companion object {
        const val MAX_FRAMES = R5FrameNonce.MAX_FRAMES
        private const val MARKER_PX = 24
        const val SAMPLE_PX = 12
        const val SECOND_SAMPLE_PX = SAMPLE_PX + MARKER_PX
    }
}
