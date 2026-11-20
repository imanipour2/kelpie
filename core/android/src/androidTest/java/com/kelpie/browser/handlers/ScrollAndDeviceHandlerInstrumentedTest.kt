package com.kelpie.browser.handlers

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kelpie.browser.capture.PrivacyFixtureActivity
import com.kelpie.browser.device.DeviceInfo
import com.kelpie.browser.network.BrowserGatewayBinding
import com.kelpie.browser.network.HttpJson
import com.kelpie.browser.network.Router
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

/** Real HandlerContext expression wrapping and native gateway responses; synthetic loopback page only. */
@RunWith(AndroidJUnit4::class)
class ScrollAndDeviceHandlerInstrumentedTest {
    @Test
    fun rtlScrollThroughNativeMcpProducesMeasuredHorizontalAndVerticalEffects() =
        runBlocking {
            withFixture { ctx, router, _ ->
                val before = observe(ctx)
                val moved = extensionValue(router, "kelpie_scroll", mapOf("deltaX" to -180, "deltaY" to 260))
                val after = observe(ctx)
                assertEquals("VERIFIED_SCROLL_OFFSET", moved["postconditionStatus"])
                assertEquals(before.x, number(moved, "preScrollX"), 0.5)
                assertEquals(before.y, number(moved, "preScrollY"), 0.5)
                assertEquals(before.x - 180.0, after.x, 0.5)
                assertEquals(before.y + 260.0, after.y, 0.5)
                assertEquals(after.x, number(moved, "scrollX"), 0.5)
                assertEquals(after.y, number(moved, "scrollY"), 0.5)

                extensionValue(router, "kelpie_scroll", mapOf("deltaX" to 75, "deltaY" to -80))
                val returned = observe(ctx)
                assertEquals(before.x - 105.0, returned.x, 0.5)
                assertEquals(before.y + 180.0, returned.y, 0.5)
            }
        }

    @Test
    fun largeRtlOffsetsClampToMeasuredBoundsAndRepeatedBoundaryCannotClaimAnEffect() =
        runBlocking {
            withFixture { ctx, router, bounds ->
                val delta = mapOf("deltaX" to -100_000, "deltaY" to 100_000)
                val clamped = router.handle("scroll", delta).second
                assertEquals(true, clamped["success"])
                assertEquals("VERIFIED_SCROLL_OFFSET", clamped["postconditionStatus"])
                val edge = observe(ctx)
                assertEquals(bounds.minX, edge.x, 0.5)
                assertEquals(bounds.maxY, edge.y, 0.5)
                assertEquals(edge.x, number(clamped, "scrollX"), 0.5)
                assertEquals(edge.y, number(clamped, "scrollY"), 0.5)

                val unchanged = router.handle("scroll", delta).second
                assertEquals(false, unchanged["success"])
                assertEquals("AT_SCROLL_BOUNDARY", objectValue(unchanged["error"])["code"])
                assertEquals(edge, observe(ctx))

                val origin = router.handle("scroll", mapOf("deltaX" to 100_000, "deltaY" to -100_000)).second
                assertEquals(true, origin["success"])
                val restored = observe(ctx)
                assertEquals(bounds.maxX, restored.x, 0.5)
                assertEquals(0.0, restored.y, 0.5)
            }
        }

    @Test
    fun topAndBottomExpressionsReturnTheActualScrollOffsetThroughHandlerContext() =
        runBlocking {
            withFixture { ctx, router, _ ->
                val bottom = extensionValue(router, "kelpie_scroll_to_bottom")
                val atBottom = observe(ctx)
                assertEquals(atBottom.maxY, atBottom.y, 0.5)
                assertEquals(atBottom.y, number(bottom, "scrollY"), 0.5)

                val top = extensionValue(router, "kelpie_scroll_to_top")
                val atTop = observe(ctx)
                assertEquals(0.0, atTop.y, 0.5)
                assertEquals(atTop.y, number(top, "scrollY"), 0.5)
            }
        }

