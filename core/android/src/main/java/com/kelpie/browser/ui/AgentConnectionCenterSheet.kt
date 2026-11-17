package com.kelpie.browser.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kelpie.browser.agent.session.AgentSessionCoordinator
import com.kelpie.browser.agent.session.R5AgentConnectionSetup
import com.kelpie.browser.agent.session.R5AgentProvider

/**
 * Guided configuration only. Real pairing, native approval and connectivity
 * verification belong to the existing authenticated R5 network/session routes.
 */
@Composable
internal fun AgentConnectionCenterSheet(
    coordinator: AgentSessionCoordinator,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val session by coordinator.active.collectAsState()
    var provider by remember { mutableStateOf(R5AgentProvider.CHATGPT) }
    var endpoint by remember { mutableStateOf("") }
    val validUrl = R5AgentConnectionSetup.safeEndpoint(endpoint)
    val instructions = R5AgentConnectionSetup.setupInstructions(provider, endpoint)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Connect an AI agent")
        Text("The browser stays on this phone. Pairing and local consent are separate steps.")
        Text(
            if (session == null) {
                "Native browser-control session: OFF"
            } else {
                "Native browser-control session: authorized locally (remote connection not verified)"
            },
        )
        HorizontalDivider()
        Text("1. Choose an MCP client")
        R5AgentProvider.entries.forEach { candidate ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                RadioButton(
                    selected = provider == candidate,
                    onClick = { provider = candidate },
                )
                TextButton(onClick = { provider = candidate }) { Text(candidate.title) }
            }
        }
        Text("2. Paste your trusted HTTPS MCP address")
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it.take(1024) },
            label = { Text("https://your-host.example/mcp") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (validUrl == null) {
                "No endpoint accepted yet. Use HTTPS, a DNS hostname, and /mcp. Do not paste tokens."
            } else {
                "Address format accepted. This does not prove reachability, trust or authentication."
            },
        )
        Button(
            enabled = instructions != null,
            onClick = {
                if (instructions != null) {
                    clipboard.setText(AnnotatedString(instructions))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Copy setup instructions") }
        TextButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(provider.helpUrl))
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                }
            },
        ) { Text("Open " + provider.title + " setup guide") }
        HorizontalDivider()
        Text("3. Finish in your chosen AI client, then approve the real Kelpie request.")
        Text(
            "The client must discover MCP tools and pass authentication. " +
                "A successful native consent is not proof of a connected remote agent.",
        )
        Text(
            "Keep the Android STOP notification available. Locking the phone suspends control. " +
                "Never share pairing secrets, bank credentials or saved passwords.",
        )
        TextButton(onClick = onDismiss) { Text("Close") }
    }
}
