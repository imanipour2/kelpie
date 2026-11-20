package com.kelpie.browser.capture

import android.app.Presentation
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.webkit.WebView

/** Structural host evidence only: no URL, DOM, pixels, account or principal. */
internal data class R5PrivateHostSnapshot(
    val owned: Boolean,
    val attached: Boolean,
    val shown: Boolean,
    val windowVisibility: Int,
    val width: Int,
    val height: Int,
    val displayMatches: Boolean,
    val displayValid: Boolean,
    val displayState: Int,
    val surfaceValid: Boolean,
    val hardwareAccelerated: Boolean,
    val rootHardwareAccelerated: Boolean,
) {
    fun failure(): String? =
        when {
            !owned -> "PRIVATE_HOST_OWNERSHIP_LOST"
            !displayValid -> "PRIVATE_DISPLAY_INVALID"
            !surfaceValid -> "PRIVATE_SURFACE_INVALID"
            !attached -> "PRIVATE_HOST_NOT_ATTACHED"
            !displayMatches -> "PRIVATE_HOST_DISPLAY_MISMATCH"
            !shown || windowVisibility != View.VISIBLE -> "PRIVATE_HOST_WINDOW_NOT_DRAWABLE"
            width <= 0 || height <= 0 -> "PRIVATE_HOST_ZERO_NATIVE_SIZE"
            else -> null
        }

    fun log(stage: String) {
        Log.i(
            "KelpieR5Host",
            "stage=" + stage + " failure=" + (failure() ?: "NONE") +
                " owned=" + owned + " attached=" + attached + " shown=" + shown +
                " windowVisibility=" + windowVisibility + " width=" + width + " height=" + height +
                " displayMatches=" + displayMatches + " displayValid=" + displayValid +
                " displayState=" + displayState + " surfaceValid=" + surfaceValid +
                " hardwareAccelerated=" + hardwareAccelerated + " rootHardwareAccelerated=" + rootHardwareAccelerated,
        )
    }

    companion object {
        fun read(
            webView: WebView,
            display: VirtualDisplay?,
            presentation: Presentation?,
            reader: ImageReader?,
            owned: Boolean,
        ): R5PrivateHostSnapshot =
            R5PrivateHostSnapshot(
                owned = owned,
                attached = webView.isAttachedToWindow,
                shown = webView.isShown,
                windowVisibility = webView.windowVisibility,
                width = webView.width,
                height = webView.height,
                displayMatches = display != null && webView.display?.displayId == display.display.displayId,
                displayValid = display?.display?.isValid == true,
                displayState = display?.display?.state ?: 0,
                surfaceValid = reader?.surface?.isValid == true,
                hardwareAccelerated = webView.isHardwareAccelerated,
                rootHardwareAccelerated = presentation?.window?.decorView?.isHardwareAccelerated == true,
            )
    }
}

internal class R5PrivateTransferTrace {
    private val startedMs = SystemClock.elapsedRealtime()

    operator fun invoke(stage: String) {
        Log.i("KelpieR5Transfer", "stage=" + stage + " elapsedMs=" + (SystemClock.elapsedRealtime() - startedMs))
    }
}
