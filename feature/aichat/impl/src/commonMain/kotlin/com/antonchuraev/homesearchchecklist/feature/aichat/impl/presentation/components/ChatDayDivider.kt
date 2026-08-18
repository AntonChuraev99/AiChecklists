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
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import org.jetbrains.compose.resources.stringResource

/**
 * Day divider pill — centered chip "Today" between message groups.
 *
 * Material 3 chat pattern from AI Chat M3 design:
 * - Pill shape (999dp radius) filled with `AppChatColors.quietFill()` — plane-relative, because the
 *   divider renders both on the page and inside the chat dock. **`quietFill`, not `raised`**: this
 *   pill carries no outline, so its fill is its only channel, and on the near-white light page
 *   `raised()` is a ΔL\* +1.7 step, i.e. no pill at all. See the accessor's own KDoc and the inline
 *   comment at the call site — both spell out the same choice, so "align the code with this doc"
 *   would restore the defect rather than fix a drift.
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
            // `surfaceContainer` is only ΔL* 2.1 off the dark chrome, so the pill dissolved into the
            // dock and the date read as loose text. `quietFill`, NOT `raised`: this divider carries
            // no outline — a ring would read as one more chip to tap — so the fill is its only
            // channel and it needs the step that works on the near-white light page too.
            color = AppChatColors.quietFill(),
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
