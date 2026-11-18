package com.kelpie.browser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.kelpie.browser.agent.session.AgentSessionCoordinator
import kotlinx.coroutines.delay

/**
 * Present-time display reconciliation only: never pairs, requests consent,
 * revokes an active session, or changes Android browser ownership.
 *
 * A previous sighting's StateFlow list is not time-driven, and re-observing the
 * same ID may refresh its timestamp without changing that list. Poll while a
 * connection UI is visible to prevent stale "Verified AI agent ready" labels.
 */
@Composable
internal fun R5ObservedAgentExpiryEffect(
    coordinator: AgentSessionCoordinator,
    hasObservedAgents: Boolean,
) {
    LaunchedEffect(coordinator, hasObservedAgents) {
        if (!hasObservedAgents) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            coordinator.refreshObservedAgents()
        }
    }
}
