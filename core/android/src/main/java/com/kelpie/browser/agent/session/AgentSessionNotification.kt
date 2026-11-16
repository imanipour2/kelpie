package com.kelpie.browser.agent.session

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Independent local STOP affordance, including while the Activity is backgrounded.
 * No account ID, URL, bearer, DOM, screenshot, or grant token enters the notification.
 */
internal object AgentSessionNotification {
    const val ACTION_STOP = "com.kelpie.browser.R5_AGENT_SESSION_STOP"
    private const val CHANNEL = "kelpie_r5_agent_qa"
    private const val NOTIFICATION_ID = 18457

    fun canNotify(context: Context): Boolean {
        val permission =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val enabled = context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
        return permission && enabled
    }

    fun show(context: Context): Boolean {
        if (!canNotify(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Kelpie agent QA", NotificationManager.IMPORTANCE_LOW),
            )
            val stopIntent =
                Intent(context, AgentSessionStopReceiver::class.java).apply {
                    action = ACTION_STOP
                    setPackage(context.packageName)
                }
            val pending =
                PendingIntent.getBroadcast(
                    context,
                    NOTIFICATION_ID,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setContentTitle("Kelpie agent session active (QA)")
                    .setContentText("Paired browser control granted; STOP to revoke")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .addAction(android.R.drawable.ic_media_pause, "STOP", pending)
                    .build()
            manager.notify(NOTIFICATION_ID, notification)
        }.isSuccess
    }

    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}

/** Manifest receiver is non-exported. An old STOP intent never grants authority. */
class AgentSessionStopReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != AgentSessionNotification.ACTION_STOP) return
        AndroidAgentProcessSession.stop()
        AgentSessionNotification.hide(context)
    }
}
