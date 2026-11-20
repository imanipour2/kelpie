package com.kelpie.browser.agent.execution

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes native lifecycle intent with physical WebView transfers. */
internal class R5LifecycleReconciler(
    private val transferMutex: Mutex,
    private val activityResumed: () -> Boolean,
) {
    data class Request(
        val epoch: Long,
        val foreground: Boolean,
    )

    private var latest = Request(0L, true)

    /** Main-thread native lifecycle only; remote requests cannot change this target. */
    fun signal(foreground: Boolean): Request = Request(latest.epoch + 1L, foreground).also { latest = it }

    fun current(): Request = latest

    fun isCurrent(request: Request): Boolean = request == latest && activityResumed() == request.foreground

    suspend fun reconcile(
        request: Request,
        transition: suspend (stillCurrent: () -> Boolean) -> Unit,
    ) = transferMutex.withLock {
        // Read the renderer state inside transition, after the earlier handoff
        // completed. Checking it before this lock loses a resume during release.
        if (isCurrent(request)) transition { isCurrent(request) }
    }
}
