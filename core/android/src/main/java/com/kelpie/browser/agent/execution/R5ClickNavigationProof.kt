package com.kelpie.browser.agent.execution

/**
 * Conservative proof for an already-dispatched click that triggered navigation.
 * Two consecutive native observations of the same changed URL/document epoch
 * are required. No DOM script callback, remote assertion, or transient state
 * by itself counts as a verified navigation effect.
 */
internal class R5ClickNavigationProof(
    private val originalUrl: String,
    private val originalEpoch: String,
) {
    private var previousChange: Pair<String, String>? = null

    fun observedStableChange(
        currentUrl: String,
        currentEpoch: String,
    ): Boolean {
        if (currentUrl.isBlank() || currentEpoch.isBlank()) {
            previousChange = null
            return false
        }
        val changed = currentUrl != originalUrl || currentEpoch != originalEpoch
        if (!changed) {
            previousChange = null
            return false
        }
        val observation = currentUrl to currentEpoch
        val stable = observation == previousChange
        previousChange = observation
        return stable
    }
}
