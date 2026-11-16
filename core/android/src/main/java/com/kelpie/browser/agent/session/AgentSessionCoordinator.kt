package com.kelpie.browser.agent.session

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.kelpie.browser.network.Router
import com.kelpie.browser.network.errorResponse
import com.kelpie.browser.network.successResponse
import com.kelpie.browser.network.verifiedClientId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opt-in isolated QA boundary for one native consent per active app process.
 * It registers NO browser action/screenshot/DOM route; operations remain OFF
 * until a separate security-reviewed NativePort and egress adapter exists.
 */
class AgentSessionCoordinator(
    private val activity: ComponentActivity,
    private val fullAgentQa: Boolean = false,
) {
    var runtimeReady: () -> Boolean = { false }
    internal val pending = AndroidAgentProcessSession.pending
    internal val active = AndroidAgentProcessSession.active
    private var closed = false

    private val screenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (!closed && intent.action == Intent.ACTION_SCREEN_OFF) {
                    AndroidAgentProcessSession.setDeviceLocked(true)
                }
            }
        }

    init {
        ContextCompat.registerReceiver(
            activity,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun register(router: Router) {
        router.register("agent-session-request") { _ ->
            val principal =
                verifiedClientId()
                    ?: return@register errorResponse("PERMISSION_REQUIRED", "Authenticated paired client required")
            withContext(Dispatchers.Main.immediate) {
                syncLock()
                if (closed ||
                    deviceLocked() ||
                    !activity.hasWindowFocus() ||
                    !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    return@withContext errorResponse("USER_TAP_REQUIRED", "Open the native Kelpie QA window to consent")
                }
                val pending =
                    AndroidAgentProcessSession.request(principal)
                        ?: return@withContext errorResponse("PERMISSION_REQUIRED", "Consent already active or pending")
                successResponse(
                    mapOf(
                        "status" to "PENDING_NATIVE_APPROVAL",
                        "promptId" to pending.promptId,
                        "approvalTtlMs" to SessionConsentCore.LOCAL_PROMPT_TTL_MS,
                    ),
                )
            }
        }

        router.register("agent-session-status") { _ ->
            val principal =
                verifiedClientId()
                    ?: return@register errorResponse("PERMISSION_REQUIRED", "Authenticated paired client required")
            withContext(Dispatchers.Main.immediate) {
                syncLock()
                when (val state = AndroidAgentProcessSession.snapshot()) {
                    SessionConsentCore.State.Off -> successResponse(mapOf("status" to "OFF"))
                    is SessionConsentCore.State.Pending ->
                        successResponse(
                            mapOf("status" to if (state.principalId == principal) "PENDING_NATIVE_APPROVAL" else "NOT_FOUND"),
                        )
                    is SessionConsentCore.State.Active -> {
                        if (state.principalId != principal) {
                            successResponse(mapOf("status" to "NOT_FOUND"))
                        } else if (state.locked) {
                            successResponse(mapOf("status" to "SUSPENDED_LOCKED"))
                        } else {
                            successResponse(
                                mapOf(
                                    "status" to if (fullAgentQa) "ACTIVE_AGENT_SESSION" else "ACTIVE_CONSENT_ONLY",
                                    "generation" to state.generation,
                                    "safetyEpoch" to state.safetyEpoch,
                                    "browserOperationsEnabled" to (fullAgentQa && runtimeReady()),
                                ),
                            )
                        }
                    }
                }
            }
        }

        router.register("agent-session-stop") { _ ->
            val principal =
                verifiedClientId()
                    ?: return@register errorResponse("PERMISSION_REQUIRED", "Authenticated paired client required")
            withContext(Dispatchers.Main.immediate) {
                val current = AndroidAgentProcessSession.snapshot()
                val owned =
                    when (current) {
                        is SessionConsentCore.State.Active -> current.principalId == principal
                        is SessionConsentCore.State.Pending -> current.principalId == principal
                        SessionConsentCore.State.Off -> false
                    }
                if (!owned) {
                    errorResponse("PERMISSION_REQUIRED", "No owned agent session")
                } else {
                    stopFromNativeUi()
                    successResponse(mapOf("status" to "STOPPED"))
                }
            }
        }
    }

    /** Only the affirmative native dialog callback invokes this, never HTTP. */
    fun approveFromNativeUi(promptId: String): Boolean {
        // Compose AlertDialog is a separate focused window. Requiring the
        // Activity's *main* window focus here rejects a genuine dialog tap.
        // This method is callable only by native onClick, never over HTTP.
        if (closed ||
            !nativeConsentTapAllowed(
                lifecycleResumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                activityVisible = activity.window.decorView.isShown,
                deviceUnlocked = !deviceLocked(),
                stopNotificationEnabled = AgentSessionNotification.canNotify(activity),
            )
        ) {
            return false
        }
        val granted =
            AndroidAgentProcessSession.approveFromNativeUi(promptId, deviceUnlocked = true)
                ?: return false
        if (!AgentSessionNotification.show(activity, fullAgentQa)) {
            AndroidAgentProcessSession.stop()
            return false
        }
        return granted.generation.isNotBlank()
    }

    /** Only the UI timer calls this; an old prompt ID cannot revoke a new request. */
    fun expireNativePromptIfDue(promptId: String): Boolean = AndroidAgentProcessSession.expirePendingIfDue(promptId)

    fun denyFromNativeUi() {
        AndroidAgentProcessSession.stop()
        AgentSessionNotification.hide(activity)
    }

    fun stopFromNativeUi() {
        AndroidAgentProcessSession.stop()
        AgentSessionNotification.hide(activity)
    }

    fun onResume() {
        syncLock()
    }

    fun close(revokeSession: Boolean) {
        if (closed) return
        closed = true
        activity.unregisterReceiver(screenOffReceiver)
        if (revokeSession) stopFromNativeUi()
    }

    private fun syncLock() {
        AndroidAgentProcessSession.setDeviceLocked(deviceLocked())
        // If notifications are disabled mid-session, there is no trustworthy
        // always-visible remote-control indicator. Revoke instead of keeping a
        // hidden grant alive until another browser operation attempts delivery.
        if (AndroidAgentProcessSession.active.value != null && !AgentSessionNotification.canNotify(activity)) {
            stopFromNativeUi()
        }
    }

    private fun deviceLocked(): Boolean {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        val power = activity.getSystemService(PowerManager::class.java)
        return keyguard?.isDeviceLocked != false || power?.isInteractive != true
    }
}

/** A genuine native dialog tap may focus the dialog, not the Activity main window. */
internal fun nativeConsentTapAllowed(
    lifecycleResumed: Boolean,
    activityVisible: Boolean,
    deviceUnlocked: Boolean,
    stopNotificationEnabled: Boolean,
): Boolean = lifecycleResumed && activityVisible && deviceUnlocked && stopNotificationEnabled
