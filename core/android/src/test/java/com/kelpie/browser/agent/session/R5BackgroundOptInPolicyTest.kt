package com.kelpie.browser.agent.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R5BackgroundOptInPolicyTest {
    @Test
    fun unapprovedBackgroundFeatureShowsOwnerChoice() {
        assertTrue(
            shouldPromptForR5BackgroundOptIn(
                backgroundFeatureRequiresOptIn = true,
                previouslyApproved = false,
            ),
        )
    }

    @Test
    fun ownerApprovedBackgroundFeatureSkipsRepeatPrompt() {
        assertFalse(
            shouldPromptForR5BackgroundOptIn(
                backgroundFeatureRequiresOptIn = true,
                previouslyApproved = true,
            ),
        )
    }

    @Test
    fun normalBrowserNeverRequiresR5StartupConsent() {
        assertFalse(
            shouldPromptForR5BackgroundOptIn(
                backgroundFeatureRequiresOptIn = false,
                previouslyApproved = false,
            ),
        )
    }

    @Test
    fun persistedStartupChoiceIsNotAnAgentSessionGrant() {
        val session = SessionConsentCore(elapsedRealtimeMs = { 12_000L })
        assertTrue(
            shouldPromptForR5BackgroundOptIn(
                backgroundFeatureRequiresOptIn = true,
                previouslyApproved = false,
            ),
        )
        assertTrue(session.snapshot() is SessionConsentCore.State.Off)
    }
}
