package com.antonchuraev.homesearchchecklist.desingsystem.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * The "type a task, press +" row that lives as the LAST item of a task list.
 *
 * Anatomy (field + optional paperclip + send) is the one the checklist-detail screen ships in the
 * v2 arm; it lives here so the create-project form can present the same row instead of a second,
 * slowly-diverging copy. Note this is NOT [AddItemInputField] — that one is the quick-capture dock's
 * row, with a 56dp filled send button and no attachment slot, sized for a bottom sheet rather than
 * for a position inside a scrolling list.
 *
 * @param canSend whether the send affordance is enabled — normally `text.isNotBlank()`.
 * @param onFocusChanged lets the host mirror focus into its state (e.g. to show a chips row only
 *   while the field is focused). Called with the raw `isFocused` value.
 * @param onAttachClick `null` hides the paperclip entirely. Not cosmetic: attachments belong to a
 *   `ChecklistFillItem`, and on the create form no fill exists yet, so there is nothing to attach
 *   to. Callers that DO have a fill (checklist detail) pass their handler.
 */
@Composable
fun InlineAddItemRow(
    text: String,
    canSend: Boolean,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(Res.string.detail_inline_add_placeholder),
    enabled: Boolean = true,
    onAttachClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        AppTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = placeholder,
            enabled = enabled,
            singleLine = false,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Sentences,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canSend) onSubmit() }),
        )
        if (onAttachClick != null) {
            IconButton(onClick = onAttachClick, enabled = enabled) {
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = stringResource(Res.string.attachment_add_file_button),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onSubmit, enabled = enabled && canSend) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(Res.string.add_item),
                tint = if (enabled && canSend) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
