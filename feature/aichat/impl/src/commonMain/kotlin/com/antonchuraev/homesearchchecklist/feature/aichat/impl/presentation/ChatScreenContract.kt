package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatChoice
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

/**
 * Immutable UI state for [ChatScreen].
 *
 * @param messages     Ordered chat history (ascending by timestamp).
 * @param inputText    Current text in the input field.
 * @param pendingChoice  Non-null when an [AiChoiceResponse] (prompt + choice chips) should be
 *                       shown as the last item — replaces the old write-intent preview card AND
 *                       the agent plan card with one Claude-style choice block.
 * @param creditBalance  User's remaining AI credits. Live value from [UserDataRepository.getUserDataFlow],
 *                       updated optimistically from server API responses on Layer 2/3 success.
 *                       Hidden in UI when ≤ 0 (loading state or genuinely empty).
 * @param showPricingSheet  Whether the pricing help bottom sheet is visible.
 * @param showSettingsSheet Whether the chat settings bottom sheet is visible.
 * @param deepThinkingEnabled When true, all queries bypass Layer 1+2 and go straight to Layer 3
 *                            (completeFreeForm, 3 credits each). Persisted in DataStore.
 * @param isProcessing  True while the router is classifying / dispatching (disables Send).
 * @param feedbackTarget    Non-null when the feedback sheet is open for this assistant message.
 * @param feedbackText      Current text typed in the feedback input field.
 * @param isSubmittingFeedback True while the feedback is being submitted (disables Submit button).
 * @param pendingAttachments List of files the user has picked but not yet sent.
 *                           Shown as a chip strip above the input row. Cleared on send/cancel.
 * @param attachmentPickerType Non-null while an attachment picker flow is in progress.
 *                             Used as a trigger-flag (trigger-flag pattern, item-attachments solution):
 *                             UI reads this value in LaunchedEffect, launches the appropriate
 *                             platform picker, then sends OnAttachmentPickerTriggered to reset.
 * @param isRecording       True while the user is holding the mic button (voice recording).
 *                          Phase 1 only bookkeeps this value — recording infra lives in Phase 3.
 * @param voiceRecordingError Non-null when the last recording attempt failed.
 * @param isTranscribing    True while a voice recording is being sent to the transcription
 *                          Cloud Function. Input field and action buttons are disabled during
 *                          this state; a progress indicator replaces the mic/send button.
 */
data class ChatScreenState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val pendingChoice: PendingChoice? = null,
    val creditBalance: Int = 0,
    val isPremium: Boolean = false,
    val showPricingSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showFeaturesSheet: Boolean = false,
    val deepThinkingEnabled: Boolean = false,
    val isProcessing: Boolean = false,
    val feedbackTarget: ChatMessage? = null,
    val feedbackText: String = "",
    val isSubmittingFeedback: Boolean = false,
    // ── Attachment state (Phase 1: domain/VM only; UI picker lives in Phase 3) ──
    val pendingAttachments: List<ChatAttachment> = emptyList(),
    val attachmentPickerType: AttachmentSource? = null,
    val isRecording: Boolean = false,
    val voiceRecordingError: String? = null,
    val isTranscribing: Boolean = false,
    /**
     * Checklist ID that was active when the sheet was opened from [ChecklistDetailScreen].
     * Used to bias Layer-1/Layer-2/Layer-3 requests to this checklist by default.
     * Null when the sheet is opened from [MainScreen] with no focus context.
     */
    val contextChecklistId: Long? = null,
) : State {
    /** True when the Send button should be active (text entered OR attachments pending). */
    val canSend: Boolean get() = inputText.isNotBlank() || pendingAttachments.isNotEmpty()
}

/**
 * Presentation wrapper around a [ChatChoice] with the transient interaction state that the
 * [AiChoiceResponse] composable needs. The domain [ChatChoice] is built by the ViewModel
 * (strings already resolved); this adds the per-render UI state.
 *
 * @param choice      The localized prompt + choice chips (and optional escape chip).
 * @param executingId Non-null while a chip is being executed — the matching chip shows a
 *                    spinner and the whole block becomes non-interactive (one tap only).
 * @param executingLabel Localized loading label for the executing chip ("Creating…" / "Deleting…"),
 *                    resolved by the ViewModel where the action type is known. Null until a chip
 *                    is tapped; the chip falls back to a generic "Working…" if absent.
 * @param editText    Non-null when the inline edit field is open (replaces the chips with an
 *                    [OutlinedTextField]; the primary chip relabels to "confirm"). Null = chips.
 * @param batchItems  Non-null for the agent-batch choice: the numbered list of proposed actions
 *                    rendered inside the prompt bubble (destructive lines error-tinted). Null for
 *                    single-action / ambiguous-match choices.
 */
