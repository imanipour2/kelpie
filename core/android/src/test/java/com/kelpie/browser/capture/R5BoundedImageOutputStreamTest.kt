package com.kelpie.browser.capture

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class R5BoundedImageOutputStreamTest {
    @Test
    fun acceptsExactLimitWithoutOverflow() {
        val buffer = R5BoundedImageOutputStream(4)
        buffer.write(byteArrayOf(1, 2, 3, 4), 0, 4)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), buffer.toByteArray())
    }

    @Test
    fun rejectsAdditionalBulkBytesWithoutMutatingOutput() {
        val buffer = R5BoundedImageOutputStream(3)
        buffer.write(byteArrayOf(1, 2), 0, 2)
        assertThrows(R5ImagePayloadTooLargeException::class.java) {
            buffer.write(byteArrayOf(3, 4), 0, 2)
        }
        assertArrayEquals(byteArrayOf(1, 2), buffer.toByteArray())
    }

    @Test
    fun rejectsSingleByteOverflowWithoutAllocating() {
        val buffer = R5BoundedImageOutputStream(1)
        buffer.write(1)
        assertThrows(R5ImagePayloadTooLargeException::class.java) { buffer.write(2) }
        assertEquals(1, buffer.size())
    }

    @Test
    fun invalidIndicesCannotChangeBuffer() {
        val buffer = R5BoundedImageOutputStream(8)
        assertThrows(IndexOutOfBoundsException::class.java) {
            buffer.write(byteArrayOf(1, 2), 1, Int.MAX_VALUE)
        }
        assertEquals(0, buffer.size())
    }

    @Test
    fun impossibleLimitCannotDisableGuard() {
        assertThrows(IllegalArgumentException::class.java) {
            R5BoundedImageOutputStream(Int.MAX_VALUE)
        }
    }
}
