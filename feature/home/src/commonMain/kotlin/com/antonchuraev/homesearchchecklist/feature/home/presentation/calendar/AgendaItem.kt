package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo

/**
 * A single item in the Calendar agenda list.
 *
 * The agenda is a flat list alternating [DateHeader] and [ReminderRow] entries,
 * produced by [CalendarViewModel.buildAgenda]. This sealed interface allows
 * LazyColumn to use a stable key per item type without boxing.
 *
 * Variants:
 * - [DateHeader] — section separator rendered as a date label row. The [label]
 *   is pre-formatted by the ViewModel (e.g. "Monday, May 13" or "Past due").
 *   [isPastDue] distinguishes the synthetic past-due group from real dates.
 * - [ReminderRow] — a tappable reminder entry backed by [TodayReminderInfo].
 */
sealed interface AgendaItem {

    /**
     * Section header separating reminders by date.
     *
     * @param epochDay A unique key derived from the date (or a negative sentinel
     *   for the synthetic "Past due" group). Used as LazyColumn item key.
     * @param label Pre-formatted display string, e.g. "Tuesday, May 13".
     * @param isPastDue True for the synthetic past-due group header.
     */
    data class DateHeader(
        val epochDay: Long,
        val label: String,
        val isPastDue: Boolean = false,
    ) : AgendaItem

    /**
     * A tappable reminder entry.
     *
     * @param info Domain model carrying all navigation and display data.
     */
    data class ReminderRow(
        val info: TodayReminderInfo,
    ) : AgendaItem
}
