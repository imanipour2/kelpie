package com.kelpie.browser.ui

import android.net.Uri
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
import androidx.compose.ui.unit.dp
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
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val request = pending
    if (request != null) {
        val activeTab = tabStore.activeTab
        val url = activeTab?.currentUrl.orEmpty()
        val origin =
            runCatching {
                val parsed = Uri.parse(url)
                parsed.scheme + "://" + parsed.host
            }.getOrDefault("نامشخص")
        AlertDialog(
            onDismissRequest = { coordinator.denyOnDevice() },
            title = { Text("تأیید محدود تصویربرداری در کلپی") },
            text = {
                Text(
                    "کلاینت احرازشده: " + request.clientId + "\n" +
                        "درخواست: " + request.attemptId + "\n" +
                        "وب‌سایت: " + origin + "\n\n" +
                        "آیا اجازه می‌دهید فقط تب فعلی، تا ۹۰ ثانیه و حداکثر " +
                        "۲۰ فریم تصویربرداری شود؟ تصویربرداری از برنامه‌های دیگر " +
                        "مجاز نیست. با بازگشت به کلپی یا دکمه «توقف» مجوز باطل می‌شود. " +
                        "اطلاعات حساس نمایش‌داده‌شده ممکن است در تصویر دیده شوند.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val requestId = request.requestId
                        scope.launch {
                            if (!coordinator.approveOnDevice(requestId)) {
                                errorMessage = "درخواست منقضی شد یا هویت تب تغییر کرد؛ مجوز صادر نشد."
                            }
                        }
                    },
                ) {
                    Text("تأیید و شروع")
                }
            },
            dismissButton = {
                TextButton(onClick = { coordinator.denyOnDevice() }) {
                    Text("لغو")
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
                    Text("توقف فوری تصویربرداری")
                }
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("مجوز صادر نشد") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("بستن") }
            },
        )
    }
}
