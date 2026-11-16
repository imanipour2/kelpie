package com.kelpie.browser.capture

import java.io.ByteArrayOutputStream

/**
 * A hard byte ceiling for the compressed screenshot payload.
 *
 * ImageReader's maxImages=2 only limits native image ownership. It does not
 * bound Bitmap.compress output, Base64 copies or HTTP JSON serialization.
 * Reject oversized output BEFORE ByteArrayOutputStream allocates more bytes.
 *
 * This is a QA safety ceiling, not a promise that Android has enough memory
 * for a full-resolution native framebuffer.
 */
internal class R5BoundedImageOutputStream(
    private val limitBytes: Int = MAX_COMPRESSED_BYTES,
) : ByteArrayOutputStream(INITIAL_CAPACITY) {
    init {
        require(limitBytes in 1..MAX_COMPRESSED_BYTES)
    }

    override fun write(b: Int) {
        reserve(1)
        super.write(b)
    }

    override fun write(
        b: ByteArray,
        off: Int,
        len: Int,
    ) {
        // Match OutputStream's range validation before doing any arithmetic.
        if (off < 0 || len < 0 || off > b.size || len > b.size - off) {
            throw IndexOutOfBoundsException()
        }
        reserve(len)
        super.write(b, off, len)
    }

    private fun reserve(length: Int) {
        if (length > limitBytes - count) {
            throw R5ImagePayloadTooLargeException()
        }
    }

    companion object {
        /** Explicit safeguard for debug PNG output. Tune only with device measurements. */
        const val MAX_COMPRESSED_BYTES = 8 * 1024 * 1024
        private const val INITIAL_CAPACITY = 16 * 1024
    }
}

/** No screenshot bytes, URLs, credentials or metadata in exception messages. */
internal class R5ImagePayloadTooLargeException : IllegalStateException("R5_IMAGE_PAYLOAD_TOO_LARGE")
