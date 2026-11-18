package com.kelpie.browser.handlers

import android.app.KeyguardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import com.kelpie.browser.BuildConfig
import com.kelpie.browser.nativecore.BrowserGatewayNative
import com.kelpie.browser.network.Router
import com.kelpie.browser.network.errorResponse
import com.kelpie.browser.network.successResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max

enum class ScreenshotResolution(
    val wireValue: String,
) {
    NATIVE("native"),
    VIEWPORT("viewport"),
    ;

    companion object {
        fun parse(raw: Any?): ScreenshotResolution? =
            when (raw) {
                null -> NATIVE
                is String -> entries.firstOrNull { it.wireValue == raw }
                else -> null
            }
    }
}

data class ScreenshotViewportMetrics(
    val viewportWidth: Int,
    val viewportHeight: Int,
    val devicePixelRatio: Double,
) {
    fun metadata(
        imageWidth: Int,
        imageHeight: Int,
        format: String,
        resolution: ScreenshotResolution,
    ): Map<String, Any> {
        val scaleX = if (viewportWidth > 0) imageWidth.toDouble() / viewportWidth.toDouble() else 1.0
        val scaleY = if (viewportHeight > 0) imageHeight.toDouble() / viewportHeight.toDouble() else 1.0
        return mapOf(
            "width" to imageWidth,
            "height" to imageHeight,
            "format" to format,
            "resolution" to resolution.wireValue,
            "coordinateSpace" to "viewport-css-pixels",
            "viewportWidth" to viewportWidth,
            "viewportHeight" to viewportHeight,
            "devicePixelRatio" to devicePixelRatio,
            "imageScaleX" to scaleX,
            "imageScaleY" to scaleY,
        )
    }
}

/**
 * One capture implementation for plain, diagnostic and annotated screenshots.
 * The existing WebView is used; no renderer, service or permission system is added.
 */
