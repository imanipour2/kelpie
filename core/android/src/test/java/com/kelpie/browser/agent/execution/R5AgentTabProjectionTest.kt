package com.kelpie.browser.agent.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R5AgentTabProjectionTest {
    private val first =
        R5AgentTabDescription(
            "tab-a",
            "epoch-a",
            "Example tab",
            "https://example.org/private/path?token=never-echoed#fragment-secret",
            true,
        )

    @Test
    fun onlyBoundedTabMetadataIsExposedWithoutQueryOrFragment() {
        val snapshot = R5AgentTabProjection.publicSnapshot(listOf(first))
        assertEquals(1, snapshot.size)
        val value = snapshot.single()
        assertEquals("tab-a", value["tabId"])
        assertEquals("epoch-a", value["documentEpoch"])
        assertEquals(true, value["active"])
        assertEquals("https://example.org", value["urlWithoutQuery"])
        assertEquals("https://example.org", value["title"])
        assertEquals(true, value["titleRedacted"])
        assertFalse(value.toString().contains("private/path"))
        assertFalse(value.toString().contains("never-echoed"))
        assertFalse(value.toString().contains("fragment-secret"))
    }

    @Test
    fun pathCredentialsAndAccountTitlesCannotEscapeWithoutDomPrivacyContext() {
        val unsafe = first.copy(title = "OTP 894231, account PERSONAL_NAME", url = "https://user:password@example.org/path-secret")
        val snapshot = R5AgentTabProjection.publicSnapshot(listOf(unsafe)).single()
        assertEquals("redacted", snapshot["urlWithoutQuery"])
        assertEquals("Kelpie tab", snapshot["title"])
        assertFalse(snapshot.toString().contains("PERSONAL_NAME"))
        assertFalse(snapshot.toString().contains("password"))
        assertFalse(snapshot.toString().contains("path-secret"))
    }

    @Test
    fun listingIsBoundedAndNeverChangesTabSelection() {
        val all = (0..70).map { first.copy(id = "tab-" + it, active = it == 0) }
        assertEquals(40, R5AgentTabProjection.publicSnapshot(all).size)
        assertEquals(true, all.first().active)
        assertTrue(R5AgentTabProjection.publicSnapshot(all).last()["tabId"] == "tab-39")
    }

    @Test
    fun identityChangesOnTabSwitchOrDocumentNavigation() {
        val old = R5AgentTabProjection.stableIdentity(listOf(first))
        assertNotEquals(old, R5AgentTabProjection.stableIdentity(listOf(first.copy(active = false))))
        assertNotEquals(old, R5AgentTabProjection.stableIdentity(listOf(first.copy(documentEpoch = "epoch-b"))))
        assertEquals(old, R5AgentTabProjection.stableIdentity(listOf(first.copy(title = "Changing title"))))
    }
}
