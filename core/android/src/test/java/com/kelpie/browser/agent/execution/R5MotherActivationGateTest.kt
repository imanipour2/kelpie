package com.kelpie.browser.agent.execution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5MotherActivationGateTest {
    @Test
    fun motherNeverStartsRendererFromConsentCollectorOutsidePauseEpoch() {
        assertFalse(R5MotherActivationGate.canEagerlyActivate(deferPrivateUntilBackground = true))
    }

    @Test
    fun nonMotherMayStartRendererFromGrantCollector() {
        assertTrue(R5MotherActivationGate.canEagerlyActivate(deferPrivateUntilBackground = false))
    }
}
