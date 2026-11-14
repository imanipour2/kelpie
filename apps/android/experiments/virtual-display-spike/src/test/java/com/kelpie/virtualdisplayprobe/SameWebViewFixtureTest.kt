package com.kelpie.virtualdisplayprobe

import org.junit.Assert.assertTrue
import org.junit.Test

class SameWebViewFixtureTest {
    @Test
    fun fixtureIsOfflineAndHasStateAndPixelProofHooks() {
        val html = SameWebViewFixture.html()
        assertTrue(html.contains("lang=\"fa\" dir=\"rtl\""))
        assertTrue(html.contains("window.kelpieSeed"))
        assertTrue(html.contains("window.kelpieProof"))
        assertTrue(html.contains("history.pushState"))
        assertTrue(html.contains("window.setProbe"))
        assertTrue(html.contains("preserved-input"))
        assertTrue(html.contains("color-box"))
        assertTrue(!html.contains("<script src="))
        assertTrue(!html.contains("<iframe"))
    }
}
