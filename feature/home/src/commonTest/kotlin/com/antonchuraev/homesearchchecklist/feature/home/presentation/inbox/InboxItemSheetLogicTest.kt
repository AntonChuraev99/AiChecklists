package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Covers the logic behind the Inbox item sheet — the sheet that is now the checklist detail screen's
 * own `ItemDetailsSheet` rather than a four-row copy of it.
 *
 * Two rules are pinned here because breaking either is invisible until it reaches a device:
 *  * exactly ONE modal surface may be composed at a time (two `ModalBottomSheet`s fight over the
 *    scrim and the predictive-back gesture);
 *  * a repeat reopens as the rule that was SAVED, and its first alarm is never in the past.
 *
 * The ViewModel itself cannot be built on a JVM host (its `init` resolves the Inbox title through
 * Compose Resources), so this logic lives in pure functions — the same route `decideRename` took.
 */
class InboxItemSheetLogicTest {

    private fun content(
        sheetForTaskId: String? = TASK_ID,
        movePickerOpen: Boolean = false,
        noteDraft: String? = null,
        reminderSheet: InboxItemReminderUi? = null,
        customPicker: InboxCustomPickerUi? = null,
        notificationPermissionOpen: Boolean = false,
    ) = InboxScreenState.Content(
        pages = emptyList(),
        sheetForTaskId = sheetForTaskId,
        movePickerOpen = movePickerOpen,
        noteDraft = noteDraft,
        reminderSheet = reminderSheet,
        customPicker = customPicker,
        notificationPermissionOpen = notificationPermissionOpen,
    )

    // ── Surface arbitration ──────────────────────────────────────────────────────────────────

    @Test
    fun `no open task means no surface`() {
        assertEquals(
            InboxItemSheetSurface.NONE,
            resolveItemSheetSurface(content(sheetForTaskId = null), taskFound = false),
        )
    }

    @Test
    fun `a task that vanished from the pager closes the sheet instead of rendering stale data`() {
        // A sync deletes the row, or "hide completed" filters it out the instant it is checked. Every
        // surface reads that row, so none of them may render.
        assertEquals(
            InboxItemSheetSurface.NONE,
            resolveItemSheetSurface(content(reminderSheet = InboxItemReminderUi()), taskFound = false),
        )
    }

    @Test
    fun `an open task with nothing raised over it shows the item sheet`() {
        assertEquals(
            InboxItemSheetSurface.ITEM_SHEET,
            resolveItemSheetSurface(content(), taskFound = true),
        )
    }

    @Test
    fun `each sub-surface replaces the item sheet rather than stacking on it`() {
        val cases = mapOf(
            InboxItemSheetSurface.MOVE_PICKER to content(movePickerOpen = true),
            InboxItemSheetSurface.NOTE_DIALOG to content(noteDraft = ""),
            InboxItemSheetSurface.REMINDER_SHEET to content(reminderSheet = InboxItemReminderUi()),
            InboxItemSheetSurface.CUSTOM_DATE_PICKER to content(customPicker = InboxCustomPickerUi(minDateMillis = 0L)),
            InboxItemSheetSurface.NOTIFICATION_PERMISSION to content(notificationPermissionOpen = true),
        )
        cases.forEach { (expected, state) ->
            assertEquals(expected, resolveItemSheetSurface(state, taskFound = true))
        }
    }

    @Test
    fun `a surface raised from the reminder sheet wins over it`() {
        // Both are modal and the reminder sheet is deliberately kept in state while they are up (its
        // full-screen toggle has to survive), so precedence — not nulling — is what prevents a stack.
        assertEquals(
            InboxItemSheetSurface.CUSTOM_DATE_PICKER,
            resolveItemSheetSurface(
                content(
                    reminderSheet = InboxItemReminderUi(tab = ReminderTab.ONCE),
                    customPicker = InboxCustomPickerUi(minDateMillis = 0L),
                ),
                taskFound = true,
            ),
        )
        assertEquals(
            InboxItemSheetSurface.NOTIFICATION_PERMISSION,
            resolveItemSheetSurface(
                content(
                    reminderSheet = InboxItemReminderUi(),
                    customPicker = InboxCustomPickerUi(minDateMillis = 0L),
                    notificationPermissionOpen = true,
                ),
                taskFound = true,
            ),
        )
    }

    @Test
    fun `dismissing a sub-surface returns to the item sheet, not to a closed sheet`() {
        // The task id is what survives every sub-surface; this is the assertion behind that choice.
        val afterDismiss = content(movePickerOpen = false, reminderSheet = null, customPicker = null)
        assertEquals(
            InboxItemSheetSurface.ITEM_SHEET,
            resolveItemSheetSurface(afterDismiss, taskFound = true),
        )
    }

