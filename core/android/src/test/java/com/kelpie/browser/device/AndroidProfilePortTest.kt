package com.kelpie.browser.device

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidProfilePortTest {
    @Test
    fun ownerRetainsLegacyPort() {
        assertEquals(18440, AndroidProfilePort.forProfile(18440, 10445))
    }

    @Test
    fun samsungDualAppUsesDifferentPort() {
        assertEquals(18535, AndroidProfilePort.forProfile(18440, 9_510_445))
        assertEquals(18590, AndroidProfilePort.forProfile(18440, 15_010_445))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidBasePortIsRejected() {
        AndroidProfilePort.forProfile(65535, 10445)
    }
}
