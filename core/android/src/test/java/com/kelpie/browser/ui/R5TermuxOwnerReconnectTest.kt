package com.kelpie.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R5TermuxOwnerReconnectTest {
    @Test
    fun nativeFirstFallbackRequiresBothExplicitOwnerChoiceAndExistingPermission() {
        assertEquals(
            R5TermuxOwnerReconnect.Dispatch.OWNER_APPROVAL_REQUIRED,
            R5TermuxOwnerReconnect.admission(ownerApprovedFallback = false, permissionGranted = true),
        )
        assertEquals(
            R5TermuxOwnerReconnect.Dispatch.OWNER_APPROVAL_REQUIRED,
            R5TermuxOwnerReconnect.admission(ownerApprovedFallback = false, permissionGranted = false),
        )
        assertEquals(
            R5TermuxOwnerReconnect.Dispatch.PERMISSION_REQUIRED,
            R5TermuxOwnerReconnect.admission(ownerApprovedFallback = true, permissionGranted = false),
        )
        assertNull(R5TermuxOwnerReconnect.admission(ownerApprovedFallback = true, permissionGranted = true))
    }

    @Test
    fun retryRequiresElapsedCooldownUnlessDeviceClockWasReset() {
        assertTrue(R5TermuxOwnerReconnect.canTryAgain(1_000L, null))
        assertFalse(R5TermuxOwnerReconnect.canTryAgain(1_001L, 1_000L))
        assertFalse(R5TermuxOwnerReconnect.canTryAgain(15_999L, 1_000L))
        assertTrue(R5TermuxOwnerReconnect.canTryAgain(16_000L, 1_000L))
        assertTrue(R5TermuxOwnerReconnect.canTryAgain(200L, 1_000L))
    }

    @Test
    fun commandTargetAndPermissionsAreFixed() {
        assertEquals("com.termux.permission.RUN_COMMAND", R5TermuxOwnerReconnect.PERMISSION)
        assertEquals("com.termux", R5TermuxOwnerReconnect.PACKAGE)
        assertEquals("com.termux.app.RunCommandService", R5TermuxOwnerReconnect.SERVICE)
        assertEquals("com.termux.RUN_COMMAND", R5TermuxOwnerReconnect.ACTION)
        assertEquals(
            "/data/data/com.termux/files/home/.local/bin/ef-kelpie-request-consent",
            R5TermuxOwnerReconnect.SCRIPT,
        )
    }

    @Test
    fun dispatchIsNotAnApprovalOrAConnectedResult() {
        assertTrue(
            R5TermuxOwnerReconnect.Dispatch.entries.contains(
                R5TermuxOwnerReconnect.Dispatch.QUEUED_NOT_CONFIRMED,
            ),
        )
        assertFalse(
            R5TermuxOwnerReconnect.Dispatch.entries.any {
                it.name == "APPROVED" || it.name == "CONNECTED"
            },
        )
    }
}
