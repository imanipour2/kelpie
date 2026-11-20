package com.kelpie.browser.network

import com.kelpie.browser.agent.execution.R5AgentDeliveryGuard
import com.kelpie.browser.agent.session.R5AgentPrincipalPolicy
import com.kelpie.browser.agent.session.SessionConsentCore

internal class R5AuthorizationChangedException : Exception()

internal class R5RendererUnavailableException : Exception()

/** Request-local fence over the existing consent authority; never creates a grant. */
internal object R5RequestAuthorization {
    /** A key-shaped legacy/account ID cannot inherit a native computer's execution epoch. */
    fun owns(
        principal: String?,
        computerKey: String?,
        active: SessionConsentCore.State.Active?,
    ): Boolean =
        R5AgentPrincipalPolicy.eligible(principal) &&
            active != null &&
            active.principalId == principal &&
            (active.trustedComputerKey == null || (computerKey == principal && computerKey == active.trustedComputerKey))

    fun permits(
        principal: String?,
        admitted: SessionConsentCore.State.Active?,
        current: SessionConsentCore.State,
        computerKey: String? = null,
    ): Boolean =
        owns(principal, computerKey, admitted) &&
            admitted != null &&
            R5AgentDeliveryGuard.permits(principal, admitted.generation, current) &&
            current == admitted

    suspend fun current(snapshot: () -> SessionConsentCore.State): Boolean =
        verifiedRequestAuthorityCurrent() &&
            permits(verifiedClientId(), verifiedOwnerGrant(), snapshot(), verifiedComputerKey())
}
