package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_help_action
import aichecklists.core.designsystem.generated.resources.chat_pricing_caption
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * Horizontal info band rendered immediately below [ChatHeader] (CenterAlignedTopAppBar).
 *
 * ## Layout
 * ```
 * ┌────────────────────────────────────────────────────────┐
 * │ ≈ 0–3 credits per query                            [?] │  ← this row
 * ├────────────────────────────────────────────────────────┤  ← HorizontalDivider
 * │                  message list                          │
 * ```
 *
 * ## Token decisions
 * - **Background**: `surfaceContainerLow` — a single tonal step above `surface` so the
 *   band is visually distinct from the message list area (which uses `surfaceContainerHigh`
 *   for outgoing bubbles). The contrast is subtle yet delineates the supporting info zone
 *   without adding visual noise.
 * - **Text**: `onSurfaceVariant` / `labelSmall` — lower-emphasis supporting label per MD3.
 * - **Icon**: `onSurfaceVariant` at 16dp — same tint as text; placed on the trailing end
 *   with enough row height (6dp vertical padding + labelSmall line ~16dp ≈ 28dp total row)
 *   so the effective tap-zone is acceptable for a secondary info action.
 * - **Divider**: `outlineVariant` 1dp — MD3 decorative separator (not `outline`, which is
 *   reserved for important boundaries like text fields).
 *
 * @param onWhyClick Callback invoked when the user taps the HelpOutline icon.
 *                   Expected to open [AiChatPricingHelpSheet].
 */
@Composable
fun ChatPricingRow(
    onWhyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(
                    horizontal = AppDimens.ScreenPaddingHorizontal,
                    vertical = AppDimens.SpacingLg,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.chat_pricing_caption),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Outlined.HelpOutline,
                contentDescription = stringResource(Res.string.chat_help_action),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onWhyClick),
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
