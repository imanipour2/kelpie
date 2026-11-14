package com.kelpie.browser

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kelpie.browser.browser.BrowserState
import com.kelpie.browser.browser.HistoryStore
import com.kelpie.browser.browser.HomeStore
import com.kelpie.browser.browser.SessionStore
import com.kelpie.browser.browser.TabStore
import com.kelpie.browser.handlers.HandlerContext
import com.kelpie.browser.qa.P2OfflineFixture
import com.kelpie.browser.qa.SurfaceProbeLease
import com.unlikeotherai.kelpie.app.BuildConfig
import org.json.JSONObject
import java.io.File

/**
 * Explicit on-device QA with a REAL TabStore-owned WebView, but a synthetic
 * offline document in a separately installed app profile. No browser service,
 * Nessie account, HTTP/MCP endpoint, remote capture, or actual website.
 * Enabled only with -PkelpieP2VerificationProfile=true.
 */
class P2SurfaceQaActivity : Activity() {
    private data class Sample(val r: Int, val g: Int, val b: Int, val receivedNs: Long)

    private val main = Handler(Looper.getMainLooper())
    private lateinit var panel: FrameLayout
    private lateinit var status: TextView
    private lateinit var store: TabStore
    private var webView: WebView? = null
    private var worker: HandlerThread? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null

