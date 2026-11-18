package com.kelpie.browser.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
    val pending by coordinator.pending.collectAsState()
    val observedClients by coordinator.observedAgents.collectAsState()
    var requestError by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(R5AgentProvider.CHATGPT) }
    var chatGptTunnel by remember { mutableStateOf(true) }
    var endpoint by remember { mutableStateOf("") }
    val validUrl = R5AgentConnectionSetup.safeEndpoint(endpoint)
    val instructions =
        if (provider == R5AgentProvider.CHATGPT && chatGptTunnel) {
            R5AgentConnectionSetup.chatGptTunnelInstructions()
        } else {
            R5AgentConnectionSetup.setupInstructions(provider, endpoint)
        }

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
            when {
                session?.locked == true -> "Native session: SUSPENDED (device locked)"
                session != null -> "Native session: approved on this device; remote agent not yet verified"
                pending != null -> "Native session: awaiting your on-device approval"
                else -> "Native session: OFF; waiting for an authenticated agent request"
            },
        )
        if (session != null) {
            Button(
                onClick = coordinator::stopFromNativeUi,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("STOP browser control") }
        } else if (pending != null) {
            Text("A native approval request is pending. Review the separate consent dialog on this phone.")
        } else {
            when (observedClients.size) {
                0 -> Text(
                    "No uniquely paired agent was recently verified. Ask your AI assistant to " +
                        "check Kelpie capabilities, or finish pairing first.",
                )
                1 -> {
                    Text("Recently verified agent ID: " + observedClients.single().take(8) + "...")
                    Button(
                        onClick = {
                            requestError = !coordinator.requestFromNativeUi()
                            if (!requestError) onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Request owner approval") }
                    Text(
                        "This button only opens a separate native consent prompt. " +
                            "No browser access is granted until you tap Approve.",
                    )
                }
                else -> Text(
                    "Multiple paired agents are active. For safety, request a session " +
                        "explicitly from the intended AI client; Kelpie will not choose for you.",
                )
            }
            if (requestError) {
                Text("No current verified agent or Kelpie is locked. Recheck the connection and try again.")
            }
        }
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
        if (provider == R5AgentProvider.CHATGPT) {
            Text("2. Choose a ChatGPT connection method")
            Row {
                RadioButton(
                    selected = !chatGptTunnel,
                    onClick = { chatGptTunnel = false },
                )
                TextButton(onClick = { chatGptTunnel = false }) { Text("HTTPS endpoint") }
            }
            Row {
                RadioButton(
                    selected = chatGptTunnel,
                    onClick = { chatGptTunnel = true },
                )
                TextButton(onClick = { chatGptTunnel = true }) { Text("Secure MCP Tunnel") }
            }
        }
        if (provider != R5AgentProvider.CHATGPT || !chatGptTunnel) {
            Text("Paste your trusted HTTPS MCP address")
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
        } else {
            Text(
                "Use an existing authorized Secure MCP Tunnel inside ChatGPT web Plugins. " +
                    "No public phone endpoint or token is required here.",
            )
        }
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
            enabled = instructions != null,
            onClick = {
                if (instructions != null) {
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, instructions)
                        }
                    context.startActivity(Intent.createChooser(send, "Share Kelpie setup"))
                }
            },
        ) { Text("Share setup through Android") }
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
