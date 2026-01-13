package com.antonchuraev.homesearchchecklist.feature.analyze.data.repository

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.analyzer.AiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.first

class AnalyzeRepositoryImpl(
    private val aiAnalyzer: AiAnalyzer,
    private val checklistRepository: ChecklistRepository
) : AnalyzeRepository {

    override suspend fun analyzeData(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist?
    ): Result<AnalyzeResult> {
        return aiAnalyzer.analyze(inputData, targetChecklist)
    }

    override suspend fun applyToChecklist(
        checklistId: Long,
        result: AnalyzeResult
    ): Result<Checklist> {
        return try {
            // Get the existing checklist
            val checklists = checklistRepository.checklists.first()
            val existingChecklist = checklists.find { it.id == checklistId }
                ?: return Result.failure(IllegalArgumentException("Checklist not found: $checklistId"))

            // Merge existing items with new suggested items
            val mergedItems = existingChecklist.items + result.suggestedItems

            // Create updated checklist
            val updatedChecklist = existingChecklist.copy(items = mergedItems)

            // Save updated checklist (uses REPLACE strategy)
            checklistRepository.addChecklist(updatedChecklist)

            Result.success(updatedChecklist)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createChecklistFromResult(
        name: String,
        result: AnalyzeResult
    ): Result<Checklist> {
        return try {
            val newChecklist = Checklist(
                name = name,
                items = result.suggestedItems
            )
            checklistRepository.addChecklist(newChecklist)
            Result.success(newChecklist)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isAnalyzerAvailable(): Boolean {
        return aiAnalyzer.isAvailable()
    }
}
