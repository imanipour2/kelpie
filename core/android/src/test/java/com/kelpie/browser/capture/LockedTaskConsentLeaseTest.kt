package com.kelpie.browser.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedTaskConsentLeaseTest {
    private var clock = 1_000L
    private val lease = LockedTaskConsentLease({ clock })
    private val originalView = Any()
    private val binding =
        LockedTaskConsentLease.Binding(
            clientId = "paired-1",
            attemptId = "attempt-1",
            tabId = "tab-1",
            documentVersion = "doc-1",
            tabUrl = "https://example.com/",
            webView = originalView,
        )

    private fun claim(
        action: LockedTaskConsentLease.Action = LockedTaskConsentLease.Action.FRESH_FRAME,
        clientId: String = "paired-1",
        attemptId: String = "attempt-1",
        tabId: String = "tab-1",
        document: String = "doc-1",
        url: String = "https://example.com/",
        actualUrl: String? = "https://example.com/",
        view: Any = originalView,
        locked: Boolean = false,
    ): Boolean =
        lease.claim(
            action = action,
            clientId = clientId,
            attemptId = attemptId,
            tabId = tabId,
            documentVersion = document,
            tabUrl = url,
            actualWebViewUrl = actualUrl,
            webView = view,
            screenLocked = locked,
        )

    @Test
    fun neverAdmitsWithoutLocalApproval() {
        assertFalse(claim())
        assertFalse(claim(locked = true))
    }

    @Test
    fun defaultConsentRevokesOnLockAndNeverAutoRenews() {
        assertTrue(lease.approveLocally(binding, explicitLockedPermission = false))
        assertTrue(claim())
        assertFalse(claim(locked = true))
        assertFalse(claim())
    }

    @Test
    fun distinctLockedConsentPermitsOnlyOriginalOwnedView() {
        assertTrue(lease.approveLocally(binding, explicitLockedPermission = true))
        assertTrue(claim(locked = true))
        assertTrue(claim(action = LockedTaskConsentLease.Action.CLICK, locked = true))
        assertTrue(claim(action = LockedTaskConsentLease.Action.TYPE, locked = true))
        assertTrue(claim(action = LockedTaskConsentLease.Action.SCROLL, locked = true))
        assertTrue(claim(action = LockedTaskConsentLease.Action.SNAPSHOT, locked = true))
        assertFalse(claim(view = Any(), locked = true))
        assertFalse(claim(locked = true))
    }

    @Test
    fun foreignPrincipalOrAttemptCannotUseOrRevokeOwnerGrant() {
        assertTrue(lease.approveLocally(binding, explicitLockedPermission = true))
        assertFalse(claim(clientId = "attacker", locked = true))
        assertFalse(claim(attemptId = "other-attempt", locked = true))
        assertTrue(lease.activeForOwner("paired-1"))
        assertTrue(claim(locked = true))
    }

    @Test
    fun wrongTabDocumentOrRedirectRevokesSameClientGrant() {
        assertTrue(lease.approveLocally(binding, explicitLockedPermission = true))
        assertFalse(claim(tabId = "other-tab", locked = true))
        assertFalse(claim(locked = true))
        assertTrue(lease.approveLocally(binding, explicitLockedPermission = true))
        assertFalse(claim(document = "doc-2"))
        assertTrue(lease.approveLocally(binding, explicitLockedPermission = true))
        assertFalse(claim(actualUrl = "https://example.com.evil.test/"))
        assertTrue(lease.approveLocally(binding, explicitLockedPermission = true))
        assertFalse(claim(url = "https://example.com:8443/"))
        assertTrue(lease.approveLocally(binding, explicitLockedPermission = true))
        assertFalse(claim(actualUrl = "https://user:pass@example.com/"))
    }

    @Test
    fun neverAdmitsSensitiveOrUnreviewedNavigationActions() {
        assertTrue(lease.approveLocally(binding, explicitLockedPermission = true))
        val blocked =
            listOf(
                LockedTaskConsentLease.Action.NAVIGATE,
                LockedTaskConsentLease.Action.PUBLISH,
                LockedTaskConsentLease.Action.DELETE,
                LockedTaskConsentLease.Action.CREDENTIALS,
                LockedTaskConsentLease.Action.PAYMENT,
            )
        blocked.forEach { assertFalse(claim(action = it, locked = true)) }
        assertTrue(claim(locked = true))
    }

    @Test
    fun finiteLeaseBudgetAndMonotonicExpiry() {
        assertTrue(lease.approveLocally(binding, explicitLockedPermission = true, maxOperations = 2))
        assertTrue(claim(locked = true))
        assertTrue(claim(locked = true))
        assertFalse(claim(locked = true))
        lease.revoke()
        assertFalse(claim())
        assertTrue(lease.approveLocally(binding, explicitLockedPermission = true))
        clock += 90_000L
        assertFalse(claim(locked = true))
    }

    @Test
    fun rejectsInvalidOriginAndInvalidDurationBeforeApproval() {
        assertFalse(
            lease.approveLocally(
                binding.copy(tabUrl = "https://test.enerfoam.com/"),
                explicitLockedPermission = true,
            ),
        )
        assertFalse(lease.approveLocally(binding, explicitLockedPermission = true, durationMs = 90_001L))
        assertFalse(lease.approveLocally(binding, explicitLockedPermission = true, maxOperations = 21))
        assertFalse(claim())
    }
}
