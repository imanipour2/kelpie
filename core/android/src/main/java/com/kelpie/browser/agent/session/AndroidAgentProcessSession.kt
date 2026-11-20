package com.kelpie.browser.agent.session

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-memory-only grant authority. Never save the generation in preferences,
 * SessionStore, Android bundles, notification extras, or logs.
 *
 * Legacy R5 grants still require the native prompt. The mother profile restores
 * execution state only from an existing, revalidated durable computer approval.
 * Explicit STOP is persisted separately; no secret or generation is persisted.
 */
internal object AndroidAgentProcessSession {
    private val core = SessionConsentCore(SystemClock::elapsedRealtime)
    private val pendingState = MutableStateFlow<SessionConsentCore.State.Pending?>(null)
    private val activeState = MutableStateFlow<SessionConsentCore.State.Active?>(null)
    private var motherContext: Context? = null
    private val pausedState = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = pausedState.asStateFlow()

    /** Called before the mother HTTP listener starts; no permission is granted here. */
    @Synchronized
    fun configureMother(context: Context) {
        motherContext = context.applicationContext
        pausedState.value = pausedState.value || R5BackgroundOptInStore.isControlPaused(context)
        if (pausedState.value) {
            core.stop()
            publish()
        }
    }

    /** Called only after the HTTP middleware revalidates this durable computer key. */
    @Synchronized
    fun restoreTrustedComputer(computerKey: String): SessionConsentCore.State.Active? {
        val context = motherContext ?: return null
        if (pausedState.value || R5BackgroundOptInStore.isControlPaused(context)) return null
        val unlocked =
            context.getSystemService(PowerManager::class.java)?.isInteractive == true &&
                context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false
        core.setDeviceLocked(!unlocked)
        val current = core.snapshot()
        if (!unlocked || current is SessionConsentCore.State.Pending ||
            (current is SessionConsentCore.State.Active && current.principalId != computerKey) ||
            !AgentSessionNotification.show(context, fullAgentQa = true)
        ) {
            publish()
            return null
        }
        val restored = core.restoreTrustedComputer(computerKey, paused = false, deviceUnlocked = true)
        publish()
        return restored
    }

    /** Owner STOP survives process recreation; it never deletes the computer key. */
    @Synchronized
    fun pauseByOwner(context: Context): Boolean {
        pausedState.value = true
        core.stop()
        publish()
        return R5BackgroundOptInStore.setControlPaused(context, true)
    }

    /** Only a native Resume gesture may clear a saved owner STOP. */
    @Synchronized
    fun resumeByOwner(): Boolean {
        val context = motherContext ?: return false
        if (!R5BackgroundOptInStore.setControlPaused(context, false)) return false
        pausedState.value = false
        return true
    }

    @Synchronized
    fun rendererUnavailable(expected: SessionConsentCore.State.Active?) {
        if (expected == null || core.snapshot() != expected) return
        if (motherContext == null) core.stop() else core.invalidateRuntime(expected)
        publish()
    }

    /** Same process-only authority used by the native UI and authenticated R5 runtime. */
    fun runtimeConsent(): SessionConsentCore = core

    val pending: StateFlow<SessionConsentCore.State.Pending?> = pendingState.asStateFlow()
    val active: StateFlow<SessionConsentCore.State.Active?> = activeState.asStateFlow()

    @Synchronized
    fun request(verifiedPrincipalId: String): SessionConsentCore.State.Pending? {
        val result = core.requestNativeConsent(verifiedPrincipalId)
        Log.i("R5ConsentState", if (result != null) "native request pending" else "native request refused")
        publish()
        return result
    }

    /** This entrypoint may be called only by a native affirmative on-device UI. */
    @Synchronized
    fun approveFromNativeUi(
        promptId: String,
        deviceUnlocked: Boolean,
    ): SessionConsentCore.State.Active? {
        val result = core.approveFromNativeUi(promptId, deviceUnlocked)
        Log.i("R5ConsentState", if (result != null) "native grant activated" else "native approval rejected")
        publish()
        return result
    }

    /** Expire only the overdue prompt observed by a native UI timer. */
    @Synchronized
    fun expirePendingIfDue(promptId: String): Boolean {
        val expired = core.expirePendingIfDue(promptId)
        if (expired) publish()
        return expired
    }

    /** Retire transient execution state; explicit owner STOP uses pauseByOwner. */
    @Synchronized
    fun stop() {
        val prior = core.snapshot()
        core.stop()
        if (prior != SessionConsentCore.State.Off) {
            // Retain only the static Kotlin caller method, never principal/token data.
            val owner = Throwable().stackTrace.getOrNull(1)?.methodName ?: "unknown"
            Log.i("R5ConsentState", "consent stopped from " + stateKind(prior) + " via " + owner)
        }
        publish()
    }

    /** Pairing revocation cannot leave a cached one-tap principal or native grant. */
    @Synchronized
    fun revokePrincipal(
        principalId: String,
        expected: SessionConsentCore.State.Active? = null,
    ) {
        val wasRevoked = core.revokePrincipal(principalId, expected)
        if (expected != null && !wasRevoked) return
        R5ObservedAgentClients.registry.forget(principalId)
        if (wasRevoked) Log.i("R5ConsentState", "paired principal revoked; consent off")
        publish()
    }

    @Synchronized
    fun setDeviceLocked(locked: Boolean) {
        val before = core.snapshot()
        core.setDeviceLocked(locked)
        if (before != core.snapshot()) {
            Log.i("R5ConsentState", if (locked) "active consent suspended by lock" else "active consent resumed after unlock")
        }
        publish()
    }

    @Synchronized
    fun snapshot(): SessionConsentCore.State {
        val current = core.snapshot()
        if (current is SessionConsentCore.State.Pending && SystemClock.elapsedRealtime() >= current.deadlineMs) {
            core.stop()
            Log.i("R5ConsentState", "native approval prompt expired")
            publish()
        }
        return core.snapshot()
    }

    private fun stateKind(value: SessionConsentCore.State): String =
        when (value) {
            SessionConsentCore.State.Off -> "off"
            is SessionConsentCore.State.Pending -> "pending"
            is SessionConsentCore.State.Active -> "active"
        }

    private fun publish() {
        val state = core.snapshot()
        pendingState.value = state as? SessionConsentCore.State.Pending
        activeState.value = state as? SessionConsentCore.State.Active
    }
}
