package com.kelpie.virtualdisplayprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeFixtureTest {
    @Test
    fun fixtureIsSelfContainedAndHasNoNetworkUrls() {
        val html = ProbeFixture.html()
        assertTrue(html.contains("window.setProbe"))
        assertTrue(html.contains("viewport"))
        assertTrue(!html.contains("https://"))
        assertTrue(!html.contains("http://"))
        assertTrue(!html.contains("<iframe"))
    }

    @Test
    fun sequentialNoncesProduceDistinctSafeChannels() {
        val colors = (1..20).map(ProbeFixture::colorFor)
        assertEquals(20, colors.distinct().size)
        assertNotEquals(ProbeFixture.colorFor(1), ProbeFixture.colorFor(2))
        colors.forEach { (r, g, b) ->
            assertTrue(r in 48..207)
            assertTrue(g in 48..207)
            assertTrue(b in 48..207)
        }
    }
}
