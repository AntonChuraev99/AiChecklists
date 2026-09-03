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

    /** User tapped "Retry" after an error. */
    data object OnRetry : CalendarIntent
}

// ─── Add-task hosting ─────────────────────────────────────────────────────────

/**
 * Does the Calendar page host the add-task action INSIDE its own placeholder for this state?
 *
 * The twin of
 * [hostsAddTaskAction][com.antonchuraev.homesearchchecklist.feature.home.presentation.today.hostsAddTaskAction]
 * on the Today side, and it exists for the same reason: TWO readers on two sides of the pager have to
 * agree, and the way they cannot drift is by both deriving from one predicate.
 *  - `CalendarTabBody` builds its placeholder's action slot from it;
 *  - `CalendarScreen` withholds its PINNED add-task row while the settled page hosts the action.
 *
 * Agree wrongly one way and the screen carries two controls both named "Add task" — ambiguous to a
 * screen reader and to every UI test matching that label. Agree wrongly the other way and an empty
 * Calendar page has no way into the capture dock, which on Compact is this tab's only route to one.
 *
 * ## Why only [CalendarState.Empty]
 * - `Empty` — nothing to act on, the placeholder fills the page and is where the eye is. This is the
 *   state the owner's request is about ("кнопка создать чеклист а не пункт чеклиста").
 * - `Error` already carries its own CTA ("Retry"), and stacking a second button under it would make
 *   the placeholder a two-button dialog; the pinned row is still on screen there, so nothing is lost.
 * - `Loading` and `Content` render no placeholder at all — there is no slot to put anything in.
 */
internal fun CalendarState.hostsAddTaskAction(): Boolean = when (this) {
    CalendarState.Empty -> true
    CalendarState.Loading,
    is CalendarState.Content,
    is CalendarState.Error,
    -> false
}

// ─── SideEffect ───────────────────────────────────────────────────────────────

/**
 * Reserved sealed interface for future one-shot side effects. The current
 * CalendarViewModel routes navigation directly via [AppNavigator] and emits
 * no side effects through the AppViewModel channel, so the type parameter is
 * [Nothing] in the VM declaration. Kept here for symmetry with other screens.
 */
sealed interface CalendarSideEffect : SideEffect
