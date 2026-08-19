package com.antonchuraev.homesearchchecklist.feature.home.presentation.today

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_checklist_name
import aichecklists.core.designsystem.generated.resources.inbox_reminder_time_in_past
import aichecklists.core.designsystem.generated.resources.inbox_task_add_failed
import aichecklists.core.designsystem.generated.resources.today_task_captured_action
import aichecklists.core.designsystem.generated.resources.today_task_captured_message
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntryDestination
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
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
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.EnsureInboxUseCase
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiItemCreateAction
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.DraftDueController
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.DraftDueIntent
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.DraftDueUiState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.ItemCreateReminderPreset
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.TaskDraft
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.dateInputMethod
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.dayScreenDraft
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.dueDateOffsetDays
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.resolveDueOutcome
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.withImportantToggled
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.withPreset
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
import kotlin.time.Clock
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
 * Wire value of [AnalyticsParams.SOURCE] on `nav_create_fab_tapped` since the floating "+" was
 * replaced by the inline row under the pager.
 *
 * Deliberately the SAME literal `InboxViewModel` reports: both tabs lost the same FAB and gained the
 * same affordance, so one value keeps them summable. The two copies are a smell only if a third
 * surface appears — at that point the constant belongs in `core:common:api`, next to the event name.
 */
private const val SOURCE_INLINE_ROW = "inline_row"

/**
 * Paywall attribution for the locked banner behind the capture dock's Repeat control on this tab.
 *
 * Its own value rather than the Inbox tab's: the two docks are one component but two surfaces, and
 * the reason `paywall_opened` carries a source at all is to keep them separable in the funnel.
 */
