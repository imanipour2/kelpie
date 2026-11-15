package com.kelpie.browser.network

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
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AuthenticatedClientContext> {
        fun fromVerifiedDecision(decision: AuthDecision.Authenticated): AuthenticatedClientContext {
            return AuthenticatedClientContext(decision.clientId)
        }
    }
}

/** Null outside a native HTTP request that passed actual bearer verification. */
suspend fun verifiedClientId(): String? = coroutineContext[AuthenticatedClientContext]?.clientId
