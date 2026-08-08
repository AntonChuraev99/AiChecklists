package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Which ONE modal surface of the Inbox item sheet is on screen.
 *
 * Exists as a single value rather than as a chain of `if`s in the composable because the constraint
 * it enforces is not cosmetic: two simultaneous `ModalBottomSheet`s fight over the scrim and over
 * Android's predictive-back gesture, so at most one of these may ever be composed. Expressed as an
 * enum, "exactly one" is checkable by a test — the composable cannot drift into showing two.
 *
 * Ordering is the interaction order, innermost first: a surface raised FROM the reminder sheet (the
 * permission prompt, the date picker) wins over it, and the item sheet is what everything falls back
 * to. Every sub-surface leaves `sheetForTaskId` set, so dismissing one returns to the item sheet
 * instead of closing the whole stack.
 */
internal enum class InboxItemSheetSurface {
    /** No task sheet open at all. */
    NONE,
    ITEM_SHEET,
    MOVE_PICKER,
    NOTE_DIALOG,
    NOTIFICATION_PERMISSION,
    CUSTOM_DATE_PICKER,
    REMINDER_SHEET,
}

/**
 * @param taskFound whether the row [InboxScreenState.Content.sheetForTaskId] names is still in the
 *   pager. It can vanish under an open sheet — a sync deletes it, or "hide completed" filters it out
 *   the moment it is checked — and every surface below reads that row's data.
 */
internal fun resolveItemSheetSurface(
    content: InboxScreenState.Content,
    taskFound: Boolean,
): InboxItemSheetSurface = when {
    content.sheetForTaskId == null || !taskFound -> InboxItemSheetSurface.NONE
    content.notificationPermissionOpen -> InboxItemSheetSurface.NOTIFICATION_PERMISSION
    content.customPicker != null -> InboxItemSheetSurface.CUSTOM_DATE_PICKER
    content.reminderSheet != null -> InboxItemSheetSurface.REMINDER_SHEET
    content.noteDraft != null -> InboxItemSheetSurface.NOTE_DIALOG
    content.movePickerOpen -> InboxItemSheetSurface.MOVE_PICKER
    else -> InboxItemSheetSurface.ITEM_SHEET
}

/**
 * Seeds the repeat editor from the rule already stored on [item], or null when it carries none.
 *
 * Reading the stored rule back is what makes the repeat tab an EDITOR rather than a fresh form:
 * without it, opening "every 2 weeks on Tue" and saving turns it into the default "every day".
 */
internal fun repeatConfigOf(
    item: ChecklistFillItem,
    defaultTimeOfDayMinutes: Int = DEFAULT_REPEAT_TIME_MINUTES,
): PendingRepeatConfig? {
    val rule = item.repeatRule ?: return null
    val minutes = item.repeatTimeOfDayMinutes ?: defaultTimeOfDayMinutes
    return PendingRepeatConfig(
        type = rule.type,
        interval = rule.interval,
        weekDays = rule.weekDays ?: emptySet(),
        endCondition = rule.endCondition,
        resetChecks = rule.resetChecks,
        // "Custom" is what makes the interval / weekday controls visible again, so a rule that only a
        // custom setup could have produced must reopen in that mode.
        isCustom = rule.interval > 1 || !rule.weekDays.isNullOrEmpty(),
        timeHour = minutes / 60,
        timeMinute = minutes % 60,
    )
}

/**
 * First fire time for a repeat scheduled at [timeOfDayMinutes]: today when that time is still ahead,
 * otherwise the same time tomorrow.
 *
 * [now] is a parameter rather than `Clock.System.now()` so the boundary (saving a 09:00 daily repeat
 * AT 09:00) is pinned by a test. Getting it wrong in the "already past" direction hands AlarmManager
 * a trigger in the past, which it fires immediately — a daily reminder that rings the instant it is
 * created and then never again today.
 */
internal fun firstRepeatTriggerAt(
    timeOfDayMinutes: Int,
    now: Instant,
    timeZone: TimeZone,
): Long {
    val today = now.toLocalDateTime(timeZone).date
    val triggerTime = LocalTime(timeOfDayMinutes / 60, timeOfDayMinutes % 60)
    val todayTrigger = LocalDateTime(today, triggerTime).toInstant(timeZone).toEpochMilliseconds()
    return if (todayTrigger > now.toEpochMilliseconds()) {
        todayTrigger
    } else {
        LocalDateTime(today.plus(1, DateTimeUnit.DAY), triggerTime)
            .toInstant(timeZone)
            .toEpochMilliseconds()
    }
}

/** 09:00 — the repeat editor's default time when the stored rule carries none. */
internal const val DEFAULT_REPEAT_TIME_MINUTES = 9 * 60
