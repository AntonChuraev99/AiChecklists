package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AudioRecorderErrorCodes
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome

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

/**
 * Whether the composer offers the mic. Web without a Google link has 0 credits (credits arrive with
 * the sign-in, same rule as [resolveAiError]), so a dictation could only fail; elsewhere it can work.
 */
internal fun voiceAvailable(isWeb: Boolean, isGoogleLinked: Boolean): Boolean = !isWeb || isGoogleLinked

/**
 * Maps a recorder `onError` code to why the press ended without audio. Anything that is not a known
 * [AudioRecorderErrorCodes] constant is a recorder failure message. Both "denied" codes are one case
 * here: onboarding offers no settings shortcut, only "type instead".
 */
internal fun voiceStopReasonFor(errorCode: String): OnboardingVoiceStop = when (errorCode) {
    AudioRecorderErrorCodes.PERMISSION_DENIED,
    AudioRecorderErrorCodes.PERMISSION_DENIED_PERMANENTLY,
    -> OnboardingVoiceStop.PermissionDenied
    AudioRecorderErrorCodes.PERMISSION_GRANTED_PRESS_AGAIN -> OnboardingVoiceStop.PermissionJustGranted
    AudioRecorderErrorCodes.RECORDING_TOO_SHORT -> OnboardingVoiceStop.TooShort
    else -> OnboardingVoiceStop.RecorderError
}

/** A transcript goes AFTER what is already in the field (dictating twice appends), like the chat. */
internal fun appendTranscript(current: String, transcript: String): String {
    val head = current.trimEnd()
    val tail = transcript.trim()
    return if (head.isEmpty()) tail else "$head $tail"
}

/**
 * The snackbar for a transcription that produced no text. Null only for [TranscriptionOutcome.Success]
 * (the text in the field is the feedback). Out of credits keeps the chat's wording.
 */
internal fun transcriptionFailureSnackbar(outcome: TranscriptionOutcome): String? = when (outcome) {
    is TranscriptionOutcome.Success -> null
    TranscriptionOutcome.EmptyTranscript -> OnboardingSnackbar.TRANSCRIBE_EMPTY
    TranscriptionOutcome.InsufficientCredits -> OnboardingSnackbar.NO_CREDITS
    TranscriptionOutcome.FileMissing,
    TranscriptionOutcome.NetworkError,
    TranscriptionOutcome.NotRegistered,
    TranscriptionOutcome.ServiceError,
    -> OnboardingSnackbar.TRANSCRIBE_ERROR
}

/** `outcome` of `onboarding_voice_recorded` for a finished transcription. */
internal fun transcriptionOutcomeWire(outcome: TranscriptionOutcome): String = when (outcome) {
    is TranscriptionOutcome.Success -> "transcribed"
    TranscriptionOutcome.EmptyTranscript -> "empty"
    TranscriptionOutcome.InsufficientCredits -> "insufficient_credits"
    TranscriptionOutcome.FileMissing -> "file_missing"
    TranscriptionOutcome.NetworkError -> "network_error"
    TranscriptionOutcome.NotRegistered -> "not_registered"
    TranscriptionOutcome.ServiceError -> "service_error"
}
