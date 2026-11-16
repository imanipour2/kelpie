package com.kelpie.browser.agent

import com.kelpie.browser.agent.admission.AgentAdmissionPolicy
import com.kelpie.browser.agent.contract.AdmissionCode
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.agent.contract.Operation
import com.kelpie.browser.agent.session.SessionConsentCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentConsentPolicyTest {
    @Test
    fun consentIsLocalAndBoundToPrincipal() {
        var clock = 10L
        val session = SessionConsentCore({ clock })
        assertNull(session.approveFromNativeUi("invented", deviceUnlocked = true))
        assertNull(session.requestNativeConsent(null))
        val pending = session.requestNativeConsent("paired-chatgpt")!!
        assertNull(session.requestNativeConsent("other-principal"))
        assertNull(session.approveFromNativeUi("stale", deviceUnlocked = true))
        val active = session.approveFromNativeUi(pending.promptId, deviceUnlocked = true)
        assertNotNull(active)
        assertEquals("paired-chatgpt", active!!.principalId)
        clock += 12_000_000L
        assertEquals(active, session.snapshot()) // Approval lasts for process session, not 90 s.
    }

    @Test
    fun initialLocalPromptCanBeApprovedAfterFortySecondsOnce() {
        var clock = 100L
        val session = SessionConsentCore({ clock })
        val pending = session.requestNativeConsent("paired")!!
        clock += 40_000L
        assertNotNull(session.approveFromNativeUi(pending.promptId, true))
        clock += 3_600_000L
        assertTrue(session.snapshot() is SessionConsentCore.State.Active)
        assertNull(session.requestNativeConsent("paired"))
    }

    @Test
    fun stopInvalidatesGenerationAndNewConsentIsRequired() {
        val session = SessionConsentCore({ 0L })
        val first = session.requestNativeConsent("paired")!!
        val active = session.approveFromNativeUi(first.promptId, true)!!
        session.stop()
        assertEquals(SessionConsentCore.State.Off, session.snapshot())
        assertNull(session.approveFromNativeUi(first.promptId, true))
        val second = session.requestNativeConsent("paired")!!
        assertNotEquals(first.promptId, second.promptId)
        assertNotEquals(active.generation, session.approveFromNativeUi(second.promptId, true)!!.generation)
    }

    @Test
    fun expiredAndLockedApprovalsFailClosed() {
        var clock = 100L
        val session = SessionConsentCore({ clock })
        val pending = session.requestNativeConsent("paired")!!
        assertNull(session.approveFromNativeUi(pending.promptId, deviceUnlocked = false))
        val second = session.requestNativeConsent("paired")!!
        clock += SessionConsentCore.LOCAL_PROMPT_TTL_MS
        assertNull(session.approveFromNativeUi(second.promptId, deviceUnlocked = true))
        assertEquals(SessionConsentCore.State.Off, session.snapshot())
    }

    @Test
    fun policyRejectsDifferentPrincipalStaleDocumentAndSensitiveWork() {
        val session = SessionConsentCore({ 0L })
        val active = session.approveFromNativeUi(session.requestNativeConsent("paired")!!.promptId, true)!!

        fun check(
            op: Operation,
            principal: String = active.principalId,
            epoch: String = "e1",
            generation: String = active.generation,
            url: String? = null,
        ) = AgentAdmissionPolicy
            .check(
                session.snapshot(),
                AgentCommand("id", principal, generation, "t1", epoch, op, url),
                "t1",
                "e1",
            ).code
        assertEquals(AdmissionCode.ALLOWED, check(Operation.READ_DOM))
        assertEquals(AdmissionCode.ALLOWED, check(Operation.CLICK))
        assertEquals(AdmissionCode.ALLOWED, check(Operation.FILL))
        assertEquals(AdmissionCode.WRONG_PRINCIPAL, check(Operation.READ_DOM, principal = "foreign"))
        assertEquals(AdmissionCode.WRONG_GENERATION, check(Operation.READ_DOM, generation = "old"))
        assertEquals(AdmissionCode.STALE_DOCUMENT, check(Operation.CLICK, epoch = "stale"))
        assertEquals(AdmissionCode.SENSITIVE_ACTION_REQUIRES_APPROVAL, check(Operation.PAYMENT))
        assertEquals(AdmissionCode.SENSITIVE_ACTION_REQUIRES_APPROVAL, check(Operation.PUBLISH))
        assertEquals(AdmissionCode.SENSITIVE_ACTION_REQUIRES_APPROVAL, check(Operation.EXPORT_CREDENTIALS))
        assertEquals(AdmissionCode.ALLOWED, check(Operation.NAVIGATE, url = "https://example.com"))
        assertEquals(AdmissionCode.ALLOWED, check(Operation.NAVIGATE, url = "https://example.org"))
        assertEquals(AdmissionCode.UNSAFE_URL, check(Operation.NAVIGATE, url = "file:///tmp/a"))
        assertEquals(AdmissionCode.UNSAFE_URL, check(Operation.NAVIGATE, url = "javascript:alert(1)"))
        assertEquals(AdmissionCode.UNSAFE_URL, check(Operation.NAVIGATE, url = "https://a:b@host.test"))
        session.setDeviceLocked(true)
        assertEquals(AdmissionCode.LOCKED, check(Operation.SCREENSHOT))
        session.setDeviceLocked(false)
        assertEquals(AdmissionCode.ALLOWED, check(Operation.SCREENSHOT))
        session.stop()
        assertEquals(AdmissionCode.NO_ACTIVE_SESSION, check(Operation.READ_DOM))
    }

    @Test
    fun lockThenUnlockRetainsConsentButInvalidatesInFlightSafetyEpoch() {
        val session = SessionConsentCore({ 10L })
        val active = session.approveFromNativeUi(session.requestNativeConsent("paired")!!.promptId, true)!!
        session.setDeviceLocked(true)
        session.setDeviceLocked(false)
        val resumed = session.snapshot() as SessionConsentCore.State.Active
        assertEquals(active.generation, resumed.generation)
        assertEquals(active.principalId, resumed.principalId)
        assertEquals(2L, resumed.safetyEpoch)
        assertNotEquals(active, resumed)
    }

    @Test
    fun noRemoteSessionRenewalByRepeatedPrompt() {
        val session = SessionConsentCore({ 10L })
        val first = session.requestNativeConsent("paired")!!
        val active = session.approveFromNativeUi(first.promptId, true)!!
        assertNull(session.requestNativeConsent("paired"))
        assertEquals(active, session.snapshot())
        assertTrue(active.generation.isNotBlank())
    }
    @Test
    fun fiftyApprovedCoreCyclesRequireUniqueGenerationAndExplicitStop() {
        val session = SessionConsentCore({ 100L })
        val generations = mutableSetOf<String>()
        repeat(50) {
            assertEquals(SessionConsentCore.State.Off, session.snapshot())
            val pending = session.requestNativeConsent("paired")!!
            assertNull(session.requestNativeConsent("other-client"))
            assertNull(session.approveFromNativeUi("forged-prompt", true))
            val active = session.approveFromNativeUi(pending.promptId, true)!!
            assertTrue(generations.add(active.generation))
            assertEquals("paired", active.principalId)
            session.setDeviceLocked(true)
            assertTrue((session.snapshot() as SessionConsentCore.State.Active).locked)
            session.setDeviceLocked(false)
            val resumed = session.snapshot() as SessionConsentCore.State.Active
            assertEquals(active.generation, resumed.generation)
            assertEquals(2L, resumed.safetyEpoch)
            session.stop()
            assertEquals(SessionConsentCore.State.Off, session.snapshot())
            assertNull(session.approveFromNativeUi(pending.promptId, true))
        }
        assertEquals(50, generations.size)
    }

}
