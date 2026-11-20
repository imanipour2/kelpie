package com.kelpie.browser.capture

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/** Real private-display/WebView ownership, using only the native-reviewed local synthetic page. */
@RunWith(AndroidJUnit4::class)
class R5PrivateWebViewLifecycleInstrumentedTest {
    @Test
    fun foregroundArrivalDuringReleaseRestoresTheSameViewAndAllowsTheNextTransfer() =
        runBlocking {
            withFixture { fixture ->
                coroutineScope {
                    withContext(Dispatchers.Main.immediate) { fixture.holdRelease = true }
                    val transfer = async { fixture.renderer.start(fixture.view) }
                    withTimeout(5_000L) { fixture.releaseRequested.await() }

                    withContext(Dispatchers.Main.immediate) {
                        assertEquals(PrivateWebViewDisplay.State.RELEASING_COMPOSE, fixture.renderer.state.value)
                        assertSame(fixture.activity.foreground, fixture.view.parent)
                        // Reproduce Resume arriving before the pending release can remove the view.
                        // Visibility immediately fences frames, but cannot abandon the host lease.
                        fixture.renderer.setActivityVisible(true)
                        assertFalse(fixture.renderer.canDeliverFrame())
                        fixture.completeRelease()
                    }

                    assertTrue("Foreground arrival must not orphan an admitted host transfer", transfer.await())
                    assertNull(fixture.renderer.captureFreshPng())
                    fixture.restoreForeground()
                    fixture.assertForegroundOwner()

                    withContext(Dispatchers.Main.immediate) { fixture.holdRelease = false }
                    assertTrue("A completed foreground restoration must permit the next transfer", fixture.renderer.start(fixture.view))
                    withContext(Dispatchers.Main.immediate) { fixture.renderer.setActivityVisible(false) }
                    fixture.assertPrivateOwner()
                    fixture.restoreForeground()
                    fixture.assertForegroundOwner()
                }
            }
        }

