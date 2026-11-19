package com.kelpie.browser.agent.execution

import com.kelpie.browser.agent.render.WebViewOwnershipState
import com.kelpie.browser.agent.session.SessionConsentCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fault injection at the native rehost seam, using the production consent/owner reducers. */
class R5PrivateTabTransactionTest {
    private class Fixture {
        val consent = SessionConsentCore({ 100L })
        val grant = consent.approveFromNativeUi(requireNotNull(consent.requestNativeConsent("agent")).promptId, true)!!
        val owner = WebViewOwnershipState()
        var physical = "a"
        var selected = "a"
        var viewport = true
        var commitFails = false
        var restoreFails = false
        var duringMove: () -> Unit = {}
        var revoked = false
        val journal = mutableListOf<String>()

        init {
            assertTrue(owner.bindForeground(grant.generation, "a"))
            assertTrue(owner.completePrivate(requireNotNull(owner.moveToPrivate(grant.generation))))
        }

        suspend fun run(): Boolean =
            R5PrivateTabTransaction.run(
                eligible = { consent.snapshot() == grant && selected == "a" && owner.mayExecuteWebOperation(grant.generation, "a") },
                move = {
                    journal.add("move")
                    physical = "b"
                    duringMove()
                    viewport
                },
                commit = {
                    assertEquals("b", physical)
                    assertTrue(viewport)
                    journal.add("commit")
                    if (commitFails) false else owner.switchPrivate(grant.generation, "a", "b").also { if (it) selected = "b" }
                },
                verify = { physical == selected && owner.mayExecuteWebOperation(grant.generation, physical) },
                rollback = {
                    journal.add("rollback")
                    if (restoreFails || consent.snapshot() != grant) {
                        false
                    } else {
                        physical = "a"
                        selected = "a"
                        owner.mayExecuteWebOperation(grant.generation, "a") || owner.switchPrivate(grant.generation, "b", "a")
                    }
                },
                revoke = {
                    revoked = true
                    owner.stop()
                },
            )
    }

    @Test
    fun selectionOnlyCommitsAfterMeasuredRehost() =
        runBlocking {
            val f = Fixture()
            assertTrue(f.run())
            assertEquals(listOf("move", "commit"), f.journal)
            assertEquals("b", f.selected)
            assertEquals(f.selected, f.physical)
            assertFalse(f.revoked)
        }

    @Test
    fun zeroViewportRestoresOldTabWithoutCommittingItAsNew() =
        runBlocking {
            val f = Fixture().apply { viewport = false }
            assertFalse(f.run())
            assertEquals(listOf("move", "rollback"), f.journal)
            assertEquals("a", f.selected)
            assertEquals("a", f.physical)
            assertFalse(f.revoked)
        }

    @Test
    fun failedMetadataCommitRollsBackPhysicalHost() =
        runBlocking {
            val f = Fixture().apply { commitFails = true }
            assertFalse(f.run())
            assertEquals("a", f.physical)
            assertTrue(f.owner.mayExecuteWebOperation(f.grant.generation, "a"))
        }

    @Test
    fun lockDuringRehostNeverCommitsAndRevokesUnrecoverableHost() =
        runBlocking {
            val f = Fixture()
            f.duringMove = { f.owner.lockDevice() }
            assertFalse(f.run())
            assertEquals("a", f.selected)
            assertTrue(f.revoked)
            assertFalse(f.owner.mayExecuteWebOperation(f.grant.generation, "b"))
        }

    @Test
    fun failedRollbackCannotReportSuccess() =
        runBlocking {
            val f =
                Fixture().apply {
                    viewport = false
                    restoreFails = true
                }
            assertFalse(f.run())
            assertTrue(f.revoked)
        }

    @Test
    fun cancellationReclaimsHostAndPropagatesCancellation() =
        runBlocking {
            val f = Fixture().apply { duringMove = { throw CancellationException("synthetic cancellation") } }
            try {
                f.run()
                throw AssertionError("Cancellation must not become success")
            } catch (_: CancellationException) {
                assertEquals("a", f.physical)
                assertEquals("a", f.selected)
                assertTrue("rollback" in f.journal)
            }
        }
}
