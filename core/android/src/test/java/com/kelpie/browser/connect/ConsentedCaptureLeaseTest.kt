package com.kelpie.browser.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentedCaptureLeaseTest {
    private var elapsed = 1_000L
    private val gate = ConsentedCaptureLease({ elapsed })
    private val current = ConsentedCaptureLease.Binding("tab-1", "document-1", "client-1", "attempt-1")
    private val original = Any()

    private fun consent(
        durationMs: Long = 20_000,
        maxFrames: Int = 2,
    ) = requireNotNull(gate.approveLocally(current, original, durationMs, maxFrames))

    private fun claim(
        token: String?,
        binding: ConsentedCaptureLease.Binding = current,
        view: Any = original,
        authenticated: Boolean = true,
        unlocked: Boolean = true,
        stopped: Boolean = false,
    ) = gate.claimFrame(token, binding, view, authenticated, unlocked, stopped)

    @Test fun noConsentMeansNoFrame() {
        assertFalse(claim(null))
        assertFalse(claim("forged"))
    }

    @Test fun localLeaseIsBoundedAndNotImplicitlyRenewed() {
        val token = consent()
        assertTrue(claim(token))
        assertTrue(claim(token))
        assertFalse(claim(token))
        assertFalse(claim(token))
        assertEquals(48, token.length)
    }

    @Test fun tabDocumentClientAndAttemptAreIndependentlyPinned() {
        val variations =
            listOf(
                current.copy(tabId = "tab-2"),
                current.copy(documentVersion = "document-2"),
                current.copy(clientId = "client-2"),
                current.copy(agentAttemptId = "attempt-2"),
            )
        variations.forEach {
            val token = consent()
            assertFalse(claim(token, binding = it))
            assertFalse(claim(token))
        }
    }

    @Test fun webViewIdentityAndSecretMustMatch() {
        val token = consent()
        assertFalse(claim(token, view = Any()))
        assertFalse(claim(token))

        val next = consent()
        assertFalse(claim(next.reversed()))
        assertFalse(claim(next))
    }

    @Test fun lockStopAndUnauthenticatedCallsRevokeTheLease() {
        val token1 = consent()
        assertFalse(claim(token1, authenticated = false))
        assertFalse(claim(token1))

        val token2 = consent()
        assertFalse(claim(token2, unlocked = false))
        assertFalse(claim(token2))

        val token3 = consent()
        assertFalse(claim(token3, stopped = true))
        assertFalse(claim(token3))
    }

    @Test fun expiryAtExactDeadlineAndExplicitRevokeFailClosed() {
        val token = consent(durationMs = 10)
        elapsed += 10
        assertFalse(claim(token))
        val replacement = consent()
        gate.revoke()
        assertFalse(claim(replacement))
    }

    @Test fun invalidConsentDoesNotCreateAuthority() {
        assertNull(gate.approveLocally(current.copy(clientId = ""), original))
        assertNull(gate.approveLocally(current, original, durationMs = 0))
        assertNull(gate.approveLocally(current, original, durationMs = 90_001))
        assertNull(gate.approveLocally(current, original, maxFrames = 0))
        assertNull(gate.approveLocally(current, original, maxFrames = 241))
        elapsed = -1
        assertNull(gate.approveLocally(current, original))
        assertFalse(claim("anything"))
    }

    @Test fun localReapprovalRevokesPriorToken() {
        val old = consent()
        val newer = consent()
        assertNotNull(newer)
        assertFalse(claim(old))
        // A stale request revokes the entire lease to stop replay, not to extend it.
        assertFalse(claim(newer))
    }
}
