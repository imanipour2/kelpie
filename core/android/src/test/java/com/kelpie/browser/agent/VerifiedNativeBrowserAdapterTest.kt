package com.kelpie.browser.agent

import com.kelpie.browser.agent.contract.AdapterOutcome
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.agent.contract.ExecutionStatus
import com.kelpie.browser.agent.contract.Operation
import com.kelpie.browser.agent.execution.AgentOperationRuntime
import com.kelpie.browser.agent.execution.VerifiedNativeBrowserAdapter
import com.kelpie.browser.agent.render.WebViewOwnershipState
import com.kelpie.browser.agent.session.SessionConsentCore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM contract tests only; no Android WebView/screen or backend is touched. */
class VerifiedNativeBrowserAdapterTest {
    private class Fixture {
        val consent = SessionConsentCore({ 100L })
        val active =
            consent.approveFromNativeUi(
                requireNotNull(consent.requestNativeConsent("trusted")).promptId,
                true,
            )!!
        val ownership = WebViewOwnershipState()
        var currentDoc = "doc-A"
        var currentRenderer = "webview-A"
        var executeCount = 0
        var proofCount = 0
        var mutateDocOnNavigate = false
        var mutateDocOnExecute = false
        var bounceOwnershipDuringExecute = false
        var proofAllowed = true
        var actualBeforeDoc: String? = null
        var actualAfterDoc: String? = null
        var onTargetLookup: () -> Unit = {}
        val port =
            object : VerifiedNativeBrowserAdapter.NativePort {
                override suspend fun actualTarget(tabId: String): VerifiedNativeBrowserAdapter.NativeTarget? {
                    onTargetLookup()
                    return if (tabId == "tab-A") {
                        VerifiedNativeBrowserAdapter.NativeTarget(
                            "tab-A",
                            currentDoc,
                            currentRenderer,
                        )
                    } else {
                        null
                    }
                }

                override suspend fun execute(
                    command: AgentCommand,
                    target: VerifiedNativeBrowserAdapter.NativeTarget,
                ): AdapterOutcome {
                    executeCount++
                    if (mutateDocOnExecute) currentDoc = "doc-replaced"
                    if (mutateDocOnNavigate && command.operation == Operation.NAVIGATE) {
                        currentDoc = "doc-B"
                    }
                    if (bounceOwnershipDuringExecute) {
                        val back = requireNotNull(ownership.moveToPrivate(active.generation))
                        assertTrue(ownership.completePrivate(back))
                        val forth = requireNotNull(ownership.restoreForeground(active.generation))
                        assertTrue(ownership.completeRestore(forth))
                    }
                    return AdapterOutcome.Verified("fresh-token")
                }

                override suspend fun proveEffect(
                    command: AgentCommand,
                    before: VerifiedNativeBrowserAdapter.NativeTarget,
                    after: VerifiedNativeBrowserAdapter.NativeTarget,
                    evidenceId: String,
                ): Boolean {
                    proofCount++
                    actualBeforeDoc = before.documentEpoch
                    actualAfterDoc = after.documentEpoch
                    return proofAllowed && evidenceId == "fresh-token"
                }
            }
        val adapter = VerifiedNativeBrowserAdapter(consent, ownership, port)
        val runtime = AgentOperationRuntime(consent, adapter)

        init {
            assertTrue(ownership.bindForeground(active.generation, currentRenderer))
        }

        fun command(
            id: String,
            seq: Long,
            op: Operation = Operation.READ_DOM,
            url: String? = null,
        ) = AgentCommand(
            id,
            active.principalId,
            active.generation,
            "tab-A",
            currentDoc,
            op,
            url,
            seq,
            selector = "#target",
            inputText = "ordinary input",
            scrollDeltaY = 120.0,
        )
    }

    @Test
    fun verifiedNavigationUsesRealBeforeAndAfterEpoch() =
        runBlocking {
            val fixture = Fixture()
            fixture.mutateDocOnNavigate = true
            val result = fixture.runtime.dispatch(fixture.command("nav", 1, Operation.NAVIGATE, "https://example.org"), "trusted")
            assertEquals(ExecutionStatus.EXECUTED, result.status)
            assertEquals("doc-A", fixture.actualBeforeDoc)
            assertEquals("doc-B", fixture.actualAfterDoc)
            assertEquals(1, fixture.proofCount)
        }