    @Test
    fun viewportAndDeviceInfoHaveSuccessfulNativeEnvelopesWhileMissingViewRemainsAnError() =
        runBlocking {
            withFixture { ctx, router, _ ->
                val expected = withContext(Dispatchers.Main.immediate) { ctx.webView!!.width to ctx.webView!!.height }
                for (viewport in listOf(router.handle("get-viewport", emptyMap()).second, extensionValue(router, "kelpie_get_viewport"))) {
                    assertEquals(true, viewport["success"])
                    assertEquals(expected.first.toDouble(), number(viewport, "width"), 0.0)
                    assertEquals(expected.second.toDouble(), number(viewport, "height"), 0.0)
                    assertTrue(number(viewport, "devicePixelRatio") > 0.0)
                    assertEquals("android", viewport["platform"])
                }
                for (device in listOf(router.handle("get-device-info", emptyMap()).second, extensionValue(router, "kelpie_get_device_info"))) {
                    assertEquals(true, device["success"])
                    assertEquals("synthetic-device", objectValue(device["device"])["id"])
                    assertEquals("Synthetic fixture", objectValue(device["device"])["name"])
                    assertEquals(640.0, number(objectValue(device["display"]), "width"), 0.0)
                    assertEquals("fixture-version", objectValue(device["app"])["version"])
                }

                val view = ctx.webView
                try {
                    ctx.webView = null
                    val refused = nativeExtension(router, "kelpie_get_viewport")
                    assertEquals(false, refused["ok"])
                    assertFalse(refused.containsKey("value"))
                } finally {
                    ctx.webView = view
                }
            }
        }

    private data class Position(
        val x: Double,
        val y: Double,
        val maxX: Double,
        val maxY: Double,
        val direction: String,
    )

    /** Independent DOM readback, not the production ScrollEffectEvidence parser or target calculation. */
    private suspend fun observe(ctx: HandlerContext): Position {
        val value =
            ctx.evaluateJSReturningJSON(
                "(()=>{const root=document.scrollingElement;return{x:scrollX,y:scrollY," +
                    "maxX:root.scrollWidth-innerWidth,maxY:root.scrollHeight-innerHeight," +
                    "direction:getComputedStyle(root).direction}})()",
            )
        return Position(number(value, "x"), number(value, "y"), number(value, "maxX"), number(value, "maxY"), value["direction"] as String)
    }

    private data class ScrollBounds(
        val minX: Double,
        val maxX: Double,
        val maxY: Double,
    )

    /**
     * Measure the fixture's actual native limits independently of ScrollEffectEvidence.
     * Mobile RTL overflow can give the layout viewport a negative rightmost offset.
     */
    private suspend fun establishScrollBounds(ctx: HandlerContext): ScrollBounds {
        var lastObserved: Position? = null
        val ready =
            withTimeoutOrNull(5_000L) {
                suspend fun observeEdge(
                    x: Int,
                    y: Int,
                ): Position {
                    val dispatched =
                        ctx.evaluateJSReturningJSON(
                            "(()=>{window.scrollTo({left:$x,top:$y,behavior:'instant'});return{dispatched:true}})()",
                        )
                    assertEquals(true, dispatched["dispatched"])
                    var previous = observe(ctx).also { lastObserved = it }
                    var stable = 0
                    while (stable < 3) {
                        delay(50L)
                        val current = observe(ctx)
                        stable = if (current == previous) stable + 1 else 0
                        previous = current
                        lastObserved = current
                    }
                    return previous
                }

                val leftBottom = observeEdge(-100_000, 100_000)
                val rightTop = observeEdge(100_000, 0)
                assertEquals("rtl", rightTop.direction)
                assertEquals(leftBottom.direction, rightTop.direction)
                assertEquals(leftBottom.maxX, rightTop.maxX, 0.5)
                assertEquals(leftBottom.maxY, rightTop.maxY, 0.5)
                assertTrue("Fixture needs horizontal travel: $leftBottom -> $rightTop", rightTop.x - leftBottom.x > 600.0)
                assertTrue("Fixture needs vertical travel: $leftBottom", leftBottom.y > 1200.0)
                assertEquals(leftBottom.maxY, leftBottom.y, 0.5)
                assertEquals(0.0, rightTop.y, 0.5)
                ScrollBounds(leftBottom.x, rightTop.x, leftBottom.y)
            }
        return ready ?: throw AssertionError("Synthetic RTL fixture did not settle at its measured bounds: $lastObserved")
    }

