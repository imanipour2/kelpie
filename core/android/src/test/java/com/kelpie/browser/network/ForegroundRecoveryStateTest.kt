package com.kelpie.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundRecoveryStateTest {
    @Test
    fun duplicateRequestsReuseOnlyPendingId() {
        var time = 100L
        var nextId = 0
        val state = ForegroundRecoveryState(now = { time }, newId = { (++nextId).toString() }, ttlMs = 1000L)
        val first = state.begin()
        assertEquals(first.id, state.begin().id)
        assertEquals(ForegroundRecoveryState.Status.NOTIFICATION_POSTED, state.transition(first.id, ForegroundRecoveryState.Status.NOTIFICATION_POSTED)?.status)
        assertEquals(first.id, state.begin().id)
        assertEquals(ForegroundRecoveryState.Status.USER_DECLINED, state.transition(first.id, ForegroundRecoveryState.Status.USER_DECLINED)?.status)
        assertEquals("2", state.begin().id)
    }

    @Test
    fun staleApprovalAndTimeoutCannotAuthorizeNewCapture() {
        var time = 100L
        var nextId = 0
        val state = ForegroundRecoveryState(now = { time }, newId = { (++nextId).toString() }, ttlMs = 1000L)
        val old = state.begin()
        state.transition(old.id, ForegroundRecoveryState.Status.NOTIFICATION_POSTED)
        time = 1100L
        assertEquals(ForegroundRecoveryState.Status.REQUEST_EXPIRED, state.snapshot()?.status)
        val fresh = state.begin()
        assertNull(state.transition(old.id, ForegroundRecoveryState.Status.USER_APPROVED))
        assertNull(state.transition(fresh.id, ForegroundRecoveryState.Status.USER_APPROVED))
    }

    @Test
    fun approvalNeedsResumeAndRenderBeforeCaptureCompleted() {
        val state = ForegroundRecoveryState(now = { 500L }, newId = { "qa-request" })
        val id = state.begin().id
        assertNull(state.transition(id, ForegroundRecoveryState.Status.CAPTURE_COMPLETED))
        val path =
            listOf(
                ForegroundRecoveryState.Status.NOTIFICATION_POSTED,
                ForegroundRecoveryState.Status.USER_APPROVED,
                ForegroundRecoveryState.Status.ACTIVITY_RESUMED,
                ForegroundRecoveryState.Status.RENDER_READY,
                ForegroundRecoveryState.Status.CAPTURE_COMPLETED,
            )
        path.forEach { assertEquals(it, state.transition(id, it)?.status) }
        assertNull(state.transition(id, ForegroundRecoveryState.Status.USER_APPROVED))
    }
}
