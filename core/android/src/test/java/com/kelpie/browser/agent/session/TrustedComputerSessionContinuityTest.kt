package com.kelpie.browser.agent.session

import com.kelpie.browser.network.R5RequestAuthorization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runtime continuity after the middleware has proved an already approved computer key. */
class TrustedComputerSessionContinuityTest {
    private val computer = "a".repeat(64)
    private val otherComputer = "b".repeat(64)

    @Test
    fun repeatedRequestsAndRecentClientExpiryKeepTheSameExecutionEpoch() {
        var clock = 1_000L
        val session = SessionConsentCore({ clock })
        val admitted = session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!

        // The recent-client UI cache expires after 120 seconds; durable key trust does not.
        clock += 120_001L
        repeat(3) {
            assertEquals(admitted, session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true))
        }
        clock += 86_400_000L
        assertEquals(admitted, session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true))
        assertTrue(R5RequestAuthorization.permits(computer, admitted, session.snapshot(), computerKey = computer))
        assertNull(session.requestNativeConsent(computer))
    }

    @Test
    fun ordinaryPrincipalNamesCannotBeUsedAsVerifiedComputerKeys() {
        val invalidKeys =
            listOf(
                "",
                "localhost",
                "paired-agent",
                "a".repeat(63),
                "a".repeat(65),
                "g".repeat(64),
                "A".repeat(64),
                computer + "\n",
            )
        for (key in invalidKeys) {
            val session = SessionConsentCore({ 10L })
            assertNull(session.restoreTrustedComputer(key, paused = false, deviceUnlocked = true))
            assertEquals(SessionConsentCore.State.Off, session.snapshot())
        }
    }

    @Test
    fun lockedDeviceCannotStartANewExecutionSession() {
        val session = SessionConsentCore({ 10L })
        assertNull(session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = false))
        assertEquals(SessionConsentCore.State.Off, session.snapshot())
        assertNotNull(session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true))
    }

    @Test
    fun lockRoundTripRetainsTrustButNeverRevivesAnAdmittedRequest() {
        val session = SessionConsentCore({ 10L })
        val admitted = session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!
        session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = false)
        val locked = session.snapshot() as SessionConsentCore.State.Active
        assertTrue(locked.locked)
        assertEquals(admitted.generation, locked.generation)
        assertFalse(R5RequestAuthorization.permits(computer, admitted, locked, computerKey = computer))

        val resumed = session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!
        assertFalse(resumed.locked)
        assertEquals(admitted.generation, resumed.generation)
        assertEquals(admitted.safetyEpoch + 2L, resumed.safetyEpoch)
        assertFalse(R5RequestAuthorization.permits(computer, admitted, resumed, computerKey = computer))
        assertTrue(R5RequestAuthorization.permits(computer, resumed, resumed, computerKey = computer))
    }

    @Test
    fun explicitStopCannotBeUndoneByTheNextAuthenticatedRequest() {
        val session = SessionConsentCore({ 10L })
        val admitted = session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!
        session.stop()

        // The Android owner-STOP adapter supplies the persisted paused state.
        repeat(3) {
            assertNull(session.restoreTrustedComputer(computer, paused = true, deviceUnlocked = true))
            assertEquals(SessionConsentCore.State.Off, session.snapshot())
        }
        val resumed = session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!
        assertNotEquals(admitted.generation, resumed.generation)
        assertFalse(R5RequestAuthorization.permits(computer, admitted, resumed, computerKey = computer))
        assertTrue(R5RequestAuthorization.permits(computer, resumed, resumed, computerKey = computer))
    }

    @Test
    fun restorationCannotReplaceAnotherOwnerOrPendingNativeDecision() {
        val session = SessionConsentCore({ 10L })
        val pending = session.requestNativeConsent("temporary-account-client")!!
        assertNull(session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true))
        assertEquals(pending, session.snapshot())

        val temporary = session.approveFromNativeUi(pending.promptId, deviceUnlocked = true)!!
        assertNull(session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true))
        assertEquals(temporary, session.snapshot())

        session.stop()
        val owner = session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!
        assertNull(session.restoreTrustedComputer(otherComputer, paused = false, deviceUnlocked = true))
        assertEquals(owner, session.snapshot())
        assertFalse(R5RequestAuthorization.permits(otherComputer, owner, session.snapshot(), computerKey = otherComputer))
    }

    @Test
    fun rendererFailureRetiresOldWorkWithoutCreatingAnotherApprovalPrompt() {
        val session = SessionConsentCore({ 10L })
        val admitted = session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!
        assertTrue(session.invalidateRuntime())
        val recovering = session.snapshot() as SessionConsentCore.State.Active
        assertEquals(admitted.principalId, recovering.principalId)
        assertEquals(admitted.locked, recovering.locked)
        assertNotEquals(admitted.generation, recovering.generation)
        assertEquals(admitted.safetyEpoch + 1L, recovering.safetyEpoch)
        assertFalse(R5RequestAuthorization.permits(computer, admitted, recovering, computerKey = computer))
        assertEquals(recovering, session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true))
        assertNull(session.requestNativeConsent(computer))
        // Renderer readiness remains a separate execution gate, outside this trust/epoch core.
        assertTrue(R5RequestAuthorization.permits(computer, recovering, recovering, computerKey = computer))
    }

    @Test
    fun rendererFailureCannotUnlockTheDeviceOrGrantAnOffOrPendingSession() {
        val session = SessionConsentCore({ 10L })
        assertFalse(session.invalidateRuntime())
        assertEquals(SessionConsentCore.State.Off, session.snapshot())

        val pending = session.requestNativeConsent(computer)!!
        assertFalse(session.invalidateRuntime())
        assertEquals(pending, session.snapshot())

        val admitted = session.approveFromNativeUi(pending.promptId, deviceUnlocked = true)!!
        session.setDeviceLocked(true)
        assertTrue(session.invalidateRuntime())
        val locked = session.snapshot() as SessionConsentCore.State.Active
        assertTrue(locked.locked)
        assertFalse(R5RequestAuthorization.permits(computer, admitted, locked))
        assertFalse(R5RequestAuthorization.permits(computer, locked, locked))
    }

    @Test
    fun lateRendererFailureCannotRetireANewerLockOrExecutionEpoch() {
        val session = SessionConsentCore({ 10L })
        val oldTransfer = session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!
        session.setDeviceLocked(true)
        session.setDeviceLocked(false)
        val afterUnlock = session.snapshot() as SessionConsentCore.State.Active
        assertFalse(session.invalidateRuntime(oldTransfer))
        assertEquals(afterUnlock, session.snapshot())

        assertTrue(session.invalidateRuntime(afterUnlock))
        val recovered = session.snapshot() as SessionConsentCore.State.Active
        assertFalse(session.invalidateRuntime(afterUnlock))
        assertEquals(recovered, session.snapshot())

        session.stop()
        val resumed = session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!
        assertFalse(session.invalidateRuntime(recovered))
        assertEquals(resumed, session.snapshot())
    }

    @Test
    fun sameNamedLegacyBearerCannotBorrowTheTrustedComputerEpoch() {
        val session = SessionConsentCore({ 10L })
        val restored = session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!
        assertEquals(computer, restored.trustedComputerKey)
        assertFalse(R5RequestAuthorization.permits(computer, restored, restored))
        assertFalse(R5RequestAuthorization.permits(computer, restored, restored, computerKey = otherComputer))
        assertTrue(R5RequestAuthorization.permits(computer, restored, restored, computerKey = computer))
        assertFalse(R5RequestAuthorization.owns(computer, null, restored))
        assertTrue(R5RequestAuthorization.owns(computer, computer, restored))

        session.setDeviceLocked(true)
        val locked = session.snapshot() as SessionConsentCore.State.Active
        assertEquals(computer, locked.trustedComputerKey)
        assertFalse(R5RequestAuthorization.permits(computer, locked, locked, computerKey = computer))
        // Native owner status/STOP still work while locked; a namesake cannot own them.
        assertTrue(R5RequestAuthorization.owns(computer, computer, locked))
        assertFalse(R5RequestAuthorization.owns(computer, null, locked))
        session.setDeviceLocked(false)
        assertTrue(session.invalidateRuntime())
        val recovered = session.snapshot() as SessionConsentCore.State.Active
        assertEquals(computer, recovered.trustedComputerKey)
        assertFalse(R5RequestAuthorization.permits(computer, recovered, recovered))
        assertTrue(R5RequestAuthorization.permits(computer, recovered, recovered, computerKey = computer))
    }

    @Test
    fun verifiedComputerUpgradeRetiresTheSameNamedLegacyGrantWithoutAnotherPrompt() {
        val session = SessionConsentCore({ 10L })
        val pending = session.requestNativeConsent(computer)!!
        val legacy = session.approveFromNativeUi(pending.promptId, deviceUnlocked = true)!!
        assertNull(legacy.trustedComputerKey)
        assertTrue(R5RequestAuthorization.permits(computer, legacy, legacy))

        val upgraded = session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!
        assertEquals(computer, upgraded.principalId)
        assertEquals(computer, upgraded.trustedComputerKey)
        assertNotEquals(legacy.generation, upgraded.generation)
        assertTrue(upgraded.safetyEpoch > legacy.safetyEpoch)
        assertFalse(R5RequestAuthorization.permits(computer, legacy, upgraded))
        assertFalse(R5RequestAuthorization.permits(computer, upgraded, upgraded))
        assertTrue(R5RequestAuthorization.permits(computer, upgraded, upgraded, computerKey = computer))
        assertEquals(upgraded, session.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true))
        assertNull(session.requestNativeConsent(computer))
    }

    @Test
    fun processRecreationRestoresTheComputerWithANewExecutionEpoch() {
        val firstProcess = SessionConsentCore({ 10L })
        val prior = firstProcess.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!
        val restartedProcess = SessionConsentCore({ 20L })
        val restored = restartedProcess.restoreTrustedComputer(computer, paused = false, deviceUnlocked = true)!!
        assertEquals(prior.principalId, restored.principalId)
        assertNotEquals(prior.generation, restored.generation)
        assertFalse(R5RequestAuthorization.permits(computer, prior, restored, computerKey = computer))
        assertTrue(R5RequestAuthorization.permits(computer, restored, restored, computerKey = computer))

        val stoppedProcess = SessionConsentCore({ 30L })
        assertNull(stoppedProcess.restoreTrustedComputer(computer, paused = true, deviceUnlocked = true))
        assertEquals(SessionConsentCore.State.Off, stoppedProcess.snapshot())
    }
}