    private suspend fun extensionValue(
        router: Router,
        name: String,
        arguments: Map<String, Any?> = emptyMap(),
    ): Map<*, *> {
        val response = nativeExtension(router, name, arguments)
        assertEquals("Native gateway rejected $name: $response", true, response["ok"])
        return objectValue(response["value"]).also { assertEquals(true, it["success"]) }
    }

    private suspend fun nativeExtension(
        router: Router,
        name: String,
        arguments: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val (status, rpc) =
            router.handle(
                "mcp",
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to 1,
                    "method" to "tools/call",
                    "params" to mapOf("name" to "kelpie_extension", "arguments" to mapOf("name" to name, "arguments" to arguments)),
                ),
            )
        assertEquals(200, status)
        assertFalse("JSON-RPC error: $rpc", rpc.containsKey("error"))
        val result = objectValue(rpc["result"])
        val text = (result["content"] as List<*>).map(::objectValue).single { it["type"] == "text" }["text"] as String
        val response = checkNotNull(HttpJson.parseJsonBody(text))
        assertEquals(response["ok"] != true, result["isError"])
        return response
    }

    private suspend fun withFixture(block: suspend (HandlerContext, Router, ScrollBounds) -> Unit) {
        val server =
            MockWebServer().apply {
                dispatcher =
                    object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse =
                            MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setHeader("Cache-Control", "no-store").setBody(HTML)
                    }
            }
        try {
            val url =
                withContext(Dispatchers.IO) {
                    server.start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0)
                    "http://127.0.0.1:${server.port}/rtl-scroll"
                }
            val activity = AtomicReference<PrivacyFixtureActivity>()
            ActivityScenario.launch(PrivacyFixtureActivity::class.java).use { scenario ->
                scenario.onActivity { activity.set(it) }
                val view = activity.get().view
                val ctx = HandlerContext().apply { webView = view }
                val router = Router().apply { handlerContext = ctx }
                ScrollHandler(ctx).register(router)
                DeviceHandler(ctx, DeviceInfo("synthetic-device", "Synthetic fixture", "fixture", 640, 960, "127.0.0.1", 8420, "fixture-version", "fixture-build"), router).register()
                val gateway = BrowserGatewayBinding(router, ctx).also { it.register() }
                try {
                    withContext(Dispatchers.Main.immediate) {
                        // Match TabStore.createTab so this fixture exercises the production viewport configuration.
                        view.settings.loadWithOverviewMode = true
                        view.settings.useWideViewPort = true
                        view.loadUrl(url)
                    }
                    withTimeout(10_000L) {
                        while (true) {
                            val document = ctx.navigationSnapshot()
                            val attached = withContext(Dispatchers.Main.immediate) { view.isAttachedToWindow && view.width > 0 && view.height > 0 && view.progress == 100 }
                            if (attached && document["url"] == url && document["readyState"] == "complete") break
                            delay(50L)
                        }
                    }
                    val bounds = establishScrollBounds(ctx)
                    withTimeout(15_000L) { block(ctx, router, bounds) }
                } finally {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.Main.immediate) { ctx.browserRetirement.retireTarget(view) }
                        gateway.close()
                    }
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { server.shutdown() }
        }
    }

    private fun objectValue(value: Any?): Map<*, *> = value as? Map<*, *> ?: throw AssertionError("Expected fixture object: $value")

    private fun number(
        value: Map<*, *>,
        key: String,
    ): Double = (value[key] as? Number)?.toDouble() ?: throw AssertionError("Missing measured $key: $value")

    companion object {
        private val HTML =
            """
            <!doctype html><html dir="rtl"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Synthetic RTL scroll</title>
            <style>html,body{margin:0;padding:0;scroll-behavior:auto}main{width:calc(100vw + 1800px);height:calc(100vh + 6400px);background:#edf4ff}</style>
            </head><body><main>محتوای آزمون</main></body></html>
            """.trimIndent()
    }
}
