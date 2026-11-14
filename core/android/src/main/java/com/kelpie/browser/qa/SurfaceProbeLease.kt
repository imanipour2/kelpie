package com.kelpie.browser.qa

/**
 * Pure single-tab / single-document, finite-lived QA probe.
 * Only a local, on-screen affirmative Android dialog may call begin().
 * This is NOT a remote screenshot capability or a persistent consent token.
 */
class SurfaceProbeLease(
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = 90_000L,
) {
    private data class Pin(
        val tabId: String,
        val documentVersion: String,
        val expiresAt: Long,
    )

    private var pin: Pin? = null
    private var nextSequence = 1

    fun begin(
        tabId: String,
        documentVersion: String,
    ): Boolean {
        if (tabId.isBlank() || documentVersion.isBlank() || pin != null) return false
        require(ttlMs in 1L..120_000L)
        pin = Pin(tabId, documentVersion, now() + ttlMs)
        nextSequence = 1
        return true
    }

    fun allows(
        tabId: String,
        documentVersion: String,
    ): Boolean {
        val current = pin ?: return false
        if (now() >= current.expiresAt) return false
        return current.tabId == tabId && current.documentVersion == documentVersion
    }

    fun claim(
        sequence: Int,
        tabId: String,
        documentVersion: String,
    ): Boolean {
        if (!allows(tabId, documentVersion) || sequence !in 1..21 || sequence != nextSequence) return false
        nextSequence++
        return true
    }

    fun completed(): Boolean = nextSequence == 22

    fun cancel() {
        pin = null
        nextSequence = 1
    }
}
