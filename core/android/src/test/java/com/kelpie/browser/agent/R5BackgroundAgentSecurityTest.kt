package com.kelpie.browser.agent

import com.kelpie.browser.agent.admission.AgentAdmissionPolicy
import com.kelpie.browser.agent.contract.AdmissionCode
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.agent.contract.Operation
import com.kelpie.browser.agent.execution.R5AgentDeliveryGuard
import com.kelpie.browser.agent.session.SessionConsentCore
import com.kelpie.browser.capture.R5FrameNonce
import com.kelpie.browser.network.Router
import com.kelpie.browser.network.successResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5BackgroundAgentSecurityTest {
    @Test
    fun appPrivateFrameNonceDoesNotRepeatWithinOneLimitedSession() {
        val seen = (1..R5FrameNonce.MAX_FRAMES).map { R5FrameNonce.color(0x123456, it) }
        assertEquals(R5FrameNonce.MAX_FRAMES, seen.toSet().size)
        assertTrue(R5FrameNonce.matches(seen[19], seen[19]))
        assertFalse(R5FrameNonce.matches(seen[19], seen[18]))
    }

    @Test
    fun commandAdmissionDeniesUnsafeScrollAndInputWithoutBypassingNativeGrant() {
        val consent = SessionConsentCore({ 100L })
        val pending = consent.requestNativeConsent("paired-a")!!
        val active = consent.approveFromNativeUi(pending.promptId, true)!!
        val base =
            AgentCommand(
                commandId = "op-1",
                principalId = "paired-a",
                consentGeneration = active.generation,
                tabId = "tab",
                observedDocumentEpoch = "epoch",
                operation = Operation.SCROLL,
                scrollDeltaY = 100.0,
                clientSequence = 1,
            )
        assertEquals(AdmissionCode.ALLOWED, AgentAdmissionPolicy.check(active, base, "tab", "epoch").code)
        assertEquals(
            AdmissionCode.INVALID_TARGET,
            AgentAdmissionPolicy.check(active, base.copy(scrollDeltaY = 99999.0), "tab", "epoch").code,
        )
        assertEquals(
            AdmissionCode.WRONG_PRINCIPAL,
            AgentAdmissionPolicy.check(active, base.copy(principalId = "paired-b"), "tab", "epoch").code,
        )
        assertEquals(
            AdmissionCode.INVALID_TARGET,
            AgentAdmissionPolicy.check(active, base.copy(operation = Operation.FILL), "tab", "epoch").code,
        )
        consent.setDeviceLocked(true)
        assertEquals(AdmissionCode.LOCKED, AgentAdmissionPolicy.check(consent.snapshot(), base, "tab", "epoch").code)
        consent.stop()
        assertEquals(AdmissionCode.NO_ACTIVE_SESSION, AgentAdmissionPolicy.check(consent.snapshot(), base, "tab", "epoch").code)
    }

    @Test
    fun fullAgentQaAcceptsOnlyNewGuardedRoutesEvenWithGatewayBypassFlags() =
        runBlocking {
            val router =
                Router().apply {
                    consentOnlyQa = true
                    backgroundAgentQa = true
                }
            val reached = mutableSetOf<String>()
            for (method in listOf("navigate", "click", "fill", "screenshot", "browser-control", "evaluate", "capture-session-frame")) {
                router.register(method) {
                    reached += method
                    successResponse()
                }
                for (fromGateway in listOf(false, true)) {
                    val (status, body) = router.handle(method, emptyMap(), bypassRecordingGate = true, fromGateway = fromGateway)
                    assertEquals(403, status)
                    assertFalse(body["success"] == true)
                }
            }
            router.register("agent-runtime-status") {
                reached += "agent-runtime-status"
                successResponse()
            }
            router.register("agent-capture-capabilities") {
                reached += "agent-capture-capabilities"
                successResponse()
            }
            val (status, body) = router.handle("agent-runtime-status", emptyMap())
            assertEquals(200, status)
            assertTrue(body["success"] == true)
            val (capsStatus, capsBody) = router.handle("agent-capture-capabilities", emptyMap())
            assertEquals(200, capsStatus)
            assertTrue(capsBody["success"] == true)
            assertEquals(setOf("agent-runtime-status", "agent-capture-capabilities"), reached)
        }

    @Test
    fun inFlightMcpOrHttpResultCannotOutliveItsNativeGrantGeneration() {
        val consent = SessionConsentCore({ 1L })
        val first = consent.approveFromNativeUi(consent.requestNativeConsent("owner")!!.promptId, true)!!
        assertTrue(R5AgentDeliveryGuard.permits("owner", first.generation, consent.snapshot()))
        assertFalse(R5AgentDeliveryGuard.permits("other", first.generation, consent.snapshot()))
        assertTrue(R5AgentDeliveryGuard.requiresGrant("agent-command", emptyMap()))
        assertTrue(R5AgentDeliveryGuard.requiresGrant("agent-capture-capabilities", emptyMap()))
        val mcp =
            mapOf<String, Any?>(
                "method" to "tools/call",
                "params" to mapOf("arguments" to mapOf("consentGeneration" to first.generation)),
            )
        assertTrue(R5AgentDeliveryGuard.requiresGrant("mcp", mcp))
        assertEquals(first.generation, R5AgentDeliveryGuard.expectedGeneration("mcp", mcp))
        consent.setDeviceLocked(true)
        assertFalse(R5AgentDeliveryGuard.permits("owner", first.generation, consent.snapshot()))
        consent.stop()
        assertFalse(R5AgentDeliveryGuard.permits("owner", first.generation, consent.snapshot()))
        val next = consent.approveFromNativeUi(consent.requestNativeConsent("owner")!!.promptId, true)!!
        assertFalse(R5AgentDeliveryGuard.permits("owner", first.generation, consent.snapshot()))
        assertTrue(R5AgentDeliveryGuard.permits("owner", next.generation, consent.snapshot()))
    }
}
