package com.kelpie.browser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.kelpie.browser.agent.execution.R5AgentCoordinator
import com.kelpie.browser.capture.PrivateCaptureCoordinator

/**
 * Exactly one native display may own the original WebView at a time.
 * Compose must unmount it before a private Presentation can attach it.
 */
@Composable
internal fun browserWebViewRelinquished(
    capture: PrivateCaptureCoordinator?,
    agent: R5AgentCoordinator?,
): Boolean {
    val p4 = capture?.relinquished?.collectAsState()?.value == true
    val r5 = agent?.relinquished?.collectAsState()?.value == true
    return p4 || r5
}
