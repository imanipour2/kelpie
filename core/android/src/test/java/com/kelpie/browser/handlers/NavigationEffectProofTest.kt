package com.kelpie.browser.handlers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationEffectProofTest {
    private val before = NavigationEffectProof.Observation("native:old", "https://fixture.invalid/two", 1, 3, false, true, 1000.0)

    @Test fun oldCompleteDocumentNeverProvesReload() {
        val proof = NavigationEffectProof(NavigationEffectProof.Action.RELOAD, before)
        assertNull(proof.refusalBeforeDispatch())
        repeat(20) { assertFalse(proof.observed(before, it * 100L)) }
        val merelyStarted = before.copy(documentIdentity = "native:new")
        assertFalse(proof.observed(merelyStarted, 2000L))
        assertFalse(proof.observed(merelyStarted, 2100L))
        val replaced = before.copy(documentIdentity = "native:new", engineTimeOrigin = 2000.0)
        assertFalse(proof.observed(replaced, 2100L))
        assertFalse(proof.observed(replaced, 2199L))
        assertTrue(proof.observed(replaced, 2200L))
    }

    @Test fun absentBackAndForwardDestinationsAreRefusedBeforeDispatch() {
        assertEquals("NO_BACK_HISTORY", NavigationEffectProof(NavigationEffectProof.Action.BACK, before.copy(historyIndex = 0)).refusalBeforeDispatch())
        assertEquals("NO_FORWARD_HISTORY", NavigationEffectProof(NavigationEffectProof.Action.FORWARD, before.copy(historyIndex = 2)).refusalBeforeDispatch())
        assertEquals("NO_FORWARD_HISTORY", NavigationEffectProof(NavigationEffectProof.Action.FORWARD, before.copy(historyIndex = -1, historySize = 0)).refusalBeforeDispatch())
    }

    @Test fun historyRequiresTheExactNativeDestinationIndexEvenWhenUrlIsUnchanged() {
        val proof = NavigationEffectProof(NavigationEffectProof.Action.BACK, before)
        val wrong = before.copy(historyIndex = 2, documentIdentity = "native:other")
        assertFalse(proof.observed(wrong, 100L))
        assertFalse(proof.observed(wrong, 300L))
        val actual = before.copy(historyIndex = 0)
        assertFalse(proof.observed(actual, 400L))
        assertTrue(proof.observed(actual, 500L))
    }

    @Test fun unchangedHistoryIsNeverSuccessfulBackOrForward() {
        for (action in listOf(NavigationEffectProof.Action.BACK, NavigationEffectProof.Action.FORWARD)) {
            val proof = NavigationEffectProof(action, before)
            repeat(10) { assertFalse(proof.observed(before, it * 100L)) }
        }
    }

    @Test fun loadingAndAnotherDocumentResetTheStabilityEvidence() {
        val proof = NavigationEffectProof(NavigationEffectProof.Action.RELOAD, before)
        val changed = before.copy(documentIdentity = "native:new", engineTimeOrigin = 2000.0)
        assertFalse(proof.observed(changed, 100L))
        assertFalse(proof.observed(changed.copy(loading = true), 200L))
        assertFalse(proof.observed(changed, 300L))
        val later = changed.copy(documentIdentity = "native:later", engineTimeOrigin = 3000.0)
        assertFalse(proof.observed(later, 400L))
        assertTrue(proof.observed(later, 500L))
    }

    @Test fun sameUrlNavigateRequiresDocumentReplacementButAllowsStableRedirect() {
        val proof = NavigationEffectProof(NavigationEffectProof.Action.NAVIGATE, before, before.url)
        assertFalse(proof.observed(before, 100L))
        assertFalse(proof.observed(before, 300L))
        val redirected = before.copy(documentIdentity = "native:new", url = "https://fixture.invalid/redirect", engineTimeOrigin = 2000.0)
        assertFalse(proof.observed(redirected, 400L))
        assertTrue(proof.observed(redirected, 500L))
    }

    @Test fun fragmentOnlyNavigateMustMatchTheExactRequestedUrl() {
        val target = before.url + "#target"
        val proof = NavigationEffectProof(NavigationEffectProof.Action.NAVIGATE, before, target)
        assertFalse(proof.observed(before.copy(url = before.url + "#other"), 100L))
        assertFalse(proof.observed(before.copy(url = target), 200L))
        assertTrue(proof.observed(before.copy(url = target), 300L))
    }

    @Test fun incompleteInconsistentOrBackwardsClockSamplesCannotComplete() {
        val proof = NavigationEffectProof(NavigationEffectProof.Action.RELOAD, before)
        val changed = before.copy(documentIdentity = "native:new", engineTimeOrigin = 2000.0)
        assertFalse(proof.observed(changed.copy(url = ""), 100L))
        assertFalse(proof.observed(changed.copy(complete = false), 200L))
        assertFalse(proof.observed(changed.copy(documentIdentity = ""), 300L))
        assertFalse(proof.observed(changed, 500L))
        assertFalse(proof.observed(changed, 400L))
        assertFalse(proof.observed(changed, -1L))
    }

    @Test fun malformedNativeHistoryCannotAttestCompletion() {
        assertEquals("NO_HISTORY_EVIDENCE", NavigationEffectProof(NavigationEffectProof.Action.RELOAD, before.copy(historyIndex = 8)).refusalBeforeDispatch())
        val proof = NavigationEffectProof(NavigationEffectProof.Action.NAVIGATE, before, before.url)
        val invalid = before.copy(documentIdentity = "native:new", engineTimeOrigin = 2000.0, historySize = 0)
        assertFalse(proof.observed(invalid, 100L))
        assertFalse(proof.observed(invalid, 300L))
    }
}
