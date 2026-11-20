package com.kelpie.browser.network

import android.webkit.WebView
import com.kelpie.browser.agent.session.AndroidAgentProcessSession
import com.kelpie.browser.agent.session.SessionConsentCore
import com.kelpie.browser.capture.PrivateCaptureOperationPolicy
import com.kelpie.browser.handlers.HandlerContext

typealias RouteHandler = suspend (body: Map<String, Any?>) -> Map<String, Any?>

class Router(
    internal val nativeConsentSnapshot: () -> SessionConsentCore.State = { AndroidAgentProcessSession.snapshot() },
) {
    private val routes = mutableMapOf<String, RouteHandler>()
    var webView: WebView? = null
    var handlerContext: HandlerContext? = null
    var scriptPlaybackState: com.kelpie.browser.handlers.ScriptPlaybackState? = null
    var browserGateway: BrowserGatewayBinding? = null

    /** Isolated R5 consent QA never accepts legacy remote browser operations. */
    var consentOnlyQa = false

    /** R5 QA commands are gated separately from all legacy browser routes. */
    var backgroundAgentQa = false

    /** R5 native renderer with standard mother MCP and bearer-first identity. */
    var motherR5BackgroundQa = false

    /** Snapshot of currently-registered method names. */
    val registeredMethods: Set<String>
        get() = routes.keys.toSet()

    fun register(
        method: String,
        handler: RouteHandler,
    ) {
        routes[method] = handler
    }

    fun registerIfAbsent(
        method: String,
        handler: RouteHandler,
    ) {
        if (routes.containsKey(method)) {
            return
        }
        routes[method] = handler
    }

    suspend fun handle(
        method: String,
        body: Map<String, Any?>,
        bypassRecordingGate: Boolean = false,
        fromGateway: Boolean = false,
    ): Pair<Int, Map<String, Any?>> {
        if (motherR5BackgroundQa && !R5RemoteOperationPolicy.permitsRoute(method, fromGateway)) {
            return 403 to errorResponse("NEEDS_HUMAN", "This export or unobserved action requires a separate native owner workflow")
        }
        val controlOnly = !R5McpBootstrapGate.requiresNativeGrant(method, body, verifiedClientId())
        // Foreground visibility is not owner consent. A request admitted before
        // STOP/re-approval or lock/unlock retains its original grant and epoch.
        if (motherR5BackgroundQa && !controlOnly && !R5RequestAuthorization.current(nativeConsentSnapshot)) {
            return 403 to errorResponse("PERMISSION_REQUIRED", "Native owner-authorized request required")
        }
        if (motherR5BackgroundQa &&
            !controlOnly &&
            method != "mcp" &&
            handlerContext?.motherPrivateAgentActive?.invoke() == true &&
            handlerContext?.motherGatewayAuthorization?.invoke() != true
        ) {
            return 503 to errorResponse("RENDERER_NOT_READY", "Computer remains approved; the private browser renderer is temporarily unavailable")
        }
        // This QA build has no real agent browser-action authorization adapter.
        // Reject legacy routes, including nested MCP and any gateway bypass.
        if (consentOnlyQa &&
            method !in CONSENT_QA_METHODS &&
            !(backgroundAgentQa && method in R5_AGENT_METHODS)
        ) {
            return 403 to errorResponse("PERMISSION_REQUIRED", "R5 QA permits consent/status/STOP only")
        }
        val privateOwnerAtAdmission = handlerContext?.privateCaptureRestricted?.invoke() == true
        if (!controlOnly &&
            !PrivateCaptureOperationPolicy.permits(
                method,
                privateOwnerAtAdmission,
                outerEnvelope = true,
            )
        ) {
            return 403 to errorResponse("PERMISSION_REQUIRED", "Private WebView capture owns the browser")
        }
        val gateway = browserGateway
        // Native consent request/status/STOP must not acquire a WebView gateway lease:
        // requiring an existing private-owner grant here deadlocks first approval.
        if (gateway != null &&
            !fromGateway &&
            !bypassRecordingGate &&
            !R5AgentControlPlaneRoutes.isConsentMethod(method) &&
            method !in setOf("browser-control", "mcp", "ai-infer", "ai-record", "ai-cancel", "abort-script", "get-script-status")
        ) {
            return gateway.legacy(method) { handle(method, body, bypassRecordingGate, fromGateway = true) }
        }
        val handler =
            routes[method]
                ?: return 404 to
                    mapOf(
                        "success" to false,
                        "error" to mapOf("code" to "NOT_FOUND", "message" to "Unknown method: $method"),
                    )
        if (!bypassRecordingGate) {
            val gateError = scriptPlaybackState?.recordingError(method)
            if (gateError != null) {
                return 409 to gateError
            }
        }
        val result = handler(body)
        // A command admitted before P4 may not leak stale DOM/screenshot data
        // after transfer. Existing private-envelope calls are allowed through
        // only if Gateway/MCPSession has independently checked their inner method.
        val privateOwnerAtDelivery = handlerContext?.privateCaptureRestricted?.invoke() == true
        if (motherR5BackgroundQa && !controlOnly && !R5RequestAuthorization.current(nativeConsentSnapshot)) {
            return 403 to errorResponse("SESSION_REVOKED", "Native owner approval changed before delivery")
        }
        if (!controlOnly &&
            !PrivateCaptureOperationPolicy.permitsDelivery(
                method,
                privateOwnerAtAdmission,
                privateOwnerAtDelivery,
            )
        ) {
            return 403 to errorResponse("PERMISSION_REQUIRED", "Private WebView capture owns the browser")
        }
        val success = result["success"] as? Boolean ?: false
        val errorCode = (result["error"] as? Map<*, *>)?.get("code") as? String
        val status =
            if (method == "browser-control" || method == "mcp" || success) {
                200
            } else if (errorCode == "SCRIPT_PARTIAL_FAILURE" || errorCode == "SCRIPT_ABORTED" || result["aborted"] == true) {
                200
            } else if (result.containsKey("error")) {
                statusForErrorCode(errorCode)
            } else {
                200
            }

        val message = body["message"] as? String
        if (message != null && handlerContext?.privateCaptureRestricted?.invoke() != true) {
            handlerContext?.showToast(message)
        }

        return status to result
    }

    companion object {
        private val CONSENT_QA_METHODS =
            setOf("agent-session-request", "agent-session-status", "agent-session-stop", "get-device-info")

        // The authenticated MCP envelope is a transport only. Its inner method
        // is re-checked by Router with the same hard allowlist.
        private val R5_AGENT_METHODS =
            setOf("agent-command", "agent-tabs", "agent-runtime-status", "agent-capture-capabilities", "mcp")
    }

    fun registerFallbacks() {
        for (method in AndroidUnsupportedMethods.all) {
            registerIfAbsent(method) {
                mapOf(
                    "success" to false,
                    "error" to
                        mapOf(
                            "code" to "PLATFORM_NOT_SUPPORTED",
                            "message" to "$method is not supported on Android",
                        ),
                )
            }
        }
    }
}

