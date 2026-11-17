package com.kelpie.browser.agent.execution

import android.app.KeyguardManager
import android.os.PowerManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.kelpie.browser.agent.contract.AdapterOutcome
import com.kelpie.browser.agent.contract.AgentCommand
import com.kelpie.browser.agent.contract.Operation
import com.kelpie.browser.capture.PrivateWebViewDisplay
import com.kelpie.browser.handlers.HandlerContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val activity: ComponentActivity,
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

    /** Native controller can resolve a renderer before Compose transfers ownership. */
    suspend fun nativeTargetForBinding(tabId: String): VerifiedNativeBrowserAdapter.NativeTarget? =
        withContext(Dispatchers.Main.immediate) {
            val tab = ctx.tabStore?.activeTab ?: return@withContext null
            if (tab.id != tabId || tab.documentVersion.isBlank() || !safeScreen()) return@withContext null
            val view = tab.webView
            if (!view.isAttachedToWindow) return@withContext null
            VerifiedNativeBrowserAdapter.NativeTarget(
                tab.id,
                tab.documentVersion,
                rendererIds.getOrPut(view) { UUID.randomUUID().toString() },
            )
        }

    override suspend fun actualTarget(tabId: String): VerifiedNativeBrowserAdapter.NativeTarget? =
        withContext(Dispatchers.Main.immediate) {
            val target = nativeTargetForBinding(tabId) ?: return@withContext null
            val view = ctx.tabStore?.activeTab?.webView ?: return@withContext null
            val foreground =
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    activity.window.decorView.isShown &&
                    view.isShown
            if (!foreground && !privateRenderer.canDeliverFrame()) return@withContext null
            target
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
                        val json =
                            runCatching { Json.decodeFromString<String>(raw) }.getOrNull()
                                ?: return@withContext AdapterOutcome.Failed
                        Pair(json, mapOf("kind" to "dom", "snapshotJson" to json))
                    }
                    Operation.NAVIGATE -> {
                        val requested = command.targetUrl ?: return@withContext AdapterOutcome.Failed
                        view.loadUrl(requested)
                        val committed =
                            withTimeoutOrNull(12_000L) {
                                while (view.url?.substringBefore("#") != requested.substringBefore("#")) {
                                    delay(80)
                                }
                                true
                            } ?: false
                        if (!committed) return@withContext AdapterOutcome.Uncertain
                        Pair(requested, mapOf("kind" to "navigate"))
                    }
                    Operation.BACK, Operation.FORWARD -> {
                        val previousUrl = view.url.orEmpty()
                        val previousEpoch = tab.documentVersion
                        val allowed =
                            if (command.operation == Operation.BACK) view.canGoBack() else view.canGoForward()
                        if (!allowed) return@withContext AdapterOutcome.Failed
                        if (command.operation == Operation.BACK) view.goBack() else view.goForward()
                        val navigationProof = R5ClickNavigationProof(previousUrl, previousEpoch)
                        val settled =
                            withTimeoutOrNull(CLICK_SETTLEMENT_TIMEOUT_MS) {
                                repeat(CLICK_SETTLEMENT_POLLS) {
                                    val liveTab = ctx.tabStore?.activeTab ?: return@withTimeoutOrNull false
                                    if (liveTab.id != tab.id || liveTab.webView !== view) {
                                        return@withTimeoutOrNull false
                                    }
                                    if (navigationProof.observedStableChange(view.url.orEmpty(), liveTab.documentVersion)) {
                                        return@withTimeoutOrNull true
                                    }
                                    delay(CLICK_SETTLEMENT_POLL_MS)
                                }
                                false
                            } ?: false
                        if (!settled) return@withContext AdapterOutcome.Uncertain
                        Pair(previousUrl, mapOf("kind" to "history"))
                    }
                    Operation.CLICK -> {
                        val selector = command.selector ?: return@withContext AdapterOutcome.Failed
                        val previousUrl = view.url.orEmpty()
                        val previousEpoch = tab.documentVersion
                        val previousFingerprint =
                            evaluate(view, R5WebViewActionScripts.DOCUMENT_FINGERPRINT)
                                ?: return@withContext AdapterOutcome.Failed
                        val outcome = evaluate(view, R5WebViewActionScripts.click(selector))
                        if (outcome != "\"DISPATCHED\"") return@withContext AdapterOutcome.Failed
                        // The agent controls ordinary clicks; never infer success from JS dispatch.
                        // Native navigation OR a stable visible DOM/control-state change
                        // constitutes independently observed effect, including SPA buttons.
                        val navigationProof = R5ClickNavigationProof(previousUrl, previousEpoch)
                        var previousChangedFingerprint: String? = null
                        val committed =
                            withTimeoutOrNull(CLICK_SETTLEMENT_TIMEOUT_MS) {
                                repeat(CLICK_SETTLEMENT_POLLS) {
                                    val now = ctx.tabStore?.activeTab ?: return@withTimeoutOrNull false
                                    if (now.id != tab.id || now.webView !== view) {
                                        return@withTimeoutOrNull false
                                    }
                                    if (navigationProof.observedStableChange(view.url.orEmpty(), now.documentVersion)) {
                                        return@withTimeoutOrNull true
                                    }
                                    if (now.documentVersion == previousEpoch && view.url == previousUrl) {
                                        val fingerprint = evaluate(view, R5WebViewActionScripts.DOCUMENT_FINGERPRINT)
                                        if (fingerprint != null && fingerprint != previousFingerprint) {
                                            if (previousChangedFingerprint == fingerprint) return@withTimeoutOrNull true
                                            previousChangedFingerprint = fingerprint
                                        } else {
                                            previousChangedFingerprint = null
                                        }
                                    }
                                    delay(CLICK_SETTLEMENT_POLL_MS)
                                }
                                false
                            } ?: false
                        if (!committed) return@withContext AdapterOutcome.Uncertain
                        Pair(
                            previousUrl,
                            mapOf("kind" to "click", "mayHaveCompleted" to true, "preFingerprint" to previousFingerprint),
                        )
                    }
                    Operation.SELECT -> {
                        val selector = command.selector ?: return@withContext AdapterOutcome.Failed
                        val value = command.inputText ?: return@withContext AdapterOutcome.Failed
                        if (evaluate(view, R5WebViewActionScripts.select(selector, value)) != "true") {
                            return@withContext AdapterOutcome.Failed
                        }
                        Pair(value, mapOf("kind" to "select"))
                    }
                    Operation.WAIT -> {
                        val selector = command.selector
                        val observed =
                            withTimeoutOrNull(7_500L) {
                                repeat(70) {
                                    if (ctx.tabStore?.activeTab?.webView !== view) return@withTimeoutOrNull false
                                    if (evaluate(view, R5WebViewActionScripts.waitFor(selector)) == "true") {
                                        return@withTimeoutOrNull true
                                    }
                                    delay(100L)
                                }
                                false
                            } ?: false
                        if (!observed) return@withContext AdapterOutcome.Uncertain
                        Pair(null, mapOf("kind" to "wait"))
                    }
                    Operation.FILL, Operation.TYPE -> {
                        val selector = command.selector ?: return@withContext AdapterOutcome.Failed
                        val text = command.inputText ?: return@withContext AdapterOutcome.Failed
                        if (evaluate(view, R5WebViewActionScripts.fill(selector, text)) != "true") {
                            return@withContext AdapterOutcome.Failed
                        }
                        Pair(null, mapOf("kind" to "fill"))
                    }
                    Operation.SCROLL -> {
                        val beforeScroll =
                            evaluate(view, R5WebViewActionScripts.SCROLL)
                                ?: return@withContext AdapterOutcome.Failed
                        if (evaluate(view, R5WebViewActionScripts.scroll(command.scrollDeltaX, command.scrollDeltaY)) != "true") {
                            return@withContext AdapterOutcome.Failed
                        }
                        Pair(beforeScroll, mapOf("kind" to "scroll"))
                    }
                    Operation.SCREENSHOT -> {
                        // Only the app's private virtual display supplies hidden frames.
                        // Foreground/system or other-app capture is never a fallback.
                        val png = privateRenderer.captureFreshPng(command.captureRequest) ?: return@withContext AdapterOutcome.Failed
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
            if (entry.first != evidenceId ||
                recorded.commandId != command.commandId ||
                recorded.tabId != after.tabId ||
                before.rendererIdentity != after.rendererIdentity ||
                recorded.identity != after.rendererIdentity
            ) {
                proof = null
                return@withContext false
            }
            // Independently read current effect; a DOM/script callback alone
            // cannot claim a remote mutation has succeeded.
            val live = ctx.tabStore?.activeTab ?: return@withContext false
            if (actualTarget(after.tabId) != after) return@withContext false
            when (command.operation) {
                Operation.NAVIGATE -> live.webView.url?.substringBefore("#") == recorded.expected?.substringBefore("#")
                Operation.FILL, Operation.TYPE -> {
                    val selector = command.selector ?: return@withContext false
                    val input = command.inputText ?: return@withContext false
                    evaluate(live.webView, R5WebViewActionScripts.verifyFill(selector, input)) == "true"
                }
                Operation.SCROLL -> {
                    val final = evaluate(live.webView, R5WebViewActionScripts.SCROLL)
                    final != null && final != recorded.expected
                }
                Operation.BACK, Operation.FORWARD ->
                    live.webView.url != recorded.expected || before.documentEpoch != after.documentEpoch
                Operation.CLICK -> {
                    val oldFingerprint = recorded.payload["preFingerprint"] as? String
                    val freshFingerprint = evaluate(live.webView, R5WebViewActionScripts.DOCUMENT_FINGERPRINT)
                    val contentChanged = oldFingerprint != null && freshFingerprint != null && freshFingerprint != oldFingerprint
                    live.webView.url != recorded.expected ||
                        before.documentEpoch != after.documentEpoch || contentChanged
                }
                Operation.SELECT -> {
                    val selector = command.selector ?: return@withContext false
                    val value = command.inputText ?: return@withContext false
                    evaluate(live.webView, R5WebViewActionScripts.verifySelect(selector, value)) == "true"
                }
                Operation.WAIT -> evaluate(live.webView, R5WebViewActionScripts.waitFor(command.selector)) == "true"
                Operation.SCREENSHOT -> privateRenderer.canDeliverFrame()
                Operation.READ_DOM -> recorded.expected?.isNotBlank() == true
                else -> false
            }
        }

    private fun safeScreen(): Boolean =
        activity.getSystemService(PowerManager::class.java).isInteractive &&
            !activity.getSystemService(KeyguardManager::class.java).isDeviceLocked

    /** Called only after a VERIFIED_EFFECT receipt and final grant recheck. */
    suspend fun takePayload(evidenceId: String?): Map<String, Any?>? =
        withContext(Dispatchers.Main.immediate) {
            if (proof?.first != evidenceId) return@withContext null
            proof?.second?.payload.also { proof = null }
        }

    fun clearPendingEvidence() {
        proof = null
    }

    private companion object {
        const val CLICK_SETTLEMENT_TIMEOUT_MS = 7_500L
        const val CLICK_SETTLEMENT_POLLS = 70
        const val CLICK_SETTLEMENT_POLL_MS = 100L
    }

    private suspend fun evaluate(
        view: WebView,
        script: String,
    ): String? =
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
