package com.kelpie.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kelpie.browser.agent.session.AgentSessionCoordinator

/**
 * Mother-first: existing computer approval is durable. Native Resume only
 * clears an explicit STOP; no Termux OTP or duplicate pairing channel exists.
 *
 * The tunnel is configured in the user's MCP client. The sole proof of agent
 * control remains real authenticated browser actions, never local /readyz.
 */
@Composable
internal fun AgentConnectionCenterSheet(
    coordinator: AgentSessionCoordinator,
    onDismiss: () -> Unit,
) {
    val session by coordinator.active.collectAsState()
    val pending by coordinator.pending.collectAsState()
    val observedClients by coordinator.connectionAgents.collectAsState()
    val paused by coordinator.paused.collectAsState()
    if (!coordinator.motherConnectionMode) R5ObservedAgentExpiryEffect(coordinator, observedClients.isNotEmpty())

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Connect to an AI agent")
        Text("Use the existing MCP connection in ChatGPT. Kelpie does not need a second code from Termux.")
        when {
            coordinator.motherConnectionMode && paused -> {
                Text("Browser control is stopped. Your trusted computer remains paired; choose Resume to allow it again.")
                for (principal in observedClients) {
                    if (observedClients.size > 1) Text("Trusted computer " + principal.take(8) + "…")
                    R5OneTapConsentAction(coordinator, principal, onGranted = onDismiss)
                }
                if (observedClients.isEmpty()) Text("No trusted computer is available. Review Paired Clients in Settings.")
            }
            session?.locked == true -> {
                Text("Paused while the device is locked.")
                Button(
                    onClick = coordinator::stopFromNativeUi,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("STOP browser control") }
            }
            session != null -> {
                Text("Approved on this device. Verify the remote connection by listing real browser tabs.")
                Button(
                    onClick = coordinator::stopFromNativeUi,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("STOP browser control") }
            }
            pending != null -> {
                Text("Approval requested. Review the native on-device confirmation dialog.")
            }
            observedClients.size == 1 -> {
                if (coordinator.motherConnectionMode) {
                    Text("This computer is already approved and reconnects automatically. Send a browser request in ChatGPT.")
                } else {
                    Text("One verified MCP client is available. Approve only if you recognize it.")
                    R5OneTapConsentAction(coordinator, observedClients.single(), onGranted = onDismiss)
                }
            }
            observedClients.size > 1 -> {
                Text("Several computers are trusted. Use the intended computer's existing MCP connection.")
            }
            else -> {
                Text(
                    "Waiting for an authenticated MCP client. Reconnect the already configured " +
                        "ChatGPT plugin; a Termux code or new API key is not required.",
                )
            }
        }
        TextButton(
            onClick = coordinator::refreshObservedAgents,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(if (coordinator.motherConnectionMode) "Refresh trusted computers" else "Refresh verified clients") }
        Text(
            if (coordinator.motherConnectionMode) {
                "Computer approval survives reconnects and app restarts. STOP remains paused until you resume; " +
                    "the phone lock suspends browser operations. Remove a trusted computer in Settings to revoke its access."
            } else {
                "Your approval controls this app session only. STOP revokes control; " +
                    "a running local tunnel does not prove that the remote agent is connected."
            },
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Close") }
    }
}
