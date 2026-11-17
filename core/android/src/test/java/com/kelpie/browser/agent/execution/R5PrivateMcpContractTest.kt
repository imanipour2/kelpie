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
