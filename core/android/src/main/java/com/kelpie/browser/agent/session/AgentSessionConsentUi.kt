package com.kelpie.browser.agent.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.text.BidiFormatter
import com.kelpie.browser.R
import kotlinx.coroutines.delay

/**
 * All actual approval happens from a local Compose click (or after the OS notification
 * permission callback that the owner explicitly opened from that click).
 * Neither MCP nor HTTP can invoke the native approval callback.
 */
@Composable
fun AgentSessionConsentUi(
    coordinator: AgentSessionCoordinator,
    r5AgentEnabled: Boolean = false,
    onOpenAgentSetup: () -> Unit = {},
    agentSetupVisible: Boolean = false,
) {
    val pending by coordinator.pending.collectAsState()
    val active by coordinator.active.collectAsState()
    val startupRequired by coordinator.startupRequired.collectAsState()
    val observedClients by coordinator.verifiedPrincipals.collectAsState()
    val context = LocalContext.current
    var permissionPrincipal by remember { mutableStateOf<String?>(null) }
    var permissionPrompt by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { approved ->
            val principal = permissionPrincipal
            val prompt = permissionPrompt
            permissionPrincipal = null
            permissionPrompt = null
            error =
                when {
                    !approved -> context.getString(R.string.r5_agent_error_notification)
                    !approveNativeSession(coordinator, prompt, principal) ->
                        context.getString(R.string.r5_agent_error_locked)
                    else -> null
                }
        }

    val request = pending
    LaunchedEffect(request?.promptId, request?.deadlineMs) {
        if (request != null) {
            // Doze time counts; an old timer cannot dismiss a newer request.
            val remainingMs = (request.deadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            delay(remainingMs)
            if (coordinator.expireNativePromptIfDue(request.promptId)) {
                error = context.getString(R.string.r5_agent_error_expired)
            }
        }
    }

    val candidates = if (request != null) listOf(request.principalId) else observedClients
    var selectedPrincipal by remember(candidates, request?.promptId) {
        mutableStateOf(request?.principalId ?: candidates.singleOrNull())
    }

    fun ownerTappedApprove(
        principal: String?,
        promptId: String?,
    ) {
        if (principal == null ||
            (promptId == null && !coordinator.isVerifiedPrincipalFresh(principal))
        ) {
            error = context.getString(R.string.r5_agent_startup_unavailable)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionPrincipal = principal
            permissionPrompt = promptId
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!approveNativeSession(coordinator, promptId, principal)) {
            error = context.getString(R.string.r5_agent_error_locked)
        } else {
            error = null
        }
    }

    if (r5AgentEnabled) {
        // Agent-first R5: no ordinary browser-mode startup without one local choice.
        if (active == null && (startupRequired || request != null) && !agentSetupVisible) {
            AlertDialog(
                onDismissRequest = {}, // A background tap is never consent or Exit.
                title = { Text(stringResource(R.string.r5_agent_startup_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.r5_agent_startup_disclosure))
                        if (candidates.isEmpty()) {
                            Text(stringResource(R.string.r5_agent_startup_wait))
                        } else if (candidates.size == 1) {
                            Text(
                                stringResource(
                                    R.string.r5_agent_startup_identity,
                                    BidiFormatter.getInstance().unicodeWrap(coordinator.principalName(candidates.first())),
                                ),
                            )
                        } else {
                            Text(stringResource(R.string.r5_agent_startup_choose))
                            candidates.forEach { principal ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = selectedPrincipal == principal,
                                        onClick = { selectedPrincipal = principal },
                                    )
                                    TextButton(onClick = { selectedPrincipal = principal }) {
                                        Text(coordinator.principalName(principal))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(
                            enabled = selectedPrincipal != null,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { ownerTappedApprove(selectedPrincipal, request?.promptId) },
                        ) { Text(stringResource(R.string.r5_agent_startup_enable)) }
                        if (candidates.isEmpty()) {
                            TextButton(
                                onClick = onOpenAgentSetup,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.r5_agent_startup_connect)) }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = coordinator::exitFromNativeUi) {
                        Text(stringResource(R.string.r5_agent_startup_exit))
                    }
                },
            )
        }
    } else if (request != null) {
        // Existing consent-only QA behavior remains unchanged.
        AlertDialog(
            onDismissRequest = coordinator::denyFromNativeUi,
            title = { Text(stringResource(R.string.r5_agent_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.r5_agent_disclosure,
                        BidiFormatter.getInstance().unicodeWrap(request.principalId),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { ownerTappedApprove(request.principalId, request.promptId) },
                ) { Text(stringResource(R.string.r5_agent_approve)) }
            },
            dismissButton = {
                TextButton(onClick = coordinator::denyFromNativeUi) {
                    Text(stringResource(R.string.r5_agent_deny))
                }
            },
        )
    }

    if (active != null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Button(
                onClick = coordinator::stopFromNativeUi,
                modifier = Modifier.padding(12.dp),
            ) { Text(stringResource(R.string.r5_agent_stop)) }
        }
    }

    val message = error
    if (message != null) {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text(stringResource(R.string.r5_agent_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { error = null }) {
                    Text(stringResource(R.string.r5_agent_close))
                }
            },
        )
    }
}

private fun approveNativeSession(
    coordinator: AgentSessionCoordinator,
    promptId: String?,
    principal: String?,
): Boolean =
    when {
        principal == null -> false
        promptId == null -> coordinator.beginFromNativeUi(principal)
        coordinator.expireNativePromptIfDue(promptId) -> false
        else -> coordinator.approveFromNativeUi(promptId)
    }
