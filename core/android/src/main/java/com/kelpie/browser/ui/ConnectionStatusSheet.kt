package com.kelpie.browser.ui

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kelpie.browser.agent.session.AndroidAgentProcessSession
import com.kelpie.browser.device.DeviceInfo
import com.kelpie.browser.network.McpActivityEvidence
import com.kelpie.browser.network.NetworkServiceState
import com.kelpie.browser.network.TunnelReadinessProbe
import com.kelpie.browser.network.TunnelReadinessSnapshot
import com.kelpie.browser.network.mcpActivityLabel
import com.kelpie.browser.network.tunnelReadyLabel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Text and symbols accompany every color: never treat tunnel health as agent authorization. */
internal enum class ConnectionTone { GOOD, BAD, UNKNOWN }

internal fun tunnelTone(healthStatus: Int?, readyStatus: Int?): ConnectionTone =
    when {
        healthStatus == null || (healthStatus == 200 && readyStatus == null) -> ConnectionTone.UNKNOWN
        healthStatus != 200 || readyStatus != 200 -> ConnectionTone.BAD
        else -> ConnectionTone.GOOD
    }

@Composable
internal fun ConnectionStatusSheet(
    deviceInfo: DeviceInfo,
    onShowPairedComputers: () -> Unit,
    onManageAgent: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var result by remember { mutableStateOf<TunnelReadinessSnapshot?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var lastCheckedAt by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    var mcpAgeMs by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val nativeConsent by AndroidAgentProcessSession.active.collectAsState()

    fun updateEvidence() {
        mcpAgeMs = McpActivityEvidence.elapsedSinceLastRequestMs()
    }

    // No background polling, token access or Termux filesystem access.
    LaunchedEffect(Unit) {
        refreshing = true
        updateEvidence()
        result = TunnelReadinessProbe.check()
        lastCheckedAt = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())
        updateEvidence()
        refreshing = false
    }

    val browserRunning = NetworkServiceState.snapshot()?.httpServer?.isRunning == true
    val tunnelLabel =
        when {
            refreshing -> "Checking"
            result == null -> "Not checked"
            else -> tunnelReadyLabel(result?.healthStatus, result?.readyStatus)
        }
    val tunnelStatus =
        if (refreshing) ConnectionTone.UNKNOWN else tunnelTone(result?.healthStatus, result?.readyStatus)
    val mcpLabel = mcpActivityLabel(mcpAgeMs)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Connection Status", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Each status is separate. Local tunnel readiness does not prove AI control.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        StatusDetail(
            "Kelpie browser",
            if (browserRunning) "HTTP service running" else "HTTP service stopped",
            if (browserRunning) ConnectionTone.GOOD else ConnectionTone.BAD,
        )
        StatusDetail("Browser port", deviceInfo.port.toString(), ConnectionTone.UNKNOWN)
        StatusDetail("Secure tunnel", tunnelLabel, tunnelStatus)
        StatusDetail(
            "MCP traffic",
            mcpLabel,
            if (mcpAgeMs != null && mcpAgeMs!! in 0L..120_000L) ConnectionTone.GOOD else ConnectionTone.UNKNOWN,
        )
        StatusDetail(
            "Owner consent",
            when {
                nativeConsent == null -> "Not approved"
                nativeConsent?.locked == true -> "Paused — device locked"
                else -> "Approved for this app session"
            },
            if (nativeConsent?.locked == false) ConnectionTone.GOOD else ConnectionTone.UNKNOWN,
        )
        StatusDetail("AI browser control", "Not tested by this panel", ConnectionTone.UNKNOWN)
        if (onManageAgent != null) {
            TextButton(onClick = onManageAgent, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Manage AI agent approval")
            }
        }
        Text(
            "If the tunnel is ready but tabs are blocked, check the agent identity and permissions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                refreshing = true
                scope.launch {
                    updateEvidence()
                    result = TunnelReadinessProbe.check()
                    lastCheckedAt = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())
                    updateEvidence()
                    refreshing = false
                }
            },
            enabled = !refreshing,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(if (refreshing) "Checking..." else "Check again")
        }
        TextButton(onClick = onShowPairedComputers, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Trusted computers")
        }
        HorizontalDivider()
        TextButton(
            onClick = { advanced = !advanced },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(if (advanced) "Hide advanced diagnostics" else "Advanced diagnostics")
        }
        if (advanced) {
            StatusDetail("Tunnel probe", "127.0.0.1:8080 (diagnostic target)", ConnectionTone.UNKNOWN)
            StatusDetail("Health endpoint", "GET /healthz", ConnectionTone.UNKNOWN)
            StatusDetail("Ready endpoint", "GET /readyz", ConnectionTone.UNKNOWN)
            StatusDetail("Last checked", lastCheckedAt ?: "Not checked", ConnectionTone.UNKNOWN)
            StatusDetail("Health HTTP", result?.healthStatus?.toString() ?: "No response", ConnectionTone.UNKNOWN)
            StatusDetail("Ready HTTP", result?.readyStatus?.toString() ?: "No response", ConnectionTone.UNKNOWN)
            Text(
                "No response can also mean the diagnostic port is different or inaccessible; it is not proof the remote tunnel is down.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "No tokens, URLs, request bodies, private keys or raw logs are collected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Close")
        }
    }
}

@Composable
private fun StatusDetail(
    label: String,
    value: String,
    tone: ConnectionTone,
) {
    val dark = isSystemInDarkTheme()
    val color =
        when (tone) {
            ConnectionTone.GOOD -> if (dark) Color(0xFF81C784) else Color(0xFF1B5E20)
            ConnectionTone.BAD -> if (dark) Color(0xFFFFB4AB) else Color(0xFFB3261E)
            ConnectionTone.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val glyph =
        when (tone) {
            ConnectionTone.GOOD -> "✓"
            ConnectionTone.BAD -> "×"
            ConnectionTone.UNKNOWN -> "?"
        }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$label:",
            modifier = Modifier.weight(0.43f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(modifier = Modifier.weight(0.57f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(glyph, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(value, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
