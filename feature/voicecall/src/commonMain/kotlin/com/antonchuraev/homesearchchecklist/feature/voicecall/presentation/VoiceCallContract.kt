package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import androidx.compose.runtime.Immutable

/**
 * Contract of the voice call overlay: what the screen draws ([VoiceCallUiState]), what the user
 * does ([VoiceCallAction]) and what the host performs ([VoiceCallEffect]).
 *
 * These are UI models on purpose: the call engine (`:core:voice:api`, `CallEngineState`) is mapped
 * onto them in the ViewModel, so the screen never depends on the engine and every row of the
 * FEATURE_SPEC matrix has a fixture without a running engine.
 *
 * The overlay is not a Nav3 destination. It is visible exactly while the state is not [VoiceCallUiState.Closed]
 * (derived state, not a fire-once "show" event): a process death recreates the ViewModel in
 * [VoiceCallUiState.Closed], so a killed call is never restored and never shows a stale summary.
 *
 * Audio levels (mic while listening, TTS while speaking) are NOT here: they tick at ~20 Hz and are
 * passed to the screen as `() -> Float` read in the draw phase only.
 */
@Immutable
sealed interface VoiceCallUiState {

    /** No call; the overlay is not composed. */
    data object Closed : VoiceCallUiState

    /**
     * The microphone permission is missing.
     *
     * @property blocked the system dialog will not show again (denied with "don't ask again" or by
     *   policy): the primary action opens the app settings instead of the dialog.
     * @property requesting the system dialog is up; the primary button shows `loading` and further
     *   taps are swallowed (one request in flight).
     */
    data class Permission(val blocked: Boolean, val requesting: Boolean = false) : VoiceCallUiState

    /** The call is running (from Connecting until the engine stops). */
    data class InCall(
        val phase: VoiceCallPhaseUi,
        val timer: CallTimerUi,
        val banner: VoiceCallBannerUi? = null,
        val transcript: VoiceTranscriptUi = VoiceTranscriptUi(),
        val micOn: Boolean = true,
        val captionsOn: Boolean = true,
        val rate: VoiceSpeechRateUi = VoiceSpeechRateUi.Normal,
        /** Show the "Ask me to add a task…" hint: only while listening on the first turn of the call. */
        val firstTurn: Boolean = true,
        /** A tap on the orb while speaking interrupts Gisti (false when the engine forbids barge-in). */
        val interruptible: Boolean = true,
        /** TalkBack mode: the user ends each utterance with "Send" on the orb, no auto end-of-speech. */
        val manualEndOfTurn: Boolean = false,
    ) : VoiceCallUiState

    /** The call could not start or was lost with nothing created in it. */
    data class Failure(val kind: VoiceCallFailureUi) : VoiceCallUiState

    /**
     * The call is over. Not shown (straight to [Closed]) when the user hung up, nothing was created
     * and the call lasted under 10 s.
     *
     * @property callAgainAvailable minutes are left for another call (hides "Call again").
     * @property freeSecondsLeft free plan only: what is left of the free minutes, for the footer line.
     */
    data class Summary(
        val reason: VoiceCallSummaryReason,
        val durationSec: Int,
        val actions: List<VoiceActionUi>,
        val callAgainAvailable: Boolean,
        val freeSecondsLeft: Int? = null,
    ) : VoiceCallUiState

    /**
     * The minutes ran out (before the call or during it, after Gisti finished the sentence).
     *
     * @property resetsAtLocalTime premium daily reset as a local wall-clock time ("03:00"), formatted
     *   by the ViewModel from the server's epoch in the device time zone; `null` for the free plan.
     * @property actions everything done in this call (empty when the limit refused the start).
     */
    data class LimitReached(
        val plan: VoicePlanUi,
        val resetsAtLocalTime: String? = null,
        val actions: List<VoiceActionUi> = emptyList(),
    ) : VoiceCallUiState
}

/** What the call is doing; one StatusTitle and one orb mode per value. */
@Immutable
sealed interface VoiceCallPhaseUi {
    data object Connecting : VoiceCallPhaseUi

    /** @property hearing speech is coming in (orb follows the mic level, partial bubble visible). */
    data class Listening(val hearing: Boolean) : VoiceCallPhaseUi

    /** @property tool what the agent is running, when known; `null` = plain "Thinking…". */
    data class Thinking(val delay: VoiceThinkingDelayUi, val tool: VoiceToolStatusUi? = null) : VoiceCallPhaseUi

