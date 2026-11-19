package com.kelpie.browser.network

/**
 * Owner-consent negotiation is control-plane traffic, never a WebView action.
 *
 * The HTTP authentication middleware must authenticate callers first.
 * The handlers still require an eligible, verified agent and native owner tap.
 * In particular, skipping the legacy WebView lease does not grant consent.
 */
internal object R5AgentControlPlaneRoutes {
    private val consentMethods =
        setOf("agent-session-request", "agent-session-status", "agent-session-stop")

    fun isConsentMethod(method: String): Boolean = method in consentMethods

    fun mayRespondWithoutOwnerGrant(
        method: String,
        verifiedClientId: String?,
    ): Boolean =
        isConsentMethod(method) &&
            !verifiedClientId.isNullOrBlank() &&
            verifiedClientId != "localhost"
}
