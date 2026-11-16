package com.kelpie.browser.capture

import android.app.Activity
import android.app.ActivityManager
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import com.kelpie.browser.agent.contract.R5VisionCaptureRequest
import java.util.Base64

/**
 * Output-only transformation of a native-verified frame.
 * PrivateWebViewDisplay still owns and recycles the original bitmap.
 */
internal class R5VerifiedFrameEncoder(
    private val activity: Activity,
) {
    /**
     * Runs only after the native compositor nonce matched. Delivery resolution
     * and format never alter the source VirtualDisplay or WebView layout.
     * Keep PNG transport for all callers until decoder capabilities are negotiated.
     */
    fun encode(
        frame: Bitmap,
        capture: R5VisionCaptureRequest,
    ): Map<String, Any>? {
        val manager = activity.getSystemService(ActivityManager::class.java)
        val power = activity.getSystemService(PowerManager::class.java)
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val severeThermal =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        val intent =
            when (capture.quality) {
                R5VisionCaptureRequest.Quality.LEGACY,
                R5VisionCaptureRequest.Quality.ORIGINAL,
                -> R5AdaptiveCapturePolicy.Intent.LEGACY
                R5VisionCaptureRequest.Quality.HIGH -> R5AdaptiveCapturePolicy.Intent.FINE_TEXT
                R5VisionCaptureRequest.Quality.AUTO ->
                    when (capture.purpose) {
                        "overview" -> R5AdaptiveCapturePolicy.Intent.OVERVIEW
                        "layout" -> R5AdaptiveCapturePolicy.Intent.LAYOUT
                        "fine_text" -> R5AdaptiveCapturePolicy.Intent.FINE_TEXT
                        "coordinate_action" -> R5AdaptiveCapturePolicy.Intent.COORDINATE_ACTION
                        "visual_evidence" -> R5AdaptiveCapturePolicy.Intent.VISUAL_EVIDENCE
                        "photo_scene" -> R5AdaptiveCapturePolicy.Intent.PHOTO_SCENE
                        else -> return null
                    }
            }
        val decision =
            R5AdaptiveCapturePolicy.choose(
                R5AdaptiveCapturePolicy.Request(
                    intent = intent,
                    sourceWidth = frame.width,
                    sourceHeight = frame.height,
                    consumer =
                        R5AdaptiveCapturePolicy.ConsumerCapabilities(
                            coordinateScaleMetadata = capture.receiverSupportsScaleMetadata,
                        ),
                    maxLongEdge = capture.maxLongEdge,
                ),
                R5AdaptiveCapturePolicy.DeviceSignals(
                    memoryClassMiB = manager.memoryClass,
                    lowRamDevice = manager.isLowRamDevice,
                    lowMemoryNow = info.lowMemory,
                    severeThermalPressure = severeThermal,
                    powerSaveMode = power.isPowerSaveMode,
                ),
            )
        if (decision !is R5AdaptiveCapturePolicy.Decision.Ready) return null

        var transformed: Bitmap? = null
        return try {
            val output =
                if (frame.width == decision.outputWidth && frame.height == decision.outputHeight) {
                    frame
                } else {
                    val scaled =
                        Bitmap.createScaledBitmap(
                            frame,
                            decision.outputWidth,
                            decision.outputHeight,
                            true,
                        )
                    transformed = scaled
                    scaled
                }
            val maxBytes =
                if (info.lowMemory || manager.isLowRamDevice || manager.memoryClass < 192) {
                    4 * 1024 * 1024
                } else {
                    R5BoundedImageOutputStream.MAX_COMPRESSED_BYTES
                }
            val bytes = R5BoundedImageOutputStream(maxBytes)
            if (!output.compress(Bitmap.CompressFormat.PNG, 100, bytes)) return null
            val result =
                mutableMapOf<String, Any>(
                    "image" to Base64.getEncoder().encodeToString(bytes.toByteArray()),
                    "width" to output.width,
                    "height" to output.height,
                    "format" to "png",
                    "coordinateSpace" to
                        if (capture.quality == R5VisionCaptureRequest.Quality.LEGACY) {
                            "viewport-css-pixels"
                        } else if (output === frame) {
                            "viewport-css-pixels"
                        } else {
                            "scaled-image-pixels"
                        },
                )
            if (capture.quality != R5VisionCaptureRequest.Quality.LEGACY) {
                result["qualityUsed"] = capture.quality.name.lowercase()
                result["sourcePixelWidth"] = frame.width
                result["sourcePixelHeight"] = frame.height
                result["scaleX"] = output.width.toDouble() / frame.width
                result["scaleY"] = output.height.toDouble() / frame.height
                result["imageToSourceScaleX"] = frame.width.toDouble() / output.width
                result["imageToSourceScaleY"] = frame.height.toDouble() / output.height
            }
            result
        } catch (_: Exception) {
            // Includes the bounded-stream overflow exception. Never log pixels.
            null
        } finally {
            if (transformed !== frame) transformed?.recycle()
        }
    }
}
