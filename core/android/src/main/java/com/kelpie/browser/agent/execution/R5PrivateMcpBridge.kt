package com.kelpie.browser.agent.execution

import com.kelpie.browser.agent.session.AndroidAgentProcessSession
import com.kelpie.browser.agent.session.SessionConsentCore
import com.kelpie.browser.network.Router
import com.kelpie.browser.network.verifiedClientId
import kotlinx.serialization.json.JsonPrimitive

/**
 * Private R5 QA MCP facade. Replaces only /mcp in an isolated R5 QA build,
 * after the shared gateway registers, never the ordinary Kelpie product routes.
 *
 * Pairing bearer -> native owner process grant -> R5 operation runtime.
 * No legacy route re-enable, raw JS evaluator, cookies, OTP or credentials.
 * Wire-protocol header/version compatibility still requires independent QA.
 */
internal class R5PrivateMcpBridge(
    private val browserReady: () -> Boolean,
) {
    fun register(router: Router) {
        router.register("mcp") { envelope ->
            val principal = verifiedClientId()
            if (principal == null) {
                rpcError(envelope["id"], -32001, "Authenticated paired client required")
            } else {
                handle(envelope, principal, router)
            }
        }
    }

    private suspend fun handle(
        envelope: Map<String, Any?>,
        principal: String,
        router: Router,
    ): Map<String, Any?> {
        val id = envelope["id"]
        if (envelope["jsonrpc"] != "2.0") return rpcError(id, -32600, "Invalid JSON-RPC envelope")
        if (id == null && envelope["method"] == "notifications/initialized") return emptyMap()
        if (id == null) return rpcError(null, -32600, "Request id required")
        return when (envelope["method"] as? String) {
            "initialize" ->
                rpcResult(
                    id,
                    mapOf(
                        "protocolVersion" to "2025-11-25",
                        "capabilities" to mapOf("tools" to emptyMap<String, Any>()),
                        "serverInfo" to mapOf("name" to "kelpie-r5-private-qa", "version" to "0.1.0"),
                    ),
                )
            "server/discover" ->
                rpcResult(
                    id,
                    mapOf(
                        "resultType" to "complete",
                        "supportedVersions" to listOf("2026-07-28", "2025-11-25"),
                        "capabilities" to mapOf("tools" to emptyMap<String, Any>()),
                        "_meta" to
                            mapOf(
                                "io.modelcontextprotocol/serverInfo" to
                                    mapOf("name" to "kelpie-r5-private-qa", "version" to "0.1.0"),
                            ),
                        "ttlMs" to 0,
                        "cacheScope" to "private",
                    ),
                )
            "tools/list" ->
                rpcResult(
                    id,
                    mapOf("resultType" to "complete", "tools" to toolCatalog(), "ttlMs" to 0, "cacheScope" to "private"),
                )
            "tools/call" -> callTool(id, envelope, principal, router)
            else -> rpcError(id, -32601, "Unsupported private R5 MCP method")
        }
    }

    private fun toolCatalog(): List<Map<String, Any?>> =
        listOf(
            mapOf(
                "name" to R5PrivateMcpContract.BROWSER_TOOL,
                "description" to "Owner-consented R5 QA browser: tabs, navigation, DOM, scroll, clicks, normal form input and app-owned PNG; never other apps or remote JS.",
                "inputSchema" to
                    mapOf(
                        "type" to "object",
                        "required" to listOf("version", "method"),
                        "properties" to
                            mapOf(
                                "version" to mapOf("type" to "integer", "const" to 1),
                                "method" to
                                    mapOf(
                                        "type" to "string",
                                        "enum" to ((
                                            listOf(
                                                "capabilities",
                                                "documentation",
                                                "session.request",
                                                "session.status",
                                                "session.stop",
                                                "tabs.list",
                                                "tab.snapshot",
                                                "tab.screenshot",
                                            ) + R5PrivateMcpActions.methods
                                        )),
                                    ),
                                "tabId" to mapOf("type" to "string"),
                                "params" to mapOf("type" to "object"),
                            ),
                    ),
                "annotations" to mapOf("readOnlyHint" to false),
            ),
            mapOf(
                "name" to R5PrivateMcpContract.ACTION_TOOL,
                "description" to "Mutating R5 QA: navigation/click/ordinary forms/history/scroll, native owner consent required.",
                "inputSchema" to
                    mapOf(
                        "type" to "object",
                        "required" to listOf("version", "method", "params"),
                        "properties" to
                            mapOf(
                                "version" to mapOf("type" to "integer", "const" to 1),
                                "method" to mapOf("type" to "string", "enum" to R5PrivateMcpActions.methods),
                                "tabId" to mapOf("type" to "string"),
                                "params" to mapOf("type" to "object"),
                            ),
                    ),
                "annotations" to mapOf("readOnlyHint" to false),
            ),
            mapOf(
                "name" to R5PrivateMcpContract.SESSION_TOOL,
                "description" to "Request, inspect or STOP an R5 native session; approval happens only by real local Android tap.",
                "inputSchema" to
                    mapOf(
                        "type" to "object",
                        "required" to listOf("method"),
                        "properties" to
                            mapOf("method" to mapOf("type" to "string", "enum" to listOf("request", "status", "stop"))),
                    ),
            ),
        )

    private suspend fun callTool(
        id: Any,
        envelope: Map<String, Any?>,
        principal: String,
        router: Router,
    ): Map<String, Any?> {
        val params = envelope["params"] as? Map<*, *> ?: return rpcError(id, -32602, "Tool params required")
        val name = params["name"] as? String ?: return rpcError(id, -32602, "Tool name required")
        val arguments =
            R5PrivateMcpContract.toolArguments(envelope)
                ?: return rpcError(id, -32602, "Valid tool arguments required")
        if (name == R5PrivateMcpContract.SESSION_TOOL) {
            val route =
                when (arguments["method"]) {
                    "request" -> "agent-session-request"
                    "status" -> "agent-session-status"
                    "stop" -> "agent-session-stop"
                    else -> return rpcError(id, -32602, "Invalid session method")
                }
            val (status, outcome) = router.handle(route, emptyMap(), fromGateway = true)
            return toolResult(id, outcome, status != 200 || outcome["success"] != true)
        }
        val actionTool = name == R5PrivateMcpContract.ACTION_TOOL
        val supportedTool = name == R5PrivateMcpContract.BROWSER_TOOL || actionTool
        if (!supportedTool || (arguments["version"] as? Number)?.toInt() != 1) {
            return rpcError(id, -32602, "Unsupported browser tool/version")
        }
        val action = arguments["method"] as? String ?: return rpcError(id, -32602, "Browser method required")
        // Explicit verbs reuse the paired browser-control transport.
        // These routes may request a native prompt but never grant approval.
        if (!actionTool && action in setOf("session.request", "session.status", "session.stop")) {
            val route =
                when (action) {
                    "session.request" -> "agent-session-request"
                    "session.status" -> "agent-session-status"
                    else -> "agent-session-stop"
                }
            val (status, outcome) = router.handle(route, emptyMap(), fromGateway = true)
            return toolResult(id, outcome, status != 200 || outcome["success"] != true)
        }
        // The ChatGPT connector currently surfaces only BROWSER_TOOL. Reuse the
        // exact same native admission/replay path for its advertised write methods.
        val controlMutation = name == R5PrivateMcpContract.BROWSER_TOOL && action in R5PrivateMcpActions.methods
        if (!actionTool && action == "documentation") {
            return toolResult(
                id,
                mapOf(
                    "setup" to "Use kelpie_agent_session.status/request; owner approves in native Android UI, then use a fresh generation.",
                    "restrictions" to "Private R5 QA only: no remote credential access, cross-app capture, or automatic STOP renewal.",
                ),
            )
        }
        if (!actionTool && action == "capabilities") {
            val active = AndroidAgentProcessSession.snapshot() as? SessionConsentCore.State.Active
            val owned = active != null && active.principalId == principal && !active.locked && browserReady()
            return toolResult(
                id,
                mapOf(
                    "version" to 1,
                    "nativeSession" to if (owned) "READY" else "OWNER_CONSENT_OR_RENDERER_REQUIRED",
                    "consentGeneration" to if (owned) active?.generation else null,
                    "methods" to
                        listOf(
                            "session.request",
                            "session.status",
                            "session.stop",
                            "tabs.list",
                            "tab.snapshot",
                            "tab.screenshot",
                        ),
                    "mutationToolsAvailable" to owned,
                    "mutationTool" to R5PrivateMcpContract.ACTION_TOOL,
                ),
            )
        }
        val actionParams = arguments["params"] as? Map<*, *> ?: return rpcError(id, -32602, "Fresh action params required")
        val generation =
            (actionParams["consentGeneration"] as? String)
                ?: (arguments["consentGeneration"] as? String)
                ?: return rpcError(id, -32602, "Fresh consentGeneration required")
        if (!actionTool && action == "tabs.list") {
            val (status, outcome) =
                router.handle(
                    "agent-tabs",
                    mapOf("consentGeneration" to generation),
                    fromGateway = true,
                )
            return toolResult(id, outcome, status != 200 || outcome["success"] != true)
        }
        val operation =
            if (actionTool || controlMutation) {
                R5PrivateMcpActions.operation(action)
            } else {
                when (action) {
                    "tab.snapshot" -> "READ_DOM"
                    "tab.screenshot" -> "SCREENSHOT"
                    else -> null
                }
            } ?: return rpcError(id, -32601, "Unsupported R5 browser operation")
        val tab = (arguments["tabId"] as? String) ?: (actionParams["tabId"] as? String)
        val epoch = actionParams["documentEpoch"] as? String
        val commandId = actionParams["commandId"] as? String
        val sequence = (actionParams["clientSequence"] as? Number)?.toLong()
        if (tab.isNullOrBlank() || epoch.isNullOrBlank() || commandId.isNullOrBlank() || sequence == null || sequence <= 0L) {
            return rpcError(id, -32602, "Observed tab/epoch, commandId and monotonic clientSequence required")
        }
        val request =
            mutableMapOf<String, Any?>(
                "operation" to operation,
                "tabId" to tab,
                "documentEpoch" to epoch,
                "commandId" to commandId,
                "clientSequence" to sequence,
                "consentGeneration" to generation,
            )
        if (actionTool || controlMutation) {
            val extra =
                R5PrivateMcpActions.safeArguments(action, actionParams)
                    ?: return rpcError(id, -32602, "Mutation input missing or invalid")
            request.putAll(extra)
        }
        if (operation == "SCREENSHOT" && actionParams["capture"] != null) {
            request["capture"] = actionParams["capture"]
        }
        val (status, outcome) = router.handle("agent-command", request, fromGateway = true)
        if (status != 200 || outcome["status"] != "EXECUTED") {
            return toolResult(id, outcome, isError = true)
        }
        if (actionTool || controlMutation) return toolResult(id, outcome)
        val payload = outcome["data"] as? Map<*, *> ?: return toolResult(id, mapOf("error" to "NO_EVIDENCE"), true)
        if (operation == "READ_DOM") {
            val snapshot = payload["snapshotJson"] as? String ?: return toolResult(id, mapOf("error" to "NO_DOM"), true)
            return rpcResult(id, complete(listOf(mapOf("type" to "text", "text" to snapshot)), false))
        }
        val encoded = payload["image"] as? String ?: return toolResult(id, mapOf("error" to "NO_IMAGE"), true)
        if (encoded.length > MAX_IMAGE_B64) {
            return toolResult(id, mapOf("error" to "IMAGE_TOO_LARGE", "hint" to "Use AUTO overview capture"), true)
        }
        val metadata = payload.filterKeys { it != "image" }.mapKeys { it.key.toString() }
        return rpcResult(
            id,
            complete(
                listOf(
                    mapOf("type" to "image", "data" to encoded, "mimeType" to "image/png"),
                    mapOf("type" to "text", "text" to stringify(metadata)),
                ),
                false,
            ),
        )
    }

    private fun toolResult(
        id: Any,
        value: Map<String, Any?>,
        isError: Boolean = false,
    ): Map<String, Any?> = rpcResult(id, complete(listOf(mapOf("type" to "text", "text" to stringify(value))), isError))

    private fun complete(
        content: List<Map<String, Any?>>,
        isError: Boolean,
    ): Map<String, Any?> = mapOf("resultType" to "complete", "content" to content, "isError" to isError)

    private fun rpcResult(
        id: Any,
        result: Map<String, Any?>,
    ): Map<String, Any?> = mapOf("jsonrpc" to "2.0", "id" to id, "result" to result)

    private fun rpcError(
        id: Any?,
        code: Int,
        message: String,
    ): Map<String, Any?> = mapOf("jsonrpc" to "2.0", "id" to id, "error" to mapOf("code" to code, "message" to message))

    private fun stringify(value: Any?): String =
        when (value) {
            null -> "null"
            is String -> JsonPrimitive(value).toString()
            is Number -> JsonPrimitive(value).toString()
            is Boolean -> JsonPrimitive(value).toString()
            is Map<*, *> ->
                value.entries.joinToString(prefix = "{", postfix = "}") {
                    JsonPrimitive(it.key.toString()).toString() + ":" + stringify(it.value)
                }
            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { stringify(it) }
            else -> JsonPrimitive(value.toString()).toString()
        }

    private companion object {
        const val MAX_IMAGE_B64 = 7_500_000
    }
}
