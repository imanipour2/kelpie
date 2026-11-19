package com.kelpie.browser.handlers

import android.graphics.Bitmap
import com.kelpie.browser.capture.CapturePrivacyEvidence
import com.kelpie.browser.capture.ForegroundWebViewCapture
import com.kelpie.browser.capture.R5BoundedImageOutputStream
import com.kelpie.browser.capture.WebViewCapturePrivacy
import com.kelpie.browser.capture.webViewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import kotlin.math.max
import kotlin.math.roundToInt

/** All existing screenshot entry points share the same native privacy admission. */
internal suspend fun HandlerContext.capturePrivacyCheckedScreenshot(
    format: String,
    quality: Int,
    resolution: ScreenshotResolution,
): Map<String, Any?>? =
    withContext(Dispatchers.Main.immediate) {
        val view = webView ?: return@withContext null
        val before = WebViewCapturePrivacy.inspect(view)

        fun admitted(): Boolean = webView === view && remoteDocumentGuard?.invoke() != false
        if (!admitted()) return@withContext null
        if (motherPrivateAgentActive?.invoke() == true) {
            if (format != "png") return@withContext null
            val frame = motherPrivateScreenshot?.invoke() ?: return@withContext null
            before.requireSameDocument(WebViewCapturePrivacy.inspect(view))
            if (!admitted() ||
                !CapturePrivacyEvidence.hasVerifiedReceipt(frame) ||
                frame["privacyDocumentStamp"] != before.documentStamp ||
                frame["privacySceneDigest"] != before.sceneDigest
            ) {
                return@withContext null
            }
            if (resolution == ScreenshotResolution.NATIVE) return@withContext frame
            val viewport = viewportMetrics()
            return@withContext withContext(Dispatchers.Default) { R5MotherMcpViewportImage.viewport(frame, viewport) }
        }
        if (privateCaptureRestricted?.invoke() == true) return@withContext null
        val activity = webViewActivity(view) ?: return@withContext null
        val viewport = viewportMetrics()
        val frame =
            ForegroundWebViewCapture.copy(view, activity) {
                admitted() && privateCaptureRestricted?.invoke() != true && motherPrivateAgentActive?.invoke() != true
            } ?: return@withContext null
        val bitmap = frame.bitmap
        var resized: Bitmap? = null
        try {
            before.requireSameDocument(WebViewCapturePrivacy.inspect(view))
            if (!admitted()) return@withContext null
            val rendered =
                if (resolution == ScreenshotResolution.VIEWPORT) {
                    val width = max((bitmap.width / max(viewport.devicePixelRatio, 1.0)).roundToInt(), 1)
                    val height = max((bitmap.height / max(viewport.devicePixelRatio, 1.0)).roundToInt(), 1)
                    if (width == bitmap.width && height == bitmap.height) {
                        bitmap
                    } else {
                        Bitmap.createScaledBitmap(bitmap, width, height, true).also { resized = it }
                    }
                } else {
                    bitmap
                }
            val stream = R5BoundedImageOutputStream()
            val type = if (format == "jpeg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            if (!rendered.compress(type, quality.coerceIn(0, 100), stream)) return@withContext null
            before.requireSameDocument(WebViewCapturePrivacy.inspect(view))
            if (!admitted()) return@withContext null
            mapOf(
                "image" to Base64.getEncoder().encodeToString(stream.toByteArray()),
                "freshnessProof" to "DOCUMENT_PIXEL_PAIR",
                "proofRegionsRedacted" to true,
            ) + frame.metadata + before.metadata() +
                viewport.metadata(rendered.width, rendered.height, if (format == "jpeg") "jpeg" else "png", resolution)
        } finally {
            if (resized !== bitmap) resized?.recycle()
            bitmap.recycle()
        }
    }
