package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_help_action
import aichecklists.core.designsystem.generated.resources.chat_pricing_caption
import org.jetbrains.compose.resources.stringResource

/**
 * Credit info banner — Material 3 pill rendered below [ChatHeader].
 *
 * Spec from AI Chat M3 design:
 * - **Margin**: `0 16dp 8dp` (no top — sits directly under the top bar)
 * - **Inner padding**: top 10, right 8, bottom 10, left 16 (asymmetric — leading
 *   text gets more breathing room than the trailing icon button)
 * - **Background**: `surfaceContainerLow`, 12dp corner radius (rounded rectangle)
 * - **Leading sparkle icon**: 16dp, tinted `primary` — signals AI-related info
 * - **Caption**: `bodySmall` (12sp / 16 line / 0.4 letterSpacing), `onSurfaceVariant`,
 *   takes `weight(1f)` so the help icon stays right-aligned
 * - **Trailing help icon**: 20dp icon inside 32dp IconButton (hit zone), `onSurfaceVariant`
 *
 * No HorizontalDivider under the banner — the floating pill provides its own
 * visual separation via the surrounding margin + tonal contrast.
 *
 * @param onWhyClick Callback for the HelpOutline tap — opens [AiChatPricingHelpSheet].
 */
@Composable
fun ChatPricingRow(
    onWhyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(
                start = 16.dp,
                top = 10.dp,
                end = 8.dp,
                bottom = 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(Res.string.chat_pricing_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onWhyClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = stringResource(Res.string.chat_help_action),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
