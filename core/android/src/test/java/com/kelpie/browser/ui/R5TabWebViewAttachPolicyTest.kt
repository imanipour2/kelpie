package com.kelpie.browser.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5TabWebViewAttachPolicyTest {
    @Test
    fun exactSameWebViewInSameHostMaySkipReattachment() {
        assertTrue(R5TabWebViewAttachPolicy.isCurrent(true, true))
    }

    @Test
    fun sameWebViewDetachedFromHostMustReattach() {
        assertFalse(R5TabWebViewAttachPolicy.isCurrent(true, false))
    }

    @Test
    fun differentWebViewMustReattachEvenIfHostPresent() {
        assertFalse(R5TabWebViewAttachPolicy.isCurrent(false, true))
    }
}
