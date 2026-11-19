package com.kelpie.browser.network

/**
 * MCP control plane is not a browser action. An already authenticated, non-localhost
 * computer may negotiate protocol metadata even when the owner has stopped or locked
 * the PRIVATE WebView session. All tools/call and direct browser operations still
 * require the native owner/session delivery gates. Never grants a bearer or consent.
 */
internal object R5McpBootstrapGate {
    private val controlMethods = setOf("initialize", "notifications/initialized", "ping", "tools/list")

    fun maySkipNativeSession(
        outerMethod: String,
        inner: Map<String, Any?>,
        verifiedClientId: String?,
    ): Boolean =
        outerMethod == "mcp" &&
            !verifiedClientId.isNullOrBlank() &&
            verifiedClientId != "localhost" &&
            inner["method"] in controlMethods
}
