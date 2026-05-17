package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * Classified user intent from a chat message.
 *
 * Each variant is handled by a corresponding [ToolCall] that encodes
 * the extracted parameters for execution. [Unknown] is the fallback
 * when confidence < 0.6 — the UI must emit a non-silent snackbar for it.
 */
sealed interface ChatIntent {
    data object CreateItem : ChatIntent
    data object SetReminder : ChatIntent
    data object FindItems : ChatIntent
    data object DeleteItem : ChatIntent
    data object CompleteItem : ChatIntent
    data object CreateChecklist : ChatIntent
    data object MoveReminders : ChatIntent

    /**
     * Open-ended / conversational input that Layer 1 and Layer 2 cannot classify
     * into a concrete tool call. Escalated to Layer 3 (full chat completion, 3 credits).
     */
    data object FreeForm : ChatIntent

    /** Raw text preserved for display in snackbar / escalation to Layer 2. */
    data class Unknown(val rawText: String) : ChatIntent
}
