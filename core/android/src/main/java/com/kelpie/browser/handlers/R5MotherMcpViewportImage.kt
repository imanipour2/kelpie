package com.kelpie.browser.handlers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kelpie.browser.capture.CapturePrivacyEvidence
import com.kelpie.browser.capture.R5BoundedImageOutputStream
import java.util.Base64
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Converts a freshly nonce-verified R5 native private frame into the viewport
 * PNG required by upstream tab.screenshot. Never captures an Activity or caches
 * a frame; native owner consent is checked by the caller before/after delivery.
 */
internal object R5MotherMcpViewportImage {
    private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    private const val MAX_IMAGE_PIXELS = 4_194_304L
    private const val MAX_OUTPUT_BYTES = 8 * 1024 * 1024

    internal fun targetPixels(
        width: Int,
        height: Int,
        dpr: Double,
    ): Pair<Int, Int>? {
        if (width < 1 || height < 1 || width.toLong() * height > MAX_IMAGE_PIXELS) return null
        if (!dpr.isFinite() || dpr < 1.0 || dpr > 8.0) return null
        return max((width / dpr).roundToInt(), 1) to max((height / dpr).roundToInt(), 1)
    }

    fun viewport(
        native: Map<String, Any?>,
        metrics: ScreenshotViewportMetrics,
    ): Map<String, Any?>? {
        if (!CapturePrivacyEvidence.hasVerifiedReceipt(native)) return null
        val base64 = native["image"] as? String ?: return null
        if (base64.length > MAX_IMAGE_BYTES * 2) return null
        val png = runCatching { Base64.getDecoder().decode(base64) }.getOrNull() ?: return null
        if (png.size > MAX_IMAGE_BYTES) return null

        // Decode only after validating native frame dimensions and compressed size.
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
        val nativeWidth = bounds.outWidth
        val nativeHeight = bounds.outHeight
        val outputPixels = targetPixels(nativeWidth, nativeHeight, metrics.devicePixelRatio) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        var resized: Bitmap? = null
        return try {
            val target =
                if (bitmap.width == outputPixels.first && bitmap.height == outputPixels.second) {
                    bitmap
                } else {
                    Bitmap.createScaledBitmap(bitmap, outputPixels.first, outputPixels.second, true).also { resized = it }
                }
            val output = R5BoundedImageOutputStream(MAX_OUTPUT_BYTES)
            if (!target.compress(Bitmap.CompressFormat.PNG, 100, output)) return null
            val bytes = output.toByteArray()
            if (bytes.isEmpty() || bytes.size > MAX_OUTPUT_BYTES) return null
            native.filterKeys { it != "image" } +
                mapOf("image" to Base64.getEncoder().encodeToString(bytes)) +
                metrics.metadata(
                    imageWidth = target.width,
                    imageHeight = target.height,
                    format = "png",
                    resolution = ScreenshotResolution.VIEWPORT,
                )
        } catch (_: Exception) {
            null
        } finally {
            if (resized !== bitmap) resized?.recycle()
            bitmap.recycle()
        }
    }
}
