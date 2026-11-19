package com.kelpie.browser.agent.execution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Metadata cannot claim success unless transfer, native proof and session all agree. */
class R5MotherPrivateSelectionProofTest {
    @Test
    fun transferredAndVerifiedSelectedTabPasses() {
        assertTrue(R5MotherPrivateSelectionProof.accepts(true, true, true, "tab-b", "tab-b"))
    }

    @Test
    fun metadataOnlyTabSelectionFails() {
        assertFalse(R5MotherPrivateSelectionProof.accepts(false, false, true, "tab-b", "tab-b"))
        assertFalse(R5MotherPrivateSelectionProof.accepts(true, false, true, "tab-b", "tab-b"))
    }

    @Test
    fun revokedOwnerOrWrongRendererTabFails() {
        assertFalse(R5MotherPrivateSelectionProof.accepts(true, true, false, "tab-b", "tab-b"))
        assertFalse(R5MotherPrivateSelectionProof.accepts(true, true, true, "tab-a", "tab-b"))
        assertFalse(R5MotherPrivateSelectionProof.accepts(true, true, true, null, "tab-b"))
    }
}
