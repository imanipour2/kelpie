package com.kelpie.browser.agent.execution

import com.kelpie.browser.agent.render.WebViewOwnershipState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Suspended handoff ordering with the production mutex/epoch and ownership reducers, not an Android renderer mock. */
@OptIn(ExperimentalCoroutinesApi::class)
class R5LifecycleReconcilerTest {
    private enum class Physical { FOREGROUND, TRANSFERRING, PRIVATE, RESTORE_PENDING }

    private class Fixture(
        private val scope: TestScope,
    ) {
        var resumed = true
        val lifecycle = R5LifecycleReconciler(Mutex()) { resumed }
        val owner = WebViewOwnershipState()
        val transfer = CompletableDeferred<Boolean>()
        var physical = Physical.FOREGROUND
        var ready = true
        var authorized = true
        var starts = 0
        var restores = 0
        var resumeActions = 0
        var restoreWait: CompletableDeferred<Unit>? = null

        init {
            assertTrue(owner.bindForeground("session", "view"))
        }

        fun pause() {
            resumed = false
            ready = false
            val request = lifecycle.signal(false)
            scope.launch {
                lifecycle.reconcile(request) { current ->
                    if (physical == Physical.RESTORE_PENDING) restore(current)
                    if (!current() || !authorized) return@reconcile
                    if (physical == Physical.FOREGROUND) {
                        owner.resetAfterVerifiedRestore()
                        if (owner.status() == WebViewOwnershipState.Host.ABSENT) owner.bindForeground("session", "view")
                        val ticket = requireNotNull(owner.moveToPrivate("session"))
                        physical = Physical.TRANSFERRING
                        val succeeded = if (++starts == 1) transfer.await() else true
                        if (succeeded && authorized && owner.completePrivate(ticket)) {
                            physical = Physical.PRIVATE
                        } else {
                            owner.failRenderer()
                            physical = Physical.RESTORE_PENDING
                        }
                    }
                    if (current()) ready = authorized && physical == Physical.PRIVATE
                }
            }
        }

        fun resume() {
            resumed = true
            ready = false
            val request = lifecycle.signal(true)
            reconcileForeground(request)
        }

        fun reconcileForeground(request: R5LifecycleReconciler.Request = lifecycle.current()) {
            scope.launch {
                lifecycle.reconcile(request) { current ->
                    resumeActions++
                    if (physical != Physical.FOREGROUND) restore(current)
                    if (current()) ready = authorized && physical == Physical.FOREGROUND
                }
            }
        }

        private suspend fun restore(current: () -> Boolean) {
            val ticket = owner.restoreForeground("session")
            physical = Physical.RESTORE_PENDING
            restoreWait?.await()
            physical = Physical.FOREGROUND
            restores++
            if (!current() || !authorized) return
            if (ticket != null) owner.completeRestore(ticket)
            if (owner.status() != WebViewOwnershipState.Host.FOREGROUND) {
                owner.resetAfterVerifiedRestore()
                assertTrue(owner.bindForeground("session", "view"))
            }
        }
    }

    @Test
    fun resumeWaitsForSuccessfulHandoffThenRestoresWithoutAnotherResume() =
        runTest {
            val f = Fixture(this)
            f.pause()
            runCurrent()
            assertEquals(Physical.TRANSFERRING, f.physical)
            f.resume()
            runCurrent()
            assertEquals(0, f.resumeActions)
            f.transfer.complete(true)
            advanceUntilIdle()
            assertEquals(Physical.FOREGROUND, f.physical)
            assertEquals(WebViewOwnershipState.Host.FOREGROUND, f.owner.status())
            assertEquals(1, f.restores)
            assertTrue(f.ready)
        }

    @Test
    fun resumeWaitsForFailedHandoffThenRestoresWithoutAnotherResume() =
        runTest {
            val f = Fixture(this)
            f.pause()
            runCurrent()
            f.resume()
            f.transfer.complete(false)
            advanceUntilIdle()
            assertEquals(Physical.FOREGROUND, f.physical)
            assertEquals(WebViewOwnershipState.Host.FOREGROUND, f.owner.status())
            assertEquals(1, f.restores)
            assertTrue(f.ready)
            f.pause()
            advanceUntilIdle()
            assertEquals(Physical.PRIVATE, f.physical)
            assertTrue(f.ready)
        }

    @Test
    fun rapidHomeResumeHomeKeepsSuccessfulPendingHostWithoutStaleForegroundPublication() =
        runTest {
            val f = Fixture(this)
            f.pause()
            runCurrent()
            f.resume()
            f.pause()
            f.transfer.complete(true)
            advanceUntilIdle()
            assertEquals(Physical.PRIVATE, f.physical)
            assertEquals(1, f.starts)
            assertEquals(0, f.resumeActions)
            assertEquals(0, f.restores)
            assertTrue(f.ready)
        }

    @Test
    fun rapidHomeResumeHomeDrainsFailedHandoffBeforeTheLatestPauseRetries() =
        runTest {
            val f = Fixture(this)
            f.pause()
            runCurrent()
            f.resume()
            f.pause()
            f.transfer.complete(false)
            advanceUntilIdle()
            assertEquals(Physical.PRIVATE, f.physical)
            assertEquals(WebViewOwnershipState.Host.PRIVATE, f.owner.status())
            assertEquals(2, f.starts)
            assertEquals(1, f.restores)
            assertEquals(0, f.resumeActions)
            assertTrue(f.ready)
        }

    @Test
    fun newerNativeVisibilityInvalidatesAResumeWhileItsRestoreAwaits() =
        runTest {
            val f = Fixture(this)
            f.transfer.complete(true)
            f.pause()
            advanceUntilIdle()
            f.restoreWait = CompletableDeferred()
            f.resume()
            runCurrent()
            assertEquals(Physical.RESTORE_PENDING, f.physical)
            f.resumed = false
            f.lifecycle.signal(false)
            f.restoreWait!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(Physical.FOREGROUND, f.physical)
            assertFalse(f.ready)
        }

    @Test
    fun stopDuringQueuedRestorationCannotRepublishReadiness() =
        runTest {
            val f = Fixture(this)
            f.pause()
            runCurrent()
            f.resume()
            f.authorized = false
            f.owner.stop()
            f.transfer.complete(true)
            advanceUntilIdle()
            assertEquals(Physical.FOREGROUND, f.physical)
            assertEquals(WebViewOwnershipState.Host.CLOSED, f.owner.status())
            assertFalse(f.ready)
        }

    @Test
    fun pauseDuringForegroundRestorationRetiresTheInterruptedTicketBeforeReenteringPrivateHost() =
        runTest {
            val f = Fixture(this)
            f.transfer.complete(true)
            f.pause()
            advanceUntilIdle()
            f.restoreWait = CompletableDeferred()
            f.resume()
            runCurrent()
            f.pause()
            f.restoreWait!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(Physical.PRIVATE, f.physical)
            assertEquals(WebViewOwnershipState.Host.PRIVATE, f.owner.status())
            assertEquals(2, f.starts)
            assertTrue(f.ready)
        }

    @Test
    fun newApprovedOwnerCanReconcileAnAlreadyResumedActivityWithoutALifecycleRoundTrip() =
        runTest {
            val f = Fixture(this)
            f.ready = false
            f.reconcileForeground()
            advanceUntilIdle()
            assertTrue(f.ready)
            assertEquals(1, f.resumeActions)
            assertEquals(0, f.starts)
        }
}
