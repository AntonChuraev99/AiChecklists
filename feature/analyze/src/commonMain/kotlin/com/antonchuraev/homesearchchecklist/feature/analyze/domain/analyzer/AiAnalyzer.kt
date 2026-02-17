package com.antonchuraev.homesearchchecklist.feature.analyze.domain.analyzer

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import kotlin.reflect.KClass

/**
 * Interface for AI-powered analysis of property data.
 * This interface defines the contract for analyzing various input types
 * and extracting relevant information to fill checklist items.
 *
 * Production implementation: [GeminiAiAnalyzer]
 */
interface AiAnalyzer {

    /**
     * Analyzes the provided input data and returns suggested checklist items.
     *
     * @param inputData The input data to analyze (photo, PDF, text, link)
     * @param targetChecklist Optional existing checklist to provide context for analysis
     * @return AnalyzeResult containing suggested items and analysis metadata
     */
    suspend fun analyze(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist? = null
    ): Result<AnalyzeResult>

    /**
     * Checks if the analyzer is available and properly configured.
     * @return true if the analyzer can process requests
     */
    suspend fun isAvailable(): Boolean

    /**
     * Returns supported input types by this analyzer implementation.
     */
    fun getSupportedInputTypes(): Set<KClass<out AnalyzeInputData>>
}
