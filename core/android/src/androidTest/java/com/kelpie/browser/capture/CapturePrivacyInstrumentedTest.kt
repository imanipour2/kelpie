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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
                    view.loadDataWithBaseURL("https://kai-fixture.invalid/", "<p>Not native reviewed</p>", "text/html", "UTF-8", null)
                }
                val failure = refuse { WebViewCapturePrivacy.inspect(view) }
                assertEquals("NEEDS_HUMAN", failure.code)
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
                val frame = ForegroundWebViewCapture.copy(view, activity) { !activity.isFinishing }
                assertNotNull(frame)
                if (frame != null) {
                    val bitmap = frame.bitmap
                    assertEquals(0xfff2ce41.toInt(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 4))
                    assertEquals(0xff4cd378.toInt(), bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4))
                    assertEquals("DOCUMENT_PIXEL_PAIR", frame.metadata["freshnessProof"])
                    assertEquals(true, frame.metadata["proofRegionsRedacted"])
                    assertEquals(false, frame.metadata.containsKey("producerTimestampNanos"))
                }
                frame?.bitmap?.recycle()
                withContext(Dispatchers.Main.immediate) { view.loadUrl("about:blank") }
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
        suspendCancellableCoroutine<Unit> { continuation ->
            view.evaluateJavascript(expression) {
                if (continuation.isActive) continuation.resume(Unit)
            }
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
