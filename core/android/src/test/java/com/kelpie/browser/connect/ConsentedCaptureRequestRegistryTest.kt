package com.kelpie.browser.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentedCaptureRequestRegistryTest {
    private var now = 1_000L
    private val subject = "paired-client"
    private val attempt = "attempt-73"
    private val doc = "doc-4"
    private val tab = "tab-1"
    private val view = Any()
    private val registry = ConsentedCaptureRequestRegistry({ now })

    private fun submit() = requireNotNull(registry.submitAuthenticated(subject, attempt, tab, doc, view))

    private fun approve(id: String) = registry.approveOnDevice(id, tab, doc, view, true)

    private fun claim(
        id: String,
        client: String? = subject,
        currentAttempt: String = attempt,
        currentDoc: String = doc,
        currentView: Any = view,
        currentTab: String = tab,
        unlocked: Boolean = true,
        stopped: Boolean = false,
    ) = registry.claimFrame(id, client, currentAttempt, currentTab, currentDoc, currentView, unlocked, stopped)

    @Test fun onlyAuthenticatedCallerCanOffer() {
        assertNull(registry.submitAuthenticated(null, attempt, tab, doc, view))
        assertNull(registry.submitAuthenticated("", attempt, tab, doc, view))
        assertNull(registry.prompt.value)
    }

    @Test fun pendingCannotBeOverwrittenOrApprovedByWrongTab() {
        val first = submit()
        assertEquals(first, registry.prompt.value)
        assertNull(registry.submitAuthenticated("other", attempt, tab, doc, view))
        assertFalse(registry.approveOnDevice(first.requestId, "wrong-tab", doc, view, true))
        assertNull(registry.prompt.value)
        assertFalse(claim(first.requestId))
    }

    @Test fun deviceApprovalPinsIdentityAndRetainsTokenOnlyInMemory() {
        val request = submit()
        assertTrue(approve(request.requestId))
        assertNull(registry.prompt.value)
        assertTrue(claim(request.requestId))
        assertFalse(claim(request.requestId, currentAttempt = "new-attempt"))
        assertFalse(claim(request.requestId))
    }

    @Test fun forgedRequestAndWrongClientFailClosed() {
        val request = submit()
        assertFalse(approve("fake"))
        assertFalse(claim(request.requestId))
        val again = submit()
        assertTrue(approve(again.requestId))
        assertFalse(claim(again.requestId, client = "attacker"))
        assertFalse(claim(again.requestId))
    }

    @Test fun newDocumentOrWebViewAndLockRevokeImmediately() {
        var req = submit()
        assertTrue(approve(req.requestId))
        assertFalse(claim(req.requestId, currentDoc = "changed"))
        req = submit()
        assertTrue(approve(req.requestId))
        assertFalse(claim(req.requestId, currentView = Any()))
        req = submit()
        assertTrue(approve(req.requestId))
        assertFalse(claim(req.requestId, unlocked = false))
    }

    @Test fun pendingExpiresAndLocalStopClearsAuthority() {
        val req = submit()
        now += ConsentedCaptureRequestRegistry.PENDING_TTL_MS
        assertFalse(approve(req.requestId))
        val newReq = submit()
        assertTrue(approve(newReq.requestId))
        registry.revoke()
        assertFalse(claim(newReq.requestId))
    }

    @Test fun localConsentIsExplicitAndSessionLimitStillApplies() {
        val req = submit()
        assertFalse(claim(req.requestId))
        // Claim before approval revoked this pending request; caller must retry.
        val second = submit()
        assertTrue(approve(second.requestId))
        now += ConsentedCaptureLease.MAX_LEASE_MS
        assertFalse(claim(second.requestId))
    }

    @Test fun finalFrameTriggersCleanupSignalOnlyAfterExactlyTwentyClaims() {
        val req = submit()
        assertTrue(approve(req.requestId))
        assertFalse(registry.finalFrameClaimed(req.requestId, subject))
        assertFalse(registry.finalFrameClaimed(req.requestId, "other-client"))
        repeat(ConsentedCaptureLease.DEFAULT_FRAME_LIMIT - 1) {
            assertTrue(claim(req.requestId))
            assertFalse(registry.finalFrameClaimed(req.requestId, subject))
        }
        assertTrue(claim(req.requestId))
        assertTrue(registry.finalFrameClaimed(req.requestId, subject))
        assertFalse(registry.finalFrameClaimed(req.requestId, "other-client"))
        registry.revoke()
        assertFalse(registry.finalFrameClaimed(req.requestId, subject))
        assertFalse(claim(req.requestId))
    }

    @Test fun injectionOrOverlongIdentifiersAreRejected() {
        assertNull(registry.submitAuthenticated(subject, "a\nb", tab, doc, view))
        assertNull(registry.submitAuthenticated(subject, "x".repeat(129), tab, doc, view))
        assertNull(registry.submitAuthenticated(subject, "", tab, doc, view))
        assertNotNull(submit())
    }

    @Test fun stoppedAndUnauthenticatedClaimAreRejected() {
        var req = submit()
        assertTrue(approve(req.requestId))
        assertFalse(claim(req.requestId, stopped = true))
        req = submit()
        assertTrue(approve(req.requestId))
        assertFalse(claim(req.requestId, client = null))
    }
}
