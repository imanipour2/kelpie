package com.kelpie.browser.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R5FrameProofEpochSequenceTest {
    private fun deterministicSequence(): R5FrameProofEpochSequence {
        var counter = 0
        return R5FrameProofEpochSequence {
            counter += 173
            counter and 0xFFFFFF
        }
    }

    @Test
    fun firstFrameStartsInFirstEpoch() {
        val token = deterministicSequence().next()
        assertEquals(1L, token.epoch)
        assertEquals(1, token.sequence)
        assertNotEquals(token.primaryRgb, token.secondaryRgb)
    }

    @Test
    fun exactlyFiveHundredTwelvePerEpoch() {
        val counter = deterministicSequence()
        val tokens = (1..512).map { counter.next() }
        assertTrue(tokens.all { it.epoch == 1L })
        assertEquals(512, tokens.last().sequence)
        val afterLimit = counter.next()
        assertEquals(2L, afterLimit.epoch)
        assertEquals(1, afterLimit.sequence)
        assertNotEquals(
            tokens.last().primaryRgb to tokens.last().secondaryRgb,
            afterLimit.primaryRgb to afterLimit.secondaryRgb,
        )
    }

    @Test
    fun twoThousandFramesNeverRepeatARecentProofPair() {
        val counter = deterministicSequence()
        val seen = mutableSetOf<Long>()
        repeat(2000) {
            val token = counter.next()
            val key = (token.primaryRgb.toLong() shl 24) or token.secondaryRgb.toLong()
            assertTrue("Repeated compositor frame token", seen.add(key))
            assertEquals(1L + it / 512, token.epoch)
            assertEquals(1 + it % 512, token.sequence)
        }
        assertEquals(2000, seen.size)
    }

    @Test
    fun recentSeedMemoryIsBounded() {
        val counter = deterministicSequence()
        repeat(90 * 512) { counter.next() }
        assertEquals(90L, counter.currentEpoch)
        assertTrue(counter.trackedEpochCount <= 64)
    }

    @Test
    fun repeatedSeedPairsAreNeverAcceptedAsNewEpochs() {
        val seeds = listOf(11, 22, 11, 22, 77, 88)
        var index = 0
        val counter = R5FrameProofEpochSequence { seeds[index++] }
        repeat(512) { counter.next() }
        val rotated = counter.next()
        assertEquals(2L, rotated.epoch)
        assertEquals(1, rotated.sequence)
    }

    @Test
    fun brokenRandomSourceFailsClosed() {
        val counter = R5FrameProofEpochSequence { 42 }
        assertThrows(IllegalStateException::class.java) { counter.next() }
    }

    @Test
    fun outOfRangeRandomSourceFailsClosed() {
        val counter = R5FrameProofEpochSequence { -1 }
        assertThrows(IllegalStateException::class.java) { counter.next() }
    }
}
