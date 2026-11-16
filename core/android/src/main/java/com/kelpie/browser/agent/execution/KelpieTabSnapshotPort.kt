package com.kelpie.browser.agent.execution

import android.webkit.WebView
import com.kelpie.browser.agent.contract.AdapterOutcome
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.browser.TabStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.WeakHashMap

/**
 * Exact Android TabStore identity reader. The WebView is NEVER reconstructed.
 * This first integration stage is deliberately READ-ID ONLY: all effects fail closed.
 * Do not register in Router/HTTP/MCP until native STOP and the unified principal gate exist.
 */
internal class KelpieTabSnapshotPort(
    private val store: TabStore,
    private val uiDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : VerifiedNativeBrowserAdapter.NativePort {
    // Weak keys: never retain a destroyed WebView solely to remember its identity.
    // All accesses happen on Android UI dispatcher; equality is WebView object identity.
    private val rendererIds = WeakHashMap<WebView, String>()

    override suspend fun actualTarget(tabId: String): VerifiedNativeBrowserAdapter.NativeTarget? =
        withContext(uiDispatcher) {
            val tab = store.tabs.value.firstOrNull { it.id == tabId } ?: return@withContext null
            val view = tab.webView
            val identity = rendererIds.getOrPut(view) { UUID.randomUUID().toString() }
            val version = tab.documentVersion
            if (version.isBlank()) return@withContext null
            VerifiedNativeBrowserAdapter.NativeTarget(tab.id, version, identity)
        }

    override suspend fun execute(
        command: AgentCommand,
        target: VerifiedNativeBrowserAdapter.NativeTarget,
    ): AdapterOutcome = AdapterOutcome.Failed // No unverified action executor installed.

    override suspend fun proveEffect(
        command: AgentCommand,
        before: VerifiedNativeBrowserAdapter.NativeTarget,
        after: VerifiedNativeBrowserAdapter.NativeTarget,
        evidenceId: String,
    ): Boolean = false // No real fresh DOM/pixel proof installed.
}
