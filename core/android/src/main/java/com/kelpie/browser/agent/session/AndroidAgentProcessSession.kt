package com.kelpie.browser.agent.session

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-memory-only grant authority. Never save the generation in preferences,
 * SessionStore, Android bundles, notification extras, or logs.
 *
 * The Activity owns only the prompt UI; ordinary HOME and rotation do not grant
 * or revoke authority. Process death reconstructs this singleton as OFF.
 */
internal object AndroidAgentProcessSession {
    private val core = SessionConsentCore(SystemClock::elapsedRealtime)
    private val pendingState = MutableStateFlow<SessionConsentCore.State.Pending?>(null)
    private val activeState = MutableStateFlow<SessionConsentCore.State.Active?>(null)

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

    /** Local STOP and verified owner-stop converge here, including notification STOP. */
    @Synchronized
    fun stop() {
        val prior = core.snapshot()
        core.stop()
        if (prior != SessionConsentCore.State.Off) {
            Log.i("R5ConsentState", "consent stopped from " + stateKind(prior))
        }
        publish()
    }

    /** Pairing revocation cannot leave a cached one-tap principal or native grant. */
    @Synchronized
    fun revokePrincipal(principalId: String) {
        val wasRevoked = core.revokePrincipal(principalId)
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
