package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo

// ─── State ────────────────────────────────────────────────────────────────────

/**
 * Screen state for the Calendar screen.
 *
 * Emitted by [CalendarViewModel.screenState]. UI renders one of four variants:
 * - [Loading] — initial load, show progress indicator.
 * - [Content] — agenda items available.
 * - [Empty] — no reminders in the fetch range.
 * - [Error] — repository threw an exception; user can retry.
 */
sealed interface CalendarState : State {

    /** Initial state while the first reminders emission is pending. */
    data object Loading : CalendarState

    /**
     * Reminders loaded successfully.
     *
     * @param agenda Flat list of [AgendaItem] entries (headers + rows) in
     *   display order. Past-due group first (if any), then date sections
     *   ascending, always including today even if empty.
     */
    data class Content(val agenda: List<AgendaItem>) : CalendarState

    /** No reminders in the [-7d, +30d] range. */
    data object Empty : CalendarState

    /**
     * Repository threw an exception.
     *
     * @param message Debug-friendly description (not shown raw to the user).
     */
    data class Error(val message: String) : CalendarState
}

// ─── Intent ───────────────────────────────────────────────────────────────────

/**
 * User actions dispatched to [CalendarViewModel.onIntent].
 */
sealed interface CalendarIntent : Intent {

    /** User tapped a reminder row. */
    data class OnReminderClick(val info: TodayReminderInfo) : CalendarIntent

    /** User tapped "Create Checklist" from the empty state. */
    data object OnCreateChecklistClick : CalendarIntent

    /** User tapped "Retry" after an error. */
    data object OnRetry : CalendarIntent
}

// ─── SideEffect ───────────────────────────────────────────────────────────────

/**
 * Reserved sealed interface for future one-shot side effects. The current
 * CalendarViewModel routes navigation directly via [AppNavigator] and emits
 * no side effects through the AppViewModel channel, so the type parameter is
 * [Nothing] in the VM declaration. Kept here for symmetry with other screens.
 */
sealed interface CalendarSideEffect : SideEffect
