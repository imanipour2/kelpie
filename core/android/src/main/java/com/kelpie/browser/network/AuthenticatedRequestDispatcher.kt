package com.kelpie.browser.network

import com.kelpie.browser.agent.session.R5AgentPrincipalPolicy
import com.kelpie.browser.agent.session.SessionConsentCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

/** HTTP admission and delivery share one principal and one native consent snapshot. */
internal class AuthenticatedRequestDispatcher(
    private val router: Router,
    private val consentSnapshot: () -> SessionConsentCore.State = router.nativeConsentSnapshot,
    private val noteMetadata: (String) -> Unit = {},
) {
    data class Outcome(
        val status: Int,
        val body: Map<String, Any?>,
        val ownerGrantRequired: Boolean,
        val admittedGrant: SessionConsentCore.State.Active?,
    )

    suspend fun dispatch(
        principal: AuthDecision.Authenticated,
        method: String,
        body: Map<String, Any?>,
        correlationId: String,
        authenticateAgain: suspend () -> AuthDecision,
        reportFailure: (String, Exception) -> Unit,
    ): Outcome {
        val required =
            router.motherR5BackgroundQa &&
                R5McpBootstrapGate.requiresNativeGrant(method, body, principal.clientId)
        val admitted = if (required) consentSnapshot() as? SessionConsentCore.State.Active else null

        fun denied(
            code: String,
            message: String,
        ) = Outcome(403, correlatedError(code, message, correlationId, "REJECTED"), false, null)

        if ((router.motherR5BackgroundQa || router.backgroundAgentQa) &&
            !R5AgentPrincipalPolicy.eligible(principal.clientId)
        ) {
            return denied("PERMISSION_REQUIRED", "Unique authenticated paired client required")
        }
        if (required && !R5RequestAuthorization.permits(principal.clientId, admitted, consentSnapshot())) {
            return denied("PERMISSION_REQUIRED", "Native owner approval required")
        }
        return try {
            // HTTP authentication preceded body parsing; revocation during that gap
            // must not create a pending prompt or execute any control-plane handler.
            if (authenticateAgain() != principal) {
                return denied("AUTHORIZATION_REVOKED", "Paired client authorization changed before admission")
            }
            withContext(
                AuthenticatedClientContext.fromVerifiedDecision(
                    principal,
                    admitted,
                    authorityCurrent = { authenticateAgain() == principal },
                ),
            ) {
                val (status, result) = router.handle(method, body)
                if (!verifiedRequestAuthorityCurrent()) {
                    denied("AUTHORIZATION_REVOKED", "Paired client authorization changed before delivery")
                } else if (required && !R5RequestAuthorization.permits(principal.clientId, admitted, consentSnapshot())) {
                    denied("SESSION_REVOKED", "Native owner approval changed before delivery")
                } else {
                    if (method == "mcp" &&
                        status == 200 &&
                        !result.containsKey("error") &&
                        !R5McpBootstrapGate.requiresNativeGrant(method, body, principal.clientId)
                    ) {
                        noteMetadata(principal.clientId)
                    }
                    val correlated = if (result.containsKey("error")) result + ("correlationId" to correlationId) else result
                    Outcome(status, correlated, required, admitted)
                }
            }
        } catch (_: R5AuthorizationChangedException) {
            denied("SESSION_REVOKED", "Native owner authorization changed before execution")
        } catch (error: TimeoutCancellationException) {
            Outcome(408, correlatedError("TIMEOUT", "Request did not complete before its deadline", correlationId), false, null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            reportFailure(correlationId, error)
            Outcome(500, correlatedError("INTERNAL_ERROR", "Request outcome is unknown; inspect native session status", correlationId), false, null)
        }
    }

    fun mayDeliver(
        principal: String,
        outcome: Outcome,
    ): Boolean =
        !outcome.ownerGrantRequired ||
            R5RequestAuthorization.permits(principal, outcome.admittedGrant, consentSnapshot())

    companion object {
        fun correlatedError(
            code: String,
            message: String,
            correlationId: String,
            outcome: String = "UNKNOWN",
        ): Map<String, Any?> =
            errorResponse(code, message, mapOf("correlationId" to correlationId)) +
                mapOf("outcome" to outcome, "correlationId" to correlationId)
    }
}
