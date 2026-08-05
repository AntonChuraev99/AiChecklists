package com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Guarantees the system Inbox exists before the v2 Inbox screen tries to capture into it.
 *
 * Why a use case rather than a bare `repository.ensureInbox(...)` call from the ViewModel: creation
 * is a one-time-per-user event that has to be **measured** (it is the v2 arm's activation
 * precondition) and it must never crash the screen it is called from. Both concerns are cross-cutting
 * and belong here, not in presentation.
 */
class EnsureInboxUseCase(
    private val repository: ChecklistRepository,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger,
) {
    /**
     * @param name the Inbox title, already resolved by the caller via
     *   `getString(Res.string.inbox_checklist_name)` — the domain layer must never touch Compose
     *   Resources, and a literal here would hardcode one language for every user.
     * @return the Inbox checklist id, or null when creation failed (already logged; the caller is
     *   expected to surface a message rather than fail silently).
     */
    suspend operator fun invoke(name: String): Long? = runCatching {
        val existed = repository.observeInbox().first() != null
        val id = repository.ensureInbox(name)
        if (!existed) {
            // Deliberately NOT AnalyticsEvents.Checklist.CREATED. The Inbox is auto-created once per
            // user in the v2 arm; routing it through the normal create funnel would add +1
            // checklist_created per user and make every activation/creation metric incomparable
            // between the arms. Same precedent as Onboarding.FIRST_CHECKLIST_AUTO_CREATED.
            analytics.event(AnalyticsEvents.Inbox.SYSTEM_CREATED)
        }
        id
    }.getOrElse { e ->
        // runCatching also catches CancellationException; swallowing it would break structured
        // concurrency (the screen's scope would look alive after it was cancelled).
        if (e is CancellationException) throw e
        logger.error(TAG, "ensureInbox failed: ${e.message}", e)
        null
    }

    private companion object {
        private const val TAG = "EnsureInbox"
    }
}
