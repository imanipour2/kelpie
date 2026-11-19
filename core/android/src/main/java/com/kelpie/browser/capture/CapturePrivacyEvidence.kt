package com.kelpie.browser.capture

import com.kelpie.browser.handlers.ScreenshotRefusal
import com.kelpie.browser.handlers.ScreenshotResolution
import com.kelpie.browser.handlers.ScreenshotViewportMetrics

/** A native admission receipt, never an assertion that DOM masking redacted pixels. */
internal data class CapturePrivacyEvidence(
    val rendererIdentity: Int,
    val documentStamp: String,
    val sceneDigest: String,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val scrollX: Double,
    val scrollY: Double,
    val devicePixelRatio: Double = 1.0,
) {
    fun requireSameDocument(after: CapturePrivacyEvidence) {
        if (this != after) {
            throw ScreenshotRefusal("OBSERVATION_RACE", "The reviewed capture document changed during capture")
        }
    }

    fun metadata(): Map<String, Any> =
        mapOf(
            "redactionStatus" to STATUS,
            "privacyDocumentStamp" to documentStamp,
            "privacySceneDigest" to sceneDigest,
        )

    /** Image/CSS mapping uses the measured document viewport, never DPR as a substitute. */
    fun imageMetadata(
        width: Int,
        height: Int,
    ): Map<String, Any> {
        if (viewportWidth <= 0 ||
            viewportHeight <= 0 ||
            width <= 0 ||
            height <= 0 ||
            !devicePixelRatio.isFinite() ||
            devicePixelRatio <= 0
        ) {
            throw ScreenshotRefusal("FRAME_PRIVACY_UNVERIFIED", "Invalid verified image geometry")
        }
        return ScreenshotViewportMetrics(viewportWidth, viewportHeight, devicePixelRatio)
            .metadata(width, height, "png", ScreenshotResolution.NATIVE)
    }

    companion object {
        const val STATUS = "REVIEWED_SYNTHETIC_FIXTURE_ONLY"

        fun hasVerifiedReceipt(native: Map<String, Any?>): Boolean =
            native["redactionStatus"] == STATUS &&
                (native["privacyDocumentStamp"] as? String)?.matches(Regex("[0-9a-f]{64}")) == true &&
                (native["privacySceneDigest"] as? String)?.matches(Regex("[0-9a-f]{8}")) == true
    }
}
