package com.kelpie.browser.agent.execution

import org.junit.Assert.assertEquals
import org.junit.Test

class R5ScreenshotRoutingTest {
    private fun choose(
        privateReady: Boolean = false,
        privateIdle: Boolean = false,
        foreground: Boolean = false,
        sameOwnedView: Boolean = false,
        unlocked: Boolean = true,
        noConcurrentCapture: Boolean = true,
    ) = R5ScreenshotRouting.select(
        privateReady,
        privateIdle,
        foreground,
        sameOwnedView,
        unlocked,
        noConcurrentCapture,
    )

    @Test
    fun authenticatedHiddenDisplayAlwaysWinsWhenReady() {
        assertEquals(R5ScreenshotSource.PRIVATE_DISPLAY, choose(privateReady = true))
        assertEquals(
            R5ScreenshotSource.PRIVATE_DISPLAY,
            choose(privateReady = true, privateIdle = true, foreground = true, sameOwnedView = true),
        )
    }

    @Test
    fun foregroundNeedsOwnerBoundWebViewAndFullyRestoredPrivateDisplay() {
        assertEquals(
            R5ScreenshotSource.FOREGROUND_WEBVIEW,
            choose(privateIdle = true, foreground = true, sameOwnedView = true),
        )
        assertEquals(R5ScreenshotSource.NONE, choose(foreground = true, sameOwnedView = true))
        assertEquals(R5ScreenshotSource.NONE, choose(privateIdle = true, sameOwnedView = true))
        assertEquals(R5ScreenshotSource.NONE, choose(privateIdle = true, foreground = true))
    }

    @Test
    fun lockAndConcurrentCaptureRevokeBothRoutes() {
        for (privateReady in listOf(false, true)) {
            assertEquals(
                R5ScreenshotSource.NONE,
                choose(privateReady = privateReady, privateIdle = true, foreground = true, sameOwnedView = true, unlocked = false),
            )
            assertEquals(
                R5ScreenshotSource.NONE,
                choose(privateReady = privateReady, privateIdle = true, foreground = true, sameOwnedView = true, noConcurrentCapture = false),
            )
        }
    }
}
