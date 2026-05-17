package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_help_action
import aichecklists.core.designsystem.generated.resources.chat_pricing_caption
import org.jetbrains.compose.resources.stringResource

/**
 * Small caption row above the input field that explains per-query cost
 * (≈ 0–3 credits) and exposes a HelpOutline icon to open [AiChatPricingHelpSheet].
 *
 * Replaces the "?" icon that used to live in the header — keeping the cost
 * explanation closer to where the user is about to spend it (the input field)
 * follows the same intuition as showing the total under a checkout button.
 *
 * Help affordance is an icon (HelpOutline), not a text label — user preference
 * for icon-driven secondary actions (recorded 2026-05-17).
 */
@Composable
fun ChatPricingCaption(
    onWhyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.chat_pricing_caption),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Outlined.HelpOutline,
            contentDescription = stringResource(Res.string.chat_help_action),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onWhyClick),
        )
    }
}
