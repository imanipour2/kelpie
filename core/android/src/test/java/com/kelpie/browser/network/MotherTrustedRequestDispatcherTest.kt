package com.kelpie.browser.network

import com.kelpie.browser.agent.session.SessionConsentCore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests actual request admission; a plausible client ID is never computer trust. */
class MotherTrustedRequestDispatcherTest {
    private val key = "a".repeat(64)
    private val computer = AuthDecision.Authenticated(key, computerKey = key)
    private val consent = SessionConsentCore({ 1_000L })
    private val router = Router(consent::snapshot).apply { motherR5BackgroundQa = true }
    private var paused = false
    private var restores = 0
    private val dispatcher =
        AuthenticatedRequestDispatcher(router, prepareTrustedSession = {
            restores++
            consent.restoreTrustedComputer(it.computerKey!!, paused, deviceUnlocked = true)
        })

    private suspend fun request(
        principal: AuthDecision.Authenticated = computer,
        authenticate: suspend () -> AuthDecision = { principal },
    ) = dispatcher.dispatch(principal, "get-tabs", emptyMap(), "fixture", authenticate) { _, _ -> }

    @Test
    fun durableComputerRunsWithoutAnotherPromptAndKeepsItsExecutionEpoch() =
        runBlocking {
            var calls = 0
            router.register("get-tabs") { calls++; successResponse(mapOf("tabs" to emptyList<Any>())) }
            assertEquals(200, request().status)
            val first = consent.snapshot() as SessionConsentCore.State.Active
            repeat(4) { assertEquals(200, request().status) }
            assertEquals(first, consent.snapshot())
            assertEquals(5, calls)
            assertEquals(5, restores)
        }

    @Test
    fun legacyAccountAndMismatchedComputerProofNeverRestoreAuthority() =
        runBlocking {
            for (caller in listOf(AuthDecision.Authenticated(key), AuthDecision.Authenticated(key, "b".repeat(64)), AuthDecision.Authenticated("account-client"))) {
                assertEquals(403, request(caller).status)
            }
            assertEquals(0, restores)
            assertEquals(SessionConsentCore.State.Off, consent.snapshot())
        }

    @Test
    fun revokedBearerBeforeRestorationNeverCreatesExecutionState() =
        runBlocking {
            assertEquals(403, request(authenticate = { AuthDecision.Reject(401, "UNAUTHORIZED", "revoked") }).status)
            assertEquals(0, restores)
            assertEquals(SessionConsentCore.State.Off, consent.snapshot())
        }

    @Test
    fun revocationDuringRestorationCannotExecuteTheBrowserHandler() =
        runBlocking {
            var valid = true
            var calls = 0
            val checked =
                AuthenticatedRequestDispatcher(router, prepareTrustedSession = {
                    consent.restoreTrustedComputer(key, paused = false, deviceUnlocked = true)
                    valid = false
                })
            router.register("get-tabs") { calls++; successResponse() }
            val result = checked.dispatch(computer, "get-tabs", emptyMap(), "fixture", {
                if (valid) computer else AuthDecision.Reject(401, "UNAUTHORIZED", "revoked")
            }) { _, _ -> }
            assertEquals(403, result.status)
            assertEquals(0, calls)
        }

    @Test
    fun revokedRestorationCannotPinAnotherComputerOrRetireANewerEpoch() =
        runBlocking {
            // A revocation callback may run while Off, before an admitted restore.
            consent.revokePrincipal(key)
            val restored = consent.restoreTrustedComputer(key, paused = false, deviceUnlocked = true)!!
            assertTrue(consent.revokePrincipal(key, restored))
            val otherKey = "b".repeat(64)
            router.register("get-tabs") { successResponse() }
            assertEquals(200, request(AuthDecision.Authenticated(otherKey, otherKey)).status)
            val newer = consent.snapshot()
            assertFalse(consent.revokePrincipal(key, restored))
            assertEquals(newer, consent.snapshot())
            consent.stop()
            val renewed = consent.restoreTrustedComputer(key, paused = false, deviceUnlocked = true)
            assertFalse(consent.revokePrincipal(key, restored))
            assertEquals(renewed, consent.snapshot())
        }

