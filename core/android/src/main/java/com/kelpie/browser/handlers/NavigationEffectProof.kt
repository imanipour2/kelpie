package com.kelpie.browser.handlers

/** Fixed native/engine observations of one already admitted navigation. No request data can attest success. */
internal class NavigationEffectProof(
    private val action: Action,
    private val before: Observation,
    private val requestedUrl: String? = null,
) {
    enum class Action { NAVIGATE, BACK, FORWARD, RELOAD }

    data class Observation(
        val documentIdentity: String,
        val url: String,
        val historyIndex: Int,
        val historySize: Int,
        val loading: Boolean,
        val complete: Boolean,
        val engineTimeOrigin: Double?,
    )

    private var candidate: Observation? = null
    private var candidateAt = 0L

    /** No history mutation is dispatched when the native destination does not exist. */
    fun refusalBeforeDispatch(): String? =
        when {
            before.documentIdentity.isBlank() -> "NO_DOCUMENT_EVIDENCE"
            before.url.isNotBlank() && !validEngineTime(before.engineTimeOrigin) -> "NO_DOCUMENT_EVIDENCE"
            before.loading -> "NAVIGATION_NOT_READY"
            !validHistory(before) -> "NO_HISTORY_EVIDENCE"
            action == Action.BACK && before.historyIndex <= 0 -> "NO_BACK_HISTORY"
            action == Action.FORWARD && (before.historyIndex < 0 || before.historyIndex + 1 >= before.historySize) -> "NO_FORWARD_HISTORY"
            action == Action.RELOAD && before.url.isBlank() -> "NO_DOCUMENT_EVIDENCE"
            action == Action.NAVIGATE && requestedUrl.isNullOrBlank() -> "INVALID_URL"
            else -> null
        }

    /** Two complete, nonloading observations must retain the same native history and document. */
    fun observed(
        current: Observation,
        monotonicMs: Long,
    ): Boolean {
        if (monotonicMs < 0 || !qualifies(current)) {
            candidate = null
            return false
        }
        if (candidate != current || monotonicMs < candidateAt) {
            candidate = current
            candidateAt = monotonicMs
            return false
        }
        return monotonicMs - candidateAt >= MIN_STABLE_MS
    }

    private fun qualifies(current: Observation): Boolean {
        if (refusalBeforeDispatch() != null ||
            current.documentIdentity.isBlank() ||
            current.url.isBlank() ||
            current.loading ||
            !current.complete ||
            !validHistory(current) ||
            !validEngineTime(current.engineTimeOrigin)
        ) {
            return false
        }
        // onPageStarted rotates the native epoch before a new DOM has committed.
        // The old complete document cannot attest a replacement by that epoch alone.
        val replaced = current.documentIdentity != before.documentIdentity && current.engineTimeOrigin != before.engineTimeOrigin
        return when (action) {
            Action.BACK -> current.historyIndex == before.historyIndex - 1 && current.historyIndex in 0 until current.historySize
            Action.FORWARD -> current.historyIndex == before.historyIndex + 1 && current.historyIndex in 0 until current.historySize
            Action.RELOAD -> replaced && current.historyIndex == before.historyIndex
            Action.NAVIGATE ->
                replaced ||
                    (current.url == requestedUrl && before.url != requestedUrl && before.url.substringBefore('#') == requestedUrl?.substringBefore('#'))
        }
    }

    private fun validHistory(value: Observation): Boolean =
        (value.historySize == 0 && value.historyIndex == -1) ||
            (value.historySize > 0 && value.historyIndex in 0 until value.historySize)

    private fun validEngineTime(value: Double?): Boolean = value != null && value.isFinite() && value > 0

    companion object {
        const val MIN_STABLE_MS = 100L
    }
}
