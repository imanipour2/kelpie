package com.kelpie.browser.network

/** Captures a renderer once at the final dispatch boundary and refuses results after a visible-tab change. */
class BrowserGatewayDispatch<T>(
    private val active: () -> String,
    private val renderer: () -> T?,
) {
    suspend fun execute(
        method: String,
        params: Map<String, Any?>,
        pinned: String?,
        operation: suspend (T?) -> Map<String, Any?>,
    ): Map<String, Any?> {
        val admitted = active()
        if (!BrowserGatewayTabPolicy.permitsDispatch(method, params, pinned, admitted)) {
            return errorResponse("TAB_CHANGED", "The requested tab is no longer active")
        }
        val management = method in setOf("switch-tab", "close-tab", "new-tab", "delete-partition")
        val requiresRenderer = requiresRenderer(method)
        val result = operation(if (management || !requiresRenderer) null else renderer())
        return if (!management && requiresRenderer && active() != admitted) errorResponse("TAB_CHANGED", "The requested tab changed during dispatch") else result
    }

    companion object {
        /** Native device metadata never reads a page. Every other method keeps its renderer requirements. */
        internal fun requiresRenderer(method: String): Boolean = method != "get-device-info"
    }
}
