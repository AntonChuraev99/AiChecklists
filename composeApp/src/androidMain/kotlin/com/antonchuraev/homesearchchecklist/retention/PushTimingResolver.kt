package com.antonchuraev.homesearchchecklist.retention

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import kotlin.concurrent.Volatile

/**
 * Resolves the retention-push TIMING experiment arm and the delivery hour for the local auto-pushes.
 *
 * The arm ("behavioral" | "fixed") comes from Remote Config ([RemoteConfigKeys.PUSH_TIMING_ARM]). It
 * is sticky per install — unlike the server copy-arm (an event-scoped param assigned per send), the
 * timing arm keeps the user in one bucket forever — so the first time it is resolved we mirror it into
 * the sticky user-property [AnalyticsParams.PUSH_AB_ARM] (guarded per process, exactly like
 * `AiModelExperimentTracker`), and every retention push event additionally carries
 * [AnalyticsParams.PUSH_AB_EXPERIMENT] = "timing".
 *
 * Delivery hour:
 *  - behavioral -> the user's most-active hour ([RetentionPrefs.mostActiveHour]).
 *  - fixed      -> [DEFAULT_HOUR] (~19:00 local).
 *
 * Best-effort throughout: analytics failures are logged and swallowed — timing must never break the
 * scheduling of a push.
 */
class PushTimingResolver(
    private val remoteConfig: RemoteConfigProvider,
    private val prefs: RetentionPrefs,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger,
) {

    @Volatile
    private var stickyArmSet = false

    /** The current timing arm, normalized to one of [ARM_BEHAVIORAL] / [ARM_FIXED]. */
    fun arm(): String {
        val raw = remoteConfig.getString(RemoteConfigKeys.PUSH_TIMING_ARM, ARM_BEHAVIORAL)
        return if (raw.trim().equals(ARM_FIXED, ignoreCase = true)) ARM_FIXED else ARM_BEHAVIORAL
    }

    /**
     * Set the sticky [AnalyticsParams.PUSH_AB_ARM] user-property once per process (idempotent guard).
     * Safe to call on every scheduling pass — only the first successful set actually writes.
     */
    fun ensureStickyArm() {
        if (stickyArmSet) return
        stickyArmSet = true
        runCatching {
            analytics.setUserProperties(mapOf(AnalyticsParams.PUSH_AB_ARM to arm()))
        }.onFailure { e ->
            // Allow a retry on the next pass if the set failed (e.g. tracker not ready yet).
            stickyArmSet = false
            logger.warning(TAG, "ensureStickyArm: failed to set push_ab_arm — ${e.message}")
        }
    }

    /** Delivery hour (0..23) for the local auto-pushes given the current arm. */
    suspend fun preferredHour(): Int =
        if (arm() == ARM_FIXED) DEFAULT_HOUR else prefs.mostActiveHour(defaultHour = DEFAULT_HOUR)

    /** The push_ab_experiment / push_ab_arm event params attached to every retention push event. */
    fun experimentParams(): Map<String, String> = mapOf(
        AnalyticsParams.PUSH_AB_EXPERIMENT to EXPERIMENT_TIMING,
        AnalyticsParams.PUSH_AB_ARM to arm(),
    )

    companion object {
        private const val TAG = "PushTiming"
        const val ARM_BEHAVIORAL = "behavioral"
        const val ARM_FIXED = "fixed"
        const val EXPERIMENT_TIMING = "timing"

        /** Fixed-arm delivery window and the behavioral fallback until the histogram has data (19:00). */
        const val DEFAULT_HOUR = 19
    }
}
