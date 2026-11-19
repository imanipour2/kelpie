package com.kelpie.browser.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Optional owner-initiated QA bridge into the ALREADY installed Termux.
 *
 * Only an explicit native tap may launch a fixed, pre-reviewed local script.
 * Never passes a token, arbitrary script, URL or agent principal as an Intent extra.
 * Termux can request native consent; it cannot grant it. The actual pending/active
 * native session state is the sole authority, not a RunCommand dispatch result.
 */
internal object R5TermuxOwnerReconnect {
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val PACKAGE = "com.termux"
    const val SERVICE = "com.termux.app.RunCommandService"
    const val ACTION = "com.termux.RUN_COMMAND"
    const val SCRIPT = "/data/data/com.termux/files/home/.local/bin/ef-kelpie-request-consent"
    const val MIN_RETRY_MS = 15_000L

    enum class Dispatch {
        QUEUED_NOT_CONFIRMED,
        OWNER_APPROVAL_REQUIRED,
        PERMISSION_REQUIRED,
        TERMUX_UNAVAILABLE,
        START_FAILED,
    }

    fun canTryAgain(
        nowElapsed: Long,
        lastAttemptElapsed: Long?,
    ): Boolean =
        lastAttemptElapsed == null ||
            nowElapsed < lastAttemptElapsed ||
            nowElapsed - lastAttemptElapsed >= MIN_RETRY_MS

    fun admission(
        ownerApprovedFallback: Boolean,
        permissionGranted: Boolean,
    ): Dispatch? =
        when {
            !ownerApprovedFallback -> Dispatch.OWNER_APPROVAL_REQUIRED
            !permissionGranted -> Dispatch.PERMISSION_REQUIRED
            else -> null
        }

    fun sendExplicitOwnerRequest(
        context: Context,
        ownerApprovedFallback: Boolean,
    ): Dispatch {
        val rejected =
            admission(
                ownerApprovedFallback,
                ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED,
            )
        if (rejected != null) return rejected
        val intent =
            Intent(ACTION).apply {
                component = ComponentName(PACKAGE, SERVICE)
                putExtra("com.termux.RUN_COMMAND_PATH", SCRIPT)
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "Kelpie owner reconnect")
            }
        return try {
            if (context.startService(intent) != null) {
                Dispatch.QUEUED_NOT_CONFIRMED
            } else {
                Dispatch.TERMUX_UNAVAILABLE
            }
        } catch (_: SecurityException) {
            Dispatch.PERMISSION_REQUIRED
        } catch (_: IllegalArgumentException) {
            Dispatch.START_FAILED
        } catch (_: IllegalStateException) {
            Dispatch.START_FAILED
        }
    }
}

@Composable
internal fun R5TermuxOwnerReconnectAction() {
    val context = LocalContext.current
    var lastAttempt by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var queuedAt by remember { mutableStateOf<Long?>(null) }
    var confirmFallback by remember { mutableStateOf(false) }

    // The native sheet is removed as soon as a real pending/active session is observed.
    // If this component stays mounted, the request has not been seen by native UI.
    LaunchedEffect(queuedAt) {
        val attempt = queuedAt
        if (attempt != null) {
            delay(12_000L)
            if (queuedAt == attempt) {
                message =
                    "هنوز درخواست تأیید در Kelpie مشاهده نشده است. " +
                    "مجوز Termux، اسکریپت ثابت و هویت عامل را بررسی کنید؛ اتصال را فرض نکنید."
            }
        }
    }

    if (confirmFallback) {
        AlertDialog(
            onDismissRequest = { confirmFallback = false },
            title = { Text("اجرای اختیاری پل Termux موجود") },
            text = {
                Text(
                    "فقط اسکریپت ثابت نصب‌شده برای درخواست تأیید اجرا می‌شود؛ " +
                        "هیچ اعتبارنامه‌ای به Termux ارسال نمی‌شود. " +
                        "این درخواست جای تأیید هویت و مجوز داخل Kelpie را نمی‌گیرد.",
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmFallback = false }) { Text("ادامه با اتصال بومی") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmFallback = false
                        val now = SystemClock.elapsedRealtime()
                        if (!R5TermuxOwnerReconnect.canTryAgain(now, lastAttempt)) {
                            message = "درخواست قبلی تازه ارسال شده است؛ ابتدا وضعیت تأیید را بررسی کنید."
                        } else {
                            when (R5TermuxOwnerReconnect.sendExplicitOwnerRequest(context, ownerApprovedFallback = true)) {
                                R5TermuxOwnerReconnect.Dispatch.QUEUED_NOT_CONFIRMED -> {
                                    lastAttempt = now
                                    queuedAt = now
                                    message =
                                        "فرمان به Termux تحویل شد؛ هنوز اتصال تأیید نشده است. " +
                                        "فقط اگر پنجره تأیید Kelpie ظاهر شد، هویت عامل را بررسی و تأیید کنید."
                                }
                                R5TermuxOwnerReconnect.Dispatch.PERMISSION_REQUIRED -> {
                                    message =
                                        "مجوز اجرای فرمان Termux از قبل آماده نیست. " +
                                        "اتصال بومی Kelpie را ادامه دهید؛ هیچ تنظیمی تغییر نکرد."
                                }
                                R5TermuxOwnerReconnect.Dispatch.OWNER_APPROVAL_REQUIRED -> {
                                    message = "پل Termux بدون انتخاب صریح مالک اجرا نمی‌شود."
                                }
                                R5TermuxOwnerReconnect.Dispatch.TERMUX_UNAVAILABLE -> {
                                    message = "سرویس Termux موجود در دسترس نیست. برنامه دیگری نصب نکنید."
                                }
                                R5TermuxOwnerReconnect.Dispatch.START_FAILED -> {
                                    message = "اجرای فرمان Termux ناموفق بود؛ هیچ مجوز تازه‌ای صادر نشده است."
                                }
                            }
                        }
                    },
                ) { Text("درخواست با پل موجود") }
            },
        )
    }

    Column {
        Text("اگر اتصال بومی در دسترس نیست، می‌توانید پل Termux موجود را جداگانه انتخاب کنید.")
        Button(
            onClick = { confirmFallback = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("درخواست اتصال از Termux") }
        if (message != null) {
            Text(message.orEmpty(), modifier = Modifier.padding(top = 8.dp))
        }
    }
}
