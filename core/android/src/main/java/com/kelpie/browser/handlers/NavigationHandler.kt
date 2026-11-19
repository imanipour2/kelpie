package com.kelpie.browser.handlers

import android.net.Uri
import android.os.SystemClock
import android.webkit.WebView
import com.kelpie.browser.browser.BrowserTab
import com.kelpie.browser.network.Router
import com.kelpie.browser.network.errorResponse
import com.kelpie.browser.network.successResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class NavigationHandler(
    private val ctx: HandlerContext,
) {
    fun register(router: Router) {
        router.register("navigate") { navigate(it) }
        router.register("back") { perform(NavigationEffectProof.Action.BACK) }
        router.register("forward") { perform(NavigationEffectProof.Action.FORWARD) }
        router.register("reload") { perform(NavigationEffectProof.Action.RELOAD) }
        router.register("get-current-url") { getCurrentUrl() }
        router.register("set-home") { setHome(it) }
        router.register("get-home") { getHome() }
    }

    private suspend fun navigate(body: Map<String, Any?>): Map<String, Any?> {
        val url = body["url"] as? String ?: return errorResponse("MISSING_PARAM", "url is required")
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
        if (scheme != "http" && scheme != "https") return errorResponse("INVALID_URL", "Missing or invalid URL")
        val supplied = body["timeout"]
        val timeout = (supplied as? Number)?.toDouble() ?: DEFAULT_TIMEOUT_MS.toDouble()
        if ((supplied != null && supplied !is Number) || !timeout.isFinite() || timeout % 1 != 0.0 || timeout !in 100.0..30_000.0) {
            return errorResponse("INVALID_PARAMS", "timeout must be an integer between 100 and 30000 milliseconds")
        }
        return perform(NavigationEffectProof.Action.NAVIGATE, url, timeout.toLong())
    }

    /** Main dispatch is awaited and retains the immutable request guard across every suspension. */
    private suspend fun perform(
        action: NavigationEffectProof.Action,
        requestedUrl: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Map<String, Any?> {
        val view = ctx.webView ?: return errorResponse("NO_WEBVIEW", "No WebView")
        val requestGuard = ctx.captureOperationGuard()
        var dispatched = false
        return try {
            withContext(Dispatchers.Main.immediate) {
                val store = ctx.tabStore
                val tab = store?.tabs?.value?.firstOrNull { it.webView === view }

                fun sameRenderer(): Boolean =
                    ctx.webView === view &&
                        (store == null || (tab != null && store.activeTab === tab && store.tabs.value.any { it === tab })) &&
                        view.isAttachedToWindow &&
                        view.width > 0 &&
                        view.height > 0 &&
                        ctx.privateCaptureRestricted?.invoke() != true

                fun beforeAction(): Boolean = sameRenderer() && requestGuard?.beforeAction() != false && ctx.remoteDocumentGuard?.invoke() != false

                fun afterNavigation(): Boolean = sameRenderer() && requestGuard?.afterNavigation() != false

                if (!beforeAction()) return@withContext refused("SESSION_REVOKED", dispatched)
                // R5 uses native BrowserTab epochs. A legacy isolated Connect context
                // without TabStore can retain its existing fixed engine-time witness.
                if (requestGuard != null && tab == null) return@withContext refused("NO_DOCUMENT_EVIDENCE", dispatched)
                val initial =
                    withTimeoutOrNull(2_000L) { ctx.evaluateJSReturningJSON(INITIAL_STATE) }
                        ?: return@withContext refused("NO_DOCUMENT_EVIDENCE", dispatched)
                if (!beforeAction()) return@withContext refused("SESSION_REVOKED", dispatched)
                val before = observation(view, tab, initial)
                val proof = NavigationEffectProof(action, before, requestedUrl)
                proof.refusalBeforeDispatch()?.let { return@withContext refused(it, dispatched) }
                if (action == NavigationEffectProof.Action.BACK && !view.canGoBack()) return@withContext refused("NO_BACK_HISTORY", false)
                if (action == NavigationEffectProof.Action.FORWARD && !view.canGoForward()) return@withContext refused("NO_FORWARD_HISTORY", false)
                // No await is allowed between these checks and the native mutation.
                if (!beforeAction() || ctx.remoteNavigationStart?.invoke() == false) {
                    return@withContext refused("SESSION_REVOKED", dispatched)
                }
                currentCoroutineContext().ensureActive()
                ctx.lastNavigationError = null
                dispatched = true
                when (action) {
                    NavigationEffectProof.Action.NAVIGATE -> view.loadUrl(requireNotNull(requestedUrl))
                    NavigationEffectProof.Action.BACK -> view.goBack()
                    NavigationEffectProof.Action.FORWARD -> view.goForward()
                    NavigationEffectProof.Action.RELOAD -> view.reload()
                }
                val started = SystemClock.elapsedRealtime()
                withTimeoutOrNull(timeoutMs) {
                    awaitEffect(view, tab, proof, started, ::afterNavigation)
                } ?: uncertain()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (dispatched) uncertain() else refused("NO_DOCUMENT_EVIDENCE", false)
        }
    }

    private suspend fun awaitEffect(
        view: WebView,
        tab: BrowserTab?,
        proof: NavigationEffectProof,
        started: Long,
        admitted: () -> Boolean,
    ): Map<String, Any?> {
        while (true) {
            if (!admitted()) return refused("SESSION_REVOKED", true)
            if (ctx.lastNavigationError != null) return refused("NAVIGATION_ERROR", true)
            val state = ctx.navigationSnapshot()
            if (!admitted()) return refused("SESSION_REVOKED", true)
            if (ctx.lastNavigationError != null) return refused("NAVIGATION_ERROR", true)
            val sample = observation(view, tab, state)
            val now = SystemClock.elapsedRealtime()
            if (proof.observed(sample, now)) {
                return navigationResponse(
                    mapOf("url" to sample.url, "loadTime" to (now - started), "status" to "EFFECT_VERIFIED"),
                )
            }
            delay(NavigationEffectProof.MIN_STABLE_MS)
        }
    }

    /** Called only on Main, adjacent to the fixed DOM observation. */
    private fun observation(
        view: WebView,
        tab: BrowserTab?,
        dom: Map<String, Any?>,
    ): NavigationEffectProof.Observation {
        val nativeUrl = view.url.orEmpty()
        val observedUrl = dom["url"] as? String
        val timeOrigin = (dom["timeOrigin"] as? Number)?.toDouble()?.takeIf { it.isFinite() && it > 0 }
        val identity =
            tab?.documentVersion?.takeIf { it.isNotBlank() }?.let { "native:$it" }
                ?: timeOrigin?.let { "engine:$it" }.orEmpty()
        val history = view.copyBackForwardList()
        return NavigationEffectProof.Observation(
            documentIdentity = identity,
            url = if (observedUrl == null || observedUrl == nativeUrl) nativeUrl else "",
            historyIndex = history.currentIndex,
            historySize = history.size,
            loading = tab?.isLoading == true || view.progress < 100,
            complete = observedUrl != null && observedUrl == nativeUrl && dom["readyState"] == "complete" && view.progress == 100,
            engineTimeOrigin = timeOrigin,
        )
    }

    private fun refused(
        code: String,
        mayHaveCompleted: Boolean,
    ): Map<String, Any?> = errorResponse(code, "Navigation was refused or its native evidence became unavailable", mapOf("mayHaveCompleted" to mayHaveCompleted, "status" to code))

    private fun uncertain(): Map<String, Any?> =
        errorResponse("UNCERTAIN", "The dispatched navigation did not produce stable completion evidence", mapOf("mayHaveCompleted" to true, "status" to "UNCERTAIN"))

    private fun navigationResponse(result: Map<String, Any?>): Map<String, Any?> {
        val rawUrl = result["url"] as? String
        return successResponse(
            result + mapOf("url" to TabMetadataPrivacy.url(rawUrl), "title" to TabMetadataPrivacy.title(rawUrl), "titleRedacted" to true),
        )
    }

    private suspend fun getCurrentUrl(): Map<String, Any?> = navigationResponse(ctx.evaluateJSReturningJSON("({url: location.href})"))

    private fun setHome(body: Map<String, Any?>): Map<String, Any?> {
        val url = body["url"] as? String
        if (url.isNullOrEmpty()) return errorResponse("MISSING_PARAM", "url is required")
        com.kelpie.browser.browser.HomeStore
            .setUrl(url)
        return navigationResponse(mapOf("url" to url))
    }

    private fun getHome(): Map<String, Any?> = navigationResponse(mapOf("url" to com.kelpie.browser.browser.HomeStore.url))

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
        const val INITIAL_STATE = "({url:location.href,readyState:document.readyState,timeOrigin:performance.timeOrigin})"
    }
}
