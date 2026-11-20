package com.kelpie.browser.capture

import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Only app-owned WebView geometry and visual-state completion; never exports page data. */
internal class R5PrivateViewportReadiness(
    private val locked: () -> Boolean,
) {
    suspend fun cssReady(webView: WebView): Boolean {
        if (locked() || !webView.isAttachedToWindow || webView.width <= 0 || webView.height <= 0) {
            Log.i(
                "KelpieR5Viewport",
                when {
                    locked() -> "CSS_GATE_LOCKED"
                    !webView.isAttachedToWindow -> "CSS_GATE_NOT_ATTACHED"
                    else -> "CSS_GATE_ZERO_NATIVE_SIZE"
                },
            )
            return false
        }
        val answer = CompletableDeferred<String>()
        try {
            webView.evaluateJavascript(
                "window.innerWidth > 0 && window.innerHeight > 0 && " +
                    "document.documentElement.getBoundingClientRect().width > 0",
            ) { raw ->
                if (!answer.isCompleted) answer.complete(raw)
            }
        } catch (_: RuntimeException) {
            Log.w("KelpieR5Viewport", "CSS_EVALUATE_EXCEPTION")
            return false
        }
        val answerValue = withTimeoutOrNull(500L) { answer.await() }
        if (answerValue != "true") {
            Log.i(
                "KelpieR5Viewport",
                if (answerValue == null) "CSS_CALLBACK_TIMEOUT" else "CSS_GEOMETRY_REJECTED",
            )
        }
        return answerValue == "true"
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
        hostFailure: () -> String? = { null },
    ): Boolean {
        val laidOut =
            withTimeoutOrNull(3_000L) {
                while (!locked() && stillOwned()) {
                    if (hostFailure() == null &&
                        R5ViewportAdmission.permits(
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
        if (!laidOut || locked() || !stillOwned()) {
            Log.i(
                "KelpieR5Viewport",
                when {
                    locked() -> "VIEWPORT_DEVICE_LOCKED"
                    !stillOwned() -> "VIEWPORT_OWNERSHIP_LOST"
                    else -> hostFailure() ?: "VIEWPORT_LAYOUT_TIMEOUT"
                },
            )
            return false
        }
        val visual = CompletableDeferred<Long>()
        webView.postVisualStateCallback(
            requestId,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (!visual.isCompleted) visual.complete(requestId)
                }
            },
        )
        val completedRequestId = withTimeoutOrNull(2_000L) { visual.await() }
        val failure =
            R5VisualCommitResult.failure(
                expectedRequestId = requestId,
                completedRequestId = completedRequestId,
                owned = stillOwned(),
                locked = locked(),
                hostFailure = hostFailure(),
            )
        if (failure != null) {
            Log.i("KelpieR5Viewport", failure)
            return false
        }
        val css = cssReady(webView)
        return stillOwned() &&
            hostFailure() == null &&
            R5ViewportAdmission.permits(
                attached = webView.isAttachedToWindow,
                nativeWidth = webView.width,
                nativeHeight = webView.height,
                cssReady = css,
                locked = locked(),
            )
    }
}

internal object R5VisualCommitResult {
    fun failure(
        expectedRequestId: Long,
        completedRequestId: Long?,
        owned: Boolean,
        locked: Boolean,
        hostFailure: String?,
    ): String? =
        when {
            locked -> "VISUAL_STATE_DEVICE_LOCKED"
            !owned -> "VISUAL_STATE_OWNERSHIP_LOST"
            hostFailure != null -> hostFailure
            completedRequestId == null -> "VISUAL_STATE_CALLBACK_TIMEOUT"
            completedRequestId != expectedRequestId -> "VISUAL_STATE_REQUEST_MISMATCH"
            else -> null
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