private const val PAYWALL_SOURCE_DOCK_REPEAT = "calendar_capture_repeat_limit"

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
    // Captures made here now carry a reminder by default (see [_draft]); persisting reminderAt
    // without arming the alarm would render a bell that never rings.
    private val reminderScheduler: ChecklistReminderScheduler,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger,
    /**
     * Premium state, for the ONE gate the capture dock can reach: the recurring-reminder cap behind
     * the planner's Repeat control. Nothing else in the rail touches a paywall — a due date is free.
     *
     * Required, exactly like [InboxViewModel]'s: the two capture hosts run the SAME gate, and a
     * nullable-with-default here made an eighth parameter compile silently while Koin fed the gate a
     * null — i.e. a paying user shown the locked upgrade banner behind Repeat, forever, with nothing
     * to explain it. A missing binding must fail at graph construction, not at the user's finger.
     */
    private val getUserLimitsUseCase: GetUserLimitsUseCase,
) : AppViewModel<TodayScreenState, TodayIntent, TodaySideEffect>() {

    /** Stable snapshot of "now" at VM creation (epoch millis). */
    private val nowMs: Long = currentTimeMillis()

    /**
     * The task being composed, seeded with THIS screen's default reminder chip.
     *
     * This screen draws the day's reminders, so a task captured here without one lands in the Inbox
     * and disappears from the very screen that produced it. The chip is pre-selected rather than the
     * time silently stamped: it is visible, and one tap removes it.
     */
    private val _draft = MutableStateFlow(dayScreenDraft())
    val draft: StateFlow<TaskDraft> = _draft

    /**
     * Latest premium snapshot, kept fresh so the repeat gate can be evaluated synchronously.
     *
     * Collected rather than read on demand for the same reason the Inbox tab keeps one: a suspend
     * read at gate time opens the sheet unlocked for a frame and then locks it under the user's
     * finger.
     */
    private val _userLimits = MutableStateFlow<UserLimits?>(null)

    /**
     * The capture dock's due rail — the SAME object the Inbox tab drives, so the two docks cannot
     * answer "when" differently.
     *
     * Declared after [_draft] because it captures it: Kotlin initialises properties in declaration
     * order, and a delegate built above would capture a null.
     */
    private val dueController = DraftDueController(
        scope = viewModelScope,
        logger = logger,
        tag = TAG,
        draft = _draft,
        limits = { _userLimits.value },
        countActiveReminders = { repository.countActiveReminders() },
        onUpgradeClick = { appNavigator.navigateToPaywall(source = PAYWALL_SOURCE_DOCK_REPEAT) },
        // This screen has no snackbar of its own — every message it produces rides the capture side
        // effect, which is also what the dock's own confirmations use.
        onMessage = { resource ->
            viewModelScope.launch {
                emitSideEffect(
                    TodaySideEffect.ShowCaptureMessage(text = getString(resource), actionLabel = null)
                )
            }
        },
    )

    /** What the capture dock's due rail currently has open. Collected by `CalendarRoute`. */
    val due: StateFlow<DraftDueUiState> = dueController.state

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

    init {
        observePremium()
    }

    /**
     * Keeps [_userLimits] fresh for the one quota-gated control the capture dock offers.
     *
     * A read failure is LOGGED and leaves the flag at its last value rather than silently pinning the
     * user to "free": a wrongly locked Repeat sheet is an upsell shown to someone who already paid,
     * and with no line in the log there is nothing to explain it afterwards.
     */
    private fun observePremium() {
        viewModelScope.launch {
            getUserLimitsUseCase()
                .catch { e -> logger.error(TAG, "user limits stream failed: ${e.message}", e) }
                .collect { limits -> _userLimits.value = limits }
        }
    }

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
            is TodayIntent.OnQuickAddTextChanged ->
                _draft.value = _draft.value.copy(text = intent.text)
            TodayIntent.OnQuickAddSubmit -> captureTask()

            // Analytics ONLY — raising the dock is the host's job (see CalendarRoute). Same EVENT
            // name the shell's "+" FAB used, because it counts "the user reached for the manual
            // create affordance"; renaming it when that affordance changed shape would split one
            // series into two that cannot be summed. Only SOURCE moves, "fab" → "inline_row".
            TodayIntent.OnAddTaskRowClick -> analytics.event(
                AnalyticsEvents.Nav.CREATE_FAB_TAPPED,
                mapOf(AnalyticsParams.SOURCE to SOURCE_INLINE_ROW),
            )

            is TodayIntent.OnCreateChipAction -> applyCreateChip(intent.action)
            is TodayIntent.OnDue -> dueController.onIntent(intent.due)

            // Report first, navigate second — the tap is the fact being measured, and it must be
            // true in the data even if the destination never opens.
            is TodayIntent.OnAiSourceTapped -> {
                analytics.event(
                    AnalyticsEvents.AiEntry.TAPPED,
                    mapOf(
                        AnalyticsParams.DESTINATION to AiEntryDestination.ANALYZE.wire,
                        AnalyticsParams.SOURCE to AiEntrySource.CAPTURE_DOCK_CALENDAR.wire,
                        AnalyticsParams.INPUT_TYPE to intent.kind.wire,
                    ),
                )
                appNavigator.navigateToAnalyzeWithInput(
                    intent.kind,
                    AiEntrySource.CAPTURE_DOCK_CALENDAR,
                )
            }
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
     * Applies a capture-dock chip to the draft.
     *
     * Since the due rail shipped, the only chip the dock still routes through here is Important —
     * the date offers go through `DraftDueIntent`, and "Pick time" / "Repeat" are rows of the
     * planner panel, whose sheets this tab now hosts itself. The other branches are kept, with a
     * log, never a silent `else`, because [GistiItemCreateAction] is shared with the detail screen
     * and an action arriving here unhandled must not vanish quietly.
     */
    private fun applyCreateChip(action: GistiItemCreateAction) {
        when (action) {
            GistiItemCreateAction.REMIND_1H ->
                _draft.value = _draft.value.withPreset(ItemCreateReminderPreset.ONE_HOUR)
            GistiItemCreateAction.REMIND_TOMORROW_MORNING ->
                _draft.value = _draft.value.withPreset(ItemCreateReminderPreset.TOMORROW_MORNING)
            GistiItemCreateAction.REMIND_TONIGHT ->
                _draft.value = _draft.value.withPreset(ItemCreateReminderPreset.TONIGHT)
            GistiItemCreateAction.IMPORTANT ->
                _draft.value = _draft.value.withImportantToggled()
            GistiItemCreateAction.REMIND_PICK, GistiItemCreateAction.REPEAT ->
                logger.warning(TAG, "capture chip $action has no host on the Today tab")
        }
    }

    /**
     * Captures a task into the system Inbox from the Today screen.
     *
     * ## Why the Inbox, and why it now carries a reminder
     * This screen renders REMINDERS inside today's window, so a task without one cannot appear in it.
     * The capture therefore lands in the Inbox — the one list that is always there — and the draft
     * arrives with the day's reminder chip already selected, which is what puts the task back on the
     * screen that created it.
     *
     * The chip is the whole point of the distinction: an earlier version of this screen refused to
     * attach a reminder because scheduling a notification nobody asked for is a heavy side effect for
     * a quick capture. A pre-selected, visible, one-tap-removable chip is not that — the user sees the
     * alarm before it exists and can decline it. Nothing is scheduled behind their back either way.
     *
     * Mirrors `InboxViewModel.addTask`'s write shape (template item first so the fill row carries its
     * stable id from birth); it cannot call it directly because that one targets the pager's visible
     * page, which does not exist here.
     */
    private fun captureTask() {
        val draft = _draft.value
        val text = draft.text.trim()
        if (text.isEmpty()) {
            // Defensive: the Add affordance is disabled on blank input. Logged rather than a bare
            // return — an invisible drop reads as a freeze.
            logger.warning(TAG, "today quick-add skipped: blank text")
            return
        }

        // Resolved ONCE, here, and then both written and reported from the same value. Resolved at
        // SEND rather than at the tap, because a dock left open across 18:00 would otherwise file a
        // "Tonight" that has already passed.
        val timeZone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val dueOutcome = draft.resolveDueOutcome(now, timeZone)
        // Same contract as the Inbox tab: keep the capture, but never drop the date silently.
        if (dueOutcome.pickedTimeExpired) {
            logger.warning(TAG, "picked due time expired before send — task created without a date")
            viewModelScope.launch {
                emitSideEffect(
                    TodaySideEffect.ShowCaptureMessage(
                        text = getString(Res.string.inbox_reminder_time_in_past),
                        actionLabel = null,
                    ),
                )
            }
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

                val priority = if (draft.important) 1 else 0
                // Priority on BOTH halves of the pair — the template feeds the edit screen, the fill
                // feeds every list (rule `checklist-domain`).
                val templateItem = ChecklistItem(text = text, priority = priority)
                val fillItem = ChecklistFillItem(
                    text = text,
                    checked = false,
                    note = null,
                    priority = priority,
                    templateItemId = templateItem.id,
                ).let { item ->
                    val rule = dueOutcome.repeatRule
                    val minutes = dueOutcome.repeatTimeOfDayMinutes
                    val firstAt = dueOutcome.repeatFirstTriggerAt
                    if (rule != null && minutes != null && firstAt != null) {
                        item.withRepeatRule(rule, minutes, firstAt)
                    } else {
                        item
                    }
                }.let { item -> dueOutcome.reminderAt?.let(item::withReminderAt) ?: item }
                repository.updateFill(fill.copy(items = fill.items + fillItem))
                repository.updateChecklistTemplate(checklist.copy(items = checklist.items + templateItem))
                // Both kinds, because the dock's planner can now stage either. Persisting without
                // arming the alarm is a bell that never rings.
                dueOutcome.reminderAt?.let { at ->
                    reminderScheduler.scheduleItemReminder(inboxId, fill.id, fillItem.id, at)
                }
                dueOutcome.repeatFirstTriggerAt?.let { at ->
                    reminderScheduler.scheduleItemRepeat(inboxId, fill.id, fillItem.id, at)
                }
                inboxId
            }.onSuccess { inboxId ->
                // Reset to a FRESH day draft, not an empty one: the next capture on this screen wants
                // the same default chip, recomputed against the clock as it stands now.
                _draft.value = dayScreenDraft()
                // ...and the rail with it: the dock stays open for the next capture, so a planner
                // left expanded would sit over a fresh draft with the AI source row still hidden.
                dueController.reset()
                lastCapturedChecklistId = inboxId
                // Same event and param shape as `InboxViewModel.addTask`, with this tab's own SOURCE
                // value: the create-FAB funnel counts `inbox_quick_added`, so a capture made here and
                // not emitted is a capture the funnel never sees. A third wire value rather than
                // reusing "inbox" — the task does land in the Inbox, but WHERE the user captured it
                // is the thing the two v2 capture surfaces have to stay separable on.
                analytics.event(
                    AnalyticsEvents.Inbox.QUICK_ADDED,
                    buildMap {
                        put(AnalyticsParams.SOURCE, SOURCE_CALENDAR)
                        // Read off the value that was WRITTEN. Recomputing it here could disagree
                        // with the write across a minute boundary and report a date the task does
                        // not carry.
                        val dueAt = dueOutcome.dueAtMillis
                        put(AnalyticsParams.HAS_DUE_DATE, (dueAt != null).toString())
                        put(AnalyticsParams.DATE_INPUT_METHOD, draft.dateInputMethod(dueAt).wire)
                        // ABSENT rather than a sentinel when there is no date — see the param's KDoc.
                        dueAt?.let {
                            put(
                                AnalyticsParams.DUE_DATE_OFFSET_DAYS,
                                dueDateOffsetDays(it, now.toEpochMilliseconds(), timeZone).toString(),
                            )
                        }
                    },
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

    /** A capture-dock chip was tapped (reminder preset or Important). */
    data class OnCreateChipAction(val action: GistiItemCreateAction) : TodayIntent

    /**
     * Anything the capture dock's due rail, its planner grid or the v1 reminder sheet behind them
     * reported.
     *
     * One case wrapping the shared vocabulary, exactly as `InboxIntent.OnDue` does: both tabs mount
     * the same dock, and the rules live once, in [DraftDueController].
     */
    data class OnDue(val due: DraftDueIntent) : TodayIntent

    /**
     * One of the AI source pills in the capture dock was tapped.
     *
     * Unlike the Inbox's twin intent this one carries no source: the Calendar tab hosts the row in
     * exactly one place, so naming it at the call site would be a value that can only ever be
     * `CAPTURE_DOCK_CALENDAR` and could silently be passed wrong.
     */
    data class OnAiSourceTapped(val kind: AnalyzeInputKind) : TodayIntent

    /** Captures the typed text into the system Inbox. See `TodayViewModel.captureTask`. */
    data object OnQuickAddSubmit : TodayIntent

    /**
     * The inline "add task" row under the Calendar tab's pager was tapped.
     *
     * ANALYTICS ONLY here — the dock's open flag is host state, so `CalendarRoute` sends this intent
     * AND calls the host back. The emit moved down from the v2 shell's "+" FAB handler, which is
     * being deleted with the FAB: an event that lives on a deleted button is an event that silently
     * stops, and this one belongs to a series that has to stay comparable across the change.
     */
    data object OnAddTaskRowClick : TodayIntent

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

