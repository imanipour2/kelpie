package com.kelpie.browser.agent.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R5PrivateMcpContractTest {
    private fun browserCall(
        method: String,
        values: Map<String, Any?> = emptyMap(),
        name: String = R5PrivateMcpContract.BROWSER_TOOL,
    ): Map<String, Any?> =
        mapOf(
            "method" to "tools/call",
            "params" to
                mapOf(
                    "name" to name,
                    "arguments" to mapOf("method" to method, "params" to values),
                ),
        )

    @Test
    fun staticMetadataDoesNotNeedAReadGrantButRealTabAccessDoes() {
        assertTrue(R5PrivateMcpContract.isGrantExempt(browserCall("capabilities")))
        assertTrue(R5PrivateMcpContract.isGrantExempt(browserCall("documentation")))
        assertFalse(R5PrivateMcpContract.isGrantExempt(browserCall("tabs.list")))
        assertFalse(R5PrivateMcpContract.isGrantExempt(browserCall("tab.snapshot")))
        assertFalse(R5PrivateMcpContract.isGrantExempt(browserCall("tab.screenshot")))
    }

    @Test
    fun grantExemptionCannotBeSpoofedByAnotherToolName() {
        assertFalse(R5PrivateMcpContract.isGrantExempt(browserCall("capabilities", name = "unknown")))
        assertFalse(R5PrivateMcpContract.isGrantExempt(mapOf("method" to "tools/call")))
        assertFalse(R5PrivateMcpContract.isGrantExempt(mapOf("method" to "tools/list")))
    }

    @Test
    fun realGenerationIsExplicitlyInToolArgumentsNotHttpClientIdentity() {
        val nested = browserCall("tabs.list", mapOf("consentGeneration" to "native-grant-123"))
        assertEquals("native-grant-123", R5PrivateMcpContract.expectedGeneration(nested))
        assertNull(R5PrivateMcpContract.expectedGeneration(browserCall("tabs.list")))
        assertNull(R5PrivateMcpContract.expectedGeneration(browserCall("capabilities")))
    }

    private fun wrappedBrowserCall(
        method: String,
        values: Map<String, Any?> = emptyMap(),
        name: String = R5PrivateMcpContract.BROWSER_TOOL,
        version: Int = 1,
    ): Map<String, Any?> =
        mapOf(
            "method" to "tools/call",
            "params" to
                mapOf(
                    "name" to name,
                    "arguments" to
                        mapOf(
                            "request" to
                                mapOf("version" to version, "method" to method, "params" to values),
                        ),
                ),
        )

    @Test
    fun installedBrowserControlWrapperAllowsOnlyStaticDiscoveryWithoutGrant() {
        for (method in listOf("capabilities", "documentation")) {
            val body = wrappedBrowserCall(method)
            assertTrue(R5PrivateMcpContract.isGrantExempt(body))
            assertFalse(R5AgentDeliveryGuard.requiresGrant("mcp", body))
            assertEquals(method, R5PrivateMcpContract.toolArguments(body)?.get("method"))
        }
        for (method in listOf("tabs.list", "tabs.new", "tabs.close", "tabs.select", "tab.snapshot", "tab.screenshot", "tab.goto", "tab.click")) {
            val body = wrappedBrowserCall(method)
            assertFalse(R5PrivateMcpContract.isGrantExempt(body))
            assertTrue(R5AgentDeliveryGuard.requiresGrant("mcp", body))
        }
    }

    @Test
    fun wrappedGenerationIsStillStrictlyRequiredForAnyPrivilegedOperation() {
        val values = mapOf<String, Any?>("consentGeneration" to "native-generation")
        val body = wrappedBrowserCall("tabs.list", values)
        assertEquals("native-generation", R5PrivateMcpContract.expectedGeneration(body))
        assertEquals("native-generation", R5AgentDeliveryGuard.expectedGeneration("mcp", body))
        assertNull(R5PrivateMcpContract.expectedGeneration(wrappedBrowserCall("tabs.list")))
    }

    @Test
    fun ambiguousOrSpoofedWrappersAreNeverExemptOrUnwrapped() {
        val valid = wrappedBrowserCall("capabilities")
        val fakeTool = wrappedBrowserCall("capabilities", name = R5PrivateMcpContract.ACTION_TOOL)
        val versionMismatch = wrappedBrowserCall("capabilities", version = 2)
        val wrongType =
            mapOf<String, Any?>(
                "method" to "tools/call",
                "params" to
                    mapOf(
                        "name" to R5PrivateMcpContract.BROWSER_TOOL,
                        "arguments" to mapOf("request" to "capabilities"),
                    ),
            )
        val ambiguous =
            mapOf<String, Any?>(
                "method" to "tools/call",
                "params" to
                    mapOf(
                        "name" to R5PrivateMcpContract.BROWSER_TOOL,
                        "arguments" to
                            mapOf(
                                "method" to "capabilities",
                                "request" to mapOf("version" to 1, "method" to "tabs.list"),
                            ),
                    ),
            )
        assertTrue(R5PrivateMcpContract.isGrantExempt(valid))
        for (body in listOf(fakeTool, versionMismatch, wrongType, ambiguous)) {
            assertNull(R5PrivateMcpContract.toolArguments(body))
            assertFalse(R5PrivateMcpContract.isGrantExempt(body))
            assertTrue(R5AgentDeliveryGuard.requiresGrant("mcp", body))
        }
    }

    @Test
    fun installedBrowserOnlyConnectorCanRequestOrStopButNeverApprove() {
        for (method in listOf("session.request", "session.status", "session.stop")) {
            val body = wrappedBrowserCall(method)
            assertTrue(R5PrivateMcpContract.isGrantExempt(body))
            assertFalse(R5AgentDeliveryGuard.requiresGrant("mcp", body))
            assertNull(R5PrivateMcpContract.expectedGeneration(body))
        }
        for (method in listOf("session.approve", "session.grant", "session.unlock")) {
            val body = wrappedBrowserCall(method)
            assertFalse(R5PrivateMcpContract.isGrantExempt(body))
            assertTrue(R5AgentDeliveryGuard.requiresGrant("mcp", body))
        }
        val wrongTool = wrappedBrowserCall("session.request", name = R5PrivateMcpContract.ACTION_TOOL)
        assertFalse(R5PrivateMcpContract.isGrantExempt(wrongTool))
        assertTrue(R5AgentDeliveryGuard.requiresGrant("mcp", wrongTool))
    }

    @Test
    fun nativeSessionMethodsRemainPairedClientOnlyInTheRouter() {
        for (method in listOf("request", "status", "stop")) {
            val envelope =
                mapOf<String, Any?>(
                    "method" to "tools/call",
                    "params" to
                        mapOf(
                            "name" to R5PrivateMcpContract.SESSION_TOOL,
                            "arguments" to mapOf("method" to method),
                        ),
                )
            assertTrue(R5PrivateMcpContract.isGrantExempt(envelope))
            assertNull(R5PrivateMcpContract.expectedGeneration(envelope))
        }
    }
}
