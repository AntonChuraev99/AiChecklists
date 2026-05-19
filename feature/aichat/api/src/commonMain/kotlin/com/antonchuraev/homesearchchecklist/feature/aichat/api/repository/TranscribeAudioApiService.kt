package com.antonchuraev.homesearchchecklist.feature.aichat.api.repository

import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale

/**
 * Network client for the `transcribe_audio` Cloud Function (speech-to-text).
 *
 * Spends 1 AI credit per successful transcription. Called by ChatViewModel after
 * the user releases the mic button — the transcribed text is then placed into the
 * chat input field so the user can edit before sending.
 *
 * The function accepts AAC m4a audio (Gemini's `audio/mp4` MIME) up to ~5MB raw.
 */
interface TranscribeAudioApiService {
    suspend fun transcribe(
        userId: String,
        audioBase64: String,
        locale: ChatLocale,
    ): RemoteTranscriptionResult
}

/**
 * Result of a remote transcription call.
 *
 * [Success] carries the transcript (may be empty if the audio was silent /
 * unintelligible) and the updated credit balance.
 */
sealed interface RemoteTranscriptionResult {
    data class Success(
        val transcript: String,
        val creditsRemaining: Int,
    ) : RemoteTranscriptionResult

    /** Server returned 402 — user has no AI credits left. */
    data object InsufficientCredits : RemoteTranscriptionResult

    /** Connection failure, timeout, or any transient network error. */
    data object NetworkError : RemoteTranscriptionResult

    /** Server returned a non-402 error (5xx, Gemini failure, oversized audio). */
    data object ServiceError : RemoteTranscriptionResult
}
