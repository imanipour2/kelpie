package com.kelpie.browser.capture

import android.app.KeyguardManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.ComponentActivity
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    fun register(router: Router) {
        if (!qaEnabled) return

        router.register("capture-session-request") { body ->
            val verifiedClient = verifiedClientId()
                ?: return@register errorResponse("PERMISSION_REQUIRED", "Verified paired HTTP client required")
            val attemptId = body["attemptId"] as? String
                ?: return@register errorResponse("INVALID_PARAMS", "attemptId required")
            withContext(Dispatchers.Main.immediate) {
                val tab = context.tabStore?.activeTab
                    ?: return@withContext errorResponse("NO_WEBVIEW", "No active browser tab")
                if (closed || renderer.state.value != PrivateWebViewDisplay.State.IDLE) {
                    return@withContext errorResponse("RECORDING_IN_PROGRESS", "Private capture busy")
                }
                if (!allowedQaOrigin(tab.currentUrl, tab.webView.url)) {
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
            val client = verifiedClientId()
                ?: return@register errorResponse("PERMISSION_REQUIRED", "Verified paired HTTP client required")
            val requestId = body["requestId"] as? String
                ?: return@register errorResponse("INVALID_PARAMS", "requestId required")
            val status = registry.status(requestId, client)
            successResponse(
                mapOf(
                    "status" to status,
                    "renderer" to renderer.state.value.name,
                ),
            )
        }

        router.register("capture-session-frame") { body ->
            val client = verifiedClientId()
                ?: return@register errorResponse("PERMISSION_REQUIRED", "Verified paired HTTP client required")
            val requestId = body["requestId"] as? String
                ?: return@register errorResponse("INVALID_PARAMS", "requestId required")
            val attemptId = body["attemptId"] as? String
                ?: return@register errorResponse("INVALID_PARAMS", "attemptId required")
            val permitted =
                withContext(Dispatchers.Main.immediate) {
                    val tab = context.tabStore?.activeTab ?: return@withContext false
                    allowedQaOrigin(tab.currentUrl, tab.webView.url) &&
                        registry.claimFrame(
                        requestId,
                        client,
                        attemptId,
                        tab.id,
                        tab.documentVersion,
                        tab.webView,
                        unlocked = !isLocked(),
                        userStopped = userStopped || closed,
                    )
                }
            if (!permitted) {
                return@register errorResponse("PERMISSION_REQUIRED", "Private capture grant missing, expired or revoked")
            }
            val image = renderer.captureFreshPng()
                ?: return@register errorResponse("SCREENSHOT_FAILED", "No fresh private frame available")
            if (closed || userStopped || renderer.state.value != PrivateWebViewDisplay.State.PRIVATE_DISPLAY) {
                return@register errorResponse("PERMISSION_REQUIRED", "Capture stopped before delivery")
            }
            successResponse(image)
        }

        router.register("capture-session-stop") { body ->
            val client = verifiedClientId()
                ?: return@register errorResponse("PERMISSION_REQUIRED", "Verified paired HTTP client required")
            val requestId = body["requestId"] as? String
                ?: return@register errorResponse("INVALID_PARAMS", "requestId required")
            if (registry.status(requestId, client) == "NOT_FOUND") {
                return@register errorResponse("PERMISSION_REQUIRED", "Unknown capture for this client")
            }
            stopByUser()
            successResponse(mapOf("stopped" to true))
        }
    }

    /** Only the native positive dialog's callback may call this function. */
    suspend fun approveOnDevice(requestId: String): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (closed || !qaEnabled || isLocked()) {
                registry.revoke()
                return@withContext false
            }
            val tab = context.tabStore?.activeTab ?: return@withContext false
            val ok =
                registry.approveOnDevice(
                    requestId,
                    tab.id,
                    tab.documentVersion,
                    tab.webView,
                    unlocked = !isLocked(),
                )
            if (!ok || !allowedQaOrigin(tab.currentUrl, tab.webView.url)) {
                registry.revoke()
                return@withContext false
            }
            userStopped = false
            val ready = renderer.start(tab.webView)
            if (!ready) {
                registry.revoke()
                renderer.stop()
                return@withContext false
            }
            expiry?.cancel()
            expiry =
                scope.launch {
                    delay(ConsentedCaptureLease.MAX_LEASE_MS)
                    stopByUser()
                }
            true
        }

    fun denyOnDevice() {
        registry.revoke()
    }

    suspend fun stopByUser() =
        withContext(Dispatchers.Main.immediate) {
            userStopped = true
            registry.revoke()
            expiry?.cancel()
            expiry = null
            renderer.stop()
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
        } else if (renderer.state.value == PrivateWebViewDisplay.State.RESTORE_PENDING) {
            scope.launch { renderer.verifyRestored() }
        }
    }

    fun close() {
        closed = true
        userStopped = true
        registry.revoke()
        expiry?.cancel()
        expiry = null
        renderer.dispose()
        scope.cancel()
    }

    private fun allowedQaOrigin(tabUrl: String, actualUrl: String?): Boolean {
        val tab = Uri.parse(tabUrl)
        val view = Uri.parse(actualUrl.orEmpty())
        return tab.scheme == "https" && tab.host == "example.com" &&
            view.scheme == "https" && view.host == "example.com"
    }

    private fun isLocked(): Boolean =
        activity.isFinishing ||
            activity.isDestroyed ||
            activity.getSystemService(KeyguardManager::class.java).isKeyguardLocked
}
