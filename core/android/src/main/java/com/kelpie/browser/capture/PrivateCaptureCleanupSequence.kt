package com.kelpie.browser.capture

/**
 * Best-effort, idempotent-by-caller cleanup runner. Native resource references
 * are nulled by the renderer BEFORE this runner invokes their release action.
 */
internal class PrivateCaptureCleanupSequence {
    var failed: Boolean = false
        private set

    fun release(action: () -> Unit) {
        try {
            action()
        } catch (_: Exception) {
            failed = true
        }
    }
}
