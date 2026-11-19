package com.kelpie.browser.agent.execution

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Transaction ordering only; the caller retains the existing transfer mutex,
 * consent reducer and WebView ownership. No independent session state is kept.
 */
internal object R5PrivateTabTransaction {
    suspend fun run(
        eligible: () -> Boolean,
        move: suspend () -> Boolean,
        commit: suspend () -> Boolean,
        verify: () -> Boolean,
        rollback: suspend () -> Boolean,
        revoke: () -> Unit,
    ): Boolean {
        if (!eligible()) return false
        try {
            if (move() && eligible() && commit() && verify()) return true
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                if (!runCatching { rollback() }.getOrDefault(false)) revoke()
            }
            throw cancelled
        } catch (_: RuntimeException) {
            // A failed native callback cannot commit logical selection.
        }
        withContext(NonCancellable) {
            if (!runCatching { rollback() }.getOrDefault(false)) revoke()
        }
        return false
    }
}
