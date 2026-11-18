package com.kelpie.browser.agent.session

/**
 * First-entry background opt-in is a separate, strictly local choice.
 * It NEVER grants a principal, a SessionConsentCore generation, or OS permissions.
 */
internal class R5StartupGateState(
    private val required: Boolean,
) {
    private enum class State { WAITING, ALLOWED, DECLINED }

    private var state = if (required) State.WAITING else State.ALLOWED

    fun mayCreateBrowserSession(): Boolean = state == State.ALLOWED

    fun allow() {
        if (state != State.DECLINED) state = State.ALLOWED
    }

    fun decline() {
        state = State.DECLINED
    }
}
