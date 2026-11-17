package com.kelpie.browser.agent

import com.kelpie.browser.agent.contract.AdapterOutcome
import com.kelpie.browser.agent.contract.AdmissionCode
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.agent.contract.ExecutionCode
import com.kelpie.browser.agent.contract.ExecutionStatus
import com.kelpie.browser.agent.contract.ObservedTarget
import com.kelpie.browser.agent.contract.Operation
import com.kelpie.browser.agent.execution.AgentOperationRuntime
import com.kelpie.browser.agent.session.SessionConsentCore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** JVM tests; never launch Android, read real web pages, or fabricate local user consent. */
class AgentOperationRuntimeTest {
    private class FakeAdapter : AgentOperationRuntime.BrowserAdapter {
        var epoch = "v1"
        var performCount = 0
        var activeCount = 0
        var peak = 0
        var hold: CompletableDeferred<Unit>? = null
        var next: AdapterOutcome = AdapterOutcome.Verified("fresh-dom-marker")
        var effectIsReallyVerified = true

        override suspend fun observe(command: AgentCommand): ObservedTarget? =
            if (command.operation == Operation.OPEN_TAB || command.operation == Operation.LIST_TABS) {
                null
            } else {
                ObservedTarget("t1", epoch)
            }

        override suspend fun verifyEffect(
            command: AgentCommand,
            before: ObservedTarget?,
            after: ObservedTarget?,
            evidenceId: String,
        ): Boolean = effectIsReallyVerified && evidenceId == "fresh-dom-marker"

        override suspend fun perform(
            command: AgentCommand,
            before: ObservedTarget?,
        ): AdapterOutcome {
            activeCount++
            peak = maxOf(peak, activeCount)
            try {
                hold?.await()
                delay(1)
                performCount++
                return next
            } finally {
                activeCount--
            }
        }
    }

    private data class Fixture(
        val session: SessionConsentCore,
        val active: SessionConsentCore.State.Active,
        val adapter: FakeAdapter,
        val runtime: AgentOperationRuntime,
    ) {
        private var nextSequence = 0L

        fun cmd(
            id: String,
            op: Operation,
            epoch: String = "v1",
            url: String? = null,
        ) = AgentCommand(
            id,
            "paired",
            active.generation,
            "t1",
            epoch,
            op,
            url,
            ++nextSequence,
            selector = if (op in setOf(Operation.CLICK, Operation.FILL, Operation.TYPE)) "#fixture" else null,
            inputText = if (op in setOf(Operation.FILL, Operation.TYPE)) "safe-test" else null,
        )
    }

    private fun fixture(): Fixture {
        val state = SessionConsentCore({ 1L })
        val grant = state.approveFromNativeUi(state.requestNativeConsent("paired")!!.promptId, true)!!
        val adapter = FakeAdapter()
        return Fixture(state, grant, adapter, AgentOperationRuntime(state, adapter))
    }

    @Test
    fun tabMutationSharesTheNormalCommandReplayAndConsentLease() =
        runBlocking {
            val f = fixture()
            var created = 0
            val cmd = f.cmd("new-tab", Operation.OPEN_TAB, url = "https://example.com/")
            val observed = suspend { ObservedTarget("t1", "v1") }
            val performed =
                suspend {
                    created++
                    true
                }
            val verified = suspend { created == 1 }
            val first = f.runtime.dispatchTabMutation(cmd, "paired", observed, performed, verified)
            assertEquals(ExecutionStatus.EXECUTED, first.status)
            assertEquals(ExecutionCode.VERIFIED_EFFECT, first.code)
            assertEquals(1, created)
            val replay = f.runtime.dispatchTabMutation(cmd.copy(clientSequence = 2), "paired", observed, performed, verified)
            assertEquals(ExecutionCode.DUPLICATE_COMMAND, replay.code)
            assertEquals(1, created)
            val normal = f.runtime.dispatch(f.cmd("regular", Operation.READ_DOM), "paired")
            assertEquals(ExecutionStatus.EXECUTED, normal.status)
        }

