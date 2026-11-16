package com.kelpie.browser.agent

import com.kelpie.browser.capture.R5AdaptiveCapturePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R5AdaptiveCapturePolicyTest {
    private val policy = R5AdaptiveCapturePolicy
    private val capable = R5AdaptiveCapturePolicy.DeviceSignals(256, false, false, false, false)
    private val mapped =
        R5AdaptiveCapturePolicy.ConsumerCapabilities(
            webpLossless = true,
            coordinateScaleMetadata = true,
        )

    private fun request(
        intent: R5AdaptiveCapturePolicy.Intent,
        consumer: R5AdaptiveCapturePolicy.ConsumerCapabilities = mapped,
        maxLongEdge: Int? = null,
    ): R5AdaptiveCapturePolicy.Request =
        R5AdaptiveCapturePolicy.Request(
            intent = intent,
            sourceWidth = 1080,
            sourceHeight = 1920,
            consumer = consumer,
            maxLongEdge = maxLongEdge,
        )

    private fun ready(result: R5AdaptiveCapturePolicy.Decision): R5AdaptiveCapturePolicy.Decision.Ready {
        assertTrue(result is R5AdaptiveCapturePolicy.Decision.Ready)
        return result as R5AdaptiveCapturePolicy.Decision.Ready
    }

    @Test
    fun legacyRequestAlwaysUsesOriginalPng() {
        val value = ready(policy.choose(request(R5AdaptiveCapturePolicy.Intent.LEGACY), capable))
        assertEquals(1080, value.outputWidth)
        assertEquals(1920, value.outputHeight)
        assertEquals(R5AdaptiveCapturePolicy.Encoding.PNG, value.encoding)
    }

    @Test
    fun overviewOnCapableDeviceScalesWithExplicitMappingSupport() {
        val value = ready(policy.choose(request(R5AdaptiveCapturePolicy.Intent.OVERVIEW), capable))
        assertEquals(864, value.outputWidth)
        assertEquals(1536, value.outputHeight)
        assertEquals(R5AdaptiveCapturePolicy.Encoding.WEBP_LOSSLESS, value.encoding)
        assertEquals(0.8, value.scaleX, 0.0001)
    }

    @Test
    fun overviewOnLowRamDeviceUsesConstrainedSize() {
        val value = ready(policy.choose(request(R5AdaptiveCapturePolicy.Intent.OVERVIEW), capable.copy(lowRamDevice = true)))
        assertEquals(720, value.outputWidth)
        assertEquals(1280, value.outputHeight)
    }

    @Test
    fun slowObservedUplinkConservativelyReducesOverview() {
        val value = ready(policy.choose(request(R5AdaptiveCapturePolicy.Intent.OVERVIEW), capable.copy(observedUplinkKbps = 400)))
        assertEquals(1280, value.outputHeight)
    }

    @Test
    fun slowCompletedDeliveryConservativelyReducesOverview() {
        val value = ready(policy.choose(request(R5AdaptiveCapturePolicy.Intent.OVERVIEW), capable.copy(previousDeliveryMs = 3000)))
        assertEquals(1280, value.outputHeight)
    }

    @Test
    fun interactionNeverDownsamplesEvenWhenDeviceIsWeak() {
        val value = ready(policy.choose(request(R5AdaptiveCapturePolicy.Intent.COORDINATE_ACTION), capable.copy(memoryClassMiB = 128)))
        assertEquals(1080, value.outputWidth)
        assertEquals(1920, value.outputHeight)
        assertEquals(R5AdaptiveCapturePolicy.Encoding.WEBP_LOSSLESS, value.encoding)
    }

    @Test
    fun fineTextDefersRatherThanDowngradesDuringMemoryPressure() {
        assertEquals(
            R5AdaptiveCapturePolicy.Decision.Deferred("PRECISE_CAPTURE_RESOURCE_PRESSURE"),
            policy.choose(request(R5AdaptiveCapturePolicy.Intent.FINE_TEXT), capable.copy(lowMemoryNow = true)),
        )
    }

    @Test
    fun evidenceDefersDuringSevereThermalPressure() {
        assertEquals(
            R5AdaptiveCapturePolicy.Decision.Deferred("PRECISE_CAPTURE_RESOURCE_PRESSURE"),
            policy.choose(request(R5AdaptiveCapturePolicy.Intent.VISUAL_EVIDENCE), capable.copy(severeThermalPressure = true)),
        )
    }

    @Test
    fun unnegotiatedConsumerAlwaysRetainsOriginalPngCoordinates() {
        val value = ready(policy.choose(request(R5AdaptiveCapturePolicy.Intent.OVERVIEW, R5AdaptiveCapturePolicy.ConsumerCapabilities()), capable))
        assertEquals(1080, value.outputWidth)
        assertEquals(1920, value.outputHeight)
        assertEquals(R5AdaptiveCapturePolicy.Encoding.PNG, value.encoding)
    }

    @Test
    fun incompatibleReceiverCapFailsInsteadOfReturningOversizePng() {
        val noScaleMetadata = R5AdaptiveCapturePolicy.ConsumerCapabilities()
        assertEquals(
            R5AdaptiveCapturePolicy.Decision.Deferred("RECEIVER_CANNOT_MAP_SCALED_OUTPUT"),
            policy.choose(request(R5AdaptiveCapturePolicy.Intent.OVERVIEW, noScaleMetadata, maxLongEdge = 1280), capable),
        )
    }

    @Test
    fun lossyEncodingIsOnlySelectedForPhotoIntent() {
        val caps = mapped.copy(webpLossy = true)
        val photo = ready(policy.choose(request(R5AdaptiveCapturePolicy.Intent.PHOTO_SCENE, caps), capable))
        val ui = ready(policy.choose(request(R5AdaptiveCapturePolicy.Intent.LAYOUT, caps), capable))
        assertEquals(R5AdaptiveCapturePolicy.Encoding.WEBP_LOSSY, photo.encoding)
        assertEquals(86, photo.quality)
        assertEquals(R5AdaptiveCapturePolicy.Encoding.WEBP_LOSSLESS, ui.encoding)
    }

    @Test
    fun explicitReceiverCapBelowReadabilityFloorIsRefused() {
        assertEquals(
            R5AdaptiveCapturePolicy.Decision.Deferred("RECEIVER_LIMIT_BELOW_LEGIBILITY_FLOOR"),
            policy.choose(request(R5AdaptiveCapturePolicy.Intent.OVERVIEW, maxLongEdge = 600), capable),
        )
    }

    @Test
    fun receiverResolutionCapCannotSilentlyDowngradePreciseScreenshot() {
        assertEquals(
            R5AdaptiveCapturePolicy.Decision.Deferred("RECEIVER_CANNOT_ACCEPT_PRECISE_RESOLUTION"),
            policy.choose(request(R5AdaptiveCapturePolicy.Intent.COORDINATE_ACTION, maxLongEdge = 1536), capable),
        )
    }

    @Test
    fun invalidSourceSizeFailsClosed() {
        val invalid = R5AdaptiveCapturePolicy.Request(sourceWidth = 100_000, sourceHeight = 1920)
        assertEquals(
            R5AdaptiveCapturePolicy.Decision.Deferred("INVALID_SOURCE_DIMENSIONS"),
            policy.choose(invalid, capable),
        )
    }

    @Test
    fun invalidDeviceMemoryClassFailsClosed() {
        assertEquals(
            R5AdaptiveCapturePolicy.Decision.Deferred("UNAVAILABLE_DEVICE_CAPABILITY"),
            policy.choose(request(R5AdaptiveCapturePolicy.Intent.OVERVIEW), capable.copy(memoryClassMiB = 0)),
        )
    }

    @Test
    fun smallerPhoneIsNeverUpscaledByOverview() {
        val req = R5AdaptiveCapturePolicy.Request(R5AdaptiveCapturePolicy.Intent.OVERVIEW, 360, 640, mapped)
        val value = ready(policy.choose(req, capable))
        assertEquals(360, value.outputWidth)
        assertEquals(640, value.outputHeight)
    }
}
