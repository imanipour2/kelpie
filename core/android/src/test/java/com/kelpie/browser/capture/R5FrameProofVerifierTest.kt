package com.kelpie.browser.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class R5FrameProofVerifierTest {
    private val token = R5FrameProofEpochSequence.Token(2L, 7, 0x124C92, 0xAFCB15)
    private val page = R5PageFrameProof.Samples(40, 56, 12, R5RgbaFrameBuffer.Region(32, 0, 64, 24))

    private fun source(
        host: Boolean,
        document: Boolean,
    ): R5RgbaFrameBuffer {
        val data = ByteBuffer.allocate(64 * 64 * 4)

        fun pixel(
            x: Int,
            y: Int,
            rgb: Int,
        ) {
            val offset = (y * 64 + x) * 4
            data.put(offset, (rgb shr 16).toByte())
            data.put(offset + 1, (rgb shr 8).toByte())
            data.put(offset + 2, rgb.toByte())
            data.put(offset + 3, -1)
        }
        pixel(12, 12, if (host) token.primaryRgb else 0x112233)
        pixel(12, 36, if (host) token.secondaryRgb else 0x445566)
        pixel(page.firstX, page.y, if (document) token.primaryRgb else 0x112233)
        pixel(page.secondX, page.y, if (document) token.secondaryRgb else 0x445566)
        return R5RgbaFrameBuffer(data, 64, 64, 256, 4)
    }

    @Test fun requiresBothProducerAndWebViewPaintToBeFresh() {
        assertTrue(R5FrameProofVerifier.matches(source(host = true, document = true), token, page))
        assertFalse(R5FrameProofVerifier.matches(source(host = true, document = false), token, page))
        assertFalse(R5FrameProofVerifier.matches(source(host = false, document = true), token, page))
        assertFalse(R5FrameProofVerifier.matches(source(host = false, document = false), token, page))
    }

    @Test fun previousRequestCannotSatisfyANewNonce() {
        val next = token.copy(sequence = 8, primaryRgb = 0x888888, secondaryRgb = 0x999999)
        assertFalse(R5FrameProofVerifier.matches(source(host = true, document = true), next, page))
    }

    @Test fun foregroundPixelCopyNeedsTheCurrentDocumentPair() {
        assertTrue(R5FrameProofVerifier.matchesPage(token, page, source(host = false, document = true)::argbAt))
        assertFalse(R5FrameProofVerifier.matchesPage(token, page, source(host = true, document = false)::argbAt))
        val next = token.copy(sequence = 8, primaryRgb = 0x888888, secondaryRgb = 0x999999)
        assertFalse(R5FrameProofVerifier.matchesPage(next, page, source(host = true, document = true)::argbAt))
    }

    @Test fun transparentNonceColoredSamplesDoNotProveAVisiblePaint() {
        val pixels = source(host = true, document = true)
        assertFalse(R5FrameProofVerifier.matchesPage(token, page) { x, y -> pixels.argbAt(x, y) and 0x00ffffff })
    }
}
