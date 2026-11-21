package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist

data class AnalyzeScreenState(
    val selectedInputType: InputDataType? = null,
    val inputText: String = "",
    val inputUrl: String = "",
    val selectedFilePath: String? = null,
    val selectedFileName: String? = null,
    val isAnalyzing: Boolean = false,
    val analyzeResult: AnalyzeResult? = null,
    val error: String? = null,
    val checklistName: String = "",
    val availableChecklists: List<Checklist> = emptyList(),
    val selectedChecklistId: Long? = null,
    val showResultDialog: Boolean = false,
    val aiCredits: Int = 0,
    /** Server's `action_cost` (`get_credits_info`); null until fetched — never a client constant. */
    val aiActionCost: Int? = null,
    val isPremium: Boolean = false,
    // Fill mode - when filling an existing checklist
    val isFillMode: Boolean = false,
    val fillDefault: Boolean = false,
    val targetChecklist: Checklist? = null,
    val fillName: String = "",
    val isSavingFill: Boolean = false,
    // Voice recording
    val isRecording: Boolean = false,
    val recordedAudioPath: String? = null,
    val recordedAudioDuration: Long = 0L,
    // ── Background analysis (FEATURE_SPEC analyze-reliability-background-2026-11-20) ──
    /** Phase of this visit's job. IMPLEMENT derives [isAnalyzing] from it and stops latching it. */
    val jobPhase: AnalyzeJobPhase = AnalyzeJobPhase.Editing,
    /** Other analyses still running (another door, a previous visit): "1 analysis in progress" strip. */
    val otherJobsInProgress: Int = 0,
    /** Credits held by running jobs, subtracted from [aiCredits] for the button gate. */
    val creditsReservedByRunningJobs: Int = 0,
    /** Inline field error under the link field (invalid URL) — not the modal [error]. */
    val inputError: String? = null,
    /** One-shot: the screen runs the platform POST_NOTIFICATIONS request, then reports the result. */
    val askNotificationPermission: Boolean = false,
    /** Owner decision 1: how many analyses may run at once ("You already have %1$d analyses running"). */
    val maxParallelJobs: Int = 3,
    /**
     * The [error] text that is a HINT, not a failure (the 4th parallel start: "You already have 3
     * analyses running"). Kept as the text itself so any later `error` write — another message or
     * null — ends the hint without every writer having to remember to clear a flag. Read [errorIsHint].
     */
    val hintError: String? = null,
) : State {
    /** True when [error] is a hint: the dialog shows it without the "Error" title. */
    val errorIsHint: Boolean
        get() = error != null && error == hintError

    /**
     * Credit gate of the Analyze button (all users, premium included, need the credits). Unknown cost
     * (not fetched yet / fetch failed) is NOT gated — the server stays the gate (402).
     */
    val hasEnoughCredits: Boolean
        get() = aiActionCost?.let { cost -> aiCredits - creditsReservedByRunningJobs >= cost } ?: true
}

sealed interface AnalyzeScreenIntent : Intent {
    data object OnBackClick : AnalyzeScreenIntent

    // Input type selection
    data class OnInputTypeSelected(val type: InputDataType) : AnalyzeScreenIntent

    // Text/URL input
    data class OnTextInputChanged(val text: String) : AnalyzeScreenIntent
    data class OnUrlInputChanged(val url: String) : AnalyzeScreenIntent

    // File selection (photo, PDF, text file)
    data class OnFileSelected(val filePath: String, val fileName: String) : AnalyzeScreenIntent

    // Voice recording
    data object OnStartRecording : AnalyzeScreenIntent
    data object OnStopRecording : AnalyzeScreenIntent
    data class OnRecordingComplete(val filePath: String, val durationMs: Long) : AnalyzeScreenIntent
    data class OnRecordingError(val error: String) : AnalyzeScreenIntent
    data object OnDeleteRecording : AnalyzeScreenIntent

    // Checklist selection
    data class OnChecklistSelected(val checklistId: Long?) : AnalyzeScreenIntent
    data class OnChecklistNameChanged(val name: String) : AnalyzeScreenIntent

    // Fill mode
    data class OnFillNameChanged(val name: String) : AnalyzeScreenIntent
    data object OnCreateFillClick : AnalyzeScreenIntent

    // Actions
    data object OnAnalyzeClick : AnalyzeScreenIntent
    data object OnApplyToChecklistClick : AnalyzeScreenIntent
    data object OnCreateNewChecklistClick : AnalyzeScreenIntent
    data object OnDismissResult : AnalyzeScreenIntent
    data object OnDismissError : AnalyzeScreenIntent
    data object OnClearInput : AnalyzeScreenIntent
}
