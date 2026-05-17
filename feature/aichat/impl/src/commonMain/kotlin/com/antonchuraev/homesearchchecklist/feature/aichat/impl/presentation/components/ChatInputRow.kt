package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_input_placeholder
import aichecklists.core.designsystem.generated.resources.chat_send_action
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom input row for the AI Chat screen.
 *
 * Layout: [AppTextField] (weight=1f) + [Send IconButton]
 *
 * - [AppTextField] uses `singleLine = true` for chat-style input (vs multiline for add-item).
 * - Send button is enabled only when [text] is not blank; tint follows enabled state.
 * - [Modifier.imePadding] is applied to the outer Row so the row lifts above the IME
 *   without the rest of the scaffold content being squished.
 * - Horizontal padding matches [AppDimens.ScreenPaddingHorizontal] for visual consistency.
 *
 * @param text          Current input text (from [ChatScreenState.inputText]).
 * @param onTextChange  Called on every keystroke.
 * @param onSend        Called when the user taps Send or confirms via IME action.
 * @param isEnabled     When false (e.g. [ChatScreenState.isProcessing]), both field and button are disabled.
 */
@Composable
fun ChatInputRow(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val canSend = isEnabled && text.isNotBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(
                horizontal = AppDimens.ScreenPaddingHorizontal,
                vertical = AppDimens.SpacingMd,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        AppTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = stringResource(Res.string.chat_input_placeholder),
            singleLine = true,
            enabled = isEnabled,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { if (canSend) onSend() }
            ),
        )

        // FilledIconButton — M3 expressive / WhatsApp / Telegram pattern for primary CTA in chat.
        // Uses default squircle shape (M3 standard for icon buttons, not circular pill) for
        // consistency with the rest of the design system.
        // Disabled state colors are handled automatically by FilledIconButton.colors().
        FilledIconButton(
            onClick = onSend,
            enabled = canSend,
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(Res.string.chat_send_action),
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
        }
    }
}
