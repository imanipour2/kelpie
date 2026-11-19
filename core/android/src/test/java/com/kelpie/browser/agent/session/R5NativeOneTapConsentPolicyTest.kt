package com.kelpie.browser.agent.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5NativeOneTapConsentPolicyTest {
    private fun allowed(
        intended: String? = "paired-client-1",
        observed: String? = "paired-client-1",
        resumed: Boolean = true,
        visible: Boolean = true,
        unlocked: Boolean = true,
        stopVisible: Boolean = true,
    ) = nativeOneTapConsentAllowed(
        intendedPrincipal = intended,
        freshUniquePrincipal = observed,
        lifecycleResumed = resumed,
        activityVisible = visible,
        deviceUnlocked = unlocked,
        stopNotificationEnabled = stopVisible,
    )

    @Test
    fun typedDenialsExplainEachFailedNativeGateWithoutChangingAuthorization() {
        fun reason(
            principal: String? = "paired-client-1",
            observed: String? = "paired-client-1",
            resumed: Boolean = true,
            visible: Boolean = true,
            unlocked: Boolean = true,
            stopEnabled: Boolean = true,
        ) = nativeOneTapRejection(principal, observed, resumed, visible, unlocked, stopEnabled)

        assertEquals(null, reason())
        assertEquals(NativeOneTapResult.INVALID_CLIENT, reason(principal = "localhost"))
        assertEquals(NativeOneTapResult.CLIENT_EXPIRED, reason(observed = null))
        assertEquals(NativeOneTapResult.CLIENT_CHANGED, reason(observed = "paired-client-2"))
        assertEquals(NativeOneTapResult.ACTIVITY_NOT_RESUMED, reason(resumed = false))
        assertEquals(NativeOneTapResult.ACTIVITY_NOT_VISIBLE, reason(visible = false))
        assertEquals(NativeOneTapResult.DEVICE_LOCKED, reason(unlocked = false))
        assertEquals(NativeOneTapResult.STOP_NOTIFICATION_UNAVAILABLE, reason(stopEnabled = false))
        assertTrue(allowed())
        assertFalse(allowed(observed = null))
    }

    @Test
    fun allowsOneFreshVerifiedPrincipalWithAllNativeOwnerGates() {
        assertTrue(allowed())
    }

    @Test
    fun expiredAndSwitchedPrincipalCannotUseAnOldOwnerTap() {
        assertFalse(allowed(observed = null))
        assertFalse(allowed(intended = "paired-client-1", observed = "paired-client-2"))
        assertFalse(allowed(intended = null))
        assertFalse(allowed(intended = "localhost", observed = "localhost"))
        assertFalse(allowed(intended = " "))
    }

    @Test
    fun failsClosedOnLockHiddenActivityOrMissingStopNotification() {
        assertFalse(allowed(resumed = false))
        assertFalse(allowed(visible = false))
        assertFalse(allowed(unlocked = false))
        assertFalse(allowed(stopVisible = false))
    }

    @Test
    fun duplicateConsentIsBlockedByTheUnderlyingProcessStateCore() {
        var now = 100L
        val core = SessionConsentCore({ now })
        val pending = core.requestNativeConsent("paired-client-1")
        assertTrue(pending != null)
        val active = core.approveFromNativeUi(pending!!.promptId, true)
        assertTrue(active != null)
        assertTrue(core.requestNativeConsent("paired-client-1") == null)
        core.stop()
        assertTrue(core.requestNativeConsent("paired-client-1") != null)
    }

    @Test
    fun aStalePromptCannotApproveAfterItsDeadline() {
        var now = 0L
        val core = SessionConsentCore({ now })
        val request = core.requestNativeConsent("paired-client-1")!!
        now = SessionConsentCore.LOCAL_PROMPT_TTL_MS
        assertTrue(core.approveFromNativeUi(request.promptId, true) == null)
        assertTrue(core.snapshot() == SessionConsentCore.State.Off)
    }
}
