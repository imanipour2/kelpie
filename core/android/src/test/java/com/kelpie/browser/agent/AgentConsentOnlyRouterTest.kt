package com.kelpie.browser.agent

import com.kelpie.browser.network.Router
import com.kelpie.browser.network.successResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The consent-only QA build must never expose legacy authenticated browser effects. */
class AgentConsentOnlyRouterTest {
    @Test
    fun legacyBrowserAndScreenshotRoutesAreDeniedEvenWithGatewayBypassFlags() =
        runBlocking {
            val router = Router().apply { consentOnlyQa = true }
            var executed = false
            router.register("screenshot") {
                executed = true
                successResponse(mapOf("screenshot" to "should-not-exist"))
            }
            for (method in listOf("screenshot", "browser-control", "mcp", "click", "fill", "navigate")) {
                val (status, result) =
                    router.handle(method, emptyMap(), bypassRecordingGate = true, fromGateway = true)
                assertEquals(403, status)
                assertFalse(result["success"] == true)
            }
            assertFalse(executed)
        }

    @Test
    fun consentRoutesStayReachableButStillRequireOwnAuthenticatedHandler() =
        runBlocking {
            val router = Router().apply { consentOnlyQa = true }
            router.register("agent-session-status") { successResponse(mapOf("status" to "OFF")) }
            val (status, data) = router.handle("agent-session-status", emptyMap())
            assertEquals(200, status)
            assertTrue(data["success"] == true)
            assertEquals("OFF", data["status"])
        }

    @Test
    fun normalBrowserProfileRetainsItsExistingRoutes() =
        runBlocking {
            val router = Router()
            router.register("get-tabs") { successResponse(mapOf("tabs" to emptyList<String>())) }
            val (status, data) = router.handle("get-tabs", emptyMap())
            assertEquals(200, status)
            assertTrue(data["success"] == true)
        }
}