    // ── Repeat editor seeding ────────────────────────────────────────────────────────────────

    @Test
    fun `an item with no repeat rule seeds no repeat config`() {
        assertNull(repeatConfigOf(ChecklistFillItem(text = "Water the plants", checked = false)))
    }

    @Test
    fun `a stored repeat reopens as itself, not as the default daily`() {
        val item = ChecklistFillItem(text = "Standup", checked = false)
            .withRepeatRule(
                ReminderRepeatRule(
                    type = RepeatType.WEEKLY,
                    interval = 2,
                    weekDays = setOf(2, 4),
                    resetChecks = true,
                ),
                timeOfDayMinutes = 8 * 60 + 45,
                nextAt = 0L,
            )

        val config = repeatConfigOf(item)
        requireNotNull(config)
        assertEquals(RepeatType.WEEKLY, config.type)
        assertEquals(2, config.interval)
        assertEquals(setOf(2, 4), config.weekDays)
        assertTrue(config.resetChecks)
        // Without isCustom the interval + weekday controls stay hidden, so a rule only a custom setup
        // could have produced would silently collapse to "every week" on the next save.
        assertTrue(config.isCustom)
        assertEquals(8, config.timeHour)
        assertEquals(45, config.timeMinute)
    }

    @Test
    fun `a plain daily repeat reopens in simple mode at the stored time`() {
        val item = ChecklistFillItem(text = "Pills", checked = false)
            .withRepeatRule(ReminderRepeatRule(type = RepeatType.DAILY), timeOfDayMinutes = 21 * 60, nextAt = 0L)

        val config = repeatConfigOf(item)
        requireNotNull(config)
        assertEquals(RepeatType.DAILY, config.type)
        assertEquals(false, config.isCustom)
        assertEquals(21, config.timeHour)
        assertEquals(0, config.timeMinute)
    }

    // No test for the "stored time is null" fallback: `withRepeatRule` takes a non-null
    // `timeOfDayMinutes`, so that state is only reachable by deserialising a legacy row and cannot be
    // constructed here. The fallback stays as the documented default of [repeatConfigOf].

    // ── First alarm of a repeat ──────────────────────────────────────────────────────────────

    @Test
    fun `a repeat whose time is still ahead today fires today`() {
        val now = Instant.parse("2026-08-07T06:30:00Z")
        assertEquals(
            Instant.parse("2026-08-07T09:00:00Z").toEpochMilliseconds(),
            firstRepeatTriggerAt(timeOfDayMinutes = 9 * 60, now = now, timeZone = TimeZone.UTC),
        )
    }

    @Test
    fun `a repeat whose time has passed today fires tomorrow`() {
        val now = Instant.parse("2026-08-07T21:15:00Z")
        assertEquals(
            Instant.parse("2026-08-08T09:00:00Z").toEpochMilliseconds(),
            firstRepeatTriggerAt(timeOfDayMinutes = 9 * 60, now = now, timeZone = TimeZone.UTC),
        )
    }

    @Test
    fun `a repeat saved at exactly its own time fires tomorrow, never in the past`() {
        // The boundary that matters: AlarmManager fires a past trigger IMMEDIATELY, so treating
        // "equal" as "still ahead" makes a daily reminder ring the moment it is created.
        val now = Instant.parse("2026-08-07T09:00:00Z")
        val trigger = firstRepeatTriggerAt(timeOfDayMinutes = 9 * 60, now = now, timeZone = TimeZone.UTC)
        assertTrue(trigger > now.toEpochMilliseconds())
        assertEquals(Instant.parse("2026-08-08T09:00:00Z").toEpochMilliseconds(), trigger)
    }

    @Test
    fun `the first trigger is resolved in the user's own zone, not in UTC`() {
        // 21:00 UTC on the 7th is 00:00 on the 8th in Kolkata, so a 09:00 repeat is still ahead THAT
        // day — computing this in UTC would push the first alarm a day out.
        val now = Instant.parse("2026-08-07T21:00:00Z")
        val kolkata = TimeZone.of("Asia/Kolkata")
        assertEquals(
            Instant.parse("2026-08-08T03:30:00Z").toEpochMilliseconds(),
            firstRepeatTriggerAt(timeOfDayMinutes = 9 * 60, now = now, timeZone = kolkata),
        )
    }

    private companion object {
        const val TASK_ID = "fill_item_1"
    }
}
