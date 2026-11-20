package com.kelpie.browser.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static discovery must never acquire a tab, renderer, or browser-content permission. */
class R5StaticMetadataAdmissionTest {
    private val paired = "a".repeat(64)

    private fun browserRequest(method: String = "capabilities"): Map<String, Any?> =
        mapOf("version" to 1, "method" to method, "params" to emptyMap<String, Any?>())

    private fun tool(
        name: String,
        arguments: Map<String, Any?>,
    ): Map<String, Any?> =
        mapOf(
            "jsonrpc" to "2.0",
            "id" to 1,
            "method" to "tools/call",
            "params" to mapOf("name" to name, "arguments" to arguments),
        )

    private fun browserTool(request: Map<String, Any?>): Map<String, Any?> = tool("kelpie_browser_control", mapOf("request" to request))

    @Test
    fun exactSharedMetadataWorksThroughBothExistingTransportEnvelopes() {
        for (method in listOf("capabilities", "documentation")) {
            val request = browserRequest(method)
            assertFalse(R5McpBootstrapGate.requiresNativeGrant("browser-control", request, paired))
            assertFalse(R5McpBootstrapGate.requiresNativeGrant("mcp", browserTool(request), paired))
            assertFalse(R5McpBootstrapGate.requiresNativeGrant("mcp", browserTool(request - "params"), paired))
            assertFalse(R5McpBootstrapGate.requiresNativeGrant("mcp", browserTool(request + ("version" to 1L)), paired))
        }
    }

    @Test
    fun aMetadataNameCannotHideATabSelectionOrNonemptyParameters() {
        for (method in listOf("capabilities", "documentation")) {
            val canonical = browserRequest(method)
            val unsafe =
                listOf(
                    canonical + ("tabId" to "real-tab"),
                    canonical + ("params" to mapOf("tabId" to "real-tab")),
                    canonical + ("params" to mapOf("selector" to "input")),
                    canonical + ("params" to null),
                    canonical + ("params" to "{}"),
                    canonical + ("version" to 2),
                    canonical + ("version" to 1.0),
                    canonical + ("consentGeneration" to "old-epoch"),
                )
            for (request in unsafe) {
                assertTrue(R5McpBootstrapGate.requiresNativeGrant("browser-control", request, paired))
                assertTrue(R5McpBootstrapGate.requiresNativeGrant("mcp", browserTool(request), paired))
            }
        }
    }

    @Test
    fun ordinaryReadsAndSpoofedToolWrappersNeverBecomeStaticMetadata() {
        for (method in listOf("tabs.list", "tab.title", "tab.url", "tab.snapshot", "tab.screenshot", "Capabilities", "session.request")) {
            assertTrue(R5McpBootstrapGate.requiresNativeGrant("mcp", browserTool(browserRequest(method)), paired))
        }
        val request = browserRequest()
        assertTrue(R5McpBootstrapGate.requiresNativeGrant("mcp", tool("kelpie_extension", mapOf("request" to request)), paired))
        assertTrue(R5McpBootstrapGate.requiresNativeGrant("mcp", tool("kelpie_browser_control", request), paired))
        val extraArgument = tool("kelpie_browser_control", mapOf("request" to request, "tabId" to "real-tab"))
        assertTrue(R5McpBootstrapGate.requiresNativeGrant("mcp", extraArgument, paired))
        assertTrue(R5McpBootstrapGate.requiresNativeGrant("mcp", browserTool(request) - "jsonrpc", paired))
        assertTrue(R5McpBootstrapGate.requiresNativeGrant("mcp", browserTool(request) + ("method" to "tools/Call"), paired))
    }

    @Test
    fun staticDiscoveryStillNeedsAnAuthenticatedEligiblePrincipal() {
        for (principal in listOf<String?>(null, "", " ", "localhost", "bad\nprincipal")) {
            assertTrue(R5McpBootstrapGate.requiresNativeGrant("mcp", browserTool(browserRequest()), principal))
            assertTrue(R5McpBootstrapGate.requiresNativeGrant("browser-control", browserRequest(), principal))
        }
    }

    @Test
    fun extensionMetadataAllowsBoundedSchemasButNeverExtensionExecution() {
        assertFalse(R5McpBootstrapGate.requiresNativeGrant("mcp", tool("kelpie_extensions", emptyMap()), paired))
        val names = listOf("fixture", "get-tabs")
        assertFalse(R5McpBootstrapGate.requiresNativeGrant("mcp", tool("kelpie_extension_tools", mapOf("names" to names)), paired))
        val maximum = List(20) { "fixture-$it" }
        assertFalse(R5McpBootstrapGate.requiresNativeGrant("mcp", tool("kelpie_extension_tools", mapOf("names" to maximum)), paired))
        // The shared discovery schema bounds item count, not each name's length.
        // Unknown names return no matching compiled schemas and cannot execute.
        val unknownNames = listOf("", " ", "x".repeat(257))
        assertFalse(R5McpBootstrapGate.requiresNativeGrant("mcp", tool("kelpie_extension_tools", mapOf("names" to unknownNames)), paired))

        val rejected =
            listOf(
                tool("kelpie_extension", mapOf("name" to "get-tabs", "arguments" to emptyMap<String, Any?>())),
                tool("kelpie_extensions", mapOf("tabId" to "real-tab")),
                tool("kelpie_extension_tools", mapOf("names" to List(21) { "fixture-$it" })),
                tool("kelpie_extension_tools", mapOf("names" to listOf("fixture", 1))),
                tool("kelpie_extension_tools", mapOf("names" to names, "arguments" to emptyMap<String, Any?>())),
                tool("kelpie_extension_tools", mapOf("names" to "fixture")),
            )
        for (request in rejected) {
            assertTrue(R5McpBootstrapGate.requiresNativeGrant("mcp", request, paired))
        }
    }
}
