package com.kelpie.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kelpie.browser.agent.session.AgentSessionCoordinator

/** A visible in-browser shortcut; never brings Kelpie forward from the background. */
@Composable
internal fun R5AgentConnectShortcut(
    coordinator: AgentSessionCoordinator?,
    onOpenCenter: () -> Unit,
) {
    if (coordinator == null) return
    val clients by coordinator.observedAgents.collectAsState()
    val pending by coordinator.pending.collectAsState()
    val active by coordinator.active.collectAsState()
    R5ObservedAgentExpiryEffect(coordinator, clients.isNotEmpty())
    if (active != null || pending != null) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                when (clients.size) {
                    0 -> "AI agent not yet paired"
                    1 -> "Verified agent " + clients.single().take(8) + "... ready"
                    else -> "Choose a verified agent"
                },
            style = MaterialTheme.typography.labelMedium,
        )
        if (clients.size == 1) {
            // The single owner gesture begins and approves this process session.
            // Multiple clients never use the one-tap shortcut.
            R5OneTapConsentAction(coordinator, clients.single(), compact = true)
        } else {
            TextButton(
                onClick = onOpenCenter,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(if (clients.isEmpty()) "Pair / Connect" else "Choose")
            }
        }
    }
}
