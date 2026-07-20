package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_settings_response_language_auto
import aichecklists.core.designsystem.generated.resources.chat_settings_response_language_title
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatLanguageOption
import org.jetbrains.compose.resources.stringResource

/**
 * Response-language picker bottom sheet (Perplexity-style: "Auto" first, then explicit languages
 * by their own endonym — no flags, no in-chat badge).
 *
 * Single-select: the currently-pinned [currentCode] (null = Auto) shows a check. Tapping any row
 * calls [onSelect] with the language's BCP-47 code (or null for Auto) and the caller persists +
 * closes. Endonyms come from [ChatLanguageOption.ALL] (language-invariant data, not localized).
 *
 * @param currentCode The persisted reply-language code, or null for Auto.
 * @param onSelect    Called with the picked code (null = Auto). Caller persists and dismisses.
 * @param onDismiss   Called on drag / scrim tap / back — no selection change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatResponseLanguageSheet(
    currentCode: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.SpacingXl, vertical = AppDimens.SpacingXl),
        ) {
            Text(
                text = stringResource(Res.string.chat_settings_response_language_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            // Auto — the null selection (server matches the message language).
            LanguageRow(
                label = stringResource(Res.string.chat_settings_response_language_auto),
                selected = currentCode == null,
                onClick = { onSelect(null) },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppDimens.SpacingSm),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            ChatLanguageOption.ALL.forEach { option ->
                LanguageRow(
                    label = option.endonym,
                    selected = currentCode == option.code,
                    onClick = { onSelect(option.code) },
                )
            }

            // Bottom spacing so the last row isn't flush with the navigation bar.
            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        }
    }
}

@Composable
private fun LanguageRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = AppDimens.SpacingSm),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
