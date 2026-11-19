package com.kelpie.browser.agent.execution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5PausedVisibilityGateTest {
    @Test
    fun samePausedGenerationCanCommitBackgroundVisibility() {
        assertTrue(R5PausedVisibilityGate.mayCommit(7L, 7L, activityResumed = false))
    }

    @Test
    fun latePausedCoroutineCannotOverwriteNewerResume() {
        assertFalse(R5PausedVisibilityGate.mayCommit(7L, 8L, activityResumed = true))
        assertFalse(R5PausedVisibilityGate.mayCommit(7L, 7L, activityResumed = true))
    }

    @Test
    fun olderPauseCannotOverwriteLaterPause() {
        assertFalse(R5PausedVisibilityGate.mayCommit(7L, 9L, activityResumed = false))
    }
}
