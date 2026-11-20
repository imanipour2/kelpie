package com.kelpie.browser.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R5PrivateHostReadinessTest {
    private val drawable =
        R5PrivateHostSnapshot(
            owned = true,
            attached = true,
            shown = true,
            windowVisibility = 0,
            width = 1080,
            height = 1920,
            displayMatches = true,
            displayValid = true,
            displayState = 2,
            surfaceValid = true,
            hardwareAccelerated = true,
            rootHardwareAccelerated = true,
        )

    @Test
    fun nativeGeometryDoesNotSubstituteForADrawableOwnedWindow() {
        assertNull(drawable.failure())
        assertEquals("PRIVATE_HOST_WINDOW_NOT_DRAWABLE", drawable.copy(windowVisibility = 4).failure())
        assertEquals("PRIVATE_HOST_WINDOW_NOT_DRAWABLE", drawable.copy(shown = false).failure())
        assertEquals("PRIVATE_HOST_DISPLAY_MISMATCH", drawable.copy(displayMatches = false).failure())
        assertEquals("PRIVATE_HOST_OWNERSHIP_LOST", drawable.copy(owned = false).failure())
    }

    @Test
    fun invalidSurfaceAndDetachedHostHaveDistinctDiagnostics() {
        assertEquals("PRIVATE_SURFACE_INVALID", drawable.copy(surfaceValid = false).failure())
        assertEquals("PRIVATE_DISPLAY_INVALID", drawable.copy(displayValid = false).failure())
        assertEquals("PRIVATE_HOST_NOT_ATTACHED", drawable.copy(attached = false).failure())
        assertEquals("PRIVATE_HOST_ZERO_NATIVE_SIZE", drawable.copy(width = 0).failure())
    }

    @Test
    fun hardwareFlagIsMeasuredWithoutInventingAnUnprovenPermissionGate() {
        assertNull(drawable.copy(hardwareAccelerated = false, rootHardwareAccelerated = false).failure())
    }

    @Test
    fun lateOrMissingCallbackAfterHostLossIsNotReportedAsACompositorTimeout() {
        assertEquals("VISUAL_STATE_OWNERSHIP_LOST", result(completed = 7L, owned = false))
        assertEquals("VISUAL_STATE_OWNERSHIP_LOST", result(completed = null, owned = false))
        assertEquals("VISUAL_STATE_DEVICE_LOCKED", result(completed = null, locked = true))
        assertEquals("VISUAL_STATE_DEVICE_LOCKED", result(completed = null, owned = false, locked = true))
        assertEquals("PRIVATE_HOST_DISPLAY_MISMATCH", result(completed = 7L, hostFailure = "PRIVATE_HOST_DISPLAY_MISMATCH"))
    }

    @Test
    fun validOwnerDistinguishesMatchingCallbackTimeoutAndWrongCallbackId() {
        assertNull(result(completed = 7L))
        assertEquals("VISUAL_STATE_CALLBACK_TIMEOUT", result(completed = null))
        assertEquals("VISUAL_STATE_REQUEST_MISMATCH", result(completed = 8L))
    }

    private fun result(
        completed: Long?,
        owned: Boolean = true,
        locked: Boolean = false,
        hostFailure: String? = null,
    ): String? = R5VisualCommitResult.failure(7L, completed, owned, locked, hostFailure)
}
