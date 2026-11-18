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

/** HTTP status is only local evidence and never proves remote agent identity. */
internal fun tunnelReadyLabel(
    healthStatus: Int?,
    readyStatus: Int?,
): String =
    when {
        healthStatus == null -> "Diagnostics unavailable"
        healthStatus != 200 -> "Diagnostics unhealthy (HTTP $healthStatus)"
        readyStatus == null -> "Running; readiness unknown"
        readyStatus != 200 -> "Running; not ready (HTTP $readyStatus)"
        else -> "Ready locally"
    }

/** Only a fresh verified non-localhost request is meaningful; none proves control. */
internal fun mcpActivityLabel(ageMs: Long?): String =
    when {
        ageMs == null || ageMs < 0L -> "No authenticated request observed in this process"
        ageMs > 120_000L -> "Last observed more than 2 minutes ago (stale)"
        else -> "Authenticated request observed recently (not an agent-control proof)"
    }