class ScreenshotHandler(
    private val ctx: HandlerContext,
) {
    fun register(router: Router) {
        router.register("screenshot") { body ->
            try {
                val resolution = ScreenshotResolution.parse(body["resolution"])
                    ?: return@register errorResponse("INVALID_PARAMS", "Unknown screenshot resolution")
                if (body["fullPage"] == true) {
                    captureFullPagePayload("png", 80, resolution)
                }
                val payload = captureViewportPayload(
                    body["format"] as? String ?: "png",
                    (body["quality"] as? Number)?.toInt() ?: 80,
                    resolution,
                )
                successResponse(payload)
            } catch (error: ScreenshotRefusal) {
                errorResponse(error.code, error.message ?: "Capture refused")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                errorResponse("NO_FRESH_FRAME", "No safely verified frame was produced")
            }
        }
    }

    /**
     * Immediate scroll-and-draw did not establish freshness or redaction per tile.
     * Keep the entry point but refuse until the existing R5 provider supports this.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun captureFullPagePayload(
        format: String,
        quality: Int,
        resolution: ScreenshotResolution,
    ): Map<String, Any?>? =
        throw ScreenshotRefusal("NEEDS_HUMAN", "Full-page capture has no verified per-tile privacy and freshness coverage")

    suspend fun captureAnnotatedPayload(
        format: String,
        quality: Int,
        resolution: ScreenshotResolution,
    ): Map<String, Any?> {
        return captureViewportPayload(format, quality, resolution, annotated = true)
    }

    suspend fun captureViewportPayload(
        format: String = "png",
        quality: Int = 80,
        resolution: ScreenshotResolution = ScreenshotResolution.NATIVE,
        annotated: Boolean = false,
    ): Map<String, Any?> =
        withContext(Dispatchers.Main.immediate) {
            if (format !in setOf("png", "jpeg") || quality !in 0..100) {
                throw ScreenshotRefusal("INVALID_PARAMS", "Invalid image encoding parameters")
            }
            val view = ctx.webView ?: throw ScreenshotRefusal("NO_FRESH_FRAME", "No renderer")
            // Page-realm JSON cannot attest that real account pixels are safe. All
            // callers share this native fixture gate, including plain screenshots.
            requireSyntheticScreenshotFixture(BuildConfig.KAI_ANNOTATION_QA_ENABLED, view.url)
            val tab = ctx.tabStore?.tabs?.value?.firstOrNull { it.webView === view }
                ?: throw ScreenshotRefusal("NO_FRESH_FRAME", "Renderer ownership is unavailable")
            val epoch = tab.documentVersion
            val activeTab = ctx.tabStore?.activeTabId?.value
            fun guard() {
                requireSyntheticScreenshotFixture(BuildConfig.KAI_ANNOTATION_QA_ENABLED, view.url)
                if (ctx.remoteDocumentGuard?.invoke() == false ||
                    ctx.scriptPlaybackState?.isAbortRequested() == true ||
                    ctx.webView !== view || tab.documentVersion != epoch ||
                    ctx.tabStore?.activeTabId?.value != activeTab ||
                    ctx.tabStore?.tabs?.value?.none { it === tab && it.webView === view } != false
                ) {
                    throw ScreenshotRefusal("OBSERVATION_RACE", "The renderer, document, target or authority changed")
                }
                if (view.context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked != false) {
                    throw ScreenshotRefusal("NEEDS_HUMAN", "Unlock the device before capture")
                }
                if (!view.isAttachedToWindow || !view.isShown || view.windowVisibility != View.VISIBLE || !view.hasWindowFocus()) {
                    throw ScreenshotRefusal("UNSUPPORTED_IN_BACKGROUND", "Background capture requires the accepted R5 frame provider")
                }
            }
            guard()
            val before = observation()
            requirePrivateCoverage(before)
            val viewport = captureMetrics(before)
            var bitmap: Bitmap? = null
            var rendered: Bitmap? = null
            val maxBytes = minOf(Runtime.getRuntime().maxMemory() / 8, 32L * 1024 * 1024)
            fun budget(width: Int, height: Int) {
                val retained = (bitmap?.allocationByteCount?.toLong() ?: 0L) +
                    (if (rendered !== bitmap) rendered?.allocationByteCount?.toLong() ?: 0L else 0L)
                requireScreenshotAllocation(width, height, retained, maxBytes)
            }
            try {
                awaitVisualState(view, ::guard)
                guard()
                budget(view.width, view.height)
                // Ownership is established before draw, outside the timeout boundary.
                bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                guard()
                val after = observation()
                requirePrivateCoverage(after)
                if (frameEvidence(before) != frameEvidence(after)) {
                    throw ScreenshotRefusal("OBSERVATION_RACE", "Page geometry changed during capture")
                }
                if (uniform(bitmap)) throw ScreenshotRefusal("NO_FRESH_FRAME", "Uniform or empty frame cannot establish a usable observation")
                rendered = if (resolution == ScreenshotResolution.VIEWPORT &&
                    (bitmap.width != viewport.viewportWidth || bitmap.height != viewport.viewportHeight)
                ) {
                    budget(viewport.viewportWidth, viewport.viewportHeight)
                    Bitmap.createScaledBitmap(bitmap, viewport.viewportWidth, viewport.viewportHeight, true)
                } else {
                    bitmap
                }
                val labels = if (annotated) annotations(before) else emptyList()
                // An annotation always owns a copy. Every concurrent allocation is budgeted.
                if (annotated) {
                    budget(rendered.width, rendered.height)
                    val copy = rendered.copy(Bitmap.Config.ARGB_8888, true)
                        ?: throw ScreenshotRefusal("NO_FRESH_FRAME", "Could not allocate annotation bitmap")
                    if (rendered !== bitmap) rendered.recycle()
                    rendered = copy
                    drawAnnotations(rendered, labels, viewport)
                }
                guard()
                val stream = ByteArrayOutputStream()
                val encoding = if (format == "jpeg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                if (!rendered.compress(encoding, quality, stream)) {
                    throw ScreenshotRefusal("NO_FRESH_FRAME", "Image encoding failed")
                }
                if (stream.size() > 8 * 1024 * 1024) throw ScreenshotRefusal("SCREENSHOT_TOO_LARGE", "Encoded frame exceeds the image budget")
                guard()
                val observationId = UUID.randomUUID().toString()
                val payload = mutableMapOf<String, Any?>(
                    "image" to Base64.getEncoder().encodeToString(stream.toByteArray()),
                    "observationId" to observationId,
                    "tabId" to tab.id,
                    "rendererIdentity" to System.identityHashCode(view).toString(16),
                    "documentEpoch" to epoch,
                    "capturedAt" to Instant.now().toString(),
                    "consentGeneration" to null,
                    "frameNonce" to null,
                    "readOnly" to true,
                    "actionByRefAvailable" to false,
                    "freshness" to "foreground-callback-unattested",
                    "privacy" to "synthetic-fixture-only-no-privacy-attestation",
                    "coverage" to before["coverage"],
                    "viewport" to before["viewport"],
                    "visualViewport" to before["visualViewport"],
                    "scroll" to before["scroll"],
                )
                payload.putAll(viewport.metadata(rendered.width, rendered.height, format, resolution))
                if (annotated) {
                    payload["annotations"] = labels
                    payload["annotationCoverage"] = mapOf(
                        "drawn" to labels.count { it["labelDrawn"] == true },
                        "omitted" to labels.count { it["labelDrawn"] != true },
                    )
                    payload["annotationSessionId"] = observationId
                    payload["validUntil"] = "display-only"
                    payload["hint"] = "E labels are visual only. Blind index actions are disabled; use a fresh canonical snapshot."
                }
                payload
            } catch (_: OutOfMemoryError) {
                throw ScreenshotRefusal("NO_FRESH_FRAME", "Capture exceeded the bounded bitmap budget")
            } finally {
                if (rendered !== bitmap) rendered?.recycle()
                bitmap?.recycle()
            }
        }

    private suspend fun observation(): Map<String, Any?> =
        ctx.evaluateJSReturningJSON(
            BrowserGatewayNative.selectorSnapshotExpression(
                """{"maxElements":50,"maxChars":16000,"prefix":"k","includeShadow":false}""",
            ),
        )

    private fun requirePrivateCoverage(snapshot: Map<String, Any?>) {
        val privacy = snapshot["privacyRects"] as? List<*>
        val coverage = snapshot["coverage"] as? Map<*, *>
        if (snapshot["screenshotBlocked"] != false || privacy == null || privacy.isNotEmpty() || coverage == null) {
            throw ScreenshotRefusal("NEEDS_HUMAN", "Sensitive or opaque content cannot be safely redacted by this frame provider")
        }
    }

    private fun frameEvidence(snapshot: Map<String, Any?>): List<Any?> =
        listOf(snapshot["url"], snapshot["elements"], snapshot["viewport"], snapshot["visualViewport"], snapshot["scroll"], snapshot["coverage"])

    private fun captureMetrics(snapshot: Map<String, Any?>): ScreenshotViewportMetrics {
        val viewport = snapshot["viewport"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val visual = snapshot["visualViewport"] as? Map<*, *> ?: emptyMap<Any, Any>()
        fun value(map: Map<*, *>, key: String): Double = (map[key] as? Number)?.toDouble() ?: Double.NaN
        val width = value(viewport, "width")
        val height = value(viewport, "height")
        val valid = listOf(width, height).all { it.isFinite() && it >= 1 && it <= 8192 }
        val visualValid = listOf("width", "height", "scale", "offsetLeft", "offsetTop").all { value(visual, it).isFinite() }
        val dpr = (snapshot["devicePixelRatio"] as? Number)?.toDouble() ?: Double.NaN
        if (!valid || !visualValid || !dpr.isFinite() || dpr <= 0 ||
            abs(value(visual, "width") - width) > 1 || abs(value(visual, "height") - height) > 1 ||
            value(visual, "scale") != 1.0 || value(visual, "offsetLeft") != 0.0 || value(visual, "offsetTop") != 0.0
        ) {
            throw ScreenshotRefusal("INVALID_CAPTURE_GEOMETRY", "Zoom, crop or visual viewport geometry requires a verified provider transform")
        }
        return ScreenshotViewportMetrics(width.toInt(), height.toInt(), dpr)
    }

    private suspend fun awaitVisualState(
        view: WebView,
        guard: () -> Unit,
    ) {
        val completed = withTimeoutOrNull(3000) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val requestId = SystemClock.elapsedRealtimeNanos()
                try {
                    guard()
                    view.postVisualStateCallback(requestId, object : WebView.VisualStateCallback() {
                        override fun onComplete(id: Long) {
                            if (!continuation.isActive) return
                            try {
                                guard()
                                if (id != requestId) throw ScreenshotRefusal("NO_FRESH_FRAME", "Unexpected frame callback")
                                continuation.resume(Unit)
                            } catch (error: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                        }
                    })
                    view.invalidate()
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
            true
        }
        if (completed != true) throw ScreenshotRefusal("NO_FRESH_FRAME", "No fresh callback arrived within the capture deadline")
    }

    private fun uniform(bitmap: Bitmap): Boolean {
        val first = bitmap.getPixel(0, 0)
        for (y in 0..15) {
            for (x in 0..15) {
                if (bitmap.getPixel(x * (bitmap.width - 1) / 15, y * (bitmap.height - 1) / 15) != first) return false
            }
        }
        return true
    }

    private fun annotations(snapshot: Map<String, Any?>): List<MutableMap<String, Any?>> =
        (snapshot["elements"] as? List<*>).orEmpty().take(50).mapIndexedNotNull { index, value ->
            val element = value as? Map<*, *> ?: return@mapIndexedNotNull null
            mutableMapOf(
                "index" to index,
                "labelDrawn" to false,
                "omissionReason" to "OUTSIDE_VIEWPORT",
                "displayId" to "E" + (index + 1).toString().padStart(2, '0'),
                "elementRef" to null,
                "role" to element["role"],
                "name" to element["label"],
                "rect" to element["rect"],
                "sensitivity" to element["sensitivity"],
                "allowedOperations" to emptyList<String>(),
            )
        }

    private fun drawAnnotations(
        bitmap: Bitmap,
        annotations: List<MutableMap<String, Any?>>,
        viewport: ScreenshotViewportMetrics,
    ) {
        val transform = ScreenshotTransform(
            bitmap.width.toDouble() / viewport.viewportWidth,
            bitmap.height.toDouble() / viewport.viewportHeight,
        )
        val canvas = Canvas(bitmap)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 76, 160)
            style = Paint.Style.STROKE
            strokeWidth = max(1f, bitmap.width / 390f)
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (bitmap.width / 28f).coerceIn(11f, 28f)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = border.color }
        val occupied = mutableListOf<RectF>()
        for (annotation in annotations) {
            val raw = annotation["rect"] as? Map<*, *> ?: continue
            fun coordinate(key: String): Double = (raw[key] as? Number)?.toDouble() ?: Double.NaN
            val css = ScreenshotRect(coordinate("x"), coordinate("y"), coordinate("width"), coordinate("height"))
            if (!css.valid()) throw ScreenshotRefusal("INVALID_CAPTURE_GEOMETRY", "Invalid annotation bounds")
            val r = transform.imageRect(css)
            if (r.width <= 0 || r.height <= 0 || r.x >= bitmap.width || r.y >= bitmap.height || r.x + r.width <= 0 || r.y + r.height <= 0) continue
            canvas.drawRect(r.x.toFloat(), r.y.toFloat(), (r.x + r.width).toFloat(), (r.y + r.height).toFloat(), border)
            val text = annotation["displayId"] as String
            val width = label.measureText(text) + 8
            val height = label.textSize + 6
            val left = r.x.toFloat().coerceIn(0f, max(bitmap.width - width, 0f))
            var top = max(r.y.toFloat() - height, 0f)
            var box = RectF(left, top, left + width, top + height)
            while (occupied.any { RectF.intersects(it, box) } && top + height * 2 <= bitmap.height) {
                top += height
                box = RectF(left, top, left + width, top + height)
            }
            if (occupied.any { RectF.intersects(it, box) }) {
                annotation["omissionReason"] = "LABEL_COLLISION"
                continue
            }
            occupied.add(box)
            annotation["labelDrawn"] = true
            annotation["omissionReason"] = null
            canvas.drawRect(box, fill)
            canvas.drawText(text, left + 4, top + height - 4, label)
        }
    }
}
