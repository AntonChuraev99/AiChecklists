package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant as KtInstant

private const val TAG = "CalendarViewModel"

/**
 * ViewModel for the Calendar screen (agenda view).
 *
 * Observes reminders in a [-7d, +30d] window around "now", combines them with
 * the user's premium status, and maps the result to [CalendarState].
 *
 * Retry pattern: a [_retryTrigger] MutableStateFlow drives flatMapLatest on the
 * inner observe flow. Incrementing it cancels the current subscription and
 * re-subscribes — restarting from scratch without recreating the ViewModel.
 *
 * Group-by-date algorithm:
 *   1. Partition reminders into pastDue (reminderAt < startOfToday) and upcoming.
 *   2. Sort each partition by reminderAt ASC.
 *   3. Emit a single "Past due" [AgendaItem.DateHeader] for the past-due group (if any).
 *   4. Fold upcoming into date-keyed sections — emit a [AgendaItem.DateHeader] whenever
 *      the local date changes. Always emit a "Today" header even if no items under it.
 */
class CalendarViewModel(
    private val repository: ChecklistRepository,
    private val appNavigator: AppNavigator,
    private val logger: AppLogger,
    /**
     * Clock provider. Defaults to [currentTimeMillis]; override in tests to pin
     * "now" to a deterministic timestamp (avoids test brittleness from live clocks).
     */
    internal val clock: () -> Long = { currentTimeMillis() },
) : AppViewModel<CalendarState, CalendarIntent, Nothing>() {

    /** Incrementing this triggers a full re-fetch by flatMapLatest. */
    private val _retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val screenState: StateFlow<CalendarState> = _retryTrigger
        .flatMapLatest { buildRemindersFlow() }
        .defaultStateIn(CalendarState.Loading)

    // ─── Intent handling ─────────────────────────────────────────────────────

    override fun onIntent(intent: CalendarIntent) {
        when (intent) {
            is CalendarIntent.OnReminderClick -> handleReminderClick(intent.info)
            CalendarIntent.OnCreateChecklistClick -> appNavigator.navigateToTemplatesScreen()
            CalendarIntent.OnRetry -> _retryTrigger.update { it + 1 }
        }
    }

    private fun handleReminderClick(info: TodayReminderInfo) {
        when (info) {
            is TodayReminderInfo.ItemLevel ->
                appNavigator.navigateToFillDetail(info.fillId)
            is TodayReminderInfo.ChecklistLevel ->
                appNavigator.navigateToChecklistDetail(info.checklistId)
        }
    }

    // ─── Flow assembly ───────────────────────────────────────────────────────

    /**
     * Builds the reminders flow for the configured [-7d, +30d] range.
     *
     * Called once per [_retryTrigger] emission. [catch] translates repository
     * exceptions into [CalendarState.Error] — no silent empty list.
     */
    private fun buildRemindersFlow() = repository.observeRemindersInRange(
        fromMs = rangeStart(clock()),
        toMs = rangeEnd(clock()),
    ).map { reminders -> mapToState(reminders) }
        .catch { e ->
            logger.error(TAG, "calendar_range_fetch_failed", e)
            emit(CalendarState.Error(e.message ?: "Unknown error"))
        }

    // ─── State mapping ───────────────────────────────────────────────────────

    private fun mapToState(reminders: List<TodayReminderInfo>): CalendarState {
        if (reminders.isEmpty()) return CalendarState.Empty

        val nowMs = clock()
        val tz = TimeZone.currentSystemDefault()
        return CalendarState.Content(agenda = buildAgenda(reminders, nowMs, tz))
    }

    /**
     * Builds a flat agenda list from a raw [reminders] list.
     *
     * Algorithm:
     * 1. Partition into pastDue vs upcoming using [startOfTodayMs].
     * 2. Sort each partition by reminderAt ASC.
     * 3. Emit a single "Past due" [AgendaItem.DateHeader] for the past-due group
     *    (sentinel epochDay = Long.MIN_VALUE so it always sorts first).
     * 4. Fold upcoming into date sections. Always include Today even if empty.
     *    Use epoch-day as key (reminderAt / MS_PER_DAY in UTC, adjusted for tz).
     */
    internal fun buildAgenda(
        reminders: List<TodayReminderInfo>,
        nowMs: Long = clock(),
        tz: TimeZone = TimeZone.currentSystemDefault(),
    ): List<AgendaItem> {
        val startOfToday = startOfDayMs(nowMs, tz)
        val todayEpochDay = epochDayFor(startOfToday, tz)

        // Past-due bucket only catches one-shot (non-recurring) past entries:
        // those are reminders the user actually missed. Past recurring entries
        // are history — they fired on that day — and belong under their own
        // date header instead of the urgency-tinted "Past due" group.
        val pastDue = reminders
            .filter { it.reminderAt < startOfToday && !it.isRecurring }
            .sortedBy { it.reminderAt }
        val dated = reminders
            .filter { it.reminderAt >= startOfToday || it.isRecurring }
            .sortedBy { it.reminderAt }

        val result = mutableListOf<AgendaItem>()

        // ── Past due group ────────────────────────────────────────────────────
        if (pastDue.isNotEmpty()) {
            result += AgendaItem.DateHeader(
                epochDay = Long.MIN_VALUE,
                label = PAST_DUE_LABEL,
                isPastDue = true,
            )
            pastDue.forEach { result += AgendaItem.ReminderRow(it) }
        }

        // ── Dated entries: group by local date, always include today ──────────
        // Both past-recurring (history) and future reminders flow through here.
        val byDay = linkedMapOf<Long, MutableList<TodayReminderInfo>>()
        byDay[todayEpochDay] = mutableListOf() // today always present

        for (reminder in dated) {
            val day = epochDayFor(reminder.reminderAt, tz)
            byDay.getOrPut(day) { mutableListOf() } += reminder
        }

        // Emit headers + rows in ascending day order
        for ((day, dayReminders) in byDay.entries.sortedBy { it.key }) {
            result += AgendaItem.DateHeader(
                epochDay = day,
                label = formatDayLabel(day, tz),
                isPastDue = false,
            )
            dayReminders.forEach { result += AgendaItem.ReminderRow(it) }
        }

        return result
    }

    // ─── Date/time helpers ───────────────────────────────────────────────────

    /**
     * Epoch millis for the start of the local day containing [epochMs].
     */
    internal fun startOfDayMs(epochMs: Long, tz: TimeZone): Long {
        val dt = KtInstant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
        return LocalDateTime(dt.year, dt.month, dt.day, 0, 0, 0, 0)
            .toInstant(tz)
            .toEpochMilliseconds()
    }

    /**
     * Returns an epoch-day key (days since 1970-01-01) for any epoch-millis
     * timestamp interpreted in [tz]. Used as a stable, sortable grouping key.
     */
    internal fun epochDayFor(epochMs: Long, tz: TimeZone): Long {
        val dt = KtInstant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
        // Convert back to UTC midnight to get a stable day-count offset
        return LocalDateTime(dt.year, dt.month, dt.day, 0, 0, 0, 0)
            .toInstant(TimeZone.UTC)
            .toEpochMilliseconds() / MS_PER_DAY
    }

    /**
     * Formats a day label like "Tuesday, May 13" from an epoch-day key.
     * All formatting uses kotlinx-datetime (KMP-safe, no java.time).
     */
    internal fun formatDayLabel(epochDay: Long, tz: TimeZone): String {
        val midnightUtc = Instant.fromEpochMilliseconds(epochDay * MS_PER_DAY)
        val dt = midnightUtc.toLocalDateTime(tz)
        val dayName = dt.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val monthName = dt.month.name.lowercase().replaceFirstChar { it.uppercase() }
        return "$dayName, $monthName ${dt.day}"
    }

    /** Range start: 7 days before start of today. */
    private fun rangeStart(nowMs: Long): Long {
        val tz = TimeZone.currentSystemDefault()
        return startOfDayMs(nowMs, tz) - PAST_DAYS_MS
    }

    /** Range end: 30 days after start of today (end of that day). */
    private fun rangeEnd(nowMs: Long): Long {
        val tz = TimeZone.currentSystemDefault()
        val startOfToday = startOfDayMs(nowMs, tz)
        // End of the 30th future day (23:59:59.999)
        return startOfToday + FUTURE_DAYS_MS + DAY_MS - 1L
    }

    companion object {
        internal const val PAST_DUE_LABEL = "Past due"
        private const val MS_PER_DAY = 86_400_000L
        private const val DAY_MS = 86_400_000L
        private const val PAST_DAYS_MS = 7 * DAY_MS
        private const val FUTURE_DAYS_MS = 30 * DAY_MS
    }
}
