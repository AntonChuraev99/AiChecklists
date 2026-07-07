package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

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
            TYPE_AI_WELCOME -> {
                // Platform gate: the AI Welcome flow only exists on Android. On other platforms
                // degrade to the default slides so the RC value can ship globally without breaking
                // web/iOS (they have no WelcomeOnboarding route).
                if (isAndroid) {
                    OnboardingVariant.AI_WELCOME
                } else {
                    logger.debug(TAG, "ai_welcome requested on non-Android — falling back to DEFAULT")
                    OnboardingVariant.DEFAULT
                }
            }
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
        private const val TYPE_AI_WELCOME = "ai_welcome"
        private const val EMPTY = ""
    }
}
