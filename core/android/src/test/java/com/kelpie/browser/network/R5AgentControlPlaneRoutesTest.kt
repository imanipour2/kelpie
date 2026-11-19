package com.kelpie.browser.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The consent control plane must never require an already-active WebView grant. */
class R5AgentControlPlaneRoutesTest {
    @Test
    fun onlyExactConsentOperationsSkipTheLegacyWebViewLease() {
        for (method in listOf("agent-session-request", "agent-session-status", "agent-session-stop")) {
            assertTrue(R5AgentControlPlaneRoutes.isConsentMethod(method))
        }
        for (
        method in listOf(
            "agent-command",
            "agent-tabs",
            "agent-runtime-status",
            "agent-capture-capabilities",
            "browser-control",
            "mcp",
            "agent-session-approve",
            "agent-session-request-extra",
            "Agent-Session-Request",
        )
        ) {
            assertFalse(R5AgentControlPlaneRoutes.isConsentMethod(method))
        }
    }

    @Test
    fun onlyAuthenticatedNonLocalhostClientsMayReceiveControlResponsesWithoutOwnerGrant() {
        for (method in listOf("agent-session-request", "agent-session-status", "agent-session-stop")) {
            assertTrue(R5AgentControlPlaneRoutes.mayRespondWithoutOwnerGrant(method, "paired-agent"))
            for (principal in listOf<String?>(null, "", "localhost")) {
                assertFalse(R5AgentControlPlaneRoutes.mayRespondWithoutOwnerGrant(method, principal))
            }
        }
    }

    @Test
    fun revokedPrivateWebViewCannotBlockConsentRequestOrStopForAuthenticatedAgent() {
        for (method in listOf("agent-session-request", "agent-session-status", "agent-session-stop")) {
            assertFalse(
                R5McpBootstrapGate.requiresNativeGrantAtDelivery(
                    method,
                    emptyMap(),
                    "paired-agent",
                    privateAtIngress = true,
                    privateAtEgress = true,
                ),
            )
            assertTrue(
                R5McpBootstrapGate.requiresNativeGrantAtDelivery(
                    method,
                    emptyMap(),
                    "localhost",
                    privateAtIngress = true,
                    privateAtEgress = true,
                ),
            )
        }
    }

    @Test
    fun browserActionsAreNotUpgradedToConsentControl() {
        for (method in listOf("agent-command", "agent-tabs", "agent-runtime-status", "tools/call")) {
            assertTrue(
                R5McpBootstrapGate.requiresNativeGrantAtDelivery(
                    method,
                    emptyMap(),
                    "paired-agent",
                    privateAtIngress = true,
                    privateAtEgress = false,
                ),
            )
        }
    }
}
