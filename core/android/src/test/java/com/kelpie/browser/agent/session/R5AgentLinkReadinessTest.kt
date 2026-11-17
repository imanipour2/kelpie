package com.kelpie.browser.agent.session

import org.junit.Assert.assertEquals
import org.junit.Test

class R5AgentLinkReadinessTest {
    @Test
    fun noAuthenticatedPresenceCannotBePresentedAsPairedAndReady() {
        assertEquals(
            R5AgentLinkStage.WAITING_FOR_AUTHENTICATED_AGENT,
            R5AgentLinkReadiness.stage(emptyList(), null),
        )
    }

    @Test
    fun authenticatedPresenceRequiresNativeOwnerTap() {
        assertEquals(
            R5AgentLinkStage.WAITING_FOR_NATIVE_OWNER,
            R5AgentLinkReadiness.stage(listOf("chatgpt"), null),
        )
    }

    @Test
    fun activeConsentIsNeverNamedRemoteConnectivityVerified() {
        assertEquals(
            R5AgentLinkStage.NATIVE_CONSENT_ACTIVE,
            R5AgentLinkReadiness.stage(emptyList(), "chatgpt"),
        )
    }

    @Test
    fun anotherVerifiedAgentCannotOverrideCurrentOwner() {
        assertEquals(
            R5AgentLinkStage.NATIVE_CONSENT_ACTIVE,
            R5AgentLinkReadiness.stage(listOf("claude"), "chatgpt"),
        )
    }
}
