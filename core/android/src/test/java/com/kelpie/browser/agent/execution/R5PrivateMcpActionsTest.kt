package com.kelpie.browser.agent.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R5PrivateMcpActionsTest {
    @Test
    fun ordinaryActionsUseExistingNativeOperations() {
        assertEquals("CLICK", R5PrivateMcpActions.operation("tab.click"))
        assertEquals("NAVIGATE", R5PrivateMcpActions.operation("tab.goto"))
        assertEquals("FILL", R5PrivateMcpActions.operation("tab.fill"))
        assertEquals("SELECT", R5PrivateMcpActions.operation("tab.select"))
        assertEquals("SCROLL", R5PrivateMcpActions.operation("page.scroll"))
        assertNull(R5PrivateMcpActions.operation("tab.evaluateJavascript"))
        assertEquals("OPEN_TAB", R5PrivateMcpActions.operation("tabs.new"))
        assertEquals("CLOSE_TAB", R5PrivateMcpActions.operation("tabs.close"))
        assertFalse(R5PrivateMcpActions.methods.contains("tabs.select"))
    }

    @Test
    fun invalidDataCannotReachTheNativeAdapter() {
        assertEquals(mapOf("selector" to "#next"), R5PrivateMcpActions.safeArguments("tab.click", mapOf("selector" to "#next")))
        assertNull(R5PrivateMcpActions.safeArguments("tab.click", mapOf("selector" to "")))
        assertNull(R5PrivateMcpActions.safeArguments("tab.select", mapOf("selector" to "#grade")))
        assertNull(R5PrivateMcpActions.safeArguments("tab.fill", mapOf("selector" to "#q", "text" to "x".repeat(4097))))
        assertNull(R5PrivateMcpActions.safeArguments("tab.goto", mapOf("url" to " ")))
        assertTrue(R5PrivateMcpActions.safeArguments("tab.wait", emptyMap<String, Any>())?.isEmpty() == true)
        assertEquals(mapOf("url" to "https://example.com/"), R5PrivateMcpActions.safeArguments("tabs.new", mapOf("url" to "https://example.com/")))
        assertEquals(
            mapOf("targetTabId" to "inactive-tab", "targetDocumentEpoch" to "e2"),
            R5PrivateMcpActions.safeArguments(
                "tabs.close",
                mapOf("targetTabId" to "inactive-tab", "targetDocumentEpoch" to "e2"),
            ),
        )
        assertNull(R5PrivateMcpActions.safeArguments("tabs.close", mapOf("targetTabId" to "inactive-tab")))
        assertNull(R5PrivateMcpActions.safeArguments("tabs.new", mapOf("url" to " ")))
        assertNull(R5PrivateMcpActions.safeArguments("tabs.close", mapOf("targetTabId" to "")))
    }

    @Test
    fun actionsCannotSkipNativeGrantBecauseOfTheirMcpToolName() {
        val envelope =
            mapOf<String, Any?>(
                "method" to "tools/call",
                "params" to
                    mapOf(
                        "name" to R5PrivateMcpContract.ACTION_TOOL,
                        "arguments" to mapOf("method" to "tab.click", "params" to emptyMap<String, Any>()),
                    ),
            )
        assertFalse(R5PrivateMcpContract.isGrantExempt(envelope))
        assertNull(R5PrivateMcpContract.expectedGeneration(envelope))
    }
}
