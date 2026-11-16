package com.kelpie.browser.network

import android.webkit.WebView
import com.kelpie.browser.capture.PrivateCaptureOperationPolicy
import com.kelpie.browser.handlers.HandlerContext

typealias RouteHandler = suspend (body: Map<String, Any?>) -> Map<String, Any?>

class Router {
    private val routes = mutableMapOf<String, RouteHandler>()
    var webView: WebView? = null
    var handlerContext: HandlerContext? = null
    var scriptPlaybackState: com.kelpie.browser.handlers.ScriptPlaybackState? = null
    var browserGateway: BrowserGatewayBinding? = null

    /** Isolated R5 consent QA never accepts legacy remote browser operations. */
    var consentOnlyQa = false

    /** Opt-in isolated R5 read-only pilot; all legacy mutators remain denied. */
    var agentObservationQa = false

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
        // This QA build has no real agent browser-action authorization adapter.
        // Reject legacy routes, including nested MCP and any gateway bypass.
        if (consentOnlyQa &&
            method !in CONSENT_QA_METHODS &&
            !(agentObservationQa && method in OBSERVATION_QA_METHODS)
        ) {
            return 403 to errorResponse("PERMISSION_REQUIRED", "R5 QA permits consent/status/STOP only")
        }
        val privateOwnerAtAdmission = handlerContext?.privateCaptureRestricted?.invoke() == true
        if (!PrivateCaptureOperationPolicy.permits(
                method,
                privateOwnerAtAdmission,
                outerEnvelope = true,
            )
        ) {
            return 403 to errorResponse("PERMISSION_REQUIRED", "Private WebView capture owns the browser")
        }
        val gateway = browserGateway
        if (gateway != null && !fromGateway && !bypassRecordingGate && method !in setOf("browser-control", "mcp", "ai-infer", "ai-record", "ai-cancel", "abort-script", "get-script-status")) {
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
        if (!PrivateCaptureOperationPolicy.permitsDelivery(
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

        // MCP is a transport, not an authority. Every inner method still
        // passes this same Router gate and its verified-client context.
        private val OBSERVATION_QA_METHODS = setOf("agent-observe", "mcp")
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
