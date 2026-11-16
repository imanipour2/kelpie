package com.kelpie.browser.agent.execution

import com.kelpie.browser.agent.contract.AdapterOutcome
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.agent.contract.ObservedTarget
import com.kelpie.browser.agent.render.WebViewOwnershipState
import com.kelpie.browser.agent.session.SessionConsentCore

/**
 * Fail-closed boundary for a future Android Main-thread TabStore/WebView implementation.
 * A production NativePort MUST obtain its target from TabStore itself, never client JSON.
 * No command is dispatched unless the exact live renderer identity and ownership match.
 *
 * This is intentionally not an HTTP/MCP handler; it must only be installed behind the
 * single authenticated AgentOperationRuntime and the actual local-consent UI.
 */
class VerifiedNativeBrowserAdapter(
    private val consent: SessionConsentCore,
    private val ownership: WebViewOwnershipState,
    private val port: NativePort,
) : AgentOperationRuntime.BrowserAdapter {
    /** Native implementer must use object identity of the actual WebView, not a URL. */
    data class NativeTarget(
        val tabId: String,
        val documentEpoch: String,
        val rendererIdentity: String,
    )

    interface NativePort {
        /** Return an atomic snapshot on the Android UI thread; null if target was destroyed. */
        suspend fun actualTarget(tabId: String): NativeTarget?

        /** Only the actual Android adapter may execute this under the UI-thread lease. */
        suspend fun execute(
            command: AgentCommand,
            target: NativeTarget,
        ): AdapterOutcome

        /** Fresh observation, independent of execute()'s success claim. */
        suspend fun proveEffect(
            command: AgentCommand,
            before: NativeTarget,
            after: NativeTarget,
            evidenceId: String,
        ): Boolean
    }

    /** Snapshot must not be cached across navigation, tab switching or ownership moves. */
    private suspend fun authorizedTarget(command: AgentCommand): NativeTarget? {
        val active = consent.snapshot() as? SessionConsentCore.State.Active ?: return null
        if (active.locked ||
            active.principalId != command.principalId ||
            active.generation != command.consentGeneration
        ) {
            return null
        }
        val target = port.actualTarget(command.tabId) ?: return null
        if (target.tabId != command.tabId ||
            target.rendererIdentity.isBlank() ||
            target.documentEpoch.isBlank() ||
            !ownership.mayExecuteWebOperation(active.generation, target.rendererIdentity)
        ) {
            return null
        }
        return target
    }

    override suspend fun observe(command: AgentCommand): ObservedTarget? = authorizedTarget(command)?.let { ObservedTarget(it.tabId, it.documentEpoch) }

    /** Native pre-action identity; this proof is single-use and never serialized. */
    private data class PendingEvidence(
        val commandId: String,
        val before: NativeTarget,
        val ownershipEpoch: Long,
    )

    // Caller must serialize all commands through AgentOperationRuntime.
    private var pendingEvidence: PendingEvidence? = null

    override suspend fun perform(
        command: AgentCommand,
        before: ObservedTarget?,
    ): AdapterOutcome {
        pendingEvidence = null
        val hostEpoch = ownership.transitionEpoch()
        val target = authorizedTarget(command) ?: return AdapterOutcome.Failed
        if (before != ObservedTarget(target.tabId, target.documentEpoch)) return AdapterOutcome.Failed
        // Never accept stale document references even if a new page has the same URL.
        if (command.observedDocumentEpoch != target.documentEpoch &&
            command.operation !in DOCUMENT_NEUTRAL
        ) {
            return AdapterOutcome.Failed
        }
        val outcome = port.execute(command, target)
        // STOP, lock/unlock, renderer loss, or a concurrent tab change after dispatch is uncertain.
        val after = authorizedTarget(command)
        if (after == null ||
            after.rendererIdentity != target.rendererIdentity ||
            ownership.transitionEpoch() != hostEpoch
        ) {
            return AdapterOutcome.Uncertain
        }
        if (outcome is AdapterOutcome.Verified) {
            pendingEvidence = PendingEvidence(command.commandId, target, hostEpoch)
        }
        return outcome
    }

    override suspend fun verifyEffect(
        command: AgentCommand,
        before: ObservedTarget?,
        after: ObservedTarget?,
        evidenceId: String,
    ): Boolean {
        val evidence = pendingEvidence ?: return false
        pendingEvidence = null // Never carry proof into a later command.
        if (evidence.commandId != command.commandId ||
            evidenceId.isBlank() ||
            before != ObservedTarget(evidence.before.tabId, evidence.before.documentEpoch) ||
            after == null ||
            ownership.transitionEpoch() != evidence.ownershipEpoch
        ) {
            return false
        }
        val live = authorizedTarget(command) ?: return false
        if (after != ObservedTarget(live.tabId, live.documentEpoch) ||
            evidence.before.rendererIdentity != live.rendererIdentity
        ) {
            return false
        }
        // Pass the real pre-effect target. Navigation may change documentVersion.
        val verified = port.proveEffect(command, evidence.before, live, evidenceId)
        return verified &&
            ownership.transitionEpoch() == evidence.ownershipEpoch &&
            authorizedTarget(command) == live
    }

    companion object {
        private val DOCUMENT_NEUTRAL =
            setOf(
                com.kelpie.browser.agent.contract.Operation.NAVIGATE,
                com.kelpie.browser.agent.contract.Operation.BACK,
                com.kelpie.browser.agent.contract.Operation.FORWARD,
                com.kelpie.browser.agent.contract.Operation.OPEN_TAB,
                com.kelpie.browser.agent.contract.Operation.CLOSE_TAB,
                com.kelpie.browser.agent.contract.Operation.SWITCH_TAB,
                com.kelpie.browser.agent.contract.Operation.LIST_TABS,
            )
    }
}
