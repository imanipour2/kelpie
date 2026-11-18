package com.kelpie.browser.handlers

/** Pure image/CSS mapping; DPR is deliberately not part of the transform. */
data class ScreenshotRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    fun valid(): Boolean = listOf(x, y, width, height).all { it.isFinite() } && width >= 0 && height >= 0
}

data class ScreenshotTransform(
    val imageScaleX: Double,
    val imageScaleY: Double,
    val visualOffsetX: Double = 0.0,
    val visualOffsetY: Double = 0.0,
    val cropX: Double = 0.0,
    val cropY: Double = 0.0,
) {
    init {
        require(listOf(imageScaleX, imageScaleY, visualOffsetX, visualOffsetY, cropX, cropY).all { it.isFinite() })
        require(imageScaleX > 0 && imageScaleY > 0)
    }

    fun imageRect(css: ScreenshotRect): ScreenshotRect {
        require(css.valid())
        return ScreenshotRect(
            (css.x - visualOffsetX) * imageScaleX - cropX,
            (css.y - visualOffsetY) * imageScaleY - cropY,
            css.width * imageScaleX,
            css.height * imageScaleY,
        )
    }

    fun cssPoint(
        imageX: Double,
        imageY: Double,
    ): Pair<Double, Double> {
        require(imageX.isFinite() && imageY.isFinite())
        return (imageX + cropX) / imageScaleX + visualOffsetX to
            (imageY + cropY) / imageScaleY + visualOffsetY
    }
}

class ScreenshotRefusal(
    val code: String,
    message: String,
) : IllegalStateException(message)

/** Native-only gate: this reserved origin is loaded by synthetic instrumentation fixtures. */
internal fun requireSyntheticScreenshotFixture(
    qaEnabled: Boolean,
    nativeUrl: String?,
) {
    if (!qaEnabled || nativeUrl != "https://kai-fixture.invalid/") {
        throw ScreenshotRefusal("NEEDS_HUMAN", "Real-page image export requires a trusted privacy and frame provider")
    }
}

/** Budget the peak live allocations, not just the first capture. */
internal fun requireScreenshotAllocation(
    width: Int,
    height: Int,
    retainedBytes: Long,
    maxBytes: Long,
) {
    if (width !in 1..8192 || height !in 1..8192 || retainedBytes < 0 || maxBytes <= 0) {
        throw ScreenshotRefusal("SCREENSHOT_TOO_LARGE", "Invalid bitmap allocation bounds")
    }
    val bytes = width.toLong() * height.toLong() * 4L
    if (retainedBytes > maxBytes || bytes > maxBytes - retainedBytes) {
        throw ScreenshotRefusal("SCREENSHOT_TOO_LARGE", "Concurrent bitmaps exceed the capture memory budget")
    }
}
