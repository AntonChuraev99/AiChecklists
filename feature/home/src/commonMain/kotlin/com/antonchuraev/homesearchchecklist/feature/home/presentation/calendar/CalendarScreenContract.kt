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
 * - [Content] — agenda items available, may or may not have premium.
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
     * @param isPremium True if the user holds an active premium entitlement.
     *   Used to show/hide the calendar-grid upgrade teaser chip.
     * @param isChipDismissed True after the user taps "Dismiss" on the
     *   upgrade teaser. Persists only for the VM lifetime (not DataStore).
     */
    data class Content(
        val agenda: List<AgendaItem>,
        val isPremium: Boolean,
        val isChipDismissed: Boolean = false,
    ) : CalendarState

    /**
     * No reminders in the [-7d, +30d] range.
     *
     * @param isPremium Forwarded so the UI can still show the teaser.
     */
    data class Empty(
        val isPremium: Boolean,
    ) : CalendarState

    /**
     * Repository threw an exception.
     *
     * @param message Debug-friendly description (not shown raw to the user).
     */
    data class Error(
        val message: String,
    ) : CalendarState
}

// ─── Intent ───────────────────────────────────────────────────────────────────

/**
 * User actions dispatched to [CalendarViewModel.onIntent].
 */
sealed interface CalendarIntent : Intent {

    /** User tapped a reminder row. */
    data class OnReminderClick(val info: TodayReminderInfo) : CalendarIntent

    /** User tapped the "Upgrade to Grid" CTA on the premium teaser. */
    data object OnUpgradeToGridClick : CalendarIntent

    /** User dismissed the premium teaser chip. */
    data object OnDismissTeaser : CalendarIntent

    /** User tapped "Create Checklist" from the empty state. */
    data object OnCreateChecklistClick : CalendarIntent

    /** User tapped "Retry" after an error. */
    data object OnRetry : CalendarIntent
}

// ─── SideEffect ───────────────────────────────────────────────────────────────

/**
 * One-shot side effects emitted by [CalendarViewModel].
 *
 * NOTE: This project routes navigation via [AppNavigator] directly from the VM,
 * so this sealed interface is declared for completeness / future use but the
 * current CalendarViewModel implementation uses Nothing (no side effects emitted
 * through the AppViewModel channel — navigation happens via AppNavigator).
 */
sealed interface CalendarSideEffect : SideEffect {
    data class NavigateToChecklist(val id: Long) : CalendarSideEffect
    data class NavigateToFill(val id: Long) : CalendarSideEffect
    data class NavigateToPaywall(val source: String) : CalendarSideEffect
    data object NavigateToCreateChecklist : CalendarSideEffect
}
