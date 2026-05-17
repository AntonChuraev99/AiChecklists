package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A small read-only pill shown below a user message bubble, indicating how many
 * AI credits were charged to classify the intent.
 *
 * Only rendered when [cost] > 0 (guard lives in [ChatMessageBubble]).
 * Has no onClick — it is purely informational.
 *
 * Icon size: 16dp — inline-decorative badge exception to the 24dp default rule
 * (see `~/.claude/agents/mobile-design-expert.md` § "Default icon size — 24dp").
 *
 * @param cost Credits consumed by the routing layer. Must be > 0 when this composable is called.
 */
@Composable
fun InlineCostBadge(
    cost: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = shape,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { /* decorative / informational — no action */ },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null, // decorative — cost text carries the semantic content
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = cost.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
