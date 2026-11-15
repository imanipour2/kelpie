package com.kelpie.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P3GuardSnapshotTest {
    private fun ready() = P3GuardSnapshot(true, true, true, true, true, true, true, true, true)

    @Test fun acceptsOnlyFullyPinnedSyntheticState() {
        assertTrue(ready().valid)
        assertEquals("UNKNOWN", ready().failedFields())
    }

    @Test fun refusesExpiredLeaseAndStaleDocument() {
        val stale = ready().copy(lease = false, document = false)
        assertFalse(stale.valid)
        assertEquals("LEASE_DOCUMENT", stale.failedFields())
    }

    @Test fun refusesWrongTabAndWrongRenderOrigin() {
        val stale = ready().copy(tab = false, origin = false, url = false)
        assertFalse(stale.valid)
        assertEquals("TAB_FIXTURE_ORIGIN_VIEW_URL", stale.failedFields())
    }
}
