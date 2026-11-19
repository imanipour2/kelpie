package com.kelpie.browser.capture

import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kelpie.browser.BuildConfig
import com.kelpie.browser.handlers.ScreenshotRefusal
import com.kelpie.browser.handlers.requireSyntheticScreenshotFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest
import java.util.UUID
import java.util.WeakHashMap
import kotlin.coroutines.resume

/**
 * Fail-closed pixel-export policy shared by foreground, R5 and diagnostic routes.
 * URL equality alone never authorizes pixels. Only the bounded native test loader
 * below registers provenance; normal navigation and loadDataWithBaseURL cannot.
 * Real-page redaction remains unavailable until an independently verified native
 * pixel provider exists. A sanitized DOM snapshot is still available separately.
 */
internal object WebViewCapturePrivacy {
    private data class Fixture(
        val nonce: String,
    )

    private val reviewed = WeakHashMap<WebView, Fixture>()
    private const val FIXTURE_URL = "https://kai-fixture.invalid/"

    suspend fun inspect(webView: WebView): CapturePrivacyEvidence =
        withContext(Dispatchers.Main.immediate) {
            if (webViewActivity(webView)
                    ?.window
                    ?.attributes
                    ?.flags
                    ?.and(WindowManager.LayoutParams.FLAG_SECURE)
                    ?.let { it != 0 } == true
            ) {
                throw ScreenshotRefusal("NO_CAPTURE_SECURE_CONTENT", "Protected windows cannot be exported")
            }
            requireSyntheticScreenshotFixture(BuildConfig.DEBUG, webView.url)
            val fixture =
                reviewed[webView]
                    ?: throw ScreenshotRefusal("NEEDS_HUMAN", "No native provenance for a reviewed pixel fixture")
            val raw =
                withTimeout(2000L) {
                    suspendCancellableCoroutine<String> { continuation ->
                        webView.evaluateJavascript(capturePrivacyScript()) { result ->
                            if (continuation.isActive) continuation.resume(result ?: "null")
                        }
                    }
                }
            if (reviewed[webView] !== fixture || webView.url != FIXTURE_URL) {
                throw ScreenshotRefusal("OBSERVATION_RACE", "Native capture document was replaced")
            }
            decode(raw, System.identityHashCode(webView), fixture.nonce)
        }

    private fun decode(
        raw: String,
        renderer: Int,
        nonce: String,
    ): CapturePrivacyEvidence {
        if (raw.length > 2048) throw ScreenshotRefusal("FRAME_PRIVACY_UNVERIFIED", "Invalid privacy evidence")
        val result =
            runCatching { Json.parseToJsonElement(Json.decodeFromString<String>(raw)).jsonObject }.getOrNull()
                ?: throw ScreenshotRefusal("FRAME_PRIVACY_UNVERIFIED", "Missing privacy evidence")
        if ((result["safe"] as? JsonPrimitive)?.booleanOrNull != true) {
            throw ScreenshotRefusal("FRAME_PRIVACY_UNVERIFIED", "Sensitive or opaque image content was refused")
        }
        val origin = (result["timeOrigin"] as? JsonPrimitive)?.doubleOrNull
        val digest = (result["digest"] as? JsonPrimitive)?.contentOrNull
        val width = (result["width"] as? JsonPrimitive)?.intOrNull ?: 0
        val height = (result["height"] as? JsonPrimitive)?.intOrNull ?: 0
        val x = (result["scrollX"] as? JsonPrimitive)?.doubleOrNull
        val y = (result["scrollY"] as? JsonPrimitive)?.doubleOrNull
        val dpr = (result["devicePixelRatio"] as? JsonPrimitive)?.doubleOrNull
        if (origin == null ||
            !origin.isFinite() ||
            origin <= 0 ||
            digest?.matches(Regex("[0-9a-f]{8}")) != true ||
            width !in 1..8192 ||
            height !in 1..8192 ||
            x == null ||
            y == null ||
            !x.isFinite() ||
            !y.isFinite() ||
            dpr == null ||
            !dpr.isFinite() ||
            dpr <= 0 ||
            dpr > 8
        ) {
            throw ScreenshotRefusal("FRAME_PRIVACY_UNVERIFIED", "Invalid capture geometry or document")
        }
        val stamp = MessageDigest.getInstance("SHA-256").digest("$nonce:$origin".toByteArray()).joinToString("") { "%02x".format(it) }
        return CapturePrivacyEvidence(renderer, stamp, digest, width, height, x, y, dpr)
    }

    /** Test infrastructure only: no HTTP/MCP/UI route calls this loader. */
    suspend fun loadReviewedFixtureForTesting(webView: WebView) =
        withContext(Dispatchers.Main.immediate) {
            check(BuildConfig.DEBUG) { "Synthetic capture fixtures require a debug build" }
            reviewed.remove(webView)
            val ready = CompletableDeferred<Unit>()
            val fixture = Fixture(UUID.randomUUID().toString())
            var started = 0
            var registered = false
            webView.webViewClient =
                object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView,
                        url: String?,
                        favicon: android.graphics.Bitmap?,
                    ) {
                        reviewed.remove(view)
                        started += 1
                    }

                    override fun onPageFinished(
                        view: WebView,
                        url: String?,
                    ) {
                        if (!registered && started == 1 && url == FIXTURE_URL) {
                            registered = true
                            reviewed[view] = fixture
                            ready.complete(Unit)
                        }
                    }
                }
            webView.loadDataWithBaseURL(FIXTURE_URL, REVIEWED_FIXTURE, "text/html", "UTF-8", null)
            try {
                withTimeout(5000L) { ready.await() }
            } catch (error: Exception) {
                reviewed.remove(webView)
                throw error
            }
        }

    private const val REVIEWED_FIXTURE = """
        <!doctype html><html><head><meta charset="utf-8">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'none'; base-uri 'none'; form-action 'none'">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Kelpie synthetic capture fixture</title>
        <style>body{margin:0;font:24px sans-serif;background:#ffffff;color:#101050}
        section{height:50vh;box-sizing:border-box;padding:20px}#first{background:#f2ce41}#second{background:#4cd378}</style>
        </head><body><section id="first">Synthetic public fixture A</section>
        <section id="second">Synthetic public fixture B</section></body></html>
    """
}
