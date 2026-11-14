package com.kelpie.browser.network

/**
 * A single consent-bound screenshot held in process memory only.
 *
 * Screenshots are never persisted to files, logs, backups or Android notifications.
 * The remote client must supply the original request ID; an unrelated screenshot
 * request must not silently consume an approved capture.
 */
internal class ApprovedScreenshotStore(
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Ticket(
        val requestId: String,
        val tabId: String,
        val documentVersion: String,
        val expiresAtMs: Long,
    )

    private data class Staged(
        val ticket: Ticket,
        val capturedAtMs: Long,
        val payload: Map<String, Any?>,
    )

    private var ticket: Ticket? = null
    private var staged: Staged? = null

    @Synchronized
    fun arm(candidate: Ticket): Boolean {
        discardExpired()
        if (candidate.expiresAtMs <= now() || candidate.requestId.isBlank()) return false
        val existing = ticket
        if (existing?.requestId == candidate.requestId) return existing == candidate
        ticket = candidate
        staged = null
        return true
    }

    @Synchronized
    fun pending(requestId: String): Ticket? {
        discardExpired()
        return ticket?.takeIf { it.requestId == requestId && staged == null }
    }

    @Synchronized
    fun stage(
        requestId: String,
        tabId: String,
        documentVersion: String,
        payload: Map<String, Any?>,
    ): Boolean {
        discardExpired()
        val current = ticket ?: return false
        if (current.requestId != requestId ||
            current.tabId != tabId ||
            current.documentVersion != documentVersion ||
            staged != null
        ) {
            return false
        }
        val image = payload["image"] as? String ?: return false
        if (image.isEmpty() || image.length > MAX_IMAGE_BASE64_CHARS) return false
        staged = Staged(current, now(), payload)
        return true
    }

    @Synchronized
    fun take(
        requestId: String,
        tabId: String,
        documentVersion: String,
    ): Map<String, Any?>? {
        discardExpired()
        val item = staged ?: return null
        if (item.ticket.requestId != requestId ||
            item.ticket.tabId != tabId ||
            item.ticket.documentVersion != documentVersion
        ) {
            return null
        }
        staged = null
        ticket = null
        return item.payload +
            mapOf(
                "recoveryRequestId" to requestId,
                "recoveryRequestStatus" to "CAPTURE_COMPLETED",
                "sourceTabId" to tabId,
                "capturedAtMs" to item.capturedAtMs,
                "captureMode" to "user-approved-foreground",
            )
    }

    @Synchronized
    fun invalidate(requestId: String? = null) {
        if (requestId == null || ticket?.requestId == requestId) {
            ticket = null
            staged = null
        }
    }

    @Synchronized
    fun discardExpired() {
        val active = ticket ?: return
        val capture = staged
        if (now() >= active.expiresAtMs ||
            (capture != null && now() - capture.capturedAtMs >= MAX_CACHE_AGE_MS)
        ) {
            ticket = null
            staged = null
        }
    }

    companion object {
        const val MAX_CACHE_AGE_MS = 60_000L
        const val MAX_IMAGE_BASE64_CHARS = 12 * 1024 * 1024
    }
}
