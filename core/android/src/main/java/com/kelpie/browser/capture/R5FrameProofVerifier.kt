package com.kelpie.browser.capture

/** Both native host pixels and actual document pixels must carry this request's nonce. */
internal object R5FrameProofVerifier {
    fun matches(
        pixels: R5RgbaFrameBuffer,
        token: R5FrameProofEpochSequence.Token,
        page: R5PageFrameProof.Samples,
    ): Boolean =
        matchesOpaque(token.primaryRgb, pixels.argbAt(R5NativeFrameProof.SAMPLE_PX, R5NativeFrameProof.SAMPLE_PX)) &&
            matchesOpaque(token.secondaryRgb, pixels.argbAt(R5NativeFrameProof.SAMPLE_PX, R5NativeFrameProof.SECOND_SAMPLE_PX)) &&
            matchesPage(token, page, pixels::argbAt)

    /** Foreground PixelCopy has no private host overlay; its document must still prove paint. */
    fun matchesPage(
        token: R5FrameProofEpochSequence.Token,
        page: R5PageFrameProof.Samples,
        pixelAt: (Int, Int) -> Int,
    ): Boolean =
        matchesOpaque(token.primaryRgb, pixelAt(page.firstX, page.y)) &&
            matchesOpaque(token.secondaryRgb, pixelAt(page.secondX, page.y))

    private fun matchesOpaque(
        expected: Int,
        actual: Int,
    ): Boolean = (actual ushr 24) == 255 && R5FrameNonce.matches(expected, actual)
}
