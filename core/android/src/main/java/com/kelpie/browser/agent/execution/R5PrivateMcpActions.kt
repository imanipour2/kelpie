package com.kelpie.browser.agent.execution

/** Explicit write-tool dispatch; native admission independently validates the live owner and target. */
internal object R5PrivateMcpActions {
    val methods = listOf("tab.goto", "tab.click", "tab.fill", "tab.select", "tab.wait", "tab.back", "tab.forward", "page.scroll", "tabs.new", "tabs.close")

    fun operation(method: String): String? =
        when (method) {
            "tab.goto" -> "NAVIGATE"
            "tab.click" -> "CLICK"
            "tab.fill" -> "FILL"
            "tab.select" -> "SELECT"
            "tab.wait" -> "WAIT"
            "tab.back" -> "BACK"
            "tab.forward" -> "FORWARD"
            "page.scroll" -> "SCROLL"
            "tabs.new" -> "OPEN_TAB"
            "tabs.close" -> "CLOSE_TAB"
            else -> null
        }

    fun safeArguments(
        method: String,
        params: Map<*, *>,
    ): Map<String, Any?>? {
        if (method !in methods) return null
        if (method == "tab.back" || method == "tab.forward") return emptyMap()
        if (method == "tabs.new") {
            val url = params["url"] as? String ?: return null
            if (url.isBlank() || url.length > 2048) return null
            return mapOf("url" to url)
        }
        if (method == "tabs.close") {
            val target = params["targetTabId"] as? String ?: return null
            if (target.isBlank() || target.length > 256) return null
            return mapOf("targetTabId" to target)
        }
        if (method == "tab.wait") {
            val selector = params["selector"] ?: return emptyMap()
            if (selector !is String || selector.isBlank() || selector.length > 500) return null
            return mapOf("selector" to selector)
        }
        if (method == "page.scroll") {
            val x = params["scrollX"] as? Number ?: return null
            val y = params["scrollY"] as? Number ?: return null
            if (!x.toDouble().isFinite() || !y.toDouble().isFinite()) return null
            return mapOf("scrollX" to x.toDouble(), "scrollY" to y.toDouble())
        }
        if (method == "tab.goto") {
            val url = params["url"] as? String ?: return null
            if (url.isBlank() || url.length > 2048) return null
            return mapOf("url" to url)
        }
        val selector = params["selector"] as? String ?: return null
        if (selector.isBlank() || selector.length > 500) return null
        if (method == "tab.click") return mapOf("selector" to selector)
        val input = params["text"] as? String ?: return null
        if (input.length > 4096) return null
        return mapOf("selector" to selector, "text" to input)
    }
}
