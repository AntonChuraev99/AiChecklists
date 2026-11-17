package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

/**
 * One [OnboardingUiState] per state the onboarding UI must render (FEATURE_SPEC 2026-11-17 §3,
 * DESIGN_SPEC §4). State only — the previews and the screenshot matrix over them belong to the layout.
 *
 * In commonMain because the module has no debug source set and a preview in main cannot see one.
 * The typed texts are fixture data, not UI copy (the UI never renders them on its own).
 */
internal object OnboardingPreviewFixtures {
    private const val TYPED = "Pack for a beach trip"
    private const val TYPED_LONG =
        "Plan the move to the new flat: book the van for Saturday morning, pack the kitchen and the books, " +
            "cancel the old internet contract, change the address at the bank and tell the landlord the date"

    val showcaseDefault = OnboardingUiState(isCallAvailable = true)
    val showcaseCallOff = OnboardingUiState(isCallAvailable = false)

    // ── Write ──
    val writeDefault = OnboardingUiState(step = OnboardingStep.Write, isCallAvailable = true)
    val writeTyped = writeDefault.copy(inputText = TYPED)
    val writeTypedLong = writeDefault.copy(inputText = TYPED_LONG)
    val writeEmptyHint = writeDefault.copy(showEmptyInputHint = true)
    val writeAiError = writeDefault.copy(inputText = TYPED, aiError = OnboardingAiError.Unavailable)
    val writeSignInError = writeDefault.copy(inputText = TYPED, aiError = OnboardingAiError.SignInRequired)
    val writeChipCreating = writeDefault.copy(phase = OnboardingPhase.Creating(OnboardingSeed.Chip))
    val writeRecording = writeDefault.copy(phase = OnboardingPhase.Recording(OnboardingStep.Write))
    val writeTranscribing = writeTyped.copy(phase = OnboardingPhase.Transcribing(OnboardingStep.Write))
    val writeVoiceTooShort = writeDefault.copy(showVoiceTooShortHint = true)

    /** Web, not linked to Google: no mic on the composer. */
    val writeNoVoice = writeDefault.copy(isVoiceAvailable = false, isCallAvailable = false)

    // ── Attach ──
    val attachDefault = OnboardingUiState(step = OnboardingStep.Attach, isCallAvailable = true)

    // ── Talk ──
    val talkDefault = OnboardingUiState(step = OnboardingStep.Talk, isCallAvailable = true)
    val talkCallOff = talkDefault.copy(isCallAvailable = false)
    val talkTyped = talkDefault.copy(inputText = TYPED)
    val talkRecording = talkDefault.copy(phase = OnboardingPhase.Recording(OnboardingStep.Talk))
    val talkTranscribing = talkDefault.copy(phase = OnboardingPhase.Transcribing(OnboardingStep.Talk))
    val talkLeaving = talkTyped.copy(phase = OnboardingPhase.Leaving(OnboardingSeed.Chat))

    /** Web, not linked to Google: no mic, no call (web has no call). */
    val talkNoVoice = talkDefault.copy(isVoiceAvailable = false, isCallAvailable = false)

    // ── Overlays ──
    val sampleReading = attachDefault.copy(phase = OnboardingPhase.SampleRunning(SampleStage.Reading))
    val sampleReady = attachDefault.copy(phase = OnboardingPhase.SampleRunning(SampleStage.Ready))
    val sampleFromShowcase = showcaseDefault.copy(phase = OnboardingPhase.SampleRunning(SampleStage.Reading))

    val aiGenerating = writeTyped.copy(phase = OnboardingPhase.AiGenerating(prompt = TYPED))
    val aiGeneratingSlowLongPrompt =
        writeTypedLong.copy(phase = OnboardingPhase.AiGenerating(prompt = TYPED_LONG, isSlow = true))

    /** Every fixture with its name — for a parameterised preview / screenshot test. */
    val all: List<Pair<String, OnboardingUiState>> = listOf(
        "showcaseDefault" to showcaseDefault,
        "showcaseCallOff" to showcaseCallOff,
        "writeDefault" to writeDefault,
        "writeTyped" to writeTyped,
        "writeTypedLong" to writeTypedLong,
        "writeEmptyHint" to writeEmptyHint,
        "writeAiError" to writeAiError,
        "writeSignInError" to writeSignInError,
        "writeChipCreating" to writeChipCreating,
        "writeRecording" to writeRecording,
        "writeTranscribing" to writeTranscribing,
        "writeVoiceTooShort" to writeVoiceTooShort,
        "writeNoVoice" to writeNoVoice,
        "attachDefault" to attachDefault,
        "talkDefault" to talkDefault,
        "talkCallOff" to talkCallOff,
        "talkTyped" to talkTyped,
        "talkRecording" to talkRecording,
        "talkTranscribing" to talkTranscribing,
        "talkLeaving" to talkLeaving,
        "talkNoVoice" to talkNoVoice,
        "sampleReading" to sampleReading,
        "sampleReady" to sampleReady,
        "sampleFromShowcase" to sampleFromShowcase,
        "aiGenerating" to aiGenerating,
        "aiGeneratingSlowLongPrompt" to aiGeneratingSlowLongPrompt,
    )
}
