package com.kelpie.browser.qa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceProbeLeaseTest {
    @Test
    fun requiresUniqueTabAndDocumentAndRejectsCrossTabReuse() {
        var now = 1000L
        val lease = SurfaceProbeLease(now = { now }, ttlMs = 2000L)
        assertFalse(lease.begin("", "v1"))
        assertTrue(lease.begin("tab1", "v1"))
        assertFalse(lease.begin("tab1", "v1"))
        assertFalse(lease.allows("tab2", "v1"))
        assertFalse(lease.allows("tab1", "v2"))
        assertTrue(lease.allows("tab1", "v1"))
        now = 3000L
        assertFalse(lease.allows("tab1", "v1"))
        lease.cancel()
        assertFalse(lease.allows("tab1", "v1"))
    }

    @Test
    fun exactlyTwentyFramesAndOneCompletion() {
        val lease = SurfaceProbeLease()
        assertTrue(lease.begin("tab", "doc"))
        assertFalse(lease.claim(2, "tab", "doc"))
        for (sequence in 1..20) {
            assertTrue(lease.claim(sequence, "tab", "doc"))
            assertFalse(lease.claim(sequence, "tab", "doc"))
        }
        assertTrue(lease.claim(21, "tab", "doc"))
        assertFalse(lease.claim(21, "tab", "doc"))
        assertTrue(lease.completed())
        lease.cancel()
        assertFalse(lease.completed())
    }

    @Test
    fun expirationFailsClosedAndCancelAllowsNewSession() {
        var now = 0L
        val lease = SurfaceProbeLease(now = { now }, ttlMs = 200L)
        assertTrue(lease.begin("tab", "doc"))
        now = 201L
        assertFalse(lease.claim(1, "tab", "doc"))
        lease.cancel()
        assertTrue(lease.begin("new", "v2"))
        assertFalse(lease.allows("tab", "doc"))
    }
}
