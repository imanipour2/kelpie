package com.kelpie.browser.agent.execution

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

/** Owns native listener registration only; all consent/ownership decisions stay in the coordinator. */
internal class R5LifecycleSignals(
    private val activity: ComponentActivity,
    onFocusRegained: () -> Unit,
    onScreenOff: () -> Unit,
) : AutoCloseable {
    private val observer = activity.window.decorView.viewTreeObserver
    private val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) onFocusRegained() }
    private val screenOff =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) onScreenOff()
            }
        }
    private var closed = false

    init {
        if (observer.isAlive) observer.addOnWindowFocusChangeListener(focus)
        try {
            ContextCompat.registerReceiver(activity, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (failure: RuntimeException) {
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focus)
            throw failure
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            activity.unregisterReceiver(screenOff)
        } finally {
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focus)
        }
    }
}
