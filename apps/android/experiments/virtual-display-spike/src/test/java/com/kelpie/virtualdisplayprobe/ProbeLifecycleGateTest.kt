package com.kelpie.virtualdisplayprobe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeLifecycleGateTest {
    @Test
    fun repeatedLoadAndMigrationAreSingleFlight() {
        val gate = ProbeLifecycleGate()
        assertFalse(gate.claimMigration())
        assertTrue(gate.claimLoad())
        assertFalse(gate.claimLoad())
        assertTrue(gate.claimMigration())
        assertFalse(gate.claimMigration())
    }

    @Test
    fun countsExactlyTwentyFramesAndOneCompletion() {
        val gate = ProbeLifecycleGate()
        assertTrue(gate.claimLoad())
        assertTrue(gate.claimMigration())
        assertFalse(gate.claimStep(2))
        for (sequence in 1..20) {
            assertTrue(gate.claimStep(sequence))
            assertFalse(gate.claimStep(sequence))
        }
        assertTrue(gate.claimStep(21))
        assertFalse(gate.claimStep(21))
        assertFalse(gate.claimStep(22))
    }

    @Test
    fun blocksFrameBeforeMigration() {
        val gate = ProbeLifecycleGate()
        assertFalse(gate.claimStep(1))
        assertTrue(gate.claimLoad())
        assertFalse(gate.claimStep(1))
    }
}
