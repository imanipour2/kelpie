package com.kelpie.virtualdisplayprobe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameEvidenceTest {
    @Test
    fun matchingPixelsAreNotEnoughIfFrameIsOld() {
        val (red, green, blue) = ProbeFixture.colorFor(7)
        val stale = PixelSample(red, green, blue, 100L)
        assertFalse(FrameEvidence.matches(7, 100L, stale))
        assertFalse(FrameEvidence.matches(7, 101L, stale))
        assertTrue(FrameEvidence.matches(7, 99L, stale))
    }

    @Test
    fun recentWrongFrameCannotPass() {
        val (red, green, blue) = ProbeFixture.colorFor(5)
        assertFalse(FrameEvidence.matches(5, 100L, null))
        assertFalse(FrameEvidence.matches(5, 100L, PixelSample(red + 1, green, blue, 200L)))
        assertFalse(FrameEvidence.matches(5, 100L, PixelSample(red, green, blue - 1, 200L)))
        assertTrue(FrameEvidence.matches(5, 100L, PixelSample(red, green, blue, 200L)))
    }

    @Test
    fun aFrameForPreviousSequenceIsRejected() {
        val (red, green, blue) = ProbeFixture.colorFor(3)
        assertFalse(FrameEvidence.matches(4, 100L, PixelSample(red, green, blue, 200L)))
    }
}
