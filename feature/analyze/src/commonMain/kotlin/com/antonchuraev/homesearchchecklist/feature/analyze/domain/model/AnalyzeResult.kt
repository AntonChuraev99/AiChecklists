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
     * AI-suggested name for the generated checklist (the `checklist_name` field of the
     * `generate_checklist` response). Null/blank in fill mode and whenever the server omitted it;
     * presentation falls back to the localized default name in that case. Carrying it here is what
     * lets each generated checklist keep its own title instead of all sharing the generic default.
     */
    val suggestedName: String? = null,

    /**
     * Any warnings or notes from the analysis
     */
    val warnings: List<String> = emptyList(),

    /**
     * True when [suggestedItems] contains at least one folder node (a nested structure was
     * detected in the AI response). Presentation creates the new checklist with
     * `foldersEnabled = true` in that case. Always false in fill mode and for flat (legacy)
     * responses. Equivalent to `suggestedItems.any { it.isFolder }`; kept as an explicit field
     * so the value is decided once at parse time alongside the flattening logic.
     */
    val hasFolders: Boolean = false,

    /**
     * Raw fill items with separate text/checked/note fields (for fill-default mode).
     * Only populated in fill mode. Not serialized.
     */
    @Transient
    val fillItems: List<ChecklistFillItem> = emptyList(),

    /**
     * Server-assigned AI-model A/B arm carried by the `analyze_and_fill_checklist` /
     * `generate_checklist` response (`model_variant` / `model_id` / `ai_flow`). Null when the
     * experiment is off or an older server didn't send them. Presentation mirrors these into the
     * `ai_analyze_completed` event dimensions and the shared AiModelExperimentTracker. Not
     * serialized — transient runtime attribution metadata, never persisted with the result.
     */
    @Transient
    val modelVariant: String? = null,
    @Transient
    val modelId: String? = null,
    @Transient
    val aiFlow: String? = null,
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
