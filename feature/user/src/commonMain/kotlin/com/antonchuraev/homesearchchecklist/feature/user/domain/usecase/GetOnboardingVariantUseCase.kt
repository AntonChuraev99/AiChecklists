package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

/**
 * Resolves which onboarding flow a user sees from the `onboarding` Remote Config value.
 *
 * ## Two flows only (since 2026-11-04)
 *
 * The `slides` (RC `default`) and `interactive` arms were deleted together with their screens.
 * What is left is the AI Welcome onboarding ([OnboardingVariant.AI_WELCOME]) and no onboarding at
 * all ([OnboardingVariant.NONE], the web RC value). AI Welcome lives in commonMain, so it is the
 * fallback on EVERY platform — Android, web and iOS alike.
 *
 * The retired RC values `default` and `interactive` may still be served by a stale template or a
 * cached activation; they resolve to AI_WELCOME, the same as an empty or unknown value.
 *
 * ## The empty sentinel
 *
 * The client default for [RemoteConfigKeys.ONBOARDING] is deliberately EMPTY (see
 * [RemoteConfigDefaults.ONBOARDING]) — that empty string is the RC-layer sentinel meaning
 * "Remote Config gave us nothing" and it MUST stay empty so `onboarding_rc_resolved.rc_value_empty`
 * can still tell a failed fetch apart from a real assignment. What the sentinel resolves to is a
 * product decision and lives here.
 */
class GetOnboardingVariantUseCase(
    private val remoteConfigProvider: RemoteConfigProvider,
    private val logger: AppLogger,
) {
    enum class OnboardingVariant { NONE, AI_WELCOME }

    operator fun invoke(): OnboardingVariant {
        val raw = remoteConfigProvider.getString(
            RemoteConfigKeys.ONBOARDING,
            RemoteConfigDefaults.ONBOARDING
        )
        // Firebase arm values are author-entered by hand in the Console. Normalize case and stray
        // whitespace so a " None " typo is still read as the arm it names.
        val normalized = raw.trim().lowercase()
        val variant = when (normalized) {
            TYPE_NONE -> OnboardingVariant.NONE
            TYPE_AI_WELCOME -> OnboardingVariant.AI_WELCOME
            // Retired arms: their screens no longer exist, so a stale assignment lands on AI Welcome.
            TYPE_LEGACY_DEFAULT, TYPE_LEGACY_INTERACTIVE -> {
                logger.debug(TAG, "retired onboarding RC value='$raw' — resolving to AI_WELCOME")
                OnboardingVariant.AI_WELCOME
            }
            EMPTY -> {
                // The RC-failure path (fetch rejected / experiment not assigned yet). Logged at
                // warning, not debug: on Android this is also recorded as
                // onboarding_rc_resolved.rc_value_empty=true, and the two must agree.
                logger.warning(
                    TAG,
                    "onboarding RC value empty (fetch failed / no assignment) — falling back to AI_WELCOME"
                )
                OnboardingVariant.AI_WELCOME
            }
            else -> {
                logger.warning(
                    TAG,
                    "Unknown onboarding RC value='$raw' — falling back to AI_WELCOME"
                )
                OnboardingVariant.AI_WELCOME
            }
        }
        logger.debug(TAG, "onboarding variant: rcValue='$raw', resolved=$variant")
        return variant
    }

    companion object {
        private const val TAG = "GetOnboardingVariant"
        private const val TYPE_NONE = "none"
        private const val TYPE_AI_WELCOME = "ai_welcome"
        private const val TYPE_LEGACY_DEFAULT = "default"
        private const val TYPE_LEGACY_INTERACTIVE = "interactive"
        private const val EMPTY = ""
    }
}