    data object Speaking : VoiceCallPhaseUi

    data object Muted : VoiceCallPhaseUi

    /** The network is gone; the call drops when [secondsLeft] runs out. */
    data class Reconnecting(val secondsLeft: Int) : VoiceCallPhaseUi

    data class Paused(val reason: VoicePauseReasonUi) : VoiceCallPhaseUi
}

enum class VoiceThinkingDelayUi {
    Normal,

    /** Over 6 s: "Just a moment…". */
    Slow,

    /** Over 20 s: "This is taking longer than usual" + Cancel. */
    VerySlow,
}

/** The tool the agent runs during a turn, localized by the client (never server prose). */
enum class VoiceToolStatusUi { CreatingChecklist, AddingTasks, Searching }

enum class VoicePauseReasonUi {
    /** The app went to the background; the user taps Resume. */
    Background,

    /** Audio focus taken (phone call, alarm); resumes by itself on focus gain, Resume also works. */
    SystemInterruption,
}

enum class VoiceSpeechRateUi { Normal, Slower }

enum class VoicePlanUi { Free, Premium }

/**
 * Call timer pill.
 *
 * @property remainingSec minutes left as the server counts them (`remaining_seconds` of the last
 *   start/heartbeat, minus the local tick since it), capped by the per-call maximum; `null` until
 *   the session started.
 * @property live the "on air" dot and the tick: Listening / Thinking / Speaking only.
 */
@Immutable
data class CallTimerUi(
    val elapsedSec: Int = 0,
    val remainingSec: Int? = null,
    val live: Boolean = false,
) {
    /** Warn look (≤ 60 s left). */
    val warning: Boolean get() = remainingSec != null && remainingSec <= WARNING_THRESHOLD_SEC

    companion object {
        const val WARNING_THRESHOLD_SEC = 60
    }
}

/** The single banner slot under the top row. Reconnecting wins over the limit warning. */
@Immutable
sealed interface VoiceCallBannerUi {
    data class Reconnecting(val secondsLeft: Int) : VoiceCallBannerUi

    /** @property minutesLeft rounded up, ≥ 1; the "Premium" action is shown for [VoicePlanUi.Free] only. */
    data class LimitWarning(val plan: VoicePlanUi, val minutesLeft: Int) : VoiceCallBannerUi
}

/**
 * The bottom-anchored transcript zone.
 *
 * @property userText the user's utterance: partial while [userFinal] is false (the bubble shows its
 *   tail), the final text while the agent thinks; `null` = no bubble.
 * @property reply the agent's reply for the captions; drawn only when captions are on.
 * @property lastAction the newest action card (older ones only in the summary).
 */
@Immutable
data class VoiceTranscriptUi(
    val userText: String? = null,
    val userFinal: Boolean = false,
    val reply: VoiceReplyUi? = null,
    val lastAction: VoiceActionUi? = null,
)

/** @property spokenChars how much of [text] TTS has already spoken (spoken part in `onSurface`). */
@Immutable
data class VoiceReplyUi(val text: String, val spokenChars: Int) {
    val spokenPart: String get() = text.take(spokenChars.coerceIn(0, text.length))
    val pendingPart: String get() = text.drop(spokenChars.coerceIn(0, text.length))
}

/**
 * Something the agent did in the user's data (a tool call that changed state).
 *
 * @property id stable key within the call (list key, click target).
 * @property title the object's name as the user sees it ("Trip to Kazan").
 * @property itemsCount [VoiceActionKind.ItemsAdded] only: how many items were added.
 * @property checklistId the checklist to open on tap; `null` = the card is not clickable.
 */
@Immutable
data class VoiceActionUi(
    val id: String,
    val kind: VoiceActionKind,
    val title: String,
    val itemsCount: Int? = null,
    val checklistId: String? = null,
)

enum class VoiceActionKind { ChecklistCreated, TaskAdded, ItemsAdded, Updated }

enum class VoiceCallFailureUi {
    /** No speech recognition service on the device. */
    NoRecognizer,

    /** No TTS voice for the call language; primary action opens the TTS settings. */
    NoVoice,

    /** No internet at start, or the call dropped offline with nothing created. */
    Offline,

    /** Connect timeout, repeated errors, rejected session, rate limit — with nothing created. */
    Server,

    /** Remote Config `voice_call_enabled` is off or the server kill switch answered. */
    FlagOff,
}

