package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

class GetOnboardingVariantUseCase(
    private val remoteConfigProvider: RemoteConfigProvider,
    private val logger: AppLogger,
) {
    enum class OnboardingVariant { DEFAULT, INTERACTIVE, NONE }

    operator fun invoke(): OnboardingVariant {
        val raw = remoteConfigProvider.getString(
            RemoteConfigKeys.ONBOARDING,
            RemoteConfigDefaults.ONBOARDING
        )
        val variant = when (raw) {
            TYPE_INTERACTIVE -> OnboardingVariant.INTERACTIVE
            TYPE_NONE -> OnboardingVariant.NONE
            TYPE_DEFAULT, EMPTY -> OnboardingVariant.DEFAULT
            else -> {
                logger.warning(
                    TAG,
                    "Unknown onboarding RC value='$raw' — falling back to DEFAULT (slides)"
                )
                OnboardingVariant.DEFAULT
            }
        }
        logger.debug(TAG, "onboarding variant: rcValue='$raw', resolved=$variant")
        return variant
    }

    companion object {
        private const val TAG = "GetOnboardingVariant"
        private const val TYPE_INTERACTIVE = "interactive"
        private const val TYPE_NONE = "none"
        private const val TYPE_DEFAULT = "default"
        private const val EMPTY = ""
    }
}
