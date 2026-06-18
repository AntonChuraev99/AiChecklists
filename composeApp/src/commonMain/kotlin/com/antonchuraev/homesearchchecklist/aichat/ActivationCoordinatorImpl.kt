package com.antonchuraev.homesearchchecklist.aichat

import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.ActivationPrefsRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-level [ActivationCoordinator]. Lives in composeApp so it can read the persisted activation
 * flags ([ActivationPrefsRepository]) and the current user id ([UserDataRepository]) alongside the
 * tool-call dispatcher that drives it.
 *
 * "First AI checklist for this new user" is detected via the persisted new-user-pending flag (set
 * once at splash for a brand-new registration). The flag is cleared the first time an AI checklist
 * is created, so the FIRST_AI_CHECKLIST_CREATED event + the reminder opt-in fire at most once per
 * user — robust across process death (unlike the transient `isNewUser` registration flag).
 */
class ActivationCoordinatorImpl(
    private val activationPrefs: ActivationPrefsRepository,
    private val userDataRepository: UserDataRepository,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger,
) : ActivationCoordinator {

    private val _reminderOptInRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    override val reminderOptInRequests: SharedFlow<Long> = _reminderOptInRequests.asSharedFlow()

    /** UID this coordinator last emitted a reminder request for — passed to [reportReminderOptInOutcome]. */
    private var pendingReminderUid: String? = null

    override suspend fun onAiChecklistCreated(checklistId: Long, activationBundleEnabled: Boolean) {
        runCatching {
            val uid = userDataRepository.getUserData().userId
            if (uid.isBlank()) return

            // Only the new user's FIRST AI checklist passes the pending gate. Consume it
            // immediately so concurrent/subsequent creations never re-trigger the funnel.
            if (!activationPrefs.isNewUserPending(uid)) return
            activationPrefs.clearNewUserPending(uid)

            // Fires in BOTH A/B arms (flag value is the variant param) — the activation funnel
            // must be able to compare static-seed vs AI-first-run cohorts.
            analytics.event(
                AnalyticsEvents.Activation.FIRST_AI_CHECKLIST_CREATED,
                mapOf(AnalyticsParams.VARIANT to activationBundleEnabled.toString()),
            )
            logger.info(TAG, "first AI checklist for new user uid=${uid.take(8)} bundle=$activationBundleEnabled")

            // Reminder opt-in is a bundle feature — only ask in the treatment arm, and only once.
            if (activationBundleEnabled && !activationPrefs.isReminderOptInShown(uid)) {
                pendingReminderUid = uid
                _reminderOptInRequests.emit(checklistId)
            }
        }.onFailure { e ->
            logger.error(TAG, "onAiChecklistCreated failed: ${e.message}", e)
        }
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
        }.onFailure { e ->
            logger.error(TAG, "reportReminderOptInOutcome failed: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "ActivationCoordinator"
        private const val OUTCOME_GRANTED = "granted"
        private const val OUTCOME_SKIPPED = "skipped"
    }
}
