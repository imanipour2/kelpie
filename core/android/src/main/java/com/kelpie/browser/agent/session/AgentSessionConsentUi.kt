package com.kelpie.browser.agent.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

/** Explicit local tap, never remotely synthesized. Independent of a website URL. */
@Composable
fun AgentSessionConsentUi(coordinator: AgentSessionCoordinator) {
    val pending by coordinator.pending.collectAsState()
    val active by coordinator.active.collectAsState()
    val context = LocalContext.current
    var pendingPermissionPrompt by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { approved ->
            val prompt = pendingPermissionPrompt
            pendingPermissionPrompt = null
            if (!approved || prompt == null || !coordinator.approveFromNativeUi(prompt)) {
                error = context.getString(R.string.r5_agent_error_notification)
            }
        }

    val request = pending
    if (request != null) {
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
                    onClick = {
                        val promptId = request.promptId
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingPermissionPrompt = promptId
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!coordinator.approveFromNativeUi(promptId)) {
                            error = context.getString(R.string.r5_agent_error_locked)
                        }
                    },
                ) { Text(stringResource(R.string.r5_agent_approve)) }
            },
            dismissButton = {
                TextButton(onClick = coordinator::denyFromNativeUi) { Text(stringResource(R.string.r5_agent_deny)) }
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
                TextButton(onClick = { error = null }) { Text(stringResource(R.string.r5_agent_close)) }
            },
        )
    }
}
