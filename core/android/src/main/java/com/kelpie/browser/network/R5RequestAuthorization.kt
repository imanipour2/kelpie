package com.kelpie.browser.network

import com.kelpie.browser.agent.execution.R5AgentDeliveryGuard
import com.kelpie.browser.agent.session.R5AgentPrincipalPolicy
import com.kelpie.browser.agent.session.SessionConsentCore

internal class R5AuthorizationChangedException : Exception()

/** Request-local fence over the existing consent authority; never creates a grant. */
internal object R5RequestAuthorization {
    fun permits(
        principal: String?,
        admitted: SessionConsentCore.State.Active?,
        current: SessionConsentCore.State,
    ): Boolean =
        R5AgentPrincipalPolicy.eligible(principal) &&
            admitted != null &&
            R5AgentDeliveryGuard.permits(principal, admitted.generation, current) &&
            current == admitted

    suspend fun current(snapshot: () -> SessionConsentCore.State): Boolean =
        verifiedRequestAuthorityCurrent() &&
            permits(verifiedClientId(), verifiedOwnerGrant(), snapshot())
}
