package com.kelpie.browser.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kelpie.browser.agent.session.SessionConsentCore
import com.kelpie.browser.device.DeviceInfo
import com.kelpie.browser.handlers.DeviceHandler
import com.kelpie.browser.handlers.HandlerContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real native MCP dispatch without a WebView; synthetic authenticated computer and device metadata only. */
@RunWith(AndroidJUnit4::class)
class R5DeviceMetadataAvailabilityInstrumentedTest {
    @Test
    fun nativeDeviceMetadataSurvivesRendererUnavailabilityWhilePageOperationsRemainGated() =
        runBlocking {
            withFixture { fixture ->
                val direct = fixture.request("get-device-info", emptyMap())
                assertEquals(200, direct.status)
                assertDevice(direct.body)
                val native = nativeResult(fixture.request("mcp", extension("kelpie_get_device_info")))
                assertEquals(true, native["ok"])
                assertDevice(objectValue(native["value"]))
                assertEquals(0, fixture.rendererChecks)
                assertEquals(0, fixture.readinessChecks)

                for (request in listOf(extension("kelpie_get_viewport"), browser("tabs.list"))) {
                    val rejected = nativeResult(fixture.request("mcp", request))
                    assertEquals(false, rejected["ok"])
                    assertEquals("UNAVAILABLE", objectValue(rejected["error"])["code"])
                    assertEquals(false, objectValue(rejected["error"])["mayHaveCompleted"])
                    assertFalse(rejected.containsKey("value"))
                }
                assertTrue(fixture.rendererChecks > 0)
                assertTrue(fixture.readinessChecks > 0)
                assertEquals(0, fixture.tabReads)
            }
        }

    @Test
    fun deviceMetadataKeepsNativeProofStopLockRevocationAndDeliveryFences() =
        runBlocking {
            withFixture { fixture ->
                val request = extension("kelpie_get_device_info")
                // A key-shaped legacy or account identity cannot borrow native computer authority.
                for (principal in listOf(AuthDecision.Authenticated(fixture.key), AuthDecision.Authenticated(fixture.key, "b".repeat(64)))) {
                    assertDenied(fixture.request("mcp", request, principal))
                }
                fixture.authorityCurrent = false
                assertDenied(fixture.request("mcp", request))
                fixture.authorityCurrent = true
                fixture.consent.setDeviceLocked(true)
                assertDenied(fixture.request("mcp", request))
                fixture.consent.setDeviceLocked(false)
                fixture.consent.stop()
                assertDenied(fixture.request("mcp", request))
                fixture.restore()

                // The same exact metadata route still suppresses results invalidated during execution.
                fixture.router.register("get-device-info") {
                    fixture.consent.stop()
                    successResponse(mapOf("fixturePayload" to "must-not-escape"))
                }
                val stale = fixture.request("mcp", request)
                assertDenied(stale)
                assertFalse(stale.body.toString().contains("must-not-escape"))
                assertEquals(0, fixture.rendererChecks)
                assertEquals(0, fixture.readinessChecks)
            }
        }

    private class Fixture {
        val key = "a".repeat(64)
        val computer = AuthDecision.Authenticated(key, computerKey = key)
        val consent = SessionConsentCore({ 1_000L })
        var authorityCurrent = true
        var rendererChecks = 0
        var readinessChecks = 0
        var tabReads = 0
        private val ctx =
            HandlerContext().apply {
                motherPrivateAgentActive = {
                    rendererChecks++
                    true
                }
                motherGatewayAuthorization = {
                    readinessChecks++
                    false
                }
            }
        val router =
            Router(consent::snapshot).apply {
                motherR5BackgroundQa = true
                handlerContext = ctx
            }
        val gateway: BrowserGatewayBinding
        private val dispatcher = AuthenticatedRequestDispatcher(router)

        init {
            DeviceHandler(ctx, DeviceInfo("synthetic-device", "Synthetic fixture", "fixture", 640, 960, "127.0.0.1", 8420, "fixture-version", "fixture-build"), router).register()
            router.register("get-tabs") {
                tabReads++
                successResponse(mapOf("tabs" to emptyList<Any>()))
            }
            gateway = BrowserGatewayBinding(router, ctx).also { it.register() }
            restore()
        }

        fun restore() {
            checkNotNull(consent.restoreTrustedComputer(key, paused = false, deviceUnlocked = true))
        }

        suspend fun request(
            method: String,
            body: Map<String, Any?>,
            principal: AuthDecision.Authenticated = computer,
        ): AuthenticatedRequestDispatcher.Outcome =
            dispatcher.dispatch(principal, method, body, "synthetic-metadata", {
                if (authorityCurrent) principal else AuthDecision.Reject(401, "UNAUTHORIZED", "Synthetic revocation")
            }) { _, failure -> throw AssertionError("Unexpected dispatch failure", failure) }
    }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            block(fixture)
        } finally {
            fixture.consent.stop()
            fixture.gateway.close()
        }
    }

    private fun extension(name: String): Map<String, Any?> = tool("kelpie_extension", mapOf("name" to name, "arguments" to emptyMap<String, Any?>()))

    private fun browser(method: String): Map<String, Any?> = tool("kelpie_browser_control", mapOf("request" to mapOf("version" to 1, "method" to method, "params" to emptyMap<String, Any?>())))

    private fun tool(
        name: String,
        arguments: Map<String, Any?>,
    ): Map<String, Any?> = mapOf("jsonrpc" to "2.0", "id" to 1, "method" to "tools/call", "params" to mapOf("name" to name, "arguments" to arguments))

    private fun nativeResult(outcome: AuthenticatedRequestDispatcher.Outcome): Map<String, Any?> {
        assertEquals(200, outcome.status)
        val result = objectValue(outcome.body["result"])
        val text = (result["content"] as List<*>).map(::objectValue).single { it["type"] == "text" }["text"] as String
        val value = checkNotNull(HttpJson.parseJsonBody(text))
        assertEquals(value["ok"] != true, result["isError"])
        return value
    }

    private fun assertDevice(value: Map<*, *>) {
        assertEquals(true, value["success"])
        assertEquals("synthetic-device", objectValue(value["device"])["id"])
        assertEquals("fixture-version", objectValue(value["app"])["version"])
    }

    private fun assertDenied(outcome: AuthenticatedRequestDispatcher.Outcome) {
        assertEquals(403, outcome.status)
        assertFalse(outcome.body.containsKey("value"))
        assertFalse(outcome.body.toString().contains("synthetic-device"))
    }

    private fun objectValue(value: Any?): Map<*, *> = value as? Map<*, *> ?: throw AssertionError("Expected object")
}
