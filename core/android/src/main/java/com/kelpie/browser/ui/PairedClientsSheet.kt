package com.kelpie.browser.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.kelpie.browser.network.PairApprovalCoordinator

/** Paired-device account management sheet; independent of WebView rendering. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PairedClientsSheet(
    coordinator: PairApprovalCoordinator,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        PairedClientsScreen(
            coordinator = coordinator,
            onBack = onDismiss,
        )
    }
}
