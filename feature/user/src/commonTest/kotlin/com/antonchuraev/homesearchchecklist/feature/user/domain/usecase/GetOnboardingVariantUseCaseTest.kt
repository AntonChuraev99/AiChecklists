package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class GetOnboardingVariantUseCaseTest {

    private fun createUseCase(onboardingValue: String): GetOnboardingVariantUseCase {
        return GetOnboardingVariantUseCase(
            remoteConfigProvider = FakeRemoteConfigProvider(
                onboardingValue = onboardingValue
            ),
            logger = NoOpLogger(),
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

    /**
     * Guards against the historical bug where the client default was "interactive":
     * every user with stale Remote Config silently landed in the interactive treatment,
     * collapsing the A/B distribution to a single variant.
     *
     * If someone reverts RemoteConfigDefaults.ONBOARDING back to "interactive",
     * this test fails immediately.
     */
    @Test
    fun invoke_clientDefaultIsEmpty_resolvesToDefaultVariant() {
        // Simulate "Remote Config returned nothing useful": getString sees defaultValue
        // bubbled in by the use case (which is RemoteConfigDefaults.ONBOARDING).
        val useCase = GetOnboardingVariantUseCase(
            remoteConfigProvider = PassThroughDefaultProvider(),
            logger = NoOpLogger(),
        )

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.DEFAULT, result)
        assertEquals(
            "",
            RemoteConfigDefaults.ONBOARDING,
            "Client default for ONBOARDING must stay empty to keep A/B distribution honest"
        )
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

    /**
     * Mirrors FirebaseRemoteConfigProvider.getString behavior when Remote Config
     * has nothing to return: simply pass back the caller-supplied default.
     */
    private class PassThroughDefaultProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
    }

    private class NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
}
