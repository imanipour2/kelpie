package com.kelpie.browser.agent.execution

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.kelpie.browser.agent.render.WebViewOwnershipState
import com.kelpie.browser.agent.session.AndroidAgentProcessSession
import com.kelpie.browser.agent.session.SessionConsentCore
import com.kelpie.browser.capture.PrivateWebViewDisplay
import com.kelpie.browser.handlers.HandlerContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lifecycle bridge from the REAL native process consent to one Kelpie-owned
 * WebView on a VirtualDisplay. The browser's Activity is not silently started
 * over ChatGPT; actual Android keyguard lock always revokes renderer ownership.
 *
 * Pure command admission and result transport are in a separate file.
 */
internal class R5AgentCoordinator(
    private val activity: ComponentActivity,
    private val ctx: HandlerContext,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transferMutex = Mutex()
    private val released = MutableStateFlow(false)
    val relinquished = released.asStateFlow()
    private val renderer =
        PrivateWebViewDisplay(
            activity,
            composeRelease = { released.value = true },
            composeRestore = { released.value = false },
            useNativeFrameProof = true,
        )
    private val consent = AndroidAgentProcessSession.runtimeConsent()
    private val nativePort = R5NativeBrowserPort(activity, ctx, renderer)
    private var ownership = WebViewOwnershipState()
    private var runtime = newRuntime()
    private var currentGeneration: String? = null
    private var currentTabId: String? = null
    private var ready = false
    private var closed = false

    private fun newRuntime(): AgentOperationRuntime =
        AgentOperationRuntime(consent, VerifiedNativeBrowserAdapter(consent, ownership, nativePort))

    private val screenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_SCREEN_OFF || closed) return
                // Authority reducer independently suspends the grant. Renderer
                // disposal does not rely on eventual HTTP reads or wakeup.
                ownership.lockDevice()
                runtime.cancelInFlight()
                ready = false
                scope.launch { releaseRenderer() }
            }
        }

    init {
        ContextCompat.registerReceiver(
            activity,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            AndroidAgentProcessSession.active.collect { state ->
                if (closed) return@collect
                if (state == null) {
                    ready = false
                    runtime.cancelInFlight()
                    nativePort.clearPendingEvidence()
                    scope.launch { releaseRenderer() }
                } else if (state.locked) {
                    ready = false
                    ownership.lockDevice()
                    runtime.cancelInFlight()
                    scope.launch { releaseRenderer() }
                } else if (state.generation != currentGeneration) {
                    currentGeneration = state.generation
                    ownership = WebViewOwnershipState()
                    runtime = newRuntime()
                    scope.launch { activateRenderer(state) }
                }
            }
        }
    }

    /** Called from Activity lifecycle, not from a remote JSON argument. */
    fun onPause() {
        scope.launch {
            val active = consent.snapshot() as? SessionConsentCore.State.Active
            if (active != null && !active.locked && renderer.state.value == PrivateWebViewDisplay.State.IDLE) {
                activateRenderer(active)
            }
            renderer.setActivityVisible(false)
        }
    }

    fun onResume() {
        renderer.setActivityVisible(true)
        scope.launch {
            if (renderer.state.value == PrivateWebViewDisplay.State.PRIVATE_DISPLAY) {
                restoreForeground()
            }
        }
    }

    private suspend fun activateRenderer(active: SessionConsentCore.State.Active) =
        transferMutex.withLock {
            if (closed || active.locked || consent.snapshot() != active) return@withLock
            if (renderer.state.value == PrivateWebViewDisplay.State.PRIVATE_DISPLAY) return@withLock
            val tab = ctx.tabStore?.activeTab ?: return@withLock
            val target = nativePort.nativeTargetForBinding(tab.id) ?: return@withLock
            if (ownership.status() == WebViewOwnershipState.Host.ABSENT &&
                !ownership.bindForeground(active.generation, target.rendererIdentity)
            ) {
                return@withLock
            }
            val ticket = ownership.moveToPrivate(active.generation) ?: return@withLock
            ready = false
            val entered = renderer.start(tab.webView)
            if (!entered || consent.snapshot() != active || !ownership.completePrivate(ticket)) {
                ownership.failRenderer()
                nativePort.clearPendingEvidence()
                renderer.stop()
                AndroidAgentProcessSession.stop()
                return@withLock
            }
            currentTabId = tab.id
            ready = true
        }

    private suspend fun restoreForeground() =
        transferMutex.withLock {
            val active = consent.snapshot() as? SessionConsentCore.State.Active
            val ticket = active?.let { ownership.restoreForeground(it.generation) }
            ready = false
            renderer.stop()
            val restored = renderer.verifyRestored()
            if (ticket != null && restored && ownership.completeRestore(ticket)) {
                ready = true
            } else if (ticket != null) {
                ownership.failRenderer()
                AndroidAgentProcessSession.stop()
            }
        }

    private suspend fun releaseRenderer() =
        transferMutex.withLock {
            withContext(NonCancellable) {
                ready = false
                nativePort.clearPendingEvidence()
                renderer.stop()
                // A locked Activity may not complete physical reattachment.
                // Keep the grant denied; native onResume may finish restoration.
            }
        }

    fun close() {
        if (closed) return
        closed = true
        activity.unregisterReceiver(screenOffReceiver)
        ready = false
        runtime.cancelInFlight()
        nativePort.clearPendingEvidence()
        ownership.stop()
        renderer.dispose()
        scope.cancel()
    }
}
