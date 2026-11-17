package com.kelpie.browser.agent.execution

import com.kelpie.browser.agent.render.WebViewOwnershipState

/**
 * Unlock is not consent. Only a still-active, same-generation native owner grant
 * can rebind the previously suspended, now visibly attached app WebView.
 */
internal object R5UnlockedForegroundGate {
    fun canRebind(
        consentActive: Boolean,
        sameGeneration: Boolean,
        owner: WebViewOwnershipState.Host,
        privateDisplayIdle: Boolean,
        resumed: Boolean,
        windowVisible: Boolean,
        windowFocused: Boolean,
        attached: Boolean,
        shown: Boolean,
        interactive: Boolean,
        keyguardGone: Boolean,
        stopNotificationPresent: Boolean,
    ): Boolean =
        consentActive &&
            sameGeneration &&
            owner == WebViewOwnershipState.Host.SUSPENDED &&
            privateDisplayIdle &&
            resumed &&
            windowVisible &&
            windowFocused &&
            attached &&
            shown &&
            interactive &&
            keyguardGone &&
            stopNotificationPresent
}
