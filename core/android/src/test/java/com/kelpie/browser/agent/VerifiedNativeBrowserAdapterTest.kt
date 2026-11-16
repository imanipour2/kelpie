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
        var bounceOwnershipDuringExecute = false
        var proofAllowed = true
        var actualBeforeDoc: String? = null
        var actualAfterDoc: String? = null
        val port =
            object : VerifiedNativeBrowserAdapter.NativePort {
                override suspend fun actualTarget(tabId: String) =
                    if (tabId == "tab-A") {
                        VerifiedNativeBrowserAdapter.NativeTarget(
                            "tab-A",
                            currentDoc,
                            currentRenderer,
                        )
                    } else {
                        null
                    }

                override suspend fun execute(
                    command: AgentCommand,
                    target: VerifiedNativeBrowserAdapter.NativeTarget,
                ): AdapterOutcome {
                    executeCount++
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
        val runtime = AgentOperationRuntime(consent, VerifiedNativeBrowserAdapter(consent, ownership, port))

        init {
            assertTrue(ownership.bindForeground(active.generation, currentRenderer))
        }

        fun command(
            id: String,
            seq: Long,
            op: Operation = Operation.READ_DOM,
            url: String? = null,
        ) = AgentCommand(id, active.principalId, active.generation, "tab-A", currentDoc, op, url, seq)
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
}
