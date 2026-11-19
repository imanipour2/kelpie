package com.kelpie.browser.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.Image
import android.media.ImageReader
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Owns exactly one pending frame, never the WebView, consent, display or lifecycle state. */
internal class R5PrivateFrameAcquisition(
    private val deliveryAllowed: (Long) -> Boolean,
) {
    internal class Request(
        val reader: ImageReader,
        val generation: Long,
        val width: Int,
        val height: Int,
        val primaryRgb: Int,
        val token: R5FrameProofEpochSequence.Token?,
        @Volatile var page: R5PageFrameProof.Samples?,
    ) {
        val result = OneShotFrameBuffer<Frame> { it.bitmap.recycle() }

        @Volatile var visualReady = false
    }

    internal data class Frame(
        val bitmap: Bitmap,
        val producerTimestampNanos: Long,
        val acquiredAtNanos: Long,
        val sequence: Int,
    )

    private val pending = AtomicReference<Request?>(null)
    private val imageLock = Any()
    private val callbackCount = AtomicInteger(0)
    private val matchingFrameCount = AtomicInteger(0)
    private val lastProducerTimestamp = AtomicLong(0L)

    @Volatile var outcome = "NOT_REQUESTED"
        private set

    val hasPending: Boolean get() = pending.get() != null

    fun diagnostics(): Map<String, Any> =
        mapOf(
            "imageCallbacks" to callbackCount.get(),
            "matchedNonceFrames" to matchingFrameCount.get(),
            "lastFrameOutcome" to outcome,
        )

    fun reset() {
        cancel()
        callbackCount.set(0)
        matchingFrameCount.set(0)
        lastProducerTimestamp.set(0L)
        outcome = "DISPLAY_INITIALIZING"
    }

    fun arm(request: Request): Boolean {
        if (!deliveryAllowed(request.generation) || !pending.compareAndSet(null, request)) return false
        outcome = "WAITING_FOR_FRESH_NONCE"
        return true
    }

    fun isCurrent(request: Request): Boolean = pending.get() === request && deliveryAllowed(request.generation)

    fun reject(
        request: Request,
        code: String,
    ) {
        if (pending.get() === request) {
            outcome = code
            request.result.fail()
        }
    }

    fun finish(request: Request) {
        pending.compareAndSet(request, null)
        request.result.close()
    }

    fun cancel() {
        pending.getAndSet(null)?.result?.close()
    }

    fun closeReader(reader: ImageReader) {
        synchronized(imageLock) {
            try {
                reader.setOnImageAvailableListener(null, null)
            } finally {
                reader.close()
            }
        }
    }

    fun onImage(reader: ImageReader) {
        synchronized(imageLock) {
            val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
            callbackCount.incrementAndGet()
            try {
                val request = pending.get() ?: return
                if (request.reader !== reader || !isCurrent(request) || !request.result.isOpen || !request.visualReady) return
                val pixels = runCatching { pixels(image, request) }.getOrNull()
                if (pixels == null) {
                    reject(request, "FRAME_BUFFER_INVALID")
                    return
                }
                if (!matches(pixels, request)) {
                    outcome = "FRAME_MISMATCH"
                    return
                }
                if (image.timestamp <= 0L || image.timestamp <= lastProducerTimestamp.get()) {
                    outcome = "STALE_PRODUCER_FRAME"
                    return
                }
                val excluded = proofRegions(request)
                if (request.token != null && !pixels.hasPageContent(excluded)) {
                    reject(request, "NO_FRESH_FRAME")
                    return
                }
                if (!isCurrent(request) || !request.result.isOpen) return
                val bitmap = runCatching { bitmap(pixels, excluded) }.getOrNull()
                if (bitmap == null) {
                    reject(request, "FRAME_COPY_FAILED")
                    return
                }
                val frame = Frame(bitmap, image.timestamp, SystemClock.elapsedRealtimeNanos(), callbackCount.get())
                if (request.result.publish(frame)) {
                    lastProducerTimestamp.set(image.timestamp)
                    matchingFrameCount.incrementAndGet()
                    outcome = "FRESH_DOCUMENT_AND_NATIVE_NONCE_MATCHED"
                }
            } finally {
                image.close()
            }
        }
    }

    private fun pixels(
        image: Image,
        request: Request,
    ): R5RgbaFrameBuffer {
        require(image.width == request.width && image.height == request.height)
        val crop = image.cropRect
        // A cropped image cannot claim to be the complete requested viewport.
        require(crop.left == 0 && crop.top == 0 && crop.width() == image.width && crop.height() == image.height)
        val plane = image.planes.single()
        return R5RgbaFrameBuffer(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride)
    }

    private fun matches(
        pixels: R5RgbaFrameBuffer,
        request: Request,
    ): Boolean {
        val token = request.token
        if (token == null) {
            return PrivateCaptureQaPixelProof.matches(request.primaryRgb, pixels.argbAt(pixels.width / 2, pixels.height / 2))
        }
        val page = request.page ?: return false
        return R5FrameProofVerifier.matches(pixels, token, page)
    }

    private fun proofRegions(request: Request): List<R5RgbaFrameBuffer.Region> =
        if (request.token != null) {
            listOfNotNull(R5RgbaFrameBuffer.Region(0, 0, 24, 48), request.page?.region)
        } else {
            emptyList()
        }

    private fun bitmap(
        pixels: R5RgbaFrameBuffer,
        proofRegions: List<R5RgbaFrameBuffer.Region>,
    ): Bitmap {
        val values = pixels.copyArgb()
        val output = Bitmap.createBitmap(pixels.width, pixels.height, Bitmap.Config.ARGB_8888)
        try {
            output.setPixels(values, 0, pixels.width, 0, 0, pixels.width, pixels.height)
            val canvas = Canvas(output)
            val paint = Paint().apply { color = Color.BLACK }
            proofRegions.forEach { region ->
                canvas.drawRect(region.left.toFloat(), region.top.toFloat(), region.right.toFloat(), region.bottom.toFloat(), paint)
            }
            return output
        } catch (error: Throwable) {
            output.recycle()
            throw error
        } finally {
            values.fill(0)
        }
    }
}
