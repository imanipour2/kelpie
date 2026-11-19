package com.kelpie.browser.handlers

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

/** Immutable request witness. Its closures capture the admitted grant, renderer and document. */
internal class BrowserOperationGuard(
    private val authorityCurrent: () -> Boolean,
    private val targetCurrent: () -> Boolean,
    private val originalDocumentCurrent: () -> Boolean,
) {
    fun beforeAction(): Boolean = afterNavigation() && documentUnchanged()

    /** Navigation may replace the document, but cannot replace this request's authority or renderer. */
    fun afterNavigation(): Boolean = authorityCurrent() && targetCurrent()

    fun documentUnchanged(): Boolean = originalDocumentCurrent()
}

/** Per-coroutine scope, following HandlerContext's existing admitted-WebView pattern. */
internal class BrowserOperationContext {
    private val guard = ThreadLocal<BrowserOperationGuard?>()

    fun capture(): BrowserOperationGuard? = guard.get()

    suspend fun <T> withGuard(
        value: BrowserOperationGuard?,
        block: suspend () -> T,
    ): T = withContext(guard.asContextElement(value)) { block() }
}
