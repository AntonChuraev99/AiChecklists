package com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType

/**
 * Locale-free description of how a repeat schedule should be phrased.
 *
 * This is the split that lets the repeat summary be localized at all: the *decision* of which
 * phrasing applies (a preset, an arbitrary interval, an explicit weekday set) is pure Kotlin and
 * unit-testable with no Compose runtime, while the *words* are resolved on the presentation side by
 * [resolveRepeatSummaryLabel] / [getRepeatSummaryLabel].
 *
 * The variants mirror the preset taxonomy already used by [resolvePresetName] and by the preset rows
 * of [ReminderSheet], so a summary never disagrees with the chip the user picked: a weekly rule with
 * `interval = 2` reads "Every 2 weeks" in both places, not "Every 2 weeks" here and "Biweekly" there.
 */
sealed interface RepeatSummary {

    /** `DAILY`, interval 1. */
    data object Daily : RepeatSummary

    /** `WEEKLY`, interval 1, no explicit weekdays. */
    data object Weekly : RepeatSummary

    /** `WEEKLY`, interval 2, no explicit weekdays. */
    data object Biweekly : RepeatSummary

    /** `MONTHLY`, interval 1. */
    data object Monthly : RepeatSummary

    /** `MONTHLY`, interval 3. */
    data object Quarterly : RepeatSummary

    /** `YEARLY`, interval 1. */
    data object Yearly : RepeatSummary

    /** `WEEKLY` on exactly Mon..Fri — phrased as a range ("Mon–Fri"), not as a five-day list. */
    data object Weekdays : RepeatSummary

    /**
     * `WEEKLY` on an explicit weekday set that is not Mon..Fri.
     *
     * [isoDays] holds ISO day numbers (1 = Monday .. 7 = Sunday), ascending and de-duplicated.
     * Never empty — an empty selection degrades to the plain weekly/interval phrasing instead.
     */
    data class OnWeekDays(val isoDays: List<Int>) : RepeatSummary {
        init {
            // The label resolvers index a seven-element name table by `isoDays[i] - 1`. Failing here
            // turns a programmer error into a loud construction-time failure instead of an
            // IndexOutOfBounds thrown from inside composition; corrupted rows cannot reach this
            // point because [repeatSummaryOf] filters them out first.
            require(isoDays.isNotEmpty() && isoDays.all { it in 1..7 }) {
                "isoDays must be a non-empty subset of 1..7, was $isoDays"
            }
        }
    }

    /** `DAILY` with an interval that is not a preset. */
    data class EveryNDays(val days: Int) : RepeatSummary

    /** `WEEKLY` with an interval that is not a preset and no explicit weekdays. */
    data class EveryNWeeks(val weeks: Int) : RepeatSummary

    /** `MONTHLY` with an interval that is not a preset. */
    data class EveryNMonths(val months: Int) : RepeatSummary

    /** `YEARLY` with an interval that is not a preset. */
    data class EveryNYears(val years: Int) : RepeatSummary
}

/** Structural summary of the repeat configuration currently being edited in the reminder sheet. */
fun PendingRepeatConfig.toRepeatSummary(): RepeatSummary =
    repeatSummaryOf(type = type, interval = interval, weekDays = weekDays)

/**
 * Structural summary of a persisted rule.
 *
 * Equivalent to `toRule().toRepeatSummary()` for any config, because [PendingRepeatConfig.toRule]
 * only drops weekdays that this function ignores anyway (a non-`WEEKLY` type never phrases them).
 */
fun ReminderRepeatRule.toRepeatSummary(): RepeatSummary =
    repeatSummaryOf(type = type, interval = interval, weekDays = weekDays.orEmpty())

/** ISO day numbers of Mon..Fri — the one weekday set that gets its own range phrasing. */
private val WorkingWeek = listOf(1, 2, 3, 4, 5)

private fun repeatSummaryOf(
    type: RepeatType,
    interval: Int,
    weekDays: Set<Int>,
): RepeatSummary = when (type) {
    RepeatType.DAILY -> if (interval == 1) RepeatSummary.Daily else RepeatSummary.EveryNDays(interval)

    RepeatType.WEEKLY -> {
        // Out-of-range values would index past the seven weekday names; drop them here rather than
        // let the label resolver crash on data that only a corrupted row could produce.
        val days = weekDays.filter { it in 1..7 }.sorted()
        when {
            days == WorkingWeek -> RepeatSummary.Weekdays
            days.isNotEmpty() -> RepeatSummary.OnWeekDays(days)
            interval == 1 -> RepeatSummary.Weekly
            interval == 2 -> RepeatSummary.Biweekly
            else -> RepeatSummary.EveryNWeeks(interval)
        }
    }

    RepeatType.MONTHLY -> when (interval) {
        1 -> RepeatSummary.Monthly
        3 -> RepeatSummary.Quarterly
        else -> RepeatSummary.EveryNMonths(interval)
    }

    RepeatType.YEARLY -> if (interval == 1) RepeatSummary.Yearly else RepeatSummary.EveryNYears(interval)
}
