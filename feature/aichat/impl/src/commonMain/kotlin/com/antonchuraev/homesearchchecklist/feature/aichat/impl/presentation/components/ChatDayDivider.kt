package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_day_divider_today
import org.jetbrains.compose.resources.stringResource

/**
 * Day divider pill — centered chip "Today" between message groups.
 *
 * Material 3 chat pattern from AI Chat M3 design:
 * - Pill shape (999dp radius) with `surfaceContainer` background.
 * - Padding 4dp vertical × 12dp horizontal.
 * - `labelSmall` typography in `onSurfaceVariant`.
 *
 * Currently only renders "Сегодня"/"Today" — full date-aware grouping is a
 * future iteration (need timestamp comparisons against system clock).
 */
@Composable
fun ChatDayDivider(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Text(
                text = stringResource(Res.string.chat_day_divider_today),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
