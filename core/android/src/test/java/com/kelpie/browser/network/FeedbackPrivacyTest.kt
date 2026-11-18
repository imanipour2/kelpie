package com.kelpie.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackPrivacyTest {
    @Test
    fun privateFieldsAndNestedPayloadsAreNeverRetained() {
        val secret = "synthetic-private-value"
        val result =
            FeedbackStore.safeMetadata(
                mapOf(
                    "category" to "interaction",
                    "command" to "fill",
                    "params" to mapOf("value" to secret),
                    "diagnostics" to mapOf("password" to secret),
                    "screenshotBase64" to secret,
                    "url" to "https://example.test/?token=$secret",
                    "context" to secret,
                    "deviceName" to secret,
                ),
            )
        assertFalse(result.toString().contains(secret))
        assertEquals(setOf("payloadRedacted", "category", "command"), result.keys)
        assertEquals("fill", result["command"])
    }

    @Test
    fun freeTextDisguisedAsMetadataAndInvalidCountersAreDropped() {
        val result =
            FeedbackStore.safeMetadata(
                mapOf(
                    "category" to "token=private",
                    "command" to "fill password=private",
                    "error" to "SECRET_PRIVATE",
                    "status" to "PRIVATE",
                    "count" to Double.NaN,
                    "seenCount" to Double.POSITIVE_INFINITY,
                    "observedCount" to -1,
                    "durationMs" to 4242424242424242L,
                    "width" to "private",
                    "success" to "private",
                ),
            )
        assertEquals("other", result["category"])
        assertEquals("unknown", result["command"])
        assertEquals(3, result.size)
    }

    @Test
    fun typedSafeCountersAndKnownStatesRemainUseful() {
        val result =
            FeedbackStore.safeMetadata(
                mapOf("category" to "screenshot", "command" to "screenshot", "status" to "BLOCKED", "error" to "NO_FRESH_FRAME", "count" to 25, "success" to false),
            )
        assertEquals("NO_FRESH_FRAME", result["error"])
        assertEquals(25L, result["count"])
        assertEquals(false, result["success"])
        assertTrue(result["payloadRedacted"] == true)
    }
}
