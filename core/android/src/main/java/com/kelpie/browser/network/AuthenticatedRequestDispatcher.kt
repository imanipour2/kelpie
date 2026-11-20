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
    private val prepareTrustedSession: suspend (AuthDecision.Authenticated) -> Unit = {},
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

        fun denied(
            code: String,
            message: String,
        ) = Outcome(403, correlatedError(code, message, correlationId, "REJECTED"), false, null)

        if ((router.motherR5BackgroundQa || router.backgroundAgentQa) &&
            !R5AgentPrincipalPolicy.eligible(principal.clientId)
        ) {
            return denied("PERMISSION_REQUIRED", "Unique authenticated paired client required")
        }
        return try {
            // HTTP authentication preceded body parsing; revocation during that gap
            // must not create a pending prompt or execute any control-plane handler.
            if (authenticateAgain() != principal) {
                return denied("AUTHORIZATION_REVOKED", "Paired client authorization changed before admission")
            }
            // Only the native computer-key bearer path may reconstruct execution
            // state. Legacy client IDs and temporary account grants are not trust.
            if (router.motherR5BackgroundQa &&
                principal.computerKey != null &&
                principal.computerKey == principal.clientId
            ) {
                prepareTrustedSession(principal)
            }
            if (authenticateAgain() != principal) {
                return denied("AUTHORIZATION_REVOKED", "Paired client authorization changed during execution-session restoration")
            }
            val admitted = if (required) consentSnapshot() as? SessionConsentCore.State.Active else null
            if (required && !R5RequestAuthorization.permits(principal.clientId, admitted, consentSnapshot())) {
                if (principal.computerKey == principal.clientId && admitted != null && admitted.principalId != principal.clientId) {
                    return Outcome(409, correlatedError("CONTROLLER_BUSY", "Another trusted computer currently owns the browser runtime", correlationId, "REJECTED"), false, null)
                }
                return denied("PERMISSION_REQUIRED", "Browser control is paused or its execution session is unavailable")
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
        } catch (_: R5RendererUnavailableException) {
            Outcome(503, correlatedError("RENDERER_NOT_READY", "Computer remains approved; the browser renderer is temporarily unavailable", correlationId, "REJECTED"), false, null)
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
