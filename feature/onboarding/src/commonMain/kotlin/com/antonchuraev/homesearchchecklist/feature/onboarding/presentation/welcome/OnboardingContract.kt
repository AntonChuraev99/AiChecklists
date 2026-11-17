package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_sample_item_1
import aichecklists.core.designsystem.generated.resources.onboarding_sample_item_2
import aichecklists.core.designsystem.generated.resources.onboarding_sample_item_3
import aichecklists.core.designsystem.generated.resources.onboarding_sample_item_4
import aichecklists.core.designsystem.generated.resources.onboarding_sample_item_5
import aichecklists.core.designsystem.generated.resources.onboarding_sample_item_6
import aichecklists.core.designsystem.generated.resources.onboarding_talk_starter_1
import aichecklists.core.designsystem.generated.resources.onboarding_talk_starter_2
import aichecklists.core.designsystem.generated.resources.onboarding_talk_starter_3
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import org.jetbrains.compose.resources.StringResource

/*
 * Contract of the `ai_welcome` onboarding: Showcase -> one screen per choice (Write / Attach / Talk).
 *
 * Behaviour (state x event matrix, finish branches, voice, analytics) is specified in
 * docs/designs/onboarding-steps-2026-11-17/FEATURE_SPEC.md (supersedes the Try-it parts of
 * docs/designs/onboarding-refresh-2026-11-04/FEATURE_SPEC.md); the layout in DESIGN_SPEC.md next to it.
 *
 * Rendered by the stateless UI; driven by WelcomeOnboardingViewModel. WelcomeStarterTemplate and
 * WelcomeStringResolver (WelcomeOnboardingScreenContract.kt) are reused as they are.
 */

/**
 * Everything the stateless onboarding UI renders. UI-local state (scroll position, focus requests and
 * Write's autofocus, demo-stage clocks, IME, the recording timer and the drag-to-cancel arm) is NOT
 * here — it belongs to the composables.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Showcase,
    /**
     * The goal in the composer, ONE value shared by Write and Talk (a goal typed on Write carries into
     * Talk). Kept across ← / → and restored after process death. A transcript lands here too.
     */
    val inputText: String = "",
    /** True after a send tap on a blank field; cleared by the next [OnboardingIntent.OnInputChanged]. */
    val showEmptyInputHint: Boolean = false,
    /**
     * "Recording too short" under the composer (DESIGN_SPEC §3.1). Set by a too-short press, cleared by
     * the next interaction (typing, a new press, any step change).
     */
    val showVoiceTooShortHint: Boolean = false,
    /**
     * Whether the Call entry is shown (Talk caption on Showcase, Call disc on Talk). Same source as
     * the Ask-pill call button: platform supports calls && RC `voice_call_enabled`.
     */
    val isCallAvailable: Boolean = false,
    /**
     * Whether the composers show the mic. False on web while the user is not linked to Google: there
     * the wallet is 0 until sign-in, so every dictation would end in "not enough credits" (same source
     * as [OnboardingAiError.SignInRequired]). True on Android / iOS. No mic → the voice intents are
     * ignored with a warning.
     */
    val isVoiceAvailable: Boolean = true,
    val phase: OnboardingPhase = OnboardingPhase.Idle,
    /** AI-failure box under the composer (only on Write, only while [phase] is Idle). */
    val aiError: OnboardingAiError? = null,
) : State {
    /** Every control is disabled and back is swallowed while this is true. */
    val isBusy: Boolean get() = phase != OnboardingPhase.Idle

    /** The mic is held on this screen's composer (draw the recording state there). */
    val isRecording: Boolean get() = phase is OnboardingPhase.Recording

    /** A clip is being turned into text ("Transcribing…" in the composer, field read-only). */
    val isTranscribing: Boolean get() = phase is OnboardingPhase.Transcribing
}

/**
 * The screens. Write / Attach / Talk are three branches of the SAME step 2: [index] is the 1-based
 * `step_index` analytics value (1/2/2/2), [count] stays 2, [wire] is the `step` param.
 */
enum class OnboardingStep(val index: Int, val wire: String) {
    Showcase(index = 1, wire = "showcase"),
    Write(index = 2, wire = "write"),
    Attach(index = 2, wire = "attach"),
    Talk(index = 2, wire = "talk"),
    ;

    /** A screen with the composer (and therefore the mic). */
    val hasComposer: Boolean get() = this == Write || this == Talk

    companion object {
        /** Steps in the progress bar — NOT [entries].size: the three branches are one step. */
        const val count: Int = 2
    }
}

/** A Showcase tile. [wire] is the `mode` param of `onboarding_mode_selected`; [step] is where it leads. */
enum class OnboardingMode(val wire: String, val step: OnboardingStep) {
    Write("write", OnboardingStep.Write),
    Attach("attach", OnboardingStep.Attach),
    Talk("talk", OnboardingStep.Talk),
}

/**
 * The three Talk starters (DESIGN_SPEC §3.5). A tap sends [textRes] to the chat at once
 * (`ShellLaunch.OpenChat(autoSend = true)`). [key] is the `key` param of `onboarding_starter_tapped`.
 */
