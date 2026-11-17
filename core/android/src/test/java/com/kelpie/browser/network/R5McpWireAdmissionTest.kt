package com.kelpie.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class R5McpWireAdmissionTest {
    private fun request(
        method: String = "tools/call",
        version: String = R5McpWireAdmission.MODERN,
        name: String = "kelpie_browser_control",
    ): Map<String, Any?> =
        mapOf(
            "jsonrpc" to "2.0",
            "id" to 7,
            "method" to method,
            "params" to
                mapOf(
                    "name" to name,
                    "_meta" to
                        mapOf(
                            "io.modelcontextprotocol/protocolVersion" to version,
                            "io.modelcontextprotocol/clientInfo" to mapOf("name" to "test-agent", "version" to "1.0"),
                            "io.modelcontextprotocol/clientCapabilities" to emptyMap<String, Any>(),
                        ),
                ),
        )

    @Test
    fun matchingModernEnvelopeAndRoutingHeadersAreAccepted() {
        assertNull(R5McpWireAdmission.check(request(), R5McpWireAdmission.MODERN, "tools/call", "kelpie_browser_control"))
    }

    @Test
    fun mismatchedToolNameAndMethodAreRejectedBeforeDispatch() {
        assertNotNull(R5McpWireAdmission.check(request(), R5McpWireAdmission.MODERN, "tools/call", "unexpected"))
        assertNotNull(R5McpWireAdmission.check(request(), R5McpWireAdmission.MODERN, "tools/list", "kelpie_browser_control"))
    }

    @Test
    fun unsupportedVersionYieldsTheModernUnsupportedVersionCode() {
        val failure = R5McpWireAdmission.check(request(), "1900-01-01", "tools/call", "kelpie_browser_control")
        assertEquals(-32022, failure?.code)
    }

    @Test
    fun incompleteModernMetadataCannotHideBehindMissingHttpHeaders() {
        assertNotNull(R5McpWireAdmission.check(request(), null, null, null))
        assertNotNull(
            R5McpWireAdmission.check(
                mapOf("method" to "tools/list", "params" to emptyMap<String, Any>()),
                R5McpWireAdmission.MODERN,
                "tools/list",
                null,
            ),
        )
    }

    @Test
    fun LegacyHandshakeWithout2026MetadataRemainsSupportedForExistingClients() {
        val legacy = mapOf<String, Any?>("method" to "initialize", "params" to emptyMap<String, Any>())
        assertNull(R5McpWireAdmission.check(legacy, null, null, null))
        assertNull(R5McpWireAdmission.check(legacy, R5McpWireAdmission.LEGACY, null, null))
    }
}
