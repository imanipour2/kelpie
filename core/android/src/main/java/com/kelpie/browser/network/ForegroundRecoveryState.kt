package com.kelpie.browser.network

import java.util.UUID

/**
 * Pure, clock-injected recovery consent state for one foreground screenshot request.
 * This class is deliberately independent of Android notifications and Activity lifecycles.
 * UI integration must verify the exact request ID before moving to APPROVED.
 */
internal class ForegroundRecoveryState(
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val ttlMs: Long = 120_000L,
) {
    enum class Status {
        IDLE,
        FOREGROUND_REQUIRED,
        NOTIFICATION_POSTED,
        USER_APPROVED,
        USER_DECLINED,
        ACTIVITY_RESUMED,
        RENDER_READY,
        CAPTURE_COMPLETED,
        FAILED,
        REQUEST_EXPIRED,
        CANCELLED,
    }

    data class Request(
        val id: String,
        val createdAt: Long,
        val expiresAt: Long,
        val status: Status,
    )

    private var current: Request? = null

    @Synchronized
    fun snapshot(): Request? {
        expire()
        return current
    }

    /** Coalesces duplicate background requests while a valid consent prompt is pending. */
    @Synchronized
    fun begin(): Request {
        expire()
        current?.takeIf {
            it.status in setOf(
                Status.FOREGROUND_REQUIRED,
                Status.NOTIFICATION_POSTED,
                Status.USER_APPROVED,
                Status.ACTIVITY_RESUMED,
                Status.RENDER_READY,
            )
        }
            ?.let { return it }
        val t = now()
        return Request(newId(), t, t + ttlMs, Status.FOREGROUND_REQUIRED).also { current = it }
    }

    @Synchronized
    fun transition(
        id: String,
        next: Status,
    ): Request? {
        expire()
        val old = current ?: return null
        if (old.id != id || now() >= old.expiresAt) return null
        val allowed =
            when (old.status) {
                Status.FOREGROUND_REQUIRED -> setOf(Status.NOTIFICATION_POSTED, Status.FAILED, Status.CANCELLED)
                Status.NOTIFICATION_POSTED -> setOf(Status.USER_APPROVED, Status.USER_DECLINED, Status.FAILED, Status.CANCELLED)
                Status.USER_APPROVED -> setOf(Status.ACTIVITY_RESUMED, Status.FAILED, Status.CANCELLED)
                Status.ACTIVITY_RESUMED -> setOf(Status.RENDER_READY, Status.FAILED, Status.CANCELLED)
                Status.RENDER_READY -> setOf(Status.CAPTURE_COMPLETED, Status.FAILED, Status.CANCELLED)
                else -> emptySet()
            }
        if (next !in allowed) return null
        return old.copy(status = next).also { current = it }
    }

    @Synchronized
    fun expire(): Request? {
        val old = current ?: return null
        if (now() >= old.expiresAt &&
            old.status !in setOf(Status.CAPTURE_COMPLETED, Status.FAILED, Status.USER_DECLINED, Status.CANCELLED, Status.REQUEST_EXPIRED)
        ) {
            current = old.copy(status = Status.REQUEST_EXPIRED)
        }
        return current
    }
}
