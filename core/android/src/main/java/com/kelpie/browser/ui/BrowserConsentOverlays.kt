package com.kelpie.browser.ui

import androidx.compose.runtime.Composable
import com.kelpie.browser.agent.session.AgentSessionConsentUi
import com.kelpie.browser.agent.session.AgentSessionCoordinator
import com.kelpie.browser.browser.TabStore
import com.kelpie.browser.capture.PrivateCaptureCoordinator

/** Distinct native approval surfaces; no remote browser operation is registered here. */
@Composable
internal fun BrowserConsentOverlays(
    capture: PrivateCaptureCoordinator?,
    tabStore: TabStore,
    agentConsent: AgentSessionCoordinator?,
    r5AgentEnabled: Boolean = false,
    onOpenAgentSetup: () -> Unit = {},
    agentSetupVisible: Boolean = false,
) {
    capture?.let { PrivateCaptureConsentDialog(it, tabStore) }
    agentConsent?.let {
        AgentSessionConsentUi(
            coordinator = it,
            r5AgentEnabled = r5AgentEnabled,
            onOpenAgentSetup = onOpenAgentSetup,
            agentSetupVisible = agentSetupVisible,
        )
    }
}
