package com.kelpie.browser.agent

import com.kelpie.browser.agent.contract.R5VisionCaptureRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R5VisionCaptureRequestTest {
    private val parse = R5VisionCaptureRequest.Companion

    @Test
    fun legacyCallWithoutCaptureObjectIsAlwaysPreserved() {
        assertEquals(
            R5VisionCaptureRequest.Parse.Valid(R5VisionCaptureRequest.LEGACY),
            parse.parse(null),
        )
    }

    @Test
    fun autoOverviewSupportsNegotiatedScaledCoordinates() {
        val parsed =
            parse.parse(
                mapOf(
                    "quality" to "auto",
                    "purpose" to "overview",
                    "receiverSupportsScaleMetadata" to true,
                    "maxLongEdge" to 1280,
                ),
            )
        assertTrue(parsed is R5VisionCaptureRequest.Parse.Valid)
        val request = (parsed as R5VisionCaptureRequest.Parse.Valid).request
        assertEquals(R5VisionCaptureRequest.Quality.AUTO, request.quality)
        assertEquals("overview", request.purpose)
        assertEquals(1280, request.maxLongEdge)
    }

    @Test
    fun highFineTextRequestIsExplicit() {
        val parsed = parse.parse(mapOf("quality" to "high", "purpose" to "fine_text"))
        assertTrue(parsed is R5VisionCaptureRequest.Parse.Valid)
        val request = (parsed as R5VisionCaptureRequest.Parse.Valid).request
        assertEquals(R5VisionCaptureRequest.Quality.HIGH, request.quality)
        assertEquals("fine_text", request.purpose)
    }

    @Test
    fun unknownQualityCannotFallbackToLegacy() {
        assertEquals(
            R5VisionCaptureRequest.Parse.Invalid("INVALID_CAPTURE_QUALITY"),
            parse.parse(mapOf("quality" to "ultra")),
        )
    }

    @Test
    fun nestedUnknownFieldsAreRejected() {
        assertEquals(
            R5VisionCaptureRequest.Parse.Invalid("INVALID_CAPTURE_OPTIONS"),
            parse.parse(mapOf("quality" to "auto", "nativeCaptureOverride" to true)),
        )
    }

    @Test
    fun invalidPurposeIsRejected() {
        assertEquals(
            R5VisionCaptureRequest.Parse.Invalid("INVALID_CAPTURE_PURPOSE"),
            parse.parse(mapOf("quality" to "auto", "purpose" to "disableProof")),
        )
    }

    @Test
    fun typeConfusionIsRejected() {
        assertEquals(
            R5VisionCaptureRequest.Parse.Invalid("INVALID_CAPTURE_LONG_EDGE"),
            parse.parse(mapOf("quality" to "auto", "maxLongEdge" to "1280")),
        )
    }

    @Test
    fun scaledOutputMustBeExplicitlyNegotiated() {
        assertEquals(
            R5VisionCaptureRequest.Parse.Invalid("SCALE_METADATA_REQUIRED"),
            parse.parse(mapOf("quality" to "auto", "maxLongEdge" to 1280)),
        )
    }

    @Test
    fun capabilityDescriptionDoesNotClaimWebpOrCropSupport() {
        val caps = parse.capabilities()
        assertEquals(listOf("png"), caps["formats"])
        assertEquals(listOf("auto", "high", "original"), caps["qualityModes"])
        assertEquals(true, caps["nativeConsentAndDocumentEpochStillRequired"])
    }

    @Test
    fun malformedPayloadIsRejected() {
        assertEquals(
            R5VisionCaptureRequest.Parse.Invalid("INVALID_CAPTURE_OPTIONS"),
            parse.parse("auto"),
        )
    }
}
