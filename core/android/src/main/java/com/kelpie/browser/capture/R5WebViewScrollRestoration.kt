package com.kelpie.browser.capture

import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Stateless CSS-coordinate read/restore; lifecycle and the one owned WebView stay in the display. */
internal object R5WebViewScrollRestoration {
    suspend fun read(view: WebView): Pair<Double, Double>? {
        suspend fun coordinate(expression: String): Double? {
            if (!view.isAttachedToWindow) return null
            val reply = CompletableDeferred<String>()
            view.evaluateJavascript(expression) { raw -> reply.complete(raw) }
            return withTimeoutOrNull(800L) { reply.await() }?.toDoubleOrNull()?.takeIf { it.isFinite() }
        }
        val x = coordinate("window.scrollX") ?: return null
        val y = coordinate("window.scrollY")?.takeIf { it >= 0.0 } ?: return null
        return x to y
    }

    suspend fun restore(
        view: WebView,
        target: Pair<Double, Double>,
        stillOwned: () -> Boolean,
    ): Boolean {
        fun matches(current: Pair<Double, Double>?): Boolean = current != null && abs(current.first - target.first) <= 1.5 && abs(current.second - target.second) <= 1.5
        delay(650)
        var stable = 0
        repeat(12) {
            if (!stillOwned() || !view.isAttachedToWindow) return false
            view.evaluateJavascript("window.scrollTo(${target.first},${target.second})", null)
            delay(250)
            stable = if (matches(read(view))) stable + 1 else 0
            if (stable >= 3) {
                delay(700)
                if (stillOwned() && matches(read(view))) return true
                stable = 0
            }
        }
        return false
    }
}
