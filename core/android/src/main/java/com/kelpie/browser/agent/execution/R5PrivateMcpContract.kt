package com.kelpie.browser.agent.execution

/** Transport-only recognition. Never derives native browser grants from MCP JSON. */
internal object R5PrivateMcpContract {
    const val BROWSER_TOOL = "kelpie_browser_control"
    const val SESSION_TOOL = "kelpie_agent_session"
    const val ACTION_TOOL = "kelpie_browser_actions"

    /** Normalize only the installed v1 browser-control wrapper. Never unwrap action/session grants. */
    fun toolArguments(body: Map<String, Any?>): Map<*, *>? {
        val params = body["params"] as? Map<*, *> ?: return null
        val supplied = params["arguments"] as? Map<*, *> ?: return null
        if (!supplied.containsKey("request")) return supplied
        if (params["name"] != BROWSER_TOOL || supplied.size != 1) return null
        val request = supplied["request"] as? Map<*, *> ?: return null
        return request.takeIf { (it["version"] as? Number)?.toInt() == 1 }
    }

    fun isGrantExempt(body: Map<String, Any?>): Boolean {
        if (body["method"] != "tools/call") return false
        val params = body["params"] as? Map<*, *> ?: return false
        val tool = params["name"] as? String
        if (tool == SESSION_TOOL) return true // Native router still verifies paired principal.
        val action = toolArguments(body)?.get("method") as? String
        return tool == BROWSER_TOOL &&
            action in
            setOf(
                "capabilities",
                "documentation",
                "session.request",
                "session.status",
                "session.stop",
            )
    }

    fun expectedGeneration(body: Map<String, Any?>): String? {
        val args = toolArguments(body) ?: return null
        return args["consentGeneration"] as? String
            ?: (args["params"] as? Map<*, *>)?.get("consentGeneration") as? String
    }
}
