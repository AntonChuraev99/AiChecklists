package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.add_item
import aichecklists.core.designsystem.generated.resources.weekly_item_count_badge
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Sticky section header for a weekly checklist day.
 *
 * Visual hierarchy:
 * - Today: titleMedium + bold + primary color
 * - Tomorrow: titleSmall + bold + onSurface
 * - Other days: titleSmall + medium weight + onSurfaceVariant
 * - Empty days: alpha 0.6 applied to entire header
 *
 * Shows item count badge inline (e.g. "Wednesday · 3") when count > 0.
 * "+" button on the trailing edge triggers per-day inline add.
 *
 * @param weekday ISO weekday 1=Monday..7=Sunday
 * @param isToday whether this slot represents today
 * @param isTomorrow whether this slot represents tomorrow
 * @param itemCount number of items in this day section
 * @param onAddClick callback when the "+" button is tapped
 */
@Composable
internal fun WeekdayHeader(
    weekday: Int,
    isToday: Boolean,
    isTomorrow: Boolean,
    itemCount: Int,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEmpty = itemCount == 0

    val labelKey = weekdayLabelKey(weekday, isToday, isTomorrow)
    val dayLabel = stringResource(labelKey)

    // Build display text: "Monday · 3" or just "Monday"
    val displayText = if (itemCount > 0) {
        "$dayLabel ${stringResource(Res.string.weekly_item_count_badge, itemCount)}"
    } else {
        dayLabel
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .alpha(if (isEmpty) 0.6f else 1f)
            .padding(
                start = AppDimens.ScreenPaddingHorizontal,
                end = AppDimens.SpacingXs,
                top = AppDimens.SpacingLg,
                bottom = AppDimens.SpacingXs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = displayText,
            style = when {
                isToday -> MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                )
                isTomorrow -> MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                )
                else -> MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Medium,
                )
            },
            color = when {
                isToday -> MaterialTheme.colorScheme.primary
                isTomorrow -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = onAddClick,
            modifier = Modifier.size(AppDimens.MinTouchTarget),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(Res.string.add_item),
                tint = if (isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
        }
    }
}
