package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

class GetOnboardingVariantUseCase(
    private val remoteConfigProvider: RemoteConfigProvider
) {
    enum class OnboardingVariant { DEFAULT, INTERACTIVE }

    operator fun invoke(): OnboardingVariant {
        val type = remoteConfigProvider.getString(
            RemoteConfigKeys.ONBOARDING,
            RemoteConfigDefaults.ONBOARDING
        )
        return when (type) {
            TYPE_INTERACTIVE -> OnboardingVariant.INTERACTIVE
            else -> OnboardingVariant.DEFAULT
        }
    }

    companion object {
        private const val TYPE_INTERACTIVE = "interactive"
    }
}
