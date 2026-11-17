package com.kelpie.browser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundTabPolicyTest {
    @Test
    fun backgroundCreationNeedsAnExistingActiveTabAndRespectsLimit() {
        assertFalse(BackgroundTabPolicy.canCreate(emptyList(), null))
        assertFalse(BackgroundTabPolicy.canCreate(listOf("a"), "missing"))
        assertFalse(BackgroundTabPolicy.canCreate(listOf("a", "a"), "a"))
        assertTrue(BackgroundTabPolicy.canCreate(listOf("a", "b"), "a"))
        assertFalse(BackgroundTabPolicy.canCreate((1..40).map { "t$it" }, "t1"))
    }

    @Test
    fun cannotCloseActiveOrUnknownTabWhileAgentOwnsTheRenderer() {
        val ids = listOf("main", "second", "third")
        assertFalse(BackgroundTabPolicy.canClose(ids, "main", "main"))
        assertFalse(BackgroundTabPolicy.canClose(ids, "main", "unknown"))
        assertFalse(BackgroundTabPolicy.canClose(ids, null, "second"))
        assertTrue(BackgroundTabPolicy.canClose(ids, "main", "second"))
        assertTrue(BackgroundTabPolicy.canClose(ids, "main", "third"))
    }
}
