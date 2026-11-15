package com.kelpie.browser.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateCaptureOperationPolicyTest {
    @Test fun ordinaryBrowserUnchangedWhenP4Inactive() {
        listOf("screenshot", "click", "type", "get-dom", "navigate", "browser-control", "mcp").forEach {
            assertTrue(PrivateCaptureOperationPolicy.permits(it, false))
        }
    }

    @Test fun noLegacyOperationCanBypassPrivateOwner() {
        listOf(
            "screenshot",
            "click",
            "tap",
            "type",
            "fill",
            "get-dom",
            "evaluate",
            "reload",
            "new-tab",
            "connect-snapshot",
            "tab.screenshot",
            "tab.click",
            "ai-infer",
        ).forEach {
            assertFalse(PrivateCaptureOperationPolicy.permits(it, true))
        }
    }

    @Test fun onlyCaptureOperationsCanEnterPrivateOwner() {
        listOf("capture-session-frame", "capture-session-status", "capture-session-stop").forEach {
            assertTrue(PrivateCaptureOperationPolicy.permits(it, true))
        }
        assertFalse(PrivateCaptureOperationPolicy.permits("capture-session-request", true))
        assertFalse(PrivateCaptureOperationPolicy.permits("capture-session-debug", true))
    }

    @Test fun envelopesNeedInnerOperationAdmission() {
        assertTrue(PrivateCaptureOperationPolicy.permits("mcp", true, outerEnvelope = true))
        assertTrue(PrivateCaptureOperationPolicy.permits("browser-control", true, outerEnvelope = true))
        assertFalse(PrivateCaptureOperationPolicy.permits("mcp", true))
        assertFalse(PrivateCaptureOperationPolicy.permits("tab.screenshot", true, outerEnvelope = true))
    }

    @Test fun staleEnvelopeIsDiscardedAfterLocalConsent() {
        assertFalse(PrivateCaptureOperationPolicy.permitsDelivery("mcp", false, true))
        assertFalse(PrivateCaptureOperationPolicy.permitsDelivery("browser-control", false, true))
        assertFalse(PrivateCaptureOperationPolicy.permitsDelivery("screenshot", false, true))
    }

    @Test fun alreadyPrivateEnvelopeRequiresInnerAdmission() {
        assertTrue(PrivateCaptureOperationPolicy.permitsDelivery("mcp", true, true))
        assertTrue(PrivateCaptureOperationPolicy.permitsDelivery("browser-control", true, true))
        assertFalse(PrivateCaptureOperationPolicy.permitsDelivery("screenshot", true, true))
        assertTrue(PrivateCaptureOperationPolicy.permitsDelivery("capture-session-frame", true, true))
    }

    @Test fun disabledP4DoesNotChangeNormalBrowserTransport() {
        assertTrue(PrivateCaptureOperationPolicy.permitsDelivery("mcp", false, false))
        assertTrue(PrivateCaptureOperationPolicy.permitsDelivery("screenshot", false, false))
    }
}
