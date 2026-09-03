package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Covers the day-screen capture rules that the emulator cannot reach.
 *
 * The evening branch of [defaultDayScreenPreset] only fires after 18:00 local, and the emulator image
 * refuses `adb root`, so its clock cannot be moved to prove it live. Both functions therefore take an
 * injectable clock precisely so the boundary is testable — a rule that can only be observed by waiting
 * until evening is a rule nobody checks.
 */
class TaskDraftTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 8, 7)

    private fun at(hour: Int, minute: Int = 0): Instant =
        LocalDateTime(today, LocalTime(hour, minute)).toInstant(tz)

    // ── defaultDayScreenPreset ──────────────────────────────────────────────────

    @Test
    fun beforeEvening_preselectsTonight() {
        assertEquals(ItemCreateReminderPreset.TONIGHT, defaultDayScreenPreset(at(8, 15), tz))
    }

    /**
     * 17:59 still gets "Tonight" even though it leaves one minute. The alternative — switching early
     * to keep the gap comfortable — would mean the chip changes under a user who opened the dock at
     * 17:58 and typed for two minutes, and a chip that renames itself mid-capture is worse than a
     * reminder that fires soon.
     */
    @Test
    fun oneMinuteBeforeEvening_stillPreselectsTonight() {
        assertEquals(ItemCreateReminderPreset.TONIGHT, defaultDayScreenPreset(at(17, 59), tz))
    }

    /**
     * The whole reason this function exists rather than a constant `TONIGHT`.
     *
     * [computePresetReminderAt] rolls TONIGHT to TOMORROW 18:00 once today's has passed — correct in
     * itself (a reminder must never be in the past), but on a screen that draws TODAY it would file
     * the task out of sight. Past 18:00 the day screen therefore picks a different CHIP, not a
     * different time for the same chip.
     */
    @Test
    fun atEvening_preselectsOneHour_notTonight() {
        assertEquals(ItemCreateReminderPreset.ONE_HOUR, defaultDayScreenPreset(at(18, 0), tz))
    }

    @Test
    fun lateEvening_preselectsOneHour() {
        assertEquals(ItemCreateReminderPreset.ONE_HOUR, defaultDayScreenPreset(at(23, 30), tz))
    }

    @Test
    fun dayScreenDraft_arrivesWithTheChipOnAndAResolvedTime() {
        val draft = dayScreenDraft(at(9, 0), tz)

        assertEquals(ItemCreateReminderPreset.TONIGHT, draft.reminderPreset)
        assertEquals(at(18, 0).toEpochMilliseconds(), draft.reminderAt)
        assertEquals("", draft.text)
        assertTrue(!draft.canSend, "an empty draft must not be sendable just because a chip is on")
    }

    // ── computePresetReminderAt ─────────────────────────────────────────────────

    @Test
    fun tonight_beforeSix_isTodayAtSix() {
        val at = computePresetReminderAt(ItemCreateReminderPreset.TONIGHT, at(9, 0), tz)
        assertEquals(LocalTime(18, 0), Instant.fromEpochMilliseconds(at).toLocalDateTime(tz).time)
        assertEquals(today, Instant.fromEpochMilliseconds(at).toLocalDateTime(tz).date)
    }

    @Test
    fun tonight_afterSix_rollsToTomorrow() {
        val at = computePresetReminderAt(ItemCreateReminderPreset.TONIGHT, at(20, 0), tz)
        val resolved = Instant.fromEpochMilliseconds(at).toLocalDateTime(tz)
        assertEquals(LocalTime(18, 0), resolved.time)
        assertEquals(LocalDate(2026, 8, 8), resolved.date)
    }

    @Test
    fun tomorrowMorning_isNineAmNextDay() {
        val at = computePresetReminderAt(ItemCreateReminderPreset.TOMORROW_MORNING, at(20, 0), tz)
        val resolved = Instant.fromEpochMilliseconds(at).toLocalDateTime(tz)
        assertEquals(LocalTime(9, 0), resolved.time)
        assertEquals(LocalDate(2026, 8, 8), resolved.date)
    }

    // ── chip toggle semantics ───────────────────────────────────────────────────

    /**
     * Re-tapping the active chip clears the reminder. Without this a reminder set by accident could
     * only be removed by sending the task and editing it afterwards.
     */
    @Test
    fun tappingTheActivePresetAgain_clearsTheReminder() {
        val armed = TaskDraft().withPreset(ItemCreateReminderPreset.TONIGHT, at(9, 0), tz)
        val cleared = armed.withPreset(ItemCreateReminderPreset.TONIGHT, at(9, 0), tz)

        assertNull(cleared.reminderPreset)
        assertNull(cleared.reminderAt)
    }

    @Test
    fun tappingAnotherPreset_replacesRatherThanAdds() {
        val draft = TaskDraft()
            .withPreset(ItemCreateReminderPreset.TONIGHT, at(9, 0), tz)
            .withPreset(ItemCreateReminderPreset.ONE_HOUR, at(9, 0), tz)

        assertEquals(ItemCreateReminderPreset.ONE_HOUR, draft.reminderPreset)
        assertEquals(at(10, 0).toEpochMilliseconds(), draft.reminderAt)
    }

    // ── resolveReminderAtNow ────────────────────────────────────────────────────

    /**
     * The defect this function exists for: the chip resolves its time when TAPPED, so a dock left
     * open across the preset's own boundary would write a moment that has already gone.
     */
    @Test
    fun aDockLeftOpenAcrossSix_doesNotWriteAPastReminder() {
        val armedAtFivePastFive = TaskDraft().withPreset(ItemCreateReminderPreset.TONIGHT, at(17, 55), tz)
        assertEquals(at(18, 0).toEpochMilliseconds(), armedAtFivePastFive.reminderAt)

        // Sent fifteen minutes later — after 18:00 has passed.
        val sentAt = at(18, 10)
        val resolved = armedAtFivePastFive.resolveReminderAtNow(sentAt, tz)

        assertTrue(
            resolved!! > sentAt.toEpochMilliseconds(),
            "resolved reminder must be in the future at Send, was ${Instant.fromEpochMilliseconds(resolved)}",
        )
        assertEquals(LocalDate(2026, 8, 8), Instant.fromEpochMilliseconds(resolved).toLocalDateTime(tz).date)
    }

    /**
     * A picked absolute time is an instant the user chose, not a relative promise, so it must survive
     * Send untouched even if it is close or already past.
     */
    @Test
    fun aCustomPickedTime_isNotRecomputed() {
        val picked = at(16, 30).toEpochMilliseconds()
        val draft = TaskDraft().withCustomReminder(picked)

        assertEquals(ItemCreateReminderPreset.CUSTOM, draft.reminderPreset)
        assertEquals(picked, draft.resolveReminderAtNow(at(20, 0), tz))
    }

    @Test
    fun noChip_resolvesToNoReminder() {
        assertNull(TaskDraft(text = "plain task").resolveReminderAtNow(at(9, 0), tz))
    }

    /**
     * Everything is dropped after Send, chips included: the dock stays open for a SERIES of captures,
     * and a sticky "Tonight" would silently stamp a reminder onto every following task.
     */
    @Test
    fun clearing_dropsTheChipsTooNotJustTheText() {
        val draft = TaskDraft(text = "milk")
            .withPreset(ItemCreateReminderPreset.TONIGHT, at(9, 0), tz)
            .withImportantToggled()

        val cleared = draft.cleared()

        assertEquals("", cleared.text)
        assertNull(cleared.reminderPreset)
        assertNull(cleared.reminderAt)
        assertTrue(!cleared.important)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // WEEKEND / NEXT_WEEK — weekday-shaped presets
    //
    // Deliberately run in Europe/Moscow (UTC+3, no DST since 2014) rather than in the [tz] used
    // above. Both presets pick a DAY OF WEEK, and a day of week read off the UTC instant instead of
    // off the local date is right in UTC and wrong everywhere else — a bug an all-UTC suite cannot
    // see. The offset is what makes these assertions load-bearing.
    // ════════════════════════════════════════════════════════════════════════════

    private val msk = TimeZone.of("Europe/Moscow")

    private val mon = LocalDate(2026, 8, 3)
    private val wed = LocalDate(2026, 8, 5)
    private val fri = LocalDate(2026, 8, 7)
    private val sat = LocalDate(2026, 8, 8)
    private val sun = LocalDate(2026, 8, 9)
    private val nextMon = LocalDate(2026, 8, 10)
    private val nextSat = LocalDate(2026, 8, 15)
    private val monAfterNext = LocalDate(2026, 8, 17)

    private fun mskAt(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        LocalDateTime(date, LocalTime(hour, minute)).toInstant(msk)

    private fun resolvedInMsk(preset: ItemCreateReminderPreset, now: Instant): LocalDateTime =
        Instant.fromEpochMilliseconds(computePresetReminderAt(preset, now, msk)).toLocalDateTime(msk)

    /**
     * Guards every expectation below. The anchors are hand-picked calendar dates, and a wrong
     * weekday would not fail the suite — it would quietly turn "the coming Saturday" into an
     * assertion about a Thursday and pass.
     */
    @Test
    fun anchorDatesAreTheWeekdaysTheyClaimToBe() {
        assertEquals(DayOfWeek.MONDAY, mon.dayOfWeek)
        assertEquals(DayOfWeek.WEDNESDAY, wed.dayOfWeek)
        assertEquals(DayOfWeek.FRIDAY, fri.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, sat.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, sun.dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, nextMon.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, nextSat.dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, monAfterNext.dayOfWeek)
    }

    // ── WEEKEND ─────────────────────────────────────────────────────────────────

    @Test
    fun weekend_onAWeekday_isTheComingSaturdayAtTen() {
        assertEquals(
            LocalDateTime(sat, LocalTime(10, 0)),
            resolvedInMsk(ItemCreateReminderPreset.WEEKEND, mskAt(mon, 8, 0)),
        )
        assertEquals(
            LocalDateTime(sat, LocalTime(10, 0)),
            resolvedInMsk(ItemCreateReminderPreset.WEEKEND, mskAt(wed, 13, 45)),
        )
        assertEquals(
            LocalDateTime(sat, LocalTime(10, 0)),
            resolvedInMsk(ItemCreateReminderPreset.WEEKEND, mskAt(fri, 23, 30)),
        )
    }

    @Test
    fun weekend_onSaturdayMorning_isTodayAtTen() {
        assertEquals(
            LocalDateTime(sat, LocalTime(10, 0)),
            resolvedInMsk(ItemCreateReminderPreset.WEEKEND, mskAt(sat, 8, 30)),
        )
    }

    /**
     * The defect this branch exists to prevent. Once Saturday 10:00 has gone, "Weekend" must move to
     * SUNDAY — the weekend the user is standing in. Rolling to the following Saturday would be a
     * silent six-day error, noticed only after the thing it was meant to cover had happened.
     */
    @Test
    fun weekend_onSaturdayAfternoon_rollsToSunday_notToTheNextWeekend() {
        val resolved = resolvedInMsk(ItemCreateReminderPreset.WEEKEND, mskAt(sat, 14, 0))

        assertEquals(LocalDateTime(sun, LocalTime(10, 0)), resolved)
        assertEquals(DayOfWeek.SUNDAY, resolved.date.dayOfWeek)
    }

    /** Exactly 10:00 counts as gone: a reminder for this instant would fire as the user taps. */
    @Test
    fun weekend_atExactlyTenOnSaturday_rollsToSunday() {
        assertEquals(
            LocalDateTime(sun, LocalTime(10, 0)),
            resolvedInMsk(ItemCreateReminderPreset.WEEKEND, mskAt(sat, 10, 0)),
        )
    }

    /**
     * Sunday is the one day where "weekend" points a whole week out — the current weekend is spent,
     * and Sunday-at-10:00 is either past or hours away, neither of which is what "weekend" promises.
     */
    @Test
    fun weekend_onSunday_isTheSaturdayOfTheComingWeekend() {
        assertEquals(
            LocalDateTime(nextSat, LocalTime(10, 0)),
            resolvedInMsk(ItemCreateReminderPreset.WEEKEND, mskAt(sun, 11, 0)),
        )
    }

    // ── NEXT_WEEK ───────────────────────────────────────────────────────────────

    /**
     * On a Monday "next week" is the Monday SEVEN days out. Resolving it to today would answer
     * "when" with "now" — a future timestamp wearing the defect of a past one.
     */
    @Test
    fun nextWeek_onMonday_isTheFollowingMonday_notToday() {
        val resolved = resolvedInMsk(ItemCreateReminderPreset.NEXT_WEEK, mskAt(mon, 8, 0))

        assertEquals(LocalDateTime(nextMon, LocalTime(9, 0)), resolved)
        assertTrue(resolved.date != mon, "\"next week\" tapped on Monday must not resolve to that Monday")
    }

    @Test
    fun nextWeek_onFriday_isTheComingMondayAtNine() {
        assertEquals(
            LocalDateTime(nextMon, LocalTime(9, 0)),
            resolvedInMsk(ItemCreateReminderPreset.NEXT_WEEK, mskAt(fri, 16, 20)),
        )
    }

    @Test
    fun nextWeek_onSunday_isTheVeryNextDay() {
        assertEquals(
            LocalDateTime(nextMon, LocalTime(9, 0)),
            resolvedInMsk(ItemCreateReminderPreset.NEXT_WEEK, mskAt(sun, 21, 0)),
        )
    }

    // ── The invariant: no preset ever resolves into the past ────────────────────

    /**
     * The acceptance requirement of the whole rail, checked across a full week × the hours where the
     * branches turn over.
     *
     * A preset that resolves behind [now] renders a chip that swallows a tap and silently does
     * nothing — precisely the failure mode for which "Pick time" and "Repeat" are switched off in the
     * v2 dock today. Strictly greater, not greater-or-equal: a reminder for this exact instant fires
     * as the user is still typing.
     */
    @Test
    fun noPresetEverResolvesIntoThePast() {
        val week = listOf(mon, wed, fri, sat, sun, nextMon)
        val hours = listOf(0 to 0, 8 to 59, 9 to 0, 9 to 1, 10 to 0, 10 to 1, 17 to 59, 18 to 0, 18 to 1, 23 to 59)

        for (date in week) {
            for ((hour, minute) in hours) {
                val now = mskAt(date, hour, minute)
                for (preset in presetDisplayOrder) {
                    val resolved = computePresetReminderAt(preset, now, msk)
                    assertTrue(
                        resolved > now.toEpochMilliseconds(),
                        "$preset resolved to ${Instant.fromEpochMilliseconds(resolved).toLocalDateTime(msk)} " +
                            "for now=${now.toLocalDateTime(msk)} (${date.dayOfWeek}) — that is not in the future",
                    )
                }
            }
        }
    }

    // ── availablePresets ────────────────────────────────────────────────────────

    /**
     * Every preset is built to resolve into the future at any hour, so the morning list is the whole
     * display order. The value of asserting the exact LIST rather than the size is the ORDER: the
     * dock renders this straight through, and a reshuffle here is a silent UI change.
     */
    @Test
    fun availablePresets_inTheMorning_isTheWholeDisplayOrder() {
        assertEquals(
            listOf(
                ItemCreateReminderPreset.TONIGHT,
                ItemCreateReminderPreset.TOMORROW_MORNING,
                ItemCreateReminderPreset.WEEKEND,
                ItemCreateReminderPreset.ONE_HOUR,
                ItemCreateReminderPreset.NEXT_WEEK,
            ),
            availablePresets(mskAt(wed, 8, 0), msk),
        )
    }

    /**
     * Late evening is where a naive implementation loses chips: "Tonight" is behind, "Weekend" may
     * be behind on a Saturday. Nothing may drop out — each preset rolls forward on its own instead.
     */
    @Test
    fun availablePresets_lateAtNight_stillOffersEveryPreset() {
        assertEquals(presetDisplayOrder, availablePresets(mskAt(wed, 23, 50), msk))
        assertEquals(presetDisplayOrder, availablePresets(mskAt(sat, 23, 50), msk))
        assertEquals(presetDisplayOrder, availablePresets(mskAt(sun, 23, 50), msk))
    }

    /**
     * CUSTOM is the RESULT of the picker, not a value to offer. Listing it would ask the user to
     * choose a time by choosing "a time I will choose".
     */
    @Test
    fun availablePresets_neverOffersCustom() {
        for (date in listOf(mon, wed, fri, sat, sun)) {
            for (hour in 0..23) {
                assertTrue(
                    ItemCreateReminderPreset.CUSTOM !in availablePresets(mskAt(date, hour), msk),
                    "CUSTOM offered as a preset at ${date.dayOfWeek} ${hour}:00",
                )
            }
        }
    }

    // ── chip toggle semantics for the new presets ───────────────────────────────

    @Test
    fun tappingWeekendAgain_clearsTheReminder() {
        val now = mskAt(wed, 9, 0)
        val armed = TaskDraft().withPreset(ItemCreateReminderPreset.WEEKEND, now, msk)
        assertEquals(ItemCreateReminderPreset.WEEKEND, armed.reminderPreset)
        assertEquals(mskAt(sat, 10, 0).toEpochMilliseconds(), armed.reminderAt)

        val cleared = armed.withPreset(ItemCreateReminderPreset.WEEKEND, now, msk)
        assertNull(cleared.reminderPreset)
        assertNull(cleared.reminderAt)
    }

    @Test
    fun tappingNextWeekAgain_clearsTheReminder() {
        val now = mskAt(fri, 12, 0)
        val armed = TaskDraft().withPreset(ItemCreateReminderPreset.NEXT_WEEK, now, msk)
        assertEquals(ItemCreateReminderPreset.NEXT_WEEK, armed.reminderPreset)
        assertEquals(mskAt(nextMon, 9, 0).toEpochMilliseconds(), armed.reminderAt)

        val cleared = armed.withPreset(ItemCreateReminderPreset.NEXT_WEEK, now, msk)
        assertNull(cleared.reminderPreset)
        assertNull(cleared.reminderAt)
    }

    /** The new presets join the existing single-select group rather than forming a second one. */
    @Test
    fun weekendReplacesTonight_ratherThanCoexistingWithIt() {
        val now = mskAt(wed, 9, 0)
        val draft = TaskDraft()
            .withPreset(ItemCreateReminderPreset.TONIGHT, now, msk)
            .withPreset(ItemCreateReminderPreset.WEEKEND, now, msk)

        assertEquals(ItemCreateReminderPreset.WEEKEND, draft.reminderPreset)
        assertEquals(mskAt(sat, 10, 0).toEpochMilliseconds(), draft.reminderAt)
    }

    // ── resolveReminderAtNow for the new presets ────────────────────────────────

    /**
     * A dock armed with "Weekend" at 09:55 on a Saturday and sent at 10:05 must not write the 10:00
     * it captured — that moment has gone. Same defect as the shipped 18:00 case, on a different
     * boundary.
     */
    @Test
    fun weekendArmedBeforeTen_isRecomputedWhenSentAfterTen() {
        val armed = TaskDraft().withPreset(ItemCreateReminderPreset.WEEKEND, mskAt(sat, 9, 55), msk)
        assertEquals(mskAt(sat, 10, 0).toEpochMilliseconds(), armed.reminderAt)

        val sentAt = mskAt(sat, 10, 5)
        val resolved = armed.resolveReminderAtNow(sentAt, msk)!!

        assertEquals(mskAt(sun, 10, 0).toEpochMilliseconds(), resolved)
        assertTrue(resolved > sentAt.toEpochMilliseconds(), "a recomputed reminder must be in the future")
    }

    /**
     * "Next week" armed on Friday and actually sent on the Monday it pointed at: 09:00 is behind, so
     * Send must move it on rather than write the stale value it was holding.
     */
    @Test
    fun nextWeekArmedOnFriday_isRecomputedWhenSentAfterThatMondayMorning() {
        val armed = TaskDraft().withPreset(ItemCreateReminderPreset.NEXT_WEEK, mskAt(fri, 16, 0), msk)
        assertEquals(mskAt(nextMon, 9, 0).toEpochMilliseconds(), armed.reminderAt)

        val sentAt = mskAt(nextMon, 10, 0)
        val resolved = armed.resolveReminderAtNow(sentAt, msk)!!

        assertEquals(mskAt(monAfterNext, 9, 0).toEpochMilliseconds(), resolved)
        assertTrue(resolved > sentAt.toEpochMilliseconds(), "a recomputed reminder must be in the future")
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Send must not silently PUSH a reminder that is still perfectly good.
    //
    // resolveReminderAtNow exists to rescue a reminder whose moment has passed while the dock sat
    // open. Recomputing one that has NOT passed is the same class of defect wearing the opposite
    // sign: the user is not warned, the task simply lands a period later than the chip they tapped.
    // The window is a few minutes wide and always sits across midnight, which is precisely why
    // nobody reports it — the user blames themselves for "picking the wrong day".
    // ════════════════════════════════════════════════════════════════════════════

    private val tue = LocalDate(2026, 8, 11)
    private val thu = LocalDate(2026, 8, 6)

    @Test
    fun tuesdayAndThursdayAnchorsAreTheWeekdaysTheyClaim() {
        assertEquals(DayOfWeek.TUESDAY, tue.dayOfWeek)
        assertEquals(DayOfWeek.THURSDAY, thu.dayOfWeek)
    }

    /**
     * Ten minutes across midnight costs the user a WEEK. Armed on Sunday night, "Next week" points
     * at tomorrow's Monday 09:00 — nine hours away and entirely valid. Send at 00:05 and the
     * unconditional recompute now reads "today is Monday", applies the on-Monday-means-next-Monday
     * rule, and files the task seven days out.
     */
    @Test
    fun nextWeekArmedBeforeMidnight_keepsItsMondayWhenSentAfterMidnight() {
        val armed = TaskDraft().withPreset(ItemCreateReminderPreset.NEXT_WEEK, mskAt(sun, 23, 55), msk)
        assertEquals(mskAt(nextMon, 9, 0).toEpochMilliseconds(), armed.reminderAt)

        val sentAt = mskAt(nextMon, 0, 5)
        val resolved = armed.resolveReminderAtNow(sentAt, msk)!!

        assertEquals(
            mskAt(nextMon, 9, 0).toEpochMilliseconds(),
            resolved,
            "the Monday the user chose is still nine hours away — Send must not push it a week on",
        )
    }

    /** Same defect, one day of cost instead of seven: tomorrow morning becomes the morning after. */
    @Test
    fun tomorrowMorningArmedBeforeMidnight_keepsItsMorningWhenSentAfterMidnight() {
        val armed = TaskDraft().withPreset(ItemCreateReminderPreset.TOMORROW_MORNING, mskAt(sun, 23, 55), msk)
        assertEquals(mskAt(nextMon, 9, 0).toEpochMilliseconds(), armed.reminderAt)

        val sentAt = mskAt(nextMon, 0, 5)
        val resolved = armed.resolveReminderAtNow(sentAt, msk)!!

        assertEquals(
            mskAt(nextMon, 9, 0).toEpochMilliseconds(),
            resolved,
            "09:00 is still nine hours away — Send must not roll it to ${tue.dayOfWeek}",
        )
    }

    /**
     * The weekend variant costs six days. Armed late on Saturday the preset has already rolled to
     * Sunday 10:00 — correct. Send five minutes after midnight and the recompute sees a Sunday,
     * applies the on-Sunday-means-the-coming-weekend rule, and jumps to the following Saturday.
     */
    @Test
    fun weekendArmedBeforeMidnight_keepsItsSundayWhenSentAfterMidnight() {
        val armed = TaskDraft().withPreset(ItemCreateReminderPreset.WEEKEND, mskAt(sat, 23, 55), msk)
        assertEquals(mskAt(sun, 10, 0).toEpochMilliseconds(), armed.reminderAt)

        val sentAt = mskAt(sun, 0, 5)
        val resolved = armed.resolveReminderAtNow(sentAt, msk)!!

        assertEquals(
            mskAt(sun, 10, 0).toEpochMilliseconds(),
            resolved,
            "Sunday 10:00 is still ten hours away — Send must not jump to the next weekend",
        )
    }

    /**
     * The counterweight, and the reason "recompute only when stale" cannot simply be applied to
     * every preset: "In 1 hour" is a RELATIVE promise. It is measured from the moment the task is
     * sent, not from the moment the chip was tapped, so an hour of typing must move it — the one
     * preset for which a still-valid stored value is nevertheless the wrong answer.
     */
    @Test
    fun oneHourIsMeasuredFromSend_notFromTheTap() {
        val armed = TaskDraft().withPreset(ItemCreateReminderPreset.ONE_HOUR, mskAt(wed, 9, 0), msk)
        assertEquals(mskAt(wed, 10, 0).toEpochMilliseconds(), armed.reminderAt)

        // Deliberately BEFORE the captured 10:00. Sending after it would make both an offset and a
        // wall-clock reading recompute, and the test would pass either way — it has to be sent while
        // the stored value is still valid for the two behaviours to differ at all.
        val sentAt = mskAt(wed, 9, 30)

        assertEquals(
            mskAt(wed, 10, 30).toEpochMilliseconds(),
            armed.resolveReminderAtNow(sentAt, msk),
            "\"in 1 hour\" means an hour from Send; the still-valid 10:00 from the tap is not it",
        )
    }

    /**
     * The boundary of "still valid": a stored moment equal to Send is NOT still valid.
     *
     * Keeping it would schedule a reminder for the instant the user pressed Send — the same defect
     * as a past one, one millisecond short of being obvious, and the mirror of the strict `>` that
     * [computePresetReminderAt] already applies when it decides to roll TONIGHT and WEEKEND forward.
     */
    @Test
    fun aStoredMomentThatIsExactlyNow_countsAsGoneAndIsRecomputed() {
        val armed = TaskDraft().withPreset(ItemCreateReminderPreset.TONIGHT, mskAt(wed, 9, 0), msk)
        val storedEvening = mskAt(wed, 18, 0)
        assertEquals(storedEvening.toEpochMilliseconds(), armed.reminderAt)

        // Sent at exactly 18:00:00.000 — the stored moment has arrived, not passed.
        val resolved = armed.resolveReminderAtNow(storedEvening, msk)!!

        assertEquals(mskAt(thu, 18, 0).toEpochMilliseconds(), resolved)
        assertTrue(
            resolved > storedEvening.toEpochMilliseconds(),
            "a reminder due at the very instant of Send must be rolled on, not written as-is",
        )
    }

    // ── the anchor classification itself ────────────────────────────────────────

    /**
     * The table the whole Send path turns on. Spelled out rather than derived, so that changing a
     * preset's anchor — the one edit that silently moves users' tasks — cannot be made quietly.
     */
    @Test
    fun everyPresetDeclaresTheAnchorItsBehaviourAssumes() {
        assertEquals(ReminderAnchor.OFFSET_FROM_SEND, ItemCreateReminderPreset.ONE_HOUR.anchor)
        assertEquals(ReminderAnchor.WALL_CLOCK, ItemCreateReminderPreset.TOMORROW_MORNING.anchor)
        assertEquals(ReminderAnchor.WALL_CLOCK, ItemCreateReminderPreset.TONIGHT.anchor)
        assertEquals(ReminderAnchor.WALL_CLOCK, ItemCreateReminderPreset.WEEKEND.anchor)
        assertEquals(ReminderAnchor.WALL_CLOCK, ItemCreateReminderPreset.NEXT_WEEK.anchor)
        assertEquals(ReminderAnchor.PICKED, ItemCreateReminderPreset.CUSTOM.anchor)
    }

    /**
     * The behavioural half of the classification, written generically so a preset added later is
     * covered whether or not anyone remembers this file: EVERY wall-clock preset must hand Send back
     * the exact moment it captured, as long as that moment is still ahead.
     */
    @Test
    fun everyWallClockPreset_keepsAStillValidStoredMoment() {
        val tappedAt = mskAt(wed, 8, 0)
        val sentAt = mskAt(wed, 8, 5)

        val wallClock = presetDisplayOrder.filter { it.anchor == ReminderAnchor.WALL_CLOCK }
        assertTrue(wallClock.isNotEmpty(), "the display order must still contain wall-clock presets")

        for (preset in wallClock) {
            val armed = TaskDraft().withPreset(preset, tappedAt, msk)
            val stored = armed.reminderAt!!
            assertTrue(stored > sentAt.toEpochMilliseconds(), "$preset: fixture must stay ahead of Send")

            assertEquals(
                stored,
                armed.resolveReminderAtNow(sentAt, msk),
                "$preset is anchored to the wall clock, so Send must not move a moment still ahead",
            )
        }
    }

    /** The mirror image: an offset preset must NOT reuse the value it captured. */
    @Test
    fun everyOffsetPreset_movesWithSend() {
        val tappedAt = mskAt(wed, 8, 0)
        val sentAt = mskAt(wed, 8, 5)

        val offset = presetDisplayOrder.filter { it.anchor == ReminderAnchor.OFFSET_FROM_SEND }
        assertTrue(offset.isNotEmpty(), "the display order must still contain an offset preset")

        for (preset in offset) {
            val armed = TaskDraft().withPreset(preset, tappedAt, msk)

            assertEquals(
                computePresetReminderAt(preset, sentAt, msk),
                armed.resolveReminderAtNow(sentAt, msk),
                "$preset is measured from Send, so the value captured at the tap must not survive",
            )
            assertTrue(
                armed.resolveReminderAtNow(sentAt, msk) != armed.reminderAt,
                "$preset must actually have moved between tap and Send",
            )
        }
    }
}
