package com.kelpie.browser.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class R5PageFrameProofTest {
    @Test fun mapsCssMarkerToMeasuredNativeViewport() {
        val samples = R5PageFrameProof.samples(listOf(312.0, 0.0, 48.0, 24.0, 360.0, 640.0), 1080, 1920)
        assertNotNull(samples)
        assertEquals(972, samples!!.firstX)
        assertEquals(1044, samples.secondX)
        assertEquals(36, samples.y)
        assertEquals(R5RgbaFrameBuffer.Region(936, 0, 1080, 72), samples.region)
    }

    @Test fun rejectsZeroForeignOffscreenAndNonFiniteViewportEvidence() {
        val invalid =
            listOf(
                listOf(0.0, 0.0, 48.0, 24.0, 0.0, 640.0),
                listOf(0.0, 0.0, 48.0, 24.0, 360.0, Double.NaN),
                listOf(350.0, 0.0, 48.0, 24.0, 360.0, 640.0),
                listOf(0.0, -1.0, 48.0, 24.0, 360.0, 640.0),
                listOf(0.0, 0.0, 2.0, 24.0, 360.0, 640.0),
            )
        invalid.forEach { assertNull(R5PageFrameProof.samples(it, 1080, 1920)) }
    }
}