data class PendingChoice(
    val choice: ChatChoice,
    val executingId: String? = null,
    val executingLabel: String? = null,
    val editText: String? = null,
    val batchItems: List<AgentPlanItem>? = null,
)

/**
 * One line in the agent-batch choice prompt.
 *
 * @param text         Human-readable description of the action (from [ToolCallPreviewRenderer]).
 * @param isDestructive When true (e.g. delete_item), the line is tinted red / prefixed with a
 *                      warning icon so the user can spot irreversible operations at a glance.
 */
data class AgentPlanItem(val text: String, val isDestructive: Boolean = false)

// ---------------------------------------------------------------------------
// Intent
// ---------------------------------------------------------------------------

sealed interface ChatScreenIntent : Intent {
    /** User typed in the input field. */
    data class OnInputChange(val text: String) : ChatScreenIntent

    /**
     * Programmatically seed the input field with [text] (caret moved to the end by the UI).
     * Emitted by App.kt when a home-screen quick-action chip pre-fills the dock input
     * (e.g. LINK → "Create a list from: ", REMIND → "Remind me to "). Does NOT send —
     * the user reviews / completes the phrase, then taps Send. Differs from [OnInputChange]
     * only in intent/audit clarity (both set [ChatScreenState.inputText]); a distinct intent
     * keeps quick-action prefill traceable and avoids overloading the user-typing path.
     */
    data class OnPrefillInput(val text: String) : ChatScreenIntent

    /**
     * Seed the input with [text] and immediately dispatch it (prefill + Send in one step).
     * Used by the PLAN_DAY quick-action ("What should I do today?") so the answer appears in
     * the dock without the user retyping. Setting state then sending is safe because state
     * updates are synchronous and [handleSend] reads the freshly-set [ChatScreenState.inputText].
     *
     * @param forceAgent When true, the message bypasses Layer 1/2 classification and goes
     *                   straight to the reasoning agent ([runAgentTurn], Layer 3). Used by the
     *                   checklist-detail reasoning chips (What's missing? / Summary / Add items):
     *                   their intent is already known to be a free-form question about the open
     *                   checklist, so classifying them is not just wasteful but actively harmful —
     *                   Layer 1/2 mis-route them to FindItems ("Nothing matches") or Unknown.
     *                   Default false preserves the existing PLAN_DAY behaviour (normal classify).
     */
    data class OnPrefillAndSend(
        val text: String,
        val forceAgent: Boolean = false,
    ) : ChatScreenIntent

    /** User tapped Send (or IME action) with non-blank input. */
    data object OnSendClick : ChatScreenIntent

    /** User tapped the "?" pricing help icon in the top bar. */
    data object OnHelpClick : ChatScreenIntent

    /** User dismissed the pricing help bottom sheet. */
    data object OnHelpDismiss : ChatScreenIntent

    /** User tapped the "?" features help icon in the input row leading position. */
    data object OnFeaturesHelpClick : ChatScreenIntent

    /** User dismissed the features help bottom sheet. */
    data object OnFeaturesHelpDismiss : ChatScreenIntent

    /**
     * User tapped a choice chip in the [AiChoiceResponse] block. [optionId] is the id of the
     * tapped [ChoiceOption] (either one of [ChatChoice.options] or [ChatChoice.escape]). The
     * ViewModel resolves it to a [ChoiceAction] and executes immediately (Execute / ExecuteAll /
     * FreeForm / Edit / Dismiss).
     */
    data class OnChoiceSelected(val optionId: String) : ChatScreenIntent

    /**
     * User dismissed the pending choice (escape chip, or back gesture). Clears the choice and
     * emits a visible response — silent dismiss is FORBIDDEN (CLAUDE.md). For an agent-batch
     * choice this also resolves the suspended agent decision with `false` (declined).
     */
    data object OnChoiceDismissed : ChatScreenIntent

    /** User edited the text inside the inline edit field of the pending choice. */
    data class OnChoiceEditChange(val text: String) : ChatScreenIntent

