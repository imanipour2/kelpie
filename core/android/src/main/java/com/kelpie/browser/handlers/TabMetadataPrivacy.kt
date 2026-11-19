package com.kelpie.browser.handlers

import com.kelpie.browser.agent.security.AgentEventRedactor

/** Remote tab inventory has no DOM privacy context, so it exports origin only. */
internal object TabMetadataPrivacy {
    fun url(raw: String?): String = if (raw == "about:blank") "about:blank" else AgentEventRedactor.safeOrigin(raw) ?: "redacted"

    // A document title can contain an account name, OTP or arbitrary page text.
    // Origin identifies the tab without making an unprovable text-redaction claim.
    fun title(rawUrl: String?): String = url(rawUrl).takeUnless { it == "redacted" } ?: "Kelpie tab"
}
