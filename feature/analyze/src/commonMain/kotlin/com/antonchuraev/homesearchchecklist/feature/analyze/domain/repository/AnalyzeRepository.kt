package com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.PreparedAnalyzeInput
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
     * Same as [analyzeData] with the server idempotency key: every attempt of one background job
     * passes the SAME [requestId] so the server reserves credits once. Null = a fresh key per call
     * (the synchronous callers). The default ignores the key so test doubles keep compiling.
     */
    suspend fun analyzeData(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist?,
        requestId: String?,
    ): Result<AnalyzeResult> = analyzeData(inputData, targetChecklist)

    /**
     * Analysis of material already in memory (a background job's captured payload). The default
     * routes text and links through [analyzeData] so test doubles that only fake [analyzeData] keep
     * working; file bytes need the real implementation.
     */
    suspend fun analyzePrepared(
        input: PreparedAnalyzeInput,
        targetChecklist: Checklist?,
        requestId: String?,
    ): Result<AnalyzeResult> = when (input) {
        is PreparedAnalyzeInput.Text -> analyzeData(AnalyzeInputData.RawText(input.text), targetChecklist, requestId)
        is PreparedAnalyzeInput.Url -> analyzeData(AnalyzeInputData.WebLink(input.url), targetChecklist, requestId)
        is PreparedAnalyzeInput.Image,
        is PreparedAnalyzeInput.Pdf,
        is PreparedAnalyzeInput.Audio,
        -> Result.failure(UnsupportedOperationException("file bytes need AnalyzeRepositoryImpl"))
    }

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