    /**
     * User confirmed the inline edit (tapped the relabelled primary chip). Applies the edited
     * text to the choice's Execute tool call and dispatches it. A blank edit shows a hint
     * snackbar instead of silently dropping (CLAUDE.md silent-skip guard).
     */
    data object OnChoiceEditConfirmed : ChatScreenIntent

    /**
     * ChatRoute round-trip for localised assistant messages: ViewModel emits
     * [ChatScreenSideEffect.ShowAssistantMessage] with a string-resource key,
     * ChatRoute resolves it via stringResource() in Composable scope, then
     * sends it back here so the message lands in the chat history.
     * [linkedChecklistId] is preserved through the round-trip so the bubble
     * can show an "Open checklist" button for successful write-intent outcomes.
     * [askAiForText] is preserved so the bubble can show an "Ask AI" button
     * for Unknown-intent responses that should offer Layer 3 escalation.
     */
    data class AppendAssistantMessage(
        val text: String,
        val linkedChecklistId: Long? = null,
        val askAiForText: String? = null,
    ) : ChatScreenIntent

    /** User tapped the back / navigation icon. */
    data object OnBackClick : ChatScreenIntent

    /** User tapped the settings gear icon in the top bar. */
    data object OnSettingsClick : ChatScreenIntent

    /** User dismissed the chat settings bottom sheet. */
    data object OnSettingsDismiss : ChatScreenIntent

    /** User toggled the "Deep Thinking" switch in the settings sheet. */
    data class OnDeepThinkingToggle(val enabled: Boolean) : ChatScreenIntent

    data object OnClearChat : ChatScreenIntent

    /** User tapped the feedback icon on an assistant bubble — opens the feedback sheet. */
    data class OnFeedbackOpen(val message: ChatMessage) : ChatScreenIntent

    /** User tapped thumb-up on an assistant bubble — fire-and-forget analytics, no UI. */
    data class OnThumbUpClick(val message: ChatMessage) : ChatScreenIntent

    /** User is typing in the feedback text field. */
    data class OnFeedbackTextChange(val text: String) : ChatScreenIntent

    /** User tapped Submit in the feedback sheet. */
    data object OnFeedbackSubmit : ChatScreenIntent

    /** User dismissed the feedback sheet (drag, scrim tap, or Cancel button). */
    data object OnFeedbackDismiss : ChatScreenIntent

    /** User tapped the "Open checklist" deeplink button on an assistant bubble. */
    data class OnOpenChecklist(val checklistId: Long) : ChatScreenIntent

    /**
     * User tapped the "Ask AI" button on an Unknown-intent assistant bubble.
     * Escalates [text] (the original user input) to Layer 3 via [completeFreeForm].
     * This is an explicit opt-in to spend 3 credits — we never auto-burn on Unknown.
     */
    data class OnAskAiFallback(val text: String) : ChatScreenIntent

    // ── Attachment intents (Phase 1: VM domain logic; picker UI lives in Phase 3) ──

    /**
     * User tapped an attachment-type button (Image / PDF / Text / Audio).
     * ViewModel sets [ChatScreenState.attachmentPickerType] as a trigger-flag
     * (trigger-flag pattern from item-attachments solution): the UI's LaunchedEffect
     * watches this field, launches the platform picker, then sends [OnAttachmentPickerTriggered]
     * to reset the flag.
     */
    data class OnPickAttachment(val source: AttachmentSource) : ChatScreenIntent

    /**
     * Sent by the UI after it has consumed [ChatScreenState.attachmentPickerType] to launch
     * the platform picker. Resets the trigger-flag so a second LaunchedEffect doesn't re-fire.
     */
    data object OnAttachmentPickerTriggered : ChatScreenIntent

    /**
     * The platform picker returned a selected file. ViewModel appends it to
     * [ChatScreenState.pendingAttachments] (up to the per-message quota).
     */
    data class OnAttachmentPicked(val attachment: ChatAttachment) : ChatScreenIntent

    /** User tapped the × chip on a pending attachment. Removes it from the list. */
    data class OnRemoveAttachment(val sourcePath: String) : ChatScreenIntent

    /**
     * User pressed-and-held the mic button (voice recording start).
     * ViewModel flips [ChatScreenState.isRecording] to true.
     * Phase 3 wires actual AudioRecorder; Phase 1 only bookkeeps state.
     */
    data object OnVoiceRecordingStarted : ChatScreenIntent

