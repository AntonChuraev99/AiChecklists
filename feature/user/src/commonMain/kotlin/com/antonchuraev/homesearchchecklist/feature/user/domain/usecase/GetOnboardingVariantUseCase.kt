package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

/**
 * Resolves which onboarding flow a user sees from the `onboarding` Remote Config value.
 *
 * ## Fallback arm = AI_WELCOME (changed 2026-07-28)
 *
 * The client default for [RemoteConfigKeys.ONBOARDING] is deliberately EMPTY (see
 * [RemoteConfigDefaults.ONBOARDING]) — that empty string is the RC-layer sentinel meaning
 * "Remote Config gave us nothing" and it MUST stay empty so `onboarding_rc_resolved.rc_value_empty`
 * can still tell a failed fetch apart from a real assignment.
 *
 * What that sentinel *resolves to* is a PRODUCT decision, and it lives here. It used to be
 * DEFAULT (slides), which meant every RC failure landed in the slides arm: measured 2026-07-28,
 * users with `rc_activated=false` split 6.13/day slides vs 0.71 ai_welcome vs 0.03 none — ~89% of
 * all RC failures piled into one arm. The AI onboarding is the product's flagship first-run, so a
 * user whose Remote Config never arrived should get it rather than the legacy slides.
 *
 * ⚠️ This does NOT clean up the A/B data: the contamination MOVES to `ai_welcome`. Any analysis of
 * the onboarding experiment must still filter on `rc_activated=true`.
 *
 * Explicit server values are unaffected — `"default"` still means slides. Only the empty sentinel
 * and unparseable values take the new fallback.
 */
class GetOnboardingVariantUseCase(
    private val remoteConfigProvider: RemoteConfigProvider,
    private val logger: AppLogger,
    // True only on Android. The "ai_welcome" onboarding flow is currently Android-only — on web/iOS
    // the same RC value must fall back to DEFAULT (slides). Supplied via the Koin named("isAndroid")
    // qualifier (mirrors named("isDebugBuild")), so this stays a plain Boolean, not an expect/actual.
    private val isAndroid: Boolean,
) {
    enum class OnboardingVariant { DEFAULT, INTERACTIVE, NONE, AI_WELCOME }

    operator fun invoke(): OnboardingVariant {
        val raw = remoteConfigProvider.getString(
            RemoteConfigKeys.ONBOARDING,
            RemoteConfigDefaults.ONBOARDING
        )
        // Firebase A/B arm values are author-entered by hand in the Console. Normalize case and
        // stray whitespace so a "Interactive" / " none " typo can't silently collapse the user
        // into the DEFAULT (slides) control arm and starve the treatment to 0 assignments.
        val normalized = raw.trim().lowercase()
        val variant = when (normalized) {
            TYPE_INTERACTIVE -> OnboardingVariant.INTERACTIVE
            TYPE_NONE -> OnboardingVariant.NONE
            TYPE_AI_WELCOME -> aiWelcomeOrSlides()
            // An explicit server "default" is a real assignment to the slides arm — never a fallback.
            TYPE_DEFAULT -> OnboardingVariant.DEFAULT
            EMPTY -> {
                // The RC-failure path (fetch rejected / experiment not assigned yet). Logged at
                // warning, not debug: on Android this is also recorded as
                // onboarding_rc_resolved.rc_value_empty=true, and the two must agree.
                logger.warning(
                    TAG,
                    "onboarding RC value empty (fetch failed / no assignment) — falling back to AI_WELCOME"
                )
                aiWelcomeOrSlides()
            }
            else -> {
                logger.warning(
                    TAG,
                    "Unknown onboarding RC value='$raw' — falling back to AI_WELCOME"
                )
                aiWelcomeOrSlides()
            }
        }
        logger.debug(TAG, "onboarding variant: rcValue='$raw', resolved=$variant")
        return variant
    }

    /**
     * Platform gate: the AI Welcome flow only exists on Android (there is no WelcomeOnboarding
     * route on web/iOS). Everywhere else it degrades to the slide onboarding, so both the explicit
     * `ai_welcome` RC value and the new empty/unknown fallback can ship globally without breaking
     * a platform that cannot render them.
     */
    private fun aiWelcomeOrSlides(): OnboardingVariant = if (isAndroid) {
        OnboardingVariant.AI_WELCOME
    } else {
        logger.debug(TAG, "ai_welcome requested on non-Android — falling back to DEFAULT")
        OnboardingVariant.DEFAULT
    }

    companion object {
        private const val TAG = "GetOnboardingVariant"
        private const val TYPE_INTERACTIVE = "interactive"
        private const val TYPE_NONE = "none"
        private const val TYPE_DEFAULT = "default"
        private const val TYPE_AI_WELCOME = "ai_welcome"
        private const val EMPTY = ""
    }
}
