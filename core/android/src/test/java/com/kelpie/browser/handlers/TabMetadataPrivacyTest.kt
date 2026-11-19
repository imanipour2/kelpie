package com.kelpie.browser.handlers

import org.junit.Assert.assertEquals
import org.junit.Test

class TabMetadataPrivacyTest {
    @Test
    fun noCredentialsPathQueryFragmentOrDocumentTitleEscapeInTabInventory() {
        assertEquals("https://example.org", TabMetadataPrivacy.url("https://example.org/private-token?otp=123456#password"))
        assertEquals("redacted", TabMetadataPrivacy.url("https://account:password@example.org/"))
        assertEquals("redacted", TabMetadataPrivacy.url("data:text/plain,PRIVATE_CONTENT"))
        assertEquals("redacted", TabMetadataPrivacy.url("javascript:document.cookie"))
        assertEquals("redacted", TabMetadataPrivacy.url(null))
        assertEquals("Kelpie tab", TabMetadataPrivacy.title("https://account:password@example.org/"))
        assertEquals("https://example.org", TabMetadataPrivacy.title("https://example.org/path-secret"))
        assertEquals("about:blank", TabMetadataPrivacy.url("about:blank"))
    }
}
