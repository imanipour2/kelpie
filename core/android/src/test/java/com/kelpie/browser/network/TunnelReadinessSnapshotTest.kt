package com.kelpie.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelReadinessSnapshotTest {
    @Test
    fun unreachableIsNeverReady() {
        val state = TunnelReadinessSnapshot(healthStatus = null, readyStatus = null)
        assertFalse(state.healthOk)
        assertFalse(state.readyOk)
        assertEquals("Not reachable", tunnelReadyLabel(state.healthOk, state.readyOk))
    }

    @Test
    fun healthAloneIsNotReadiness() {
        val state = TunnelReadinessSnapshot(healthStatus = 200, readyStatus = 503)
        assertTrue(state.healthOk)
        assertFalse(state.readyOk)
        assertEquals("Running; not ready", tunnelReadyLabel(state.healthOk, state.readyOk))
    }

    @Test
    fun bothChecksOnlyProveLocalReadiness() {
        val state = TunnelReadinessSnapshot(healthStatus = 200, readyStatus = 200)
        assertTrue(state.readyOk)
        assertEquals("Ready locally", tunnelReadyLabel(state.healthOk, state.readyOk))
    }

    @Test
    fun healthFailureOverridesReadinessStatus() {
        val state = TunnelReadinessSnapshot(healthStatus = 500, readyStatus = 200)
        assertFalse(state.readyOk)
        assertEquals("Not reachable", tunnelReadyLabel(state.healthOk, state.readyOk))
    }
}
