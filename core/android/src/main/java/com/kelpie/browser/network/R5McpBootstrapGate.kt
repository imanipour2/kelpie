package com.kelpie.browser.network

import com.kelpie.browser.agent.session.R5AgentPrincipalPolicy

/**
 * MCP control plane is not a browser action. An already authenticated, non-localhost
 * computer may negotiate protocol metadata even when the owner has stopped or locked
 * the PRIVATE WebView session. All tools/call and direct browser operations still
 * require the native owner/session delivery gates. Never grants a bearer or consent.
 */
internal object R5McpBootstrapGate {
    private val controlMethods = setOf("initialize", "notifications/initialized", "ping", "tools/list")

    /**
     * Applies in foreground and PRIVATE ownership. Only verified protocol
     * metadata and native consent negotiation are independent of a grant.
     */
    fun requiresNativeGrant(
        outerMethod: String,
        inner: Map<String, Any?>,
        verifiedClientId: String?,
    ): Boolean =
        !maySkipNativeSession(outerMethod, inner, verifiedClientId) &&
            !R5AgentControlPlaneRoutes.mayRespondWithoutOwnerGrant(outerMethod, verifiedClientId)

    fun maySkipNativeSession(
        outerMethod: String,
        inner: Map<String, Any?>,
        verifiedClientId: String?,
    ): Boolean =
        outerMethod == "mcp" &&
            R5AgentPrincipalPolicy.eligible(verifiedClientId) &&
            (inner["method"] as? String)?.let { it in controlMethods } == true
}
