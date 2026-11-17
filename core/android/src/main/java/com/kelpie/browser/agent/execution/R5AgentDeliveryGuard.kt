package com.kelpie.browser.agent.execution

import com.kelpie.browser.agent.session.SessionConsentCore

/**
 * Last-mile check before Ktor serializes a previously awaited R5 agent result.
 * This cannot retract bytes already sent; it prevents late success delivery
 * from a revoked or replaced process grant.
 */
internal object R5AgentDeliveryGuard {
    fun requiresGrant(
        method: String,
        body: Map<String, Any?>,
    ): Boolean =
        method in setOf("agent-command", "agent-tabs", "agent-runtime-status", "agent-capture-capabilities") ||
            (method == "mcp" && (body["method"] as? String) == "tools/call" &&
                !R5PrivateMcpContract.isGrantExempt(body))

    fun expectedGeneration(
        method: String,
        body: Map<String, Any?>,
    ): String? {
        if (method != "mcp") return body["consentGeneration"] as? String
        return R5PrivateMcpContract.expectedGeneration(body)
    }

    fun permits(
        principal: String?,
        expectedGeneration: String?,
        state: SessionConsentCore.State,
    ): Boolean {
        val active = state as? SessionConsentCore.State.Active ?: return false
        return !active.locked &&
            !principal.isNullOrBlank() &&
            !expectedGeneration.isNullOrBlank() &&
            active.principalId == principal &&
            active.generation == expectedGeneration
    }
}
