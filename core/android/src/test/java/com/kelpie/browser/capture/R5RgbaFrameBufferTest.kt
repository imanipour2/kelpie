package com.kelpie.browser.capture

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class R5RgbaFrameBufferTest {
    @Test fun copiesUnpaddedRowsWithoutRecyclingOrAliasingTheSource() {
        val bytes = ByteBuffer.wrap(byteArrayOf(1, 2, 3, -1, 4, 5, 6, -1))
        val pixels = R5RgbaFrameBuffer(bytes, 2, 1, 8, 4).copyArgb()
        bytes.put(0, 99)
        assertArrayEquals(intArrayOf(0xFF010203.toInt(), 0xFF040506.toInt()), pixels)
    }

    @Test fun lastRowNeedsOnlyItsPixelsNotTrailingPadding() {
        val bytes = ByteBuffer.allocate(20)
        bytes.put(0, 1)
        bytes.put(3, -1)
        bytes.put(12, 2)
        bytes.put(15, -1)
        val pixels = R5RgbaFrameBuffer(bytes, 2, 2, 12, 4)
        assertEquals(0xFF010000.toInt(), pixels.argbAt(0, 0))
        assertEquals(0xFF020000.toInt(), pixels.argbAt(0, 1))
        assertEquals(4, pixels.copyArgb().size)
    }

    @Test fun honorsBufferPositionAndCropWithoutExportingPadding() {
        val bytes = ByteBuffer.allocate(44)
        bytes.position(4)
        bytes.put(24, 0x5A)
        bytes.put(27, -1)
        val cropped = R5RgbaFrameBuffer(bytes, 1, 1, 16, 4, left = 1, top = 1)
        assertArrayEquals(intArrayOf(0xFF5A0000.toInt()), cropped.copyArgb())
        assertEquals(4, bytes.position())
    }

    @Test fun rejectsTruncatedStrideOverflowAndOverBudgetFrames() {
        val cases =
            listOf<() -> Unit>(
                { R5RgbaFrameBuffer(ByteBuffer.allocate(19), 2, 2, 12, 4) },
                { R5RgbaFrameBuffer(ByteBuffer.allocate(32), 3, 2, 8, 4) },
                { R5RgbaFrameBuffer(ByteBuffer.allocate(32), 2, 2, 8, 3) },
                { R5RgbaFrameBuffer(ByteBuffer.allocate(32), Int.MAX_VALUE, 2, 8, 4) },
                { R5RgbaFrameBuffer(ByteBuffer.allocate(32), 2, 2, Int.MAX_VALUE, 4, top = Int.MAX_VALUE) },
            )
        cases.forEach { construct ->
            assertTrue(runCatching { construct() }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun freshColoredMarkersCannotTurnAGrayWebViewIntoAPass() {
        val bytes = ByteBuffer.allocate(64 * 64 * 4)
        repeat(64 * 64) { index ->
            bytes.put(index * 4, 0x77)
            bytes.put(index * 4 + 1, 0x77)
            bytes.put(index * 4 + 2, 0x77)
            bytes.put(index * 4 + 3, -1)
        }
        repeat(48) { y -> repeat(24) { x -> bytes.put((y * 64 + x) * 4, 0x31) } }
        val pixels = R5RgbaFrameBuffer(bytes, 64, 64, 256, 4)
        assertFalse(pixels.hasPageContent(listOf(R5RgbaFrameBuffer.Region(0, 0, 24, 48))))
        repeat(16) { y -> repeat(64) { x -> bytes.put(((y + 48) * 64 + x) * 4, 0x50) } }
        assertTrue(pixels.hasPageContent(listOf(R5RgbaFrameBuffer.Region(0, 0, 24, 48))))
    }
}
