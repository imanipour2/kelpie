package com.kelpie.browser.qa

import org.junit.Assert.assertTrue
import org.junit.Test

class P2OfflineFixtureTest {
    @Test
    fun fixtureIsRtlOfflineAndIncludesStateAndPixelHooks() {
        val fixture = P2OfflineFixture.html()
        assertTrue(fixture.contains("lang=\"fa\" dir=\"rtl\""))
        assertTrue(fixture.contains("window.kelpieSeed"))
        assertTrue(fixture.contains("window.kelpieProof"))
        assertTrue(fixture.contains("window.setProbe"))
        assertTrue(fixture.contains("history.pushState"))
        assertTrue(!fixture.contains("<script src="))
        assertTrue(!fixture.contains("<iframe"))
    }
}
