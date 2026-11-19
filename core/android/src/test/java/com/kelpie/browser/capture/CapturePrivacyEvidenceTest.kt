package com.kelpie.browser.capture

import com.kelpie.browser.handlers.ScreenshotRefusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePrivacyEvidenceTest {
    private val original = CapturePrivacyEvidence(12, "a".repeat(64), "01234567", 390, 844, 0.0, 0.0)

    @Test
    fun captureRequiresTheSameRendererDocumentSceneAndViewport() {
        original.requireSameDocument(original.copy())
        val invalid =
            listOf(
                original.copy(rendererIdentity = 13),
                original.copy(documentStamp = "b".repeat(64)),
                original.copy(sceneDigest = "89abcdef"),
                original.copy(viewportWidth = 844, viewportHeight = 390),
                original.copy(scrollY = 100.0),
            )
        for (changed in invalid) {
            assertThrows(ScreenshotRefusal::class.java) { original.requireSameDocument(changed) }
        }
    }

    @Test
    fun rawOrIncompletePngMetadataCannotMasqueradeAsAPrivacyCheckedFrame() {
        assertTrue(CapturePrivacyEvidence.hasVerifiedReceipt(original.metadata()))
        assertFalse(CapturePrivacyEvidence.hasVerifiedReceipt(mapOf("image" to "unverified-png")))
        assertFalse(CapturePrivacyEvidence.hasVerifiedReceipt(original.metadata() - "privacyDocumentStamp"))
        assertFalse(CapturePrivacyEvidence.hasVerifiedReceipt(original.metadata() + ("redactionStatus" to "MASKED_BY_DOM")))
        assertFalse(CapturePrivacyEvidence.hasVerifiedReceipt(original.metadata() + ("privacyDocumentStamp" to "https://private/")))
    }

    @Test
    fun everyNativeOrScaledImageMapsToTheMeasuredCssViewport() {
        val privacy = CapturePrivacyEvidence(1, "a".repeat(64), "12345678", 400, 300, 0.0, 0.0, 3.0)
        val original = privacy.imageMetadata(1200, 900)
        assertEquals(400, original["viewportWidth"])
        assertEquals(300, original["viewportHeight"])
        assertEquals(3.0, original["devicePixelRatio"])
        assertEquals(3.0, original["imageScaleX"])
        assertEquals(3.0, original["imageScaleY"])
        assertEquals("viewport-css-pixels", original["coordinateSpace"])
        val scaled = privacy.imageMetadata(600, 360)
        assertEquals(1.5, scaled["imageScaleX"])
        assertEquals(1.2, scaled["imageScaleY"])
        val transform =
            com.kelpie.browser.handlers
                .ScreenshotTransform(1.5, 1.2)
        assertEquals(100.0 to 50.0, transform.cssPoint(150.0, 60.0))
    }
}
