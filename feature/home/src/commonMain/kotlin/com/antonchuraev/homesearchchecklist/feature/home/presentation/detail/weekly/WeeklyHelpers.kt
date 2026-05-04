package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.weekday_friday
import aichecklists.core.designsystem.generated.resources.weekday_monday
import aichecklists.core.designsystem.generated.resources.weekday_saturday
import aichecklists.core.designsystem.generated.resources.weekday_sunday
import aichecklists.core.designsystem.generated.resources.weekday_thursday
import aichecklists.core.designsystem.generated.resources.weekday_today
import aichecklists.core.designsystem.generated.resources.weekday_tomorrow
import aichecklists.core.designsystem.generated.resources.weekday_tuesday
import aichecklists.core.designsystem.generated.resources.weekday_wednesday
import org.jetbrains.compose.resources.StringResource

/**
 * Returns 7 weekday indices starting from today, in rolling weekly order.
 * E.g. todayWeekday=5 (Friday) → [5, 6, 7, 1, 2, 3, 4]
 *
 * @param todayWeekday ISO weekday number 1=Monday .. 7=Sunday
 */
internal fun weeklyOrderFromToday(todayWeekday: Int): List<Int> =
    (0..6).map { offset -> ((todayWeekday - 1 + offset) % 7) + 1 }

/**
 * Returns the localized string resource key for a given weekday slot.
 *
 * @param weekday ISO weekday 1..7
 * @param isToday whether this slot represents today
 * @param isTomorrow whether this slot represents tomorrow
 */
internal fun weekdayLabelKey(
    weekday: Int,
    isToday: Boolean,
    isTomorrow: Boolean,
): StringResource = when {
    isToday -> Res.string.weekday_today
    isTomorrow -> Res.string.weekday_tomorrow
    else -> when (weekday) {
        1 -> Res.string.weekday_monday
        2 -> Res.string.weekday_tuesday
        3 -> Res.string.weekday_wednesday
        4 -> Res.string.weekday_thursday
        5 -> Res.string.weekday_friday
        6 -> Res.string.weekday_saturday
        7 -> Res.string.weekday_sunday
        else -> Res.string.weekday_monday
    }
}

/**
 * Full weekday name regardless of today/tomorrow context.
 * Used in MoveToDayBottomSheet where all 7 days are listed by name.
 */
internal fun weekdayNameKey(weekday: Int): StringResource = when (weekday) {
    1 -> Res.string.weekday_monday
    2 -> Res.string.weekday_tuesday
    3 -> Res.string.weekday_wednesday
    4 -> Res.string.weekday_thursday
    5 -> Res.string.weekday_friday
    6 -> Res.string.weekday_saturday
    7 -> Res.string.weekday_sunday
    else -> Res.string.weekday_monday
}
