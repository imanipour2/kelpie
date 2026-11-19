package com.kelpie.browser.agent.execution

import android.app.KeyguardManager
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.agent.contract.ExecutionStatus
import com.kelpie.browser.agent.contract.Operation
import com.kelpie.browser.agent.contract.R5VisionCaptureRequest
import com.kelpie.browser.agent.render.WebViewOwnershipState
import com.kelpie.browser.agent.session.AgentSessionNotification
import com.kelpie.browser.agent.session.AndroidAgentProcessSession
import com.kelpie.browser.agent.session.SessionConsentCore
import com.kelpie.browser.capture.ForegroundWebViewCapture
import com.kelpie.browser.capture.PrivateWebViewDisplay
import com.kelpie.browser.handlers.HandlerContext
import com.kelpie.browser.network.Router
import com.kelpie.browser.network.errorResponse
import com.kelpie.browser.network.successResponse
import com.kelpie.browser.network.verifiedClientId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Lifecycle bridge from the REAL native process consent to one Kelpie-owned
 * WebView on a VirtualDisplay. The browser's Activity is not silently started
 * over ChatGPT; actual Android keyguard lock always revokes renderer ownership.
 *
 * Pure command admission and result transport are in a separate file.
 */
class R5AgentCoordinator(
    private val activity: ComponentActivity,
    private val ctx: HandlerContext,
    private val deferPrivateUntilBackground: Boolean = false,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transferMutex = Mutex()
    // Monotonic main-thread epoch invalidates old background handoffs on resume.
    private var lifecycleVisibilityEpoch = 0L
    private val released = MutableStateFlow(false)
    val relinquished = released.asStateFlow()
    private val renderer =
        PrivateWebViewDisplay(
            activity,
            composeRelease = { released.value = true },
            composeRestore = { released.value = false },
            useNativeFrameProof = true,
        )
    private val consent = AndroidAgentProcessSession.runtimeConsent()
    private val nativePort = R5NativeBrowserPort(activity, ctx, renderer)
    private var ownership = WebViewOwnershipState()

    @Volatile private var runtime = newRuntime()
    private var currentGeneration: String? = null
    private var currentTabId: String? = null

    @Volatile private var ready = false

    @Volatile private var closed = false

    private val tabCommands by lazy(LazyThreadSafetyMode.NONE) {
        R5AgentTabCommands(
            activity = activity,
            context = ctx,
            renderer = renderer,
            nativePort = nativePort,
            consent = consent,
            transferMutex = transferMutex,
            ownership = { ownership },
            isReady = { ready },
            setReady = { ready = it },
            onSelected = { currentTabId = it },
        )
    }

    // Focus grants nothing; it only schedules the existing same-generation checks.
    private val lifecycleSignals =
        R5LifecycleSignals(
            activity,
            onFocusRegained = {
                val active = consent.snapshot() as? SessionConsentCore.State.Active
                if (!closed && ownership.status() == WebViewOwnershipState.Host.SUSPENDED && active != null && !active.locked) {
                    scope.launch { rebindAfterRealOwnerUnlock(active) }
                }
            },
            onScreenOff = {
                if (!closed) {
                    ownership.lockDevice()
                    ready = false
                    renderer.cancelPendingCapture()
                    runtime.cancelInFlight()
                    scope.launch { releaseRenderer() }
                }
            },
        )

    private fun newRuntime(): AgentOperationRuntime = AgentOperationRuntime(consent, VerifiedNativeBrowserAdapter(consent, ownership, nativePort))

    init {
        ctx.motherPrivateTabSwitch = R5MotherTabSwitch(activity, consent, tabCommands, { ready }, { currentTabId }, { closed }, { runtime.cancelInFlight() })::select
        ctx.nativeRendererGone = { dead ->
            if (!closed) {
                ready = false
                ownership.failRenderer()
                nativePort.clearPendingEvidence()
                // Clear dead-renderer witnesses before cancellation can run capture finally.
                ForegroundWebViewCapture.onRendererGone(dead)
                renderer.onRendererGone(dead)
                runtime.cancelInFlight()
                AndroidAgentProcessSession.stop()
                AgentSessionNotification.hide(activity)
            }
        }
        scope.launch {
            AndroidAgentProcessSession.active.collect { state ->
                if (closed) return@collect
                if (state == null) {
                    ready = false
                    ownership.stop()
                    runtime.cancelInFlight()
                    nativePort.clearPendingEvidence()
                    scope.launch { releaseRenderer() }
                } else if (state.locked) {
                    ready = false
                    ownership.lockDevice()
                    runtime.cancelInFlight()
                    scope.launch { releaseRenderer() }
                } else if (state.generation != currentGeneration) {
                    currentGeneration = state.generation
                    ownership = WebViewOwnershipState()
                    runtime = newRuntime()
                    if (!deferPrivateUntilBackground || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        scope.launch { activateRenderer(state) }
                    }
                } else if (ownership.status() == WebViewOwnershipState.Host.SUSPENDED) {
                    // Unlock updates safetyEpoch but does NOT create a new generation.
                    // Activity may already be RESUMED, so onResume will not re-fire.
                    scope.launch { rebindAfterRealOwnerUnlock(state) }
                }
            }
        }
    }

    fun isReady(): Boolean = !closed && ready

    /** Native R5 compositor stays behind standard mother MCP; no private /mcp overlay. */
    fun attachMotherMcpScreenshot() {
        R5MotherMcpCaptureAdapter(
            activity = activity,
            ctx = ctx,
            renderer = renderer,
            authorized = {
                val principal = verifiedClientId()
                val active = consent.snapshot() as? SessionConsentCore.State.Active
                active != null &&
                    ready &&
                    canUse(principal, active.generation, active) &&
                    principal != "localhost"
            },
            privateOwned = { ready && ownership.status() == WebViewOwnershipState.Host.PRIVATE },
            sameTab = { id -> currentTabId == id },
        ).attach()
    }

    /** A single authenticated command ingress for HTTP and validated MCP envelopes. */
    fun register(router: Router) {
        router.register("agent-runtime-status") { body ->
            val client = verifiedClientId()
            val expected = body["consentGeneration"] as? String
            val active = consent.snapshot() as? SessionConsentCore.State.Active
            if (!canUse(client, expected, active)) {
                errorResponse("PERMISSION_REQUIRED", "No owner-authorized R5 agent session")
            } else {
                successResponse(
                    mapOf(
                        "status" to if (ready) "READY" else "SUSPENDED_OR_TRANSFER",
                        "owner" to ownership.status().name,
                        "privateRenderer" to renderer.state.value.name,
                        "supported" to
                            listOf("LIST_TABS", "OPEN_TAB", "CLOSE_TAB", "SWITCH_TAB", "NAVIGATE", "BACK", "FORWARD", "READ_DOM", "CLICK", "FILL", "TYPE", "SELECT", "WAIT", "SCROLL", "SCREENSHOT"),
                        "captureCapabilities" to R5VisionCaptureRequest.capabilities(),
                    ),
                )
            }
        }
        router.register("agent-capture-capabilities") { body ->
            val client = verifiedClientId()
            val active = consent.snapshot() as? SessionConsentCore.State.Active
            if (!canUse(client, body["consentGeneration"] as? String, active) || !ready) {
                errorResponse("PERMISSION_REQUIRED", "No active authorized R5 capture session")
            } else {
                successResponse(R5VisionCaptureRequest.capabilities())
            }
        }
        router.register("agent-tabs") { body ->
            val client = verifiedClientId()
            val active = consent.snapshot() as? SessionConsentCore.State.Active
            if (!canUse(client, body["consentGeneration"] as? String, active) || !ready) {
                return@register errorResponse("PERMISSION_REQUIRED", "R5 session/renderer unavailable")
            }
            withContext(Dispatchers.Main.immediate) {
                val store = ctx.tabStore ?: return@withContext errorResponse("NO_WEBVIEW", "No owned tab store")
                val tabs = R5AgentTabProjection.publicSnapshot(R5AgentTabProjection.fromStore(store))
                if (!canUse(client, body["consentGeneration"] as? String, consent.snapshot() as? SessionConsentCore.State.Active)) {
                    errorResponse("SESSION_REVOKED", "Consent ended during tab observation")
                } else {
                    successResponse(mapOf("tabs" to tabs))
                }
            }
        }
        router.register("agent-command") { body ->
            val client = verifiedClientId()
            val active = consent.snapshot() as? SessionConsentCore.State.Active
            val generation = body["consentGeneration"] as? String
            if (!canUse(client, generation, active) || !ready) {
                return@register errorResponse("PERMISSION_REQUIRED", "R5 session/renderer unavailable")
            }
            val operation =
                (body["operation"] as? String)?.let { runCatching { Operation.valueOf(it.uppercase()) }.getOrNull() }
                    ?: return@register errorResponse("INVALID_PARAMS", "Unknown agent operation")
            val capture =
                if (operation == Operation.SCREENSHOT) {
                    when (val parsed = R5VisionCaptureRequest.parse(body["capture"])) {
                        is R5VisionCaptureRequest.Parse.Valid -> parsed.request
                        is R5VisionCaptureRequest.Parse.Invalid ->
                            return@register errorResponse("INVALID_PARAMS", parsed.code)
                    }
                } else {
                    if (body.containsKey("capture")) {
                        return@register errorResponse("INVALID_PARAMS", "Capture parameters require SCREENSHOT")
                    }
                    R5VisionCaptureRequest.LEGACY
                }
            val tabId =
                body["tabId"] as? String
                    ?: return@register errorResponse("INVALID_PARAMS", "An observed tabId is required")
            val epoch =
                body["documentEpoch"] as? String
                    ?: return@register errorResponse("INVALID_PARAMS", "A fresh documentEpoch is required")
            val command =
                AgentCommand(
                    commandId = body["commandId"] as? String ?: UUID.randomUUID().toString(),
                    principalId = client!!,
                    consentGeneration = generation!!,
                    tabId = tabId,
                    observedDocumentEpoch = epoch,
                    operation = operation,
                    targetUrl = body["url"] as? String,
                    selector = (body["targetTabId"] as? String) ?: (body["selector"] as? String),
                    inputText = body["text"] as? String,
                    scrollDeltaX = (body["scrollX"] as? Number)?.toDouble() ?: 0.0,
                    scrollDeltaY = (body["scrollY"] as? Number)?.toDouble() ?: 0.0,
                    clientSequence = (body["clientSequence"] as? Number)?.toLong() ?: 0L,
                    captureRequest = capture,
                )
            // Capture a stable runtime reference: STOP may replace the active
            // generation while this suspended action is in flight.
            val selectedRuntime = runtime
            val receipt =
                if (operation in setOf(Operation.OPEN_TAB, Operation.CLOSE_TAB, Operation.SWITCH_TAB)) {
                    selectedRuntime.dispatchTabMutation(
                        command,
                        client,
                        observe = tabCommands::observe,
                        mutate = { tabCommands.perform(command) },
                        verify = { tabCommands.verify(command) },
                    )
                } else {
                    selectedRuntime.dispatch(command, client)
                }
            val stillOwner = canUse(client, generation, consent.snapshot() as? SessionConsentCore.State.Active) && ready
            val payload =
                if (receipt.status == ExecutionStatus.EXECUTED && stillOwner) {
                    nativePort.takePayload(receipt.evidenceId)
                } else {
                    withContext(Dispatchers.Main.immediate) { nativePort.clearPendingEvidence() }
                    null
                }
            // STOP or a replacement generation can occur while payload bytes
            // are being read from Main. Never expose those bytes afterwards.
            val deliverable =
                canUse(client, generation, consent.snapshot() as? SessionConsentCore.State.Active) &&
                    ready &&
                    selectedRuntime === runtime
            if (!deliverable) {
                return@register errorResponse("SESSION_REVOKED", "Agent result was invalidated before delivery")
            }
            successResponse(
                mapOf(
                    "commandId" to receipt.commandId,
                    "status" to receipt.status.name,
                    "code" to receipt.code?.name,
                    "admission" to receipt.admission?.name,
                    "mayHaveCompleted" to (
                        operation !in setOf(Operation.READ_DOM, Operation.SCREENSHOT) &&
                            receipt.status != ExecutionStatus.REJECTED
                    ),
                    "data" to payload,
                ),
            )
        }
    }

    private fun canUse(
        client: String?,
        expectedGeneration: String?,
        active: SessionConsentCore.State.Active?,
    ): Boolean = R5AgentAuthorization.permits(activity, client, expectedGeneration, active, closed)

    /** Called from Activity lifecycle, not from a remote JSON argument. */
    fun onPause() {
        val pauseEpoch = ++lifecycleVisibilityEpoch
        scope.launch {
            val stillBackground = {
                R5PausedVisibilityGate.mayCommit(
                    pauseEpoch = pauseEpoch,
                    currentEpoch = lifecycleVisibilityEpoch,
                    activityResumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                )
            }
            val active = consent.snapshot() as? SessionConsentCore.State.Active
            if (stillBackground() && active != null && !active.locked &&
                renderer.state.value == PrivateWebViewDisplay.State.IDLE
            ) {
                activateRenderer(active)
            }
            if (stillBackground()) {
                renderer.setActivityVisible(false)
            } else if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                renderer.state.value == PrivateWebViewDisplay.State.PRIVATE_DISPLAY
            ) {
                // onResume may have run while the asynchronous handoff was still
                // in progress. Never leave an old background transfer active
                // in the foreground, even though frame capture is already fenced.
                restoreForeground()
            }
        }
    }

    fun onResume() {
        ++lifecycleVisibilityEpoch
        renderer.setActivityVisible(true)
        scope.launch {
            when (renderer.state.value) {
                PrivateWebViewDisplay.State.PRIVATE_DISPLAY -> restoreForeground()
                PrivateWebViewDisplay.State.RESTORE_PENDING,
                PrivateWebViewDisplay.State.FAILED,
                -> {
                    transferMutex.withLock {
                        ready = false
                        val restored = renderer.verifyRestored()
                        val active = consent.snapshot() as? SessionConsentCore.State.Active
                        if (!restored) {
                            if (active != null) revokeOnRendererFailure("RESTORE_VERIFY_FAILED")
                            return@withLock
                        }
                        if (active != null && !active.locked) {
                            if (ownership.status() == WebViewOwnershipState.Host.SUSPENDED) {
                                ownership.unlockNeedsReattach(active.generation)
                            }
                            val tab = ctx.tabStore?.activeTab
                            val target = tab?.let { nativePort.nativeTargetForBinding(it.id) }
                            if (target != null && ownership.status() == WebViewOwnershipState.Host.ABSENT) {
                                ownership.bindForeground(active.generation, target.rendererIdentity)
                            }
                            ready = ownership.status() == WebViewOwnershipState.Host.FOREGROUND
                        }
                    }
                }
                PrivateWebViewDisplay.State.IDLE -> {
                    val active = consent.snapshot() as? SessionConsentCore.State.Active
                    if (active != null && !active.locked && ownership.status() == WebViewOwnershipState.Host.SUSPENDED) {
                        rebindAfterRealOwnerUnlock(active)
                    }
                }
                else -> Unit
            }
        }
    }

    /** Unlock is a native signal, not a renewed remote authorization. */
    private suspend fun rebindAfterRealOwnerUnlock(active: SessionConsentCore.State.Active) =
        transferMutex.withLock {
            if (closed || ready || consent.snapshot() != active) return@withLock
            val tab = ctx.tabStore?.activeTab ?: return@withLock
            val keyguard = activity.getSystemService(KeyguardManager::class.java)
            val power = activity.getSystemService(PowerManager::class.java)
            val eligible =
                R5UnlockedForegroundGate.canRebind(
                    consentActive = !active.locked && currentGeneration == active.generation,
                    sameGeneration = consent.snapshot() == active,
                    owner = ownership.status(),
                    privateDisplayIdle = renderer.state.value == PrivateWebViewDisplay.State.IDLE,
                    resumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                    windowVisible = activity.window.decorView.isShown,
                    windowFocused = activity.hasWindowFocus(),
                    attached = tab.webView.isAttachedToWindow,
                    shown = tab.webView.isShown,
                    interactive = power?.isInteractive == true,
                    keyguardGone = keyguard?.isDeviceLocked == false,
                    stopNotificationPresent = AgentSessionNotification.canNotify(activity),
                )
            if (!eligible) return@withLock
            val target = nativePort.nativeTargetForBinding(tab.id) ?: return@withLock
            if (!ownership.unlockNeedsReattach(active.generation)) return@withLock
            if (!ownership.bindForeground(active.generation, target.rendererIdentity)) {
                ownership.failRenderer()
                revokeOnRendererFailure("UNLOCK_REBIND_FAILED")
                return@withLock
            }
            val unchanged =
                consent.snapshot() == active &&
                    ctx.tabStore?.activeTab?.webView === tab.webView &&
                    ctx.tabStore?.activeTab?.id == tab.id &&
                    !closed &&
                    power?.isInteractive == true &&
                    keyguard?.isDeviceLocked == false &&
                    renderer.state.value == PrivateWebViewDisplay.State.IDLE
            if (!unchanged) {
                ready = false
                ownership.failRenderer()
                revokeOnRendererFailure("UNLOCK_GENERATION_CHANGED")
                return@withLock
            }
            ready = true
            Log.i("KelpieR5Unlock", "OWNER_UNLOCK_REBIND_VERIFIED")
        }

    private suspend fun activateRenderer(active: SessionConsentCore.State.Active) =
        transferMutex.withLock {
            if (closed || active.locked || consent.snapshot() != active) return@withLock
            if (renderer.state.value == PrivateWebViewDisplay.State.PRIVATE_DISPLAY) return@withLock
            val tab = ctx.tabStore?.activeTab ?: return@withLock
            val target = nativePort.nativeTargetForBinding(tab.id) ?: return@withLock
            if (ownership.status() == WebViewOwnershipState.Host.ABSENT &&
                !ownership.bindForeground(active.generation, target.rendererIdentity)
            ) {
                return@withLock
            }
            val ticket = ownership.moveToPrivate(active.generation) ?: return@withLock
            ready = false
            val entered = renderer.start(tab.webView)
            if (!entered || consent.snapshot() != active || !ownership.completePrivate(ticket)) {
                ownership.failRenderer()
                nativePort.clearPendingEvidence()
                renderer.stop()
                revokeOnRendererFailure("PRIVATE_RENDERER_TRANSFER_FAILED")
                return@withLock
            }
            currentTabId = tab.id
            ready = true
        }

    private suspend fun restoreForeground() =
        transferMutex.withLock {
            val active = consent.snapshot() as? SessionConsentCore.State.Active
            val ticket = active?.let { ownership.restoreForeground(it.generation) }
            ready = false
            renderer.stop()
            val restored = renderer.verifyRestored()
            if (ticket != null && restored && ownership.completeRestore(ticket)) {
                ready = true
            } else if (ticket != null) {
                ownership.failRenderer()
                revokeOnRendererFailure("FOREGROUND_RESTORE_FAILED")
            }
        }

    /**
     * An invalid renderer/ownership transition revokes the session fail-closed.
     * Never leave a misleading STOP notification for a now-OFF authority.
     * Reasons are fixed categories; no tab, principal, bearer or URL is logged.
     */
    private fun revokeOnRendererFailure(reason: String) {
        Log.w("KelpieR5Recovery", "consent revoked after " + reason)
        AndroidAgentProcessSession.stop()
        AgentSessionNotification.hide(activity)
    }

    private suspend fun releaseRenderer() =
        transferMutex.withLock {
            withContext(NonCancellable) {
                ready = false
                nativePort.clearPendingEvidence()
                renderer.stop()
                // A locked Activity may not complete physical reattachment.
                // Keep the grant denied; native onResume may finish restoration.
            }
        }

    fun close() {
        if (closed) return
        closed = true
        ctx.motherPrivateTabSwitch = null
        ctx.motherPrivateScreenshot = null
        ctx.motherPrivateAgentActive = null
        ctx.motherGatewayAuthorization = null
        ctx.nativeRendererGone = null
        ready = false
        runCatching { lifecycleSignals.close() }
        runtime.cancelInFlight()
        nativePort.clearPendingEvidence()
        ownership.stop()
        renderer.dispose()
        scope.cancel()
    }
}

/**
 * A suspended onPause coroutine must not overwrite a later onResume.
 * No remote operation or renderer ownership is granted by this predicate.
 */
internal object R5PausedVisibilityGate {
    fun mayCommit(
        pauseEpoch: Long,
        currentEpoch: Long,
        activityResumed: Boolean,
    ): Boolean = pauseEpoch == currentEpoch && !activityResumed
}
