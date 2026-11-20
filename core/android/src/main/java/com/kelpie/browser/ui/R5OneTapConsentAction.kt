package com.kelpie.browser.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kelpie.browser.agent.session.AgentSessionCoordinator
import com.kelpie.browser.agent.session.NativeOneTapResult

/**
 * The button itself is the owner's affirmative native consent gesture.
 * No remote caller, Compose effect, or OS permission callback independently
 * initiates consent. The callback can only complete the SAME explicit gesture
 * after rechecking its originally selected principal in the native coordinator.
 */
@Composable
internal fun R5OneTapConsentAction(
    coordinator: AgentSessionCoordinator,
    principal: String,
    compact: Boolean = false,
    onGranted: () -> Unit = {},
) {
    val context = LocalContext.current
    var pendingPrincipal by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun report(result: NativeOneTapResult) {
        if (result == NativeOneTapResult.GRANTED) {
            error = null
            onGranted()
        } else {
            error = nativeConsentErrorLabel(result)
        }
    }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            val selected = pendingPrincipal
            pendingPrincipal = null
            if (allowed && selected != null) {
                report(coordinator.beginOneTapFromNativeUiWithReason(selected))
            } else {
                error = "Notification permission is required for the visible STOP control. No access was granted."
            }
        }
    val start = {
        // Capture the displayed principal at the moment of the actual tap.
        val selected = principal
        if (pendingPrincipal == null) {
            error = null
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                pendingPrincipal = selected
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                report(coordinator.beginOneTapFromNativeUiWithReason(selected))
            }
        }
    }
    Column {
        if (compact) {
            TextButton(onClick = start, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Allow control for this session")
            }
        } else {
            Button(onClick = start, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Allow control for this session")
            }
        }
        if (error != null) {
            Text(error ?: "")
        }
    }
}

/** A denial is actionable without leaking who the authenticated caller is. */
internal fun nativeConsentErrorLabel(result: NativeOneTapResult): String =
    when (result) {
        NativeOneTapResult.GRANTED -> "Approved for this app session."
        NativeOneTapResult.CLIENT_EXPIRED ->
            "Verified client expired. Send a fresh MCP request in ChatGPT, then tap Refresh verified clients."
        NativeOneTapResult.CLIENT_CHANGED, NativeOneTapResult.INVALID_CLIENT ->
            "The verified client changed or is ambiguous. Reconnect the intended MCP client and refresh."
        NativeOneTapResult.ACTIVITY_NOT_RESUMED, NativeOneTapResult.ACTIVITY_NOT_VISIBLE ->
            "Kelpie is not fully foregrounded. Close overlays, return to Kelpie, then try again."
        NativeOneTapResult.DEVICE_LOCKED ->
            "Unlock the phone and try again; no remote control was granted."
        NativeOneTapResult.STOP_NOTIFICATION_UNAVAILABLE ->
            "Enable Kelpie notifications and the agent-session STOP channel before approving."
        NativeOneTapResult.COORDINATOR_CLOSED ->
            "Kelpie's previous screen has closed. Reopen Connect to an AI agent."
        NativeOneTapResult.PAIRING_REVOKED ->
            "This computer is no longer trusted. Reconnect and approve the computer in Paired Clients."
        NativeOneTapResult.SESSION_BUSY ->
            "A consent request is already pending or active. Check session status or use STOP before retrying."
        NativeOneTapResult.APPROVAL_FAILED ->
            "Approval failed after the tap. Reopen the connection screen and inspect notification availability."
    }
