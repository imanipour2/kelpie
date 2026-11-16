package com.kelpie.browser.agent

import com.kelpie.browser.network.Router
import com.kelpie.browser.network.successResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No legacy router/gateway bypass may expand the R5 mobile observer grant. */
class R5ObservationRouterTest {
    @Test
    fun onlyReadOnlyRouteAndMcpEnvelopeAreAdmittedByIsolatedProfile() =
        runBlocking {
            val router =
                Router().apply {
                    consentOnlyQa = true
                    agentObservationQa = true
                }
            val reached = mutableSetOf<String>()
            router.register("agent-observe") {
                reached += "agent-observe"
                successResponse(mapOf("kind" to "tabs"))
            }
            router.register("get-tabs") {
                reached += "get-tabs"
                successResponse(mapOf("tabs" to "unprotected"))
            }
            router.register("screenshot") {
                reached += "screenshot"
                successResponse(mapOf("image" to "unprotected"))
            }
            for (route in listOf("get-tabs", "screenshot", "click", "fill", "navigate", "evaluate", "browser-control")) {
                for (gateway in listOf(false, true)) {
                    val (code, result) =
                        router.handle(
                            route,
                            mapOf("bypassRecordingGate" to true),
                            bypassRecordingGate = true,
                            fromGateway = gateway,
                        )
                    assertEquals(route, 403, code)
                    assertFalse(result["success"] == true)
                }
            }
            val (status, result) = router.handle("agent-observe", mapOf("kind" to "tabs"), fromGateway = true)
            assertEquals(200, status)
            assertTrue(result["success"] == true)
            assertEquals(setOf("agent-observe"), reached)
        }

    @Test
    fun oldR5ConsentOnlyProfileStillBlocksAnyObserverRoute() =
        runBlocking {
            val router = Router().apply { consentOnlyQa = true }
            router.register("agent-observe") { successResponse() }
            val (status, result) = router.handle("agent-observe", emptyMap())
            assertEquals(403, status)
            assertFalse(result["success"] == true)
        }
}
