package com.kelpie.browser.agent.execution

import android.webkit.WebView
import com.kelpie.browser.agent.contract.AdapterOutcome
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.agent.contract.Operation
import com.kelpie.browser.capture.PrivateWebViewDisplay
import com.kelpie.browser.handlers.HandlerContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.WeakHashMap
import kotlin.coroutines.resume

/**
 * Actual TabStore/WebView port. It never reconstructs the visited WebView or
 * trusts a JSON tab/document/renderer identity. All WebView work is on Main.
 * Execution remains serialized by AgentOperationRuntime.
 */
internal class R5NativeBrowserPort(
    private val ctx: HandlerContext,
    private val privateRenderer: PrivateWebViewDisplay,
) : VerifiedNativeBrowserAdapter.NativePort {
    private val rendererIds = WeakHashMap<WebView, String>()

    private data class Proof(
        val commandId: String,
        val tabId: String,
        val identity: String,
        val operation: Operation,
        val expected: String?,
        val payload: Map<String, Any?>,
    )

    private var proof: Pair<String, Proof>? = null

    override suspend fun actualTarget(tabId: String): VerifiedNativeBrowserAdapter.NativeTarget? =
        withContext(Dispatchers.Main.immediate) {
            val tab = ctx.tabStore?.activeTab ?: return@withContext null
            if (tab.id != tabId || tab.documentVersion.isBlank()) return@withContext null
            val view = tab.webView
            if (!view.isAttachedToWindow) return@withContext null
            VerifiedNativeBrowserAdapter.NativeTarget(
                tab.id,
                tab.documentVersion,
                rendererIds.getOrPut(view) { UUID.randomUUID().toString() },
            )
        }

    override suspend fun execute(
        command: AgentCommand,
        target: VerifiedNativeBrowserAdapter.NativeTarget,
    ): AdapterOutcome =
        withContext(Dispatchers.Main.immediate) {
            proof = null
            val tab = ctx.tabStore?.activeTab ?: return@withContext AdapterOutcome.Failed
            val live = actualTarget(tab.id) ?: return@withContext AdapterOutcome.Failed
            if (live != target) return@withContext AdapterOutcome.Failed
            val view = tab.webView
            val result =
                when (command.operation) {
                    Operation.READ_DOM -> {
                        val raw = evaluate(view, R5WebViewActionScripts.SNAPSHOT) ?: return@withContext AdapterOutcome.Failed
                        val json = runCatching { Json.decodeFromString<String>(raw) }.getOrNull()
                            ?: return@withContext AdapterOutcome.Failed
                        Pair(json, mapOf("kind" to "dom", "snapshotJson" to json))
                    }
                    Operation.SCREENSHOT -> {
                        // Only the app's private virtual display supplies hidden frames.
                        // Foreground/system or other-app capture is never a fallback.
                        val png = privateRenderer.captureFreshPng() ?: return@withContext AdapterOutcome.Failed
                        Pair(null, png + mapOf("kind" to "screenshot"))
                    }
                    else -> return@withContext AdapterOutcome.Failed
                }
            val evidenceId = UUID.randomUUID().toString()
            proof = evidenceId to Proof(command.commandId, tab.id, target.rendererIdentity, command.operation, result.first, result.second)
            AdapterOutcome.Verified(evidenceId)
        }

    override suspend fun proveEffect(
        command: AgentCommand,
        before: VerifiedNativeBrowserAdapter.NativeTarget,
        after: VerifiedNativeBrowserAdapter.NativeTarget,
        evidenceId: String,
    ): Boolean =
        withContext(Dispatchers.Main.immediate) {
            val entry = proof ?: return@withContext false
            val recorded = entry.second
            if (entry.first != evidenceId || recorded.commandId != command.commandId ||
                recorded.tabId != after.tabId || before.rendererIdentity != after.rendererIdentity ||
                recorded.identity != after.rendererIdentity
            ) {
                proof = null
                return@withContext false
            }
            // The adapter additionally checks consent, epoch and exact host at
            // delivery; a read is independently proved by a real live target.
            actualTarget(after.tabId) == after
        }

    /** Called only after a VERIFIED_EFFECT receipt and final grant recheck. */
    suspend fun takePayload(evidenceId: String?): Map<String, Any?>? =
        withContext(Dispatchers.Main.immediate) {
            if (proof?.first != evidenceId) return@withContext null
            proof?.second?.payload.also { proof = null }
        }

    fun clearPendingEvidence() {
        proof = null
    }

    private suspend fun evaluate(view: WebView, script: String): String? =
        withTimeoutOrNull(5_000L) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    view.evaluateJavascript(script) { response ->
                        if (continuation.isActive) continuation.resume(response ?: "null")
                    }
                }
            }
        }
}
