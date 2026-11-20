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
    fun privateHostSwitchRevokesOldWebViewWithoutNewConsentOrActivity() {
        val owner = WebViewOwnershipState()
        assertTrue(owner.bindForeground("generation", "webview-A"))
        val ticket = owner.moveToPrivate("generation")!!
        assertTrue(owner.completePrivate(ticket))
        val beforeEpoch = owner.transitionEpoch()
        assertFalse(owner.switchPrivate("foreign", "webview-A", "webview-B"))
        assertFalse(owner.switchPrivate("generation", "wrong-current", "webview-B"))
        assertFalse(owner.switchPrivate("generation", "webview-A", "webview-A"))
        assertTrue(owner.switchPrivate("generation", "webview-A", "webview-B"))
        assertTrue(owner.transitionEpoch() > beforeEpoch)
        assertFalse(owner.mayExecuteWebOperation("generation", "webview-A"))
        assertTrue(owner.mayExecuteWebOperation("generation", "webview-B"))
        owner.lockDevice()
        assertFalse(owner.switchPrivate("generation", "webview-B", "webview-C"))
    }

    @Test
    fun stopAlwaysRevokesRendererOwnership() {
        val owner = WebViewOwnershipState()
        assertTrue(owner.bindForeground("local", "renderer-1"))
        owner.stop()
        assertFalse(owner.mayExecuteWebOperation("local", "renderer-1"))
        assertFalse(owner.bindForeground("local", "renderer-2"))
    }

    @Test
    fun verifiedRestoreRetiresInterruptedTicketButCannotTransferItsGeneration() {
        val owner = WebViewOwnershipState()
        assertTrue(owner.bindForeground("local", "renderer-1"))
        assertTrue(owner.completePrivate(owner.moveToPrivate("local")!!))
        val interrupted = owner.restoreForeground("local")!!
        owner.resetAfterVerifiedRestore()
        assertEquals(WebViewOwnershipState.Host.ABSENT, owner.status())
        assertFalse(owner.completeRestore(interrupted))
        assertFalse(owner.bindForeground("foreign", "renderer-1"))
        assertTrue(owner.bindForeground("local", "renderer-1"))
    }

    @Test
    fun physicalRestoreCannotClearLockOrExplicitStop() {
        val owner = WebViewOwnershipState()
        assertTrue(owner.bindForeground("local", "renderer-1"))
        owner.lockDevice()
        owner.resetAfterVerifiedRestore()
        assertEquals(WebViewOwnershipState.Host.SUSPENDED, owner.status())
        assertFalse(owner.bindForeground("local", "renderer-1"))
        owner.stop()
        owner.resetAfterVerifiedRestore()
        assertEquals(WebViewOwnershipState.Host.CLOSED, owner.status())
        assertFalse(owner.bindForeground("local", "renderer-1"))
    }
}
