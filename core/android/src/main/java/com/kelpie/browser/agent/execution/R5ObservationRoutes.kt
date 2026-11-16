package com.kelpie.browser.agent.execution

import android.app.KeyguardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.PowerManager
import android.os.SystemClock
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.kelpie.browser.agent.session.AgentSessionNotification
import com.kelpie.browser.agent.session.AndroidAgentProcessSession
import com.kelpie.browser.browser.BrowserTab
import com.kelpie.browser.handlers.HandlerContext
import com.kelpie.browser.network.Router
import com.kelpie.browser.network.errorResponse
import com.kelpie.browser.network.successResponse
import com.kelpie.browser.network.verifiedClientId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deliberately limited R5 read-only pilot, distinct from AgentOperationRuntime.
 *
 * Only the *already verified* bearer principal and an existing native on-device
 * process grant can enter. This never enables legacy navigation, JS eval,
 * mutation, browser-control or MCP action routing. The caller may inspect only
 * the actual, on-screen Kelpie WebView. No MediaProjection, accessibility,
 * Android screen or other-app window is accessed.
 *
 * Background/private-renderer access belongs to the separate reviewed R5
 * AgentOperationRuntime + NativePort milestone, not this pilot.
 */
internal class R5ObservationRoutes(
    private val activity: ComponentActivity,
    private val ctx: HandlerContext,
) {
    fun register(router: Router) {
        router.register("agent-observe") { body ->
            val verifiedPrincipal = verifiedClientId()
            val generation = body["consentGeneration"] as? String
            val kind = body["kind"] as? String
            if (kind !in setOf("tabs", "dom", "screenshot")) {
                return@register errorResponse("INVALID_PARAMS", "kind must be tabs, dom or screenshot")
            }
            val result =
                withContext(Dispatchers.Main.immediate) {
                    if (!authorized(verifiedPrincipal, generation)) {
                        return@withContext errorResponse("PERMISSION_REQUIRED", "Native consent is not active")
                    }
                    val store = ctx.tabStore ?: return@withContext errorResponse("NO_WEBVIEW", "Browser not ready")
                    val active = store.activeTab ?: return@withContext errorResponse("NO_WEBVIEW", "No active tab")
                    if (!foregroundOwned(active)) {
                        return@withContext errorResponse("USER_TAP_REQUIRED", "Open Kelpie in the foreground")
                    }
                    if (kind == "tabs") {
                        val entries =
                            store.tabs.value.take(MAX_TABS).map { tab ->
                                tabMetadata(tab) + mapOf("active" to (tab.id == active.id))
                            }
                        if (!authorized(verifiedPrincipal, generation)) {
                            return@withContext errorResponse("PERMISSION_REQUIRED", "Consent changed during observation")
                        }
                        return@withContext successResponse(mapOf("tabs" to entries))
                    }

                    if (!R5ObservationPolicy.sameTarget(
                            body["tabId"] as? String,
                            body["documentEpoch"] as? String,
                            active.id,
                            active.documentVersion,
                        )
                    ) {
                        return@withContext errorResponse("STALE_DOCUMENT", "Reselect the actual active tab")
                    }
                    val view = active.webView
                    val beforeTab = active.id
                    val beforeEpoch = active.documentVersion
                    val observed =
                        when (kind) {
                            "dom" -> observeDom(view)
                            "screenshot" -> observeScreenshot(view)
                            else -> null
                        } ?: return@withContext errorResponse("OBSERVATION_FAILED", "No fresh visible WebView observation")

                    // Never deliver DOM/pixels captured across STOP, lock, tab switch,
                    // page navigation, or Activity backgrounding.
                    val live = ctx.tabStore?.activeTab
                    if (!authorized(verifiedPrincipal, generation) ||
                        live?.id != beforeTab ||
                        live.documentVersion != beforeEpoch ||
                        live.webView !== view ||
                        !foregroundOwned(live)
                    ) {
                        return@withContext errorResponse("SESSION_REVOKED", "Observation discarded")
                    }
                    successResponse(tabMetadata(live) + observed)
                }
            result
        }
    }

    private fun authorized(
        principal: String?,
        generation: String?,
    ): Boolean {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        val power = activity.getSystemService(PowerManager::class.java)
        val unlocked = keyguard?.isDeviceLocked == false && power?.isInteractive == true
        return R5ObservationPolicy.permits(
            AndroidAgentProcessSession.snapshot(),
            principal,
            generation,
            unlocked,
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                activity.window.decorView.isShown,
            AgentSessionNotification.canNotify(activity),
        )
    }

    private fun foregroundOwned(tab: BrowserTab): Boolean =
        tab.webView.isAttachedToWindow &&
            tab.webView.isShown &&
            tab.webView.width > 0 &&
            tab.webView.height > 0

    private fun tabMetadata(tab: BrowserTab): Map<String, Any?> =
        mapOf(
            "tabId" to tab.id,
            "documentEpoch" to tab.documentVersion,
            // Query strings often contain account/session material; deliberately strip.
            "urlWithoutQuery" to tab.currentUrl.substringBefore("?").substringBefore("#").take(2048),
            "title" to tab.pageTitle.take(256),
        )

    /** Reads visible text only: never HTML, scripts, input values or cookies. */
    private suspend fun observeDom(view: WebView): Map<String, Any?>? {
        val raw =
            withTimeoutOrNull(5_000L) {
                suspendCancellableCoroutine<String> { cont ->
                    view.evaluateJavascript(VISIBLE_TEXT_SCRIPT) { response ->
                        if (cont.isActive) cont.resume(response ?: "null")
                    }
                }
            } ?: return null
        val text = runCatching { Json.decodeFromString<String>(raw) }.getOrNull() ?: return null
        return mapOf("kind" to "dom", "visibleText" to text.take(R5ObservationPolicy.MAX_VISIBLE_TEXT))
    }

    /**
     * Pixel source is WebView.draw() on the Android UI thread, after a visual
     * state callback; background captures intentionally fail closed.
     */
    private suspend fun observeScreenshot(view: WebView): Map<String, Any?>? {
        val bitmap =
            withTimeoutOrNull(5_000L) {
                suspendCancellableCoroutine<Bitmap?> { cont ->
                    view.postVisualStateCallback(
                        SystemClock.elapsedRealtimeNanos(),
                        object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                if (!cont.isActive || !view.isShown) return
                                var output: Bitmap? = null
                                try {
                                    val scale =
                                        min(
                                            1f,
                                            min(
                                                MAX_SCREEN_WIDTH.toFloat() / view.width,
                                                MAX_SCREEN_HEIGHT.toFloat() / view.height,
                                            ),
                                        )
                                    val width = (view.width * scale).roundToInt().coerceAtLeast(1)
                                    val height = (view.height * scale).roundToInt().coerceAtLeast(1)
                                    output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(output)
                                    canvas.scale(scale, scale)
                                    view.draw(canvas)
                                    if (cont.isActive) {
                                        cont.resume(output)
                                    } else {
                                        output.recycle()
                                    }
                                } catch (_: Exception) {
                                    output?.recycle()
                                    if (cont.isActive) cont.resume(null)
                                }
                            }
                        },
                    )
                }
            } ?: return null
        return try {
            val bytes =
                withContext(Dispatchers.Default) {
                    val stream = ByteArrayOutputStream()
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) null else stream.toByteArray()
                } ?: return null
            if (bytes.size > R5ObservationPolicy.MAX_PNG_BYTES) return null
            mapOf(
                "kind" to "screenshot",
                "image" to Base64.getEncoder().encodeToString(bytes),
                "mimeType" to "image/png",
                "width" to bitmap.width,
                "height" to bitmap.height,
                "freshVisualState" to true,
                "coordinateSpace" to "viewport-css-pixels",
            )
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        private const val MAX_TABS = 30
        private const val MAX_SCREEN_WIDTH = 1280
        private const val MAX_SCREEN_HEIGHT = 1600
        private const val VISIBLE_TEXT_SCRIPT =
            "(function(){return (document.body ? document.body.innerText : '').slice(0,24000);})()"
    }
}
