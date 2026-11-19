package com.kelpie.browser.handlers

import com.kelpie.browser.network.Router
import com.kelpie.browser.network.errorResponse
import com.kelpie.browser.network.successResponse
import kotlinx.coroutines.CancellationException

class ScreenshotHandler(
    private val ctx: HandlerContext,
) {
    fun register(router: Router) {
        router.register("screenshot") { screenshot(it) }
    }

    private suspend fun screenshot(body: Map<String, Any?>): Map<String, Any?> {
        val fullPage = body["fullPage"] as? Boolean ?: false
        val format = body["format"] as? String ?: "png"
        val quality = (body["quality"] as? Int) ?: 80
        val resolution =
            ScreenshotResolution.parse(body["resolution"])
                ?: return errorResponse("INVALID_PARAMS", "resolution must be 'native' or 'viewport'")

        return try {
            val payload =
                if (fullPage) {
                    captureFullPagePayload(format, quality, resolution)
                } else {
                    ctx.captureScreenshotPayload(format, quality, resolution)
                } ?: return errorResponse("NO_FRESH_FRAME", "No admissible fresh frame is available")
            successResponse(payload)
        } catch (error: ScreenshotRefusal) {
            errorResponse(error.code, error.message ?: "Capture refused")
        } catch (error: CancellationException) {
            throw error
        }
    }

    /** Tile freshness and privacy cannot yet be proved, so no Canvas fallback is exported. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun captureFullPagePayload(
        format: String,
        quality: Int,
        resolution: ScreenshotResolution,
    ): Map<String, Any?>? = refuseUnverifiedFullPageCapture()
}
