package com.kelpie.browser.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kelpie.browser.R
import com.kelpie.browser.browser.TabStore
import com.kelpie.browser.capture.PrivateCaptureCoordinator
import com.kelpie.browser.capture.PrivateWebViewDisplay
import kotlinx.coroutines.launch

/**
 * Native-only user approval. An HTTP/MCP caller can request this sheet,
 * but no remote method can trigger its affirmative callback.
 *
 * The app currently enables this only for the isolated PC QA build.
 */
@Composable
fun PrivateCaptureConsentDialog(
    coordinator: PrivateCaptureCoordinator,
    tabStore: TabStore,
) {
    val pending by coordinator.pending.collectAsState()
    val captureState by coordinator.rendererState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var afterNotificationPermission by remember { mutableStateOf<String?>(null) }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            val requestId = afterNotificationPermission
            afterNotificationPermission = null
            if (allowed && requestId != null) {
                scope.launch {
                    if (!coordinator.approveOnDevice(requestId)) {
                        errorMessage = context.getString(R.string.p4_capture_error_denied)
                    }
                }
            } else {
                errorMessage = context.getString(R.string.p4_capture_error_notification)
            }
        }

    val request = pending
    if (request != null) {
        val activeTab = tabStore.activeTab
        val url = activeTab?.currentUrl.orEmpty()
        val origin =
            runCatching {
                val parsed = Uri.parse(url)
                parsed.scheme + "://" + parsed.host
            }.getOrDefault(stringResource(R.string.p4_capture_unknown_site))
        AlertDialog(
            onDismissRequest = { coordinator.denyOnDevice() },
            title = { Text(stringResource(R.string.p4_capture_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.p4_capture_disclosure,
                        request.clientId,
                        request.attemptId,
                        origin,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val requestId = request.requestId
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            afterNotificationPermission = requestId
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            scope.launch {
                                if (!coordinator.approveOnDevice(requestId)) {
                                    errorMessage = context.getString(R.string.p4_capture_error_expired)
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.p4_capture_approve))
                }
            },
            dismissButton = {
                TextButton(onClick = { coordinator.denyOnDevice() }) {
                    Text(stringResource(R.string.p4_capture_cancel))
                }
            },
        )
    }

    if (captureState == PrivateWebViewDisplay.State.PRIVATE_DISPLAY) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(16.dp),
            ) {
                Button(
                    onClick = { scope.launch { coordinator.stopByUser() } },
                    modifier = Modifier.padding(8.dp),
                ) {
                    Text(stringResource(R.string.p4_capture_stop))
                }
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource(R.string.p4_capture_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text(stringResource(R.string.p4_capture_dismiss)) }
            },
        )
    }
}
