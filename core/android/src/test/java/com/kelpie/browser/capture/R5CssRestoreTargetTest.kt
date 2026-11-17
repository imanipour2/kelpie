package com.kelpie.browser.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class R5CssRestoreTargetTest {
    @Test
    fun latestOwnedDocumentScrollOverridesEntrySnapshot() {
        assertEquals(430.0 to 250.0, R5CssRestoreTarget.choose(0.0, 0.0, 430.0 to 250.0))
    }

    @Test
    fun rtlSignedHorizontalCssScrollIsValid() {
        assertEquals(-68.25 to 640.0, R5CssRestoreTarget.choose(0.0, 0.0, -68.25 to 640.0))
    }

    @Test
    fun nullOrUnsafeLatestReadLeavesPreviousTargetForFailClosedVerification() {
        assertEquals(4.0 to 12.0, R5CssRestoreTarget.choose(4.0, 12.0, null))
        assertEquals(4.0 to 12.0, R5CssRestoreTarget.choose(4.0, 12.0, 1.0 to -4.0))
        assertEquals(4.0 to 12.0, R5CssRestoreTarget.choose(4.0, 12.0, 1.0 to Double.NaN))
    }
}
