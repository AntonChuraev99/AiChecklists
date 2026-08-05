package com.antonchuraev.homesearchchecklist.feature.user.domain.experiment

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.core.datastore.api.NavExperimentPrefsRepository
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "NavVariant"

/**
 * Resolves which navigation shell renders. Since 2026-08-03 this is a **user setting**, not an A/B
 * arm — see `docs/decisions/2026-08-03-shift-from-ai-first-to-checklist-first.md`.
 *
 * Two layers:
 *  1. [resolved] — per-process cache. Once set, nothing swaps the shell out mid-session. A change
 *     made in Settings goes through [setVariant], which updates this cache once the write lands, so
 *     the switch is immediate without ever being *accidentally* re-read from elsewhere.
 *  2. [prefs] — DataStore, the durable choice.
 *
 * ## Default is V2, and the absent value means "never chose"
 * An install with nothing stored gets [NavVariant.V2]: v2 IS the product now, and v1 is the escape
 * hatch. This inverts the previous experiment semantics, where an absent value meant CONTROL —
 * under an experiment the un-assigned population had to fall back to the untouched baseline, while
 * here falling back to v1 would ship the old app to everyone who has not opened Settings.
 *
 * ## Remote Config is no longer consulted
 * `nav_v2_arm` was never created in the Firebase console, so it always returned the empty string
 * and 100% of production ran v1 regardless. Reading it now would mean an owner-facing setting that
 * a console value could silently override.
 *
 * All members are best-effort and never throw: navigation must render even if DataStore or
 * analytics are broken.
 */
class NavExperimentResolverImpl(
    private val prefs: NavExperimentPrefsRepository,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger,
) : NavExperimentResolver {

    @Volatile
    private var resolved: NavVariant? = null

    @Volatile
    private var propertyMirrored = false

    // DEFAULT_VARIANT, not CONTROL: this is read during composition for the very first frame, and
    // handing out v1 there would flash the old shell before the stored value lands.
    override fun currentArm(): NavVariant = resolved ?: DEFAULT_VARIANT

    /**
     * True once the stored preference has actually been read this process.
     *
     * The name is a leftover from the experiment; what it now answers is "has resolution run", not
     * "was an arm assigned". Kept because callers use it for exactly that — to avoid acting on the
     * pre-resolution default. See the naming note in the ADR's consequences.
     */
    override fun isArmAssigned(): Boolean = resolved != null

    override suspend fun ensureResolved(): NavVariant {
        resolved?.let {
            // Cheap re-entry: still make sure the sticky property landed (a very early call can
            // run before the analytics tracker is ready).
            mirrorOnce(it)
            return it
        }

        // try/catch rather than runCatching: runCatching would also swallow CancellationException
        // and keep working inside a cancelled scope.
        val persisted = try {
            prefs.getNavArm()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warning(TAG, "ensureResolved: failed to read stored variant — ${e.message}")
            null
        }

        // Anything unrecognised resolves to the default rather than to v1: a corrupted or
        // future-version string must not silently downgrade the user to the old navigation.
        //
        // Blank is ABSENT, not unrecognised. The empty string is the prefs layer's own "no choice
        // stored" sentinel (NavExperimentPrefsRepository), so an install that simply never chose can
        // surface as "" here; routing that into the unknown-value branch would log a warning on
        // every launch for the majority of the installed base and bury the real corruption signal.
        val variant = when {
            persisted.isNullOrBlank() -> DEFAULT_VARIANT
            persisted == ARM_CONTROL -> NavVariant.CONTROL
            persisted == ARM_V2 -> NavVariant.V2
            else -> {
                logger.warning(TAG, "ensureResolved: unknown stored variant='$persisted' — using default")
                DEFAULT_VARIANT
            }
        }

        resolved = variant
        mirrorOnce(variant)
        logger.debug(TAG, "ensureResolved: stored='$persisted' -> $variant")
        return variant
    }

    /**
     * Applies a choice made in Settings: persists it and, once the write has landed, updates the
     * per-process cache so the shell switches on the next recomposition rather than on the next
     * launch.
     *
     * The cache advances only AFTER a successful write, and a failed write is dropped rather than
     * applied session-only. Applying it anyway (the previous order) made the failure invisible: the
     * shell switched, [currentArm] reported the new value, and the next launch restored the old one
     * with nothing having told the user. Callers read [currentArm] back to learn whether the choice
     * actually stuck.
     *
     * The user property is deliberately NOT re-mirrored. It is a sticky, once-per-process value; a
     * user who toggles back and forth would otherwise emit a stream of contradictory property
     * writes, and every historical dashboard reading `nav_arm` would attribute their whole session
     * to whichever value happened to be written last.
     */
    override suspend fun setVariant(variant: NavVariant) {
        val wire = variant.wire()
        try {
            prefs.setNavArm(wire)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Error, not warning: the user changed a setting and it is not going to survive.
            logger.error(TAG, "setVariant: failed to persist variant='$wire'", e)
            return
        }
        resolved = variant
        logger.debug(TAG, "setVariant: $variant")
    }

    /**
     * Debug/QA reset — see [NavExperimentResolver.clearVariant].
     *
     * Goes through the resolver rather than letting the debug screen write DataStore itself, so the
     * per-process cache below is updated in the same step. A direct write left the two disagreeing:
     * storage said "no choice", this cache still reported the forced one, and the Settings switch
     * (which seeds from [currentArm]) showed a variant that was no longer stored.
     */
    override suspend fun clearVariant() {
        try {
            // The EMPTY string is the repository's absent sentinel — there is no delete on that API,
            // and getNavArm() maps it back to null.
            prefs.setNavArm("")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "clearVariant: failed to clear the stored variant", e)
            return
        }
        // The default, not null: an absent value resolves to exactly this, so a re-read would return
        // it anyway — and dropping back to "unresolved" would make isArmAssigned() report false again
        // mid-process, which callers read as "resolution has not run, do not act on this yet".
        resolved = DEFAULT_VARIANT
        logger.debug(TAG, "clearVariant: reset to $DEFAULT_VARIANT")
    }

    /**
     * Mirrors the variant into the sticky `nav_arm` user property exactly once per process.
     *
     * Still emitted even though this is no longer an experiment: it is the only way to tell, in
     * analytics, which shell a session actually rendered — which matters more now that both exist
     * in production simultaneously. On failure the guard is RESET so a later pass retries (the
     * tracker may simply not be initialised yet) — the PushTimingResolver pattern.
     */
    private fun mirrorOnce(variant: NavVariant) {
        if (propertyMirrored) return
        propertyMirrored = true
        runCatching {
            // String, never a Boolean: Firebase stringifies user properties while Amplitude
            // preserves native types, so a boolean would be "true" in GA4 and `true` in Amplitude
            // and any dashboard filtering on the wrong type silently returns zero rows.
            analytics.setUserProperties(mapOf(AnalyticsParams.NAV_ARM to variant.wire()))
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
        /** Wire values kept unchanged so installs that already stored an arm keep resolving to it. */
        const val ARM_CONTROL = "control"
        const val ARM_V2 = "v2"

        /** What an install with no stored choice gets. v2 is the product; v1 is opt-in. */
        val DEFAULT_VARIANT = NavVariant.V2
    }
}
