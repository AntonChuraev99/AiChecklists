package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_sample_item_1
import aichecklists.core.designsystem.generated.resources.onboarding_sample_item_2
import aichecklists.core.designsystem.generated.resources.onboarding_sample_item_3
import aichecklists.core.designsystem.generated.resources.onboarding_sample_item_4
import aichecklists.core.designsystem.generated.resources.onboarding_sample_item_5
import aichecklists.core.designsystem.generated.resources.onboarding_sample_item_6
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import org.jetbrains.compose.resources.StringResource

/*
 * Contract of the refreshed `ai_welcome` onboarding: two steps, Showcase -> Try it.
 *
 * Behaviour (state x event matrix, finish branches, analytics) is specified in
 * docs/designs/onboarding-refresh-2026-11-04/FEATURE_SPEC.md; the layout in DESIGN_SPEC.md next to it.
 *
 * Rendered by the stateless UI; driven by WelcomeOnboardingViewModel. WelcomeStarterTemplate and
 * WelcomeStringResolver (WelcomeOnboardingScreenContract.kt) are reused as they are.
 */

/**
 * Everything the stateless onboarding UI renders. UI-local state (scroll position, focus ring
 * animation, demo-stage clocks, IME) is NOT here — it belongs to the composables.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Showcase,
    /**
     * The block Try-it must scroll to / ring / focus ONCE after a Showcase tile tap. Null after the
     * UI reports [OnboardingIntent.OnModeFocusConsumed], so ← then → does not replay the highlight.
     * [OnboardingMode.Default] is never stored here (the CTA path has nothing to highlight).
     */
    val pendingFocus: OnboardingMode? = null,
    /** The typed goal. Kept across ← / → and restored after process death. */
    val inputText: String = "",
    /** True after a send tap on a blank field; cleared by the next [OnboardingIntent.OnInputChanged]. */
    val showEmptyInputHint: Boolean = false,
    /**
     * Whether the Call entry is shown (Talk caption on Showcase, Call button on Try it). Same source as
     * the Ask-pill call button: platform supports calls && RC `voice_call_enabled`.
     */
    val isCallAvailable: Boolean = false,
    val phase: OnboardingPhase = OnboardingPhase.Idle,
    /** AI-failure box under the field (only on Try it, only while [phase] is Idle). */
    val aiError: OnboardingAiError? = null,
) : State {
    /** Every control is disabled and back is swallowed while this is true. */
    val isBusy: Boolean get() = phase != OnboardingPhase.Idle
}

/** The two screens, in forward order. [index] is the 1-based `step_index` analytics value. */
enum class OnboardingStep(val index: Int, val wire: String) {
    Showcase(index = 1, wire = "showcase"),
    TryIt(index = 2, wire = "try_it"),
    ;

    companion object {
        val count: Int get() = entries.size
    }
}

/** How the user entered Try it. [wire] is the `mode` param of `onboarding_mode_selected`. */
enum class OnboardingMode(val wire: String) {
    /** The Showcase CTA "Try it now" — no block is highlighted. */
    Default("default"),
    Write("write"),
    Attach("attach"),
    Talk("talk"),
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
    Card("card"),
    AiError("ai_error"),
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
    /** A Showcase tile: go to Try it and highlight [mode]'s block once. [mode] is never Default. */
    data class OnShowcaseTileClick(val mode: OnboardingMode) : OnboardingIntent

    /** Showcase CTA "Try it now": go to Try it, nothing highlighted. */
    data object OnShowcaseCtaClick : OnboardingIntent

    // ── Shared chrome ────────────────────────────────────────────────────────
    /** Top-bar ← or system back. Try it -> Showcase; ignored on Showcase and while busy. */
    data object OnBack : OnboardingIntent

    data object OnSkip : OnboardingIntent

    // ── Try it ───────────────────────────────────────────────────────────────
    /** The UI scrolled to / ringed / focused [OnboardingUiState.pendingFocus]. */
    data object OnModeFocusConsumed : OnboardingIntent

    data class OnInputChanged(val text: String) : OnboardingIntent

    /** Send disc or IME Send. Blank text -> hint; otherwise AI generation. */
    data object OnSendClick : OnboardingIntent

    /** "Try with this note" (card) or "Try the sample" (AI-error box). */
    data class OnSampleClick(val origin: SampleOrigin) : OnboardingIntent

    /** The overlay finished its "ready" animation; navigation may happen once the write is done too. */
    data object OnSampleAnimationFinished : OnboardingIntent

    /** A material pill of the own-file SourceRow. */
    data class OnOwnSourceClick(val kind: AnalyzeInputKind) : OnboardingIntent

    /** A starter chip: create the preset list (no AI) and open it in one tap. */
    data class OnStarterChipClick(val template: WelcomeStarterTemplate) : OnboardingIntent

    /** AI-error box "Save as a plain list". */
    data object OnSavePlainListClick : OnboardingIntent

    data object OnChatClick : OnboardingIntent

    /** Only reachable while [OnboardingUiState.isCallAvailable]; the ViewModel re-checks it. */
    data object OnCallClick : OnboardingIntent
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
     * Show a snackbar. [messageKey] is resolved to a localized string in the Screen (the ViewModel
     * never imports Compose Resources for snackbar text).
     */
    data class ShowSnackbar(val messageKey: String) : OnboardingSideEffect
}