    /**
     * User released the mic button (voice recording stop).
     * ViewModel flips [ChatScreenState.isRecording] to false and, when [recordingPath]
     * is non-null, sends the file to the transcription Cloud Function
     * ([AiChatRepository.transcribeAudio]) and appends the resulting text to the
     * input field so the user can review and edit before sending.
     *
     * [recordingPath] is null when recording was cancelled (no-op per silent-skip guard).
     * A null path → user cancelled → emit ShowSnackbar("chat_recording_cancelled").
     */
    data class OnVoiceRecordingStopped(
        val recordingPath: String?,
        val mimeType: String = "audio/m4a",
    ) : ChatScreenIntent

    /**
     * Seed the chat session with the checklist context that triggered the sheet.
     *
     * Called by App.kt when the user opens the sheet from [ChecklistDetailScreen]
     * with a specific checklist in focus. The ViewModel stores [checklistId] as the
     * "active context" so Layer-1/Layer-2/Layer-3 requests can default to this
     * checklist instead of requiring the user to name it explicitly.
     *
     * Pass `null` to clear the context (sheet opened from [MainScreen] with no focus).
     */
    data class OnSetContextChecklist(val checklistId: Long?) : ChatScreenIntent

    /**
     * The chat surface became visible — fire once per open (analytics funnel entry).
     *
     * Emitted from the composition root of each entry point: [ChatRoute] (full-screen,
     * `source="screen"`) and the inline dock in `App.kt` (`source="dock"`). NOT fired from
     * the ViewModel's `init`: the ViewModel is an App-scoped singleton, so `init` runs once
     * per process and would under-count opens. The handler emits
     * [AnalyticsEvents.Chat.OPENED] + `screenView(CHAT)`.
     *
     * @param source Where the chat was opened from ("screen", "dock", or a future deeplink tag).
     */
    data class OnChatOpened(val source: String) : ChatScreenIntent
}

// ---------------------------------------------------------------------------
// SideEffect
// ---------------------------------------------------------------------------

sealed interface ChatScreenSideEffect : SideEffect {
    /** Show a snackbar with a localised message. [messageKey] maps to a string resource key. */
    data class ShowSnackbar(val messageKey: String) : ChatScreenSideEffect

    /**
     * Ask ChatRoute to resolve a localised assistant message and append it to the chat.
     * Avoids hardcoded EN strings in ViewModel — Composable scope owns string-resource lookup.
     * Optional [args] support `%1$s` / `%2$s` etc. placeholder substitution.
     * [linkedChecklistId] is forwarded to [ChatMessage] so the bubble can show an "Open
     * checklist" deeplink button for successful write-intent dispatch outcomes.
     * [askAiForText] is forwarded when the assistant message is an Unknown-intent response;
     * the bubble shows an "Ask AI" TextButton that escalates to Layer 3 explicitly.
     */
    data class ShowAssistantMessage(
        val messageKey: String,
        val args: List<String> = emptyList(),
        val linkedChecklistId: Long? = null,
        val askAiForText: String? = null,
    ) : ChatScreenSideEffect

    /** Navigate back (handled by the host NavController). */
    data object NavigateBack : ChatScreenSideEffect

    /** Navigate to [ChecklistDetail] for the given checklist (triggered by "Open checklist" button). */
    data class NavigateToChecklist(val checklistId: Long) : ChatScreenSideEffect

    // ── Attachment side-effects (Phase 1 contract; UI wiring lives in Phase 3) ──

    /**
     * Request the RECORD_AUDIO Android permission before starting voice recording.
     * Emitted by [OnVoiceRecordingStarted] when the ViewModel cannot confirm permission
     * has already been granted. Phase 3 observes this in ChatRoute and calls
     * rememberPermissionState(...).launchPermissionRequest().
     */
    data object RequestRecordAudioPermission : ChatScreenSideEffect

    /**
     * Ask the UI to open a platform file picker for the given [source] type.
     * Emitted as an alternative to the trigger-flag approach when the caller
     * prefers SideEffect-driven picker opening (e.g. in test harnesses).
     * Phase 3 implementation choice: either this SideEffect OR trigger-flag; only one path needed.
     */
    data class OpenFilePicker(val source: AttachmentSource) : ChatScreenSideEffect

    /**
     * Navigate to the paywall (triggered by [RequiresPremium] dispatch outcome for
     * CreateChecklistFromAttachment when the free attachment/checklist quota is exceeded).
     */
    data object NavigateToPaywall : ChatScreenSideEffect
}
