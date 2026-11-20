package com.kelpie.browser.network

import com.kelpie.browser.agent.session.R5AgentPrincipalPolicy

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
    ): Boolean = isConsentMethod(method) && R5AgentPrincipalPolicy.eligible(verifiedClientId)

    /** A legacy request must not prompt again or bypass the mother's saved STOP. */
    suspend fun motherComputerRequestResponse(
        motherMode: Boolean,
        computerStillTrusted: (String) -> Boolean,
        paused: Boolean,
    ): Map<String, Any?>? {
        if (!motherMode) return null
        val key = verifiedComputerKey() ?: return null
        if (key != verifiedClientId() || !computerStillTrusted(key)) return null
        return successResponse(
            mapOf(
                "status" to if (paused) "PAUSED_NATIVE_RESUME_REQUIRED" else "COMPUTER_ALREADY_APPROVED",
                "nativeApprovalRequired" to false,
                "paused" to paused,
            ),
        )
    }

    /** Run after the Main-thread hop, before touching process consent state. */
    suspend fun rejectionIfRevoked(): Map<String, Any?>? =
        if (verifiedRequestAuthorityCurrent()) {
            null
        } else {
            errorResponse("AUTHORIZATION_REVOKED", "Paired client authorization changed before consent handling")
        }
}
