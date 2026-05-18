package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
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
 */
data class ChatScreenState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val pendingPreview: PendingPreview? = null,
    val creditBalance: Int = 0,
    val showPricingSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val deepThinkingEnabled: Boolean = false,
    val isProcessing: Boolean = false,
    val feedbackTarget: ChatMessage? = null,
    val feedbackText: String = "",
    val isSubmittingFeedback: Boolean = false,
) : State

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
 */
data class PendingPreview(
    val toolCall: ToolCall,
    val humanReadable: String,
    val targetChecklistHint: String? = null,
    val editableItemText: String = "",
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

    /** User edited the item text inside the preview card. */
    data class OnPreviewItemTextChange(val text: String) : ChatScreenIntent

    /**
     * ChatRoute round-trip for localised assistant messages: ViewModel emits
     * [ChatScreenSideEffect.ShowAssistantMessage] with a string-resource key,
     * ChatRoute resolves it via stringResource() in Composable scope, then
     * sends it back here so the message lands in the chat history.
     * [linkedChecklistId] is preserved through the round-trip so the bubble
     * can show an "Open checklist" button for successful write-intent outcomes.
     */
    data class AppendAssistantMessage(
        val text: String,
        val linkedChecklistId: Long? = null,
    ) : ChatScreenIntent

    /** User approved the pending write-intent preview. */
    data object OnPreviewApply : ChatScreenIntent

    /** User cancelled the pending write-intent preview. */
    data object OnPreviewCancel : ChatScreenIntent

    /** User tapped the back / navigation icon. */
    data object OnBackClick : ChatScreenIntent

    /** User tapped the settings gear icon in the top bar. */
    data object OnSettingsClick : ChatScreenIntent

    /** User dismissed the chat settings bottom sheet. */
    data object OnSettingsDismiss : ChatScreenIntent

    /** User toggled the "Deep Thinking" switch in the settings sheet. */
    data class OnDeepThinkingToggle(val enabled: Boolean) : ChatScreenIntent

    /** User tapped the feedback icon on an assistant bubble — opens the feedback sheet. */
    data class OnFeedbackOpen(val message: ChatMessage) : ChatScreenIntent

    /** User is typing in the feedback text field. */
    data class OnFeedbackTextChange(val text: String) : ChatScreenIntent

    /** User tapped Submit in the feedback sheet. */
    data object OnFeedbackSubmit : ChatScreenIntent

    /** User dismissed the feedback sheet (drag, scrim tap, or Cancel button). */
    data object OnFeedbackDismiss : ChatScreenIntent

    /** User tapped the "Open checklist" deeplink button on an assistant bubble. */
    data class OnOpenChecklist(val checklistId: Long) : ChatScreenIntent
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
     */
    data class ShowAssistantMessage(
        val messageKey: String,
        val args: List<String> = emptyList(),
        val linkedChecklistId: Long? = null,
    ) : ChatScreenSideEffect

    /** Navigate back (handled by the host NavController). */
    data object NavigateBack : ChatScreenSideEffect

    /** Navigate to [ChecklistDetail] for the given checklist (triggered by "Open checklist" button). */
    data class NavigateToChecklist(val checklistId: Long) : ChatScreenSideEffect
}
