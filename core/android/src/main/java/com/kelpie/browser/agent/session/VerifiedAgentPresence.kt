package com.kelpie.browser.agent.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Short-lived evidence that a client was authenticated by the existing HTTP/MCP gateway.
 *
 * A stored pairing name is never sufficient: only [observeAuthenticated] called after
 * bearer verification exposes a principal to the native startup consent UI.
 * This does not grant any authority; every later operation is re-authenticated by
 * the gateway and checked against the process consent generation.
 */
internal class VerifiedAgentPresence(
    private val elapsedRealtimeMs: () -> Long,
    private val isStillPaired: (String) -> Boolean,
    private val ttlMs: Long = 120_000L,
) {
    private val observedAt = linkedMapOf<String, Long>()
    private val _principals = MutableStateFlow<List<String>>(emptyList())
    val principals: StateFlow<List<String>> = _principals.asStateFlow()

    @Synchronized
    fun observeAuthenticated(principalId: String) {
        val now = elapsedRealtimeMs()
        prune(now)
        if (principalId.isBlank() ||
            principalId.length > SessionConsentCore.MAX_PRINCIPAL_LENGTH ||
            principalId.any(Char::isISOControl) ||
            !isStillPaired(principalId)
        ) {
            publish()
            return
        }
        observedAt.remove(principalId)
        observedAt[principalId] = now
        while (observedAt.size > MAX_CANDIDATES) {
            observedAt.remove(observedAt.keys.first())
        }
        publish()
    }

    @Synchronized
    fun isFreshAndPaired(principalId: String): Boolean {
        prune(elapsedRealtimeMs())
        publish()
        return observedAt.containsKey(principalId) && isStillPaired(principalId)
    }

    @Synchronized
    fun refresh() {
        prune(elapsedRealtimeMs())
        publish()
    }

    private fun prune(now: Long) {
        observedAt.entries.removeAll { (principal, seen) ->
            now < seen || now - seen >= ttlMs || !isStillPaired(principal)
        }
    }

    private fun publish() {
        _principals.value = observedAt.keys.toList()
    }

    private companion object {
        const val MAX_CANDIDATES = 8
    }
}
