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
import com.kelpie.browser.agent.session.R5AgentLinkReadiness
import com.kelpie.browser.agent.session.R5AgentLinkStage
import com.kelpie.browser.agent.session.R5AgentProvider

/**
 * R5 private QA only: a progressive onboarding flow that does not pretend to
 * provision a cloud tunnel or install a provider connector from Android.
 *
 * The provider owns its OAuth/plugin-authorization screen. The phone owns
 * pairing approval and a separate one-tap process grant.
 */
@Composable
internal fun AgentConnectionCenterSheet(
    coordinator: AgentSessionCoordinator,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val session by coordinator.active.collectAsState()
    val verifiedPrincipals by coordinator.verifiedPrincipals.collectAsState()
    val stage = R5AgentLinkReadiness.stage(verifiedPrincipals, session?.principalId)
    var provider by remember { mutableStateOf(R5AgentProvider.CHATGPT) }
    var showAdvanced by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf("") }
    val safeEndpoint = R5AgentConnectionSetup.safeEndpoint(endpoint)
    val instructions =
        if (provider == R5AgentProvider.CHATGPT && safeEndpoint == null) {
            R5AgentConnectionSetup.chatGptTunnelInstructions()
        } else {
            R5AgentConnectionSetup.setupInstructions(provider, endpoint)
        }

    fun openProviderHelp() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(provider.helpUrl))
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Kelpie Link")
        Text("Connect your AI agent once. Approve browser control locally on this phone for each new process session.")
        HorizontalDivider()
        Text("Connection status")
        when (stage) {
            R5AgentLinkStage.WAITING_FOR_AUTHENTICATED_AGENT -> {
                Text("Not connected to a verified AI client yet.")
                Text("Open your AI app's MCP connection settings. After connection, ask the agent to check Kelpie capabilities.")
            }
            R5AgentLinkStage.WAITING_FOR_NATIVE_OWNER -> {
                Text("An authenticated paired agent was detected.")
                verifiedPrincipals.forEach { principal ->
                    Text("Verified agent: " + coordinator.principalName(principal))
                }
                Text("Return to Kelpie's native approval screen to enable agent control and background access.")
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue to phone approval")
                }
            }
            R5AgentLinkStage.NATIVE_CONSENT_ACTIVE -> {
                Text("Agent control has been approved locally on this phone.")
                Text("The remote connection is not confirmed until the AI agent successfully reads a Kelpie tab.")
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to Kelpie")
                }
            }
        }
        HorizontalDivider()
        Text("Step 1 — Choose your AI agent")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { provider = R5AgentProvider.CHATGPT }) {
                Text(if (provider == R5AgentProvider.CHATGPT) "✓ ChatGPT" else "ChatGPT")
            }
            TextButton(onClick = { provider = R5AgentProvider.CLAUDE }) {
                Text(if (provider == R5AgentProvider.CLAUDE) "✓ Claude" else "Claude")
            }
        }
        Text("Step 2 — Connect in " + provider.title)
        Text(
            if (provider == R5AgentProvider.CHATGPT) {
                "Use the authorized Kelpie MCP connection or Secure MCP Tunnel in ChatGPT. " +
                    "Plugin installation and account approval happen inside ChatGPT."
            } else {
                "Your AI client must use a reachable, trusted HTTPS MCP endpoint. " +
                    "A local Android service alone is not reachable by a cloud agent."
            },
        )
        Button(
            onClick = ::openProviderHelp,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open " + provider.title + " connection guide") }
        Text("A verified request will automatically appear here. " +
            "Only your tap on the phone grants control; a cloud connection cannot approve itself.")
        HorizontalDivider()
        TextButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (showAdvanced) "Hide advanced settings" else "Advanced MCP setup / other agents") }
        if (showAdvanced) {
            Text("Advanced — choose an MCP provider")
            R5AgentProvider.entries.forEach { candidate ->
                TextButton(onClick = { provider = candidate }) {
                    Text(if (provider == candidate) "✓ " + candidate.title else candidate.title)
                }
            }
            Text("Optional: your existing trusted HTTPS /mcp endpoint")
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it.take(1024) },
                label = { Text("https://your-host.example/mcp") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (endpoint.isBlank()) {
                    "No endpoint entered. ChatGPT's existing private tunnel needs no URL on the phone."
                } else if (safeEndpoint == null) {
                    "Invalid endpoint: HTTPS DNS hostname and /mcp only; never include a token."
                } else {
                    "Endpoint format accepted. Reachability and authentication are NOT verified."
                },
            )
            Button(
                enabled = instructions != null,
                onClick = {
                    instructions?.let { clipboard.setText(AnnotatedString(it)) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copy setup instructions") }
            TextButton(
                enabled = instructions != null,
                onClick = {
                    instructions?.let {
                        val send =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, it)
                            }
                        context.startActivity(Intent.createChooser(send, "Share Kelpie setup"))
                    }
                },
            ) { Text("Share setup through Android") }
        }
        Text(
            "Safety: Keep the STOP notification visible. Device lock suspends control. " +
                "Do not paste pairing tokens, passwords or payment details into setup.",
        )
        TextButton(onClick = onDismiss) { Text("Close") }
    }
}
