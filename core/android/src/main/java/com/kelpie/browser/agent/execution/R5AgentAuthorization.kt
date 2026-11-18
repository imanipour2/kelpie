package com.kelpie.browser.agent.execution

import android.app.KeyguardManager
import android.os.PowerManager
import androidx.activity.ComponentActivity
import com.kelpie.browser.agent.session.AgentSessionNotification
import com.kelpie.browser.agent.session.SessionConsentCore

/** Rechecked before dispatch and after awaiting a private frame. */
internal object R5AgentAuthorization {
    fun permits(
        activity: ComponentActivity,
        client: String?,
        expectedGeneration: String?,
        active: SessionConsentCore.State.Active?,
        closed: Boolean,
    ): Boolean =
        !closed &&
            client != null &&
            expectedGeneration != null &&
            active != null &&
            !active.locked &&
            active.principalId == client &&
            active.generation == expectedGeneration &&
            activity.getSystemService(PowerManager::class.java).isInteractive &&
            !activity.getSystemService(KeyguardManager::class.java).isDeviceLocked &&
            AgentSessionNotification.canNotify(activity)
}
