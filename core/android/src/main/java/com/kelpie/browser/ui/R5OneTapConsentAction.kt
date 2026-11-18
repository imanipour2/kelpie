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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kelpie.browser.agent.session.AgentSessionCoordinator

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
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            val selected = pendingPrincipal
            pendingPrincipal = null
            if (allowed && selected != null && coordinator.beginOneTapFromNativeUi(selected)) {
                error = null
                onGranted()
            } else {
                error = "Agent control not started. Check the verified agent, unlock, and STOP notification."
            }
        }
    val start = {
        // Capture the displayed principal at the moment of the actual tap.
        val selected = principal
        if (pendingPrincipal == null) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                pendingPrincipal = selected
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (coordinator.beginOneTapFromNativeUi(selected)) {
                error = null
                onGranted()
            } else {
                error = "Agent control not started. Check the verified agent, unlock, and STOP notification."
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
