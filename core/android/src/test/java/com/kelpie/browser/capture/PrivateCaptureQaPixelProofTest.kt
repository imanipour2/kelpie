package com.kelpie.browser.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateCaptureQaPixelProofTest {
    @Test
    fun everyRequestGetsItsOwnExactPixel() {
        val values = (1..20).map(PrivateCaptureQaPixelProof::rgb)
        assertEquals(20, values.toSet().size)
        for (rgb in values) {
            assertTrue(PrivateCaptureQaPixelProof.matches(rgb, rgb or (0xFF shl 24)))
            assertFalse(PrivateCaptureQaPixelProof.matches(rgb, rgb xor 0x000100))
        }
        assertFalse(PrivateCaptureQaPixelProof.matches(values[0], values[1]))
    }

    @Test
    fun onlyValidFrameNumbersAreAllowed() {
        assertTrue(runCatching { PrivateCaptureQaPixelProof.rgb(0) }.isFailure)
        assertTrue(runCatching { PrivateCaptureQaPixelProof.rgb(21) }.isFailure)
    }

    @Test
    fun repaintIsStrictlyPublicAndExplicitlyRemovable() {
        val script = PrivateCaptureQaPixelProof.repaintScript(0x010A2B)
        assertTrue(script.contains("#010a2b"))
        assertTrue(script.contains("location.hostname !== 'example.com'"))
        assertTrue(script.contains("location.port"))
        assertTrue(script.contains("aria-hidden"))
        assertTrue(script.contains("left:50%"))
        assertTrue(script.contains("top:50%"))
        assertTrue(PrivateCaptureQaPixelProof.REMOVE_SCRIPT.contains("remove()"))
    }
}
