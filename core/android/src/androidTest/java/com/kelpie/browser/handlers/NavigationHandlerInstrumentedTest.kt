package com.kelpie.browser.handlers

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kelpie.browser.capture.PrivacyFixtureActivity
import com.kelpie.browser.network.Router
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Dedicated synthetic WebView and loopback server only; never operates a retained user tab. */
@RunWith(AndroidJUnit4::class)
class NavigationHandlerInstrumentedTest {
    @Test fun realHistoryAndReloadRequireObservedEffects() =
        runBlocking {
            withFixture { urls, _, ctx, router ->
                assertEquals("NO_BACK_HISTORY", code(router.handle("back", emptyMap()).second))
                assertEquals("NO_FORWARD_HISTORY", code(router.handle("forward", emptyMap()).second))
                val originalIndex = index(ctx)
                val moved = router.handle("navigate", mapOf("url" to urls.second)).second
                assertEquals("EFFECT_VERIFIED", moved["status"])
                assertEquals(originalIndex + 1, index(ctx))
                assertEquals("EFFECT_VERIFIED", router.handle("back", emptyMap()).second["status"])
                assertEquals(originalIndex, index(ctx))
                assertEquals("EFFECT_VERIFIED", router.handle("forward", emptyMap()).second["status"])
                assertEquals(originalIndex + 1, index(ctx))
                val beforeOrigin = ctx.navigationSnapshot()["timeOrigin"]
                assertEquals("EFFECT_VERIFIED", router.handle("reload", emptyMap()).second["status"])
                assertNotEquals(beforeOrigin, ctx.navigationSnapshot()["timeOrigin"])
                assertEquals(originalIndex + 1, index(ctx))
            }
        }

    @Test fun sameUrlPendingLoadCannotReuseTheOldCompleteDocumentAsSuccess() =
        runBlocking {
            withFixture { urls, blockResponses, _, router ->
                blockResponses.set(true)
                val result = router.handle("navigate", mapOf("url" to urls.first, "timeout" to 300)).second
                assertFalse(result["success"] == true)
                assertEquals("UNCERTAIN", code(result))
                val diagnostics = (result["error"] as Map<*, *>)["diagnostics"] as Map<*, *>
                assertEquals(true, diagnostics["mayHaveCompleted"])
            }
        }

    private data class FixtureUrls(
        val first: String,
        val second: String,
    )

    private suspend fun withFixture(block: suspend (FixtureUrls, AtomicBoolean, HandlerContext, Router) -> Unit) {
        val blocked = AtomicBoolean(false)
        val server =
            MockWebServer().apply {
                dispatcher =
                    object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse =
                            if (blocked.get()) {
                                MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                            } else {
                                MockResponse()
                                    .setHeader("Content-Type", "text/html")
                                    .setHeader("Cache-Control", "no-store")
                                    .setBody("<!doctype html><title>Synthetic navigation</title><p>Public fixture</p>")
                            }
                    }
            }
        try {
            val urls =
                withContext(Dispatchers.IO) {
                    server.start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0)
                    // MockWebServer.url() performs a reverse-DNS lookup. Keep all
                    // server work off Main and pass only immutable literal URLs to WebView.
                    val base = "http://127.0.0.1:${server.port}"
                    FixtureUrls("$base/first", "$base/second")
                }
            val activity = AtomicReference<PrivacyFixtureActivity>()
            ActivityScenario.launch(PrivacyFixtureActivity::class.java).use { scenario ->
                scenario.onActivity { activity.set(it) }
                val view = activity.get().view
                val ctx = HandlerContext().apply { webView = view }
                val router = Router().apply { NavigationHandler(ctx).register(this) }
                try {
                    withContext(Dispatchers.Main.immediate) { view.loadUrl(urls.first) }
                    withTimeout(10_000L) {
                        while (true) {
                            val state = ctx.navigationSnapshot()
                            val ready =
                                withContext(Dispatchers.Main.immediate) {
                                    view.isAttachedToWindow && view.width > 0 && view.height > 0 && view.progress == 100
                                }
                            if (ready && state["url"] == urls.first && state["readyState"] == "complete") break
                            delay(50L)
                        }
                    }
                    block(urls, blocked, ctx, router)
                } finally {
                    withContext(Dispatchers.Main.immediate) { ctx.browserRetirement.retireTarget(view) }
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { server.shutdown() }
        }
    }

    private suspend fun index(ctx: HandlerContext): Int = withContext(Dispatchers.Main.immediate) { ctx.webView!!.copyBackForwardList().currentIndex }

    private fun code(result: Map<String, Any?>): Any? = (result["error"] as? Map<*, *>)?.get("code")
}
