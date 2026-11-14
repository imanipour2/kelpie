package com.kelpie.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedScreenshotStoreTest {
    private var clock = 100L
    private val store = ApprovedScreenshotStore { clock }
    private val ticket = ApprovedScreenshotStore.Ticket("r1", "tab-a", "document-a", 120_000L)
    private val payload = mapOf<String, Any?>("image" to "cG5n", "format" to "png")

    @Test
    fun oneApprovalIsConsumedOnlyOnceForMatchingTabAndDocument() {
        assertTrue(store.arm(ticket))
        assertNotNull(store.pending("r1"))
        assertTrue(store.stage("r1", "tab-a", "document-a", payload))
        assertNull(store.take("r1", "tab-b", "document-a"))
        assertNull(store.take("r1", "tab-a", "document-b"))
        val captured = store.take("r1", "tab-a", "document-a")
        assertEquals("cG5n", captured?.get("image"))
        assertEquals("r1", captured?.get("recoveryRequestId"))
        assertNull(store.take("r1", "tab-a", "document-a"))
    }

    @Test
    fun newRequestCannotReuseOldApprovalOrOldCapture() {
        assertTrue(store.arm(ticket))
        assertTrue(store.stage("r1", "tab-a", "document-a", payload))
        val successor = ticket.copy(requestId = "r2")
        assertTrue(store.arm(successor))
        assertNull(store.take("r1", "tab-a", "document-a"))
        assertFalse(store.stage("r1", "tab-a", "document-a", payload))
        assertNotNull(store.pending("r2"))
    }

    @Test
    fun sameRequestForDifferentTabIsRejectedWithoutOverwritingOriginal() {
        assertTrue(store.arm(ticket))
        assertFalse(store.arm(ticket.copy(tabId = "tab-b")))
        assertEquals(ticket, store.pending("r1"))
    }

    @Test
    fun expiryAndEarlyCancellationDiscardImage() {
        assertTrue(store.arm(ticket))
        assertTrue(store.stage("r1", "tab-a", "document-a", payload))
        clock += ApprovedScreenshotStore.MAX_CACHE_AGE_MS
        assertNull(store.take("r1", "tab-a", "document-a"))
        assertTrue(store.arm(ticket))
        store.invalidate("r1")
        assertNull(store.pending("r1"))
    }

    @Test
    fun oversizedImageIsNotStaged() {
        assertTrue(store.arm(ticket))
        assertFalse(store.stage("r1", "tab-a", "document-a", mapOf("image" to "x".repeat(ApprovedScreenshotStore.MAX_IMAGE_BASE64_CHARS + 1))))
    }

    @Test
    fun sameRequestDoesNotArmAfterExpiry() {
        clock = 120_000L
        assertFalse(store.arm(ticket))
        assertNull(store.pending("r1"))
    }
}
