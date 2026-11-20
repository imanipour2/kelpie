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

    private class LoopbackComputerTrust(
        private val trustedKey: String? = null,
    ) : ComputerTrust {
        override fun request(
            operation: String,
            body: JsonObject,
        ): JsonObject =
            when (operation) {
                "authorize" -> {
                    val client = if (trustedKey != null && body["token"] == JsonPrimitive("fixture-computer-token")) trustedKey else "localhost"
                    JsonObject(mapOf("clientId" to JsonPrimitive(client)))
                }
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
    fun loopbackComputerBearerRetainsItsNativeTrustDiscriminator() {
        val key = "a".repeat(64)
        val store = PairingStore(File(temp, "pairings.json"), LoopbackComputerTrust(key))
        val auth = AuthMiddleware(store, r5PrivateMcpQa = true, r5PrivateMcpWire = false)
        for (path in listOf("/mcp", "/v1/browser-control", "/v1/agent-session-request")) {
            assertEquals(AuthDecision.Authenticated(key, key), auth.evaluate(request(path, "fixture-computer-token")))
        }
        val legacy = store.startPairing(key, "legacy fixture", "127.0.0.1").request!!
        val token = store.approve(legacy.requestId, persist = true)!!.first
        assertEquals(AuthDecision.Authenticated(key), auth.evaluate(request("/mcp", token)))
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
            assertEquals(null, verified.computerKey)

            // Public discovery remains separate; protected metadata must not
            // turn an anonymous or invalid bearer into a localhost identity.
            val localOnly = r5.evaluate(request(path))
            assertEquals(401, (localOnly as AuthDecision.Reject).status)
            val badBearer = r5.evaluate(request(path, "not-a-valid-token"))
            assertEquals(401, (badBearer as AuthDecision.Reject).status)
        }
    }

    @Test
    fun verifiedBearerCannotAuthorizeBrowserOriginOrCrossSiteRequests() {
        val store = PairingStore(File(temp, "pairings.json"), LoopbackComputerTrust())
        val started = store.startPairing("paired-chatgpt", "ChatGPT", "127.0.0.1")
        val token = store.approve(started.request!!.requestId, persist = false)!!.first
        val auth = AuthMiddleware(store, r5PrivateMcpQa = true)
        for (path in listOf("/mcp", "/v1/agent-session-request", "/v1/screenshot", "/v1/browser-control")) {
            val valid = request(path, token)
            for (extra in listOf("origin" to listOf("https://untrusted.invalid"), "sec-fetch-site" to listOf("cross-site"))) {
                val result = auth.evaluate(valid.copy(headers = valid.headers + extra))
                assertEquals(403, (result as AuthDecision.Reject).status)
            }
            assertEquals(401, (auth.evaluate(request(path, "invalid")) as AuthDecision.Reject).status)
        }
    }

    @Test
    fun publicLivenessAndDiscoveryDoNotGrantAuthenticatedMetadata() {
        val auth = AuthMiddleware(PairingStore(File(temp, "pairings.json"), LoopbackComputerTrust()), r5PrivateMcpQa = true)
        for (path in listOf("/health", "/v1/get-device-info")) {
            assertEquals(AuthDecision.Unauthenticated, auth.evaluate(request(path)))
        }
    }

    @Test
    fun motherAcceptsItsNativeGatewayAdvertisedProtocolWhilePrivateBridgeKeepsItsOwnVersions() {
        val store = PairingStore(File(temp, "pairings.json"), LoopbackComputerTrust())
        val pending = store.startPairing("paired-chatgpt", "ChatGPT", "127.0.0.1").request!!
        val token = store.approve(pending.requestId, persist = false)!!.first
        for (path in listOf("/mcp", "/v1/mcp")) {
            val base = request(path, token)
            val mother = AuthMiddleware(store, r5PrivateMcpQa = true, r5PrivateMcpWire = false)
            val bridge = AuthMiddleware(store, r5PrivateMcpQa = true, r5PrivateMcpWire = true)
            for (version in listOf("2025-03-26", "2025-06-18")) {
                val wire = base.copy(headers = base.headers + ("mcp-protocol-version" to listOf(version)))
                assertTrue(mother.evaluate(wire) is AuthDecision.Authenticated)
                if (path == "/mcp") {
                    assertEquals(400, (bridge.evaluate(wire) as AuthDecision.Reject).status)
                } else {
                    assertTrue(bridge.evaluate(wire) is AuthDecision.Authenticated)
                }
            }
            for (version in R5McpWireAdmission.SUPPORTED) {
                val wire = base.copy(headers = base.headers + ("mcp-protocol-version" to listOf(version)))
                if (path == "/mcp") {
                    assertTrue(bridge.evaluate(wire) is AuthDecision.Authenticated)
                } else {
                    assertEquals(400, (bridge.evaluate(wire) as AuthDecision.Reject).status)
                }
                assertEquals(400, (mother.evaluate(wire) as AuthDecision.Reject).status)
            }
        }
    }

    @Test
    fun legacyLocalControlIsUnaffectedOutsideTheIsolatedR5QaProfile() {
        val store = PairingStore(File(temp, "pairings.json"), LoopbackComputerTrust())
        val local = AuthMiddleware(store, r5PrivateMcpQa = false).evaluate(request("/v1/get-browser-state"))
        assertEquals("localhost", (local as AuthDecision.Authenticated).clientId)
    }
}
