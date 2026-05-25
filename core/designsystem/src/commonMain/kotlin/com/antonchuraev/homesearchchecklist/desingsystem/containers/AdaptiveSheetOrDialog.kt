package com.antonchuraev.homesearchchecklist.desingsystem.containers

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass

/**
 * Adaptive modal presentation that chooses the correct container based on screen size:
 * - **Compact** (<600dp): [ModalBottomSheet] — phone-native, supports swipe-dismiss
 * - **Medium/Expanded** (≥600dp): [AlertDialog] — tablet/desktop native, centered modal
 *
 * On large screens a ModalBottomSheet is an anti-pattern UX: it slides from the bottom of
 * the full viewport rather than from near the triggering element, feels foreign on a
 * side-by-side list-detail layout, and breaks spatial model.
 *
 * @param onDismiss called on swipe-dismiss (Compact) or outside tap (Expanded)
 * @param modifier applied to the sheet/dialog container
 * @param title optional title for the dialog on Expanded screens; not shown on Compact
 *   (ModalBottomSheet doesn't render a title by default)
 * @param content body composable rendered inside the sheet or dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheetOrDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val windowSizeClass = rememberAppWindowSizeClass()
    if (windowSizeClass == AppWindowSizeClass.Compact) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = modifier,
        ) {
            content()
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = title,
            text = { content() },
            // Action buttons are handled inside content composables to preserve existing
            // UX (confirm/cancel/delete rows already exist inside sheet bodies).
            confirmButton = {},
            modifier = modifier,
        )
    }
}
