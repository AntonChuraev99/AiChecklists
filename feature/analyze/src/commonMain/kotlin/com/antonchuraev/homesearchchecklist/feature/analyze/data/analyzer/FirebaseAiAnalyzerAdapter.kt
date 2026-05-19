package com.antonchuraev.homesearchchecklist.feature.analyze.data.analyzer

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.analyzer.AiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import kotlin.reflect.KClass

/**
 * Production [AiAnalyzer] adapter that delegates to [AnalyzeRepository].
 *
 * [AnalyzeRepository] wraps [FirebaseAiServiceImpl] — the same Cloud Function path
 * used by Create via AI / Fill via AI. Server-side credit accounting, prompt
 * assembly, and reasoning flow through there. This adapter exposes that path
 * under the [AiAnalyzer] domain abstraction so callers like
 * [ToolCallDispatcherImpl.handleCreateChecklistFromAttachment] can depend on the
 * interface without reaching for the repository directly.
 */
class FirebaseAiAnalyzerAdapter(
    private val analyzeRepository: AnalyzeRepository,
) : AiAnalyzer {

    override suspend fun analyze(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist?,
    ): Result<AnalyzeResult> = analyzeRepository.analyzeData(inputData, targetChecklist)

    override suspend fun isAvailable(): Boolean = analyzeRepository.isAnalyzerAvailable()

    override fun getSupportedInputTypes(): Set<KClass<out AnalyzeInputData>> = setOf(
        AnalyzeInputData.Photo::class,
        AnalyzeInputData.PdfDocument::class,
        AnalyzeInputData.TextFile::class,
        AnalyzeInputData.WebLink::class,
        AnalyzeInputData.RawText::class,
        AnalyzeInputData.Audio::class,
    )
}
