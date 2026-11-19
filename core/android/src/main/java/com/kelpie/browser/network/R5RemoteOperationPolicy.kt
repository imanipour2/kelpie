package com.kelpie.browser.network

/** Reviewed R5 routes only. A general owner grant is not consent to export secrets. */
internal object R5RemoteOperationPolicy {
    private val controls =
        setOf("mcp", "browser-control", "agent-session-request", "agent-session-status", "agent-session-stop")
    private val reads =
        setOf(
            "get-tabs",
            "get-current-url",
            "get-viewport",
            "get-capabilities",
            "get-device-info",
            "get-orientation",
            "get-page-text",
            "get-visible-elements",
            "find-element",
            "find-button",
            "find-link",
            "find-input",
            "get-form-state",
            "get-accessibility-tree",
            "screenshot",
            "screenshot-annotated",
            "agent-tabs",
            "agent-runtime-status",
            "agent-capture-capabilities",
        )
    private val navigation = setOf("navigate", "back", "forward", "reload", "new-tab", "switch-tab", "close-tab")
    private val observedActions = setOf("click", "type", "scroll", "press-key")
    private val canonical =
        setOf(
            "capabilities",
            "documentation",
            "tabs.list",
            "tabs.new",
            "tabs.select",
            "tabs.close",
            "tab.goto",
            "tab.back",
            "tab.forward",
            "tab.reload",
            "tab.url",
            "tab.title",
            "tab.snapshot",
            "tab.screenshot",
            "tab.click",
            "tab.type",
            "page.scroll",
            "page.press",
        )

    fun permitsRoute(
        method: String,
        fromGateway: Boolean,
    ): Boolean =
        method in controls ||
            method in reads ||
            method in navigation ||
            method == "agent-command" ||
            (fromGateway && method in observedActions)

    // The C++ capability map needs click/type to advertise tab.click/type. Their
    // actual canonical dispatch uses its fixed, observation-bound native reader.
    fun permitsNativeCapability(method: String): Boolean = permitsRoute(method, fromGateway = true)

    fun permitsExtension(method: String): Boolean =
        method in reads ||
            method in navigation ||
            method == "agent-command" ||
            R5AgentControlPlaneRoutes.isConsentMethod(method)

    fun permitsGatewayRequest(method: String): Boolean = method in canonical || permitsExtension(method)
}
