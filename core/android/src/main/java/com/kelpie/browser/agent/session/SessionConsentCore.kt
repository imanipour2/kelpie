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

    /**
     * Mother profile: the durable computer approval is already the owner's grant.
     * The caller must verify its bearer against ComputerTrust before using this
     * adapter. It reconstructs execution state, never creates persistent trust.
     */
    @Synchronized
    fun restoreTrustedComputer(
        computerKey: String,
        paused: Boolean,
        deviceUnlocked: Boolean,
    ): State.Active? {
        if (paused || computerKey.length != 64 || computerKey.any { it !in "0123456789abcdef" }) return null
        when (val state = current) {
            is State.Pending -> return null
            is State.Active -> {
                if (state.principalId != computerKey) return null
                setDeviceLocked(!deviceUnlocked)
                return current as State.Active
            }
            State.Off -> {
                if (!deviceUnlocked) return null
                return State.Active(computerKey, nonce()).also { current = it }
            }
        }
    }

    /** Retire in-flight work after a renderer failure without erasing approval. */
    @Synchronized
    fun invalidateRuntime(expected: State.Active? = snapshot() as? State.Active): Boolean {
        val active = current as? State.Active ?: return false
        if (active != expected) return false
        current = active.copy(generation = nonce(), safetyEpoch = active.safetyEpoch + 1L)
        return true
    }

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

    /** A delayed UI timeout must never cancel a different, newer native prompt. */
    @Synchronized
    fun expirePendingIfDue(promptId: String): Boolean {
        val pending = current as? State.Pending ?: return false
        if (pending.promptId != promptId || elapsedRealtimeMs() < pending.deadlineMs) return false
        current = State.Off
        return true
    }

    /** Local STOP, unpair, process-loss adapter and explicit end all converge here. */
    @Synchronized
    fun stop() {
        current = State.Off
    }

    /** Unpairing revokes only the pending/active grant bound to that identity. */
    @Synchronized
    fun revokePrincipal(
        principalId: String,
        expected: State.Active? = null,
    ): Boolean {
        if (expected != null && current != expected) return false
        val owner =
            when (val state = current) {
                is State.Active -> state.principalId
                is State.Pending -> state.principalId
                State.Off -> null
            }
        if (owner != principalId) return false
        current = State.Off
        return true
    }

    private fun nonce(): String = ByteArray(NONCE_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    private fun valid(value: String?): Boolean = value != null && value.isNotBlank() && value.length <= MAX_PRINCIPAL_LENGTH && value.none(Char::isISOControl)

    companion object {
        const val LOCAL_PROMPT_TTL_MS = 300_000L
        const val MAX_PRINCIPAL_LENGTH = 128
        private const val NONCE_BYTES = 24
    }
}
