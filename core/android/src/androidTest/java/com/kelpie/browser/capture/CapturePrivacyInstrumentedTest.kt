package com.kelpie.browser.capture

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kelpie.browser.handlers.ScreenshotRefusal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** Synthetic data only. These Android tests are not substitutes for the S24 matrix. */
@RunWith(AndroidJUnit4::class)
class CapturePrivacyInstrumentedTest {
    @Test
    fun matchingBaseUrlWithoutNativeFixtureProvenanceIsRefused() =
        runBlocking {
            withFixtureActivity { _, view ->
                withContext(Dispatchers.Main.immediate) {
                    view.loadDataWithBaseURL(FIXTURE_URL, "<p>Not native reviewed</p>", "text/html", "UTF-8", FIXTURE_URL)
                }
                awaitDocument(view, FIXTURE_URL)
                val failure = refuse { WebViewCapturePrivacy.inspect(view) }
                assertEquals("NEEDS_HUMAN", failure.code)
                assertEquals("No native provenance for a reviewed pixel fixture", failure.message)
            }
        }

    @Test
    fun nativeLoaderProducesAReceiptButSensitivePixelsAreRefused() =
        runBlocking {
            withFixtureActivity { _, view ->
                WebViewCapturePrivacy.loadReviewedFixtureForTesting(view)
                val before = WebViewCapturePrivacy.inspect(view)
                before.requireSameDocument(WebViewCapturePrivacy.inspect(view))
                evaluate(view, "document.body.insertAdjacentHTML('beforeend','<input type=password value=SYNTHETIC_SECRET>')")
                assertEquals("FRAME_PRIVACY_UNVERIFIED", refuse { WebViewCapturePrivacy.inspect(view) }.code)
            }
        }

    @Test
    fun sameDocumentSceneChangesInvalidateCaptureReceipts() =
        runBlocking {
            withFixtureActivity { _, view ->
                WebViewCapturePrivacy.loadReviewedFixtureForTesting(view)
                val before = WebViewCapturePrivacy.inspect(view)
                evaluate(view, "document.querySelector('#first').textContent='Synthetic changed scene'")
                val after = WebViewCapturePrivacy.inspect(view)
                assertThrows(ScreenshotRefusal::class.java) { before.requireSameDocument(after) }
            }
        }

    @Test
    fun foregroundPixelCopyReturnsOnlyTheRegisteredFixture() =
        runBlocking {
            withFixtureActivity { activity, view ->
                WebViewCapturePrivacy.loadReviewedFixtureForTesting(view)
                val privacy = WebViewCapturePrivacy.inspect(view)
                val frame = ForegroundWebViewCapture.copy(view, activity) { !activity.isFinishing }
                assertNotNull(frame)
                try {
                    val bitmap = checkNotNull(frame).bitmap
                    assertEquals(0xfff2ce41.toInt(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 4))
                    assertEquals(0xff4cd378.toInt(), bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4))
                    val scaleX = bitmap.width.toDouble() / privacy.viewportWidth
                    val scaleY = bitmap.height.toDouble() / privacy.viewportHeight
                    // Both halves of the 48x24 CSS-pixel witness must be black in actual output pixels.
                    for (cssFromRight in listOf(36, 12)) {
                        assertEquals(
                            0xff000000.toInt(),
                            bitmap.getPixel((bitmap.width - cssFromRight * scaleX).toInt(), (12 * scaleY).toInt()),
                        )
                    }
                    assertEquals("DOCUMENT_PIXEL_PAIR", frame.metadata["freshnessProof"])
                    assertEquals(true, frame.metadata["proofRegionsRedacted"])
                    assertEquals(false, frame.metadata.containsKey("producerTimestampNanos"))
                } finally {
                    frame?.bitmap?.recycle()
                }
                withContext(Dispatchers.Main.immediate) { view.loadUrl("about:blank") }
                awaitDocument(view, "about:blank")
                assertEquals("NEEDS_HUMAN", refuse { WebViewCapturePrivacy.inspect(view) }.code)
            }
        }

    @Test
    fun nativeOverlayCannotLeakIntoFixturePixels() =
        runBlocking {
            withFixtureActivity { activity, view ->
                WebViewCapturePrivacy.loadReviewedFixtureForTesting(view)
                withContext(Dispatchers.Main.immediate) {
                    activity.addContentView(View(activity), ViewGroup.LayoutParams(view.width, view.height))
                }
                assertNull(ForegroundWebViewCapture.copy(view, activity) { true })
            }
        }

    @Test
    fun secureWindowRefusesPixelCopyBeforeAllocation() =
        runBlocking {
            withFixtureActivity { activity, view ->
                WebViewCapturePrivacy.loadReviewedFixtureForTesting(view)
                withContext(Dispatchers.Main.immediate) { activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                assertEquals("NO_CAPTURE_SECURE_CONTENT", refuse { ForegroundWebViewCapture.copy(view, activity) { true } }.code)
            }
        }

    private suspend fun withFixtureActivity(block: suspend (PrivacyFixtureActivity, WebView) -> Unit) {
        val activity = AtomicReference<PrivacyFixtureActivity>()
        ActivityScenario.launch(PrivacyFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity.set(it) }
            block(activity.get(), activity.get().view)
        }
    }

    private suspend fun evaluate(
        view: WebView,
        expression: String,
    ) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine<String> { continuation ->
            view.evaluateJavascript(expression) { result ->
                if (continuation.isActive) continuation.resume(result ?: "null")
            }
        }
    }

    /** Wait for both native history and the completed DOM, rather than loadUrl dispatch. */
    private suspend fun awaitDocument(
        view: WebView,
        url: String,
    ) = withTimeout(5000L) {
        while (true) {
            val nativeMatches = withContext(Dispatchers.Main.immediate) { view.url == url }
            val documentMatches = evaluate(view, "location.href === ${Json.encodeToString(url)} && document.readyState === 'complete'")
            if (nativeMatches && documentMatches == "true") return@withTimeout
            delay(16L)
        }
    }

    private suspend fun refuse(block: suspend () -> Unit): ScreenshotRefusal {
        try {
            block()
        } catch (error: ScreenshotRefusal) {
            return error
        }
        throw AssertionError("Unproven image capture was not refused")
    }

    companion object {
        private const val FIXTURE_URL = "https://kai-fixture.invalid/"
    }
}

/** Only the instrumentation APK declares this isolated synthetic fixture host. */
class PrivacyFixtureActivity : Activity() {
    lateinit var view: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = WebView(this)
        view.settings.javaScriptEnabled = true
        setContentView(view)
    }

    override fun onDestroy() {
        view.destroy()
        super.onDestroy()
    }
}
