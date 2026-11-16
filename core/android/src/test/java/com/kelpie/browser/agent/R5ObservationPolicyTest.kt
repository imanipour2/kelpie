package com.kelpie.browser.agent

import com.kelpie.browser.agent.execution.R5ObservationPolicy
import com.kelpie.browser.agent.session.SessionConsentCore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5ObservationPolicyTest {
    @Test
    fun onlyExactlyPairedActiveUnlockedVisibleNotifiedSessionMayObserve() {
        val consent = SessionConsentCore({ 10L })
        val pending = consent.requestNativeConsent("paired-owner")!!
        val active = consent.approveFromNativeUi(pending.promptId, deviceUnlocked = true)!!
        assertTrue(
            R5ObservationPolicy.permits(
                active,
                "paired-owner",
                active.generation,
                unlocked = true,
                activityResumed = true,
                notificationVisible = true,
            ),
        )
        for (bits in 0 until 8) {
            val permitted =
                R5ObservationPolicy.permits(
                    active,
                    "paired-owner",
                    active.generation,
                    unlocked = bits and 1 != 0,
                    activityResumed = bits and 2 != 0,
                    notificationVisible = bits and 4 != 0,
                )
            if (bits == 7) assertTrue(permitted) else assertFalse(permitted)
        }
        assertFalse(R5ObservationPolicy.permits(active, "second-client", active.generation, true, true, true))
        assertFalse(R5ObservationPolicy.permits(active, "paired-owner", "stale-generation", true, true, true))
        assertFalse(R5ObservationPolicy.permits(active, null, active.generation, true, true, true))
        consent.setDeviceLocked(true)
        assertFalse(R5ObservationPolicy.permits(consent.snapshot(), "paired-owner", active.generation, true, true, true))
        consent.stop()
        assertFalse(R5ObservationPolicy.permits(consent.snapshot(), "paired-owner", active.generation, true, true, true))
    }

    @Test
    fun documentEpochAndTabMustMatchExactLiveRenderer() {
        assertTrue(R5ObservationPolicy.sameTarget("tab-1", "epoch-2", "tab-1", "epoch-2"))
        assertFalse(R5ObservationPolicy.sameTarget("tab-1", "epoch-1", "tab-1", "epoch-2"))
        assertFalse(R5ObservationPolicy.sameTarget("tab-1", "epoch-2", "tab-2", "epoch-2"))
        assertFalse(R5ObservationPolicy.sameTarget("", "epoch-2", "tab-1", "epoch-2"))
        assertFalse(R5ObservationPolicy.sameTarget("tab-1", null, "tab-1", "epoch-2"))
    }
}