    @Test
    fun privateHostRoundTripDuringExecutionIsUncertain() =
        runBlocking {
            val fixture = Fixture()
            fixture.bounceOwnershipDuringExecute = true
            val result = fixture.runtime.dispatch(fixture.command("bounce", 1), "trusted")
            assertEquals(ExecutionStatus.UNCERTAIN, result.status)
            assertEquals(0, fixture.proofCount)
        }

    @Test
    fun replacedWebViewIsRejectedWithoutExecuting() =
        runBlocking {
            val fixture = Fixture()
            fixture.currentRenderer = "new-webview"
            val result = fixture.runtime.dispatch(fixture.command("bad-target", 1), "trusted")
            assertFalse(result.status == ExecutionStatus.EXECUTED)
            assertEquals(0, fixture.executeCount)
        }

    @Test
    fun unprovenEffectMustNeverBecomeExecuted() =
        runBlocking {
            val fixture = Fixture()
            fixture.proofAllowed = false
            val result = fixture.runtime.dispatch(fixture.command("no-proof", 1), "trusted")
            assertEquals(ExecutionStatus.UNCERTAIN, result.status)
            assertEquals(1, fixture.executeCount)
        }

    @Test
    fun readAndTargetedActionsCannotVerifyAgainstAReplacementDocument() =
        runBlocking {
            for (operation in listOf(Operation.READ_DOM, Operation.SCREENSHOT, Operation.SCROLL, Operation.FILL, Operation.TYPE, Operation.SELECT)) {
                val fixture = Fixture()
                fixture.mutateDocOnExecute = true
                val result = fixture.runtime.dispatch(fixture.command("replace", 1, operation), "trusted")
                assertFalse("Replacement document accepted for $operation", result.status == ExecutionStatus.EXECUTED)
                assertEquals("Action was never dispatched for $operation", 1, fixture.executeCount)
                assertEquals(0, fixture.proofCount)
            }
        }

    @Test
    fun staleDocumentCannotDispatchAnyTargetedAction() =
        runBlocking {
            for (operation in listOf(Operation.CLICK, Operation.TYPE, Operation.SCROLL, Operation.SCREENSHOT, Operation.READ_DOM)) {
                val fixture = Fixture()
                val stale = fixture.command("stale", 1, operation)
                fixture.currentDoc = "new-document"
                val result = fixture.runtime.dispatch(stale, "trusted")
                assertFalse(result.status == ExecutionStatus.EXECUTED)
                assertEquals(0, fixture.executeCount)
            }
        }

    @Test fun ownershipRoundtripWhileResolvingTheNativeTargetPreventsDispatch() =
        runBlocking {
            val fixture = Fixture()
            val command = fixture.command("pre-dispatch-host", 1, Operation.CLICK)
            val before = fixture.adapter.observe(command)
            fixture.onTargetLookup = {
                val move = requireNotNull(fixture.ownership.moveToPrivate(fixture.active.generation))
                assertTrue(fixture.ownership.completePrivate(move))
                val restore = requireNotNull(fixture.ownership.restoreForeground(fixture.active.generation))
                assertTrue(fixture.ownership.completeRestore(restore))
            }
            assertEquals(AdapterOutcome.Failed, fixture.adapter.perform(command, before))
            assertEquals(0, fixture.executeCount)
        }

    @Test fun lockRoundtripWhileResolvingTheNativeTargetPreventsDispatch() =
        runBlocking {
            val fixture = Fixture()
            val command = fixture.command("pre-dispatch-lock", 1, Operation.CLICK)
            val before = fixture.adapter.observe(command)
            fixture.onTargetLookup = {
                fixture.consent.setDeviceLocked(true)
                fixture.consent.setDeviceLocked(false)
            }
            assertEquals(AdapterOutcome.Failed, fixture.adapter.perform(command, before))
            assertEquals(0, fixture.executeCount)
        }
}
