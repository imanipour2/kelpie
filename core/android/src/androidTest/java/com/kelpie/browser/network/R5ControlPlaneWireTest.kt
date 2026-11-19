package com.kelpie.browser.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kelpie.browser.agent.session.SessionConsentCore
import com.kelpie.browser.device.DeviceInfo
import com.kelpie.browser.handlers.HandlerContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.UUID

/** Real Ktor/HTTP + native MCP gateway, using synthetic identities and no WebView. */
@RunWith(AndroidJUnit4::class)
class R5ControlPlaneWireTest {
    @Test
    fun nativeMetadataSurvivesStopAndLockWhileActionsAndUntrustedCallersAreDenied() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "r5-wire-${UUID.randomUUID()}").apply { mkdirs() }
        val trust =
            object : ComputerTrust {
                override fun request(
                    operation: String,
                    body: JsonObject,
                ): JsonObject =
                    when (operation) {
                        "normalize" -> JsonObject(mapOf("address" to JsonPrimitive("127.0.0.1")))
                        "safe-host" -> JsonObject(mapOf("safe" to JsonPrimitive(true)))
                        "authorize" -> JsonObject(mapOf("clientId" to JsonPrimitive("localhost")))
                        else -> JsonObject(emptyMap())
                    }
            }
        val store = PairingStore(File(folder, "pairings.json"), trust)
        val paired = store.startPairing("wire-fixture-agent", "Synthetic fixture", "127.0.0.1").request!!
        val token = store.approve(paired.requestId, persist = false)!!.first
        val consent = SessionConsentCore({ 1_000L })
        val router = Router(consent::snapshot).apply { motherR5BackgroundQa = true }
        var browserCalls = 0
        router.register("get-tabs") {
            browserCalls++
            successResponse(mapOf("tabs" to emptyList<Any>()))
        }
        val gateway = BrowserGatewayBinding(router, HandlerContext()).also { it.register() }
        val port = ServerSocket(0).use { it.localPort }
        val device = DeviceInfo("fixture", "fixture", "fixture", 1, 1, "127.0.0.1", port, "fixture", "fixture")
        val server = HTTPServer(port, router, context, store, device) {}

        fun post(
            path: String,
            method: String,
            bearer: String? = token,
            origin: Boolean = false,
        ): Pair<Int, Map<String, Any?>?> {
            val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
            return try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("MCP-Protocol-Version", "2025-06-18")
                if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
                if (origin) connection.setRequestProperty("Origin", "https://untrusted.invalid")
                val envelope = mutableMapOf<String, Any?>("jsonrpc" to "2.0", "method" to method)
                if (method != "notifications/initialized") envelope["id"] = 1
                val body = HttpJson.mapToJsonString(envelope).toByteArray()
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
                val status = connection.responseCode
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }
                status to responseText?.takeIf { it.isNotEmpty() }?.let(HttpJson::parseJsonBody)
            } finally {
                connection.disconnect()
            }
        }
        try {
            server.start()
            for (phase in 0..2) {
                if (phase == 1) {
                    val prompt = consent.requestNativeConsent("wire-fixture-agent")!!
                    consent.approveFromNativeUi(prompt.promptId, deviceUnlocked = true)
                    consent.setDeviceLocked(true)
                }
                if (phase == 2) consent.stop()
                for (path in listOf("/mcp", "/v1/mcp")) {
                    for (method in listOf("initialize", "ping", "tools/list", "notifications/initialized")) {
                        val result = post(path, method)
                        if (method == "notifications/initialized") {
                            assertEquals(202, result.first)
                            assertNull(result.second)
                        } else {
                            assertEquals(200, result.first)
                            assertNotNull(result.second?.get("result"))
                        }
                    }
                    assertEquals(403, post(path, "tools/call").first)
                    assertEquals(401, post(path, "initialize", bearer = null).first)
                    assertEquals(401, post(path, "initialize", bearer = "invalid-fixture").first)
                    assertEquals(403, post(path, "initialize", origin = true).first)
                }
            }
            assertEquals(0, browserCalls)
            store.revoke("wire-fixture-agent")
            assertEquals(401, post("/mcp", "initialize").first)
        } finally {
            store.revoke("wire-fixture-agent")
            server.stop()
            runBlocking { gateway.close() }
            assertFalse(server.isRunning)
            folder.deleteRecursively()
        }
    }
}
