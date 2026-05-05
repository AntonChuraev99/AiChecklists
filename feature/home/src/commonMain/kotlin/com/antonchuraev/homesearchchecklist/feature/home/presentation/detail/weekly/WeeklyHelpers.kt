package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.weekday_friday
import aichecklists.core.designsystem.generated.resources.weekday_monday
import aichecklists.core.designsystem.generated.resources.weekday_saturday
import aichecklists.core.designsystem.generated.resources.weekday_sunday
import aichecklists.core.designsystem.generated.resources.weekday_thursday
import aichecklists.core.designsystem.generated.resources.weekday_tuesday
import aichecklists.core.designsystem.generated.resources.weekday_wednesday
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import org.jetbrains.compose.resources.StringResource

/**
 * Fixed ISO calendar week order: Monday (1) through Sunday (7).
 * The view uses this constant order regardless of "today" — today's section
 * is highlighted in place via styling, not by rotating the list. This matches
 * how Sunsama, Akiflow, Apple Calendar week view, and habit trackers render
 * the week, and removes the "Monday is at the bottom" surprise the rolling
 * view created when today wasn't Monday.
 */
internal val ISO_WEEK_DAYS: List<Int> = (1..7).toList()

/**
 * Localized weekday name resource for ISO weekday 1..7.
 * No "Today"/"Tomorrow" substitution — the day-section header relies on
 * visual styling (bold + primary color) to mark today; the move-to bottom
 * sheet always shows full names. Single source of truth keeps both views
 * consistent.
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

/**
 * Single source of truth for the overdue predicate, used by both
 * [getOverdueItems] (to populate the top section) and the per-day section
 * filter (to avoid rendering an overdue item twice).
 *
 * An item is overdue when it is unchecked AND scheduled for a past
 * weekday in the current week (weekday < todayWeekday).
 *
 * On Monday (todayWeekday=1), no weekday satisfies `weekday < 1`,
 * so overdue is always empty — week-reset semantics.
 */
internal fun isOverdue(item: ChecklistFillItem, todayWeekday: Int): Boolean {
    if (item.checked) return false
    val weekday = item.weekday ?: return false
    return weekday < todayWeekday
}

/** Returns unchecked items scheduled for past weekdays in the current week. */
internal fun getOverdueItems(
    items: List<ChecklistFillItem>,
    todayWeekday: Int,
): List<ChecklistFillItem> =
    items.filter { isOverdue(it, todayWeekday) }
