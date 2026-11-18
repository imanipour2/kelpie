package com.kelpie.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionToneTest {
    @Test
    fun readyOnlyAfterBothLocalHealthChecksPass() {
        assertEquals(ConnectionTone.GOOD, tunnelTone(200, 200))
    }

    @Test
    fun explicitFailureIsRed() {
        assertEquals(ConnectionTone.BAD, tunnelTone(200, 503))
        assertEquals(ConnectionTone.BAD, tunnelTone(500, 200))
    }

    @Test
    fun missingEvidenceIsNeutralNotFalseFailure() {
        assertEquals(ConnectionTone.UNKNOWN, tunnelTone(null, null))
        assertEquals(ConnectionTone.UNKNOWN, tunnelTone(200, null))
    }
}
