package com.kelpie.browser.agent.contract

/**
 * Authenticated caller preference, not an authorization grant.
 * A high-detail re-capture must still pass the current session/tab/epoch gates.
 */
data class R5VisionCaptureRequest(
    val quality: Quality = Quality.LEGACY,
    val purpose: String = "legacy",
    val receiverSupportsScaleMetadata: Boolean = false,
    val maxLongEdge: Int? = null,
) {
    enum class Quality { LEGACY, AUTO, HIGH, ORIGINAL }

    sealed interface Parse {
        data class Valid(
            val request: R5VisionCaptureRequest,
        ) : Parse

        data class Invalid(
            val code: String,
        ) : Parse
    }

    companion object {
        val LEGACY = R5VisionCaptureRequest()

        private val permittedFields =
            setOf("quality", "purpose", "receiverSupportsScaleMetadata", "maxLongEdge")

        fun parse(raw: Any?): Parse {
            if (raw == null) return Parse.Valid(LEGACY)
            if (raw !is Map<*, *> || raw.keys.any { it !is String || it !in permittedFields }) {
                return Parse.Invalid("INVALID_CAPTURE_OPTIONS")
            }
            val rawQuality = raw["quality"] as? String ?: return Parse.Invalid("CAPTURE_QUALITY_REQUIRED")
            val quality =
                when (rawQuality) {
                    "auto" -> Quality.AUTO
                    "high" -> Quality.HIGH
                    "original" -> Quality.ORIGINAL
                    else -> return Parse.Invalid("INVALID_CAPTURE_QUALITY")
                }
            val rawPurpose = raw["purpose"] as? String
            val purpose =
                when (rawPurpose) {
                    null, "overview" -> "overview"
                    "layout" -> "layout"
                    "fine_text" -> "fine_text"
                    "coordinate_action" -> "coordinate_action"
                    "visual_evidence" -> "visual_evidence"
                    "photo_scene" -> "photo_scene"
                    else -> return Parse.Invalid("INVALID_CAPTURE_PURPOSE")
                }
            val scaled = raw["receiverSupportsScaleMetadata"]
            if (scaled != null && scaled !is Boolean) return Parse.Invalid("INVALID_SCALE_CAPABILITY")
            val rawLimit = raw["maxLongEdge"]
            if (rawLimit != null && (rawLimit !is Int || rawLimit !in 1024..4096)) {
                return Parse.Invalid("INVALID_CAPTURE_LONG_EDGE")
            }
            if (rawLimit != null && scaled != true) {
                return Parse.Invalid("SCALE_METADATA_REQUIRED")
            }
            return Parse.Valid(
                R5VisionCaptureRequest(
                    quality = quality,
                    purpose = purpose,
                    receiverSupportsScaleMetadata = scaled == true,
                    maxLongEdge = rawLimit as? Int,
                ),
            )
        }

        /** Discoverable only AFTER native consent; advertise only implemented PNG modes. */
        fun capabilities(): Map<String, Any> =
            mapOf(
                "version" to 1,
                "captureArgument" to "capture",
                "qualityModes" to listOf("auto", "high", "original"),
                "purposeModes" to
                    listOf(
                        "overview",
                        "layout",
                        "fine_text",
                        "coordinate_action",
                        "visual_evidence",
                        "photo_scene",
                    ),
                "formats" to listOf("png"),
                "receiverScaleMetadataRequiredForResize" to true,
                "highAndOriginalAreLossless" to true,
                "defaultWithoutCaptureArgument" to "original_png_legacy",
                "reCaptureRequiresNewCommandIdAndMonotonicClientSequence" to true,
                "nativeConsentAndDocumentEpochStillRequired" to true,
                "qualityUpgradeGuidance" to
                    "For unreadable text or precise clicks request quality=high; " +
                    "the new capture must reference the same current tab/document. " +
                    "Use DOM or owner assistance instead of repeated blind retries.",
            )
    }
}
