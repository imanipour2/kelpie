package com.kelpie.browser.agent

import com.kelpie.browser.agent.session.stopChannelVisible
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No consent may be granted if the STOP notification channel or group is blocked. */
class AgentNotificationChannelPolicyTest {
    @Test
    fun disabledStopChannelFailsClosedEvenWhenAppNotificationsAreAllowed() {
        assertFalse(stopChannelVisible(importance = 0, groupBlocked = false))
        assertFalse(stopChannelVisible(importance = 2, groupBlocked = true))
        assertFalse(stopChannelVisible(importance = null, groupBlocked = true))
    }

    @Test
    fun newOrVisibleChannelCanProceedToNotificationCreation() {
        assertTrue(stopChannelVisible(importance = null, groupBlocked = false))
        assertTrue(stopChannelVisible(importance = 2, groupBlocked = false))
    }
}
