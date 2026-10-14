package com.antonchuraev.homesearchchecklist.core.common.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val NeverPending: StateFlow<ReminderTarget?> = MutableStateFlow<ReminderTarget?>(null).asStateFlow()

/**
 * What the one-time reminder opt-in sheet sets its reminder on: the user's first own checklist,
 * or — for a v2 user whose first capture is a task — that task. Whichever comes first wins.
 */
sealed interface ReminderTarget {
    val checklistId: Long

    /** Checklist-level reminder (`setReminder` + `scheduleReminder`). */
    data class Checklist(override val checklistId: Long) : ReminderTarget

    /** Item reminder on the default fill (`withReminderAt` + `scheduleItemReminder`); [text] = sheet preview. */
    data class Item(
        override val checklistId: Long,
        val fillId: Long,
        val itemId: String,
        val text: String,
    ) : ReminderTarget
}

/**
 * Orchestrates the new-user activation funnel WITHOUT coupling the create paths (data / feature
 * ViewModels) to the UI. The bundle is unconditional since 2026-10-14 — the former RC flag
 * `activation_bundle_v1` was deleted together with its OFF arm.
 *
 * Flow of control:
 *  1. A create path persists a checklist the USER asked for and reports it: AI paths via
 *     [onAiChecklistCreated], manual / template paths via [onUserChecklistCreated]. Automatic
 *     creations (system Inbox, sync restore) never report.
 *  2. The implementation decides — using the persisted per-UID "new-user-pending" flag — whether
 *     this is the new user's first checklist. If so it emits the checklist id on
 *     [reminderOptInRequests] (at most once per user). The AI path additionally fires
 *     [AnalyticsEvents.Activation.FIRST_AI_CHECKLIST_CREATED] once and consumes the pending flag.
 *  3. The app shell (App.kt) collects [reminderOptInRequests], shows the one-time reminder
 *     opt-in sheet for that checklist, then reports the result via [reportReminderOptInOutcome].
 *
 * Keeping this in `core/common/api` lets both the create paths and App.kt depend on the same
 * coordinator without a feature ↔ feature edge. The implementation lives in `composeApp`.
 */
interface ActivationCoordinator {

    /**
     * Emits the id of a just-created checklist for which the one-time reminder opt-in should be
     * shown. Emits at most once per user (persisted new-user-pending + opt-in-shown flags). A
     * [SharedFlow] (not StateFlow) so a late UI collector does not replay a stale request.
     */
    val reminderOptInRequests: SharedFlow<Long>

    /**
     * Target of the reminder opt-in sheet that is requested but not yet resolved, else null.
     * Set right before [reminderOptInRequests] emits (which still carries only the checklist id),
     * cleared by [reportReminderOptInOutcome]. The sheet host reads it to know WHAT to remind about;
     * screens use it to keep the notifications-off banner out from under the modal sheet.
     * Default: never pending.
     */
    val reminderOptInTarget: StateFlow<ReminderTarget?> get() = NeverPending

    /**
     * Called by a quick-add path right after it persisted a task the user typed (Inbox / Today
     * capture). Feeds the same once-per-install sheet as [onUserChecklistCreated] — first wins.
     */
    suspend fun onUserTaskCreated(target: ReminderTarget.Item) = Unit

    /**
     * Drops a pending request WITHOUT an outcome: no `activation_reminder_optin` event and the
     * opt-in is NOT marked shown. For a host that cannot show the sheet at all — reporting
     * "skipped" there would dilute the opt-in metric with users who never saw it.
     */
    fun cancelReminderOptIn() = Unit

    /**
     * Re-installs a sheet request the UI restored after process death (the pending target lives in
     * memory only). No event, no emission — the restored sheet is already on screen; this only makes
     * [reminderOptInTarget] true again so banners stay hidden under it and the latch holds.
     */
    fun restoreReminderOptIn(target: ReminderTarget) = Unit

    /**
     * Called by an AI create path right after it persists a new checklist (chat tool-call
     * dispatcher, Analyze, result preview, welcome onboarding).
     */
    suspend fun onAiChecklistCreated(checklistId: Long)

    /**
     * Called by a manual / template create path right after it persists a checklist the user
     * created (create form, template preview, templates list, weekly). Never call it for a
     * checklist the app created on the user's behalf.
     */
    suspend fun onUserChecklistCreated(checklistId: Long)

    /**
     * Records the outcome of the reminder opt-in soft-ask (after the UI has resolved it) and marks
     * it shown so it never appears again for this user.
     *
     * @param granted `true` if the user enabled (and was granted) notifications, `false` if skipped.
     */
    suspend fun reportReminderOptInOutcome(granted: Boolean)

    /** No-op coordinator — the default for ViewModels constructed outside Koin (tests, previews). */
    object NoOp : ActivationCoordinator {
        override val reminderOptInRequests: SharedFlow<Long> =
            kotlinx.coroutines.flow.MutableSharedFlow()
        override suspend fun onAiChecklistCreated(checklistId: Long) = Unit
        override suspend fun onUserChecklistCreated(checklistId: Long) = Unit
        override suspend fun reportReminderOptInOutcome(granted: Boolean) = Unit
    }
}
