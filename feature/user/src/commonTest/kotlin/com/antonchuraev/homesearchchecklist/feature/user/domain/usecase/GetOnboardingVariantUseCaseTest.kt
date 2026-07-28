package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class GetOnboardingVariantUseCaseTest {

    private fun createUseCase(
        onboardingValue: String,
        isAndroid: Boolean = true,
    ): GetOnboardingVariantUseCase {
        return GetOnboardingVariantUseCase(
            remoteConfigProvider = FakeRemoteConfigProvider(
                onboardingValue = onboardingValue
            ),
            logger = NoOpLogger(),
            isAndroid = isAndroid,
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

    /**
     * The RC-failure path. Empty = "Remote Config gave us nothing" (fetch rejected / experiment
     * not assigned yet), and since 2026-07-28 that user gets the flagship AI onboarding instead of
     * the legacy slides: prod measured ~89% of all RC failures piling into the slides arm
     * (rc_activated=false: 6.13/day slides vs 0.71 ai_welcome vs 0.03 none).
     */
    @Test
    fun invoke_emptyString_onAndroid_returnsAiWelcome() {
        val useCase = createUseCase("")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.AI_WELCOME, result)
    }

    /**
     * A typo'd / retired arm name is the same class of failure as an empty value — the user has no
     * valid assignment — so it takes the same fallback rather than silently landing in slides.
     */
    @Test
    fun invoke_unknownValue_onAndroid_returnsAiWelcome() {
        val useCase = createUseCase("unknown_variant")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.AI_WELCOME, result)
    }

    /**
     * The fallback still respects the Android-only platform gate: web/iOS have no
     * WelcomeOnboarding route, so an RC failure there must land on slides, not on a dead route.
     */
    @Test
    fun invoke_emptyString_onNonAndroid_fallsBackToSlides() {
        val useCase = createUseCase("", isAndroid = false)

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.DEFAULT, result)
    }

    @Test
    fun invoke_unknownValue_onNonAndroid_fallsBackToSlides() {
        val useCase = createUseCase("unknown_variant", isAndroid = false)

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.DEFAULT, result)
    }

    /**
     * An EXPLICIT server "default" is a real A/B assignment to the slides arm — it must never be
     * swept into the empty/unknown fallback, or the slides treatment can no longer be tested.
     */
    @Test
    fun invoke_explicitDefault_staysSlidesEvenOnAndroid() {
        val useCase = createUseCase("default", isAndroid = true)

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
     * Firebase A/B arm values are author-entered by hand in the Console. A capitalized
     * "Interactive" (or a value with stray whitespace) must NOT fall through to the DEFAULT
     * (slides) control arm — that silently starves the interactive treatment to 0 users while
     * the experiment reports as running. The parser must be case- and whitespace-insensitive.
     */
    @Test
    fun invoke_mixedCaseInteractive_returnsInteractive() {
        val useCase = createUseCase("Interactive")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.INTERACTIVE, result)
    }

    @Test
    fun invoke_paddedUppercaseNone_returnsNone() {
        val useCase = createUseCase("  NONE  ")

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.NONE, result)
    }

    @Test
    fun invoke_aiWelcomeValue_onAndroid_returnsAiWelcome() {
        val useCase = createUseCase("ai_welcome", isAndroid = true)

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.AI_WELCOME, result)
    }

    @Test
    fun invoke_aiWelcomeValue_onNonAndroid_fallsBackToDefault() {
        // The "ai_welcome" flow is Android-only — web/iOS must degrade to slides so the RC value
        // can ship globally without breaking platforms that have no WelcomeOnboarding route.
        val useCase = createUseCase("ai_welcome", isAndroid = false)

        val result = useCase()

        assertEquals(GetOnboardingVariantUseCase.OnboardingVariant.DEFAULT, result)
    }

    /**
     * Two invariants in one test, because they only make sense together:
     *
     * 1. The CLIENT DEFAULT must stay the empty string. It is also pushed into the Firebase SDK's
     *    in-app defaults, so any real arm name written there would make
     *    `remoteConfig.getString("onboarding")` non-empty even when nothing was fetched — pinning
     *    `onboarding_rc_resolved.rc_value_empty` to false forever and blinding the A/B health
     *    signal. (It also guards the older bug where the default was "interactive", which silently
     *    swept every stale-RC user into that treatment.)
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
            isAndroid = true,
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
