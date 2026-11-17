package com.kelpie.browser.agent.execution

/** Neither route can bypass the local owner grant, exact tab, STOP or lock gates. */
internal enum class R5ScreenshotSource { NONE, PRIVATE_DISPLAY, FOREGROUND_WEBVIEW }

internal object R5ScreenshotRouting {
    fun select(
        privateReady: Boolean,
        privateIdle: Boolean,
        foreground: Boolean,
        sameOwnedView: Boolean,
        unlocked: Boolean,
        noConcurrentCapture: Boolean,
    ): R5ScreenshotSource =
        when {
            !unlocked || !noConcurrentCapture -> R5ScreenshotSource.NONE
            privateReady -> R5ScreenshotSource.PRIVATE_DISPLAY
            privateIdle && foreground && sameOwnedView -> R5ScreenshotSource.FOREGROUND_WEBVIEW
            else -> R5ScreenshotSource.NONE
        }
}