enum class OnboardingTalkStarter(val key: String, val textRes: StringResource) {
    PlanWeek("plan_week", Res.string.onboarding_talk_starter_1),
    PackTrip("pack_trip", Res.string.onboarding_talk_starter_2),
    WhatCanYouDo("what_can_you_do", Res.string.onboarding_talk_starter_3),
}

/**
 * Why a mic press ended without audio — what the Route reports with a null recording. Mapped from the
 * recorder's `onError` codes by [voiceStopReasonFor]; a plain null result is [UserCancelled].
 */
enum class OnboardingVoiceStop(val wire: String) {
    UserCancelled("cancelled"),
    TooShort("too_short"),
    PermissionDenied("permission_denied"),

    /** The permission dialog ate the press: nothing recorded, nothing cancelled — press again. */
    PermissionJustGranted("permission_just_granted"),
    RecorderError("recorder_error"),
}

/**
 * What the screen is doing right now. Anything but [Idle] disables input, hides nothing on its own
 * (the UI decides which overlay to draw from the variant) and swallows back.
 */
sealed interface OnboardingPhase {
    /** Waiting for the user. */
    data object Idle : OnboardingPhase

    /**
     * A local, AI-free write is in flight (starter chip or "Save as a plain list"). Controls stay
     * visible but disabled; the UI shows an inline spinner only after 300 ms.
     */
    data class Creating(val seed: OnboardingSeed) : OnboardingPhase

    /** The sample overlay is up. [stage] drives "Reading the note…" -> "Your list is ready". */
    data class SampleRunning(val stage: SampleStage) : OnboardingPhase

    /**
     * Typed text is being turned into a checklist by AI. Full-screen loader with the quoted [prompt];
     * [isSlow] flips to true after 8 s ("Still working on it…").
     */
    data class AiGenerating(val prompt: String, val isSlow: Boolean = false) : OnboardingPhase

    /**
     * The mic is held on [screen]'s composer (Write or Talk). The composer draws its recording state;
     * every other control is disabled and back is swallowed until the press ends.
     */
    data class Recording(val screen: OnboardingStep) : OnboardingPhase

    /**
     * The clip from [screen] is being transcribed (`transcribe_audio`, 1 credit). The composer shows
     * "Transcribing…" and is read-only; on success the text lands in the field and the phase returns
     * to [Idle] — nothing is sent until the user presses Send / Call.
     */
    data class Transcribing(val screen: OnboardingStep) : OnboardingPhase

    /**
     * Terminal: onboarding is being completed and navigation is about to happen. Never left again in
     * this ViewModel instance except by a failed completion, which returns to [Idle] with a snackbar.
     */
    data class Leaving(val seed: OnboardingSeed) : OnboardingPhase
}

/** Stages of the sample overlay (DESIGN_SPEC §1.5 / §7). */
enum class SampleStage {
    /** The note is being "read"; the checklist write may still be running. */
    Reading,

    /** The checklist is saved; the ResultCard is shown. The UI reports the end of its animation. */
    Ready,
}

/** Which AI-failure box is shown under the field. Both offer the same two AI-free actions. */
enum class OnboardingAiError(val wire: String) {
    /** Server / network / timeout / unknown — "Gisti can't reach the AI right now". */
    Unavailable("unavailable"),

    /** Web, not signed in with Google, out of credits — "Sign in with Google to use AI here". */
    SignInRequired("sign_in_required"),
}

/** Where a "Try the sample" tap came from (analytics `origin` on `onboarding_sample_used`). */
enum class SampleOrigin(val wire: String) {
    /** The photo card's CTA on Attach. */
    Card("card"),

    /** "Try the sample" in the AI-error box on Write. */
    AiError("ai_error"),

    /** "Show me an example" on Showcase — the zero-decision path, straight from step 1. */
    Showcase("showcase"),
}

/**
 * How onboarding was finished — the `seed` param of `onboarding_completed`, present on EVERY path.
 * [OwnFile] carries the material kind (`own_file_photo`, `own_file_pdf`, `own_file_web_link`,
 * `own_file_voice`).
 */
sealed interface OnboardingSeed {
    val wire: String

    data object Sample : OnboardingSeed { override val wire = "sample" }

    data class OwnFile(val kind: AnalyzeInputKind) : OnboardingSeed {
        override val wire: String get() = "own_file_${kind.wire}"
    }

    data object Typed : OnboardingSeed { override val wire = "typed" }

    data object Chip : OnboardingSeed { override val wire = "chip" }

    data object PlainList : OnboardingSeed { override val wire = "plain_list" }

    data object Chat : OnboardingSeed { override val wire = "chat" }

    data object Call : OnboardingSeed { override val wire = "call" }

    data object Skip : OnboardingSeed { override val wire = "skip" }
}

sealed interface OnboardingIntent : Intent {
    // ── Showcase ─────────────────────────────────────────────────────────────
    /** A Showcase tile: go to [mode]'s own screen (Write / Attach / Talk). */
    data class OnShowcaseTileClick(val mode: OnboardingMode) : OnboardingIntent

