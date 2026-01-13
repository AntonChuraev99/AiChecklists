package com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist

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
     * Checks if analyzer is available.
     */
    suspend fun isAnalyzerAvailable(): Boolean
}
