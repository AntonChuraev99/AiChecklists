package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason

/** Longest name "Save as a plain list" gives a checklist (FEATURE_SPEC §4.9), ellipsis included. */
internal const val PLAIN_LIST_NAME_MAX = 60

private const val ELLIPSIS = "…"
private val WHITESPACE_RUN = Regex("\\s+")

/**
 * The name of the plain list saved from the typed text when AI failed: the text with whitespace runs
 * (the field takes 4 lines) collapsed to one space, untouched if it fits [max], otherwise cut on the
 * last word boundary before the limit with an ellipsis — the result never exceeds [max] characters.
 * A single word longer than the limit is cut mid-word (there is no boundary to use).
 */
internal fun plainListName(text: String, max: Int = PLAIN_LIST_NAME_MAX): String {
    val flat = text.trim().replace(WHITESPACE_RUN, " ")
    if (flat.length <= max) return flat
    val room = max - ELLIPSIS.length
    val head = flat.substring(0, room)
    // The character right after the cut is a space: the cut already sits on a word boundary.
    val cut = if (flat[room] == ' ') head else head.substringBeforeLast(' ', missingDelimiterValue = head)
    return cut.trimEnd() + ELLIPSIS
}

/**
 * Which AI-failure box Try it shows (FEATURE_SPEC §5.3). Only web, signed out of Google and out of
 * credits (402 / no user yet) gets the "sign in with Google" text: on web credits arrive with the
 * sign-in. Everything else — and every Android failure — is "can't reach the AI right now".
 */
internal fun resolveAiError(reason: AiFailureReason, isWeb: Boolean, isGoogleLinked: Boolean): OnboardingAiError =
    if (isWeb && !isGoogleLinked &&
        (reason == AiFailureReason.CREDIT_GATE || reason == AiFailureReason.USER_NOT_READY)
    ) {
        OnboardingAiError.SignInRequired
    } else {
        OnboardingAiError.Unavailable
    }
