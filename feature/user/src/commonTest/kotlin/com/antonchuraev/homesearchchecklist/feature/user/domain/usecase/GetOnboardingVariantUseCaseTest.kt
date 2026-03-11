package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class GetOnboardingVariantUseCaseTest {

    private fun createUseCase(configJson: String): GetOnboardingVariantUseCase {
        return GetOnboardingVariantUseCase(
            remoteConfigProvider = FakeRemoteConfigProvider(
                onboardingConfig = configJson
            )
        )
    }

    @Test
    fun invoke_interactiveConfig_returnsInteractive() {
        val useCase = createUseCase("""{"type":"interactive"}""")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.INTERACTIVE, result)
    }

    @Test
    fun invoke_slidesConfig_returnsSlides() {
        val useCase = createUseCase("""{"type":"slides"}""")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.SLIDES, result)
    }

    @Test
    fun invoke_emptyString_returnsSlidesDefault() {
        val useCase = createUseCase("")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.SLIDES, result)
    }

    @Test
    fun invoke_malformedJson_returnsSlidesDefault() {
        val useCase = createUseCase("{not valid json}")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.SLIDES, result)
    }

    @Test
    fun invoke_unknownType_returnsSlidesDefault() {
        val useCase = createUseCase("""{"type":"unknown_variant"}""")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.SLIDES, result)
    }

    // --- Test doubles ---

    private class FakeRemoteConfigProvider(
        private val onboardingConfig: String
    ) : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
        override fun getString(key: String, defaultValue: String): String {
            return if (key == RemoteConfigKeys.ONBOARDING_CONFIG) onboardingConfig else defaultValue
        }
    }
}
