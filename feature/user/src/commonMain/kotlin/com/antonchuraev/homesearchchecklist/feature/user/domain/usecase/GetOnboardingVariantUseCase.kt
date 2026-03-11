package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GetOnboardingVariantUseCase(
    private val remoteConfigProvider: RemoteConfigProvider
) {
    enum class OnboardingVariant { SLIDES, INTERACTIVE }

    private val json = Json { ignoreUnknownKeys = true }

    operator fun invoke(): OnboardingVariant {
        val configJson = remoteConfigProvider.getString(
            RemoteConfigKeys.ONBOARDING_CONFIG,
            RemoteConfigDefaults.ONBOARDING_CONFIG
        )
        return try {
            val config = json.decodeFromString<OnboardingConfig>(configJson)
            when (config.type) {
                TYPE_INTERACTIVE -> OnboardingVariant.INTERACTIVE
                else -> OnboardingVariant.SLIDES
            }
        } catch (_: Exception) {
            OnboardingVariant.SLIDES
        }
    }

    @Serializable
    private data class OnboardingConfig(val type: String = TYPE_SLIDES)

    companion object {
        private const val TYPE_SLIDES = "slides"
        private const val TYPE_INTERACTIVE = "interactive"
    }
}
