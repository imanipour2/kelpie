package com.kelpie.virtualdisplayprobe

import android.app.Activity
import android.app.KeyguardManager
import android.app.Presentation
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
 * User-started, completely offline QA for SAME WebView reparenting.
 * No Kelpie session, other-app surface, real URL, credential or screenshot is used.
 * Switching HOME never causes a background Activity launch.
 */
class SameWebViewActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var browser: WebView? = null
    private var worker: HandlerThread? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null

    @Volatile
    private var latest: PixelSample? = null
    private var visible = false
    private var active = false
    private var generation = 0
    private var passed = 0
    private var counted = 0
    private var initialProof: String? = null
    private var originalViewIdentity = 0
    private var migrationComplete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }
        status = TextView(this).apply {
            text = "S24 same-WebView migration (offline). Start, then HOME immediately. Return after 35 s."
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Start migration / 20 changed frames"
            setOnClickListener { start() }
        })
        root.addView(Button(this).apply {
            text = "Restore SAME WebView to Activity"
            setOnClickListener { restoreToActivity() }
        })
        root.addView(Button(this).apply {
            text = "Stop and release"
            setOnClickListener { stop("USER_STOP") }
        })
        setContentView(root)
    }

    private fun start() {
        stop("RESTART")
        generation++
        val ticket = generation
        counted = 0
        passed = 0
        latest = null
        migrationComplete = false
        initialProof = null
        File(filesDir, "same-webview-migration.jsonl").writeText("")
        val view = WebView(this)
        browser = view
        originalViewIdentity = System.identityHashCode(view)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = false
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.javaScriptCanOpenWindowsAutomatically = false
        view.settings.setSupportMultipleWindows(false)
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            280,
        )
        root.addView(view)
        active = true
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(target: WebView?, url: String?) {
                if (!active || ticket != generation) return
                view.evaluateJavascript("window.kelpieSeed(); window.kelpieProof()", { proof ->
                    if (!active || ticket != generation) return@evaluateJavascript
                    initialProof = proof
                    write("INITIAL_PROOF", 0, null, false, proof)
                    main.postDelayed({ migrate(ticket) }, 700)
                })
            }
        }
        // A fictional .invalid base and no INTERNET permission guarantee local-only operation.
        view.loadDataWithBaseURL(
            "https://fixture.invalid/",
            SameWebViewFixture.html(),
            "text/html",
            "UTF-8",
            null,
        )
    }

    private fun migrate(ticket: Int) {
        if (!active || ticket != generation) return
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            stop("KEYGUARD_STOP")
            return
        }
        val view = browser ?: return
        try {
            val thread = HandlerThread("SameWebView-Frames")
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
                        if (offset + 2 < plane.buffer.limit()) {
                            latest = PixelSample(
                                plane.buffer.get(offset).toInt() and 255,
                                plane.buffer.get(offset + 1).toInt() and 255,
                                plane.buffer.get(offset + 2).toInt() and 255,
                                SystemClock.elapsedRealtimeNanos(),
                            )
                        }
                    } finally {
                        image.close()
                    }
                } catch (_: RuntimeException) {
                    // Reader may close concurrently during cleanup.
                }
            }, Handler(thread.looper))
            val virtual = getSystemService(DisplayManager::class.java).createVirtualDisplay(
                "KelpieSameWebViewOffline",
                320,
                480,
                resources.displayMetrics.densityDpi,
                imageReader.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            ) ?: error("Private display creation failed")
            display = virtual
            val target = Presentation(this, virtual.display)
            presentation = target
            // Detach rather than destroy or instantiate a replacement WebView.
            (view.parent as? ViewGroup)?.removeView(view)
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            target.setContentView(view)
            target.show()
            migrationComplete = true
            write("MIGRATED_SAME_OBJECT", 0, null, sameObject(), "")
            status.text = "Presentation attached. Press HOME now; do not lock."
            main.postDelayed({ step(ticket, 1) }, 3000)
        } catch (e: Exception) {
            write("MIGRATION_FAILED_" + e.javaClass.simpleName, 0, null, false, "")
            stop("MIGRATION_FAILED")
        }
    }

    private fun step(ticket: Int, sequence: Int) {
        if (!active || ticket != generation) return
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            stop("KEYGUARD_STOP")
            return
        }
        if (sequence > 20) {
            write("FRAMES_COMPLETE", 20, null, counted == 20 && passed == 20, "")
            active = false
            status.text = "Background frames: $passed / $counted. Return and tap Restore."
            // Leave Presentation and original WebView alive for state/identity readback.
            return
        }
        val view = browser ?: run { stop("MISSING_WEBVIEW"); return }
        val (r, g, b) = ProbeFixture.colorFor(sequence)
        val before = SystemClock.elapsedRealtimeNanos()
        view.evaluateJavascript("window.setProbe($sequence,$r,$g,$b)", null)
        main.postDelayed({
            if (!active || ticket != generation) return@postDelayed
            val observed = latest
            val ok = FrameEvidence.matches(sequence, before, observed) && sameObject()
            counted++
            if (ok) passed++
            write(if (ok) "FRESH_MATCH" else "FRAME_INVALID", sequence, observed, ok, "")
            status.text = "Frames: $passed / $counted"
            main.postDelayed({ step(ticket, sequence + 1) }, 400)
        }, 900)
    }

    private fun restoreToActivity() {
        val view = browser ?: run {
            status.text = "No WebView to restore"
            return
        }
        if (!migrationComplete || !visible) {
            status.text = "Restore is permitted only while Activity is visible after migration."
            return
        }
        active = false
        generation++
        val same = sameObject()
        // Preserve the Java object and document; dismissal must not destroy the view.
        (view.parent as? ViewGroup)?.removeView(view)
        presentation?.dismiss()
        presentation = null
        display?.release()
        display = null
        reader?.close()
        reader = null
        worker?.quitSafely()
        worker = null
        latest = null
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            280,
        )
        root.addView(view)
        view.evaluateJavascript("window.kelpieProof()", { proof ->
            val stateOk = proof != null && proof == initialProof
            write("RESTORE_VERDICT", 0, null, same && stateOk && counted == 20 && passed == 20, proof)
            status.text =
                "Same object: $same; same document/form/history/scroll: $stateOk; frames: $passed/$counted"
        })
        migrationComplete = false
    }

    private fun sameObject(): Boolean =
        browser != null && System.identityHashCode(browser) == originalViewIdentity

    private fun write(
        event: String,
        sequence: Int,
        sample: PixelSample?,
        matched: Boolean,
        proof: String,
    ) {
        val message = JSONObject()
            .put("event", event)
            .put("sequence", sequence)
            .put("activityVisible", visible)
            .put("sameViewObject", sameObject())
            .put("matched", matched)
            .put("elapsedMs", SystemClock.elapsedRealtime())
            .put("frameTimeNs", sample?.receivedAtNs ?: 0L)
            .put(
                "sampleRgb",
                if (sample == null) "none"
                else "${sample.red},${sample.green},${sample.blue}",
            )
            // Synthetic-only values, never content from a real browsing session.
            .put("syntheticState", proof.take(350))
        try {
            File(filesDir, "same-webview-migration.jsonl").appendText(message.toString() + "\n")
        } catch (_: Exception) {
            status.post { status.text = "Evidence write failed; QA result invalid." }
        }
    }

    private fun stop(reason: String) {
        if (browser != null) write(reason, counted, null, false, "")
        active = false
        generation++
        browser?.stopLoading()
        val view = browser
        (view?.parent as? ViewGroup)?.removeView(view)
        presentation?.dismiss()
        presentation = null
        view?.destroy()
        browser = null
        display?.release()
        display = null
        reader?.close()
        reader = null
        worker?.quitSafely()
        worker = null
        latest = null
        migrationComplete = false
    }

    override fun onPause() {
        visible = false
        super.onPause()
        // No Activity launch or lock bypass. Only private app-owned content renders.
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
