package com.kelpie.browser.agent.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R5ObservedAgentClientsTest {
    @Test
    fun neverTreatAndroidLocalhostAsAnIdentifiedAgent() {
        assertFalse(R5AgentPrincipalPolicy.eligible("localhost"))
        assertFalse(R5AgentPrincipalPolicy.eligible(null))
        assertFalse(R5AgentPrincipalPolicy.eligible(" "))
        assertFalse(R5AgentPrincipalPolicy.eligible("bad\u0000id"))
        assertFalse(R5AgentPrincipalPolicy.eligible("a".repeat(SessionConsentCore.MAX_PRINCIPAL_LENGTH + 1)))
        assertTrue(R5AgentPrincipalPolicy.eligible("paired-client-1"))
    }

    @Test
    fun oneRecentlyVerifiedClientMayBeOfferedForExplicitLocalRequest() {
        var now = 10_000L
        val clients = RecentVerifiedAgentClients({ now })
        clients.noteVerified("localhost")
        assertTrue(clients.observed.value.isEmpty())
        assertNull(clients.soleRecentPrincipal())

        clients.noteVerified("paired-client-1")
        assertEquals("paired-client-1", clients.soleRecentPrincipal())
        assertEquals(listOf("paired-client-1"), clients.observed.value)

        now += 60_000L
        assertEquals("paired-client-1", clients.soleRecentPrincipal())
        now += 60_001L
        assertNull(clients.soleRecentPrincipal())
        assertTrue(clients.observed.value.isEmpty())
    }

    @Test
    fun multipleRecentClientsNeverSelectAnArbitraryOwner() {
        var now = 100L
        val clients = RecentVerifiedAgentClients({ now })
        clients.noteVerified("chatgpt-paired-id")
        now += 2L
        clients.noteVerified("other-paired-id")
        assertEquals(2, clients.observed.value.size)
        assertNull(clients.soleRecentPrincipal())

        clients.noteVerified("chatgpt-paired-id")
        assertNull(clients.soleRecentPrincipal())
        now += 120_001L
        assertNull(clients.soleRecentPrincipal())
        clients.noteVerified("chatgpt-paired-id")
        assertEquals("chatgpt-paired-id", clients.soleRecentPrincipal())
        clients.clear()
        assertNull(clients.soleRecentPrincipal())
    }

    @Test
    fun backwardsTimeCannotResurrectAnOldClient() {
        var now = 1_000L
        val clients = RecentVerifiedAgentClients({ now })
        clients.noteVerified("paired-client")
        now = 100L
        assertNull(clients.soleRecentPrincipal())
    }
}
