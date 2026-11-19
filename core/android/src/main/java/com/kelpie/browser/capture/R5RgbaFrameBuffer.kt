package com.kelpie.browser.capture

import java.nio.ByteBuffer

/** Bounded RGBA copy that never assumes padding exists after the final row. */
internal class R5RgbaFrameBuffer(
    private val buffer: ByteBuffer,
    val width: Int,
    val height: Int,
    private val rowStride: Int,
    private val pixelStride: Int,
    private val left: Int = 0,
    private val top: Int = 0,
) {
    private val base = buffer.position()

    init {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS)
        require(left >= 0 && top >= 0 && pixelStride == 4)
        require(rowStride > 0 && rowStride.toLong() >= (left.toLong() + width) * pixelStride)
        require(buffer.remaining() <= MAX_BUFFER_BYTES)
        val end = base.toLong() + (top.toLong() + height - 1) * rowStride + (left.toLong() + width - 1) * pixelStride + 4
        require(end <= buffer.limit())
    }

    fun argbAt(
        x: Int,
        y: Int,
    ): Int {
        require(x in 0 until width && y in 0 until height)
        val offset = (base.toLong() + (top.toLong() + y) * rowStride + (left.toLong() + x) * pixelStride).toInt()

        fun channel(index: Int) = buffer.get(offset + index).toInt() and 255
        return (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
    }

    fun copyArgb(): IntArray = IntArray(width * height) { index -> argbAt(index % width, index / width) }

    /** Reject transparent/blank compositor output; proof markers are excluded. */
    fun hasPageContent(excluded: List<Region>): Boolean = R5FrameContentCheck.hasContent(width, height, { x, y -> excluded.any { it.contains(x, y) } }, ::argbAt)

    data class Region(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        fun contains(
            x: Int,
            y: Int,
        ): Boolean = x in left until right && y in top until bottom
    }

    companion object {
        const val MAX_PIXELS = 4 * 1024 * 1024
        private const val MAX_BUFFER_BYTES = 32 * 1024 * 1024
    }
}
