package com.kelpie.browser.agent

import com.kelpie.browser.agent.execution.R5ClickNavigationProof
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5ClickNavigationProofTest {
    @Test
    fun unchangedStateCannotProveClick() {
        val proof = R5ClickNavigationProof("https://fixture.example/", "epoch-1")
        repeat(10) { assertFalse(proof.observedStableChange("https://fixture.example/", "epoch-1")) }
    }

    @Test
    fun stableUrlChangeProvesNavigation() {
        val proof = R5ClickNavigationProof("https://fixture.example/", "epoch-1")
        assertFalse(proof.observedStableChange("https://fixture.example/next", "epoch-1"))
        assertTrue(proof.observedStableChange("https://fixture.example/next", "epoch-1"))
    }

    @Test
    fun stableSameUrlDocumentReloadCanProveNavigation() {
        val proof = R5ClickNavigationProof("https://fixture.example/", "epoch-1")
        assertFalse(proof.observedStableChange("https://fixture.example/", "epoch-2"))
        assertTrue(proof.observedStableChange("https://fixture.example/", "epoch-2"))
    }

    @Test
    fun transientUrlChangeDoesNotProveNavigation() {
        val proof = R5ClickNavigationProof("https://fixture.example/", "epoch-1")
        assertFalse(proof.observedStableChange("https://fixture.example/loading", "epoch-1"))
        assertFalse(proof.observedStableChange("https://fixture.example/", "epoch-1"))
        assertFalse(proof.observedStableChange("https://fixture.example/loading", "epoch-1"))
        assertTrue(proof.observedStableChange("https://fixture.example/loading", "epoch-1"))
    }

    @Test
    fun differentChangedStatesCannotBeCombined() {
        val proof = R5ClickNavigationProof("https://fixture.example/", "epoch-1")
        assertFalse(proof.observedStableChange("https://fixture.example/next", "epoch-1"))
        assertFalse(proof.observedStableChange("https://fixture.example/next", "epoch-2"))
        assertTrue(proof.observedStableChange("https://fixture.example/next", "epoch-2"))
    }

    @Test
    fun incompleteNativeObservationsAreNeverProof() {
        val proof = R5ClickNavigationProof("https://fixture.example/", "epoch-1")
        assertFalse(proof.observedStableChange("", "epoch-2"))
        assertFalse(proof.observedStableChange("https://fixture.example/next", ""))
        assertFalse(proof.observedStableChange("https://fixture.example/next", "epoch-2"))
        assertTrue(proof.observedStableChange("https://fixture.example/next", "epoch-2"))
    }
}
