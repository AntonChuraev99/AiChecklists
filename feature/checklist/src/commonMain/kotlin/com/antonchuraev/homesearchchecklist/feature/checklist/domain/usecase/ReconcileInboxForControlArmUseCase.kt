package com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * Rollback path for the nav A/B experiment: gives a CONTROL-arm user back the tasks trapped in a
 * system Inbox row.
 *
 * ## The hole this closes
 * `isInbox = true` hides the row from [ChecklistRepository.projects], from every picker, from the
 * widget's DAO query, from the free-tier count and from MCP. The only surface that can read it is
 * the v2 arm's Inbox tab, which does not exist in CONTROL. So a control user who owns a flagged row
 * cannot list it, open it, or delete it — while it keeps existing and keeps syncing. Real routes
 * into that state:
 *
 *  * **Reinstall.** DataStore is fresh, so no arm is persisted; Firestore re-syncs the flagged row
 *    from the previous install before Remote Config assigns anything.
 *  * **Cleared DataStore.** Same, without the reinstall.
 *  * **Wind-down / losing-arm rollback.** The console is set to "control" and newly-resolving
 *    installs land there.
 *
 * Without this use case nothing detects or repairs it.
 *
 * ## Why the arm must be proven ASSIGNED, not merely equal to CONTROL
 * [NavExperimentResolver] collapses "assigned control" and "Remote Config has not answered yet"
 * into the same [NavVariant.CONTROL] — by design, because CONTROL is the safe shell to render while
 * resolution is in flight. Acting on that value alone would de-flag the Inbox of every *v2* user
 * whose RC has not activated on this launch (the rc-activation-gap population, ~35% of new users —
 * and exactly the reinstall case above). Their tasks would not be lost, but their Inbox would
 * dissolve into the Projects list and re-create itself empty on the next launch, and the experiment
 * would be unreadable.
 *
 * So the gate is [NavExperimentResolver.isArmAssigned] AND [NavVariant.CONTROL] — one source, read
 * straight from the resolver, no wire strings compared here.
 *
 * An earlier shape read the persisted arm through `NavExperimentPrefsRepository` and treated
 * `getNavArm() != null` as the proof of assignment. That accepted a persisted **"v2"** as well, and
 * the path was reachable: inside the resolver the DataStore read is wrapped in try/catch, so one
 * transient failure degrades to `persisted = null`; with RC also un-activated the resolver returns
 * CONTROL and opens its negative-cache window, and this use case's own read of the same DataStore
 * would then succeed, see "v2" and clear the flag — dissolving a real v2 user's Inbox. Two sources
 * that can disagree about the same fact is the defect; asking the resolver is the fix.
 *
 * ## Shape
 * One-shot per process, idempotent, and off the hot path — the caller must not await it before
 * navigating. A skipped attempt (arm not assigned yet) does NOT consume the one-shot guard, so the
 * next launch tries again. Never throws: a failure here must not break app start.
 */
class ReconcileInboxForControlArmUseCase(
    private val repository: ChecklistRepository,
    private val navResolver: NavExperimentResolver,
    private val logger: AppLogger,
) {

    /** Set once a decision was actually reached, so repeat calls in the same process cost nothing. */
    @Volatile
    private var decided = false

    /**
     * @return true when a flagged row was de-flagged by this call, false in every other case
     *   (already reconciled, v2 arm, arm not assigned yet, nothing flagged, or a handled failure).
     */
    suspend operator fun invoke(): Boolean {
        if (decided) return false

        return try {
            val arm = navResolver.ensureResolved()
            if (arm != NavVariant.CONTROL) {
                // A v2 user: the Inbox is their quick-capture zone and must stay flagged. Decided —
                // and the arm is sticky, so this can never change later in the process.
                decided = true
                return false
            }

            // See the class KDoc: a resolved CONTROL that is NOT assigned means "RC has not
            // answered", not "this user is control". Leave the flag alone and re-attempt next launch.
            // Deliberately does NOT consume `decided`.
            if (!navResolver.isArmAssigned()) {
                logger.debug(
                    TAG,
                    "skip: arm reads CONTROL but is not assigned — Remote Config has not answered " +
                        "yet, so a v2 Inbox must not be de-flagged. Retrying next launch.",
                )
                return false
            }

            decided = true
            val cleared = repository.clearInboxFlag()
            if (cleared) {
                logger.info(
                    TAG,
                    "control arm with a system Inbox present — flag cleared, the row is back in " +
                        "Projects with its tasks intact",
                )
            }
            cleared
        } catch (e: CancellationException) {
            // Structured concurrency: the caller's scope was cancelled (app going away). Not a
            // failure, and the guard stays clear so the next launch reconciles.
            throw e
        } catch (e: Exception) {
            // Never fatal: this runs during app start. Logged with the throwable so it reaches
            // Crashlytics instead of vanishing — a silently failing rollback is indistinguishable
            // from a user who never had an Inbox.
            //
            // The guard is released again (the mirrorOnce pattern in NavExperimentResolverImpl): a
            // transient DataStore/DB failure must not disable the rollback for the rest of the
            // process. It is bounded anyway — the arm check runs first and short-circuits for v2.
            decided = false
            logger.error(TAG, "reconcile failed: ${e.message}", e)
            false
        }
    }

    private companion object {
        private const val TAG = "InboxReconcile"
    }
}