enum class VoiceCallSummaryReason {
    /** The user hung up (End, Back). */
    User,

    /** The per-call maximum (`max_call_seconds`) was reached; minutes may still be left. */
    CallCap,

    /** The app stayed in the background too long. */
    Background,

    /** The call was lost (network, errors) after something was created: "Call dropped". */
    Dropped,
}

/** Everything the user can do on the overlay. Lifecycle signals come from the Route. */
sealed interface VoiceCallAction {
    /** Entry: the call button in the chat pill. [surface] is the analytics entry point (`home`, `chat`, …). */
    data class Start(val surface: String) : VoiceCallAction

    // --- permission ---
    data object AllowMicClicked : VoiceCallAction

    /**
     * Answer of the system permission dialog.
     *
     * @property canAskAgain `shouldShowRequestPermissionRationale` read AFTER the answer: false
     *   after a denial means the dialog will not show again (permission becomes blocked).
     */
    data class MicPermissionResult(val granted: Boolean, val canAskAgain: Boolean) : VoiceCallAction
    data object OpenAppSettingsClicked : VoiceCallAction
    data object NotNowClicked : VoiceCallAction

    // --- lifecycle (Route, from the hosting lifecycle) ---
    /** ON_START; [micGranted] is a fresh permission probe (user may have changed it in settings). */
    data class ScreenStarted(val micGranted: Boolean) : VoiceCallAction

    /** ON_STOP, except a configuration change (rotation keeps the call running). */
    data object ScreenStopped : VoiceCallAction

    // --- in call ---
    /** Orb tap: interrupt while speaking; "Send" (finish utterance) while listening in TalkBack mode. */
    data object OrbClicked : VoiceCallAction
    data object MicToggled : VoiceCallAction
    data object CaptionsToggled : VoiceCallAction
    data class RateSelected(val rate: VoiceSpeechRateUi) : VoiceCallAction
    data object CancelTurnClicked : VoiceCallAction
    data object ResumeClicked : VoiceCallAction
    data object EndClicked : VoiceCallAction

    /** System Back: ends a running call without a dialog (D2); closes every full-screen state. */
    data object BackPressed : VoiceCallAction

    /**
     * Banner "Premium" (free plan, ≤ 1 min left). The paywall is a Nav3 route under the overlay, so
     * opening it ends the call first (same rule as D1: leaving the call ends it).
     */
    data object BannerPremiumClicked : VoiceCallAction

    /** Tap on an action card (in call or in the summary): ends the call and opens the object (D1). */
    data class ActionCardClicked(val actionId: String) : VoiceCallAction

    // --- full-screen states ---
    data object RetryClicked : VoiceCallAction
    data object OpenChatClicked : VoiceCallAction
    data object OpenVoiceSettingsClicked : VoiceCallAction
    data object CallAgainClicked : VoiceCallAction
    data object TryPremiumClicked : VoiceCallAction

    /** "Done" / "Close" / "Not now" on a final screen: closes the overlay. */
    data object CloseClicked : VoiceCallAction
}

/**
 * One-shot work the host does (navigation, system screens). Closing the overlay is NOT an effect:
 * the state goes to [VoiceCallUiState.Closed] together with the effect that needs it.
 */
sealed interface VoiceCallEffect {
    /** Launch the RECORD_AUDIO system dialog; answer with [VoiceCallAction.MicPermissionResult]. */
    data object RequestMicPermission : VoiceCallEffect

    /** The app's system settings page (blocked permission). */
    data object OpenAppSettings : VoiceCallEffect

    /** The system TTS settings (no voice for the language). */
    data object OpenTtsSettings : VoiceCallEffect

    /**
     * Open a checklist after the call ended from an action card (D1); the host also shows the
     * "Call ended · m:ss" snackbar with [callDurationSec] (`null` = from the summary, no snackbar).
     */
    data class OpenChecklist(val checklistId: String, val callDurationSec: Int?) : VoiceCallEffect

    /** The existing paywall route; [source] is `voice_call_limit` or `voice_call_warning`. */
    data class OpenPaywall(val source: String) : VoiceCallEffect

    /** Open the existing v2 chat sheet ("Ask in chat"). */
    data object OpenChatSheet : VoiceCallEffect
}

/** Paywall sources of the voice call (analytics `paywall_opened.source`). */
object VoiceCallPaywallSource {
    const val LIMIT = "voice_call_limit"
    const val WARNING = "voice_call_warning"
}
