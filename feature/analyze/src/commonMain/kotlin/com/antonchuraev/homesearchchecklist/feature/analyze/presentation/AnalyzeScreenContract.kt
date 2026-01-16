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
    val aiActionCost: Int = 30,
    val isPremium: Boolean = false,
    // Fill mode - when filling an existing checklist
    val isFillMode: Boolean = false,
    val targetChecklist: Checklist? = null,
    val fillName: String = "",
    val isSavingFill: Boolean = false,
    // Voice recording
    val isRecording: Boolean = false,
    val recordedAudioPath: String? = null,
    val recordedAudioDuration: Long = 0L
) : State

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
