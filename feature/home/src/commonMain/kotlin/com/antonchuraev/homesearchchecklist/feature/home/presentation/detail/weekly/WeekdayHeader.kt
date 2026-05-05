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
import androidx.compose.ui.text.font.FontWeight
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.add_item
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Sticky section header for a weekly checklist day.
 *
 * Always shows the plain weekday name (Monday, Tuesday, ...). Today's section is
 * marked only by visual weight — bold + primary color. All other days share the
 * same neutral styling regardless of whether they have items, to keep the only
 * meaningful hierarchy "today vs the rest" — without competing emphasis from
 * non-empty days.
 *
 * @param weekday ISO weekday 1=Monday..7=Sunday
 * @param isToday whether this slot represents today (drives bold + primary styling)
 * @param onAddClick callback when the "+" button is tapped
 */
@Composable
internal fun WeekdayHeader(
    weekday: Int,
    isToday: Boolean,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayLabel = stringResource(weekdayNameKey(weekday))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
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
            text = dayLabel,
            style = if (isToday) {
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
            },
            color = if (isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
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
