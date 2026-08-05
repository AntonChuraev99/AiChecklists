package com.antonchuraev.homesearchchecklist.feature.home.presentation.today

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_checklist_name
import aichecklists.core.designsystem.generated.resources.inbox_task_add_failed
import aichecklists.core.designsystem.generated.resources.today_task_captured_action
import aichecklists.core.designsystem.generated.resources.today_task_captured_message
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.EnsureInboxUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private const val TAG = "TodayViewModel"

/**
 * Wire value of [AnalyticsParams.SOURCE] on `inbox_quick_added` for captures made from the Calendar
 * tab, alongside the Inbox pager's own "inbox" / "project".
 */
private const val SOURCE_CALENDAR = "calendar"

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
    private val ensureInbox: EnsureInboxUseCase,
    private val appNavigator: AppNavigator,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger,
) : AppViewModel<TodayScreenState, TodayIntent, TodaySideEffect>() {

    /** Stable snapshot of "now" at VM creation (epoch millis). */
    private val nowMs: Long = currentTimeMillis()

    private val _quickAddText = MutableStateFlow("")
    val quickAddText: StateFlow<String> = _quickAddText

    private val _sideEffect = MutableSharedFlow<TodaySideEffect>(extraBufferCapacity = 16)
    val sideEffect: Flow<TodaySideEffect> = _sideEffect.asSharedFlow()

    /**
     * Id of the checklist the last capture landed in, so the snackbar's "Open" can reach it.
     *
     * Held here rather than carried in the side effect because the side effect crosses a snackbar,
     * and the user may tap Open several seconds later — by which point a value captured in the UI
     * lambda would be from whichever capture happened to be the last one recomposed.
     */
    private var lastCapturedChecklistId: Long? = null

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
            is TodayIntent.OnQuickAddTextChanged -> _quickAddText.value = intent.text
            TodayIntent.OnQuickAddSubmit -> captureTask()
            TodayIntent.OnOpenCapturedChecklist -> {
                val id = lastCapturedChecklistId
                if (id == null) {
                    logger.warning(TAG, "open-captured skipped: no capture recorded")
                } else {
                    appNavigator.navigateToChecklistDetail(id)
                }
            }
        }
    }

    /**
     * Captures a task into the system Inbox from the Today screen.
     *
     * ## Why the Inbox and not "today"
     * This screen renders REMINDERS inside today's window, so a task without one cannot appear in it.
     * Giving the capture an automatic reminder would make it visible — and would also schedule a
     * notification the user never asked for, which is a heavier side effect than a quick capture
     * should have. The task therefore goes to the Inbox and the snackbar says where it went, with an
     * Open action: nothing is hidden, and no alarm is created behind the user's back.
     *
     * Mirrors `InboxViewModel.addTask`'s write shape (template item first so the fill row carries its
     * stable id from birth); it cannot call it directly because that one targets the pager's visible
     * page, which does not exist here.
     */
    private fun captureTask() {
        val text = _quickAddText.value.trim()
        if (text.isEmpty()) {
            // Defensive: the Add affordance is disabled on blank input. Logged rather than a bare
            // return — an invisible drop reads as a freeze.
            logger.warning(TAG, "today quick-add skipped: blank text")
            return
        }

        viewModelScope.launch {
            runCatching {
                // The Inbox row may legitimately not exist yet: a user can reach the Calendar tab
                // before ever opening the Inbox tab, and EnsureInboxUseCase is invoked from there.
                val inboxName = getString(Res.string.inbox_checklist_name)
                val inboxId = ensureInbox(inboxName) ?: error("Inbox unavailable")
                val checklist = repository.getChecklistById(inboxId)
                    ?: error("Checklist $inboxId not found")
                val fill = repository.getDefaultFillByChecklistId(inboxId).first()
                    ?: error("No default fill for checklist $inboxId")

                val templateItem = ChecklistItem(text = text)
                val fillItem = ChecklistFillItem(
                    text = text,
                    checked = false,
                    note = null,
                    templateItemId = templateItem.id,
                )
                repository.updateFill(fill.copy(items = fill.items + fillItem))
                repository.updateChecklistTemplate(checklist.copy(items = checklist.items + templateItem))
                inboxId
            }.onSuccess { inboxId ->
                _quickAddText.value = ""
                lastCapturedChecklistId = inboxId
                // Same event and param shape as `InboxViewModel.addTask`, with this tab's own SOURCE
                // value: the create-FAB funnel counts `inbox_quick_added`, so a capture made here and
                // not emitted is a capture the funnel never sees. A third wire value rather than
                // reusing "inbox" — the task does land in the Inbox, but WHERE the user captured it
                // is the thing the two v2 capture surfaces have to stay separable on.
                analytics.event(
                    AnalyticsEvents.Inbox.QUICK_ADDED,
                    mapOf(AnalyticsParams.SOURCE to SOURCE_CALENDAR),
                )
                emitSideEffect(
                    TodaySideEffect.ShowCaptureMessage(
                        text = getString(Res.string.today_task_captured_message),
                        actionLabel = getString(Res.string.today_task_captured_action),
                    )
                )
            }.onFailure { e ->
                if (e is CancellationException) throw e
                logger.error(TAG, "today quick-add failed: ${e.message}", e)
                emitSideEffect(
                    TodaySideEffect.ShowCaptureMessage(
                        text = getString(Res.string.inbox_task_add_failed),
                        actionLabel = null,
                    )
                )
            }
        }
    }

    /**
     * Hands [effect] to the screen, WAITING for a collector instead of dropping it.
     *
     * `tryEmit` on a replay-0 [MutableSharedFlow] returns **true** and silently discards the value
     * whenever `subscriptionCount == 0` — no exception, no log, nothing to grep. That made it the
     * only link in the capture chain that can fail without leaving a trace, and the message it
     * carries is the ONLY feedback a capture has: the task goes to the Inbox and cannot appear on
     * this screen (it has no reminder), so a dropped message reads as "my tap did nothing".
     *
     * The single subscriber is a `LaunchedEffect` inside `CalendarRoute`. It is registered
     * asynchronously (a LaunchedEffect coroutine is dispatched, it does not run inline with
     * composition) and torn down whenever that route leaves composition — a tab switch, a pushed
     * detail screen, a window-size-class change in the v2 shell all do it. "Somebody is listening"
     * is therefore not a guarantee this ViewModel can make, and it must not silently assume it.
     * Same fix, same reason, as `InboxViewModel.emitMessage`.
     *
     * `internal` rather than private for the regression test — the drop is invisible from outside,
     * so the guarantee has to be asserted on this function directly.
     */
    internal suspend fun emitSideEffect(effect: TodaySideEffect) {
        _sideEffect.subscriptionCount.first { it > 0 }
        _sideEffect.emit(effect)
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

    data class OnQuickAddTextChanged(val text: String) : TodayIntent

    /** Captures the typed text into the system Inbox. See `TodayViewModel.captureTask`. */
    data object OnQuickAddSubmit : TodayIntent

    /** Snackbar action after a capture — opens the checklist the task landed in. */
    data object OnOpenCapturedChecklist : TodayIntent
    /**
     * Explicit refresh/retry request. Re-subscribes the reminders flow from scratch —
     * the recovery path out of [TodayScreenState.Error].
     */
    data object OnRefresh : TodayIntent
}

/**
 * [text] and [actionLabel] arrive already resolved from Compose Resources — a resource KEY here
 * would push string resolution into the collector and make it easy to leak a literal. Same contract
 * as `InboxSideEffect.ShowMessage`.
 *
 * [actionLabel] is null for failures: "Open" would point at a checklist nothing was written to.
 */
sealed interface TodaySideEffect : SideEffect {
    data class ShowCaptureMessage(val text: String, val actionLabel: String?) : TodaySideEffect
}

