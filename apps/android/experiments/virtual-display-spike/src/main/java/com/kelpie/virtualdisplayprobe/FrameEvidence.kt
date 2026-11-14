package com.kelpie.virtualdisplayprobe

/** Pure logic: a new Android ImageReader callback and matching pixels are BOTH required. */
internal data class PixelSample(
    val red: Int,
    val green: Int,
    val blue: Int,
    val receivedAtNs: Long,
)

internal object FrameEvidence {
    fun matches(
        sequence: Int,
        mutatedAtNs: Long,
        frame: PixelSample?,
    ): Boolean {
        if (frame == null || frame.receivedAtNs <= mutatedAtNs) return false
        val (red, green, blue) = ProbeFixture.colorFor(sequence)
        return frame.red == red && frame.green == green && frame.blue == blue
    }
}
