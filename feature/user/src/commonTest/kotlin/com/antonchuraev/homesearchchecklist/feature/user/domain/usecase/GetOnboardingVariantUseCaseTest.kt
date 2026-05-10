package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class GetOnboardingVariantUseCaseTest {

    private fun createUseCase(onboardingValue: String): GetOnboardingVariantUseCase {
        return GetOnboardingVariantUseCase(
            remoteConfigProvider = FakeRemoteConfigProvider(
                onboardingValue = onboardingValue
            )
        )
    }

    @Test
    fun invoke_interactive_returnsInteractive() {
        val useCase = createUseCase("interactive")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.INTERACTIVE, result)
    }

    @Test
    fun invoke_default_returnsDefault() {
        val useCase = createUseCase("default")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.DEFAULT, result)
    }

    @Test
    fun invoke_emptyString_returnsDefault() {
        val useCase = createUseCase("")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.DEFAULT, result)
    }

    @Test
    fun invoke_unknownValue_returnsDefault() {
        val useCase = createUseCase("unknown_variant")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.DEFAULT, result)
    }

    @Test
    fun invoke_none_returnsNone() {
        val useCase = createUseCase("none")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.NONE, result)
    }

    // --- Test doubles ---

    private class FakeRemoteConfigProvider(
        private val onboardingValue: String
    ) : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
        override fun getString(key: String, defaultValue: String): String {
            return if (key == RemoteConfigKeys.ONBOARDING) onboardingValue else defaultValue
        }
    }
}
