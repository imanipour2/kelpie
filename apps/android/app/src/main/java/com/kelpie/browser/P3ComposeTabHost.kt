package com.kelpie.browser

import android.view.View
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.kelpie.browser.browser.BrowserState
import com.kelpie.browser.browser.TabStore
import com.kelpie.browser.handlers.HandlerContext
import com.kelpie.browser.ui.TabWebViewContent

/** QA-only host for the exact production Compose TabWebViewContent implementation. */
internal class P3ComposeTabHost(
    activity: ComponentActivity,
    store: TabStore,
    state: BrowserState,
    handlers: HandlerContext,
) {
    private var compositionOwns by mutableStateOf(true)
    val ownsCompose get() = compositionOwns
    var lastAttached: WebView? = null
        private set
    var mountCount = 0
        private set

    val view: ComposeView = ComposeView(activity).apply {
        id = View.generateViewId()
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            if (compositionOwns) {
                TabWebViewContent(
                    tabStore = store,
                    browserState = state,
                    handlerContext = handlers,
                    onScrollDirectionChange = {},
                    onWebViewReady = { webView ->
                        lastAttached = webView
                        mountCount++
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    Text("QA-only private display: Compose relinquished its original WebView")
                }
            }
        }
    }

    fun release() {
        lastAttached = null
        compositionOwns = false
    }

    fun restore() {
        compositionOwns = true
    }
}
