package com.kelpie.browser.capture

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.kelpie.browser.connect.ConsentedCaptureLease
import com.kelpie.browser.connect.ConsentedCaptureRequestRegistry
import com.kelpie.browser.handlers.HandlerContext
import com.kelpie.browser.network.Router
import com.kelpie.browser.network.errorResponse
import com.kelpie.browser.network.successResponse
import com.kelpie.browser.network.verifiedClientId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Early production candidate, compiled only into an opt-in QA app initially.
 * No real browser data leaves the phone without a verified paired principal,
 * a fresh native user gesture and exactly matching TabStore document identity.
 */
class PrivateCaptureCoordinator(
    private val activity: ComponentActivity,
    private val context: HandlerContext,
    private val qaEnabled: Boolean,
) {
    private val registry = ConsentedCaptureRequestRegistry(SystemClock::elapsedRealtime)
    private val compositionReleased = MutableStateFlow(false)
    val relinquished = compositionReleased.asStateFlow()
    val pending = registry.prompt
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val renderer =
        PrivateWebViewDisplay(
            activity,
            composeRelease = { compositionReleased.value = true },
            composeRestore = { compositionReleased.value = false },
        )
    val rendererState = renderer.state
    private var expiry: Job? = null
    private var closed = false
    private var userStopped = false
    private val frameFlight = Mutex()

    // Internal, authenticated ownership: a random client or request ID cannot
    // end another user's active capture by querying its status.
    private var activeRequestId: String? = null
    private var activeClientId: String? = null

    // Screen-off is a security boundary, not merely a pause in screenshot delivery.
    // Use a context receiver because ACTION_SCREEN_OFF is not delivered to manifest receivers.
    // Registration and removal are scoped to this QA-only coordinator's lifecycle.
    private val screenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action != Intent.ACTION_SCREEN_OFF || closed) return
                val completion = goAsync()
                scope.launch {
                    try {
                        stopByUser()
                    } finally {
                        completion.finish()
                    }
                }
            }
        }

    init {
        if (qaEnabled) {
            context.privateCaptureRestricted = {
                closed || renderer.state.value != PrivateWebViewDisplay.State.IDLE
            }
            ContextCompat.registerReceiver(
                activity,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    fun register(router: Router) {
        if (!qaEnabled) return

        router.register("capture-session-request") { body ->
            val verifiedClient =
                verifiedClientId()
                    ?: return@register errorResponse("PERMISSION_REQUIRED", "Verified paired HTTP client required")
            val attemptId =
                body["attemptId"] as? String
                    ?: return@register errorResponse("INVALID_PARAMS", "attemptId required")
            withContext(Dispatchers.Main.immediate) {
                val tab =
                    context.tabStore?.activeTab
                        ?: return@withContext errorResponse("NO_WEBVIEW", "No active browser tab")
                if (closed || renderer.state.value != PrivateWebViewDisplay.State.IDLE) {
                    return@withContext errorResponse("RECORDING_IN_PROGRESS", "Private capture busy")
                }
                if (!PrivateCaptureQaOrigin.permitted(tab.currentUrl, tab.webView.url)) {
                    return@withContext errorResponse("PERMISSION_REQUIRED", "QA capture limited to public example.com")
                }
                val request =
                    registry.submitAuthenticated(
                        verifiedClient,
                        attemptId,
                        tab.id,
                        tab.documentVersion,
                        tab.webView,
                    ) ?: return@withContext errorResponse("PERMISSION_REQUIRED", "Invalid or pending request")
                successResponse(
                    mapOf(
                        "requestId" to request.requestId,
                        "status" to "PENDING_LOCAL_APPROVAL",
                        "approvalTtlMs" to ConsentedCaptureRequestRegistry.PENDING_TTL_MS,
                    ),
                )
            }
        }

        router.register("capture-session-status") { body ->
            val client =
                verifiedClientId()
                    ?: return@register errorResponse("PERMISSION_REQUIRED", "Verified paired HTTP client required")
            val requestId =
                body["requestId"] as? String
                    ?: return@register errorResponse("INVALID_PARAMS", "requestId required")
            // status() itself expires/revokes grants on the monotonic clock.
            // Release the renderer in the same operation instead of depending
            // on an Android background coroutine that may resume much later.
            val status =
                withContext(Dispatchers.Main.immediate) {
                    val status = registry.status(requestId, client)
                    if (PrivateCaptureExpiryCleanup.shouldRelease(activeRequestId, activeClientId, requestId, client, status)) {
                        stopByUser()
                    }
                    status
                }
            // No cross-client renderer state, image counts or outcome disclosure.
            if (status == "NOT_FOUND") return@register successResponse(mapOf("status" to "NOT_FOUND"))
            successResponse(
                mapOf(
                    "status" to status,
                    "renderer" to renderer.state.value.name,
                    "qaFrameDiagnostics" to renderer.qaDiagnostics(),
                ),
            )
        }

        router.register("capture-session-frame") { body ->
            val client =
                verifiedClientId()
                    ?: return@register errorResponse("PERMISSION_REQUIRED", "Verified paired HTTP client required")
            val requestId =
                body["requestId"] as? String
                    ?: return@register errorResponse("INVALID_PARAMS", "requestId required")
            val attemptId =
                body["attemptId"] as? String
                    ?: return@register errorResponse("INVALID_PARAMS", "attemptId required")
            // Reject overlap instead of queuing: a denied concurrent request must
            // consume neither the grant's frame budget nor the QA pixel sequence.
            if (!frameFlight.tryLock()) {
                return@register errorResponse("RECORDING_IN_PROGRESS", "Capture frame already pending")
            }
            try {
                val frameTarget =
                    withContext(Dispatchers.Main.immediate) {
                        val tab = context.tabStore?.activeTab ?: return@withContext null
                        if (!PrivateCaptureQaOrigin.permitted(tab.currentUrl, tab.webView.url)) {
                            return@withContext null
                        }
                        if (!registry.claimFrame(
                                requestId,
                                client,
                                attemptId,
                                tab.id,
                                tab.documentVersion,
                                tab.webView,
                                unlocked = !isLocked(),
                                userStopped = userStopped || closed,
                            )
                        ) {
                            return@withContext null
                        }
                        Triple(tab.id, tab.documentVersion, tab.webView)
                    }
                if (frameTarget == null) {
                    withContext(Dispatchers.Main.immediate) {
                        if (activeRequestId == requestId && activeClientId == client) {
                            val tab = context.tabStore?.activeTab
                            val invalidOrigin =
                                tab == null || !PrivateCaptureQaOrigin.permitted(tab.currentUrl, tab.webView.url)
                            // A wrong attempt with a legitimate requestId must not stop
                            // an otherwise valid owner's private display.
                            if (invalidOrigin || registry.status(requestId, client) != "APPROVED") {
                                stopByUser()
                            }
                        }
                    }
                    return@register errorResponse("PERMISSION_REQUIRED", "Private capture grant missing, expired or revoked")
                }
                val image = renderer.captureFreshPng()
                if (image == null) {
                    if (registry.finalFrameClaimed(requestId, client)) stopByUser()
                    return@register errorResponse("SCREENSHOT_FAILED", "No fresh private frame available")
                }

                // Navigation/STOP/expiry may occur during ImageReader or PNG encoding.
                // Do not send a frame unless the same approved document is still hidden.
                val stillAuthorized =
                    withContext(Dispatchers.Main.immediate) {
                        val tab = context.tabStore?.activeTab
                        tab != null &&
                            tab.id == frameTarget.first &&
                            tab.documentVersion == frameTarget.second &&
                            tab.webView === frameTarget.third &&
                            PrivateCaptureQaOrigin.permitted(tab.currentUrl, tab.webView.url) &&
                            registry.status(requestId, client) == "APPROVED" &&
                            !closed &&
                            !userStopped &&
                            !isLocked() &&
                            renderer.canDeliverFrame()
                    }
                if (!stillAuthorized) {
                    withContext(Dispatchers.Main.immediate) {
                        if (activeRequestId == requestId && activeClientId == client) stopByUser()
                    }
                    return@register errorResponse("PERMISSION_REQUIRED", "Capture identity or visibility changed before delivery")
                }
                // Revoke and release the private display as soon as the last
                // locally approved frame is encoded. A 21st request is not needed.
                if (registry.finalFrameClaimed(requestId, client)) stopByUser()
                successResponse(image)
            } finally {
                frameFlight.unlock()
            }
        }

        router.register("capture-session-stop") { body ->
            val client =
                verifiedClientId()
                    ?: return@register errorResponse("PERMISSION_REQUIRED", "Verified paired HTTP client required")
            val requestId =
                body["requestId"] as? String
                    ?: return@register errorResponse("INVALID_PARAMS", "requestId required")
            val owned = registry.revokeOwned(requestId, client)
            val activeOwned = activeRequestId == requestId && activeClientId == client
            if (!owned && !activeOwned) {
                return@register errorResponse("PERMISSION_REQUIRED", "Unknown capture for this client")
            }
            stopByUser()
            successResponse(mapOf("stopped" to true))
        }
    }

    /** Only the native positive dialog's callback may call this function. */
    suspend fun approveOnDevice(requestId: String): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (closed || !qaEnabled || isLocked() || !PrivateCaptureNotification.canNotify(activity)) {
                registry.revoke()
                return@withContext false
            }
            val tab = context.tabStore?.activeTab ?: return@withContext false
            val approvedPrincipal = pending.value?.takeIf { it.requestId == requestId }?.clientId ?: return@withContext false
            val ok =
                registry.approveOnDevice(
                    requestId,
                    tab.id,
                    tab.documentVersion,
                    tab.webView,
                    unlocked = !isLocked(),
                )
            if (!ok || !PrivateCaptureQaOrigin.permitted(tab.currentUrl, tab.webView.url)) {
                registry.revoke()
                return@withContext false
            }
            userStopped = false
            val ready = renderer.start(tab.webView)
            if (!ready || !PrivateCaptureNotification.show(activity, this@PrivateCaptureCoordinator)) {
                registry.revoke()
                // An unsuccessful START runs while the Activity is already visible;
                // there may be no future onResume() to finish restoration.
                withContext(NonCancellable) {
                    renderer.stop()
                    if (renderer.state.value == PrivateWebViewDisplay.State.RESTORE_PENDING ||
                        renderer.state.value == PrivateWebViewDisplay.State.FAILED
                    ) {
                        renderer.verifyRestored()
                    }
                    PrivateCaptureNotification.hide(activity)
                }
                return@withContext false
            }
            activeRequestId = requestId
            activeClientId = approvedPrincipal
            expiry?.cancel()
            expiry =
                scope.launch {
                    delay(ConsentedCaptureLease.MAX_LEASE_MS)
                    // Do not cancel this same expiry Job from stopByUser():
                    // self-cancellation can interrupt the suspended renderer
                    // cleanup and leave PRIVATE_DISPLAY after grant revocation.
                    expiry = null
                    stopByUser()
                }
            true
        }

    fun denyOnDevice() {
        registry.revoke()
    }

    suspend fun stopByUser() =
        // STOP/expiry/lock must complete even when the calling HTTP coroutine
        // is cancelled while native display restoration is in progress.
        withContext(NonCancellable) {
            withContext(Dispatchers.Main.immediate) {
                userStopped = true
                registry.revoke()
                activeRequestId = null
                activeClientId = null
                expiry?.cancel()
                expiry = null
                try {
                    renderer.stop()
                } finally {
                    PrivateCaptureNotification.hide(activity)
                }
            }
        }

    fun onPause() {
        renderer.setActivityVisible(false)
    }

    fun onResume() {
        renderer.setActivityVisible(true)
        if (renderer.state.value == PrivateWebViewDisplay.State.PRIVATE_DISPLAY) {
            scope.launch {
                stopByUser()
                renderer.verifyRestored()
            }
        } else if (renderer.state.value == PrivateWebViewDisplay.State.RESTORE_PENDING ||
            renderer.state.value == PrivateWebViewDisplay.State.FAILED
        ) {
            scope.launch { renderer.verifyRestored() }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        if (qaEnabled) activity.unregisterReceiver(screenOffReceiver)
        userStopped = true
        registry.revoke()
        activeRequestId = null
        activeClientId = null
        expiry?.cancel()
        expiry = null
        renderer.dispose()
        // A posted off-main disposal may still own the original WebView.
        // Never open a remote operation window during Activity destruction.
        PrivateCaptureNotification.hide(activity)
        scope.cancel()
    }

    private fun isLocked(): Boolean =
        activity.isFinishing ||
            activity.isDestroyed ||
            !activity.getSystemService(PowerManager::class.java).isInteractive ||
            activity.getSystemService(KeyguardManager::class.java).isKeyguardLocked
}