    @Test
    fun revokeDuringTabMutationNeverProducesVerifiedSuccess() =
        runBlocking {
            val f = fixture()
            val cmd = f.cmd("revoked-new-tab", Operation.OPEN_TAB, url = "https://example.org/")
            val result =
                f.runtime.dispatchTabMutation(
                    cmd,
                    "paired",
                    observe = { ObservedTarget("t1", "v1") },
                    mutate = {
                        f.session.stop()
                        true
                    },
                    verify = { true },
                )
            assertEquals(ExecutionStatus.UNCERTAIN, result.status)
            assertEquals(ExecutionCode.SESSION_REVOKED, result.code)
        }

    @Test fun requestIdentityNotDerivedFromClientJson() =
        runBlocking {
            val f = fixture()
            val receipt = f.runtime.dispatch(f.cmd("read", Operation.READ_DOM), "attacker")
            assertEquals(AdmissionCode.WRONG_PRINCIPAL, receipt.admission)
            assertEquals(0, f.adapter.performCount)
        }

    @Test fun stalePageAndSensitiveCommandsNeverExecute() =
        runBlocking {
            val f = fixture()
            f.adapter.epoch = "v2"
            assertEquals(AdmissionCode.STALE_DOCUMENT, f.runtime.dispatch(f.cmd("stale", Operation.CLICK), "paired").admission)
            assertEquals(AdmissionCode.SENSITIVE_ACTION_REQUIRES_APPROVAL, f.runtime.dispatch(f.cmd("purchase", Operation.PAYMENT, "v2"), "paired").admission)
            assertEquals(AdmissionCode.SENSITIVE_ACTION_REQUIRES_APPROVAL, f.runtime.dispatch(f.cmd("publish", Operation.PUBLISH, "v2"), "paired").admission)
            assertEquals(AdmissionCode.SENSITIVE_ACTION_REQUIRES_APPROVAL, f.runtime.dispatch(f.cmd("erase", Operation.IRREVERSIBLE_DELETE, "v2"), "paired").admission)
            assertEquals(0, f.adapter.performCount)
        }

    @Test fun duplicateCommandIsNotReplayed() =
        runBlocking {
            val f = fixture()
            assertEquals(ExecutionStatus.EXECUTED, f.runtime.dispatch(f.cmd("c", Operation.FILL), "paired").status)
            assertEquals(ExecutionCode.DUPLICATE_COMMAND, f.runtime.dispatch(f.cmd("c", Operation.FILL), "paired").code)
            assertEquals(1, f.adapter.performCount)
        }

    @Test fun aNewSequenceCannotReuseAnEarlierCommandId() =
        runBlocking {
            val f = fixture()
            assertEquals(ExecutionStatus.EXECUTED, f.runtime.dispatch(f.cmd("same", Operation.TYPE), "paired").status)
            val duplicateWithNewSequence = f.runtime.dispatch(f.cmd("same", Operation.TYPE), "paired")
            assertEquals(ExecutionCode.DUPLICATE_COMMAND, duplicateWithNewSequence.code)
            assertEquals(1, f.adapter.performCount)
        }

    @Test fun legacyZeroOrNegativeSequenceFailsClosed() =
        runBlocking {
            val f = fixture()
            val zero = f.cmd("zero", Operation.READ_DOM).copy(clientSequence = 0)
            val negative = f.cmd("negative", Operation.READ_DOM).copy(clientSequence = -1)
            assertEquals(ExecutionCode.DUPLICATE_COMMAND, f.runtime.dispatch(zero, "paired").code)
            assertEquals(ExecutionCode.DUPLICATE_COMMAND, f.runtime.dispatch(negative, "paired").code)
            assertEquals(0, f.adapter.performCount)
        }

