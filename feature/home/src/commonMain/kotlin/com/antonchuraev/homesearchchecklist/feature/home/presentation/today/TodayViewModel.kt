package com.antonchuraev.homesearchchecklist.feature.home.presentation.today

import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * ViewModel for the Today screen.
 *
 * Observes all reminders (checklist-level + per-item) scheduled within today's
 * date window [startOfDayMs, endOfDayMs] and maps them to [TodayScreenState].
 *
 * Sorting contract:
 *   1. Past-due reminders first (reminderAt < nowMs), ascending by reminderAt.
 *   2. Future reminders for today (reminderAt >= nowMs), ascending by reminderAt.
 *
 * Date window is recalculated once at init and treated as stable for the session.
 * If the device clock crosses midnight, the user would need to reopen the app to
 * refresh — acceptable for a v1 Today view (same pattern as Todoist, TickTick).
 */
class TodayViewModel(
    private val repository: ChecklistRepository,
    private val appNavigator: AppNavigator,
) : AppViewModel<TodayScreenState, TodayIntent, Nothing>() {

    /** Stable snapshot of "now" at VM creation (epoch millis). */
    private val nowMs: Long = currentTimeMillis()

    /** Start-of-today (midnight) and end-of-today (23:59:59.999) in epoch millis. */
    private val todayRange: Pair<Long, Long> = computeTodayRange(nowMs)

    override val screenState: StateFlow<TodayScreenState> = repository
        .observeRemindersInRange(todayRange.first, todayRange.second)
        .map { infos -> mapToState(infos) }
        .defaultStateIn(TodayScreenState.Loading)

    override fun onIntent(intent: TodayIntent) {
        when (intent) {
            is TodayIntent.OnReminderClick -> {
                val fillId = intent.fillId
                if (fillId != null) {
                    appNavigator.navigateToFillDetail(fillId)
                } else {
                    appNavigator.navigateToChecklistDetail(intent.checklistId)
                }
            }
            TodayIntent.OnCreateChecklistClick -> appNavigator.navigateToTemplatesScreen()
            TodayIntent.OnRefresh -> {
                // Flow-based state reacts to DB changes automatically.
                // This intent is a no-op in v1; reserved for pull-to-refresh.
            }
        }
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private fun mapToState(infos: List<TodayReminderInfo>): TodayScreenState {
        if (infos.isEmpty()) return TodayScreenState.Empty

        // Build display items, keeping reminderAt for sorting via a parallel list.
        // We cannot add a field to TodayReminderItem (defined by mobile-design), so we
        // carry reminderAt alongside in a Pair and strip it after sorting.
        val pastDuePairs = mutableListOf<Pair<TodayReminderItem, Long>>()
        val todayFuturePairs = mutableListOf<Pair<TodayReminderItem, Long>>()

        for (info in infos) {
            val item = info.toDisplayItem()
            val pair = item to info.reminderAt
            if (item.isPastDue) pastDuePairs.add(pair) else todayFuturePairs.add(pair)
        }

        // Sort each bucket ascending by reminderAt (earliest first)
        pastDuePairs.sortBy { it.second }
        todayFuturePairs.sortBy { it.second }

        return TodayScreenState.Success(
            dateLabel = buildDateLabel(nowMs),
            pastDue = pastDuePairs.map { it.first },
            today = todayFuturePairs.map { it.first },
        )
    }

    private fun TodayReminderInfo.toDisplayItem(): TodayReminderItem {
        return when (this) {
            is TodayReminderInfo.ChecklistLevel -> TodayReminderItem(
                id = "cl_${checklistId}_${reminderAt}",
                itemName = null,
                checklistName = checklistName,
                checklistId = checklistId,
                fillId = null,
                timeLabel = formatTime(reminderAt),
                isPastDue = reminderAt < nowMs,
                isRecurring = isRecurring,
            )
            is TodayReminderInfo.ItemLevel -> TodayReminderItem(
                id = "il_${fillId}_${itemId}_${reminderAt}",
                itemName = itemText,
                checklistName = checklistName,
                checklistId = checklistId,
                fillId = fillId,
                timeLabel = formatTime(reminderAt),
                isPastDue = reminderAt < nowMs,
                isRecurring = isRecurring,
            )
        }
    }

    // ─── Date/Time helpers ───────────────────────────────────────────────────

    companion object {

        /**
         * Computes [startOfDayMs, endOfDayMs] for the current local date.
         *
         * Uses kotlinx-datetime for KMP timezone awareness. Avoids java.util.Calendar
         * and java.time (not available in commonMain).
         */
        internal fun computeTodayRange(nowMs: Long): Pair<Long, Long> {
            val tz = TimeZone.currentSystemDefault()
            val now = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz)

            val startOfDay = LocalDateTime(
                year = now.year,
                month = now.month,
                dayOfMonth = now.dayOfMonth,
                hour = 0,
                minute = 0,
                second = 0,
                nanosecond = 0,
            )
            val endOfDay = LocalDateTime(
                year = now.year,
                month = now.month,
                dayOfMonth = now.dayOfMonth,
                hour = 23,
                minute = 59,
                second = 59,
                nanosecond = 999_999_999,
            )

            val startMs = startOfDay.toInstant(tz).toEpochMilliseconds()
            val endMs = endOfDay.toInstant(tz).toEpochMilliseconds()
            return startMs to endMs
        }

        /**
         * Formats "Tuesday, May 6" from epoch millis using the current system timezone.
         * All formatting done with kotlinx-datetime (KMP-safe, no java.time).
         */
        internal fun buildDateLabel(nowMs: Long): String {
            val tz = TimeZone.currentSystemDefault()
            val dt = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz)
            val dayName = dt.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            val monthName = dt.month.name.lowercase().replaceFirstChar { it.uppercase() }
            return "$dayName, $monthName ${dt.dayOfMonth}"
        }

        /**
         * Formats epoch millis as "HH:mm" in local timezone (24-hour, KMP-safe).
         */
        internal fun formatTime(epochMs: Long): String {
            val tz = TimeZone.currentSystemDefault()
            val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
            val h = dt.hour.toString().padStart(2, '0')
            val m = dt.minute.toString().padStart(2, '0')
            return "$h:$m"
        }
    }
}

// ─── Intent sealed interface ─────────────────────────────────────────────────

/**
 * Intents for TodayScreen actions.
 */
sealed interface TodayIntent : Intent {
    /** User tapped a reminder row. Navigates to FillDetail (fillId != null) or ChecklistDetail. */
    data class OnReminderClick(val checklistId: Long, val fillId: Long?) : TodayIntent
    /** User tapped "Create Checklist" in the NoChecklists empty state. */
    data object OnCreateChecklistClick : TodayIntent
    /** Pull-to-refresh or explicit refresh request (no-op in v1, flow reacts automatically). */
    data object OnRefresh : TodayIntent
}

