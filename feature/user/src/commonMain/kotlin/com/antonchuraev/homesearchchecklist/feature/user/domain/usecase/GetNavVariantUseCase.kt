package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

/**
 * Pure, synchronous read of the navigation A/B arm from Remote Config.
 *
 * Stateless on purpose: stickiness (per-process cache, DataStore persistence, sticky user
 * property) belongs to `NavExperimentResolver`, so this use case stays trivially testable and
 * can be called repeatedly while the arm is still unassigned.
 */
class GetNavVariantUseCase(
    private val remoteConfigProvider: RemoteConfigProvider,
    private val logger: AppLogger,
) {

    /**
     * @property variant the arm to render — always a usable value, CONTROL on every failure path.
     * @property assigned whether Remote Config actually named an arm. This is the load-bearing
     *   field: it is what stops the "RC has not fetched yet" fallback from being persisted as a
     *   real control assignment, which would pin the whole installed base to control forever and
     *   make the experiment read 100/0. Users with `assigned = false` also carry no `nav_arm` user
     *   property, so the rc-activation-gap population is excluded from the analysis rather than
     *   silently counted as control.
     * @property rawValue the untouched RC string, kept for diagnostics (a console typo shows here).
     */
    data class Result(
        val variant: NavVariant,
        val assigned: Boolean,
        val rawValue: String,
    )

    operator fun invoke(): Result {
        val raw = remoteConfigProvider.getString(
            RemoteConfigKeys.NAV_V2_ARM,
            RemoteConfigDefaults.NAV_V2_ARM,
        )
        // Firebase A/B arm values are author-entered by hand in the Console. Normalize case and
        // stray whitespace so a "V2" / " control " typo cannot starve the treatment to 0.
        val result = when (raw.trim().lowercase()) {
            ARM_V2 -> Result(NavVariant.V2, assigned = true, rawValue = raw)
            ARM_CONTROL -> Result(NavVariant.CONTROL, assigned = true, rawValue = raw)
            EMPTY -> Result(NavVariant.CONTROL, assigned = false, rawValue = raw)
            else -> {
                logger.warning(
                    TAG,
                    "Unknown nav_v2_arm RC value='$raw' — falling back to CONTROL",
                )
                // Deliberately NOT assigned: an unrecognised value is a console mistake, and
                // persisting it as control would make the mistake permanent for this install.
                Result(NavVariant.CONTROL, assigned = false, rawValue = raw)
            }
        }
        logger.debug(
            TAG,
            "nav variant: rcValue='$raw', resolved=${result.variant}, assigned=${result.assigned}",
        )
        return result
    }

    companion object {
        private const val TAG = "GetNavVariant"
        private const val ARM_CONTROL = "control"
        private const val ARM_V2 = "v2"
        private const val EMPTY = ""
    }
}
