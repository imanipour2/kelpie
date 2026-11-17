package com.kelpie.browser.network

/** Verify the private R5 QA HTTP MCP 2026 envelope before any native route. */
internal object R5McpWireAdmission {
    const val MODERN = "2026-07-28"
    const val LEGACY = "2025-11-25"
    val SUPPORTED = listOf(MODERN, LEGACY)

    data class Failure(
        val code: Int,
        val message: String,
        val requested: String?,
    )

    fun errorBody(
        id: Any?,
        failure: Failure,
    ): Map<String, Any?> =
        mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "error" to
                mapOf(
                    "code" to failure.code,
                    "message" to failure.message,
                    "data" to
                        mapOf(
                            "supported" to SUPPORTED,
                            "requested" to failure.requested,
                        ),
                ),
        )

    fun check(
        body: Map<String, Any?>,
        httpVersion: String?,
        httpMethod: String?,
        httpName: String?,
    ): Failure? {
        val params = body["params"] as? Map<*, *>
        val metadata = params?.get("_meta") as? Map<*, *>
        val declared = metadata?.get("io.modelcontextprotocol/protocolVersion") as? String
        if (httpVersion == null) {
            // Legacy clients establish a handshake; modern callers MUST send
            // matching version/method headers and complete request metadata.
            if (declared == MODERN) return Failure(-32600, "Modern MCP version header missing", declared)
            return null
        }
        if (httpVersion !in SUPPORTED) return Failure(-32022, "Unsupported protocol version", httpVersion)
        if (httpVersion == LEGACY) {
            if (declared == MODERN) return Failure(-32600, "Mismatched legacy MCP envelope", declared)
            return null
        }
        if (declared == null) return Failure(-32602, "Required modern MCP protocolVersion metadata missing", null)
        if (declared != MODERN) return Failure(-32600, "Modern MCP metadata version mismatch", declared)
        val infoSupplied = metadata?.containsKey("io.modelcontextprotocol/clientInfo") == true
        val identity = metadata?.get("io.modelcontextprotocol/clientInfo") as? Map<*, *>
        val capability = metadata?.get("io.modelcontextprotocol/clientCapabilities") as? Map<*, *>
        val badIdentity =
            infoSupplied &&
                ((identity?.get("name") as? String).isNullOrBlank() ||
                    (identity?.get("version") as? String).isNullOrBlank())
        if (capability == null || badIdentity) {
            return Failure(-32602, "Modern MCP client metadata invalid or incomplete", declared)
        }
        if (body["method"] != httpMethod) return Failure(-32600, "Mcp-Method disagrees with JSON-RPC method", declared)
        if (body["method"] == "tools/call" && params?.get("name") != httpName) {
            return Failure(-32600, "Mcp-Name disagrees with selected tool", declared)
        }
        return null
    }
}
