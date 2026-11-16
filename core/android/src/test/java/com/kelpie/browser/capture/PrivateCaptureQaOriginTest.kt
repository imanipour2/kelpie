package com.kelpie.browser.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateCaptureQaOriginTest {
    @Test
    fun acceptsOnlyPublicDefaultHttpsOrigin() {
        assertTrue(PrivateCaptureQaOrigin.permitted("https://example.com", "https://example.com/"))
        assertTrue(PrivateCaptureQaOrigin.permitted("https://example.com:443/qa", "https://example.com/"))
        assertTrue(PrivateCaptureQaOrigin.permitted("https://example.com/a?test=1", "https://example.com/a"))
    }

    @Test
    fun rejectsOtherSchemesHostsPortsAndUserInfo() {
        val legitimate = "https://example.com/"
        val forbidden =
            listOf(
                "http://example.com/",
                "https://example.com:8443/",
                "https://example.com.evil.test/",
                "https://user:secret@example.com/",
                "https://example.com@evil.test/",
                "https://www.example.com/",
                "https://127.0.0.1/",
                "not a valid URL",
            )
        forbidden.forEach { url ->
            assertFalse(url, PrivateCaptureQaOrigin.permitted(url, legitimate))
            assertFalse(url, PrivateCaptureQaOrigin.permitted(legitimate, url))
        }
        assertFalse(PrivateCaptureQaOrigin.permitted(legitimate, null))
        assertFalse(PrivateCaptureQaOrigin.permitted("", legitimate))
    }

    @Test
    fun redirectInActualWebViewFailsEvenIfTabStillShowsPublicUrl() {
        assertFalse(PrivateCaptureQaOrigin.permitted("https://example.com/", "https://private.test/account"))
    }
}
