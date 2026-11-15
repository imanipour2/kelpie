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
        assertTrue(claim(request.requestId))
    }

    @Test fun forgedRequestAndWrongClientFailClosed() {
        val request = submit()
        assertFalse(approve("fake"))
        // A stale local dialog callback must not destroy a newer pending approval.
        assertTrue(approve(request.requestId))
        assertTrue(registry.revokeOwned(request.requestId, subject))
        val again = submit()
        assertTrue(approve(again.requestId))
        assertFalse(claim(again.requestId, client = "attacker"))
        assertTrue(claim(again.requestId))
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
        // Unapproved frame requests must not destroy the legitimate pending consent.
        assertTrue(approve(req.requestId))
        now += ConsentedCaptureLease.MAX_LEASE_MS
        assertFalse(claim(req.requestId))
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
        assertTrue(claim(req.requestId))
    }

    @Test fun wrongClientCannotRevokeOwner() {
        val req = submit()
        assertTrue(approve(req.requestId))
        assertFalse(claim(req.requestId, client = "other-principal"))
        assertEquals("APPROVED", registry.status(req.requestId, subject))
        assertTrue(claim(req.requestId))
    }

    @Test fun wrongRequestCannotStopOwner() {
        val req = submit()
        assertTrue(approve(req.requestId))
        assertFalse(registry.revokeOwned("wrong-id", subject))
        assertFalse(registry.revokeOwned(req.requestId, "other-principal"))
        assertEquals("APPROVED", registry.status(req.requestId, subject))
        assertTrue(claim(req.requestId))
    }

    @Test fun staleAttemptCannotAffectNewGeneration() {
        val old = submit()
        assertTrue(approve(old.requestId))
        assertTrue(registry.revokeOwned(old.requestId, subject))
        val fresh = requireNotNull(registry.submitAuthenticated(subject, "attempt-74", tab, doc, view))
        assertTrue(approve(fresh.requestId))
        assertFalse(claim(old.requestId, currentAttempt = attempt))
        assertFalse(claim(fresh.requestId, currentAttempt = attempt))
        assertTrue(claim(fresh.requestId, currentAttempt = "attempt-74"))
    }

    @Test fun authenticatedClientSwapDenied() {
        val req = submit()
        assertTrue(approve(req.requestId))
        assertFalse(claim(req.requestId, client = "swapped-authenticated-client"))
        assertTrue(claim(req.requestId))
    }

    @Test fun ownerDocumentChangeRevokesAndReleases() {
        // Pure registry test: release of Android native resources is separately gated.
        val req = submit()
        assertTrue(approve(req.requestId))
        assertFalse(claim(req.requestId, currentDoc = "new-document"))
        assertEquals("NOT_FOUND", registry.status(req.requestId, subject))
        assertFalse(claim(req.requestId))
    }

    @Test fun replayAfterStopDenied() {
        val req = submit()
        assertTrue(approve(req.requestId))
        assertTrue(registry.revokeOwned(req.requestId, subject))
        assertFalse(registry.revokeOwned(req.requestId, subject))
        assertFalse(claim(req.requestId))
    }

    @Test fun pendingConsentCannotBeOverwritten() {
        val first = submit()
        assertNull(registry.submitAuthenticated("other-principal", attempt, tab, doc, view))
        assertEquals(first.requestId, registry.prompt.value?.requestId)
        assertFalse(registry.revokeOwned(first.requestId, "other-principal"))
        assertTrue(approve(first.requestId))
        assertTrue(claim(first.requestId))
    }

    @Test fun propertyForeignRequestsNeverChangeOwnerGrant() {
        val req = submit()
        assertTrue(approve(req.requestId))
        val random = java.util.Random(0x504L)
        repeat(500) {
            val request = if (random.nextBoolean()) req.requestId else "unknown-$it"
            val client = if (random.nextBoolean()) subject else "different-$it"
            val attempted = if (random.nextBoolean()) attempt else "other-attempt-$it"
            // Force every generated case to be foreign to at least one binding
            // field; never consume the owner's frame budget in the property test.
            val safeRequest = if (request == req.requestId && client == subject && attempted == attempt) "foreign-$it" else request
            assertFalse(claim(safeRequest, client = client, currentAttempt = attempted))
            assertEquals("APPROVED", registry.status(req.requestId, subject))
        }
        assertTrue(claim(req.requestId))
    }

    @Test fun concurrentDistinctPrincipalsIsolated() {
        // Two independently named model principals; real paired-device isolation remains HARDWARE_NOT_TESTED.
        val req = submit()
        assertTrue(approve(req.requestId))
        val start = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(1)
        val intruder =
            Thread {
                start.await()
                repeat(200) {
                    registry.revokeOwned(req.requestId, "principal-B")
                    registry.claimFrame(req.requestId, "principal-B", attempt, tab, doc, view, true, false)
                }
                done.countDown()
            }
        intruder.start()
        start.countDown()
        assertTrue(done.await(3, java.util.concurrent.TimeUnit.SECONDS))
        intruder.join()
        assertEquals("APPROVED", registry.status(req.requestId, subject))
        assertTrue(claim(req.requestId))
    }
}
