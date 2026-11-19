package com.kelpie.browser.agent.execution

import android.app.Activity
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.agent.contract.ObservedTarget
import com.kelpie.browser.agent.contract.Operation
import com.kelpie.browser.agent.render.WebViewOwnershipState
import com.kelpie.browser.agent.session.AgentSessionNotification
import com.kelpie.browser.agent.session.AndroidAgentProcessSession
import com.kelpie.browser.agent.session.SessionConsentCore
import com.kelpie.browser.capture.PrivateWebViewDisplay
import com.kelpie.browser.handlers.HandlerContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * R5 tab lifecycle boundary. Browser tabs are app-owned, not Android tasks.
 * The private renderer stays hidden and its exact original WebView is exchanged
 * in place; no remote command starts an Activity or reads another app's pixels.
 * All entrypoints are called inside AgentOperationRuntime's shared replay lock.
 */
internal class R5AgentTabCommands(
    private val activity: Activity,
    private val context: HandlerContext,
    private val renderer: PrivateWebViewDisplay,
    private val nativePort: R5NativeBrowserPort,
    private val consent: SessionConsentCore,
    private val transferMutex: Mutex,
    private val ownership: () -> WebViewOwnershipState,
    private val isReady: () -> Boolean,
    private val setReady: (Boolean) -> Unit,
    private val onSelected: (String) -> Unit,
) {
    private data class Proof(
        val commandId: String,
        val operation: Operation,
        val targetId: String,
        val count: Int,
        val selectedId: String,
        val targetUrl: String?,
    )

    private var proof: Proof? = null

    suspend fun observe(): ObservedTarget? =
        withContext(Dispatchers.Main.immediate) {
            val active = context.tabStore?.activeTab ?: return@withContext null
            ObservedTarget(active.id, active.documentVersion)
        }

    suspend fun perform(command: AgentCommand): Boolean =
        withContext(Dispatchers.Main.immediate) {
            transferMutex.withLock {
                proof = null
                if (!isReady()) return@withLock false
                val session = consent.snapshot() as? SessionConsentCore.State.Active ?: return@withLock false
                if (session.locked ||
                    session.generation != command.consentGeneration ||
                    session.principalId != command.principalId
                ) {
                    return@withLock false
                }
                val store = context.tabStore ?: return@withLock false
                val active = store.activeTab ?: return@withLock false
                if (active.id != command.tabId || active.documentVersion != command.observedDocumentEpoch) {
                    return@withLock false
                }
                val count = store.tabs.value.size
                when (command.operation) {
                    Operation.OPEN_TAB -> {
                        val url = command.targetUrl ?: return@withLock false
                        val created = store.addBackgroundTab(url) ?: return@withLock false
                        proof = Proof(command.commandId, command.operation, created.id, count + 1, active.id, url)
                        true
                    }
                    Operation.CLOSE_TAB -> {
                        val targetId = command.selector ?: return@withLock false
                        val victim = store.tabs.value.firstOrNull { it.id == targetId } ?: return@withLock false
                        // Never destroy the private renderer or a view attached to Compose.
                        if (victim.id == active.id || victim.webView.isAttachedToWindow) return@withLock false
                        store.closeTab(victim.id)
                        proof = Proof(command.commandId, command.operation, targetId, count - 1, active.id, null)
                        true
                    }
                    Operation.SWITCH_TAB -> {
                        val targetId = command.selector ?: return@withLock false
                        val target = store.tabs.value.firstOrNull { it.id == targetId } ?: return@withLock false
                        if (target.id == active.id) {
                            proof = Proof(command.commandId, command.operation, target.id, count, active.id, null)
                            return@withLock true
                        }
                        if (renderer.state.value != PrivateWebViewDisplay.State.PRIVATE_DISPLAY ||
                            !renderer.canDeliverFrame() ||
                            target.webView.isAttachedToWindow
                        ) {
                            return@withLock false
                        }
                        val before = nativePort.nativeTargetForBinding(active.id) ?: return@withLock false
                        setReady(false)
                        if (!renderer.switchPrivateWebView(target.webView)) {
                            val restored = renderer.canDeliverFrame()
                            setReady(restored)
                            if (!restored) {
                                // The rollback could not prove that the old native owner still renders.
                                AndroidAgentProcessSession.stop()
                                AgentSessionNotification.hide(activity)
                            }
                            return@withLock false
                        }
                        // The tab index is changed only after the physical reparent succeeds.
                        val selected = store.selectTabAsAgent(target.id)
                        val after = if (selected) nativePort.nativeTargetForBinding(target.id) else null
                        val transferred =
                            after != null &&
                                ownership().switchPrivate(
                                    command.consentGeneration,
                                    before.rendererIdentity,
                                    after.rendererIdentity,
                                )
                        if (!transferred || !renderer.canDeliverFrame()) {
                            // Any uncertain renderer ownership is a hard local revocation.
                            // The native lifecycle will release/restore the private display.
                            setReady(false)
                            AndroidAgentProcessSession.stop()
                            AgentSessionNotification.hide(activity)
                            return@withLock false
                        }
                        onSelected(target.id)
                        setReady(true)
                        proof = Proof(command.commandId, command.operation, target.id, count, target.id, null)
                        true
                    }
                    else -> false
                }
            }
        }

    suspend fun verify(command: AgentCommand): Boolean =
        withContext(Dispatchers.Main.immediate) {
            val evidence = proof ?: return@withContext false
            proof = null
            val store = context.tabStore ?: return@withContext false
            val session = consent.snapshot() as? SessionConsentCore.State.Active ?: return@withContext false
            if (session.locked ||
                session.generation != command.consentGeneration ||
                session.principalId != command.principalId ||
                evidence.commandId != command.commandId ||
                evidence.operation != command.operation ||
                !isReady() ||
                store.tabs.value.size != evidence.count ||
                store.activeTabId.value != evidence.selectedId
            ) {
                return@withContext false
            }
            when (command.operation) {
                Operation.OPEN_TAB ->
                    store.tabs.value.any { it.id == evidence.targetId && it.currentUrl == evidence.targetUrl }
                Operation.CLOSE_TAB -> store.tabs.value.none { it.id == evidence.targetId }
                Operation.SWITCH_TAB -> {
                    val current = store.activeTab ?: return@withContext false
                    val live = nativePort.actualTarget(current.id) ?: return@withContext false
                    renderer.canDeliverFrame() &&
                        ownership().mayExecuteWebOperation(command.consentGeneration, live.rendererIdentity)
                }
                else -> false
            }
        }
}
