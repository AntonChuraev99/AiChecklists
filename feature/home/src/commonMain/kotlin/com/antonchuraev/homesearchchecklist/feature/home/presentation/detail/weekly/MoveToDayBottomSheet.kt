package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.cancel
import aichecklists.core.designsystem.generated.resources.selected
import aichecklists.core.designsystem.generated.resources.weekly_move_to_day_title
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Modal bottom sheet for moving a weekly checklist item to a different day.
 *
 * Shows all 7 weekdays (Mon-Sun) as tappable rows.
 * Today is highlighted with primary color.
 * Currently selected day shows a check icon on the trailing side.
 *
 * @param currentWeekday ISO weekday (1..7) of the item's current day, or null if unassigned
 * @param todayWeekday ISO weekday (1..7) of today
 * @param onDaySelected callback with the target weekday (1..7)
 * @param onDismiss callback when sheet is dismissed without selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveToDayBottomSheet(
    currentWeekday: Int?,
    todayWeekday: Int,
    onDaySelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = AppDimens.CardElevation,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = AppDimens.SpacingLg),
        ) {
            // Sheet title
            Text(
                text = stringResource(Res.string.weekly_move_to_day_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppDimens.ScreenPaddingHorizontal,
                        vertical = AppDimens.SpacingMd,
                    ),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // All 7 days in ISO order (Mon=1 .. Sun=7)
            (1..7).forEach { weekday ->
                val isToday = weekday == todayWeekday
                val isSelected = weekday == currentWeekday
                val dayLabel = stringResource(weekdayNameKey(weekday))
                val selectedLabel = stringResource(Res.string.selected)

                val interactionSource = remember { MutableInteractionSource() }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onDaySelected(weekday) },
                        )
                        .semantics { role = Role.Button }
                        .padding(
                            horizontal = AppDimens.ScreenPaddingHorizontal,
                            vertical = AppDimens.SpacingMd,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = selectedLabel,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(AppDimens.IconSizeMd),
                        )
                    }
                }

                if (weekday < 7) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                    )
                }
            }

            // Cancel button
            AppButtonText(
                text = stringResource(Res.string.cancel),
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppDimens.ScreenPaddingHorizontal,
                        vertical = AppDimens.SpacingSm,
                    ),
            )
        }
    }
}
