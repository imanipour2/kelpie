package com.kelpie.browser.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Builder
import androidx.core.content.ContextCompat

private const val RECOVERY_NOTIFICATION_ID = 0x4B455243

/**
 * A conspicuous, user-mediated permission request. The app cannot bring an
 * Activity to the front from an ordinary background network command.
 * Heads-up visibility is controlled by Android/user channel settings and DND.
 */
internal object KelpieRecoveryNotification {
    // New channel ID: Android does not let an app upgrade an existing channel
    // from DEFAULT to HIGH once the channel has been created.
    private const val CHANNEL_ID = "kelpie_recovery_prompt_v2"

    @Synchronized
    fun request(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Kelpie foreground access requests",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Prominent notification for user-approved Kelpie foreground access requested by ChatGPT"
                },
            )
        }
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return false
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false

        // Do not repeatedly alert while the same user-consent request is
        // already visible. A new request after dismissal may appear again.
        val promptAlreadyVisible =
            manager.activeNotifications.any {
                it.id == RECOVERY_NOTIFICATION_ID && it.notification.channelId == CHANNEL_ID
            }
        if (promptAlreadyVisible) return true

        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            } ?: return false
        val open =
            PendingIntent.getActivity(
                context,
                RECOVERY_NOTIFICATION_ID,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val dismiss =
            PendingIntent.getBroadcast(
                context,
                RECOVERY_NOTIFICATION_ID + 1,
                Intent(context, KelpieRecoveryDismissReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val explanation = "ChatGPT needs Kelpie in the foreground to capture the browser screen. Tap Allow to open Kelpie, or Not now to dismiss."
        val notification =
            Builder(context, CHANNEL_ID)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle("ChatGPT requests to open Kelpie")
                .setContentText("Your approval is needed to continue browser screen capture.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(explanation))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(open)
                .addAction(android.R.drawable.ic_menu_view, "Allow & open Kelpie", open)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Not now", dismiss)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

        manager.notify(RECOVERY_NOTIFICATION_ID, notification)
        return true
    }
}

/** Explicit decline: dismiss only this prompt. Never starts any Activity. */
class KelpieRecoveryDismissReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        context.getSystemService(NotificationManager::class.java)?.cancel(RECOVERY_NOTIFICATION_ID)
    }
}
