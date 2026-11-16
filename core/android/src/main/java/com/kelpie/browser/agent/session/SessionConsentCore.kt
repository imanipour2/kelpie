package com.kelpie.browser.agent.session

import java.security.SecureRandom

/** In-memory owner of locally granted authority; not an Android permission or a durable token. */
class SessionConsentCore(
    private val elapsedRealtimeMs: () -> Long,
    private val random: SecureRandom = SecureRandom(),
) {
    sealed interface State {
        data object Off : State

        data class Pending(
            val promptId: String,
            val principalId: String,
            val deadlineMs: Long,
        ) : State

        data class Active(
            val principalId: String,
            val generation: String,
            val locked: Boolean = false,
            /** Monotonic safety epoch: lock/unlock races invalidate in-flight work. */
            val safetyEpoch: Long = 0L,
        ) : State
    }

    private var current: State = State.Off

    @Synchronized
    fun snapshot(): State = current

    /** A verified gateway can request a prompt but cannot grant consent. */
    @Synchronized
    fun requestNativeConsent(verifiedPrincipalId: String?): State.Pending? {
        if (!valid(verifiedPrincipalId) || current is State.Active) return null
        val pending = current as? State.Pending
        if (pending != null && elapsedRealtimeMs() < pending.deadlineMs) return null
        val now = elapsedRealtimeMs()
        if (now < 0 || now > Long.MAX_VALUE - LOCAL_PROMPT_TTL_MS) return null
        return State.Pending(nonce(), verifiedPrincipalId!!, now + LOCAL_PROMPT_TTL_MS).also {
            current = it
        }
    }

    /** Called only from the native affirmative on-device UI callback. */
    @Synchronized
    fun approveFromNativeUi(
        promptId: String,
        deviceUnlocked: Boolean,
    ): State.Active? {
        val pending = current as? State.Pending ?: return null
        if (pending.promptId != promptId) return null // Stale callback must not revoke a newer prompt.
        if (!deviceUnlocked || elapsedRealtimeMs() >= pending.deadlineMs) {
            current = State.Off
            return null
        }
        return State.Active(pending.principalId, nonce()).also { current = it }
    }

    /** A lock suspends agent operations without silently erasing live-session consent. */
    @Synchronized
    fun setDeviceLocked(locked: Boolean) {
        val active = current as? State.Active ?: return
        if (active.locked != locked) {
            // Permanently invalidates work admitted before a lock transition,
            // even when the screen unlocks before an asynchronous callback.
            current = active.copy(locked = locked, safetyEpoch = active.safetyEpoch + 1L)
        }
    }

    /** Local STOP, unpair, process-loss adapter and explicit end all converge here. */
    @Synchronized
    fun stop() {
        current = State.Off
    }

    private fun nonce(): String = ByteArray(NONCE_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    private fun valid(value: String?): Boolean = value != null && value.isNotBlank() && value.length <= MAX_PRINCIPAL_LENGTH && value.none(Char::isISOControl)

    companion object {
        const val LOCAL_PROMPT_TTL_MS = 300_000L
        const val MAX_PRINCIPAL_LENGTH = 128
        private const val NONCE_BYTES = 24
    }
}
