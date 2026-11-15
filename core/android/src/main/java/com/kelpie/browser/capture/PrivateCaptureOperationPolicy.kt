package com.kelpie.browser.capture

/**
 * Shared QA-only admission policy for an app-owned WebView transferred to P4.
 * Pairing never upgrades ordinary DOM, screenshot or click permissions.
 * The outer MCP/browser-control envelope may enter, but every inner operation
 * must be admitted again by the gateway/Router before touching a WebView.
 */
internal object PrivateCaptureOperationPolicy {
    private val captureMethods =
        setOf("capture-session-frame", "capture-session-status", "capture-session-stop")

    fun permits(
        method: String,
        privateOwnerActive: Boolean,
        outerEnvelope: Boolean = false,
    ): Boolean =
        !privateOwnerActive ||
            method in captureMethods ||
            (outerEnvelope && method in setOf("mcp", "browser-control"))

    /** Suppress any pre-consent result that finishes after private ownership starts. */
    fun permitsDelivery(
        method: String,
        privateAtAdmission: Boolean,
        privateAtDelivery: Boolean,
    ): Boolean =
        (!privateAtDelivery || privateAtAdmission) &&
            permits(method, privateAtDelivery, outerEnvelope = privateAtAdmission)
}
