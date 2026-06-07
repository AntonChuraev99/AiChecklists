package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

/**
 * Resolves the first-checklist A/B variant from Remote Config.
 *
 * - [FirstChecklistVariant.AUTO_CREATE] (RC value "auto_create"): seed a "Your first
 *   checklist" template on first launch for new users.
 * - [FirstChecklistVariant.CURRENT] (RC value "current" or empty / unknown): keep the
 *   existing empty-state flow.
 *
 * Mirrors [GetOnboardingVariantUseCase]: any non-"auto_create" value (including the empty
 * client default) maps to the control variant so a stale/empty RC never collapses the A/B
 * distribution into the treatment cohort.
 */
class GetFirstChecklistVariantUseCase(
    private val remoteConfigProvider: RemoteConfigProvider,
    private val logger: AppLogger,
) {
    enum class FirstChecklistVariant { AUTO_CREATE, CURRENT }

    operator fun invoke(): FirstChecklistVariant {
        val raw = remoteConfigProvider.getString(
            RemoteConfigKeys.FIRST_CHECKLIST_VARIANT,
            RemoteConfigDefaults.FIRST_CHECKLIST_VARIANT
        )
        val variant = when (raw) {
            TYPE_AUTO_CREATE -> FirstChecklistVariant.AUTO_CREATE
            TYPE_CURRENT, EMPTY -> FirstChecklistVariant.CURRENT
            else -> {
                logger.warning(
                    TAG,
                    "Unknown first_checklist_variant RC value='$raw' — falling back to CURRENT"
                )
                FirstChecklistVariant.CURRENT
            }
        }
        logger.debug(TAG, "first checklist variant: rcValue='$raw', resolved=$variant")
        return variant
    }

    companion object {
        private const val TAG = "GetFirstChecklistVariant"
        private const val TYPE_AUTO_CREATE = "auto_create"
        private const val TYPE_CURRENT = "current"
        private const val EMPTY = ""
    }
}
