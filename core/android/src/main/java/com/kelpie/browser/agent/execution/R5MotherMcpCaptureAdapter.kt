package com.kelpie.browser.agent.execution

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.kelpie.browser.capture.PrivateWebViewDisplay
import com.kelpie.browser.handlers.HandlerContext

/**
 * Adapts one owner-approved R5 compositor frame to the standard mother MCP
 * screenshot handler. Remote transport status never grants renderer access.
 */
internal class R5MotherMcpCaptureAdapter(
    private val activity: ComponentActivity,
    private val ctx: HandlerContext,
    private val renderer: PrivateWebViewDisplay,
    private val authorized: suspend () -> Boolean,
    private val privateOwned: () -> Boolean,
    private val sameTab: (String) -> Boolean,
) {
    fun attach() {
        ctx.motherPrivateAgentActive = {
            renderer.state.value != PrivateWebViewDisplay.State.IDLE ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        ctx.motherGatewayAuthorization = authorized
        ctx.motherPrivateScreenshot = {
            val tab = ctx.tabStore?.activeTab
            if (tab == null ||
                !authorized() ||
                !privateOwned() ||
                !sameTab(tab.id) ||
                renderer.state.value != PrivateWebViewDisplay.State.PRIVATE_DISPLAY ||
                !renderer.canDeliverFrame()
            ) {
                null
            } else {
                val frame = renderer.captureFreshPng()
                if (frame != null &&
                    authorized() &&
                    privateOwned() &&
                    sameTab(tab.id) &&
                    renderer.canDeliverFrame()
                ) {
                    frame
                } else {
                    null
                }
            }
        }
    }
}
