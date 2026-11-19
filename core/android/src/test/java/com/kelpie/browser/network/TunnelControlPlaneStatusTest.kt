package com.kelpie.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The probe must distinguish remote polling from startup readiness without exposing raw diagnostics. */
class TunnelControlPlaneStatusTest {
    @Test
    fun degradedPollerRemainsDegradedDespiteIndependentReadyHttp200() {
        val report = """{"status":"degraded","reason_code":"network_error","details":{"consecutive_failures":79}}"""
        val result = parseControlPlaneStatus(report)
        assertEquals("degraded", result?.status)
        assertEquals("network_error", result?.reasonCode)
        assertEquals(79, result?.consecutiveFailures)
    }

    @Test
    fun unknownReasonIsNotDisplayedAsUntrustedText() {
        val report = """{"status":"degraded","reason_code":"https://private.example/sensitive?key=abc","details":{"consecutive_failures":999999}}"""
        val result = parseControlPlaneStatus(report)
        assertNull(result?.reasonCode)
        assertEquals(9999, result?.consecutiveFailures)
    }

    @Test
    fun malformedAndUnrecognizedStatusFailClosed() {
        assertNull(parseControlPlaneStatus("not-json"))
        assertNull(parseControlPlaneStatus("""{"status":"connected-to-remote-agent"}"""))
    }
}
