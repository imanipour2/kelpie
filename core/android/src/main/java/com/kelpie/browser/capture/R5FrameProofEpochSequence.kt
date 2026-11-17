package com.kelpie.browser.capture

import java.security.SecureRandom
import java.util.ArrayDeque

/**
 * Private-display frame freshness sequence. A new, unguessable two-pixel
 * 48-bit nonce domain starts after each 512-frame epoch, while the actual
 * browser WebView, native session grant and document remain unchanged.
 *
 * Not an authorization token: the normal owner/tab/document/STOP fences apply
 * to every capture independently. No frame contents or screenshot bytes are
 * retained by this state machine.
 */
internal class R5FrameProofEpochSequence(
    private val nextSeed: () -> Int = { SECURE_RANDOM.nextInt(COLOR_SPACE) },
) {
    data class Token(
        val epoch: Long,
        val sequence: Int,
        val primaryRgb: Int,
        val secondaryRgb: Int,
    )

    private var epoch = 0L
    private var sequence = 0
    private var primarySeed = 0
    private var secondarySeed = 0
    private val recentSeedPairs = ArrayDeque<Long>()
    private val recentSeedSet = HashSet<Long>()
    private var lastToken: Token? = null

    val currentEpoch: Long get() = epoch
    val trackedEpochCount: Int get() = recentSeedSet.size

    fun next(): Token {
        if (sequence == 0 || sequence >= R5FrameNonce.MAX_FRAMES) rotateEpoch()
        val nextSequence = sequence + 1
        val token =
            Token(
                epoch = epoch,
                sequence = nextSequence,
                primaryRgb = R5FrameNonce.color(primarySeed, nextSequence),
                secondaryRgb = R5FrameNonce.color(secondarySeed, nextSequence),
            )
        check(token.primaryRgb != token.secondaryRgb) { "Nonce marker collision" }
        check(
            lastToken?.let {
                it.primaryRgb != token.primaryRgb || it.secondaryRgb != token.secondaryRgb
            } != false,
        ) { "Repeated compositor frame proof" }
        sequence = nextSequence
        lastToken = token
        return token
    }

    private fun rotateEpoch() {
        check(epoch != Long.MAX_VALUE) { "Frame epoch counter exhausted" }
        repeat(MAX_EPOCH_SEED_ATTEMPTS) {
            val primary = nextSeed()
            val secondary = nextSeed()
            check(primary in 0 until COLOR_SPACE && secondary in 0 until COLOR_SPACE) {
                "Invalid compositor nonce seed"
            }
            if (primary == secondary) return@repeat
            val key = pairKey(primary, secondary)
            if (key in recentSeedSet) return@repeat
            // This pair cannot reproduce the immediately previous frame even
            // when sequence rolls back to one.
            val first = R5FrameNonce.color(primary, 1)
            val second = R5FrameNonce.color(secondary, 1)
            if (lastToken?.let { it.primaryRgb == first && it.secondaryRgb == second } == true) {
                return@repeat
            }
            primarySeed = primary
            secondarySeed = secondary
            epoch += 1
            sequence = 0
            recentSeedPairs.addLast(key)
            recentSeedSet.add(key)
            while (recentSeedPairs.size > MAX_TRACKED_EPOCHS) {
                recentSeedSet.remove(recentSeedPairs.removeFirst())
            }
            return
        }
        error("Compositor nonce seed unavailable")
    }

    private fun pairKey(
        primary: Int,
        secondary: Int,
    ): Long = (primary.toLong() shl 24) or secondary.toLong()

    companion object {
        private val SECURE_RANDOM = SecureRandom()
        private const val COLOR_SPACE = 0x1000000
        private const val MAX_TRACKED_EPOCHS = 64
        private const val MAX_EPOCH_SEED_ATTEMPTS = 128
    }
}
