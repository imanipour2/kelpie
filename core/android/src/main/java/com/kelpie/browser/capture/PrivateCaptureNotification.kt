package com.kelpie.browser.capture

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.kelpie.browser.R
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Persistent, non-sensitive, user-visible STOP affordance for a locally
 * approved capture. Separate from normal Kelpie's connection notification.
 */
internal object PrivateCaptureNotification {
    private const val CHANNEL = "kelpie_private_capture"
    private const val ID = 18437
    private const val ACTION_STOP = "com.kelpie.browser.PRIVATE_CAPTURE_STOP"
    private var active = WeakReference<PrivateCaptureCoordinator>(null)

    fun canNotify(context: Context): Boolean {
        val permission =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return permission && context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    fun show(
        activity: Activity,
        coordinator: PrivateCaptureCoordinator,
    ): Boolean {
        if (!canNotify(activity)) return false
        val manager = activity.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, activity.getString(R.string.p4_capture_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val stopIntent =
            Intent(activity, PrivateCaptureStopReceiver::class.java).apply {
                action = ACTION_STOP
                setPackage(activity.packageName)
            }
        val stopPending =
            PendingIntent.getBroadcast(
                activity,
                ID,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopAction =
            Notification.Action
                .Builder(
                    android.R.drawable.ic_media_pause,
                    activity.getString(R.string.p4_capture_notification_stop),
                    stopPending,
                ).build()
        val notification =
            Notification
                .Builder(activity, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle(activity.getString(R.string.p4_capture_notification_title))
                .setContentText(activity.getString(R.string.p4_capture_notification_description))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(stopAction)
                .build()
        active = WeakReference(coordinator)
        manager.notify(ID, notification)
        return true
    }

    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
        active.clear()
    }

    fun activeCoordinator(action: String?): PrivateCaptureCoordinator? = if (action == ACTION_STOP) active.get() else null
}

/** Non-exported receiver. STOP always revokes consent before it touches UI. */
class PrivateCaptureStopReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val coordinator = PrivateCaptureNotification.activeCoordinator(intent.action) ?: return
        val completion = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                coordinator.stopByUser()
            } finally {
                PrivateCaptureNotification.hide(context)
                completion.finish()
            }
        }
    }
}
