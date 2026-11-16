package com.kelpie.browser.capture

import java.net.URI

/**
 * Pure, currently UNWIRED research policy for a separately consented locked-screen task.
 *
 * This class deliberately has no HTTP route, Android receiver, or UI integration.
 * Only a future trusted native approval callback may invoke [approveLocally].
 * Existing P4 behavior must continue revoking at screen-off by default.
 */
internal class LockedTaskConsentLease(
    private val elapsedMs: () -> Long,
) {
    internal enum class Action {
        SNAPSHOT,
        FRESH_FRAME,
        CLICK,
        TYPE,
        SCROLL,
        NAVIGATE,
        PUBLISH,
        DELETE,
        CREDENTIALS,
        PAYMENT,
    }

    internal data class Binding(
        val clientId: String,
        val attemptId: String,
        val tabId: String,
        val documentVersion: String,
        val tabUrl: String,
        val webView: Any,
    )

    private data class Grant(
        val binding: Binding,
        val expiresAtMs: Long,
        val allowsLocked: Boolean,
        var operationsLeft: Int,
    )

    private var grant: Grant? = null

    /** Must be called only by a real native affirmative gesture, not request JSON. */
    @Synchronized
    fun approveLocally(
        binding: Binding,
        explicitLockedPermission: Boolean,
        durationMs: Long = MAX_DURATION_MS,
        maxOperations: Int = MAX_OPERATIONS,
    ): Boolean {
        revoke()
        if (!validBinding(binding) || durationMs !in 1..MAX_DURATION_MS || maxOperations !in 1..MAX_OPERATIONS) {
            return false
        }
        val now = elapsedMs()
        if (now < 0 || now > Long.MAX_VALUE - durationMs) return false
        grant = Grant(binding, now + durationMs, explicitLockedPermission, maxOperations)
        return true
    }

    /**
     * Admission is scoped to the exact authenticated principal and original WebView.
     * A foreign principal cannot steal or revoke another client's active grant.
     * The eventual gateway MUST also revalidate after every suspending operation.
     */
    @Synchronized
    fun claim(
        action: Action,
        clientId: String?,
        attemptId: String,
        tabId: String,
        documentVersion: String,
        tabUrl: String,
        actualWebViewUrl: String?,
        webView: Any,
        screenLocked: Boolean,
    ): Boolean {
        val current = grant ?: return false
        if (clientId != current.binding.clientId || attemptId != current.binding.attemptId) return false
        if (elapsedMs() >= current.expiresAtMs ||
            tabId != current.binding.tabId ||
            documentVersion != current.binding.documentVersion ||
            webView !== current.binding.webView ||
            !originPermitted(tabUrl) ||
            !originPermitted(actualWebViewUrl)
        ) {
            revoke()
            return false
        }
        if (screenLocked && !current.allowsLocked) {
            revoke()
            return false
        }
        if (action !in ALLOWED_ACTIONS || current.operationsLeft <= 0) return false
        current.operationsLeft--
        return true
    }

    @Synchronized
    fun revoke() {
        grant = null
    }

    @Synchronized
    fun activeForOwner(clientId: String?): Boolean {
        val current = grant ?: return false
        return current.binding.clientId == clientId && elapsedMs() < current.expiresAtMs
    }

    private fun validBinding(binding: Binding): Boolean {
        val identifiers = listOf(binding.clientId, binding.attemptId, binding.tabId, binding.documentVersion)
        val identifiersValid =
            identifiers.all { id ->
                id.isNotBlank() && id.length <= 128 && id.none { char -> char.isISOControl() }
            }
        return identifiersValid && originPermitted(binding.tabUrl)
    }

    private fun originPermitted(value: String?): Boolean {
        val uri = value?.let { runCatching { URI(it) }.getOrNull() } ?: return false
        return uri.scheme == "https" &&
            uri.host == "example.com" &&
            (uri.port == -1 || uri.port == 443) &&
            uri.rawUserInfo == null
    }

    private companion object {
        const val MAX_DURATION_MS = 90_000L
        const val MAX_OPERATIONS = 20
        val ALLOWED_ACTIONS = setOf(Action.SNAPSHOT, Action.FRESH_FRAME, Action.CLICK, Action.TYPE, Action.SCROLL)
    }
}
