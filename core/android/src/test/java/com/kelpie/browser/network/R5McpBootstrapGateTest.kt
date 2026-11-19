package com.kelpie.browser.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression boundary for the R5 mother MCP startup probe:
 * protocol negotiation must not require a private WebView owner grant,
 * while browser actions must still pass the native session gates.
 */
class R5McpBootstrapGateTest {
    @Test
    fun authenticatedNonLocalhostClientCanNegotiateProtocol() {
        for (method in listOf("initialize", "notifications/initialized", "ping", "tools/list")) {
            assertTrue(
                "Authenticated protocol method $method should remain available after native STOP",
                R5McpBootstrapGate.maySkipNativeSession(
                    "mcp",
                    mapOf("method" to method),
                    "verified-paired-computer",
                ),
            )
        }
    }

    @Test
    fun localhostAndMissingPrincipalCannotBypassOwnerGrant() {
        for (principal in listOf<String?>(null, "", "  ", "localhost")) {
            assertFalse(
                R5McpBootstrapGate.maySkipNativeSession(
                    "mcp",
                    mapOf("method" to "initialize"),
                    principal,
                ),
            )
        }
    }

    @Test
    fun browserActionsAndUnrecognizedMcpMethodsRemainProtected() {
        for (method in listOf("tools/call", "resources/read", "prompts/get", "get-tabs", "agent-command")) {
            assertFalse(
                R5McpBootstrapGate.maySkipNativeSession(
                    "mcp",
                    mapOf("method" to method),
                    "verified-paired-computer",
                ),
            )
        }
        assertFalse(
            R5McpBootstrapGate.maySkipNativeSession(
                "mcp",
                mapOf("method" to "Initialize"),
                "verified-paired-computer",
            ),
        )
    }

    @Test
    fun nonMcpRoutesAndMalformedRequestsCannotBypassOwnerGrant() {
        assertFalse(
            R5McpBootstrapGate.maySkipNativeSession(
                "browser-control",
                mapOf("method" to "initialize"),
                "verified-paired-computer",
            ),
        )
        assertFalse(
            R5McpBootstrapGate.maySkipNativeSession(
                "agent-command",
                mapOf("method" to "tools/list"),
                "verified-paired-computer",
            ),
        )
        assertFalse(
            R5McpBootstrapGate.maySkipNativeSession("mcp", emptyMap(), "verified-paired-computer"),
        )
        assertFalse(
            R5McpBootstrapGate.maySkipNativeSession("mcp", mapOf("method" to 1), "verified-paired-computer"),
        )
    }
}
