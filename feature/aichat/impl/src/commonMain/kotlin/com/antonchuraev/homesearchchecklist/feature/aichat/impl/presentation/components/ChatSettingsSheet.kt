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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import aichecklists.core.designsystem.generated.resources.chat_settings_title
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCreditsChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSwitch
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
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

            // Bottom spacing so the last row isn't flush with the navigation bar
            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        }
    }
}
