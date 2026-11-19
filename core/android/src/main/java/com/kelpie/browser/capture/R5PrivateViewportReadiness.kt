package com.kelpie.browser.capture

import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Only app-owned WebView geometry and visual-state completion; never exports page data. */
internal class R5PrivateViewportReadiness(
    private val locked: () -> Boolean,
) {
    suspend fun cssReady(webView: WebView): Boolean {
        if (locked() || !webView.isAttachedToWindow || webView.width <= 0 || webView.height <= 0) return false
        val answer = CompletableDeferred<String>()
        try {
            webView.evaluateJavascript(
                "window.innerWidth > 0 && window.innerHeight > 0 && " +
                    "document.documentElement.getBoundingClientRect().width > 0",
            ) { raw ->
                if (!answer.isCompleted) answer.complete(raw)
            }
        } catch (_: RuntimeException) {
            return false
        }
        return withTimeoutOrNull(500L) { answer.await() } == "true"
    }

    /**
     * A tab is not considered selected just because its JavaScript realm exists.
     * Wait for attachment, native and CSS dimensions and a visual-state callback;
     * failures must leave the previous private renderer in charge.
     */
    suspend fun awaitCommittedViewport(
        webView: WebView,
        requestId: Long,
        stillOwned: () -> Boolean = { true },
    ): Boolean {
        val laidOut =
            withTimeoutOrNull(3_000L) {
                while (!locked() && stillOwned()) {
                    if (R5ViewportAdmission.permits(
                            attached = webView.isAttachedToWindow,
                            nativeWidth = webView.width,
                            nativeHeight = webView.height,
                            cssReady = cssReady(webView),
                            locked = locked(),
                        )
                    ) {
                        return@withTimeoutOrNull true
                    }
                    delay(80)
                }
                false
            } ?: false
        if (!laidOut || locked() || !stillOwned()) return false
        val visual = CompletableDeferred<Boolean>()
        val expectedRequestId = requestId
        webView.postVisualStateCallback(
            requestId,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (!visual.isCompleted) visual.complete(requestId == expectedRequestId && stillOwned())
                }
            },
        )
        val committed = withTimeoutOrNull(2_000L) { visual.await() } == true
        return committed &&
            stillOwned() &&
            R5ViewportAdmission.permits(
                attached = webView.isAttachedToWindow,
                nativeWidth = webView.width,
                nativeHeight = webView.height,
                cssReady = cssReady(webView),
                locked = locked(),
            )
    }
}

internal object R5ViewportAdmission {
    fun permits(
        attached: Boolean,
        nativeWidth: Int,
        nativeHeight: Int,
        cssReady: Boolean,
        locked: Boolean,
    ): Boolean = attached && nativeWidth > 0 && nativeHeight > 0 && cssReady && !locked
}
