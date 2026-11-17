package com.kelpie.browser.browser

import java.net.URI

/**
 * The user can explicitly hand a page to the Android system browser.
 *
 * This is not a way to share cookies, passwords or OAuth credentials with Kelpie.
 * Keep the target in memory only and never dispatch non-web schemes.
 */
internal object ExternalBrowserHandoffPolicy {
    fun mayOpen(url: String): Boolean {
        if (url.length !in 1..4096 || url.any(Char::isISOControl)) return false
        val parsed = runCatching { URI(url) }.getOrNull() ?: return false
        return parsed.scheme.equals("https", ignoreCase = true) &&
            !parsed.host.isNullOrBlank() &&
            parsed.userInfo == null
    }
}
