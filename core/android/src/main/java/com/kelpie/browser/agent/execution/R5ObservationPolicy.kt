package com.kelpie.browser.agent.execution

import com.kelpie.browser.agent.session.SessionConsentCore

/**
 * G4 read-only pilot gate. The paired client's bearer identity is verified by
 * HTTP middleware; neither JSON principal, IP, page script nor an MCP name
 * can create a grant. No permission is persisted across process death.
 */
internal object R5ObservationPolicy {
    fun permits(
        state: SessionConsentCore.State,
        verifiedPrincipal: String?,
        requestedGeneration: String?,
        unlocked: Boolean,
        activityResumed: Boolean,
        notificationVisible: Boolean,
    ): Boolean {
        val active = state as? SessionConsentCore.State.Active ?: return false
        return !active.locked &&
            !verifiedPrincipal.isNullOrBlank() &&
            active.principalId == verifiedPrincipal &&
            active.generation == requestedGeneration &&
            unlocked &&
            activityResumed &&
            notificationVisible
    }

    fun sameTarget(
        requestedTab: String?,
        requestedDocument: String?,
        liveTab: String?,
        liveDocument: String?,
    ): Boolean =
        !requestedTab.isNullOrBlank() &&
            !requestedDocument.isNullOrBlank() &&
            requestedTab == liveTab &&
            requestedDocument == liveDocument

    const val MAX_VISIBLE_TEXT = 24_000
    const val MAX_PNG_BYTES = 2_500_000
}
