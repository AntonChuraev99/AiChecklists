package com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill

/**
 * Repository interface for managing AI analysis operations.
 */
interface AnalyzeRepository {

    /**
     * Analyzes input data and returns suggested checklist items.
     *
     * @param inputData The data to analyze
     * @param targetChecklist Optional checklist for context
     * @return Result containing AnalyzeResult or error
     */
    suspend fun analyzeData(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist? = null
    ): Result<AnalyzeResult>

    /**
     * Applies analysis results to a checklist by adding suggested items.
     *
     * @param checklistId The ID of the checklist to update
     * @param result The analysis result containing items to add
     * @return Result indicating success or failure
     */
    suspend fun applyToChecklist(
        checklistId: Long,
        result: AnalyzeResult
    ): Result<Checklist>

    /**
     * Creates a new checklist from analysis results.
     *
     * @param name Name for the new checklist
     * @param result Analysis result containing items
     * @return Result containing the created checklist
     */
    suspend fun createChecklistFromResult(
        name: String,
        result: AnalyzeResult
    ): Result<Checklist>

    /**
     * Creates a new Fill for a checklist from analysis results.
     * This fills an existing checklist with checked/unchecked items based on AI analysis.
     *
     * @param checklistId The ID of the checklist to fill
     * @param fillName Name for the new fill
     * @param result Analysis result containing items with checked states
     * @return Result containing the fill ID
     */
    suspend fun createFillFromResult(
        checklistId: Long,
        fillName: String,
        result: AnalyzeResult
    ): Result<Long>

    /**
     * Checks if analyzer is available.
     */
    suspend fun isAnalyzerAvailable(): Boolean
}
