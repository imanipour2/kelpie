package com.kelpie.browser.capture

/** Pure source of per-session hardware overlay nonces; no browser-page injection. */
internal object R5FrameNonce {
    const val MAX_FRAMES = 512
    private const val COLOR_STEP = 0x010307

    fun color(
        seed: Int,
        sequence: Int,
    ): Int {
        require(seed in 0..0xFFFFFF)
        require(sequence in 1..MAX_FRAMES)
        return (seed + sequence * COLOR_STEP) and 0xFFFFFF
    }

    fun matches(
        expected: Int,
        actual: Int,
    ): Boolean = expected in 0..0xFFFFFF && (actual and 0xFFFFFF) == expected
}
