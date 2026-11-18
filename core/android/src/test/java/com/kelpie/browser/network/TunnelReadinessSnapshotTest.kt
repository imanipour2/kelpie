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
        assertEquals("Diagnostics unavailable", tunnelReadyLabel(state.healthStatus, state.readyStatus))
    }

    @Test
    fun healthAloneIsNotReadiness() {
        val state = TunnelReadinessSnapshot(healthStatus = 200, readyStatus = 503)
        assertTrue(state.healthOk)
        assertFalse(state.readyOk)
        assertEquals("Running; not ready (HTTP 503)", tunnelReadyLabel(state.healthStatus, state.readyStatus))
    }

    @Test
    fun bothChecksOnlyProveLocalReadiness() {
        val state = TunnelReadinessSnapshot(healthStatus = 200, readyStatus = 200)
        assertTrue(state.readyOk)
        assertEquals("Ready locally", tunnelReadyLabel(state.healthStatus, state.readyStatus))
    }

    @Test
    fun healthSuccessAndMissingReadyIsNotReportedAsReady() {
        assertEquals("Running; readiness unknown", tunnelReadyLabel(200, null))
    }

    @Test
    fun mcpEvidenceIsNotInferredFromNullOrInvalidAge() {
        assertEquals("No authenticated request observed in this process", mcpActivityLabel(null))
        assertEquals("No authenticated request observed in this process", mcpActivityLabel(-1L))
    }

    @Test
    fun mcpEvidenceExpiresWithoutClaimingToolControl() {
        assertTrue(mcpActivityLabel(0L).contains("not an agent-control proof"))
        assertTrue(mcpActivityLabel(120_001L).contains("stale"))
    }

    @Test
    fun healthFailureOverridesReadinessStatus() {
        val state = TunnelReadinessSnapshot(healthStatus = 500, readyStatus = 200)
        assertFalse(state.readyOk)
        assertEquals("Diagnostics unhealthy (HTTP 500)", tunnelReadyLabel(state.healthStatus, state.readyStatus))
    }
}
