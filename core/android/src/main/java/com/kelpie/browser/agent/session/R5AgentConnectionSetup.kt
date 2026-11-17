package com.kelpie.browser.agent.session

import java.net.URI

/**
 * Non-secret setup metadata for a user-controlled MCP client.
 * This does not authorize a client, create a tunnel, or approve the native R5 session.
 */
internal enum class R5AgentProvider(
    val title: String,
    val helpUrl: String,
    val instructions: String,
) {
    CHATGPT(
        "ChatGPT",
        "https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt",
        "In ChatGPT web, open Plugins and add a custom MCP server. Use the secure endpoint, " +
            "or an existing Secure MCP Tunnel if available. Workspace permissions may apply.",
    ),
    CLAUDE(
        "Claude",
        "https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp",
        "In claude.ai, open Connectors and add a custom remote MCP connector. " +
            "After setup, enabled connectors can also be used from mobile.",
    ),
    GEMINI_CLI(
        "Gemini CLI",
        "https://github.com/google-gemini/gemini-cli/blob/main/docs/tools/mcp-server.md",
        "On your trusted computer, run: gemini mcp add --transport http kelpie <MCP_URL>. " +
            "Authenticate in Gemini CLI if asked. This is a terminal workflow.",
    ),
    OTHER(
        "Other MCP client",
        "https://github.com/modelcontextprotocol/modelcontextprotocol",
        "Open your AI client's custom MCP server settings and add the HTTPS endpoint. " +
            "The client must support remote Streamable HTTP MCP and authentication.",
    ),
}

/**
 * An address is safe to DISPLAY or COPY, not evidence of pairing.
 *
 * Limit the easy-connect path to the conventional /mcp route so a URL carrying
 * tokens in query strings, fragments or custom opaque path segments cannot be
 * accidentally placed on Android's shared clipboard. Advanced routes require
 * a separately reviewed setup flow.
 */
internal object R5AgentConnectionSetup {
    private val dnsName =
        Regex("""^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$""")

    fun safeEndpoint(raw: String): String? {
        val value = raw.trim()
        if (value.length !in 1..1024 || value.any(Char::isISOControl) || value.any(Char::isWhitespace)) {
            return null
        }
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.isOpaque ||
            uri.scheme?.lowercase() != "https" ||
            uri.rawUserInfo != null ||
            uri.rawQuery != null ||
            uri.rawFragment != null ||
            (uri.port != -1 && uri.port != 443) ||
            uri.rawPath !in setOf("/mcp", "/mcp/")
        ) {
            return null
        }
        val host = uri.host?.lowercase() ?: return null
        if (!dnsName.matches(host) ||
            host.length > 253 ||
            host.split('.').any { it.length !in 1..63 } ||
            host.substringAfterLast('.').none(Char::isLetter) ||
            host.endsWith(".local") ||
            host.endsWith(".internal") ||
            host.endsWith(".localhost")
        ) {
            return null
        }
        return "https://$host/mcp"
    }

    fun chatGptTunnelInstructions(): String =
        listOf(
            "Kelpie R5 — private ChatGPT MCP tunnel setup",
            "Open ChatGPT on the web and choose Plugins > Add custom MCP server.",
            "Under Connection, choose Tunnel and select an available Secure MCP Tunnel.",
            "If required, enter a tunnel_id from your authorized OpenAI Platform tunnel setup.",
            "Follow your workspace's authorization and risk acknowledgement steps.",
            "Review discovered MCP tools, install the plugin, and explicitly approve the",
            "native Kelpie session on your Android device.",
            "Finally verify tools/list, actual tabs and a fresh page observation.",
            "Tunnel readiness alone does not mean the browser agent is connected.",
            "Never share your tunnel API key, password, bank data or authorization token.",
        ).joinToString("\n")

    fun setupInstructions(
        provider: R5AgentProvider,
        rawEndpoint: String,
    ): String? {
        val endpoint = safeEndpoint(rawEndpoint) ?: return null
        return listOf(
            "Kelpie R5 remote MCP setup",
            "Provider: " + provider.title,
            "Endpoint: " + endpoint,
            provider.instructions.replace("<MCP_URL>", endpoint),
            "After the client connects, approve its real native Kelpie session request on the phone.",
            "Check authenticated tab listing and an observed page before claiming connection.",
            "Do not put tokens, passwords, payment data or login codes into setup messages.",
        ).joinToString("\n")
    }
}
