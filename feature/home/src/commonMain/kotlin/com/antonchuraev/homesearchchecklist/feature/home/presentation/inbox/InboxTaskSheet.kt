package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_move_picker_empty
import aichecklists.core.designsystem.generated.resources.inbox_move_picker_title
import aichecklists.core.designsystem.generated.resources.inbox_open_project_action
import aichecklists.core.designsystem.generated.resources.inbox_task_sheet_delete
import aichecklists.core.designsystem.generated.resources.inbox_task_sheet_important
import aichecklists.core.designsystem.generated.resources.inbox_task_sheet_important_remove
import aichecklists.core.designsystem.generated.resources.inbox_task_sheet_move
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
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AdaptiveSheetOrDialog
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Triage sheet for a single Inbox task — the ONLY place per-task actions live.
 *
 * Per `.claude/rules/ui-card-patterns.md` the task row itself is a 30/70 hit-zone split (check /
 * open) and may carry read-only indicators only; every action (move, importance, delete, open
 * project) belongs here. Adding even one icon button to the row would eat the hit zone and start
 * the "Frankenstein card" drift the rule exists to prevent.
 *
 * The move picker is rendered as a REPLACEMENT sheet rather than a second sheet stacked on top:
 * two simultaneous `ModalBottomSheet`s fight over the scrim and the predictive-back gesture on
 * Android. Dismissing the picker returns to this sheet because [InboxScreenState.Content.sheetForTaskId]
 * is untouched by [InboxIntent.OnMovePickerDismiss].
 *
 * @param isProjectPage true when the task lives on a project page of the pager — only then is
 *   "Open project" meaningful (the Inbox page has no detail screen to open).
 */
@Composable
internal fun InboxTaskSheet(
    task: InboxTask,
    isProjectPage: Boolean,
    projectId: Long,
    moveTargets: List<InboxPage>,
    movePickerOpen: Boolean,
    onIntent: (InboxIntent) -> Unit,
) {
    if (movePickerOpen) {
        AdaptiveSheetOrDialog(
            onDismiss = { onIntent(InboxIntent.OnMovePickerDismiss) },
            title = { Text(stringResource(Res.string.inbox_move_picker_title)) },
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
                // idea what tapping one does. Same workaround as the triage sheet below.
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
                        InboxSheetRow(
                            icon = Icons.Outlined.ChecklistRtl,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            title = target.title,
                            onClick = { onIntent(InboxIntent.OnMoveToProject(target.checklistId)) },
                        )
                    }
                }
            }
        }
        return
    }

    AdaptiveSheetOrDialog(
        onDismiss = { onIntent(InboxIntent.OnTaskSheetDismiss) },
        title = { Text(task.text) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .navigationBarsPadding(),
        ) {
            // The task text also titles the dialog variant on Medium/Expanded; on Compact the
            // ModalBottomSheet has no title slot, so it is repeated here to keep the sheet
            // unambiguous when several tasks read similarly.
            Text(
                text = task.text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppDimens.SpacingSm),
            )
            HorizontalDivider()

            InboxSheetRow(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = stringResource(Res.string.inbox_task_sheet_move),
                onClick = { onIntent(InboxIntent.OnMovePickerOpen) },
            )
            InboxSheetRow(
                icon = if (task.priority > 0) Icons.Filled.Star else Icons.Outlined.Star,
                iconTint = if (task.priority > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                title = if (task.priority > 0) {
                    stringResource(Res.string.inbox_task_sheet_important_remove)
                } else {
                    stringResource(Res.string.inbox_task_sheet_important)
                },
                onClick = { onIntent(InboxIntent.OnToggleImportant) },
            )
            if (isProjectPage) {
                InboxSheetRow(
                    icon = Icons.Outlined.ChecklistRtl,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(Res.string.inbox_open_project_action),
                    onClick = { onIntent(InboxIntent.OnOpenProject(projectId)) },
                )
            }
            InboxSheetRow(
                icon = Icons.Outlined.Delete,
                iconTint = MaterialTheme.colorScheme.error,
                title = stringResource(Res.string.inbox_task_sheet_delete),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { onIntent(InboxIntent.OnDeleteTask) },
            )
        }
    }
}

/**
 * One tappable action row. Deliberately a local copy of the detail screen's `ItemDetailsSheetRow`
 * shape (same 24dp icon, same [AppDimens.MinTouchTarget] floor) rather than a shared extraction:
 * that one is private to a 4000-line file, and promoting it would widen a public API for two
 * call sites.
 */
@Composable
private fun InboxSheetRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    onClick: () -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.MinTouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = AppDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
