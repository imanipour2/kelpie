package com.kelpie.browser.handlers

import com.kelpie.browser.agent.session.SessionConsentCore
import com.kelpie.browser.network.R5RequestAuthorization
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserOperationGuardTest {
    private val consent = SessionConsentCore({ 1_000L })
    private val principal = "fixture-agent"

    private fun approve(): SessionConsentCore.State.Active {
        val prompt = consent.requestNativeConsent(principal)!!
        return consent.approveFromNativeUi(prompt.promptId, deviceUnlocked = true)!!
    }

    private fun guard(
        admitted: SessionConsentCore.State.Active,
        target: () -> Boolean = { true },
        document: () -> Boolean = { true },
    ) = BrowserOperationGuard({ R5RequestAuthorization.permits(principal, admitted, consent.snapshot()) }, target, document)

    @Test
    fun queuedActionCannotRunAfterStopLockRoundTripReapprovalOrUnpair() =
        runBlocking {
            for (transition in listOf("STOP", "LOCK", "LOCK_UNLOCK", "REAPPROVE", "UNPAIR")) {
                consent.stop()
                val admitted = approve()
                val scope = BrowserOperationContext()
                val queued = CompletableDeferred<Unit>()
                val resumeMain = CompletableDeferred<Unit>()
                var mutations = 0
                val operation =
                    async {
                        scope.withGuard(guard(admitted)) {
                            val captured = scope.capture()!!
                            queued.complete(Unit)
                            resumeMain.await()
                            if (captured.beforeAction()) mutations++
                        }
                    }
                queued.await()
                when (transition) {
                    "STOP" -> consent.stop()
                    "LOCK" -> consent.setDeviceLocked(true)
                    "LOCK_UNLOCK" -> {
                        consent.setDeviceLocked(true)
                        consent.setDeviceLocked(false)
                    }
                    "REAPPROVE" -> {
                        consent.stop()
                        approve()
                    }
                    "UNPAIR" -> consent.revokePrincipal(principal)
                }
                resumeMain.complete(Unit)
                operation.await()
                assertEquals(transition, 0, mutations)
                assertNull(scope.capture())
            }
        }

    @Test
    fun callbackCannotDeliverAfterAuthorityChangesEvenWhenActionWasAdmitted() {
        val captured = guard(approve())
        assertTrue(captured.beforeAction())
        consent.stop()
        assertFalse(captured.beforeAction())
        assertFalse(captured.afterNavigation())
    }

    @Test
    fun documentReplacementOnlyAllowsFixedNavigationReadOfSameRenderer() {
        var document = "old-document"
        var renderer = "original-renderer"
        val admitted = approve()
        val captured = guard(admitted, target = { renderer == "original-renderer" }, document = { document == "old-document" })
        assertTrue(captured.beforeAction())
        document = "new-document"
        assertFalse(captured.documentUnchanged())
        assertFalse(captured.beforeAction())
        assertTrue(captured.afterNavigation())
        renderer = "replacement-renderer"
        assertFalse(captured.afterNavigation())
    }

    @Test
    fun postedWitnessDoesNotReadAnotherCoroutinesThreadLocalOrNewGrant() =
        runBlocking {
            val scope = BrowserOperationContext()
            val first = guard(approve())
            val queued = CompletableDeferred<BrowserOperationGuard>()
            val finish = CompletableDeferred<Unit>()
            val previous =
                async {
                    scope.withGuard(first) {
                        queued.complete(scope.capture()!!)
                        finish.await()
                        assertSame(first, scope.capture())
                    }
                }
            val posted = queued.await()
            consent.stop()
            val replacement = guard(approve())
            scope.withGuard(replacement) {
                assertSame(replacement, scope.capture())
                assertNotSame(scope.capture(), posted)
                assertFalse(posted.beforeAction())
                assertTrue(scope.capture()!!.beforeAction())
            }
            finish.complete(Unit)
            previous.await()
            assertNull(scope.capture())
        }
}
