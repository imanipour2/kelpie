package com.kelpie.browser.agent.session

import android.content.Context

/**
 * Remembers the owner's background-capability startup choice for this install.
 *
 * This setting is NOT an AI-agent grant, computer pairing, native session,
 * privileged background-execution permission, or a replacement for STOP.
 * Execution epochs remain process-local and bound to authenticated identity.
 */
internal object R5BackgroundOptInStore {
    private const val PREFS_NAME = "kelpie_r5_background_startup_opt_in"
    private const val APPROVED_KEY = "owner_approved"
    private const val CONTROL_PAUSED_KEY = "computer_control_paused"

    /** Explicit STOP is durable; network and renderer failures never write it. */
    fun isControlPaused(context: Context): Boolean =
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(CONTROL_PAUSED_KEY, false)
        }.getOrDefault(true)

    fun setControlPaused(
        context: Context,
        paused: Boolean,
    ): Boolean =
        runCatching {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(CONTROL_PAUSED_KEY, paused)
                .commit()
        }.getOrDefault(false)

    fun isApproved(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(APPROVED_KEY, false)

    /** Synchronous commit: never skip the owner's first-entry choice on write failure. */
    fun rememberApproval(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(APPROVED_KEY, true)
            .commit()

    /** Only resets the startup choice; never grants or renews remote browser access. */
    fun askAgainNextLaunch(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(APPROVED_KEY)
            .commit()
}

/** Pure policy seam for QA testing; no permission is inferred from network reachability. */
internal fun shouldPromptForR5BackgroundOptIn(
    backgroundFeatureRequiresOptIn: Boolean,
    previouslyApproved: Boolean,
): Boolean = backgroundFeatureRequiresOptIn && !previouslyApproved
