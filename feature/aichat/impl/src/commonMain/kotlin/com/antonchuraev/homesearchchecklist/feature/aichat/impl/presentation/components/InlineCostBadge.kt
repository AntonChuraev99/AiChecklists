package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A small read-only pill shown below a user message bubble, indicating how many
 * AI credits were charged to classify the intent.
 *
 * Always renders (even when [cost] == 0) so the user can learn "standard commands are free".
 * Has no onClick — it is purely informational.
 *
 * @param cost Credits consumed by the routing layer (0 in Phase A / Layer 1).
 */
@Composable
fun InlineCostBadge(
    cost: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = "· $cost",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(shape)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = shape,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { /* decorative / informational — no action */ },
    )
}
