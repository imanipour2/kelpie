package com.kelpie.browser.handlers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScreenshotGeometryTest {
    @Test
    fun nativeAndViewportUseTheirActualIndependentScales() {
        val css = ScreenshotRect(20.0, 30.0, 80.0, 40.0)
        assertEquals(css, ScreenshotTransform(1.0, 1.0).imageRect(css))
        assertEquals(ScreenshotRect(60.0, 60.0, 240.0, 80.0), ScreenshotTransform(3.0, 2.0).imageRect(css))
    }

    @Test
    fun visualOffsetAndCropAreAppliedBeforeInverseMapping() {
        val transform = ScreenshotTransform(2.0, 3.0, visualOffsetX = 7.0, visualOffsetY = 11.0, cropX = 4.0, cropY = 6.0)
        val image = transform.imageRect(ScreenshotRect(17.0, 21.0, 10.0, 20.0))
        assertEquals(ScreenshotRect(16.0, 24.0, 20.0, 60.0), image)
        assertEquals(17.0 to 21.0, transform.cssPoint(image.x, image.y))
    }

    @Test
    fun invalidGeometryIsRefusedInsteadOfClampingToAnotherTarget() {
        assertThrows(IllegalArgumentException::class.java) { ScreenshotTransform(0.0, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { ScreenshotTransform(Double.NaN, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { ScreenshotTransform(1.0, 1.0).cssPoint(Double.POSITIVE_INFINITY, 0.0) }
        assertThrows(IllegalArgumentException::class.java) {
            ScreenshotTransform(1.0, 1.0).imageRect(ScreenshotRect(0.0, 0.0, -1.0, 5.0))
        }
    }

    @Test
    fun everyImageExportRequiresTheNativeSyntheticFixtureGate() {
        assertThrows(ScreenshotRefusal::class.java) { requireSyntheticScreenshotFixture(false, "https://kai-fixture.invalid/") }
        for (url in listOf(null, "https://example.com/", "https://kai-fixture.invalid.evil/", "https://kai-fixture.invalid/?secret=x")) {
            val error = assertThrows(ScreenshotRefusal::class.java) { requireSyntheticScreenshotFixture(true, url) }
            assertEquals("NEEDS_HUMAN", error.code)
        }
        requireSyntheticScreenshotFixture(true, "https://kai-fixture.invalid/")
    }

    @Test
    fun fullPageCannotExportUnverifiedCanvasTiles() {
        val error = assertThrows(ScreenshotRefusal::class.java) { refuseUnverifiedFullPageCapture() }
        assertEquals("UNSUPPORTED", error.code)
    }

    @Test
    fun bitmapBudgetIncludesRetainedImagesAndRejectsOverflowDimensions() {
        requireScreenshotAllocation(100, 100, 40_000L, 80_000L)
        assertThrows(ScreenshotRefusal::class.java) { requireScreenshotAllocation(100, 100, 40_001L, 80_000L) }
        assertThrows(ScreenshotRefusal::class.java) { requireScreenshotAllocation(8192, 8192, 0L, 32L * 1024 * 1024) }
        assertThrows(ScreenshotRefusal::class.java) { requireScreenshotAllocation(Int.MAX_VALUE, Int.MAX_VALUE, 0L, Long.MAX_VALUE) }
    }
}
