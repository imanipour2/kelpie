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

    private val lifecycle =
        R5LifecycleReconciler(transferMutex) {
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
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
            context = ctx,
            renderer = renderer,
            nativePort = nativePort,
            consent = consent,
            transferMutex = transferMutex,
            ownership = { ownership },
            isReady = { ready },
            setReady = { ready = it },
            onSelected = { currentTabId = it },
            onRendererFailure = { suspendOnRendererFailure("PRIVATE_TAB_TRANSFER_FAILED", it) },
        )
    }

    // Focus grants nothing; it only schedules the existing same-generation checks.
    private val lifecycleSignals =
        R5LifecycleSignals(
            activity,
            onFocusRegained = {
                val active = consent.snapshot() as? SessionConsentCore.State.Active
                if (!closed && !ready && active != null && !active.locked) {
                    scheduleForegroundReconciliation()
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
        ctx.motherPrivateTabSwitch =
            R5MotherTabSwitch(
                activity,
                consent,
                tabCommands,
                { ready },
                { currentTabId },
                { closed },
                { runtime.cancelInFlight() },
                { suspendOnRendererFailure("PRIVATE_TAB_VERIFY_FAILED", it) },
            )::select
        ctx.nativeRendererGone = { dead ->
            if (!closed) {
                ready = false
                ownership.failRenderer()
                nativePort.clearPendingEvidence()
                // Clear dead-renderer witnesses before cancellation can run capture finally.
                ForegroundWebViewCapture.onRendererGone(dead)
                renderer.onRendererGone(dead)
                runtime.cancelInFlight()
                suspendOnRendererFailure("RENDERER_GONE", consent.snapshot() as? SessionConsentCore.State.Active)
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
                    // Mother mode must transfer only from the matching onPause
                    // lifecycle epoch. Treating !RESUMED as a transfer trigger
                    // races with an earlier onPause that already set
                    // activityVisible=false, causing immediate fail-closed
                    // START_PRECONDITION_DENIED_ACTIVITY_VISIBILITY_CHANGED.
                    if (R5MotherActivationGate.canEagerlyActivate(deferPrivateUntilBackground)) {
                        scope.launch { activateRenderer(state) }
                    } else if (renderer.state.value == PrivateWebViewDisplay.State.IDLE) {
                        // First pairing may finish while already resumed. A
                        // failed physical restore must not retry on every new
                        // execution generation and create a recovery loop.
                        scheduleForegroundReconciliation()
                    }
                } else if (ownership.status() == WebViewOwnershipState.Host.SUSPENDED) {
                    // Unlock updates safetyEpoch but does NOT create a new generation.
                    // Activity may already be RESUMED, so onResume will not re-fire.
                    scheduleForegroundReconciliation()
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
        val request = lifecycle.signal(foreground = false)
        Log.i("KelpieR5Lifecycle", "pause epoch=" + request.epoch)
        ready = false
        scope.launch {
            lifecycle.reconcile(request) { stillCurrent ->
                // A newer pause may follow a resume while the old transfer is
                // pending. Drain that transfer's physical result before retrying.
                if (renderer.state.value != PrivateWebViewDisplay.State.PRIVATE_DISPLAY ||
                    ownership.status() != WebViewOwnershipState.Host.PRIVATE
                ) {
                    if (!finishRestoreLocked(stillCurrent)) return@reconcile
                }
                val active = consent.snapshot() as? SessionConsentCore.State.Active
                if (!stillCurrent() || active == null || active.locked || closed) return@reconcile
                val entered = activateRendererLocked(active, stillCurrent)
                if (stillCurrent()) {
                    renderer.setActivityVisible(false)
                    ready = entered && consent.snapshot() == active && renderer.isOwnedBy(ctx.tabStore?.activeTab?.webView)
                    Log.i("KelpieR5Lifecycle", "pause_commit_background epoch=" + request.epoch + " ready=" + ready)
                }
            }
        }
    }

    fun onResume() {
        val request = lifecycle.signal(foreground = true)
        Log.i("KelpieR5Lifecycle", "resume epoch=" + request.epoch)
        ready = false
        renderer.setActivityVisible(true)
        scheduleForegroundReconciliation()
    }

    private fun scheduleForegroundReconciliation() {
        val request = lifecycle.current()
        if (!request.foreground) return
        scope.launch {
            lifecycle.reconcile(request) { stillCurrent ->
                if (!finishRestoreLocked(stillCurrent)) return@reconcile
                val active = consent.snapshot() as? SessionConsentCore.State.Active ?: return@reconcile
                if (!stillCurrent() || active.locked || closed) return@reconcile
                if (ownership.status() == WebViewOwnershipState.Host.SUSPENDED) {
                    rebindAfterRealOwnerUnlockLocked(active, stillCurrent)
                    return@reconcile
                }
                val tab = ctx.tabStore?.activeTab ?: return@reconcile
                val target = nativePort.nativeTargetForBinding(tab.id) ?: return@reconcile
                if (!stillCurrent() ||
                    consent.snapshot() != active ||
                    !tab.webView.isShown ||
                    !activity.window.decorView.isShown
                ) {
                    return@reconcile
                }
                if (ownership.status() == WebViewOwnershipState.Host.ABSENT) {
                    ownership.bindForeground(active.generation, target.rendererIdentity)
                }
                ready = ownership.mayExecuteWebOperation(active.generation, target.rendererIdentity)
            }
        }
    }

    /** Called only with transferMutex held; no lifecycle decision is made before acquiring it. */
    private suspend fun finishRestoreLocked(stillCurrent: () -> Boolean): Boolean {
        if (closed || !stillCurrent()) return false
        if (renderer.state.value == PrivateWebViewDisplay.State.IDLE) {
            ownership.resetAfterVerifiedRestore()
            return true
        }
        ready = false
        val active = consent.snapshot() as? SessionConsentCore.State.Active
        val owner = ownership
        val ticket = active?.let { owner.restoreForeground(it.generation) }
        renderer.stop()
        if (closed || !stillCurrent()) return false
        val restored = renderer.verifyRestored()
        if (closed || !stillCurrent()) return false
        if (!restored) {
            suspendOnRendererFailure("RESTORE_VERIFY_FAILED", active)
            return false
        }
        if (ownership === owner) {
            if (ticket != null) owner.completeRestore(ticket)
            owner.resetAfterVerifiedRestore()
        }
        return true
    }

    /** Unlock is a native signal, not a renewed remote authorization. */
    private suspend fun rebindAfterRealOwnerUnlockLocked(
        active: SessionConsentCore.State.Active,
        stillCurrent: () -> Boolean,
    ) {
        if (closed || ready || consent.snapshot() != active || !stillCurrent()) return
        val tab = ctx.tabStore?.activeTab ?: return
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
        if (!eligible) return
        val target = nativePort.nativeTargetForBinding(tab.id) ?: return
        if (!stillCurrent() || consent.snapshot() != active) return
        if (!ownership.unlockNeedsReattach(active.generation)) return
        if (!ownership.bindForeground(active.generation, target.rendererIdentity)) {
            ownership.failRenderer()
            suspendOnRendererFailure("UNLOCK_REBIND_FAILED", active)
            return
        }
        val unchanged =
            stillCurrent() &&
                consent.snapshot() == active &&
                ctx.tabStore?.activeTab?.webView === tab.webView &&
                ctx.tabStore?.activeTab?.id == tab.id &&
                !closed &&
                power?.isInteractive == true &&
                keyguard?.isDeviceLocked == false &&
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                renderer.state.value == PrivateWebViewDisplay.State.IDLE
        if (!unchanged) {
            ready = false
            ownership.failRenderer()
            suspendOnRendererFailure("UNLOCK_GENERATION_CHANGED", active)
            return
        }
        ready = true
        Log.i("KelpieR5Unlock", "OWNER_UNLOCK_REBIND_VERIFIED")
    }

    private suspend fun activateRenderer(active: SessionConsentCore.State.Active) =
        transferMutex.withLock {
            val request = lifecycle.current()
            activateRendererLocked(active) { !closed && lifecycle.isCurrent(request) }
        }

    private suspend fun activateRendererLocked(
        active: SessionConsentCore.State.Active,
        stillCurrent: () -> Boolean,
    ): Boolean {
        if (closed || active.locked || consent.snapshot() != active || !stillCurrent()) return false
        val tab = ctx.tabStore?.activeTab ?: return false
        val target = nativePort.nativeTargetForBinding(tab.id) ?: return false
        if (!stillCurrent() || consent.snapshot() != active || ctx.tabStore?.activeTab?.webView !== tab.webView) return false
        if (renderer.state.value == PrivateWebViewDisplay.State.PRIVATE_DISPLAY) {
            return currentTabId == tab.id && ownership.mayExecuteWebOperation(active.generation, target.rendererIdentity)
        }
        if (ownership.status() == WebViewOwnershipState.Host.ABSENT &&
            !ownership.bindForeground(active.generation, target.rendererIdentity)
        ) {
            return false
        }
        val owner = ownership
        val ticket = owner.moveToPrivate(active.generation) ?: return false
        ready = false
        Log.i(
            "KelpieR5Lifecycle",
            "private_start lifecycleResumed=" +
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) +
                " viewAttached=" + tab.webView.isAttachedToWindow +
                " nativeGeometryValid=" + (tab.webView.width > 0 && tab.webView.height > 0),
        )
        val entered = renderer.start(tab.webView)
        if (!entered || consent.snapshot() != active || ownership !== owner || !owner.completePrivate(ticket)) {
            owner.failRenderer()
            nativePort.clearPendingEvidence()
            renderer.stop()
            if (stillCurrent()) suspendOnRendererFailure("PRIVATE_RENDERER_TRANSFER_FAILED", active)
            return false
        }
        currentTabId = tab.id
        ready = stillCurrent()
        return true
    }

    /**
     * A renderer failure retires work, not the owner's durable computer approval.
     * Mother mode does not eagerly transfer on a new execution epoch; recovery
     * still waits for the existing native lifecycle path. No retry loop is added.
     */
    private fun suspendOnRendererFailure(
        reason: String,
        admitted: SessionConsentCore.State.Active?,
    ) {
        if (admitted == null || consent.snapshot() != admitted) return
        Log.w("KelpieR5Recovery", "runtime suspended after " + reason)
        ready = false
        runtime.cancelInFlight()
        if (deferPrivateUntilBackground) {
            AndroidAgentProcessSession.rendererUnavailable(admitted)
        } else {
            AndroidAgentProcessSession.stop()
            AgentSessionNotification.hide(activity)
        }
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
 * Mother profile requires an actual, epoch-verified onPause transfer. A
 * temporarily non-RESUMED lifecycle while collecting native consent does not
 * authorize a separate renderer ownership change.
 */
internal object R5MotherActivationGate {
    fun canEagerlyActivate(deferPrivateUntilBackground: Boolean): Boolean = !deferPrivateUntilBackground
}
