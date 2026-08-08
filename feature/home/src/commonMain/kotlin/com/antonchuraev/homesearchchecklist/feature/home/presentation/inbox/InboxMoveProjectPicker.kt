package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_move_picker_empty
import aichecklists.core.designsystem.generated.resources.inbox_move_picker_title
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AdaptiveSheetOrDialog
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Target picker for the item sheet's "Move to project" row.
 *
 * Rendered as a REPLACEMENT sheet rather than a second sheet stacked on top of the item sheet: two
 * simultaneous `ModalBottomSheet`s fight over the scrim and the predictive-back gesture on Android.
 * Dismissing it returns to the item sheet because [InboxScreenState.Content.sheetForTaskId] is
 * untouched by [InboxIntent.OnMovePickerDismiss] — the same shape every other sub-surface this
 * screen raises (reminder sheet, note dialog, date picker) now uses.
 *
 * This file is what remains of the old `InboxTaskSheet`. The rest of that sheet — a four-row
 * reimplementation of the checklist detail screen's `ItemDetailsSheet` — is gone: the Inbox now hosts
 * the real one, so the Inbox is no longer the weaker surface of the two.
 */
@Composable
internal fun InboxMoveProjectPicker(
    moveTargets: List<InboxPage>,
    onIntent: (InboxIntent) -> Unit,
) {
    AdaptiveSheetOrDialog(
        onDismiss = { onIntent(InboxIntent.OnMovePickerDismiss) },
        // No `title` argument: the heading is rendered by the content below so it also appears on
        // Compact (AdaptiveSheetOrDialog forwards `title` only to the AlertDialog branch). Passing
        // both showed it TWICE on Medium/Expanded.
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .navigationBarsPadding(),
        ) {
            // AdaptiveSheetOrDialog renders its `title` slot ONLY on the AlertDialog branch
            // (Medium/Expanded); on Compact it drops straight into ModalBottomSheet { content() }.
            // Without repeating it here the phone user gets a bare list of project names with no
            // idea what tapping one does.
            Text(
                text = stringResource(Res.string.inbox_move_picker_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppDimens.SpacingSm),
            )
            HorizontalDivider()

            if (moveTargets.isEmpty()) {
                Text(
                    text = stringResource(Res.string.inbox_move_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppDimens.SpacingLg),
                )
            } else {
                moveTargets.forEach { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppDimens.MinTouchTarget)
                            .clickable { onIntent(InboxIntent.OnMoveToProject(target.checklistId)) }
                            .padding(vertical = AppDimens.SpacingMd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChecklistRtl,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = target.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
