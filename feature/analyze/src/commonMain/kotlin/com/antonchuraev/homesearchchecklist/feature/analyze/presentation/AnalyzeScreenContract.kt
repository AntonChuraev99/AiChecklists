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
    val showResultDialog: Boolean = false
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

    // Checklist selection
    data class OnChecklistSelected(val checklistId: Long?) : AnalyzeScreenIntent
    data class OnChecklistNameChanged(val name: String) : AnalyzeScreenIntent

    // Actions
    data object OnAnalyzeClick : AnalyzeScreenIntent
    data object OnApplyToChecklistClick : AnalyzeScreenIntent
    data object OnCreateNewChecklistClick : AnalyzeScreenIntent
    data object OnDismissResult : AnalyzeScreenIntent
    data object OnDismissError : AnalyzeScreenIntent
    data object OnClearInput : AnalyzeScreenIntent
}
