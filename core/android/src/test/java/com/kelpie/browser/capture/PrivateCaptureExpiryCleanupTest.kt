package com.kelpie.browser.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateCaptureExpiryCleanupTest {
    @Test fun correctClientExpiredGrantRequiresImmediateCleanup() {
        assertTrue(PrivateCaptureExpiryCleanup.shouldRelease("request-1", "client-1", "request-1", "client-1", "EXPIRED"))
        // A preceding read may already have purged the registry.
        assertTrue(PrivateCaptureExpiryCleanup.shouldRelease("request-1", "client-1", "request-1", "client-1", "NOT_FOUND"))
    }

    @Test fun unexpiredGrantAndUnrelatedClientNeverForceCleanup() {
        assertFalse(PrivateCaptureExpiryCleanup.shouldRelease("request-1", "client-1", "request-1", "client-1", "APPROVED"))
        assertFalse(PrivateCaptureExpiryCleanup.shouldRelease("request-1", "client-1", "request-2", "client-1", "EXPIRED"))
        assertFalse(PrivateCaptureExpiryCleanup.shouldRelease("request-1", "client-1", "request-1", "client-2", "NOT_FOUND"))
        assertFalse(PrivateCaptureExpiryCleanup.shouldRelease(null, null, "request-1", "client-1", "EXPIRED"))
    }
}
