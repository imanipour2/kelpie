package com.kelpie.browser.network

import com.kelpie.browser.agent.session.SessionConsentCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedRequestDispatcherTest {
    private val consent = SessionConsentCore({ 1_000L })
    private val router = Router(consent::snapshot).apply { motherR5BackgroundQa = true }
    private val dispatcher = AuthenticatedRequestDispatcher(router)
    private val principal = AuthDecision.Authenticated("verified-agent")

    private fun approve() {
        val pending = consent.requestNativeConsent(principal.clientId)!!
        consent.approveFromNativeUi(pending.promptId, deviceUnlocked = true)!!
    }

    private suspend fun request(
        method: String,
        body: Map<String, Any?> = emptyMap(),
        authenticateAgain: suspend () -> AuthDecision = { principal },
    ): AuthenticatedRequestDispatcher.Outcome = dispatcher.dispatch(principal, method, body, "fixture-request-1", authenticateAgain) { _, _ -> }

    @Test
    fun metadataRemainsAvailableAfterStopAndLockButBrowserActionsDoNotRun() =
        runBlocking {
            var browserCalls = 0
            router.register("mcp") { body ->
                if (body["method"] == "tools/call") browserCalls++
                successResponse(mapOf("metadata" to true))
            }
            repeat(3) { phase ->
                if (phase == 1) {
                    approve()
                    consent.setDeviceLocked(true)
                }
                if (phase == 2) consent.stop()
                for (method in listOf("initialize", "notifications/initialized", "ping", "tools/list")) {
                    val result = request("mcp", mapOf("jsonrpc" to "2.0", "method" to method))
                    assertEquals(200, result.status)
                    assertFalse(result.ownerGrantRequired)
                }
                assertEquals(403, request("mcp", mapOf("method" to "tools/call")).status)
            }
            assertEquals(0, browserCalls)
        }

    @Test
    fun metadataObservationRequiresSuccessfulPostDispatchAuthentication() =
        runBlocking {
            val observed = mutableListOf<String>()
            val checked = AuthenticatedRequestDispatcher(router, noteMetadata = { observed.add(it) })
            var authority: AuthDecision = principal
            router.register("mcp") { successResponse() }

            suspend fun ping() = checked.dispatch(principal, "mcp", mapOf("method" to "ping"), "fixture", { authority }) { _, _ -> }
            assertEquals(200, ping().status)
            assertEquals(listOf(principal.clientId), observed)
            authority = AuthDecision.Reject(401, "UNAUTHORIZED", "Revoked before dispatch")
            assertEquals(403, ping().status)
            authority = principal
            router.register("mcp") {
                authority = AuthDecision.Reject(401, "UNAUTHORIZED", "Revoked during metadata")
                successResponse()
            }
            assertEquals(403, ping().status)
            assertEquals(listOf(principal.clientId), observed)
        }

    @Test
    fun consentRequestCanBecomePendingWithoutAnyPreviousOwnerGrant() =
        runBlocking {
            router.register("agent-session-request") {
                val pending = consent.requestNativeConsent(verifiedClientId())!!
                successResponse(mapOf("status" to "PENDING_NATIVE_APPROVAL", "promptId" to pending.promptId))
            }
            val result = request("agent-session-request")
            assertEquals(200, result.status)
            assertEquals("PENDING_NATIVE_APPROVAL", result.body["status"])
            assertTrue(consent.snapshot() is SessionConsentCore.State.Pending)
            assertEquals(403, request("get-tabs").status)
        }

    @Test
    fun principalRevokedBeforeConsentAdmissionNeverCreatesPending() =
        runBlocking {
            var handlerCalls = 0
            router.register("agent-session-request") {
                handlerCalls++
                consent.requestNativeConsent(verifiedClientId())
                successResponse()
            }
            val result =
                request("agent-session-request", authenticateAgain = {
                    AuthDecision.Reject(401, "UNAUTHORIZED", "Revoked while reading the HTTP body")
                })
            assertEquals(403, result.status)
            assertEquals("AUTHORIZATION_REVOKED", (result.body["error"] as Map<*, *>)["code"])
            assertEquals(0, handlerCalls)
            assertTrue(consent.snapshot() is SessionConsentCore.State.Off)
        }

    @Test
    fun revocationWhileConsentHandlerAwaitsMainThreadCannotCreatePending() =
        runBlocking {
            val queued = CompletableDeferred<Unit>()
            val resumeMain = CompletableDeferred<Unit>()
            var authority: AuthDecision = principal
            var promptCreations = 0
            var mainThreadGateRejected = false
            router.register("agent-session-request") {
                queued.complete(Unit)
                resumeMain.await()
                val rejection = R5AgentControlPlaneRoutes.rejectionIfRevoked()
                if (rejection != null) {
                    mainThreadGateRejected = true
                    rejection
                } else {
                    promptCreations++
                    consent.requestNativeConsent(verifiedClientId())
                    successResponse()
                }
            }
            val pending = async { request("agent-session-request", authenticateAgain = { authority }) }
            queued.await()
            authority = AuthDecision.Reject(401, "UNAUTHORIZED", "Revoked while queued for Main")
            resumeMain.complete(Unit)
            assertEquals(403, pending.await().status)
            assertTrue(mainThreadGateRejected)
            assertEquals(0, promptCreations)
            assertTrue(consent.snapshot() is SessionConsentCore.State.Off)
        }

    @Test
    fun inFlightStopReapprovalAndLockRoundTripNeverDeliverOldBrowserContent() =
        runBlocking {
            for (transition in listOf("STOP", "REAPPROVE", "LOCK_UNLOCK")) {
                consent.stop()
                approve()
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                router.register("get-tabs") {
                    started.complete(Unit)
                    release.await()
                    successResponse(mapOf("fixtureContent" to "must-not-be-delivered"))
                }
                val pending = async { request("get-tabs") }
                started.await()
                when (transition) {
                    "STOP" -> consent.stop()
                    "REAPPROVE" -> {
                        consent.stop()
                        approve()
                    }
                    "LOCK_UNLOCK" -> {
                        consent.setDeviceLocked(true)
                        consent.setDeviceLocked(false)
                    }
                }
                release.complete(Unit)
                val result = pending.await()
                assertEquals(403, result.status)
                assertFalse(result.body.toString().contains("must-not-be-delivered"))
            }
        }

    @Test
    fun lastMileDeliveryRechecksOriginalGrantAfterDispatchHasCompleted() =
        runBlocking {
            approve()
            router.register("get-tabs") { successResponse(mapOf("tabs" to emptyList<Any>())) }
            val outcome = request("get-tabs")
            assertEquals(200, outcome.status)
            assertTrue(dispatcher.mayDeliver(principal.clientId, outcome))
            consent.stop()
            approve()
            assertFalse(dispatcher.mayDeliver(principal.clientId, outcome))
        }

    @Test
    fun principalRevocationDuringDispatchSuppressesResult() =
        runBlocking {
            approve()
            var authority: AuthDecision = principal
            router.register("get-tabs") {
                authority = AuthDecision.Reject(401, "UNAUTHORIZED", "Revoked")
                successResponse(mapOf("fixtureContent" to "must-not-be-delivered"))
            }
            val result = request("get-tabs", authenticateAgain = { authority })
            assertEquals(403, result.status)
            assertEquals("AUTHORIZATION_REVOKED", (result.body["error"] as Map<*, *>)["code"])
            assertFalse(result.body.toString().contains("must-not-be-delivered"))
        }

    @Test
    fun failureAfterPromptCreationReportsUnknownWithoutInventingNoPendingState() =
        runBlocking {
            router.register("agent-session-request") {
                consent.requestNativeConsent(verifiedClientId())
                error("secret-otp-and-private-url-never-returned")
            }
            var loggedId: String? = null
            val result =
                dispatcher.dispatch(principal, "agent-session-request", emptyMap(), "fixture-correlation", { principal }) { id, _ ->
                    loggedId = id
                }
            assertEquals(500, result.status)
            assertEquals("UNKNOWN", result.body["outcome"])
            assertEquals("fixture-correlation", loggedId)
            assertEquals("fixture-correlation", result.body["correlationId"])
            assertFalse(result.body.containsKey("pending"))
            assertFalse(result.body.toString().contains("secret-otp"))
            assertTrue(consent.snapshot() is SessionConsentCore.State.Pending)
        }

    @Test
    fun cancellationPropagatesWithoutFalseSuccessOrErrorRecovery() =
        runBlocking {
            router.register("mcp") { throw CancellationException("cancel fixture") }
            var cancelled = false
            try {
                request("mcp", mapOf("method" to "ping"))
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        }

    @Test
    fun localhostMalformedAndConfusableMethodsNeverBecomeControlPlane() =
        runBlocking {
            var invoked = false
            router.register("mcp") {
                invoked = true
                successResponse()
            }
            for (id in listOf("localhost", "", "bad\nprincipal", "x".repeat(129))) {
                val caller = AuthDecision.Authenticated(id)
                val result = dispatcher.dispatch(caller, "mcp", mapOf("method" to "initialize"), "fixture", { caller }) { _, _ -> }
                assertEquals(403, result.status)
            }
            for (method in listOf<Any?>(null, 1, "Initialize", "initialize-extra", "tools/call", "resources/read")) {
                assertEquals(403, request("mcp", mapOf("method" to method)).status)
            }
            assertFalse(invoked)
        }
}
