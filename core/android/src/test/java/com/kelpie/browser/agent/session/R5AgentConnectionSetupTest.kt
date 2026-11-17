package com.kelpie.browser.agent.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R5AgentConnectionSetupTest {
    @Test
    fun validHttpsMcpEndpointIsNormalizedWithoutSecrets() {
        assertEquals(
            "https://secure.example.com/mcp",
            R5AgentConnectionSetup.safeEndpoint(" https://SECURE.EXAMPLE.COM/mcp/ "),
        )
        assertEquals(
            "https://secure.example.com/mcp",
            R5AgentConnectionSetup.safeEndpoint("https://secure.example.com:443/mcp"),
        )
    }

    @Test
    fun untrustedOrLeakyEndpointsCannotEnterSetupClipboard() {
        for (
            value in
            listOf(
                "http://secure.example.com/mcp",
                "https://127.0.0.1/mcp",
                "https://localhost/mcp",
                "https://dev.local/mcp",
                "https://internal.example.internal/mcp",
                "https://user:pass@secure.example.com/mcp",
                "https://secure.example.com/mcp?access_token=secret",
                "https://secure.example.com/mcp#secret",
                "https://secure.example.com/other",
                "https://secure.example.com:8443/mcp",
                "javascript:alert(1)",
                "https://secure.example.com/mcp\nAuthorization: Bearer test",
                "https://secure.example.com/mcp ",
            )
        ) {
            if (value == "https://secure.example.com/mcp ") continue // Trimmed form is valid.
            assertNull("Should reject: " + value, R5AgentConnectionSetup.safeEndpoint(value))
        }
    }

    @Test
    fun privateChatGptTunnelNeedsNoEndpointOrSecret() {
        val instructions = R5AgentConnectionSetup.chatGptTunnelInstructions()
        assertTrue(instructions.contains("Secure MCP Tunnel"))
        assertTrue(instructions.contains("tunnel_id"))
        assertTrue(instructions.contains("native Kelpie"))
        assertFalse(instructions.contains("Authorization: Bearer"))
        assertFalse(instructions.contains("CONNECTED"))
    }

    @Test
    fun providerTemplatesHaveRealGuidanceButNeverAuthorize() {
        val url = "https://secure.example.com/mcp"
        for (provider in R5AgentProvider.entries) {
            val text = R5AgentConnectionSetup.setupInstructions(provider, url)
            assertNotNull(text)
            assertTrue(text!!.contains(url))
            assertTrue(text.contains("approve its real native Kelpie session"))
            assertFalse(text.contains("CONNECTED"))
            assertFalse(text.contains("Authorization: Bearer"))
        }
        assertNull(R5AgentConnectionSetup.setupInstructions(R5AgentProvider.CHATGPT, "http://bad.test/mcp"))
    }
}
