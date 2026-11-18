package com.kelpie.browser.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

/**
 * Device-local, read-only connectivity diagnostics.
 * A local tunnel response is never evidence of remote account identity or agent control.
 */
@Composable
internal fun ConnectionStatusSheet(
    deviceInfo: DeviceInfo,
    onShowPairedComputers: () -> Unit,
    onDismiss: () -> Unit,
) {
    var result by remember { mutableStateOf<TunnelReadinessSnapshot?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var lastCheckedAt by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    var mcpAgeMs by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    fun updateEvidence() {
        mcpAgeMs = McpActivityEvidence.elapsedSinceLastRequestMs()
    }

    // Probe only while the owner opens the sheet. No periodic background polling.
    LaunchedEffect(Unit) {
        refreshing = true
        updateEvidence()
        result = TunnelReadinessProbe.check()
        lastCheckedAt = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())
        updateEvidence()
        refreshing = false
    }

    val stage = NetworkServiceState.snapshot()
    val browserRunning = stage?.httpServer?.isRunning == true
    val health = result?.healthOk == true
    val ready = result?.readyOk == true

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Connection Status")
        Text("Local checks only. A ready tunnel does not prove an AI agent can control the browser.")
        HorizontalDivider()
        StatusDetail("Kelpie browser", if (browserRunning) "HTTP service running" else "HTTP service stopped")
        StatusDetail("Browser port", deviceInfo.port.toString())
        StatusDetail(
            "Tunnel health",
            when {
                refreshing -> "Checking..."
                result == null -> "Not checked"
                else -> tunnelReadyLabel(health, ready)
            },
        )
        StatusDetail("MCP request activity", mcpActivityLabel(mcpAgeMs))
        StatusDetail("AI browser control", "Not verified by this local diagnostic")
        Text(
            "If ChatGPT can reach capabilities but cannot list tabs, check the actual agent permissions " +
                "and MCP tool contract. Tunnel readiness alone cannot diagnose that failure.",
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
        TextButton(onClick = onShowPairedComputers, modifier = Modifier.fillMaxWidth()) {
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
            StatusDetail("Local tunnel diagnostics", "127.0.0.1:8080 (probe target)")
            StatusDetail("Health endpoint", "GET /healthz")
            StatusDetail("Readiness endpoint", "GET /readyz")
            StatusDetail(
                "Probe timestamp",
                lastCheckedAt ?: "Not checked",
            )
            StatusDetail("HTTP health", result?.healthStatus?.toString() ?: "No HTTP response")
            StatusDetail("HTTP readiness", result?.readyStatus?.toString() ?: "No HTTP response")
            Text(
                "An unreachable local diagnostics endpoint may mean Termux is stopped, listening " +
                    "on another port, or inaccessible to this app. It does not prove the tunnel is down.",
            )
            Text("No credentials, request bodies or raw tunnel logs are collected.")
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
    }
}

@Composable
private fun StatusDetail(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(0.45f))
        Text(value, modifier = Modifier.weight(0.55f))
    }
}