    @Volatile private var latest: Sample? = null
    private var gate = SurfaceProbeLease()
    private var runToken = 0
    private var active = false
    private var visible = false
    private var migrated = false
    private var frames = 0
    private var passed = 0
    private var documentVersion = ""
    private var tabId = ""
    private var originalWebView: WebView? = null
    private var initialProof: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.P2_QA_ENABLED && packageName == "com.kelpie.browser.p2verify") {
            "P2 hardware QA is unavailable in the normal browser"
        }
        // This is a separate package, but clear its OWN synthetic URL session on launch.
        SessionStore.clear(this)
        HomeStore.init(this)
        HistoryStore.init(this)
        store = TabStore(this, HandlerContext(), BrowserState())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }
        status = TextView(this).apply {
            text = "P2: real Kelpie TabStore WebView / offline-only fixture. Explicit local approval required."
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Approve local P2 test (20 frames)"
            setOnClickListener { showConsent() }
        })
        root.addView(Button(this).apply {
            text = "Restore SAME TabStore tab"
            setOnClickListener { restore() }
        })
        root.addView(Button(this).apply {
            text = "STOP / release private display"
            setOnClickListener { stop("USER_STOP") }
        })
        panel = FrameLayout(this)
        root.addView(
            panel,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)
    }

    private fun showConsent() {
        AlertDialog.Builder(this)
            .setTitle("تأیید آزمایش محدود P2")
            .setMessage(
                "فقط یک صفحه ساختگی فارسی و آفلاین در WebView متعلق به TabStore آزمایش می‌شود. " +
                    "هیچ سایت، رمز عبور، کوکی، تصویر یا نشست واقعی ارسال و ذخیره نمی‌شود. " +
                    "پس از شروع HOME را بزنید و صفحه را قفل نکنید. " +
                    "حداکثر زمان مجوز ۹۰ ثانیه است. دکمه STOP عملیات را قطع می‌کند.",
            )
            .setNegativeButton("لغو", null)
            .setPositiveButton("تأیید و شروع") { _, _ -> start() }
            .show()
    }

    private fun start() {
        stop("RESTART")
        runToken++
        val ticket = runToken
        gate = SurfaceProbeLease()
        active = true
        frames = 0
        passed = 0
        latest = null
        initialProof = null
        File(filesDir, "p2-tabstore-surface.jsonl").writeText("")
        val tab = store.activeTab ?: run {
            stop("TAB_MISSING")
            return
        }
        val view = tab.webView
        originalWebView = view
        webView = view
        tabId = tab.id
        // No network requests even if a malformed fixture introduces subresources.
        view.settings.blockNetworkLoads = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.javaScriptEnabled = true
        (view.parent as? ViewGroup)?.removeView(view)
        panel.addView(view, FrameLayout.LayoutParams(-1, -1))
        tab.isStartPage = false
        view.loadDataWithBaseURL(
            P2OfflineFixture.BASE,
            P2OfflineFixture.html(),
            "text/html",
            "UTF-8",
            null,
        )
        main.postDelayed({ awaitFixture(ticket, 0) }, 500)
    }

    private fun awaitFixture(ticket: Int, attempt: Int) {
        if (!active || runToken != ticket) return
        val tab = store.activeTab ?: run { stop("TAB_LOST"); return }
        if (tab.id != tabId || tab.webView !== originalWebView) {
            stop("TAB_CHANGED")
            return
        }
        if (attempt >= 30) {
            stop("FIXTURE_TIMEOUT")
            return
        }
        val view = tab.webView
        view.evaluateJavascript(
            "(document.readyState === 'complete' && typeof window.kelpieSeed === 'function')",
        ) { value ->
            if (!active || runToken != ticket) return@evaluateJavascript
            if (value != "true" || tab.isLoading) {
                main.postDelayed({ awaitFixture(ticket, attempt + 1) }, 200)
            } else {
                // Preserve the genuine TabStore WebViewClient, including its documentVersion.
                documentVersion = tab.documentVersion
                if (!gate.begin(tab.id, documentVersion)) {
                    stop("LEASE_FAILED")
                    return@evaluateJavascript
                }
                view.evaluateJavascript("window.kelpieSeed(); window.kelpieProof()") { proof ->
                    if (!valid(ticket)) {
                        stop("STALE_BEFORE_MIGRATION")
                        return@evaluateJavascript
                    }
                    initialProof = proof
                    write("INITIAL_PROOF", 0, null, true, proof)
                    main.postDelayed({ migrate(ticket) }, 650)
                }
            }
        }
    }

    private fun valid(ticket: Int): Boolean {
        val tab = store.activeTab ?: return false
        return active && ticket == runToken && gate.allows(tabId, documentVersion) &&
            tab.id == tabId && tab.webView === originalWebView &&
            tab.documentVersion == documentVersion && !tab.isLoading &&
            (tab.currentUrl.startsWith(P2OfflineFixture.BASE))
    }

    private fun locked(): Boolean = getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun migrate(ticket: Int) {
        if (!valid(ticket) || locked()) {
            stop("MIGRATION_GUARD_FAILED")
            return
        }
        val view = webView ?: return
        try {
            val thread = HandlerThread("P2TabStore-Frames")
            thread.start()
            worker = thread
            val images = ImageReader.newInstance(320, 480, PixelFormat.RGBA_8888, 2)
            reader = images
            images.setOnImageAvailableListener({ source ->
                try {
                    val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val plane = image.planes[0]
                        val offset = 240 * plane.rowStride + 160 * plane.pixelStride
                        if (offset + 2 < plane.buffer.limit()) {
                            latest = Sample(
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
                    // A stopped/closed ImageReader may race with the callback.
                }
            }, Handler(thread.looper))
            val privateDisplay = getSystemService(DisplayManager::class.java).createVirtualDisplay(
                "KelpieP2TabStoreOffline",
                320, 480, resources.displayMetrics.densityDpi,
                images.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            ) ?: error("VirtualDisplay unavailable")
            display = privateDisplay
            val target = Presentation(this, privateDisplay.display)
            presentation = target
            (view.parent as? ViewGroup)?.removeView(view)
            target.setContentView(view)
            target.show()
            migrated = true
            write("MIGRATED_TABSTORE_WEBVIEW", 0, null, valid(ticket), "")
            main.postDelayed({ step(ticket, 1) }, 3000)
        } catch (_: Exception) {
            stop("MIGRATION_EXCEPTION")
        }
    }

    private fun step(ticket: Int, sequence: Int) {
        if (!valid(ticket) || locked() || !gate.claim(sequence, tabId, documentVersion)) {
            stop("STEP_GUARD_FAILED")
            return
        }
        if (sequence == 21) {
            write("FRAMES_COMPLETE", 20, null, frames == 20 && passed == 20, "")
            active = false
            status.text = "P2 frames $passed/$frames; open this QA app and press Restore."
            return
        }
        val view = webView ?: run { stop("WEBVIEW_MISSING"); return }
        val (r, g, b) = P2OfflineFixture.colorFor(sequence)
        latest = null
        val before = SystemClock.elapsedRealtimeNanos()
        view.evaluateJavascript("window.setProbe($sequence,$r,$g,$b)", null)
        main.postDelayed({
            if (!valid(ticket)) {
                stop("FRAME_GUARD_FAILED")
                return@postDelayed
            }
            val sample = latest
            val match = sample != null && sample.receivedNs > before &&
                sample.r == r && sample.g == g && sample.b == b
            frames++
            if (match) passed++
            write(if (match) "FRESH_MATCH" else "FRAME_INVALID", sequence, sample, match, "")
            status.text = "P2 frames $passed/$frames"
            main.postDelayed({ step(ticket, sequence + 1) }, 400)
        }, 900)
    }

    private fun restore() {
        val view = originalWebView ?: run {
            status.text = "No tab to restore"
            return
        }
        if (!visible || !migrated || active) {
            status.text = "Restoration only when visible after the 20-frame run."
            return
        }
        val tab = store.activeTab
        val identity = tab?.webView === view && tab?.id == tabId &&
            tab.documentVersion == documentVersion && gate.completed()
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
        panel.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.evaluateJavascript("window.kelpieProof()") { proof ->
            val ok = identity && proof == initialProof && frames == 20 && passed == 20
            write("RESTORE_VERDICT", 0, null, ok, proof)
            status.text = "P2 TabStore same object/state: $ok; frames $passed/$frames"
        }
        migrated = false
        runToken++
        gate.cancel()
    }

    private fun write(event: String, sequence: Int, sample: Sample?, matched: Boolean, proof: String) {
        val record = JSONObject()
            .put("event", event)
            .put("sequence", sequence)
            .put("activityVisible", visible)
            .put("tabStoreIdentity", store.activeTab?.webView === originalWebView)
            .put("sameDocument", store.activeTab?.documentVersion == documentVersion)
            .put("matched", matched)
            .put("elapsedMs", SystemClock.elapsedRealtime())
            .put("sampleRgb", if (sample == null) "" else "${sample.r},${sample.g},${sample.b}")
            .put("frameReceivedNs", sample?.receivedNs ?: 0L)
            .put("syntheticState", proof.take(250))
        try {
            File(filesDir, "p2-tabstore-surface.jsonl").appendText(record.toString() + "\n")
        } catch (_: Exception) {
            status.post { status.text = "QA evidence write FAILED; result invalid." }
        }
    }

    private fun stop(reason: String) {
        if (::status.isInitialized && originalWebView != null) {
            write(reason, frames, null, false, "")
        }
        active = false
        runToken++
        gate.cancel()
        val view = originalWebView
        (view?.parent as? ViewGroup)?.removeView(view)
        presentation?.dismiss()
        presentation = null
        display?.release()
        display = null
        reader?.close()
        reader = null
        worker?.quitSafely()
        worker = null
        latest = null
        migrated = false
        if (view != null && ::panel.isInitialized) {
            panel.addView(view, FrameLayout.LayoutParams(-1, -1))
        }
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    override fun onDestroy() {
        stop("ACTIVITY_DESTROYED")
        store.destroyAllTabs()
        super.onDestroy()
    }
}
