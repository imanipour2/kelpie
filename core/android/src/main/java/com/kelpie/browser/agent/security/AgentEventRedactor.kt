package com.kelpie.browser.agent.security

import java.net.URI

/**
 * Fail-closed metadata sanitizer for trace events and command receipts.
 * This does NOT replace the existing JsBridge traffic-store audit: callers must
 * avoid collecting/storing request bodies and sensitive response bodies at source.
 */
object AgentEventRedactor {
    // Only headers with non-credential semantics may appear in agent diagnostics.
    // Unknown headers are dropped rather than attempting to blacklist every token alias.
    private val safeHeaderNames = setOf("content-type", "content-length", "cache-control")

    fun safeHeaders(headers: Map<String, String>): Map<String, String> =
        headers
            .filterKeys { it.trim().lowercase() in safeHeaderNames }
            .mapValues { (_, value) -> value.take(MAX_HEADER_VALUE) }

    /** URL logs must exclude query strings (OTP/state/tokens), fragment, and userinfo. */
    fun safeOrigin(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank() || rawUrl.length > 8192) return null
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host ?: return null
        if (scheme != "https" && scheme != "http") return null
        if (uri.rawUserInfo != null) return null
        return if (uri.port == -1) "$scheme://$host" else "$scheme://$host:${uri.port}"
    }

    fun traceEvent(
        eventType: String,
        commandId: String,
        origin: String?,
    ): Map<String, String> =
        buildMap {
            put("event", eventType.take(MAX_METADATA_VALUE))
            put("commandId", commandId.take(MAX_METADATA_VALUE))
            safeOrigin(origin)?.let { put("origin", it) }
        }

    private const val MAX_METADATA_VALUE = 128
    private const val MAX_HEADER_VALUE = 256
}
