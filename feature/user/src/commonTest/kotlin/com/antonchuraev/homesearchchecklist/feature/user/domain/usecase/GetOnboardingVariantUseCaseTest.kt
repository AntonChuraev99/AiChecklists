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

    /**
     * The `interactive` arm was deleted on 2026-11-04 together with its screens. A stale template or
     * a cached activation can still serve the value — it must land on AI Welcome, never on a dead
     * route.
     */
    @Test
    fun invoke_retiredInteractive_returnsAiWelcome() {
        val result = createUseCase("interactive")()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.AI_WELCOME, result)
    }

    /** Same for the retired slides arm, whose RC value was `default`. */
    @Test
    fun invoke_retiredDefault_returnsAiWelcome() {
        val result = createUseCase("default")()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.AI_WELCOME, result)
    }

    @Test
    fun invoke_retiredValuesMixedCase_returnAiWelcome() {
        assertEquals(
            GetOnboardingVariantUseCase.OnboardingVariant.AI_WELCOME,
            createUseCase("Interactive")(),
        )
        assertEquals(
            GetOnboardingVariantUseCase.OnboardingVariant.AI_WELCOME,
            createUseCase(" DEFAULT ")(),
        )
    }

    /**
     * The RC-failure path. Empty = "Remote Config gave us nothing" (fetch rejected / experiment
     * not assigned yet) — the user gets the AI onboarding.
     */
    @Test
    fun invoke_emptyString_returnsAiWelcome() {
        val result = createUseCase("")()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.AI_WELCOME, result)
    }

    /**
     * A typo'd / retired arm name is the same class of failure as an empty value — the user has no
     * valid assignment — so it takes the same fallback.
     */
    @Test
    fun invoke_unknownValue_returnsAiWelcome() {
        val result = createUseCase("unknown_variant")()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.AI_WELCOME, result)
    }

    @Test
    fun invoke_none_returnsNone() {
        val result = createUseCase("none")()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.NONE, result)
    }

    /**
     * Firebase arm values are author-entered by hand in the Console. A capitalized or padded
     * "none" must still read as the none arm — the parser is case- and whitespace-insensitive.
     */
    @Test
    fun invoke_paddedUppercaseNone_returnsNone() {
        val result = createUseCase("  NONE  ")()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.NONE, result)
    }

    @Test
    fun invoke_aiWelcomeValue_returnsAiWelcome() {
        val result = createUseCase("ai_welcome")()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.AI_WELCOME, result)
    }

    /**
     * Two invariants in one test, because they only make sense together:
     *
     * 1. The CLIENT DEFAULT must stay the empty string. It is also pushed into the Firebase SDK's
     *    in-app defaults, so any real arm name written there would make
     *    `remoteConfig.getString("onboarding")` non-empty even when nothing was fetched — pinning
     *    `onboarding_rc_resolved.rc_value_empty` to false forever and blinding the RC health
     *    signal.
     * 2. That empty sentinel must RESOLVE to AI_WELCOME — the product-level fallback decision,
     *    which lives in the use case rather than in the constant.
     */
    @Test
    fun invoke_clientDefaultIsEmpty_resolvesToAiWelcome() {
        // Simulate "Remote Config returned nothing useful": getString sees defaultValue
        // bubbled in by the use case (which is RemoteConfigDefaults.ONBOARDING).
        val useCase = GetOnboardingVariantUseCase(
            remoteConfigProvider = PassThroughDefaultProvider(),
            logger = NoOpLogger(),
        )

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.AI_WELCOME, result)
        assertEquals(
            "",
            RemoteConfigDefaults.ONBOARDING,
            "Client default for ONBOARDING must stay empty — it is the RC-layer sentinel behind " +
                "onboarding_rc_resolved.rc_value_empty; the fallback ARM belongs in the use case"
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
