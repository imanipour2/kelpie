package com.kelpie.browser.network

import com.kelpie.browser.agent.session.R5AgentPrincipalPolicy

/**
 * MCP control plane is not a browser action. An already authenticated, non-localhost
 * computer may negotiate protocol metadata even when the owner has stopped or locked
 * the PRIVATE WebView session. Static documentation/capabilities and extension
 * schemas also contain no browser content. Browser operations retain their
 * execution/delivery gates. Never grants a bearer or computer approval.
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
    ): Boolean {
        if (!R5AgentPrincipalPolicy.eligible(verifiedClientId)) return false
        if (outerMethod == "browser-control") return isStaticBrowserMetadata(inner)
        if (outerMethod != "mcp") return false
        if ((inner["method"] as? String)?.let { it in controlMethods } == true) return true
        if (inner["jsonrpc"] != "2.0" || inner["method"] != "tools/call") return false
        val params = inner["params"] as? Map<*, *> ?: return false
        val arguments = params["arguments"] as? Map<*, *> ?: return false
        return when (params["name"]) {
            "kelpie_browser_control" -> arguments.keys == setOf("request") && isStaticBrowserMetadata(arguments["request"] as? Map<*, *>)
            "kelpie_extensions" -> arguments.isEmpty()
            "kelpie_extension_tools" -> {
                val names = arguments["names"] as? List<*> ?: return false
                arguments.keys == setOf("names") && names.size <= 20 && names.all { it is String }
            }
            else -> false
        }
    }

    /** Mirrors the shared v1 empty-params schema; never treats a tab read as metadata. */
    fun isStaticBrowserMetadata(request: Map<*, *>?): Boolean =
        request != null &&
            (request["version"] == 1 || request["version"] == 1L) &&
            request["method"] in setOf("capabilities", "documentation") &&
            request.keys.all { it in setOf("version", "method", "params") } &&
            (!request.containsKey("params") || (request["params"] as? Map<*, *>)?.isEmpty() == true)
}
