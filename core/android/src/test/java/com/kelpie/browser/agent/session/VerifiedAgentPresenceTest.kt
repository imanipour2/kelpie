package com.kelpie.browser.agent.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedAgentPresenceTest {
    @Test
    fun onlyAuthenticatedAndStillPairedPrincipalCanEnterStartupGate() {
        var now = 1_000L
        val paired = mutableSetOf("chatgpt")
        val presence = VerifiedAgentPresence({ now }, { it in paired }, ttlMs = 120_000L)
        assertTrue(presence.principals.value.isEmpty())
        assertFalse(presence.isFreshAndPaired("chatgpt"))
        presence.observeAuthenticated("foreign")
        assertTrue(presence.principals.value.isEmpty())
        presence.observeAuthenticated("chatgpt")
        assertEquals(listOf("chatgpt"), presence.principals.value)
        assertTrue(presence.isFreshAndPaired("chatgpt"))
        now += 120_000L
        assertFalse(presence.isFreshAndPaired("chatgpt"))
        assertTrue(presence.principals.value.isEmpty())
    }

    @Test
    fun revokingPairingRevokesStartupCandidateWithoutNewRemoteCommand() {
        val paired = mutableSetOf("chatgpt")
        val presence = VerifiedAgentPresence({ 500L }, { it in paired })
        presence.observeAuthenticated("chatgpt")
        paired.clear()
        assertFalse(presence.isFreshAndPaired("chatgpt"))
        assertTrue(presence.principals.value.isEmpty())
    }

    @Test
    fun separateVerifiedPrincipalsAreVisibleForExplicitSelection() {
        val paired = mutableSetOf("chatgpt", "another-agent")
        val presence = VerifiedAgentPresence({ 10L }, { it in paired })
        presence.observeAuthenticated("chatgpt")
        presence.observeAuthenticated("another-agent")
        assertEquals(listOf("chatgpt", "another-agent"), presence.principals.value)
        presence.observeAuthenticated("chatgpt")
        assertEquals(listOf("another-agent", "chatgpt"), presence.principals.value)
    }

    @Test
    fun malformedAndTimeReversedCredentialsNeverBecomeCandidates() {
        var now = 100L
        val presence = VerifiedAgentPresence({ now }, { true })
        presence.observeAuthenticated(" ")
        presence.observeAuthenticated("bad\nprincipal")
        presence.observeAuthenticated("x".repeat(SessionConsentCore.MAX_PRINCIPAL_LENGTH + 1))
        assertTrue(presence.principals.value.isEmpty())
        presence.observeAuthenticated("chatgpt")
        now = 0L
        assertFalse(presence.isFreshAndPaired("chatgpt"))
    }
}
