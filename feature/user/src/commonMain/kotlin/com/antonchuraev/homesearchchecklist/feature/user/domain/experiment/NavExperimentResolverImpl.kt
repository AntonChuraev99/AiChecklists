package com.antonchuraev.homesearchchecklist.feature.user.domain.experiment

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.datastore.api.NavExperimentPrefsRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetNavVariantUseCase
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "NavExperiment"

/**
 * Sticky implementation of [NavExperimentResolver]: three layers, checked in order.
 *
 *  1. [resolved] — per-process cache. Once set, Remote Config is NEVER consulted again, so a
 *     background `fetchAndActivate()` (SplashViewModel fires one fire-and-forget on every start)
 *     can never swap the navigation shell out from under the user mid-session.
 *  2. [prefs] — DataStore. Makes the assignment stable across launches that never await an RC
 *     activation, which is most launches for an existing user.
 *  3. [getNavVariant] — Remote Config, the only source that can ever ASSIGN an arm.
 *
 * Lives in feature/user rather than core/common/impl purely for module reach: core/common/impl has
 * no dependency on core:remoteconfig:api, while feature/user already depends on common.api +
 * datastore.api + remoteconfig.api — so this costs zero build-script changes.
 *
 * ## The one rule that must not be simplified away
 * An empty / unrecognised RC value is returned as CONTROL but is **never cached, persisted or
 * mirrored**. It means "RC has not assigned an arm yet", not "this user is control". Persisting it
 * would pin every install that starts before the first successful fetch to control permanently and
 * the experiment would read 100/0. Leaving it unresolved buys a correct split; the re-attempt cost
 * is bounded by the [UNASSIGNED_BACKOFF_MS] negative cache (see [ensureResolved]), so a user RC has
 * not assigned pays at most one DataStore+RC round-trip per 30s no matter how often callers ask.
 *
 * @param nowMs wall-clock source for the unassigned-attempt negative cache. Injectable only so a
 *   test can advance it: the backoff is measured in real milliseconds, and `runTest`'s virtual
 *   clock does not move `currentTimeMillis()`, so without this a test that re-attempts immediately
 *   would be indistinguishable from a real one 30 s later. Production always uses the default.
 */
