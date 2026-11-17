package com.kelpie.browser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalBrowserHandoffPolicyTest {
    @Test
    fun ordinaryHttpsPagesMayBeHandedOffOnlyByAnExplicitUserAction() {
        assertTrue(ExternalBrowserHandoffPolicy.mayOpen("https://accounts.google.com/o/oauth2/v2/auth?client_id=example"))
        assertTrue(ExternalBrowserHandoffPolicy.mayOpen("https://example.org/path?q=one#fragment"))
    }

    @Test
    fun unsafeSchemesMalformedUrisAndCredentialAuthoritiesNeverLeaveKelpie() {
        listOf(
            "http://accounts.google.com/",
            "javascript:alert(1)",
            "intent://accounts.google.com/#Intent;scheme=https;end",
            "file:///sdcard/secret",
            "content://provider/data",
            "https://user:pass@accounts.google.com/",
            "https://",
            "https://a.example/\nheader: injected",
            "",
        ).forEach { assertFalse(it, ExternalBrowserHandoffPolicy.mayOpen(it)) }
    }
}
