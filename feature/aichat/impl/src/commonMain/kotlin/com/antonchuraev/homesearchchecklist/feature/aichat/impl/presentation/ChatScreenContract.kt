package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

/**
 * Immutable UI state for [ChatScreen].
 *
 * @param messages     Ordered chat history (ascending by timestamp).
 * @param inputText    Current text in the input field.
 * @param pendingPreview  Non-null when a write-intent preview card should be shown.
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
    val pendingPreview: PendingPreview? = null,
    val creditBalance: Int = 0,
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
) : State {
    /** True when the Send button should be active (text entered OR attachments pending). */
    val canSend: Boolean get() = inputText.isNotBlank() || pendingAttachments.isNotEmpty()
}

/**
 * A pending write-intent that has been classified and awaits user approval.
 *
 * @param toolCall              The structured action to execute on [OnPreviewApply].
 * @param humanReadable         Pre-rendered description string (used for AddAssistantMessage fallback).
 * @param targetChecklistHint   Resolved checklist hint extracted from the user phrase
 *                              (e.g. «апки» for «добавь в апки тест»). May be null when
 *                              the user didn't specify a target list.
 * @param editableItemText      The current item text shown in the preview's text field.
 *                              Initially equals the item parsed from user input. The user
 *                              can edit before tapping Apply — final dispatch uses this value.
 * @param originalText          The original raw user input that produced this preview.
 *                              Used by [OnPreviewReject] to re-classify the text in the next layer.
 *                              Empty string for attachment-only previews (reject is hidden in that case).
 * @param sourceLayer           The routing layer that produced this preview.
 *                              Used to decide which layer to escalate to on [OnPreviewReject]:
 *                              Local → Classifier (Layer 2), Classifier → FullChat (Layer 3).
 */
data class PendingPreview(
    val toolCall: ToolCall,
    val humanReadable: String,
    val targetChecklistHint: String? = null,
    val editableItemText: String = "",
    val originalText: String = "",
    val sourceLayer: RoutingLayer = RoutingLayer.Local,
)

// ---------------------------------------------------------------------------
// Intent
// ---------------------------------------------------------------------------

sealed interface ChatScreenIntent : Intent {
    /** User typed in the input field. */
    data class OnInputChange(val text: String) : ChatScreenIntent

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

    /** User edited the item text inside the preview card. */
    data class OnPreviewItemTextChange(val text: String) : ChatScreenIntent

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

    /** User approved the pending write-intent preview. */
    data object OnPreviewApply : ChatScreenIntent

    /** User cancelled the pending write-intent preview. */
    data object OnPreviewCancel : ChatScreenIntent

    /**
     * User tapped the "I meant something else" button on the pending preview.
     *
     * Escalates the original user input to the next pipeline layer:
     * - [RoutingLayer.Local] preview → re-classify with [skipLayer1=true] (Layer 2, 1 credit)
     * - [RoutingLayer.Classifier] preview → escalate directly to Layer 3 via [completeFreeForm] (3 credits)
     *
     * Hidden for [ToolCall.CreateChecklistFromAttachment] (no original text to re-classify).
     */
    data object OnPreviewReject : ChatScreenIntent

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
