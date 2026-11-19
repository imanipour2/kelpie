package com.kelpie.browser.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5FrameContentCheckTest {
    @Test
    fun uniformGrayAndUniformWhiteAreRefused() {
        assertFalse(R5FrameContentCheck.hasContent(100, 100) { _, _ -> 0xff777777.toInt() })
        assertFalse(R5FrameContentCheck.hasContent(100, 100) { _, _ -> 0xffffffff.toInt() })
    }

    @Test
    fun lateTransparentSampleCannotBeHiddenByEarlyContentVariation() {
        assertFalse(
            R5FrameContentCheck.hasContent(100, 100) { x, y ->
                when {
                    y > 80 -> 0
                    x > 30 -> 0xffffffff.toInt()
                    else -> 0xff000000.toInt()
                }
            },
        )
    }

    @Test
    fun onlyOpaqueVariationOutsideProofRegionsQualifies() {
        val varied: (Int, Int) -> Int = { x, _ -> if (x < 50) 0xff112233.toInt() else 0xffaabbcc.toInt() }
        assertTrue(R5FrameContentCheck.hasContent(100, 100, pixelAt = varied))
        assertFalse(R5FrameContentCheck.hasContent(100, 100, { x, _ -> x >= 50 }, varied))
        assertFalse(R5FrameContentCheck.hasContent(0, 100, pixelAt = varied))
        assertFalse(R5FrameContentCheck.hasContent(Int.MAX_VALUE, Int.MAX_VALUE, pixelAt = varied))
    }
}
