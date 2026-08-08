package com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the split between "which phrasing applies" (pure, here) and "which words" (string
 * resources, not reachable from a plain unit test).
 *
 * The `buildRepeatSummary` cases are characterization tests: they assert the exact English wording
 * the screen shipped before the summary was localized, so the refactor cannot silently reword the
 * EN locale on the paths that have not migrated yet.
 */
class RepeatSummaryTest {

    private fun config(
        type: RepeatType,
        interval: Int = 1,
        weekDays: Set<Int> = emptySet(),
    ) = PendingRepeatConfig(type = type, interval = interval, weekDays = weekDays)

    // ─── Structural mapping ───────────────────────────────────────────────────

    @Test
    fun dailyInterval1_isDailyPreset() {
        assertEquals(RepeatSummary.Daily, config(RepeatType.DAILY).toRepeatSummary())
    }

    @Test
    fun dailyInterval5_isArbitraryInterval() {
        assertEquals(
            RepeatSummary.EveryNDays(5),
            config(RepeatType.DAILY, interval = 5).toRepeatSummary(),
        )
    }

    @Test
    fun weeklyPresets_mapToTheSamePresetsTheSheetOffers() {
        assertEquals(RepeatSummary.Weekly, config(RepeatType.WEEKLY).toRepeatSummary())
        assertEquals(RepeatSummary.Biweekly, config(RepeatType.WEEKLY, interval = 2).toRepeatSummary())
        assertEquals(
            RepeatSummary.EveryNWeeks(3),
            config(RepeatType.WEEKLY, interval = 3).toRepeatSummary(),
        )
    }

    @Test
    fun monFriSelection_isTheRangePhrasing_notAFiveDayList() {
        assertEquals(
            RepeatSummary.Weekdays,
            config(RepeatType.WEEKLY, weekDays = setOf(5, 1, 3, 2, 4)).toRepeatSummary(),
        )
    }

    @Test
    fun otherWeekdaySelection_isSortedAscending() {
        assertEquals(
            RepeatSummary.OnWeekDays(listOf(1, 3, 7)),
            config(RepeatType.WEEKLY, weekDays = setOf(7, 1, 3)).toRepeatSummary(),
        )
    }

    @Test
    fun weekdaysOutsideIsoRange_areDroppedInsteadOfIndexingPastTheDayNames() {
        // The pre-refactor builder did `dayNames[it - 1]` and would throw on a corrupted row.
        assertEquals(
            RepeatSummary.OnWeekDays(listOf(2)),
            config(RepeatType.WEEKLY, weekDays = setOf(0, 2, 9)).toRepeatSummary(),
        )
        // Nothing survives the filter -> fall back to the plain interval phrasing, never an empty list.
        assertEquals(
            RepeatSummary.Weekly,
            config(RepeatType.WEEKLY, weekDays = setOf(0, 9)).toRepeatSummary(),
        )
    }

    @Test
    fun monthlyAndYearlyPresets() {
        assertEquals(RepeatSummary.Monthly, config(RepeatType.MONTHLY).toRepeatSummary())
        assertEquals(RepeatSummary.Quarterly, config(RepeatType.MONTHLY, interval = 3).toRepeatSummary())
        assertEquals(
            RepeatSummary.EveryNMonths(4),
            config(RepeatType.MONTHLY, interval = 4).toRepeatSummary(),
        )
        assertEquals(RepeatSummary.Yearly, config(RepeatType.YEARLY).toRepeatSummary())
        assertEquals(
            RepeatSummary.EveryNYears(2),
            config(RepeatType.YEARLY, interval = 2).toRepeatSummary(),
        )
    }

    @Test
    fun ruleAndConfigAgree_soTheSheetCanSummarizeEither() {
        // The sheet now derives the card text from the persisted rule while the caller still passes a
        // config-derived string; if these two ever disagreed the card would contradict the picker.
        listOf(
            config(RepeatType.DAILY, interval = 2),
            config(RepeatType.WEEKLY, weekDays = setOf(1, 2, 3, 4, 5)),
            config(RepeatType.WEEKLY, weekDays = setOf(2, 6)),
            config(RepeatType.WEEKLY, interval = 2),
            config(RepeatType.MONTHLY, interval = 3),
            config(RepeatType.YEARLY),
        ).forEach { cfg ->
            assertEquals(cfg.toRepeatSummary(), cfg.toRule().toRepeatSummary(), "config vs rule: $cfg")
        }
    }

    @Test
    fun ruleWithWeekDaysOnANonWeeklyType_ignoresThem() {
        val rule = ReminderRepeatRule(type = RepeatType.MONTHLY, interval = 1, weekDays = setOf(1, 2))
        assertEquals(RepeatSummary.Monthly, rule.toRepeatSummary())
    }

    // ─── English wording preserved for the not-yet-migrated call sites ────────

    @Suppress("DEPRECATION")
    @Test
    fun legacyBuilder_stillProducesTheExactPreRefactorEnglish() {
        assertEquals("Every day", buildRepeatSummary(config(RepeatType.DAILY)))
        assertEquals("Every 5 days", buildRepeatSummary(config(RepeatType.DAILY, interval = 5)))
        assertEquals("Every week", buildRepeatSummary(config(RepeatType.WEEKLY)))
        assertEquals("Every 2 weeks", buildRepeatSummary(config(RepeatType.WEEKLY, interval = 2)))
        assertEquals("Every 3 weeks", buildRepeatSummary(config(RepeatType.WEEKLY, interval = 3)))
        assertEquals(
            "Mon–Fri",
            buildRepeatSummary(config(RepeatType.WEEKLY, weekDays = setOf(1, 2, 3, 4, 5))),
        )
        assertEquals(
            "Mon, Wed, Sun",
            buildRepeatSummary(config(RepeatType.WEEKLY, weekDays = setOf(7, 1, 3))),
        )
        assertEquals("Every month", buildRepeatSummary(config(RepeatType.MONTHLY)))
        assertEquals("Every 3 months", buildRepeatSummary(config(RepeatType.MONTHLY, interval = 3)))
        assertEquals("Every 4 months", buildRepeatSummary(config(RepeatType.MONTHLY, interval = 4)))
        assertEquals("Every year", buildRepeatSummary(config(RepeatType.YEARLY)))
        assertEquals("Every 2 years", buildRepeatSummary(config(RepeatType.YEARLY, interval = 2)))
    }
}
