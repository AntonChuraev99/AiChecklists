package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome

/**
 * Speech-to-text for the onboarding composer. The production binding (OnboardingFeatureModule) is the
 * chat's own `AiChatRepository.transcribeAudio` with the chat's locale — the same Cloud Function
 * (`transcribe_audio`, 1 credit, refunded on a Gemini failure) and the same file cleanup. A narrow
 * seam, so the ViewModel does not take the whole chat repository and tests pass a fake.
 */
fun interface OnboardingVoiceTranscriber {
    suspend fun transcribe(audioPath: String, mimeType: String): TranscriptionOutcome
}
