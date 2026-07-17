package com.antonchuraev.homesearchchecklist.feature.home.presentation.today

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.Intent
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private const val TAG = "TodayViewModel"

/**
 * ViewModel for the Today screen.
 *
 * Observes all reminders (checklist-level + per-item) scheduled within today's
 * date window [startOfDayMs, endOfDayMs] and maps them to [TodayScreenState].
 *
 * Failure contract: [buildRemindersFlow] terminates the upstream on any repository
 * exception, so [catch] is mandatory — without it the exception cancels the
 * [defaultStateIn] sharing scope and the StateFlow is stuck on [TodayScreenState.Loading]
 * forever (infinite spinner, no log, no user-visible signal). The catch turns any
 * failure into a loud, logged, retryable [TodayScreenState.Error].
 *
 * Retry pattern: a [_retryTrigger] MutableStateFlow drives flatMapLatest on the inner
 * observe flow. Incrementing it cancels the current subscription and re-subscribes,
 * restarting from scratch without recreating the ViewModel.
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
    private val logger: AppLogger,
) : AppViewModel<TodayScreenState, TodayIntent, Nothing>() {

    /** Stable snapshot of "now" at VM creation (epoch millis). */
    private val nowMs: Long = currentTimeMillis()

    /** Start-of-today (midnight) and end-of-today (23:59:59.999) in epoch millis. */
    private val todayRange: Pair<Long, Long> = computeTodayRange(nowMs)

    /** Incrementing this cancels the current subscription and re-fetches via flatMapLatest. */
    private val _retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val screenState: StateFlow<TodayScreenState> = _retryTrigger
        .flatMapLatest { attempt ->
            // Emit Loading on retries only. screenState is a StateFlow, which conflates equal
            // values, and Error is a data object equal to itself: without a distinct value in
            // between, a retry that fails again re-emits Error == Error, nothing recomposes, and
            // the user sees a frozen screen after tapping Try Again. Gating on attempt > 0 keeps
            // the first subscription free of a spurious spinner — WhileSubscribed(5000) re-runs
            // this flow whenever the screen is re-entered, and that should show the cached state.
            buildRemindersFlow().onStart { if (attempt > 0) emit(TodayScreenState.Loading) }
        }
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
            TodayIntent.OnRefresh -> _retryTrigger.update { it + 1 }
        }
    }

    // ─── Flow assembly ────────────────────────────────────────────────────────

    /**
     * Builds today's reminders flow.
     *
     * Called once per [_retryTrigger] emission. [catch] translates repository
     * exceptions into a logged [TodayScreenState.Error] — never a silent stall.
     */
    private fun buildRemindersFlow() = repository
        .observeRemindersInRange(todayRange.first, todayRange.second)
        .map { infos -> mapToState(infos) }
        .catch { e ->
            logger.error(TAG, "today_range_fetch_failed", e)
            emit(TodayScreenState.Error)
        }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private fun mapToState(infos: List<TodayReminderInfo>): TodayScreenState {
        if (infos.isEmpty()) return TodayScreenState.Empty

        // Build display items, carrying reminderAt and priority for sorting alongside.
        // TodayReminderItem (defined by mobile-design) does not expose these fields,
        // so we use a Triple<TodayReminderItem, reminderAt, priority> and strip the
        // sort keys after ordering each bucket.
        // Sort contract (per spec):
        //   1. Within group: priority DESC (starred first).
        //   2. Then: reminderAt ASC (earliest first within the same priority tier).
        val pastDueTriples  = mutableListOf<Triple<TodayReminderItem, Long, Int>>()
        val todayFutureTriples = mutableListOf<Triple<TodayReminderItem, Long, Int>>()

        for (info in infos) {
            val item = info.toDisplayItem()
            val triple = Triple(item, info.reminderAt, info.priority)
            if (item.isPastDue) pastDueTriples.add(triple) else todayFutureTriples.add(triple)
        }

        val comparator = compareByDescending<Triple<TodayReminderItem, Long, Int>> { it.third }
            .thenBy { it.second }

        pastDueTriples.sortWith(comparator)
        todayFutureTriples.sortWith(comparator)

        return TodayScreenState.Success(
            dateLabel = buildDateLabel(nowMs),
            pastDue = pastDueTriples.map { it.first },
            today = todayFutureTriples.map { it.first },
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
                day = now.day,
                hour = 0,
                minute = 0,
                second = 0,
                nanosecond = 0,
            )
            val endOfDay = LocalDateTime(
                year = now.year,
                month = now.month,
                day = now.day,
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
            return "$dayName, $monthName ${dt.day}"
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
    /**
     * Explicit refresh/retry request. Re-subscribes the reminders flow from scratch —
     * the recovery path out of [TodayScreenState.Error].
     */
    data object OnRefresh : TodayIntent
}

