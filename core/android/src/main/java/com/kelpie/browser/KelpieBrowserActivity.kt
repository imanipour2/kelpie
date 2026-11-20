package com.kelpie.browser

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.kelpie.browser.agent.session.AgentSessionNotification
import com.kelpie.browser.agent.session.AndroidAgentProcessSession
import com.kelpie.browser.agent.session.R5BackgroundOptInStore
import com.kelpie.browser.agent.session.R5ObservedAgentClients
import com.kelpie.browser.agent.session.R5StartupGateState
import com.kelpie.browser.agent.session.shouldPromptForR5BackgroundOptIn
import com.kelpie.browser.network.KelpieNetworkService
import com.kelpie.browser.network.NetworkServiceState

/** Optional standalone-window host; other apps may embed KelpieBrowserSession.view(). */
open class KelpieBrowserActivity : ComponentActivity() {
    private lateinit var browser: KelpieBrowserSession
    private val startupGate by lazy {
        R5StartupGateState(
            shouldPromptForR5BackgroundOptIn(
                backgroundFeatureRequiresOptIn = requireR5FirstEntryConsent(),
                previouslyApproved = R5BackgroundOptInStore.isApproved(this),
            ),
        )
    }

    protected open fun enablePrivateCaptureQa(): Boolean = false

    protected open fun enableAgentConsentQa(): Boolean = false

    protected open fun enableR5BackgroundAgentQa(): Boolean = false

    protected open fun useMotherMcpWithR5Renderer(): Boolean = false

    /** Private Android R5 beta only. Embedded consumers and production builds are unchanged. */
    protected open fun requireR5FirstEntryConsent(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (startupGate.mayCreateBrowserSession()) {
            launchBrowserIfAllowed()
        } else {
            showR5FirstEntryConsent()
        }
    }

    private fun showR5FirstEntryConsent() {
        // No browser session, HTTP listener, or Kelpie-owned foreground service exists yet.
        // A genuine foreground tap is required; Back/outside are not implied approval.
        AlertDialog
            .Builder(this)
            .setTitle("Allow Kelpie background activity?")
            .setMessage(
                "Kelpie can keep its own browser connection available while you use ChatGPT, " +
                    "subject to Android background restrictions. This does not give an unknown AI " +
                    "agent control. " +
                    (if (useMotherMcpWithR5Renderer()) "Approve a trusted computer once; " else "Pair a verified client and approve its session; ") +
                    "STOP is always available. Sensitive actions need separate approval.",
            ).setPositiveButton("Allow & continue") { _, _ ->
                if (R5BackgroundOptInStore.rememberApproval(this)) {
                    startupGate.allow()
                    launchBrowserIfAllowed()
                } else {
                    Toast.makeText(this, "Could not save startup choice. Please retry.", Toast.LENGTH_LONG).show()
                    showR5FirstEntryConsent()
                }
            }.setNegativeButton("No — bye bye") { _, _ ->
                startupGate.decline()
                exitWithoutStarting()
            }.setCancelable(false)
            .show()
    }

    private fun launchBrowserIfAllowed() {
        if (!startupGate.mayCreateBrowserSession() || ::browser.isInitialized || isFinishing || isDestroyed) return
        browser =
            KelpieBrowserSession(
                this,
                privateCaptureQaEnabled = enablePrivateCaptureQa(),
                agentConsentQaEnabled = enableAgentConsentQa(),
                agentBackgroundQaEnabled = enableR5BackgroundAgentQa(),
                motherMcpR5RendererQaEnabled = useMotherMcpWithR5Renderer(),
            )
        setContentView(browser.view())
    }

    private fun exitWithoutStarting() {
        // Decline never touches Termux or another app's tunnel. No agent grant survives.
        AndroidAgentProcessSession.stop()
        R5ObservedAgentClients.registry.clear()
        AgentSessionNotification.hide(this)
        KelpieNetworkService.stop(this)
        NetworkServiceState.snapshot()?.let { stage ->
            stage.stop()
            NetworkServiceState.clearIf(stage)
        }
        Toast.makeText(this, "Bye bye", Toast.LENGTH_SHORT).show()
        finishAndRemoveTask()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::browser.isInitialized) browser.trimMemory()
    }
}
