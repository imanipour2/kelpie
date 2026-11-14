package com.kelpie.browser.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * User-mediated return to Kelpie. An authenticated remote call may post this
 * notification but NEVER starts an Activity or bypasses the device lock.
 */
internal object KelpieRecoveryNotification {
    private const val CHANNEL_ID = "kelpie_recovery"
    private const val NOTIFICATION_ID = 0x4B455243
    private const val COOLDOWN_MS = 15_000L
    private var lastPostedAt = 0L

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
                NotificationChannel(CHANNEL_ID, "Kelpie recovery", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false

        val now = SystemClock.elapsedRealtime()
        if (lastPostedAt != 0L && now - lastPostedAt < COOLDOWN_MS) return true

        val intent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            } ?: return false
        val open =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        val notification =
            builder
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle("Kelpie: return to browser")
                .setContentText("Tap here to resume screenshot capture in Kelpie")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
        lastPostedAt = now
        return true
    }
}
