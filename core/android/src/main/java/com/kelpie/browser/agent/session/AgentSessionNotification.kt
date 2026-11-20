package com.kelpie.browser.agent.session

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
import androidx.core.content.ContextCompat
import com.kelpie.browser.R

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
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(CHANNEL)
        val groupBlocked =
            channel?.group?.let { manager.getNotificationChannelGroup(it)?.isBlocked == true } ?: false
        return permission &&
            manager.areNotificationsEnabled() &&
            stopChannelVisible(channel?.importance, groupBlocked)
    }

    fun show(
        context: Context,
        fullAgentQa: Boolean = false,
    ): Boolean {
        if (!canNotify(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.r5_agent_notification_channel), NotificationManager.IMPORTANCE_LOW),
            )
            // A user-blocked channel survives createNotificationChannel. Never activate
            // consent when STOP would be invisible despite app-wide permission.
            if (!canNotify(context)) return@runCatching false
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
                    .setContentTitle(context.getString(R.string.r5_agent_notification_title))
                    .setContentText(
                        context.getString(
                            if (fullAgentQa) R.string.r5_agent_full_notification_desc else R.string.r5_agent_notification_desc,
                        ),
                    ).setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .addAction(android.R.drawable.ic_media_pause, context.getString(R.string.r5_agent_notification_stop), pending)
                    .build()
            manager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
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
        // This receiver may start in a fresh process before any Activity/server.
        // Persist the owner action using its own Context before any reconnect.
        val saved = AndroidAgentProcessSession.pauseByOwner(context)
        if (!saved) {
            android.util.Log.e("R5Connection", "OWNER_STOP_PERSISTENCE_FAILED")
            android.widget.Toast.makeText(context, "Control is paused now. The pause could not be saved. Retry STOP before restarting Kelpie.", android.widget.Toast.LENGTH_LONG).show()
        }
        AgentSessionNotification.hide(context)
    }
}

/** Pure policy seam for a channel disabled independently of app-level permission. */
internal fun stopChannelVisible(
    importance: Int?,
    groupBlocked: Boolean,
): Boolean = (importance == null || importance > NotificationManager.IMPORTANCE_NONE) && !groupBlocked
