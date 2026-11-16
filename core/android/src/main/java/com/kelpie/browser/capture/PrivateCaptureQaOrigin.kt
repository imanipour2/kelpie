package com.kelpie.browser.capture

import java.net.URI

/**
 * Isolated, public-page-only capture QA gate.
 *
 * Compare BOTH the tab state and the actual WebView URL. No user-info,
 * alternate-port, lookalike-host or non-HTTPS origin may enter the grant.
 * This is a pure JVM policy so it can be checked by negative unit tests.
 */
internal object PrivateCaptureQaOrigin {
    fun permitted(
        tabUrl: String,
        webViewUrl: String?,
    ): Boolean = allowedUrl(tabUrl) && allowedUrl(webViewUrl)

    private fun allowedUrl(value: String?): Boolean {
        val uri = value?.let { runCatching { URI(it) }.getOrNull() } ?: return false
        return uri.scheme == "https" &&
            uri.host == "example.com" &&
            (uri.port == -1 || uri.port == 443) &&
            uri.rawUserInfo == null
    }
}
