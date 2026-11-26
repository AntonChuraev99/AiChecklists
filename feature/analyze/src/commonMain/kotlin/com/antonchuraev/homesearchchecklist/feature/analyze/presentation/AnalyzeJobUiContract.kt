package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

/**
 * Phase of the analysis THIS screen visit started, derived by the ViewModel from
 * `AnalysisJobScheduler.observe(jobId)` — never a latched flag. Replaces the meaning of
 * `AnalyzeScreenState.isAnalyzing`, which becomes `phase is Waiting || phase is Delivering`.
 */
sealed interface AnalyzeJobPhase {

    /** Input form. Also the phase after a re-entry, whatever happened to the previous visit's job. */
    data object Editing : AnalyzeJobPhase

    /**
     * Job enqueued and not finished. [waitingForNetwork] = Queued without connectivity ("Waiting for
     * a connection — the analysis starts automatically"); [canCancel] is true only then (a sent request
     * holds reserved credits and is never cancelled).
     */
    data class Waiting(val waitingForNetwork: Boolean, val canCancel: Boolean) : AnalyzeJobPhase

    /** Succeeded and this screen won the delivery claim: the loader stays until navigation lands. */
    data object Delivering : AnalyzeJobPhase

    /**
     * The job failed while this screen watched it. [message] is localized (the body under the
     * `analyze_failed_title` title). [cta] picks the buttons — see [FailedCta].
     */
    data class Failed(val message: String, val cta: FailedCta) : AnalyzeJobPhase
}

/** Buttons of [AnalyzeJobPhase.Failed] (DESIGN_SPEC G1). */
enum class FailedCta {
    /**
     * "Try again" ([AnalyzeJobIntent.OnRetryClick], same input as a NEW job) with a secondary
     * "Change input" ([AnalyzeJobIntent.OnBackToEditingClick]). Network / timeout / 5xx / 403.
     */
    Retry,

    /** Only "Change input" ([AnalyzeJobIntent.OnBackToEditingClick]): the input itself was the problem. */
    ChangeInput,

    /**
     * Only "OK" ([AnalyzeJobIntent.OnBackToEditingClick]): a Premium user refused for credits (402),
     * a cancelled job or a deleted target. Retrying would fail the same way.
     */
    Dismiss,

    /**
     * Free user refused for credits (402): primary "Get Pro" ([AnalyzeJobIntent.OnUpgradeClick] →
     * paywall, source `analyze_insufficient_credits`), secondary "OK"
     * ([AnalyzeJobIntent.OnBackToEditingClick]).
     */
    Upgrade,
}

/**
 * Screen-lifecycle and job intents. A sub-hierarchy so the ViewModel routes them with ONE branch.
 */
sealed interface AnalyzeJobIntent : AnalyzeScreenIntent {

    /**
     * Sent by the screen on entering composition with a token from `rememberSaveable` — the token
     * survives rotation (same visit) and is new after the entry was popped and pushed again (new visit).
     * A new token resets the form to the route arguments and detaches from the previous job: this is
     * the fix for the Activity-scoped ViewModel (no ViewModel-store entry decorator in NavDisplay)
     * reopening on an endless "Analyzing" loader.
     */
    data class OnScreenEntered(val entryToken: String) : AnalyzeJobIntent

    /** `DisposableEffect.onDispose`: the screen no longer watches; delivery falls to the app host. */
    data object OnScreenLeft : AnalyzeJobIntent

    /** "Continue in background" while [AnalyzeJobPhase.Waiting]: same as Back, stated as a promise. */
    data object OnContinueInBackgroundClick : AnalyzeJobIntent

    /** "Cancel" while Waiting for network (the only cancellable phase). */
    data object OnCancelWaitingClick : AnalyzeJobIntent

    /** "Try again" on [AnalyzeJobPhase.Failed] with `canRetrySameInput`. */
    data object OnRetryClick : AnalyzeJobIntent

    /** "Change input" on [AnalyzeJobPhase.Failed] without `canRetrySameInput`, or error dismissal. */
    data object OnBackToEditingClick : AnalyzeJobIntent

    /** "Get Pro" on [AnalyzeJobPhase.Failed] with [FailedCta.Upgrade] → paywall. */
    data object OnUpgradeClick : AnalyzeJobIntent

    /** Result of the POST_NOTIFICATIONS request the screen ran because `askNotificationPermission` was true. */
    data class OnNotificationPermissionResult(val granted: Boolean) : AnalyzeJobIntent
}
