package com.antonchuraev.homesearchchecklist.aichat

import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderTarget
import com.antonchuraev.homesearchchecklist.core.datastore.api.ActivationPrefsRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What one reported checklist creation does to the activation funnel. Pure — see
 * [decideActivationStep]; the coordinator only executes it.
 */
internal data class ActivationStep(
    /** Fire FIRST_AI_CHECKLIST_CREATED and clear the new-user-pending marker. */
    val fireFirstAiEvent: Boolean,
    /** Emit the checklist on `reminderOptInRequests` (the one-time reminder opt-in sheet). */
    val requestReminderOptIn: Boolean,
)

/**
 * Decides the funnel step for a USER-initiated creation.
 *
 * - The sheet follows the first checklist the new user creates by ANY path (AI or manual), at
 *   most once: gated by the pending marker AND the opt-in-shown flag.
 * - Only the AI path consumes the pending marker, so a user whose first checklist is manual still
 *   gets FIRST_AI_CHECKLIST_CREATED on their first AI checklist later (the event keeps its meaning).
 * - A returning user (no marker) never gets either — the auto-seeded starter list is gone and the
 *   marker is set only for a brand-new registration at splash.
 */
internal fun decideActivationStep(
    isNewUserPending: Boolean,
    reminderOptInShown: Boolean,
    isAiPath: Boolean,
): ActivationStep = ActivationStep(
    fireFirstAiEvent = isNewUserPending && isAiPath,
    requestReminderOptIn = isNewUserPending && !reminderOptInShown,
)

/**
 * App-level [ActivationCoordinator]. Lives in composeApp so it can read the persisted activation
 * flags ([ActivationPrefsRepository]) and the current user id ([UserDataRepository]).
 */
class ActivationCoordinatorImpl(
    private val activationPrefs: ActivationPrefsRepository,
    private val userDataRepository: UserDataRepository,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger,
    /**
     * Whether the platform delivers reminders as system notifications (Android: yes; web / iOS: no).
     * Without them the sheet is never requested — so web new users never enter the opt-in funnel and
     * `activation_reminder_optin` stays an Android-only metric. A lambda (DI reads
     * `ChecklistReminderScheduler.supportsSystemNotifications`) keeps Compose and the scheduler out
     * of this class; defaulted to `true` for the existing tests.
     */
    private val supportsSystemNotifications: () -> Boolean = { true },
) : ActivationCoordinator {

    private val _reminderOptInRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    override val reminderOptInRequests: SharedFlow<Long> = _reminderOptInRequests.asSharedFlow()

    /** UID this coordinator last emitted a reminder request for — passed to [reportReminderOptInOutcome]. */
    private var pendingReminderUid: String? = null

    /**
     * Non-null between emitting a request and [reportReminderOptInOutcome]. Doubles as the
     * in-flight latch: two creations in quick succession (double-tap on Create) would otherwise
     * both pass `isReminderOptInShown == false` and queue two sheets.
     */
    private val _pending = MutableStateFlow<ReminderTarget?>(null)
    override val reminderOptInTarget: StateFlow<ReminderTarget?> = _pending.asStateFlow()

    override suspend fun onAiChecklistCreated(checklistId: Long) =
        onCreated(ReminderTarget.Checklist(checklistId), isAiPath = true)

    override suspend fun onUserChecklistCreated(checklistId: Long) =
        onCreated(ReminderTarget.Checklist(checklistId), isAiPath = false)

    // A task is the user's own capture exactly like a manual checklist: same gate, first wins.
    override suspend fun onUserTaskCreated(target: ReminderTarget.Item) =
        onCreated(target, isAiPath = false)

    private suspend fun onCreated(target: ReminderTarget, isAiPath: Boolean) {
        runCatching {
            val uid = userDataRepository.getUserData().userId
            if (uid.isBlank()) {
                logger.warning(TAG, "checklist created without a user id — activation funnel skipped")
                return
            }
            val step = decideActivationStep(
                isNewUserPending = activationPrefs.isNewUserPending(uid),
                reminderOptInShown = _pending.value != null || activationPrefs.isReminderOptInShown(uid),
                isAiPath = isAiPath,
            )

            if (step.fireFirstAiEvent) {
                // Consume first so a concurrent AI creation never re-fires the event.
                activationPrefs.clearNewUserPending(uid)
                // VARIANT pinned to "true": the bundle is unconditional since 2026-10-14 (flag
                // deleted). Kept as a constant so pre-cut charts that break down by it stay readable.
                analytics.event(
                    AnalyticsEvents.Activation.FIRST_AI_CHECKLIST_CREATED,
                    mapOf(AnalyticsParams.VARIANT to BUNDLE_VARIANT),
                )
                logger.info(TAG, "first AI checklist for new user uid=${uid.take(8)}")
            }

            if (step.requestReminderOptIn && !supportsSystemNotifications()) {
                // Not marked shown: the user never saw anything, and the marker is per uid — a later
                // Android sign-in with the same account should still get its one chance.
                logger.info(TAG, "reminder opt-in not requested: platform has no system notifications")
            } else if (step.requestReminderOptIn) {
                _pending.value = target
                pendingReminderUid = uid
                _reminderOptInRequests.emit(target.checklistId)
            }
        }.onFailure { e ->
            logger.error(TAG, "onChecklistCreated(ai=$isAiPath) failed: ${e.message}", e)
        }
    }

    override fun restoreReminderOptIn(target: ReminderTarget) {
        if (_pending.value == null) _pending.value = target
    }

    override fun cancelReminderOptIn() {
        if (_pending.value != null) logger.info(TAG, "reminder opt-in cancelled without an outcome")
        pendingReminderUid = null
        _pending.value = null
    }

    override suspend fun reportReminderOptInOutcome(granted: Boolean) {
        runCatching {
            analytics.event(
                AnalyticsEvents.Activation.REMINDER_OPTIN,
                mapOf(AnalyticsParams.OUTCOME to if (granted) OUTCOME_GRANTED else OUTCOME_SKIPPED),
            )
            // Mark shown regardless of outcome so the soft-ask never reappears for this user.
            val uid = pendingReminderUid ?: userDataRepository.getUserData().userId
            if (uid.isNotBlank()) {
                activationPrefs.markReminderOptInShown(uid)
            }
            pendingReminderUid = null
            _pending.value = null
        }.onFailure { e ->
            logger.error(TAG, "reportReminderOptInOutcome failed: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "ActivationCoordinator"
        private const val OUTCOME_GRANTED = "granted"
        private const val OUTCOME_SKIPPED = "skipped"
        private const val BUNDLE_VARIANT = "true"
    }
}
