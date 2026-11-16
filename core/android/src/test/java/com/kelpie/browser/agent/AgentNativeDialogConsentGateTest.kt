package com.kelpie.browser.agent

import com.kelpie.browser.agent.session.nativeConsentTapAllowed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dialog owns focus; native onClick must not depend on the parent Activity window focus. */
class AgentNativeDialogConsentGateTest {
    @Test
    fun genuineVisibleResumedUnlockedNativeTapCanPassWhenStopIsVisible() {
        assertTrue(
            nativeConsentTapAllowed(
                lifecycleResumed = true,
                activityVisible = true,
                deviceUnlocked = true,
                stopNotificationEnabled = true,
            ),
        )
    }

    @Test
    fun backgroundLockedOrInvisibleStopCannotActivateConsent() {
        assertFalse(nativeConsentTapAllowed(false, true, true, true))
        assertFalse(nativeConsentTapAllowed(true, false, true, true))
        assertFalse(nativeConsentTapAllowed(true, true, false, true))
        assertFalse(nativeConsentTapAllowed(true, true, true, false))
    }
}
