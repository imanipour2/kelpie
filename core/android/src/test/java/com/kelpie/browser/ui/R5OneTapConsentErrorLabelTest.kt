package com.kelpie.browser.ui

import com.kelpie.browser.agent.session.NativeOneTapResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5OneTapConsentErrorLabelTest {
    @Test
    fun expiredClientShowsRecoveryStepWithoutApproving() {
        val message = nativeConsentErrorLabel(NativeOneTapResult.CLIENT_EXPIRED)
        assertTrue(message.contains("fresh MCP request"))
        assertTrue(message.contains("Refresh verified clients"))
    }

    @Test
    fun notificationGateExplainsVisibleStopRequirement() {
        val message = nativeConsentErrorLabel(NativeOneTapResult.STOP_NOTIFICATION_UNAVAILABLE)
        assertTrue(message.contains("STOP"))
        assertTrue(message.contains("notifications"))
    }

    @Test
    fun everyDeniedGateHasUsefulTextWithoutCredentialLeakage() {
        for (reason in NativeOneTapResult.entries) {
            val message = nativeConsentErrorLabel(reason)
            assertTrue("Missing explanation for ${reason.name}", message.length >= 20)
            assertFalse(message.contains("Bearer "))
            assertFalse(message.contains("Authorization:"))
            assertFalse(message.contains("http://"))
        }
    }
}