    @Test
    fun savedOwnerStopCannotBeClearedByAnotherAuthenticatedRequest() =
        runBlocking {
            router.register("get-tabs") { successResponse() }
            assertEquals(200, request().status)
            paused = true
            consent.stop()
            repeat(3) { assertEquals(403, request().status) }
            assertEquals(SessionConsentCore.State.Off, consent.snapshot())
        }

    @Test
    fun legacySessionRequestForTrustedComputerReportsPauseWithoutAnotherPrompt() =
        runBlocking {
            var prompts = 0
            router.register("agent-session-request") {
                R5AgentControlPlaneRoutes.motherComputerRequestResponse(true, { it == key }, paused)
                    ?: run {
                        prompts++
                        consent.requestNativeConsent(verifiedClientId()!!)
                        successResponse(mapOf("status" to "PENDING_NATIVE_APPROVAL"))
                    }
            }
            suspend fun sessionRequest(principal: AuthDecision.Authenticated) =
                dispatcher.dispatch(principal, "agent-session-request", emptyMap(), "fixture", { principal }) { _, _ -> }
            paused = true
            val stopped = sessionRequest(computer)
            assertEquals("PAUSED_NATIVE_RESUME_REQUIRED", stopped.body["status"])
            assertEquals(false, stopped.body["nativeApprovalRequired"])
            assertEquals(SessionConsentCore.State.Off, consent.snapshot())
            paused = false
            assertEquals("COMPUTER_ALREADY_APPROVED", sessionRequest(computer).body["status"])
            assertEquals(0, prompts)
            consent.stop()
            paused = true
            assertEquals("PENDING_NATIVE_APPROVAL", sessionRequest(AuthDecision.Authenticated(key)).body["status"])
            assertEquals(1, prompts)
        }

    @Test
    fun rendererFailureIsUnavailableAndDoesNotEraseComputerApproval() =
        runBlocking {
            router.register("get-tabs") { throw R5RendererUnavailableException() }
            val result = request()
            assertEquals(503, result.status)
            assertEquals("RENDERER_NOT_READY", (result.body["error"] as Map<*, *>)["code"])
            assertTrue(consent.snapshot() is SessionConsentCore.State.Active)
            val approved = consent.snapshot()
            router.register("get-tabs") { successResponse() }
            assertEquals(200, request().status)
            assertEquals(approved, consent.snapshot())
        }

    @Test
    fun inFlightResultCannotFollowARendererRecoveryEpoch() =
        runBlocking {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            router.register("get-tabs") {
                entered.complete(Unit)
                release.await()
                successResponse(mapOf("privateFixture" to "must-not-escape"))
            }
            val running = async { request() }
            entered.await()
            val admitted = consent.snapshot() as SessionConsentCore.State.Active
            consent.invalidateRuntime(admitted)
            release.complete(Unit)
            val result = running.await()
            assertEquals(403, result.status)
            assertFalse(result.body.toString().contains("must-not-escape"))
            assertTrue(consent.snapshot() is SessionConsentCore.State.Active)
        }

    @Test
    fun anotherApprovedComputerReportsRuntimeContentionWithoutAnotherPrompt() =
        runBlocking {
            router.register("get-tabs") { successResponse() }
            assertEquals(200, request().status)
            val otherKey = "b".repeat(64)
            val result = request(AuthDecision.Authenticated(otherKey, otherKey))
            assertEquals(409, result.status)
            assertEquals("CONTROLLER_BUSY", (result.body["error"] as Map<*, *>)["code"])
            assertEquals(key, (consent.snapshot() as SessionConsentCore.State.Active).principalId)
        }
}
