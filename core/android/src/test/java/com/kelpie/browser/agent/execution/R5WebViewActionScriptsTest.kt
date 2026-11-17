package com.kelpie.browser.agent.execution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-contract tests; device WebView instrumentation is still required. */
class R5WebViewActionScriptsTest {
    @Test
    fun ordinaryButtonsNoLongerHaveBlanketDenyRule() {
        val script = R5WebViewActionScripts.click("#ordinary-button")
        assertTrue(script.contains("e.click();return 'DISPATCHED'"))
        assertFalse(script.contains("e.matches('button,[type=submit]"))
        assertTrue(script.contains("DENIED_SENSITIVE"))
        assertTrue(script.contains("one-time-code"))
        assertTrue(script.contains("payment"))
    }

    @Test
    fun selectorCannotBreakOutOfQuotedJavaScriptData() {
        val malicious = """x');window.location='https://other.example';//"""
        val script = R5WebViewActionScripts.click(malicious)
        assertFalse(script.contains("querySelector('x');window.location"))
        assertTrue(script.contains("querySelector(\""))
        assertTrue(script.contains("DENIED_SENSITIVE"))
    }

    @Test
    fun observationExposesHumanChallengeHintButNoCaptchaToken() {
        val snapshot = R5WebViewActionScripts.SNAPSHOT
        assertTrue(snapshot.contains("humanChallengeDetected"))
        assertTrue(snapshot.contains("recaptcha"))
        assertTrue(snapshot.contains("hcaptcha"))
        assertTrue(snapshot.contains("turnstile"))
        assertFalse(snapshot.contains("g-recaptcha-response"))
        assertFalse(snapshot.contains("cf-turnstile-response"))
    }

    @Test
    fun selectAndWaitAreFixedScriptsWithQuotedArguments() {
        val choose = R5WebViewActionScripts.select("#grade", "PRO 25")
        val verify = R5WebViewActionScripts.verifySelect("#grade", "PRO 25")
        assertTrue(choose.contains("HTMLSelectElement"))
        assertTrue(verify.contains("selectedOptions"))
        assertTrue(choose.contains("PRO 25"))
        assertTrue(R5WebViewActionScripts.waitFor("#ready").contains("querySelector"))
        assertTrue(R5WebViewActionScripts.waitFor(null).contains("readyState"))
    }
}
