package com.kelpie.browser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.kelpie.browser.capture.PrivateCaptureCleanupSequence
import com.kelpie.browser.handlers.HandlerContext
import com.kelpie.browser.handlers.WebSocketHandler
import com.kelpie.browser.kelpieActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Manages the set of open browser tabs. Creates configured WebViews for new tabs. */
class TabStore(
    private val context: Context,
    private val handlerContext: HandlerContext,
    private val browserState: BrowserState,
) {
    private val previewSampler = context.kelpieActivity()?.window?.let(::ChromeSampler)
    private val previewTabs = mutableListOf<String>()
    private val retiredRenderers = java.util.WeakHashMap<WebView, Boolean>()

    fun captureActivePreview() {
        val tab = activeTab ?: return
        if (tab.isStartPage) return
        previewTabs.remove(tab.id)
        previewTabs.add(tab.id)
        while (previewTabs.size > 12) {
            val oldest = previewTabs.removeAt(0)
            _tabs.value.firstOrNull { it.id == oldest }?.preview = null
        }
        val url = tab.currentUrl
        previewSampler?.capture(tab.webView) { preview ->
            if (_tabs.value.any { it === tab } && tab.currentUrl == url && tab.id in previewTabs) tab.preview = preview
        }
    }

    fun discardPreviews() {
        previewTabs.clear()
        _tabs.value.forEach { it.preview = null }
    }

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId

    val activeTab: BrowserTab?
        get() = _tabs.value.firstOrNull { it.id == _activeTabId.value }

    init {
        val session = SessionStore.load(context)
        if (session != null) {
            val restoredTabs = restoredTabs(session.tabs)
            if (restoredTabs.isNotEmpty()) {
                _tabs.value = restoredTabs
                val activeRecord = session.tabs.getOrNull(session.activeIndex)
                _activeTabId.value = restoredTabs.firstOrNull { activeRecord != null && it.currentUrl == activeRecord.url && it.partition == activeRecord.partition }?.id
                    ?: restoredTabs.first().id
            }
        }
        if (_tabs.value.isEmpty()) {
            val showStartPage =
                !context
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_HIDE_WELCOME, false)
            val tab = createTab(isStartPage = showStartPage)
            _tabs.value = listOf(tab)
            _activeTabId.value = tab.id
            if (!showStartPage) {
                tab.webView.loadUrl(HomeStore.url)
            }
        }
    }

    fun addTab(
        url: String? = null,
        partition: String? = null,
        name: String? = null,
    ): BrowserTab {
        val profile = partition?.let { PartitionRegistry.resolve(it) }
        com.kelpie.browser.connect.NessieConnection
            .personChangedTab()
        captureActivePreview()
        val tab = createTab(partition = partition, profile = profile, name = name)
        _tabs.value = _tabs.value + tab
        _activeTabId.value = tab.id
        if (url != null) {
            tab.isStartPage = false
            tab.currentUrl = url
            tab.webView.loadUrl(url)
        }
        persistSession()
        return tab
    }

    /**
     * Agent-only creation does not change selected tab or start an Activity.
     * The WebView stays app-owned but unmounted until the owner or agent selects it.
     */
    fun addBackgroundTab(url: String): BrowserTab? {
        if (_tabs.value.size >= MAX_AGENT_TABS) return null
        val tab = createTab(isStartPage = false)
        _tabs.value = _tabs.value + tab
        tab.currentUrl = url
        tab.webView.loadUrl(url)
        persistSession()
        return tab
    }

    /** Selection occurs only after R5 rehosts the exact target WebView. */
    fun selectTabAsAgent(id: String): Boolean {
        if (_tabs.value.none { it.id == id }) return false
        _activeTabId.value = id
        persistSession()
        return true
    }

    fun closeTab(id: String) {
        if (id == _activeTabId.value) {
            com.kelpie.browser.connect.NessieConnection
                .personChangedTab()
        }
        previewTabs.remove(id)
        val index = _tabs.value.indexOfFirst { it.id == id }
        if (index < 0) return

        if (_tabs.value.size == 1) {
            val replacement = createTab()
            val currentTab = _tabs.value.first()
            destroyWebView(currentTab.webView)
            _tabs.value = listOf(replacement)
            _activeTabId.value = replacement.id
            persistSession()
            return
        }

        val removed = _tabs.value[index]
        _tabs.value = _tabs.value.filterNot { it.id == id }
        destroyWebView(removed.webView)
        if (_activeTabId.value == id) {
            _activeTabId.value = _tabs.value[minOf(index, _tabs.value.size - 1)].id
        }
        persistSession()
    }

    fun selectTab(id: String) {
        if (id != _activeTabId.value) {
            com.kelpie.browser.connect.NessieConnection
                .personChangedTab()
        }
        if (_tabs.value.any { it.id == id }) {
            if (id != _activeTabId.value) captureActivePreview()
            _activeTabId.value = id
            persistSession()
        }
    }

    /**
     * Android forbids reusing a WebView after renderer death. Retire the exact
     * instance, revoke outstanding native work, and recover a blank native tab.
     * History remains in HistoryStore; recovery never silently reloads an account.
     */
    internal fun retireDeadRenderer(dead: WebView): Boolean {
        if (retiredRenderers.put(dead, true) != null) return true
        val lost = _tabs.value.firstOrNull { it.webView === dead }
        val lostWasActive = lost != null && activeTab === lost
        val cleanup = PrivateCaptureCleanupSequence()
        // Private owner must clear its witnesses BEFORE STOP/cancellation can
        // resume a capture finally block against this unusable shared renderer.
        cleanup.release { handlerContext.nativeRendererGone?.invoke(dead) }
        cleanup.release {
            com.kelpie.browser.agent.session.AndroidAgentProcessSession
                .stop()
        }
        cleanup.release { handlerContext.browserRetirement.retireTarget(dead) }
        cleanup.release { lost?.let { handlerContext.browserDocumentChanged?.invoke(it.id) } }
        handlerContext.retireVisibleWebView(dead)
        cleanup.release { (dead.parent as? ViewGroup)?.removeView(dead) }
        // Do not call stopLoading/evaluateJavascript/removeJavascriptInterface on
        // the unusable renderer. destroy() follows native hierarchy removal.
        cleanup.release { dead.destroy() }
        if (lost == null) return true
        lost.remoteNavigationLease = null
        previewTabs.remove(lost.id)
        lost.preview = null
        val replacement =
            runCatching {
                val profile = lost.partition?.let { PartitionRegistry.resolve(it, restoring = true) }
                createTab(partition = lost.partition, profile = profile, name = lost.name, id = lost.id)
            }.getOrNull()
        _tabs.value =
            _tabs.value.flatMap { tab ->
                if (tab !== lost) listOf(tab) else listOfNotNull(replacement)
            }
        // Resource exhaustion can also prevent creating a replacement WebView.
        // An empty native tab list is safer than propagating from this callback
        // or retaining a renderer-dead instance as the selected tab.
        if (_tabs.value.isEmpty()) _tabs.value = listOfNotNull(runCatching { createTab() }.getOrNull())
        if (_tabs.value.none { it.id == _activeTabId.value }) _activeTabId.value = _tabs.value.firstOrNull()?.id
        if (lostWasActive) {
            cleanup.release { handlerContext.dialogState.dismissPending() }
            browserState.updateUrl("")
            browserState.updateTitle("")
            browserState.updateLoading(false)
            browserState.updateCanGoBack(false)
            browserState.updateCanGoForward(false)
        }
        handlerContext.lastNavigationError =
            if (_tabs.value.isEmpty()) {
                "Renderer terminated; native tab recovery requires the owner to reopen Kelpie"
            } else {
                "Renderer terminated; a new empty tab requires owner navigation"
            }
        cleanup.release { persistSession() }
        return true
    }

    /**
     * Destroys every tab's WebView. Call this from the Activity's onDestroy so per-tab WebView,
     * JavaScript interface, and WebChromeClient references do not leak across Activity recreation.
     */
    fun destroyAllTabs() {
        discardPreviews()
        previewSampler?.cancel()
        val snapshot = _tabs.value
        _tabs.value = emptyList()
        _activeTabId.value = null
        snapshot.forEach { destroyWebView(it.webView) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createTab(
        isStartPage: Boolean = true,
        partition: String? = null,
        profile: String? = null,
        name: String? = null,
        id: String =
            java.util.UUID
                .randomUUID()
                .toString(),
    ): BrowserTab {
        val webView =
            WebView(context).apply {
                PartitionRegistry.bind(this, profile)
                // Keep the real Android AutofillService responsible for credential
                // selection, save prompts and biometric gates. Never pass passwords
                // through the remote R5 agent command channel or a local vault.
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // WebAuthn is enabled only for websites with a verified Digital
                // Asset Link to this app; privileged arbitrary-site browser
                // WebAuthn cannot be claimed by a normal sideloaded QA APK.
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
                    WebSettingsCompat.setWebAuthenticationSupport(
                        settings,
                        WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP,
                    )
                }
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.setSupportMultipleWindows(false)
                settings.mediaPlaybackRequiresUserGesture = false
                // Keep the actual Android System WebView user agent; do not impersonate Chrome.
                addJavascriptInterface(JsBridge(handlerContext), JS_BRIDGE_NAME)
                WebSocketHandler.installBridge(this)
            }
        val tab = BrowserTab(id = id, webView = webView, isStartPage = isStartPage, partition = partition, name = name)
        installTabWebViewClients(webView, tab, this, browserState, handlerContext)
        return tab
    }

    private fun destroyWebView(webView: WebView) {
        if (retiredRenderers.containsKey(webView)) return
        try {
            webView.removeJavascriptInterface(JS_BRIDGE_NAME)
        } catch (_: Exception) {
        }
        webView.destroy()
        handlerContext.browserRetirement.retireTarget(webView)
    }

    private fun restoredTabs(records: List<SessionStore.TabRecord>): List<BrowserTab> =
        records.mapNotNull { record ->
            try {
                val profile = record.partition?.let { PartitionRegistry.resolve(it, restoring = true) }
                createTab(isStartPage = false, partition = record.partition, profile = profile, name = record.name).also {
                    it.currentUrl = record.url
                    it.webView.loadUrl(record.url)
                }
            } catch (failure: PartitionRegistry.Failure) {
                android.util.Log.w("TabStore", "Skipped unavailable isolated tab: ${failure.code}")
                null
            }
        }

    fun persistSession() {
        SessionStore.save(context, _tabs.value, _activeTabId.value)
    }

    companion object {
        private const val MAX_AGENT_TABS = 40
        private const val PREFS_NAME = "kelpie_prefs"
        private const val KEY_HIDE_WELCOME = "hide_welcome_card"
        private const val JS_BRIDGE_NAME = "KelpieBridge"
    }
}
