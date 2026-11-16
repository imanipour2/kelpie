package com.kelpie.browser.agent

import com.kelpie.browser.agent.render.WebViewOwnershipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure source-level tests; native WebView attach/detach requires Android hardware QA. */
class WebViewOwnershipStateTest {
    @Test
    fun lockUnlockCannotRebindAnotherSessionGeneration() {
        val owner = WebViewOwnershipState()
        assertTrue(owner.bindForeground("first-session", "webview-A"))
        owner.lockDevice()
        assertEquals(WebViewOwnershipState.Host.SUSPENDED, owner.status())
        assertTrue(owner.unlockNeedsReattach("first-session"))
        assertFalse(owner.bindForeground("second-session", "webview-B"))
        assertTrue(owner.bindForeground("first-session", "webview-A-restored"))
        assertTrue(owner.mayExecuteWebOperation("first-session", "webview-A-restored"))
        assertFalse(owner.mayExecuteWebOperation("second-session", "webview-A-restored"))
    }

    @Test
    fun rendererFailureDoesNotTransferOwnershipToNewSession() {
        val owner = WebViewOwnershipState()
        assertTrue(owner.bindForeground("initial", "renderer-1"))
        owner.failRenderer()
        owner.detachAfterFailure()
        assertFalse(owner.bindForeground("foreign", "renderer-2"))
        assertTrue(owner.bindForeground("initial", "renderer-2"))
    }

    @Test
    fun lockBeforeBindingDoesNotStrandFreshSession() {
        val owner = WebViewOwnershipState()
        owner.lockDevice()
        assertEquals(WebViewOwnershipState.Host.ABSENT, owner.status())
        assertTrue(owner.bindForeground("local", "renderer-1"))
    }

    @Test
    fun stopAlwaysRevokesRendererOwnership() {
        val owner = WebViewOwnershipState()
        assertTrue(owner.bindForeground("local", "renderer-1"))
        owner.stop()
        assertFalse(owner.mayExecuteWebOperation("local", "renderer-1"))
        assertFalse(owner.bindForeground("local", "renderer-2"))
    }
}
