package com.kelpie.browser.agent.session

/**
 * Presentation-only connection readiness. The native process grant remains the
 * sole authorization decision and is always validated again by the gateway.
 * Never treat a locally granted session as proof that the cloud client can
 * reach the phone, or a retained pairing as a currently authenticated client.
 */
internal enum class R5AgentLinkStage {
    WAITING_FOR_AUTHENTICATED_AGENT,
    WAITING_FOR_NATIVE_OWNER,
    NATIVE_CONSENT_ACTIVE,
}

internal object R5AgentLinkReadiness {
    fun stage(
        verifiedPrincipalIds: List<String>,
        activePrincipalId: String?,
    ): R5AgentLinkStage =
        when {
            activePrincipalId != null -> R5AgentLinkStage.NATIVE_CONSENT_ACTIVE
            verifiedPrincipalIds.isNotEmpty() -> R5AgentLinkStage.WAITING_FOR_NATIVE_OWNER
            else -> R5AgentLinkStage.WAITING_FOR_AUTHENTICATED_AGENT
        }
}
