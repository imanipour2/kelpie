package com.kelpie.browser.agent.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5StartupGateStateTest {
    @Test
    fun privateR5RequiresLocalChoiceBeforeCreatingAnyBrowserOrListener() {
        val gate = R5StartupGateState(required = true)
        assertFalse(gate.mayCreateBrowserSession())
        gate.allow()
        assertTrue(gate.mayCreateBrowserSession())
    }

    @Test
    fun declineCannotBeReversedByStaleAcceptCallback() {
        val gate = R5StartupGateState(required = true)
        gate.decline()
        assertFalse(gate.mayCreateBrowserSession())
        gate.allow()
        assertFalse(gate.mayCreateBrowserSession())
    }

    @Test
    fun normalEmbeddedAndNonQaHostsRemainAvailableWithoutGate() {
        val gate = R5StartupGateState(required = false)
        assertTrue(gate.mayCreateBrowserSession())
    }
}
