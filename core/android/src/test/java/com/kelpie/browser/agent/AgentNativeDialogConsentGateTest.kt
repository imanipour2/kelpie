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
    @Test
    fun allSixteenForegroundSafetyCombinationsAreChecked() {
        for (bits in 0 until 16) {
            val resumed = bits and 1 != 0
            val visible = bits and 2 != 0
            val unlocked = bits and 4 != 0
            val stopVisible = bits and 8 != 0
            val accepted = nativeConsentTapAllowed(resumed, visible, unlocked, stopVisible)
            if (bits == 15) {
                assertTrue("bits=$bits", accepted)
            } else {
                assertFalse("bits=$bits", accepted)
            }
        }
    }

}
