package com.kelpie.browser.capture

import kotlin.math.abs

/** A fail-closed blank-frame check shared by ImageReader and PixelCopy. */
internal object R5FrameContentCheck {
    /** This proves sampled opaque variation, not privacy or page identity. */
    fun hasContent(
        width: Int,
        height: Int,
        isExcluded: (Int, Int) -> Boolean = { _, _ -> false },
        pixelAt: (Int, Int) -> Int,
    ): Boolean {
        if (width <= 0 || height <= 0 || width.toLong() * height > R5RgbaFrameBuffer.MAX_PIXELS) return false
        var first: Int? = null
        var varied = false
        for (row in 1..GRID) {
            for (column in 1..GRID) {
                val x = (width - 1) * column / (GRID + 1)
                val y = (height - 1) * row / (GRID + 1)
                if (isExcluded(x, y)) continue
                val pixel = pixelAt(x, y)
                if ((pixel ushr 24) < 250) return false
                val reference = first
                if (reference == null) {
                    first = pixel
                } else if (CHANNEL_SHIFTS.any { shift -> abs(((reference ushr shift) and 255) - ((pixel ushr shift) and 255)) > 5 }) {
                    varied = true
                }
            }
        }
        return varied
    }

    private const val GRID = 16
    private val CHANNEL_SHIFTS = intArrayOf(0, 8, 16)
}
