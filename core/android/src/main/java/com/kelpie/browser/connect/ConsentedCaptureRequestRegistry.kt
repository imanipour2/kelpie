package com.kelpie.browser.connect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/**
 * Holds a real paired client's request until the person approves it in Kelpie.
 *
 * Never obtains authority from unverified JSON. The transport must supply the
 * client ID returned by its authenticated middleware, not a request field.
 * This registry intentionally does not render, encode, send or persist images.
 */
class ConsentedCaptureRequestRegistry(
    private val clock: () -> Long,
    private val random: SecureRandom = SecureRandom(),
) {
    data class Prompt(
        val requestId: String,
        val clientId: String,
        val attemptId: String,
        val tabId: String,
        val documentVersion: String,
        val expiresAtMs: Long,
    )

    private data class Pending(val prompt: Prompt, val target: Any)
    private data class Approved(
        val requestId: String,
        val binding: ConsentedCaptureLease.Binding,
        val target: Any,
        val localToken: String,
    )

    private val lease = ConsentedCaptureLease(clock, random)
    private val mutablePrompt = MutableStateFlow<Prompt?>(null)
    val prompt = mutablePrompt.asStateFlow()
    private var pending: Pending? = null
    private var approved: Approved? = null

    /**
     * Only an authenticated HTTP/MCP gateway may invoke this method; it must
     * resolve the tab/document/actual WebView from the live TabStore.
     */
    @Synchronized
    fun submitAuthenticated(
        verifiedClientId: String?,
        attemptId: String,
        tabId: String,
        documentVersion: String,
        view: Any,
    ): Prompt? {
        if (pending != null || approved != null || verifiedClientId.isNullOrBlank()) return null
        if (!validId(verifiedClientId) || !validId(attemptId) || !validId(tabId) || !validId(documentVersion)) return null
        val now = clock()
        if (now < 0 || now > Long.MAX_VALUE - PENDING_TTL_MS) return null
        val id = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val request = Prompt(id, verifiedClientId, attemptId, tabId, documentVersion, now + PENDING_TTL_MS)
        pending = Pending(request, view)
        mutablePrompt.value = request
        return request
    }

    /** Only the native local dialog's affirmative callback may call this. */
    @Synchronized
    fun approveOnDevice(
        requestId: String,
        currentTabId: String,
        currentDocumentVersion: String,
        currentView: Any,
        unlocked: Boolean,
    ): Boolean {
        val p = pending
        if (p == null || p.prompt.requestId != requestId || clock() >= p.prompt.expiresAtMs ||
            !unlocked || p.prompt.tabId != currentTabId || p.prompt.documentVersion != currentDocumentVersion ||
            p.target !== currentView
        ) {
            revoke()
            return false
        }
        val binding =
            ConsentedCaptureLease.Binding(
                p.prompt.tabId,
                p.prompt.documentVersion,
                p.prompt.clientId,
                p.prompt.attemptId,
            )
        val token = lease.approveLocally(binding, currentView) ?: run {
            revoke()
            return false
        }
        approved = Approved(requestId, binding, currentView, token)
        pending = null
        mutablePrompt.value = null
        return true
    }

    /** A remote request never sees or controls the locally generated lease. */
    @Synchronized
    fun claimFrame(
        requestId: String,
        verifiedClientId: String?,
        attemptId: String,
        currentTabId: String,
        currentDocumentVersion: String,
        currentView: Any,
        unlocked: Boolean,
        userStopped: Boolean,
    ): Boolean {
        val a = approved
        if (a == null || a.requestId != requestId || verifiedClientId != a.binding.clientId ||
            attemptId != a.binding.agentAttemptId
        ) {
            revoke()
            return false
        }
        val binding =
            ConsentedCaptureLease.Binding(currentTabId, currentDocumentVersion, verifiedClientId, attemptId)
        val valid = lease.claimFrame(a.localToken, binding, currentView, true, unlocked, userStopped)
        if (!valid) revoke()
        return valid
    }

    @Synchronized
    fun revoke() {
        pending = null
        approved = null
        mutablePrompt.value = null
        lease.revoke()
    }

    private fun validId(text: String): Boolean = text.isNotBlank() && text.length <= MAX_ID_LENGTH && text.none { it.isISOControl() }

    companion object {
        const val PENDING_TTL_MS = 30_000L
        const val MAX_ID_LENGTH = 128
    }
}
