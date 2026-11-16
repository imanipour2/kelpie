package com.kelpie.browser.capture

import kotlin.math.roundToInt

/** Pure, side-effect-free planning. Does not capture pixels or authorize access. */
internal object R5AdaptiveCapturePolicy {
    enum class Intent { LEGACY, OVERVIEW, LAYOUT, FINE_TEXT, COORDINATE_ACTION, VISUAL_EVIDENCE, PHOTO_SCENE }
    enum class Encoding { PNG, WEBP_LOSSLESS, WEBP_LOSSY, JPEG }

    data class DeviceSignals(
        val memoryClassMiB: Int,
        val lowRamDevice: Boolean,
        val lowMemoryNow: Boolean,
        val severeThermalPressure: Boolean,
        val powerSaveMode: Boolean,
        /** Observed end-to-end upload performance, not the first-hop link estimate. */
        val observedUplinkKbps: Int? = null,
        val previousDeliveryMs: Long? = null,
    )

    data class ConsumerCapabilities(
        val webpLossless: Boolean = false,
        val webpLossy: Boolean = false,
        val jpeg: Boolean = false,
        /** Mandatory for all scaled capture results, including future click coordinate mapping. */
        val coordinateScaleMetadata: Boolean = false,
    )

    data class Request(
        val intent: Intent = Intent.LEGACY,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val consumer: ConsumerCapabilities = ConsumerCapabilities(),
        val maxLongEdge: Int? = null,
    )

    sealed interface Decision {
        data class Ready(
            val outputWidth: Int,
            val outputHeight: Int,
            val encoding: Encoding,
            val quality: Int,
            val sourceWidth: Int,
            val sourceHeight: Int,
        ) : Decision {
            val scaleX: Double get() = outputWidth.toDouble() / sourceWidth
            val scaleY: Double get() = outputHeight.toDouble() / sourceHeight
        }

        data class Deferred(val reason: String) : Decision
    }

    fun choose(request: Request, signals: DeviceSignals): Decision {
        val width = request.sourceWidth
        val height = request.sourceHeight
        if (width !in 1..4096 || height !in 1..4096 || width.toLong() * height > 8_388_608) {
            return Decision.Deferred("INVALID_SOURCE_DIMENSIONS")
        }
        if (request.maxLongEdge != null && request.maxLongEdge !in 1..4096) {
            return Decision.Deferred("INVALID_RECEIVER_LIMIT")
        }
        if (signals.memoryClassMiB <= 0) return Decision.Deferred("UNAVAILABLE_DEVICE_CAPABILITY")

        val precise = request.intent in setOf(Intent.FINE_TEXT, Intent.COORDINATE_ACTION, Intent.VISUAL_EVIDENCE)
        if ((signals.lowMemoryNow || signals.severeThermalPressure) && precise) {
            return Decision.Deferred("PRECISE_CAPTURE_RESOURCE_PRESSURE")
        }
        // Old clients get exactly the existing native PNG contract. Never reinterpret
        // a legacy screenshot as permission to reduce quality or change coordinates.
        if (request.intent == Intent.LEGACY) return ready(width, height, Encoding.PNG, 100, width, height)

        val isConstrained = signals.lowRamDevice || signals.memoryClassMiB < 192 ||
            signals.lowMemoryNow || signals.severeThermalPressure || signals.powerSaveMode ||
            (signals.observedUplinkKbps != null && signals.observedUplinkKbps in 1..511) ||
            (signals.previousDeliveryMs != null && signals.previousDeliveryMs > 2_000)

        if (precise) {
            if (request.maxLongEdge != null && request.maxLongEdge < maxOf(width, height)) {
                return Decision.Deferred("RECEIVER_CANNOT_ACCEPT_PRECISE_RESOLUTION")
            }
            return ready(width, height, lossless(request.consumer), 100, width, height)
        }

        val nativeLongEdge = maxOf(width, height)
        val desired = when (request.intent) {
            Intent.OVERVIEW, Intent.PHOTO_SCENE -> if (isConstrained) 1280 else 1536
            Intent.LAYOUT -> if (isConstrained) 1536 else 1920
            else -> nativeLongEdge
        }
        val minimum = minOf(nativeLongEdge, if (request.intent == Intent.LAYOUT) 1280 else 1024)
        if (request.maxLongEdge != null && request.maxLongEdge < minimum) {
            return Decision.Deferred("RECEIVER_LIMIT_BELOW_LEGIBILITY_FLOOR")
        }
        val longEdge = minOf(nativeLongEdge, desired, request.maxLongEdge ?: nativeLongEdge)
        // Without explicit support for scale metadata, preserve pixel coordinates.
        val outputLongEdge = if (request.consumer.coordinateScaleMetadata) longEdge else nativeLongEdge
        val ratio = outputLongEdge.toDouble() / nativeLongEdge
        val outputWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        val outputHeight = (height * ratio).roundToInt().coerceAtLeast(1)
        val encoding = when (request.intent) {
            Intent.PHOTO_SCENE -> when {
                request.consumer.webpLossy -> Encoding.WEBP_LOSSY
                request.consumer.jpeg -> Encoding.JPEG
                else -> Encoding.PNG
            }
            else -> lossless(request.consumer)
        }
        val quality = if (encoding == Encoding.JPEG || encoding == Encoding.WEBP_LOSSY) 86 else 100
        return ready(outputWidth, outputHeight, encoding, quality, width, height)
    }

    private fun lossless(c: ConsumerCapabilities): Encoding =
        if (c.webpLossless) Encoding.WEBP_LOSSLESS else Encoding.PNG

    private fun ready(w: Int, h: Int, f: Encoding, quality: Int, sw: Int, sh: Int) =
        Decision.Ready(w, h, f, quality, sw, sh)
}
