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
 * @param creditBalance  User's remaining AI credits (0 in Phase A, Layer 1 = always free).
 * @param showPricingSheet  Whether the pricing help bottom sheet is visible.
 * @param isProcessing  True while the router is classifying / dispatching (disables Send).
 */
data class ChatScreenState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val pendingPreview: PendingPreview? = null,
    val creditBalance: Int = 0,
    val showPricingSheet: Boolean = false,
    val isProcessing: Boolean = false,
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
     */
    data class AppendAssistantMessage(val text: String) : ChatScreenIntent

    /** User approved the pending write-intent preview. */
    data object OnPreviewApply : ChatScreenIntent

    /** User cancelled the pending write-intent preview. */
    data object OnPreviewCancel : ChatScreenIntent

    /** User tapped the back / navigation icon. */
    data object OnBackClick : ChatScreenIntent
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
     */
    data class ShowAssistantMessage(
        val messageKey: String,
        val args: List<String> = emptyList(),
    ) : ChatScreenSideEffect

    /** Navigate back (handled by the host NavController). */
    data object NavigateBack : ChatScreenSideEffect
}
