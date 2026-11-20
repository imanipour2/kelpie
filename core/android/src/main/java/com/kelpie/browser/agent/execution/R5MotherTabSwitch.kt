package com.kelpie.browser.agent.execution

import androidx.activity.ComponentActivity
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.agent.contract.Operation
import com.kelpie.browser.agent.session.SessionConsentCore
import com.kelpie.browser.network.verifiedClientId
import java.util.UUID

/**
 * Bridges authenticated mother MCP tab selection to the existing R5 native switch.
 * No caller-supplied principal, consent generation or renderer identity is trusted.
 */
internal class R5MotherTabSwitch(
    private val activity: ComponentActivity,
    private val consent: SessionConsentCore,
    private val nativeTabCommands: R5AgentTabCommands,
    private val ready: () -> Boolean,
    private val selectedTab: () -> String?,
    private val closed: () -> Boolean,
    private val cancelInFlight: () -> Unit,
    private val onRendererFailure: (SessionConsentCore.State.Active) -> Unit,
) {
    suspend fun select(targetId: String): Boolean {
        val principal = verifiedClientId() ?: return false
        val active = consent.snapshot() as? SessionConsentCore.State.Active ?: return false

        fun admitted(): Boolean =
            ready() &&
                R5AgentAuthorization.permits(
                    activity,
                    principal,
                    active.generation,
                    consent.snapshot() as? SessionConsentCore.State.Active,
                    closed(),
                )

        if (!admitted()) return false
        val target = nativeTabCommands.observe() ?: return false
        val command =
            AgentCommand(
                commandId = UUID.randomUUID().toString(),
                principalId = principal,
                consentGeneration = active.generation,
                tabId = target.tabId,
                observedDocumentEpoch = target.documentEpoch,
                operation = Operation.SWITCH_TAB,
                selector = targetId,
            )
        // Existing R5 operations must be cancelled before changing WebView ownership.
        // The native R5 switch itself holds the transfer mutex and verifies the epoch.
        cancelInFlight()
        val performed = nativeTabCommands.perform(command)
        val verified = performed && nativeTabCommands.verify(command)
        if (!R5MotherPrivateSelectionProof.accepts(
                performed = performed,
                nativeVerified = verified,
                ownerStillAdmitted = admitted(),
                selectedTabId = selectedTab(),
                requestedTabId = targetId,
            )
        ) {
            if (performed) {
                onRendererFailure(active)
            }
            return false
        }
        return true
    }
}