class NavExperimentResolverImpl(
    private val getNavVariant: GetNavVariantUseCase,
    private val prefs: NavExperimentPrefsRepository,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger,
    private val nowMs: () -> Long = { currentTimeMillis() },
) : NavExperimentResolver {

    @Volatile
    private var resolved: NavVariant? = null

    @Volatile
    private var propertyMirrored = false

    /**
     * Monotonic-ish timestamp of the last attempt that ended UNASSIGNED, or 0 if there was none.
     * Negative cache for [UNASSIGNED_BACKOFF_MS] — see [ensureResolved].
     */
    @Volatile
    private var lastUnassignedAttemptAt = 0L

    override fun currentArm(): NavVariant = resolved ?: NavVariant.CONTROL

    // `resolved` is written ONLY on the two paths that carry a real assignment (restored from
    // DataStore, or returned by an activated Remote Config); the unassigned fallback deliberately
    // leaves it null so the installed base is never pinned to control. That invariant is exactly
    // what makes assignment observable here without a second source to disagree with.
    override fun isArmAssigned(): Boolean = resolved != null

    override suspend fun ensureResolved(): NavVariant {
        resolved?.let {
            // Cheap re-entry: still make sure the sticky property landed (a very early call can
            // run before the analytics tracker is ready).
            mirrorOnce(it)
            return it
        }

        // Negative cache. For the rc-activation-gap population (~35% of new users never get an
        // activated Remote Config) nothing below ever caches, so every call pays a suspending
        // DataStore read + an RC read. Callers are allowed to re-attempt freely (the interface
        // promises they may), so the cheap guard belongs here rather than in each call site.
        //
        // Stickiness is NOT affected: the unassigned path persists nothing either way, so skipping
        // the round-trip and performing it both return the same CONTROL fallback. The only cost is
        // that a fetch landing mid-window is picked up up to UNASSIGNED_BACKOFF_MS late — and the
        // shell is latched for the session anyway, so an arm arriving later in the same process
        // could not be applied without swapping navigation under the user.
        //
        // The range's lower bound (0L, not -infinity) guards a backwards clock jump — NTP correction
        // or the user changing the date: a negative delta falls OUTSIDE the window and expires it,
        // instead of pinning the resolver in the window for good.
        val now = nowMs()
        if (lastUnassignedAttemptAt != 0L) {
            val elapsed = now - lastUnassignedAttemptAt
            if (elapsed in 0L until UNASSIGNED_BACKOFF_MS) return NavVariant.CONTROL
        }

        // try/catch rather than runCatching: runCatching would also swallow CancellationException
        // and keep working inside a cancelled scope. Same shape as
        // FirebaseRemoteConfigProvider.warmUpInstallations.
        val persisted = try {
            prefs.getNavArm()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warning(TAG, "ensureResolved: failed to read persisted arm — ${e.message}")
            null
        }

        if (persisted != null) {
            // Anything that is not the treatment wire value reads as control: a corrupted or
            // future-version string must degrade to today's navigation, never to a half-built one.
            val arm = if (persisted == ARM_V2) NavVariant.V2 else NavVariant.CONTROL
            resolved = arm
            mirrorOnce(arm)
            logger.debug(TAG, "ensureResolved: restored persisted arm='$persisted' -> $arm")
            return arm
        }

        val rc = getNavVariant()
        if (!rc.assigned) {
            // Opens the negative-cache window. Stamped with `now` (read before the two suspending
            // reads above) rather than a fresh reading, so the window measures from the START of the
            // attempt and cannot be stretched by a slow DataStore/RC read.
            lastUnassignedAttemptAt = now
            logger.debug(TAG, "ensureResolved: RC has no arm yet (raw='${rc.rawValue}') — will re-attempt")
            return NavVariant.CONTROL
        }

        val arm = rc.variant
        resolved = arm
        val wire = arm.wire()
        try {
            prefs.setNavArm(wire)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Non-fatal: the arm is already cached for this process, so the session renders the
            // right shell; only cross-launch stickiness is lost and the next launch re-resolves.
            logger.warning(TAG, "ensureResolved: failed to persist arm='$wire' — ${e.message}")
        }
        mirrorOnce(arm)
        logger.debug(TAG, "ensureResolved: assigned arm='$wire' from RC")
        return arm
    }

    /**
     * Mirrors the arm into the sticky `nav_arm` user property exactly once per process.
     *
     * Set in BOTH arms, control included — a breakdown that only tags the treatment compares it
     * against "undefined" instead of against control. On failure the guard is RESET so a later
     * pass retries (the tracker may simply not be initialised yet) — the PushTimingResolver
     * pattern.
     */
    private fun mirrorOnce(arm: NavVariant) {
        if (propertyMirrored) return
        propertyMirrored = true
        runCatching {
            // String, never a Boolean: Firebase stringifies user properties while Amplitude
            // preserves native types, so a boolean would be "true" in GA4 and `true` in Amplitude
            // and any dashboard filtering on the wrong type silently returns zero rows.
            analytics.setUserProperties(mapOf(AnalyticsParams.NAV_ARM to arm.wire()))
        }.onFailure { e ->
            propertyMirrored = false
            logger.warning(TAG, "mirrorOnce: failed to set nav_arm user-property — ${e.message}")
        }
    }

    private fun NavVariant.wire(): String = when (this) {
        NavVariant.V2 -> ARM_V2
        NavVariant.CONTROL -> ARM_CONTROL
    }

    companion object {
        /** Wire values — identical to the Firebase RC console parameter values. */
        const val ARM_CONTROL = "control"
        const val ARM_V2 = "v2"

        /**
         * How long an UNASSIGNED attempt suppresses the next round-trip.
         *
         * Sized for "much shorter than a session, much longer than a burst of navigation changes":
         * long enough that repeated calls within one screen flow cost nothing, short enough that a
         * Remote Config activation landing mid-session is still picked up by a later call.
         */
        const val UNASSIGNED_BACKOFF_MS = 30_000L
    }
}
