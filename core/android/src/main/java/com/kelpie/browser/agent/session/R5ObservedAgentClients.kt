package com.kelpie.browser.agent.session

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A generic localhost principal can identify any app sharing Android loopback. */
internal object R5AgentPrincipalPolicy {
    fun eligible(principal: String?): Boolean =
        principal != null &&
            principal.isNotBlank() &&
            principal != "localhost" &&
            principal.length <= SessionConsentCore.MAX_PRINCIPAL_LENGTH &&
            principal.none(Char::isISOControl)
}

/**
 * Process-only, short-lived observation of uniquely bearer-verified agent clients.
 *
 * Observation alone NEVER creates a pending grant. A native user gesture must
 * explicitly request it, and a second affirmative native dialog tap approves it.
 * Multiple recent principals are deliberately ambiguous (no automatic choice).
 */
internal class RecentVerifiedAgentClients(
    private val elapsedRealtimeMs: () -> Long,
    private val ttlMs: Long = 120_000L,
) {
    private val sightings = linkedMapOf<String, Long>()
    private val state = MutableStateFlow<List<String>>(emptyList())
    val observed: StateFlow<List<String>> = state.asStateFlow()

    @Synchronized
    fun noteVerified(principal: String) {
        if (!R5AgentPrincipalPolicy.eligible(principal)) return
        val now = elapsedRealtimeMs()
        if (now < 0L) return
        expire(now)
        if (sightings.size < MAX_OBSERVED || principal in sightings) {
            sightings[principal] = now
        }
        publish()
    }

    /**
     * Reconcile time-based expiration with UI state while the connection surface
     * is visible. Expiration is not a pairing revocation or native STOP.
     */
    @Synchronized
    fun refreshExpired() {
        expire(elapsedRealtimeMs())
        publish()
    }

    @Synchronized
    fun soleRecentPrincipal(): String? {
        expire(elapsedRealtimeMs())
        publish()
        return sightings.keys.singleOrNull()
    }

    @Synchronized
    fun clear() {
        sightings.clear()
        publish()
    }

    private fun expire(now: Long) {
        sightings.entries.removeAll { (_, time) -> now < time || now - time > ttlMs }
    }

    private fun publish() {
        state.value = sightings.keys.toList()
    }

    private companion object {
        const val MAX_OBSERVED = 8
    }
}

/** Shared by the authenticated native MCP endpoint and the in-app connection sheet. */
internal object R5ObservedAgentClients {
    val registry = RecentVerifiedAgentClients(SystemClock::elapsedRealtime)
}
