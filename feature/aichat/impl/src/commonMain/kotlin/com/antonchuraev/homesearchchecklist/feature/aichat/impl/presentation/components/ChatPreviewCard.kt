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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_preview_apply
import aichecklists.core.designsystem.generated.resources.chat_preview_cancel
import aichecklists.core.designsystem.generated.resources.chat_preview_reject
import aichecklists.core.designsystem.generated.resources.chat_preview_checklist_label
import aichecklists.core.designsystem.generated.resources.chat_preview_item_label
import aichecklists.core.designsystem.generated.resources.chat_preview_new_list_label
import aichecklists.core.designsystem.generated.resources.chat_preview_no_checklist_hint
import aichecklists.core.designsystem.generated.resources.chat_preview_will_complete
import aichecklists.core.designsystem.generated.resources.chat_preview_will_create_checklist
import aichecklists.core.designsystem.generated.resources.chat_preview_will_create_one
import aichecklists.core.designsystem.generated.resources.chat_preview_will_delete
import aichecklists.core.designsystem.generated.resources.chat_preview_will_attach_to_item
import aichecklists.core.designsystem.generated.resources.chat_preview_will_create_from_attachment
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
 * - Cancel / Reject / Apply action buttons
 *
 * Uses [AppCard] (the project's design system wrapper) for consistent elevation/border.
 * The card itself is NOT clickable — only the buttons inside are interactive.
 *
 * @param showRejectButton When `true` (default), shows the "I meant something else" button
 *   between Cancel and Apply. Set to `false` for [ToolCall.CreateChecklistFromAttachment]
 *   where there is no original text to re-classify.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPreviewCard(
    preview: PendingPreview,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onReject: () -> Unit,
    onItemTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showRejectButton: Boolean = true,
) {
    val (icon, headerRes) = preview.toolCall.iconAndHeader()
    val isCreateChecklist = preview.toolCall is ToolCall.CreateChecklist
    val showItemField = preview.toolCall is ToolCall.AddItem ||
        preview.toolCall is ToolCall.DeleteItem ||
        preview.toolCall is ToolCall.CompleteItem ||
        preview.toolCall is ToolCall.SetItemReminder ||
        isCreateChecklist

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

            // Target checklist label — shown for item-targeted intents only.
            // CreateChecklist creates a new list (no target) so we hide this row.
            if (!isCreateChecklist) {
                val hintText = preview.targetChecklistHint
                    ?.let { stringResource(Res.string.chat_preview_checklist_label, it) }
                    ?: stringResource(Res.string.chat_preview_no_checklist_hint)
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Editable item text — for any intent that carries a user-visible payload.
            if (showItemField) {
                // Auto-focus + raise IME the moment the preview card appears so the user
                // can immediately edit the captured raw payload (often noisy after a long
                // dictated phrase) without an extra tap. Card is recreated on each new
                // preview, so LaunchedEffect(Unit) fires exactly once per preview lifecycle.
                val focusRequester = remember { FocusRequester() }
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
                OutlinedTextField(
                    value = preview.editableItemText,
                    onValueChange = onItemTextChange,
                    label = {
                        Text(
                            text = stringResource(
                                if (isCreateChecklist) Res.string.chat_preview_new_list_label
                                else Res.string.chat_preview_item_label
                            )
                        )
                    },
                    // Multiline: grows up to 6 lines so the user sees the full raw payload
                    // (often noisy long phrases) without horizontal scrolling. Beyond 6 lines
                    // the field scrolls vertically inside.
                    minLines = 1,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            } else {
                // For MoveAllReminders / FindItemsQuery there is no editable payload —
                // fall back to the pre-rendered description so the card still shows context.
                Text(
                    text = preview.humanReadable,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            // Reject — recovery action on its own row above the primary actions.
            // Russian copy "Я имел в виду другое" + EN "I meant something else" are
            // too long to fit alongside Cancel + Apply in a single Row on phone widths
            // (Pixel 9 ~411dp dropped the primary button off-screen). Material 3
            // split-actions: secondary recovery gets its own slot so primary Apply
            // stays visible and the action hierarchy reads cleanly.
            if (showRejectButton) {
                AppButtonText(
                    text = stringResource(Res.string.chat_preview_reject),
                    onClick = onReject,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Primary action row: Cancel (text, left) | Apply (filled, right).
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
    is ToolCall.CreateChecklistFromAttachment -> Icons.AutoMirrored.Outlined.List to Res.string.chat_preview_will_create_from_attachment
    is ToolCall.AttachToItem -> Icons.Outlined.Add to Res.string.chat_preview_will_attach_to_item
    // Agent-only variants: treated like AddItem/List operations for any accidental preview
    is ToolCall.AddItems -> Icons.Outlined.Add to Res.string.chat_preview_will_create_one
    is ToolCall.ReadChecklist -> Icons.AutoMirrored.Outlined.List to Res.string.chat_preview_will_create_checklist
    is ToolCall.RenameChecklist -> Icons.AutoMirrored.Outlined.List to Res.string.chat_preview_will_create_checklist
}
