package com.kelpie.browser.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class R5PrivateAgentAuthenticationTest {
    private val temp = Files.createTempDirectory("r5-agent-auth-test").toFile()

    private class LoopbackComputerTrust : ComputerTrust {
        override fun request(
            operation: String,
            body: JsonObject,
        ): JsonObject =
            when (operation) {
                "authorize" -> JsonObject(mapOf("clientId" to JsonPrimitive("localhost")))
                "safe-host" -> JsonObject(mapOf("safe" to JsonPrimitive(true)))
                else -> JsonObject(emptyMap())
            }
    }

    @After
    fun cleanup() {
        temp.deleteRecursively()
    }

    private fun request(
        path: String,
        bearer: String? = null,
    ): ParsedRequest {
        val headers =
            mutableMapOf(
                "host" to listOf("localhost"),
                "content-type" to listOf("application/json"),
            )
        if (bearer != null) headers["authorization"] = listOf("Bearer $bearer")
        return ParsedRequest(
            method = "POST",
            path = path,
            headers = headers,
            hasTransferEncoding = false,
            sourceAddress = "127.0.0.1",
        )
    }

    @Test
    fun r5PrivateRoutesUseUniquePairedBearerNotBroadLocalhostPrincipal() {
        val store = PairingStore(File(temp, "pairings.json"), LoopbackComputerTrust())
        val started = store.startPairing("paired-chatgpt", "ChatGPT", "127.0.0.1")
        val issued = store.approve(started.request!!.requestId, persist = true)
        assertTrue(issued != null)
        val token = issued!!.first

        val r5 = AuthMiddleware(store, r5PrivateMcpQa = true)
        for (path in listOf("/mcp", "/v1/mcp", "/v1/agent-session-request", "/v1/agent-tabs")) {
            val verified = r5.evaluate(request(path, token))
            assertTrue(verified is AuthDecision.Authenticated)
            assertEquals("paired-chatgpt", (verified as AuthDecision.Authenticated).clientId)

            // Metadata discovery may still identify generic localhost, but
            // the owner-session coordinator must reject that identity.
            val localOnly = r5.evaluate(request(path))
            assertEquals("localhost", (localOnly as AuthDecision.Authenticated).clientId)
            val badBearer = r5.evaluate(request(path, "not-a-valid-token"))
            assertEquals("localhost", (badBearer as AuthDecision.Authenticated).clientId)
        }
    }

    @Test
    fun legacyLocalControlIsUnaffectedOutsideTheIsolatedR5QaProfile() {
        val store = PairingStore(File(temp, "pairings.json"), LoopbackComputerTrust())
        val local = AuthMiddleware(store, r5PrivateMcpQa = false).evaluate(request("/v1/get-browser-state"))
        assertEquals("localhost", (local as AuthDecision.Authenticated).clientId)
    }
}
