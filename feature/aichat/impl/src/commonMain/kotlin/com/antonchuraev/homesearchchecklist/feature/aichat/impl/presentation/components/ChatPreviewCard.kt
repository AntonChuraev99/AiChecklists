package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_preview_apply
import aichecklists.core.designsystem.generated.resources.chat_preview_cancel
import aichecklists.core.designsystem.generated.resources.chat_preview_will_complete
import aichecklists.core.designsystem.generated.resources.chat_preview_will_create_checklist
import aichecklists.core.designsystem.generated.resources.chat_preview_will_create_one
import aichecklists.core.designsystem.generated.resources.chat_preview_will_delete
import aichecklists.core.designsystem.generated.resources.chat_preview_will_move_reminders
import aichecklists.core.designsystem.generated.resources.chat_preview_will_set_reminder
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.PendingPreview
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Preview card shown when the AI classified a write-intent (AddItem, DeleteItem, etc.).
 *
 * Displays:
 * - Intent icon + header label (e.g. "This will create 1 item:")
 * - [PendingPreview.humanReadable] text (pre-rendered by the ViewModel, keeps composable dumb)
 * - Apply / Cancel action buttons
 *
 * Uses [AppCard] (the project's design system wrapper) for consistent elevation/border.
 * The card itself is NOT clickable — only the two buttons inside are interactive.
 */
@Composable
fun ChatPreviewCard(
    preview: PendingPreview,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, headerRes) = preview.toolCall.iconAndHeader()

    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            // Header row: icon + label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(headerRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Action description (pre-rendered by ViewModel)
            Text(
                text = preview.humanReadable,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppButtonText(
                    text = stringResource(Res.string.chat_preview_cancel),
                    onClick = onCancel,
                )
                AppButton(
                    text = stringResource(Res.string.chat_preview_apply),
                    onClick = onApply,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Maps a write-intent [ToolCall] to its icon and header string resource.
 *
 * [ToolCall.FindItemsQuery] is a read-intent and never produces a preview card;
 * the ViewModel must not set [PendingPreview] for it. If somehow passed here,
 * it falls through to AddItem as a safe no-crash default.
 */
private fun ToolCall.iconAndHeader(): Pair<ImageVector, StringResource> = when (this) {
    is ToolCall.AddItem -> Icons.Outlined.Add to Res.string.chat_preview_will_create_one
    is ToolCall.DeleteItem -> Icons.Outlined.Delete to Res.string.chat_preview_will_delete
    is ToolCall.CompleteItem -> Icons.Outlined.CheckCircle to Res.string.chat_preview_will_complete
    is ToolCall.CreateChecklist -> Icons.AutoMirrored.Outlined.List to Res.string.chat_preview_will_create_checklist
    is ToolCall.SetItemReminder -> Icons.Outlined.Notifications to Res.string.chat_preview_will_set_reminder
    is ToolCall.MoveAllReminders -> Icons.Outlined.SwapHoriz to Res.string.chat_preview_will_move_reminders
    is ToolCall.FindItemsQuery -> Icons.Outlined.Add to Res.string.chat_preview_will_create_one
}
