package com.kelpie.browser.network

import android.os.SystemClock

/**
 * In-memory, non-identifying diagnostic evidence only. An authenticated MCP
 * envelope is NOT proof of a particular ChatGPT account or working browser tool.
 * No principal, request body, token, tab, origin or URL is retained.
 */
internal object McpActivityEvidence {
    @Volatile private var lastObservedElapsedMs: Long? = null

    fun noteAuthenticatedMcpRequest() {
        lastObservedElapsedMs = SystemClock.elapsedRealtime()
    }

    fun elapsedSinceLastRequestMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long? =
        lastObservedElapsedMs?.let { last ->
            (nowElapsedMs - last).takeIf { it >= 0L }
        }
}

/** A successful readiness check does not establish remote identity or tool access. */
internal fun tunnelReadyLabel(
    healthOk: Boolean,
    readyOk: Boolean,
): String =
    when {
        !healthOk -> "Not reachable"
        readyOk -> "Ready locally"
        else -> "Running; not ready"
    }
