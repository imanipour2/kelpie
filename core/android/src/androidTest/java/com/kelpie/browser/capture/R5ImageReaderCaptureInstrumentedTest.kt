package com.kelpie.browser.capture

import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Surface/ImageReader fixture; this does not claim WebView/HOME/S24 acceptance. */
@RunWith(AndroidJUnit4::class)
class R5ImageReaderCaptureInstrumentedTest {
    private val page = R5PageFrameProof.Samples(40, 56, 12, R5RgbaFrameBuffer.Region(32, 0, 64, 24))

    @Test fun twentyFiveProducerFramesAreOwnedOnceAndProofPixelsAreRemoved() =
        runBlocking {
            val worker = HandlerThread("KelpieFrameFixture").apply { start() }
            val reader = ImageReader.newInstance(64, 64, PixelFormat.RGBA_8888, 2)
            val acquisition = R5PrivateFrameAcquisition { it == 1L }
            reader.setOnImageAvailableListener({ acquisition.onImage(it) }, Handler(worker.looper))
            try {
                var previousTimestamp = 0L
                repeat(25) { index ->
                    val token = R5FrameProofEpochSequence.Token(1L, index + 1, 0x135700 + index, 0x2F9B00 + index)
                    val request = R5PrivateFrameAcquisition.Request(reader, 1L, 64, 64, token.primaryRgb, token, page)
                    assertTrue(acquisition.arm(request))
                    request.visualReady = true
                    produce(reader, token, gray = false)
                    val frame = withTimeout(3000L) { request.result.awaitTake() }
                    assertNotNull(frame)
                    frame!!
                    try {
                        assertFalse(frame.bitmap.isRecycled)
                        assertTrue(frame.producerTimestampNanos > previousTimestamp)
                        previousTimestamp = frame.producerTimestampNanos
                        assertEquals(Color.BLACK, frame.bitmap.getPixel(12, 12))
                        assertEquals(Color.BLACK, frame.bitmap.getPixel(page.firstX, page.y))
                        assertEquals(Color.GREEN, frame.bitmap.getPixel(32, 56))
                    } finally {
                        acquisition.finish(request)
                        frame.bitmap.recycle()
                    }
                }
            } finally {
                acquisition.cancel()
                acquisition.closeReader(reader)
                worker.quitSafely()
                worker.join(2000L)
            }
        }

    @Test fun grayWebViewWithFreshHostAndDocumentMarkersIsStillRefused() =
        runBlocking {
            val worker = HandlerThread("KelpieGrayFixture").apply { start() }
            val reader = ImageReader.newInstance(64, 64, PixelFormat.RGBA_8888, 2)
            val acquisition = R5PrivateFrameAcquisition { true }
            reader.setOnImageAvailableListener({ acquisition.onImage(it) }, Handler(worker.looper))
            try {
                val token = R5FrameProofEpochSequence.Token(1L, 1, 0x135700, 0x2F9B00)
                val request = R5PrivateFrameAcquisition.Request(reader, 1L, 64, 64, token.primaryRgb, token, page)
                assertTrue(acquisition.arm(request))
                request.visualReady = true
                produce(reader, token, gray = true)
                assertNull(withTimeout(3000L) { request.result.awaitTake() })
                assertEquals("NO_FRESH_FRAME", acquisition.outcome)
                acquisition.finish(request)
            } finally {
                acquisition.cancel()
                acquisition.closeReader(reader)
                worker.quitSafely()
                worker.join(2000L)
            }
        }

    private fun produce(
        reader: ImageReader,
        token: R5FrameProofEpochSequence.Token,
        gray: Boolean,
    ) {
        val canvas = reader.surface.lockCanvas(null)
        val paint = Paint()
        try {
            canvas.drawColor(if (gray) Color.GRAY else Color.YELLOW)
            if (!gray) {
                paint.color = Color.GREEN
                canvas.drawRect(0f, 48f, 64f, 64f, paint)
            }
            paint.color = Color.BLACK or token.primaryRgb
            canvas.drawRect(0f, 0f, 24f, 24f, paint)
            canvas.drawRect(32f, 0f, 48f, 24f, paint)
            paint.color = Color.BLACK or token.secondaryRgb
            canvas.drawRect(0f, 24f, 24f, 48f, paint)
            canvas.drawRect(48f, 0f, 64f, 24f, paint)
        } finally {
            reader.surface.unlockCanvasAndPost(canvas)
        }
    }
}
