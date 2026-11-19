package com.kelpie.browser.browser

import android.graphics.Bitmap
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kelpie.browser.handlers.ConsoleHandler
import com.kelpie.browser.handlers.HandlerContext
import com.kelpie.browser.handlers.WebSocketHandler

/**
 * Installs the WebViewClient and WebChromeClient for a tab's WebView. Per-tab state (URL, title,
 * loading, history) is always updated on the [BrowserTab]. The shared [BrowserState] used by the
 * URL bar is only updated when the tab is currently active in [tabStore], so background loads
 * never clobber the active tab's UI.
 */
internal fun installTabWebViewClients(
    webView: WebView,
    tab: BrowserTab,
    tabStore: TabStore,
    browserState: BrowserState,
    handlerContext: HandlerContext,
) {
    val isActive: () -> Boolean = { tabStore.activeTab === tab }

    webView.webViewClient = tabWebViewClient(tab, tabStore, browserState, handlerContext, isActive)
    webView.webChromeClient = tabWebChromeClient(tab, tabStore, browserState, handlerContext, isActive)
}

private fun tabWebViewClient(
    tab: BrowserTab,
    tabStore: TabStore,
    browserState: BrowserState,
    handlerContext: HandlerContext,
    isActive: () -> Boolean,
): WebViewClient =
    object : WebViewClient() {
        private var documentNavigationUrl: String? = null
        private var didCaptureDocumentForNavigation = false
        private var rendererRetired = false

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            rendererRetired = true
            return tabStore.retireDeadRenderer(view)
        }

        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?,
        ) {
            if (rendererRetired || !isRegisteredTab(tabStore, tab, view)) return
            val previousVersion = tab.documentVersion
            tab.documentVersion =
                java.util.UUID
                    .randomUUID()
                    .toString()
            tab.remoteNavigationLease?.started(previousVersion, tab.documentVersion, url)
            handlerContext.browserDocumentChanged?.invoke(tab.id)
            if (isActive()) {
                handlerContext.dialogState.dismissPending()
            }
            handlerContext.lastNavigationError = null
            tab.currentUrl = url
            tab.recordHistoryIfNeeded(url)
            tab.isLoading = true
            tabStore.persistSession()
            if (isActive()) {
                browserState.updateUrl(url)
                browserState.updateLoading(true)
                handlerContext.mark3DInspectorInactive()
            }
            documentNavigationUrl = url
            didCaptureDocumentForNavigation = false
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            if (rendererRetired || !isRegisteredTab(tabStore, tab, view)) return
            if (request?.isForMainFrame != true) return
            val description =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    error?.description?.toString()
                } else {
                    null
                }
            handlerContext.lastNavigationError = description ?: "Navigation failed"
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?,
        ) {
            if (rendererRetired || !isRegisteredTab(tabStore, tab, view)) return
            if (request?.isForMainFrame != true) return
            val code = errorResponse?.statusCode ?: return
            if (code >= 400) {
                handlerContext.lastNavigationError = "HTTP $code"
            }
        }

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            if (rendererRetired || !isRegisteredTab(tabStore, tab, view)) return
            tab.currentUrl = url
            tab.recordHistoryIfNeeded(url)
            tab.isLoading = false
            tabStore.persistSession()
            // Only the default store syncs; partitioned tabs never feed cookie sync.
            if (tab.partition == null) {
                com.kelpie.browser.sync.CookieSync
                    .pageLoaded(url)
            }
            if (isActive()) {
                browserState.updateUrl(url)
                browserState.updateLoading(false)
                browserState.updateCanGoBack(view.canGoBack())
                browserState.updateCanGoForward(view.canGoForward())
            }

            view.evaluateJavascript(ConsoleHandler.BRIDGE_SCRIPT, null)
            view.evaluateJavascript(JsBridge.NETWORK_BRIDGE_SCRIPT, null)
            if (!WebSocketHandler.isDocumentStartSupported) {
                view.evaluateJavascript(WebSocketHandler.BRIDGE_SCRIPT, null)
            }

            if (!didCaptureDocumentForNavigation && documentNavigationUrl == url) {
                didCaptureDocumentForNavigation = true
                view.evaluateJavascript("(document.contentType || 'text/html')") { value ->
                    if (rendererRetired || !isRegisteredTab(tabStore, tab, view)) return@evaluateJavascript
                    val contentType =
                        value
                            ?.trim()
                            ?.removePrefix("\"")
                            ?.removeSuffix("\"")
                            ?.takeIf { it.isNotEmpty() && it != "null" }
                            ?: "text/html"
                    NetworkTrafficStore.appendDocumentNavigation(
                        url = url,
                        statusCode = 0,
                        contentType = contentType,
                    )
                }
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = false
    }

private fun tabWebChromeClient(
    tab: BrowserTab,
    tabStore: TabStore,
    browserState: BrowserState,
    handlerContext: HandlerContext,
    isActive: () -> Boolean,
): WebChromeClient =
    object : WebChromeClient() {
        override fun onReceivedTitle(
            view: WebView,
            title: String?,
        ) {
            if (!isRegisteredTab(tabStore, tab, view)) return
            val nextTitle = title ?: ""
            tab.pageTitle = nextTitle
            tab.updateHistoryTitleIfNeeded(nextTitle)
            tabStore.persistSession()
            if (isActive()) {
                browserState.updateTitle(nextTitle)
            }
        }

        override fun onProgressChanged(
            view: WebView,
            newProgress: Int,
        ) {
            if (!isRegisteredTab(tabStore, tab, view)) return
            if (isActive()) {
                browserState.updateProgress(newProgress)
            }
        }

        override fun onJsAlert(
            view: WebView,
            url: String?,
            message: String?,
            result: android.webkit.JsResult,
        ): Boolean {
            if (!isRegisteredTab(tabStore, tab, view)) {
                runCatching { result.cancel() }
                return true
            }
            handlerContext.dialogState.enqueue(
                DialogState.PendingDialog(
                    type = "alert",
                    message = message.orEmpty(),
                    defaultText = null,
                    jsResult = result,
                    jsPromptResult = null,
                ),
            )
            return true
        }

        override fun onJsConfirm(
            view: WebView,
            url: String?,
            message: String?,
            result: android.webkit.JsResult,
        ): Boolean {
            if (!isRegisteredTab(tabStore, tab, view)) {
                runCatching { result.cancel() }
                return true
            }
            handlerContext.dialogState.enqueue(
                DialogState.PendingDialog(
                    type = "confirm",
                    message = message.orEmpty(),
                    defaultText = null,
                    jsResult = result,
                    jsPromptResult = null,
                ),
            )
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: android.webkit.JsPromptResult,
        ): Boolean {
            if (!isRegisteredTab(tabStore, tab, view)) {
                runCatching { result.cancel() }
                return true
            }
            handlerContext.dialogState.enqueue(
                DialogState.PendingDialog(
                    type = "prompt",
                    message = message.orEmpty(),
                    defaultText = defaultValue,
                    jsResult = null,
                    jsPromptResult = result,
                ),
            )
            return true
        }
    }

/** A replacement can retain the tab ID; stale callbacks must match the actual tab object and WebView. */
private fun isRegisteredTab(
    store: TabStore,
    tab: BrowserTab,
    view: WebView,
): Boolean = tab.webView === view && store.tabs.value.any { it === tab }
