package com.antonchuraev.homesearchchecklist.feature.analyze.domain.model

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Result of AI analysis containing suggested checklist items
 * that were extracted from the input data.
 */
@Serializable
data class AnalyzeResult(
    /**
     * List of checklist items suggested by AI based on analyzed content
     */
    val suggestedItems: List<ChecklistItem>,

    /**
     * Confidence score of the analysis (0.0 - 1.0)
     */
    val confidence: Float = 0.0f,

    /**
     * Optional summary of what was found in the analyzed content
     */
    val summary: String? = null,

    /**
     * Any warnings or notes from the analysis
     */
    val warnings: List<String> = emptyList(),

    /**
     * Raw fill items with separate text/checked/note fields (for fill-default mode).
     * Only populated in fill mode. Not serialized.
     */
    @Transient
    val fillItems: List<ChecklistFillItem> = emptyList()
)

/**
 * Sealed class representing the state of analysis operation
 */
sealed interface AnalyzeState {
    data object Idle : AnalyzeState
    data object Loading : AnalyzeState
    data class Success(val result: AnalyzeResult) : AnalyzeState
    data class Error(val message: String) : AnalyzeState
}
