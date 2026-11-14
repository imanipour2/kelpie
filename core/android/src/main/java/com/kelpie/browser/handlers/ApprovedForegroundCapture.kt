package com.kelpie.browser.handlers

import com.kelpie.browser.network.ApprovedScreenshotStore
import com.kelpie.browser.network.ForegroundRecoveryState
import com.kelpie.browser.network.KelpieRecoveryNotification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Capture on the device AFTER an explicit notification approval, while the
 * Activity is visible. The remote requester may collect the short-lived image
 * after returning to ChatGPT using the original unguessable request ID.
 */
internal class ApprovedForegroundCapture(
    private val ctx: HandlerContext,
    private val fullPageCapture: suspend (String, Int, ScreenshotResolution) -> Map<String, Any?>?,
) {
    private data class CapturePlan(
        val ticket: ApprovedScreenshotStore.Ticket,
        val fullPage: Boolean,
        val format: String,
        val quality: Int,
        val resolution: ScreenshotResolution,
    )

    private val store = ApprovedScreenshotStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: CapturePlan? = null
    private var worker: Job? = null
    private var foreground = false

    fun arm(
        request: ForegroundRecoveryState.Request,
        fullPage: Boolean,
        format: String,
        quality: Int,
        resolution: ScreenshotResolution,
    ): Boolean {
        val tab = ctx.tabStore?.activeTab ?: return false
        if (tab.currentUrl.isBlank() || tab.isLoading) return false
        val ticket =
            ApprovedScreenshotStore.Ticket(
                requestId = request.id,
                tabId = tab.id,
                documentVersion = tab.documentVersion,
                expiresAtMs = request.expiresAt,
            )
        val prior = pending
        if (prior?.ticket?.requestId == ticket.requestId &&
            (prior.ticket != ticket ||
                prior.fullPage != fullPage ||
                prior.format != format ||
                prior.quality != quality ||
                prior.resolution != resolution)
        ) {
            return false
        }
        if (!store.arm(ticket)) return false
        pending = CapturePlan(ticket, fullPage, format, quality, resolution)
        return true
    }

    /**
     * Do not let a legacy paired call consume a consent result: retrieval
     * requires the matching token, original tab, original document and options.
     */
    fun take(
        requestId: String,
        fullPage: Boolean,
        format: String,
        quality: Int,
        resolution: ScreenshotResolution,
    ): Map<String, Any?>? {
        val plan = pending ?: return null
        if (plan.ticket.requestId != requestId ||
            plan.fullPage != fullPage ||
            plan.format != format ||
            plan.quality != quality ||
            plan.resolution != resolution
        ) {
            return null
        }
        val tab = ctx.tabStore?.activeTab ?: return null
        val result = store.take(requestId, tab.id, tab.documentVersion)
        if (result != null) pending = null
        return result
    }

    fun onResumed() {
        foreground = true
        if (worker?.isActive == true) return
        val plan = pending ?: return
        worker =
            scope.launch {
                try {
                    // Compose and the WebView may not have attached at onResume.
                    repeat(24) {
                        if (!eligible(plan)) return@launch
                        val ready = ctx.isScreenshotForegroundReady()
                        if (ready && eligible(plan)) {
                            KelpieRecoveryNotification.renderReady()
                            val tab = ctx.tabStore?.activeTab ?: return@launch
                            val payload =
                                ctx.withBrowserTarget(tab.webView) {
                                    if (plan.fullPage) {
                                        fullPageCapture(plan.format, plan.quality, plan.resolution)
                                    } else {
                                        ctx.captureScreenshotPayload(plan.format, plan.quality, plan.resolution)
                                    }
                                }
                            if (!eligible(plan)) return@launch
                            if (payload != null &&
                                store.stage(
                                    plan.ticket.requestId,
                                    plan.ticket.tabId,
                                    plan.ticket.documentVersion,
                                    payload,
                                )
                            ) {
                                KelpieRecoveryNotification.captureCompleted()
                                return@launch
                            }
                        }
                        delay(200)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // No page data or stack trace in diagnostic logs.
                }
            }
    }

    fun onPaused() {
        foreground = false
        worker?.cancel()
        worker = null
    }

    fun onDocumentChanged(tabId: String) {
        if (pending?.ticket?.tabId == tabId) invalidate()
    }

    private fun eligible(plan: CapturePlan): Boolean {
        val tab = ctx.tabStore?.activeTab ?: return false
        val status = KelpieRecoveryNotification.current() ?: return false
        return foreground &&
            status.id == plan.ticket.requestId &&
            status.status in setOf(
                ForegroundRecoveryState.Status.ACTIVITY_RESUMED,
                ForegroundRecoveryState.Status.RENDER_READY,
            ) &&
            tab.id == plan.ticket.tabId &&
            tab.documentVersion == plan.ticket.documentVersion &&
            !tab.isLoading &&
            System.currentTimeMillis() < plan.ticket.expiresAtMs
    }

    fun invalidate() {
        worker?.cancel()
        worker = null
        pending = null
        store.invalidate()
    }

    fun close() {
        invalidate()
        scope.cancel()
    }
}
