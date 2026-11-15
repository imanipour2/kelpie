package com.kelpie.browser.connect

import java.security.SecureRandom

/**
 * Short-lived, single-tab authority for a private, on-device renderer.
 *
 * A browser UI action, never an MCP/HTTP request, must call [approveLocally].
 * This does not grant the browser permission to bypass screen locks, protected
 * content, Android background-activity rules, or user-visible STOP controls.
 *
 * No screenshots, URLs, cookie values, or credentials are persisted here.
 */
class ConsentedCaptureLease(
    private val elapsedMs: () -> Long,
    private val random: SecureRandom = SecureRandom(),
) {
    data class Binding(
        val tabId: String,
        val documentVersion: String,
        val clientId: String,
        val agentAttemptId: String,
    ) {
        val isWellFormed: Boolean
            get() = listOf(tabId, documentVersion, clientId, agentAttemptId).all { it.isNotBlank() }
    }

    private data class Grant(
        val id: String,
        val binding: Binding,
        val view: Any,
        val expiresAtMs: Long,
        val maxFrames: Int,
        var framesUsed: Int = 0,
    )

    private var grant: Grant? = null

    /** Called only by a trusted, explicit local user interaction. Never auto-renew. */
    @Synchronized
    fun approveLocally(
        binding: Binding,
        view: Any,
        durationMs: Long = MAX_LEASE_MS,
        maxFrames: Int = DEFAULT_FRAME_LIMIT,
    ): String? {
        revoke()
        if (!binding.isWellFormed || durationMs !in 1..MAX_LEASE_MS || maxFrames !in 1..MAX_FRAME_LIMIT) {
            return null
        }
        val now = elapsedMs()
        if (now < 0 || now > Long.MAX_VALUE - durationMs) return null
        val token = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        grant = Grant(token, binding, view, now + durationMs, maxFrames)
        return token
    }

    /**
     * Validates a fresh frame operation against the locally approved scope.
     * Any mismatch revokes the entire lease, including the unused frame budget.
     */
    @Synchronized
    fun claimFrame(
        token: String?,
        current: Binding,
        view: Any,
        authenticatedClient: Boolean,
        deviceUnlocked: Boolean,
        userStopped: Boolean,
    ): Boolean {
        val g = grant ?: return false
        val allowed =
            token != null &&
                constantTimeEquals(g.id, token) &&
                g.binding == current &&
                g.view === view &&
                authenticatedClient &&
                deviceUnlocked &&
                !userStopped &&
                elapsedMs() < g.expiresAtMs &&
                g.framesUsed < g.maxFrames
        if (!allowed) {
            revoke()
            return false
        }
        g.framesUsed++
        return true
    }

    @Synchronized
    fun revoke() {
        grant = null
    }

    /** A zero-width token comparison avoids normal string early-exit timing. */
    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean {
        val ab = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        return java.security.MessageDigest.isEqual(ab, bb)
    }

    companion object {
        const val MAX_LEASE_MS = 90_000L
        const val DEFAULT_FRAME_LIMIT = 20
        const val MAX_FRAME_LIMIT = 240
    }
}
