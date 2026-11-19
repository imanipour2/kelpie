package com.kelpie.browser.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Native/DOM geometry gate for same-tab private display transfers. */
class R5ViewportAdmissionTest {
    @Test
    fun validAttachedAndUnlockedViewportPasses() {
        assertTrue(R5ViewportAdmission.permits(true, 384, 682, true, false))
    }

    @Test
    fun zeroDimensionDomReadableTabCannotBeSelected() {
        assertFalse(R5ViewportAdmission.permits(true, 0, 0, false, false))
        assertFalse(R5ViewportAdmission.permits(true, 384, 682, false, false))
        assertFalse(R5ViewportAdmission.permits(true, 0, 682, true, false))
        assertFalse(R5ViewportAdmission.permits(true, 384, 0, true, false))
    }

    @Test
    fun detachedAndLockedViewportsAlwaysFailClosed() {
        assertFalse(R5ViewportAdmission.permits(false, 384, 682, true, false))
        assertFalse(R5ViewportAdmission.permits(true, 384, 682, true, true))
        assertFalse(R5ViewportAdmission.permits(false, 0, 0, false, true))
    }
}
