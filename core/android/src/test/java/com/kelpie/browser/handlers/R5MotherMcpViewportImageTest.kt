package com.kelpie.browser.handlers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R5MotherMcpViewportImageTest {
    @Test
    fun nativePhysicalFrameUsesCssViewportPixelsForMotherMcp() {
        assertEquals(384 to 683, R5MotherMcpViewportImage.targetPixels(1080, 1920, 2.8125))
        assertEquals(1080 to 1920, R5MotherMcpViewportImage.targetPixels(1080, 1920, 1.0))
    }

    @Test
    fun refusesInvalidFrameAndScaleBeforeDecode() {
        assertNull(R5MotherMcpViewportImage.targetPixels(0, 1920, 2.0))
        assertNull(R5MotherMcpViewportImage.targetPixels(1080, 1920, 0.5))
        assertNull(R5MotherMcpViewportImage.targetPixels(1080, 1920, Double.NaN))
        assertNull(R5MotherMcpViewportImage.targetPixels(1080, 1920, Double.POSITIVE_INFINITY))
        assertNull(R5MotherMcpViewportImage.targetPixels(5000, 5000, 2.0))
    }
}
