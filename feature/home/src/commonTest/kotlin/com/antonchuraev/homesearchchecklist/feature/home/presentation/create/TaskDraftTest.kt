package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
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
}
