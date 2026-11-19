package com.kelpie.browser.network

import com.kelpie.browser.agent.session.SessionConsentCore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5RemoteOperationPolicyTest {
    @Test
    fun nativeGrantAndGatewayFlagsNeverAuthorizeRawSecretExports() =
        runBlocking {
            val consent = SessionConsentCore({ 0L })
            val principal = AuthDecision.Authenticated("verified-owner")
            val pending = consent.requestNativeConsent(principal.clientId)!!
            val active = consent.approveFromNativeUi(pending.promptId, deviceUnlocked = true)!!
            val router = Router(consent::snapshot).apply { motherR5BackgroundQa = true }
            var executed = false
            val exports =
                listOf(
                    "evaluate",
                    "evaluate-js",
                    "browser-control-evaluate",
                    "play-script",
                    "get-cookies",
                    "set-cookie",
                    "set-cookies",
                    "copy-cookies",
                    "delete-cookies",
                    "get-storage",
                    "set-storage",
                    "clear-storage",
                    "get-clipboard",
                    "set-clipboard",
                    "get-console-messages",
                    "get-js-errors",
                    "network-list",
                    "network-detail",
                    "network-current",
                    "get-websockets",
                    "get-websocket-messages",
                    "get-dom",
                    "query-selector-all",
                    "get-attributes",
                    "get-mutations",
                    "capture-session-frame",
                    "debug-screens",
                    "click-annotation",
                    "fill-annotation",
                    "unknown-future-export",
                )
            exports.forEach { method ->
                router.register(method) {
                    executed = true
                    successResponse(mapOf("secret" to "fixture-private-content"))
                }
            }
            withContext(AuthenticatedClientContext.fromVerifiedDecision(principal, active)) {
                for (method in exports) {
                    for (gateway in listOf(false, true)) {
                        val (status, body) = router.handle(method, emptyMap(), bypassRecordingGate = true, fromGateway = gateway)
                        assertEquals(403, status)
                        assertEquals("NEEDS_HUMAN", (body["error"] as Map<*, *>)["code"])
                        assertFalse(body.toString().contains("fixture-private-content"))
                    }
                    assertFalse(R5RemoteOperationPolicy.permitsExtension(method))
                }
            }
            assertFalse(executed)
        }

    @Test
    fun unobservedSelectorActionsCannotEnterThroughLegacyHttpOrExtension() =
        runBlocking {
            val router = Router().apply { motherR5BackgroundQa = true }
            for (method in listOf("click", "type", "fill", "tap", "scroll", "press-key", "select-option", "check", "uncheck")) {
                router.register(method) { error("Unobserved action must never execute") }
                assertEquals(403, router.handle(method, emptyMap(), bypassRecordingGate = true).first)
                assertFalse(R5RemoteOperationPolicy.permitsExtension(method))
                assertFalse(R5RemoteOperationPolicy.permitsGatewayRequest(method))
            }
            for (method in listOf("tab.click", "tab.type", "tab.snapshot", "page.scroll", "tabs.select")) {
                assertTrue(R5RemoteOperationPolicy.permitsGatewayRequest(method))
            }
            assertTrue(R5RemoteOperationPolicy.permitsNativeCapability("click"))
            assertTrue(R5RemoteOperationPolicy.permitsNativeCapability("type"))
        }
}
