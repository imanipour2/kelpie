package com.kelpie.browser.agent.execution

import com.kelpie.browser.agent.render.WebViewOwnershipState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5UnlockedForegroundGateTest {
    private fun allow(
        consentActive: Boolean = true,
        sameGeneration: Boolean = true,
        owner: WebViewOwnershipState.Host = WebViewOwnershipState.Host.SUSPENDED,
        privateDisplayIdle: Boolean = true,
        resumed: Boolean = true,
        windowVisible: Boolean = true,
        windowFocused: Boolean = true,
        attached: Boolean = true,
        shown: Boolean = true,
        interactive: Boolean = true,
        keyguardGone: Boolean = true,
        stopNotificationPresent: Boolean = true,
    ) = R5UnlockedForegroundGate.canRebind(
        consentActive,
        sameGeneration,
        owner,
        privateDisplayIdle,
        resumed,
        windowVisible,
        windowFocused,
        attached,
        shown,
        interactive,
        keyguardGone,
        stopNotificationPresent,
    )

    @Test
    fun physicallyUnlockedOriginalNativeGrantMayRebindOnlyItsVisibleWebView() {
        assertTrue(allow())
        assertFalse(allow(consentActive = false))
        assertFalse(allow(sameGeneration = false))
        assertFalse(allow(owner = WebViewOwnershipState.Host.FAILED))
        assertFalse(allow(owner = WebViewOwnershipState.Host.CLOSED))
        assertFalse(allow(owner = WebViewOwnershipState.Host.ABSENT))
        assertFalse(allow(privateDisplayIdle = false))
    }

    @Test
    fun backgroundOrLockedOrHiddenDisplayCannotBeRebound() {
        assertFalse(allow(resumed = false))
        assertFalse(allow(windowVisible = false))
        assertFalse(allow(windowFocused = false))
        assertFalse(allow(attached = false))
        assertFalse(allow(shown = false))
        assertFalse(allow(interactive = false))
        assertFalse(allow(keyguardGone = false))
        assertFalse(allow(stopNotificationPresent = false))
    }
}