/**
 * Methods the Android app explicitly advertises as unsupported. This is the
 * single source of truth — DeviceHandler reads it for `get-capabilities`, and
 * the Router uses it to register fallback handlers for any method that no real
 * handler claims.
 */
object AndroidUnsupportedMethods {
    val all: Set<String> =
        setOf(
            "debug-screens",
            "set-debug-overlay",
            "get-debug-overlay",
            "set-geolocation",
            "clear-geolocation",
            "set-request-interception",
            "get-intercepted-requests",
            "clear-request-interception",
            "set-fullscreen",
            "get-fullscreen",
            "set-renderer",
            "get-renderer",
        )
}

fun errorResponse(
    code: String,
    message: String,
    diagnostics: Map<String, Any?>? = null,
): Map<String, Any?> {
    val error = mutableMapOf<String, Any?>("code" to code, "message" to message)
    if (diagnostics != null && diagnostics.isNotEmpty()) {
        error["diagnostics"] = diagnostics
    }
    return mapOf("success" to false, "error" to error)
}

fun successResponse(data: Map<String, Any?> = emptyMap()): Map<String, Any?> = mutableMapOf<String, Any?>("success" to true).apply { putAll(data) }

private fun statusForErrorCode(code: String?): Int =
    when (code) {
        "ELEMENT_NOT_FOUND",
        "WATCH_NOT_FOUND",
        -> 404
        "TIMEOUT" -> 408
        "RENDERER_NOT_READY" -> 503
        "NAVIGATION_ERROR" -> 502
        "PLATFORM_NOT_SUPPORTED", "PARTITION_UNSUPPORTED" -> 501
        "IFRAME_ACCESS_DENIED",
        "PERMISSION_REQUIRED",
        "SHADOW_ROOT_CLOSED",
        -> 403
        "RECORDING_IN_PROGRESS", "PARTITION_IN_USE", "PARTITION_DELETING" -> 409
        "WEBVIEW_ERROR" -> 500
        else -> 400
    }