    @Test
    fun privateWebViewProducesFreshVerifiedPixelsAndForegroundReturnRejectsInflightCapture() =
        runBlocking {
            withFixture { fixture ->
                assertTrue("The real private WebView must reach committed readiness", fixture.renderer.start(fixture.view))
                withContext(Dispatchers.Main.immediate) { fixture.renderer.setActivityVisible(false) }
                fixture.assertPrivateOwner()

                val first = fixture.verifiedFrame()
                val second = fixture.verifiedFrame()
                assertNotEquals(first["frameNonce"], second["frameNonce"])
                assertTrue((second["producerTimestampNanos"] as Long) > (first["producerTimestampNanos"] as Long))
                assertTrue((second["imageCallbackSequence"] as Int) > (first["imageCallbackSequence"] as Int))

                val stale =
                    withContext(Dispatchers.Main.immediate) {
                        // Begin a real capture until its first asynchronous WebView callback.
                        // Fence it in the same Main turn, before that callback can be delivered.
                        val pending = async(start = CoroutineStart.UNDISPATCHED) { fixture.renderer.captureFreshPng() }
                        assertFalse("A WebView capture must await native callbacks", pending.isCompleted)
                        fixture.renderer.setActivityVisible(true)
                        pending
                    }
                assertNull("An admitted capture cannot outlive foreground arrival", stale.await())
                assertNull(fixture.renderer.captureFreshPng())

                fixture.restoreForeground()
                fixture.assertForegroundOwner()
            }
        }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val activity = AtomicReference<R5PrivateWebViewFixtureActivity>()
        ActivityScenario.launch(R5PrivateWebViewFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity.set(it) }
            val fixture = withContext(Dispatchers.Main.immediate) { Fixture(checkNotNull(activity.get())) }
            try {
                // This bounds the harness only. Production visual/frame deadlines remain unchanged.
                withTimeout(40_000L) {
                    fixture.awaitForegroundGeometry()
                    WebViewCapturePrivacy.loadReviewedFixtureForTesting(fixture.view)
                    fixture.assertForegroundOwner()
                    block(fixture)
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) { fixture.renderer.dispose() }
            }
        }
    }

    private class Fixture(
        val activity: R5PrivateWebViewFixtureActivity,
    ) {
        val view: WebView = activity.view
        val releaseRequested = CompletableDeferred<Unit>()
        var holdRelease = false
        private var foregroundDisplayId: Int? = null
        val renderer =
            PrivateWebViewDisplay(
                activity,
                composeRelease = {
                    releaseRequested.complete(Unit)
                    if (!holdRelease) completeRelease()
                },
                composeRestore = { activity.restoreOriginalView() },
                useNativeFrameProof = true,
            )

        fun completeRelease() {
            assertSame(activity.foreground, view.parent)
            activity.foreground.removeView(view)
        }

        suspend fun awaitForegroundGeometry() =
            withTimeout(5_000L) {
                while (true) {
                    val ready =
                        withContext(Dispatchers.Main.immediate) {
                            if (view.isAttachedToWindow && view.width > 0 && view.height > 0) {
                                foregroundDisplayId = checkNotNull(view.display).displayId
                                true
                            } else {
                                false
                            }
                        }
                    if (ready) return@withTimeout
                    delay(16L)
                }
            }

        suspend fun assertForegroundOwner() =
            withContext(Dispatchers.Main.immediate) {
                assertSame(activity.view, view)
                assertSame(activity.foreground, view.parent)
                assertEquals(1, activity.foreground.childCount)
                assertSame(view, activity.foreground.getChildAt(0))
                assertTrue(view.isAttachedToWindow)
                assertTrue(view.isHardwareAccelerated)
                assertEquals(foregroundDisplayId, view.display?.displayId)
                assertEquals(PrivateWebViewDisplay.State.IDLE, renderer.state.value)
                assertFalse(renderer.canDeliverFrame())
                assertFalse(renderer.isOwnedBy(view))
                assertNull(renderer.captureFreshPng())
            }

        suspend fun assertPrivateOwner() =
            withContext(Dispatchers.Main.immediate) {
                assertSame(activity.view, view)
                assertEquals(0, activity.foreground.childCount)
                assertNotEquals(foregroundDisplayId, view.display?.displayId)
                assertTrue(view.isAttachedToWindow)
                assertTrue("The private Presentation must provide a hardware-accelerated WebView", view.isHardwareAccelerated)
                assertEquals(PrivateWebViewDisplay.State.PRIVATE_DISPLAY, renderer.state.value)
                assertTrue(renderer.canDeliverFrame())
                assertTrue(renderer.isOwnedBy(view))
            }

        suspend fun restoreForeground() {
            withContext(Dispatchers.Main.immediate) { renderer.setActivityVisible(true) }
            renderer.stop()
            assertTrue("The original foreground host and CSS viewport must be restored", renderer.verifyRestored())
        }

        suspend fun verifiedFrame(): Map<String, Any> {
            val result = renderer.captureFreshPng()
            assertNotNull("No native/document-verified frame: ${renderer.qaDiagnostics()}", result)
            val frame = checkNotNull(result)
            assertEquals("NATIVE_AND_DOCUMENT_PIXEL_PAIR", frame["freshnessProof"])
            assertEquals(true, frame["proofRegionsRedacted"])
            assertEquals("REVIEWED_SYNTHETIC_FIXTURE_ONLY", frame["redactionStatus"])
            assertTrue((frame["producerTimestampNanos"] as Long) > 0L)
            val bytes = Base64.getDecoder().decode(frame["image"] as String)
            val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            try {
                assertEquals(0xfff2ce41.toInt(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 4))
                assertEquals(0xff4cd378.toInt(), bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4))
                assertEquals(0xff000000.toInt(), bitmap.getPixel(12, 12))
                assertEquals(0xff000000.toInt(), bitmap.getPixel(12, 36))
            } finally {
                bitmap.recycle()
                bytes.fill(0)
            }
            return frame
        }
    }
}

/** Test APK only: one same-instance WebView and a deterministic native ownership handoff. */
class R5PrivateWebViewFixtureActivity : Activity() {
    lateinit var foreground: FrameLayout
    lateinit var view: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        foreground = FrameLayout(this)
        view = WebView(this).apply { settings.javaScriptEnabled = true }
        restoreOriginalView()
        setContentView(foreground)
    }

    fun restoreOriginalView() {
        if (view.parent !== foreground) {
            (view.parent as? ViewGroup)?.removeView(view)
            foreground.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        view.requestLayout()
        view.postInvalidateOnAnimation()
    }

    override fun onDestroy() {
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
        super.onDestroy()
    }
}
