package com.kelpie.browser.network

import com.kelpie.browser.agent.session.SessionConsentCore
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Principal that passed the native pairing/account bearer middleware.
 *
 * Never infer this from request JSON or headers inside a screenshot handler.
 * The Ktor route attaches the principal to the coroutine that dispatches the
 * canonical gateway. Context propagates across suspensions and Main dispatch.
 */
class AuthenticatedClientContext private constructor(
    val clientId: String,
    internal val ownerGrant: SessionConsentCore.State.Active?,
    internal val authorityCurrent: suspend () -> Boolean,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AuthenticatedClientContext> {
        fun fromVerifiedDecision(
            decision: AuthDecision.Authenticated,
            ownerGrant: SessionConsentCore.State.Active? = null,
            authorityCurrent: suspend () -> Boolean = { true },
        ) = AuthenticatedClientContext(decision.clientId, ownerGrant, authorityCurrent)
    }
}

/** Null outside a native HTTP request that passed actual bearer verification. */
suspend fun verifiedClientId(): String? = coroutineContext[AuthenticatedClientContext]?.clientId

/** The original native grant travels with the verified principal across every await. */
internal suspend fun verifiedOwnerGrant(): SessionConsentCore.State.Active? = coroutineContext[AuthenticatedClientContext]?.ownerGrant

/** Rechecks pairing revocation; no bearer is exposed to handlers or page scripts. */
internal suspend fun verifiedRequestAuthorityCurrent(): Boolean = coroutineContext[AuthenticatedClientContext]?.authorityCurrent?.invoke() == true