    // "Show me an example" on Showcase = OnSampleClick(SampleOrigin.Showcase).

    // ── Shared chrome ────────────────────────────────────────────────────────
    /** Top-bar ← or system back. Write / Attach / Talk -> Showcase; ignored on Showcase and while busy. */
    data object OnBack : OnboardingIntent

    data object OnSkip : OnboardingIntent

    // ── Composer (Write and Talk share [OnboardingUiState.inputText]) ────────
    data class OnInputChanged(val text: String) : OnboardingIntent

    /** Write: Send disc or IME Send. Blank text -> hint; otherwise AI generation into a list. */
    data object OnSendClick : OnboardingIntent

    /**
     * Talk: Send (and any other "continue in the chat" entry). Opens the chat with the trimmed text
     * ALREADY SENT (`ShellLaunch.OpenChat(prefill, autoSend = true)`); a blank field opens the chat
     * empty. Kept apart from [OnSendClick] because the destinations differ (chat vs a list here).
     */
    data object OnChatClick : OnboardingIntent

    /** Talk: Call disc. Only reachable while [OnboardingUiState.isCallAvailable]; the ViewModel re-checks it. */
    data object OnCallClick : OnboardingIntent

    /** Talk: a starter bubble — opens the chat with [starter]'s text already sent. */
    data class OnTalkStarterClick(val starter: OnboardingTalkStarter) : OnboardingIntent

    // ── Voice dictation (Write and Talk) ─────────────────────────────────────
    /**
     * Finger down on the mic. Sent by the Route right before it calls the recorder's `start()` (the
     * only caller; `start()` owns the permission prompt). Ignored unless Idle on a composer screen.
     */
    data object OnVoiceRecordingStarted : OnboardingIntent

    /**
     * The finger left the mic while the recorder's start was still pending (permission prompt up).
     * Drops the recording state only; the press's single [OnVoiceRecordingStopped] still follows.
     */
    data object OnVoicePressReleasedBeforeStart : OnboardingIntent

    /**
     * The recorder's single terminal callback for the press. Non-null [recordingPath] -> transcribe
     * and put the text into the field; null -> [stop] says why (null [stop] = user cancelled).
     */
    data class OnVoiceRecordingStopped(
        val recordingPath: String?,
        val mimeType: String = "audio/m4a",
        val stop: OnboardingVoiceStop? = null,
    ) : OnboardingIntent

    // ── Write / Attach / Showcase finishes ───────────────────────────────────
    /** "Try with this note" (Attach card), "Try the sample" (AI-error box) or "Show me an example" (Showcase). */
    data class OnSampleClick(val origin: SampleOrigin) : OnboardingIntent

    /** The overlay finished its "ready" animation; navigation may happen once the write is done too. */
    data object OnSampleAnimationFinished : OnboardingIntent

    /** Attach: a source card (Photo / PDF / Link / Voice) -> the Analyze screen with that input. */
    data class OnOwnSourceClick(val kind: AnalyzeInputKind) : OnboardingIntent

    /** Write: a starter chip — create the preset list (no AI) and open it in one tap. */
    data class OnStarterChipClick(val template: WelcomeStarterTemplate) : OnboardingIntent

    /** AI-error box "Save as a plain list". */
    data object OnSavePlainListClick : OnboardingIntent
}

/**
 * The sample note's six items — ONE list for both the checklist the sample run writes
 * (WelcomeOnboardingViewModel) and the ResultCard picture of it (OnboardingOverlays), so the card
 * can never show a list that differs from the one the user lands on.
 */
internal val SAMPLE_ITEMS: List<StringResource> = listOf(
    Res.string.onboarding_sample_item_1,
    Res.string.onboarding_sample_item_2,
    Res.string.onboarding_sample_item_3,
    Res.string.onboarding_sample_item_4,
    Res.string.onboarding_sample_item_5,
    Res.string.onboarding_sample_item_6,
)

sealed interface OnboardingSideEffect : SideEffect {
    /**
     * Show a snackbar. [messageKey] is one of [OnboardingSnackbar]'s keys, resolved to a localized
     * string in the Screen (the ViewModel never imports Compose Resources for snackbar text).
     */
    data class ShowSnackbar(val messageKey: String) : OnboardingSideEffect
}

/** Every [OnboardingSideEffect.ShowSnackbar] key — each the name of an existing string resource. */
object OnboardingSnackbar {
    /** A local write or the completion failed. */
    const val CREATE_ERROR = "onboarding_welcome_create_error"

    /** Microphone permission refused: type instead (the field stays usable). */
    const val MIC_DENIED = "onboarding_mic_denied"

    /** The permission dialog ate the press: hold the mic again. */
    const val MIC_HOLD_AGAIN = "chat_mic_hold_to_record"

    const val RECORDING_FAILED = "chat_recording_failed"
    const val TRANSCRIBE_EMPTY = "chat_transcribe_empty"
    const val TRANSCRIBE_ERROR = "chat_transcribe_error"
    const val NO_CREDITS = "chat_insufficient_credits"
}
