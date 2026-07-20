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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_settings_balance_label
import aichecklists.core.designsystem.generated.resources.chat_settings_deep_thinking_subtitle
import aichecklists.core.designsystem.generated.resources.chat_settings_deep_thinking_title
import aichecklists.core.designsystem.generated.resources.chat_settings_clear_chat
import aichecklists.core.designsystem.generated.resources.chat_settings_default_list_reset
import aichecklists.core.designsystem.generated.resources.chat_settings_default_list_subtitle
import aichecklists.core.designsystem.generated.resources.chat_settings_default_list_title
import aichecklists.core.designsystem.generated.resources.chat_settings_response_language_auto
import aichecklists.core.designsystem.generated.resources.chat_settings_response_language_subtitle_auto
import aichecklists.core.designsystem.generated.resources.chat_settings_response_language_subtitle_fixed
import aichecklists.core.designsystem.generated.resources.chat_settings_response_language_title
import aichecklists.core.designsystem.generated.resources.chat_settings_title
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCreditsChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSwitch
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatLanguageOption
import org.jetbrains.compose.resources.stringResource

/**
 * Chat Settings bottom sheet.
 *
 * Sections (top → bottom):
 * 1. Credit balance — [AppCreditsChip] with "Get More" CTA for non-premium users.
 * 2. Divider
 * 3. Deep Thinking toggle — bypasses Layer 1+2, sends all queries to Layer 3 (3 credits each).
 *
 * Opened when the user taps the gear icon in [ChatHeader].
 * Dismissed via drag, scrim tap, or programmatic [onDismiss].
 *
 * @param creditBalance       Current AI credits balance.
 * @param isPremium           Whether the user has an active premium subscription.
 * @param deepThinkingEnabled Current Deep Thinking toggle state.
 * @param onDeepThinkingToggle Callback when the user flips the Deep Thinking switch.
 * @param onGetMoreClick      Callback for the "Get More" credits CTA (navigates to Paywall).
 * @param defaultChecklistName Name of the list the user asked the chat to remember, or null when
 *                            the chat still asks every time. Non-null renders the reset row.
 * @param onResetDefaultChecklist Clears the remembered default ("Ask me every time").
 * @param onDismiss           Called when the sheet should close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsSheet(
    creditBalance: Int,
    isPremium: Boolean,
    deepThinkingEnabled: Boolean,
    onDeepThinkingToggle: (Boolean) -> Unit,
    onGetMoreClick: () -> Unit,
    onClearChat: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    responseLanguageCode: String? = null,
    onResponseLanguageClick: () -> Unit = {},
    defaultChecklistName: String? = null,
    onResetDefaultChecklist: () -> Unit = {},
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
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        ) {
            // Title
            Text(
                text = stringResource(Res.string.chat_settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            // Credit balance section
            Text(
                text = stringResource(Res.string.chat_settings_balance_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            AppCreditsChip(
                credits = creditBalance,
                isPremium = isPremium,
                onClick = if (!isPremium) onGetMoreClick else null,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppDimens.SpacingLg),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Deep Thinking toggle section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Switch,
                        onClick = { onDeepThinkingToggle(!deepThinkingEnabled) },
                    )
                    .padding(vertical = AppDimens.SpacingXs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.chat_settings_deep_thinking_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
                    Text(
                        text = stringResource(Res.string.chat_settings_deep_thinking_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppSwitch(
                    checked = deepThinkingEnabled,
                    // null — Row handles the click; switch is visual-only per AppSwitch pattern
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = AppDimens.SpacingLg),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppDimens.SpacingLg),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Response-language row (Perplexity-style drill-in). null → "Auto"; a pinned code shows
            // its endonym. Tapping opens the language picker sheet; the localized empty-state is the
            // signal we speak the user's language, so there is no in-chat language badge.
            val responseLanguageEndonym = ChatLanguageOption.endonymFor(responseLanguageCode)
            val responseLanguageValueLabel = responseLanguageEndonym
                ?: stringResource(Res.string.chat_settings_response_language_auto)
            val responseLanguageSubtitle = if (responseLanguageEndonym == null) {
                stringResource(Res.string.chat_settings_response_language_subtitle_auto)
            } else {
                stringResource(Res.string.chat_settings_response_language_subtitle_fixed, responseLanguageEndonym)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onResponseLanguageClick)
                    .padding(vertical = AppDimens.SpacingXs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.chat_settings_response_language_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
                    Text(
                        text = responseLanguageSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXxs),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = AppDimens.SpacingLg),
                ) {
                    Text(
                        text = responseLanguageValueLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Default-list section — rendered ONLY once a default exists.
            //
            // This row is the escape hatch for "Remember my choice": without it the chat silently
            // keeps routing every new item to one list with no visible way back, which is the
            // trap the memory feature must not become. No default → nothing to reset → no row.
            if (defaultChecklistName != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = AppDimens.SpacingLg),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onResetDefaultChecklist)
                        .padding(vertical = AppDimens.SpacingXs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.chat_settings_default_list_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
                        Text(
                            text = stringResource(
                                Res.string.chat_settings_default_list_subtitle,
                                defaultChecklistName,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(Res.string.chat_settings_default_list_reset),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = AppDimens.SpacingLg),
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppDimens.SpacingLg),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Clear chat button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearChat)
                    .padding(vertical = AppDimens.SpacingSm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.chat_settings_clear_chat),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Bottom spacing so the last row isn't flush with the navigation bar
            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        }
    }
}
