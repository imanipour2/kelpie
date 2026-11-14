package com.kelpie.virtualdisplayprobe

import android.app.Activity
import android.app.KeyguardManager
import android.app.Presentation
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.File

/**
 * Permission-free, offline, user-started QA. No user browser data or images
 * are saved. A hidden WebView is tested on its own private virtual display.
 */
class ProbeActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private var worker: HandlerThread? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var webView: WebView? = null

    @Volatile
    private var frame: PixelSample? = null
    private var generation = 0
    private var running = false
    private var visible = true
    private var passes = 0
    private var attempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
            }
        status =
            TextView(this).apply {
                textSize = 16f
                text = "Private VirtualDisplay test. Tap Start, then HOME. No phone screen recording or Internet access."
            }
        column.addView(status)
        column.addView(
            Button(this).apply {
                text = "Start 20-frame local fixture"
                setOnClickListener { start() }
            },
        )
        column.addView(
            Button(this).apply {
                text = "Open SAME-WebView migration QA"
                setOnClickListener { startActivity(Intent(this@ProbeActivity, SameWebViewActivity::class.java)) }
            },
        )
        column.addView(
            Button(this).apply {
                text = "Stop and release resources"
                setOnClickListener { stop("USER_STOP") }
            },
        )
        setContentView(column)
    }

    private fun start() {
        stop("RESTART")
        generation++
        val token = generation
        frame = null
        attempts = 0
        passes = 0
        File(filesDir, "virtual-display-probe.jsonl").writeText("")
        try {
            val thread = HandlerThread("VirtualDisplay-QA-Frames")
            thread.start()
            worker = thread
            val imageReader = ImageReader.newInstance(320, 480, PixelFormat.RGBA_8888, 2)
            reader = imageReader
            imageReader.setOnImageAvailableListener({ source ->
                try {
                    val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val plane = image.planes[0]
                        val offset = 240 * plane.rowStride + 160 * plane.pixelStride
                        val buffer = plane.buffer
                        if (offset + 2 < buffer.limit()) {
                            frame =
                                PixelSample(
                                    buffer.get(offset).toInt() and 255,
                                    buffer.get(offset + 1).toInt() and 255,
                                    buffer.get(offset + 2).toInt() and 255,
                                    SystemClock.elapsedRealtimeNanos(),
                                )
                        }
                    } finally {
                        image.close()
                    }
                } catch (_: RuntimeException) {
                    // Teardown may race image acquisition.
                }
            }, Handler(thread.looper))
            val flags =
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            val virtual =
                getSystemService(DisplayManager::class.java).createVirtualDisplay(
                    "KelpiePrivateFixture",
                    320,
                    480,
                    resources.displayMetrics.densityDpi,
                    imageReader.surface,
                    flags,
                ) ?: error("No private display")
            display = virtual
            val window = Presentation(this, virtual.display)
            presentation = window
            val view = WebView(window.context)
            webView = view
            view.layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = false
            view.settings.allowFileAccess = false
            view.settings.allowContentAccess = false
            view.settings.javaScriptCanOpenWindowsAutomatically = false
            view.settings.setSupportMultipleWindows(false)
            running = true
            view.webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        if (token == generation && running) {
                            writeEvent("FIXTURE_READY", 0, null, false)
                            main.postDelayed({ step(token, 1) }, 3000)
                        }
                    }
                }
            window.setContentView(view)
            window.show()
            view.loadDataWithBaseURL(
                "https://fixture.invalid/",
                ProbeFixture.html(),
                "text/html",
                "UTF-8",
                null,
            )
            status.text = "Private display readying. Press HOME now, then return after 35 seconds."
        } catch (e: Exception) {
            writeEvent("SETUP_FAILED_" + e.javaClass.simpleName, 0, null, false)
            stop("SETUP_FAILED")
        }
    }

    private fun step(
        token: Int,
        sequence: Int,
    ) {
        if (!running || token != generation) return
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            writeEvent("LOCKED_STOP", sequence, null, false)
            stop("LOCKED")
            return
        }
        if (sequence > 20) {
            writeEvent("COMPLETED", 20, null, false)
            status.text = "Result: " + passes + "/" + attempts + " fresh matching frames."
            running = false
            release()
            return
        }
        val view =
            webView ?: run {
                stop("NO_WEBVIEW")
                return
            }
        val (red, green, blue) = ProbeFixture.colorFor(sequence)
        val before = SystemClock.elapsedRealtimeNanos()
        view.evaluateJavascript("window.setProbe($sequence,$red,$green,$blue)", null)
        main.postDelayed({
            if (!running || token != generation) return@postDelayed
            val f = frame
            val pass = FrameEvidence.matches(sequence, before, f)
            attempts++
            if (pass) passes++
            writeEvent(if (pass) "FRESH_MATCH" else "STALE_OR_MISSING", sequence, f, pass)
            status.text = "Frames: " + attempts + "/20; PASS: " + passes
            main.postDelayed({ step(token, sequence + 1) }, 400)
        }, 900)
    }

    private fun writeEvent(
        event: String,
        sequence: Int,
        observed: PixelSample?,
        matched: Boolean,
    ) {
        val json =
            JSONObject()
                .put("event", event)
                .put("sequence", sequence)
                .put("activityVisible", visible)
                .put("elapsedMs", SystemClock.elapsedRealtime())
                .put("matched", matched)
                .put("frameTimeNs", observed?.receivedAtNs ?: 0)
                .put(
                    "sampleRgb",
                    if (observed == null) {
                        "none"
                    } else {
                        observed.red.toString() + "," + observed.green + "," + observed.blue
                    },
                )
        try {
            File(filesDir, "virtual-display-probe.jsonl")
                .appendText(json.toString() + "\n")
        } catch (_: Exception) {
            status.post { status.text = "Evidence storage failed; QA result invalid." }
        }
    }

    private fun stop(reason: String) {
        if (running) writeEvent(reason, attempts, null, false)
        running = false
        generation++
        release()
    }

    private fun release() {
        webView?.stopLoading()
        presentation?.dismiss()
        webView?.destroy()
        webView = null
        presentation = null
        display?.release()
        display = null
        reader?.close()
        reader = null
        worker?.quitSafely()
        worker = null
    }

    override fun onPause() {
        visible = false
        super.onPause()
        // The probe continues only while Android permits the process to live.
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    override fun onDestroy() {
        stop("ACTIVITY_DESTROYED")
        super.onDestroy()
    }
}
