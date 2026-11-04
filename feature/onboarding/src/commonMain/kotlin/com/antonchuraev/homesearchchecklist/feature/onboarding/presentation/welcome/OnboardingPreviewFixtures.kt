package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

/**
 * One [OnboardingUiState] per state the onboarding UI must render (FEATURE_SPEC §12, DESIGN_SPEC §13).
 * State only — the previews and the screenshot matrix over them belong to the layout.
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

    val tryItDefault = OnboardingUiState(step = OnboardingStep.TryIt, isCallAvailable = true)
    val tryItModeWrite = tryItDefault.copy(pendingFocus = OnboardingMode.Write)
    val tryItModeAttach = tryItDefault.copy(pendingFocus = OnboardingMode.Attach)
    val tryItModeTalk = tryItDefault.copy(pendingFocus = OnboardingMode.Talk)
    val tryItCallHidden = tryItDefault.copy(isCallAvailable = false)
    val tryItTyped = tryItDefault.copy(inputText = TYPED)
    val tryItTypedLong = tryItDefault.copy(inputText = TYPED_LONG)
    val tryItEmptyHint = tryItDefault.copy(showEmptyInputHint = true)
    val tryItAiError = tryItDefault.copy(inputText = TYPED, aiError = OnboardingAiError.Unavailable)
    val tryItSignInError = tryItDefault.copy(inputText = TYPED, aiError = OnboardingAiError.SignInRequired)
    val tryItChipCreating = tryItDefault.copy(phase = OnboardingPhase.Creating(OnboardingSeed.Chip))

    val sampleReading = tryItDefault.copy(phase = OnboardingPhase.SampleRunning(SampleStage.Reading))
    val sampleReady = tryItDefault.copy(phase = OnboardingPhase.SampleRunning(SampleStage.Ready))

    val aiGenerating = tryItTyped.copy(phase = OnboardingPhase.AiGenerating(prompt = TYPED))
    val aiGeneratingSlowLongPrompt =
        tryItTypedLong.copy(phase = OnboardingPhase.AiGenerating(prompt = TYPED_LONG, isSlow = true))

    /** Every fixture with its name — for a parameterised preview / screenshot test. */
    val all: List<Pair<String, OnboardingUiState>> = listOf(
        "showcaseDefault" to showcaseDefault,
        "showcaseCallOff" to showcaseCallOff,
        "tryItDefault" to tryItDefault,
        "tryItModeWrite" to tryItModeWrite,
        "tryItModeAttach" to tryItModeAttach,
        "tryItModeTalk" to tryItModeTalk,
        "tryItCallHidden" to tryItCallHidden,
        "tryItTyped" to tryItTyped,
        "tryItTypedLong" to tryItTypedLong,
        "tryItEmptyHint" to tryItEmptyHint,
        "tryItAiError" to tryItAiError,
        "tryItSignInError" to tryItSignInError,
        "tryItChipCreating" to tryItChipCreating,
        "sampleReading" to sampleReading,
        "sampleReady" to sampleReady,
        "aiGenerating" to aiGenerating,
        "aiGeneratingSlowLongPrompt" to aiGeneratingSlowLongPrompt,
    )
}
