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
    fun mutationAndExternalProtocolButtonsHaveConservativeGuards() {
        val script = R5WebViewActionScripts.click("#ordinary-button")
        assertTrue(script.contains("nonGetFormButton"))
        assertTrue(script.contains("formHasSecrets"))
        assertTrue(script.contains("unsafeLink"))
        assertTrue(script.contains("destructive.test(label)"))
        assertTrue(script.contains("DENIED_SENSITIVE"))
        assertTrue(script.contains("e.click();return 'DISPATCHED'"))
    }

    @Test
    fun snapshotProvidesBoundedUniqueCssFallbackWhenIdIsMissing() {
        val snapshot = R5WebViewActionScripts.SNAPSHOT
        assertTrue(snapshot.contains("uniqueSelector(e)"))
        assertTrue(snapshot.contains(":nth-of-type("))
        assertTrue(snapshot.contains("depth<24"))
        assertTrue(snapshot.contains("css.length>500"))
        assertTrue(snapshot.contains("document.querySelectorAll(css).length===1"))
        assertTrue(snapshot.contains("role:(e.getAttribute('role')"))
        assertFalse(snapshot.contains("selector:e.id ?"))
        assertFalse(snapshot.contains("e.value"))
    }

    @Test
    fun r5SnapshotReusesPrivacyContractAndNeverRawInnerText() {
        val snapshot = R5WebViewActionScripts.SNAPSHOT
        assertTrue(snapshot.contains("kelpieSafeNodeText(document.body)"))
        assertTrue(snapshot.contains("kelpieSafeName(e)"))
        assertTrue(snapshot.contains("kelpieSafeURL(location.href)"))
        assertTrue(snapshot.contains("kelpiePrivacyBudgetExceeded"))
        assertTrue(snapshot.contains("kelpieSafeText(document.title)"))
        assertTrue(snapshot.contains("coverage:'INCOMPLETE'"))
        assertTrue(snapshot.contains("redacted:true"))
        assertFalse(snapshot.contains("document.body?.innerText"))
        assertFalse(snapshot.contains("url:location.href"))
        assertFalse(snapshot.contains("title:document.title"))
        assertFalse(snapshot.contains("e.getAttribute('placeholder')||e.innerText"))
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