    @Test fun oldSequenceCannotReplayAfterRecentIdCacheEviction() =
        runBlocking {
            val f = fixture()
            val runtime = AgentOperationRuntime(f.session, f.adapter, maxTrackedCommands = 2)
            val first = f.cmd("first", Operation.SCROLL)
            assertEquals(ExecutionStatus.EXECUTED, runtime.dispatch(first, "paired").status)
            assertEquals(ExecutionStatus.EXECUTED, runtime.dispatch(f.cmd("second", Operation.SCROLL), "paired").status)
            assertEquals(ExecutionStatus.EXECUTED, runtime.dispatch(f.cmd("third", Operation.SCROLL), "paired").status)
            assertEquals(ExecutionCode.DUPLICATE_COMMAND, runtime.dispatch(first, "paired").code)
            assertEquals(3, f.adapter.performCount)
        }

    @Test fun unprovenClickCannotProduceSuccessReceipt() =
        runBlocking {
            val f = fixture()
            f.adapter.next = AdapterOutcome.Uncertain
            val receipt = f.runtime.dispatch(f.cmd("c", Operation.CLICK), "paired")
            assertEquals(ExecutionStatus.UNCERTAIN, receipt.status)
            assertNull(receipt.evidenceId)
        }

    @Test fun adapterClaimAloneIsNotProofOfSuccess() =
        runBlocking {
            val f = fixture()
            f.adapter.effectIsReallyVerified = false
            val receipt = f.runtime.dispatch(f.cmd("not-proven", Operation.CLICK), "paired")
            assertEquals(ExecutionStatus.UNCERTAIN, receipt.status)
            assertNull(receipt.evidenceId)
        }

    @Test fun nativeStopCancelsInFlightCommandAndRequiresFreshConsent() =
        runBlocking {
            val f = fixture()
            f.adapter.hold = CompletableDeferred()
            val inFlight = async { f.runtime.dispatch(f.cmd("work", Operation.TYPE), "paired") }
            withTimeout(2_000) { while (f.adapter.activeCount == 0) delay(1) }
            f.runtime.stopFromTrustedNativeUi()
            try {
                inFlight.await()
            } catch (_: kotlinx.coroutines.CancellationException) {
                // transport may be cancelled
            }
            assertEquals(0, f.adapter.activeCount)
            assertEquals(0, f.adapter.performCount)
            assertEquals(AdmissionCode.NO_ACTIVE_SESSION, f.runtime.dispatch(f.cmd("new", Operation.READ_DOM), "paired").admission)
        }

    @Test fun lockUnlockDuringAwaitCannotDeliverExecutedReceipt() =
        runBlocking {
            val f = fixture()
            f.adapter.hold = CompletableDeferred()
            val inFlight = async { f.runtime.dispatch(f.cmd("in-flight", Operation.TYPE), "paired") }
            withTimeout(2_000) { while (f.adapter.activeCount == 0) delay(1) }
            f.session.setDeviceLocked(true)
            f.session.setDeviceLocked(false)
            f.adapter.hold!!.complete(Unit)
            val receipt = inFlight.await()
            assertEquals(ExecutionStatus.UNCERTAIN, receipt.status)
            assertNull(receipt.evidenceId)
        }

    @Test fun tablessCommandsDoNotDereferenceNullTargets() =
        runBlocking {
            val f = fixture()
            assertEquals(ExecutionStatus.EXECUTED, f.runtime.dispatch(f.cmd("list", Operation.LIST_TABS), "paired").status)
            assertEquals(ExecutionStatus.EXECUTED, f.runtime.dispatch(f.cmd("open", Operation.OPEN_TAB, url = "https://example.org"), "paired").status)
        }

    @Test fun policyHoldsThroughMultipleBrowserOperationsAndFiftyCycles() =
        runBlocking {
            val f = fixture()
            for (i in 0 until 50) {
                assertEquals(ExecutionStatus.EXECUTED, f.runtime.dispatch(f.cmd("scroll-$i", Operation.SCROLL), "paired").status)
            }
            assertEquals(50, f.adapter.performCount)
            assertEquals(1, f.adapter.peak)
            f.session.setDeviceLocked(true)
            assertEquals(AdmissionCode.LOCKED, f.runtime.dispatch(f.cmd("locked", Operation.SCREENSHOT), "paired").admission)
        }
}
