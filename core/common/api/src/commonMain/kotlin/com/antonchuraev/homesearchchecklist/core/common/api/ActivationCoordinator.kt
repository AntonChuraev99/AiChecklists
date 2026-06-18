package com.antonchuraev.homesearchchecklist.core.common.api

import kotlinx.coroutines.flow.SharedFlow

/**
 * Orchestrates the new-user activation funnel (RC flag `activation_bundle_v1`) WITHOUT coupling
 * the data layer (the AI tool-call dispatcher) to the UI.
 *
 * Flow of control:
 *  1. The dispatcher creates a checklist via the AI path and calls [onAiChecklistCreated].
 *  2. The implementation decides — using the persisted per-UID "new-user-pending" flag — whether
 *     this is the new user's *first* AI checklist. If so it fires
 *     [AnalyticsEvents.Activation.FIRST_AI_CHECKLIST_CREATED] (in BOTH A/B arms) and, only when the
 *     activation bundle is enabled AND the reminder opt-in has not been shown yet, emits the
 *     checklist id on [reminderOptInRequests].
 *  3. The app shell (App.kt) collects [reminderOptInRequests] and shows the one-time reminder
 *     opt-in soft-ask for that checklist, then reports the result via [reportReminderOptInOutcome].
 *
 * Keeping this in `core/common/api` lets both the dispatcher (data) and App.kt (UI) depend on the
 * same coordinator without a feature ↔ feature edge. The implementation lives in `composeApp`
 * where the full graph (datastore, analytics) is available.
 */
interface ActivationCoordinator {

    /**
     * Emits the id of a just-created checklist for which the one-time reminder opt-in should be
     * shown. Emits at most once per user (gated by the persisted new-user-pending + opt-in-shown
     * flags) and only when the activation bundle is enabled. A [SharedFlow] (not StateFlow) so a
     * late UI collector does not replay a stale request.
     */
    val reminderOptInRequests: SharedFlow<Long>

    /**
     * Called by the AI tool-call dispatcher right after it persists a new checklist
     * (chat create / preview-confirmed / attachment paths converge here).
     *
     * @param checklistId The id returned by `addChecklist`.
     * @param activationBundleEnabled The current value of the `activation_bundle_v1` RC flag. The
     *        analytics event still fires in both arms (so the A/B can compare); only the reminder
     *        opt-in emission is gated on this being `true`.
     */
    suspend fun onAiChecklistCreated(checklistId: Long, activationBundleEnabled: Boolean)

    /**
     * Records the outcome of the reminder opt-in soft-ask (after the UI has resolved it) and marks
     * it shown so it never appears again for this user.
     *
     * @param granted `true` if the user enabled (and was granted) notifications, `false` if skipped.
     */
    suspend fun reportReminderOptInOutcome(granted: Boolean)
}
