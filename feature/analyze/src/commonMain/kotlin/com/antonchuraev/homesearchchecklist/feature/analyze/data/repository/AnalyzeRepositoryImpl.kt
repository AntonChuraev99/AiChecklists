package com.antonchuraev.homesearchchecklist.feature.analyze.data.repository

import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.AiInputType
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiService
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.analyze.data.util.FileReader
import kotlinx.coroutines.flow.first
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class AnalyzeRepositoryImpl(
    private val firebaseAiService: FirebaseAiService,
    private val checklistRepository: ChecklistRepository,
    private val userDataRepository: UserDataRepository
) : AnalyzeRepository {

    override suspend fun analyzeData(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist?
    ): Result<AnalyzeResult> {
        val userData = userDataRepository.getUserData()
        val userId = userData.userId
        val isPremium = userData.isPremium

        // Convert input data to API format
        val (inputType, inputDataString) = convertInputData(inputData)

        return if (targetChecklist != null) {
            // Use analyze_and_fill_checklist for existing checklists
            firebaseAiService.analyzeAndFillChecklist(
                userId = userId,
                isPremium = isPremium,
                checklist = targetChecklist,
                inputType = inputType,
                inputData = inputDataString
            ).fold(
                onSuccess = { response ->
                    if (response.success && response.data != null) {
                        // Update local free generations count if provided
                        val newGenerations = response.data.aiCredits
                        if (newGenerations >= 0) {
                            userDataRepository.update(userData.copy(aiCredits = newGenerations))
                        }

                        // Build the template items first so each fill item can carry a stable
                        // link to its template counterpart (index-aligned, same source list).
                        val filledItems = response.data.filledItems.map { filled ->
                            ChecklistItem(
                                text = filled.note?.let { "${filled.text} - $it" } ?: filled.text,
                                checked = filled.checked
                            )
                        }
                        val fillItems = response.data.filledItems.mapIndexed { index, filled ->
                            ChecklistFillItem(
                                text = filled.text,
                                checked = filled.checked,
                                note = filled.note?.takeIf { it.isNotBlank() },
                                templateItemId = filledItems[index].id,
                            )
                        }
                        Result.success(
                            AnalyzeResult(
                                suggestedItems = filledItems,
                                confidence = response.data.confidence,
                                summary = response.data.summary,
                                fillItems = fillItems
                            )
                        )
                    } else {
                        Result.failure(Exception(response.error ?: "Analysis failed"))
                    }
                },
                onFailure = { Result.failure(it) }
            )
        } else {
            // Use generate_checklist for new checklists
            firebaseAiService.generateChecklist(
                userId = userId,
                isPremium = isPremium,
                prompt = getPromptFromInput(inputData),
                inputType = inputType,
                inputData = inputDataString
            ).fold(
                onSuccess = { response ->
                    if (response.success && response.data != null) {
                        // Update local free generations count if provided
                        val newGenerations = response.data.aiCredits
                        if (newGenerations >= 0) {
                            userDataRepository.update(userData.copy(aiCredits = newGenerations))
                        }

                        Result.success(
                            AnalyzeResult(
                                suggestedItems = response.data.items,
                                confidence = response.data.confidence,
                                summary = response.data.summary,
                                suggestedName = response.data.checklistName,
                                hasFolders = response.data.hasFolders
                            )
                        )
                    } else {
                        Result.failure(Exception(response.error ?: "Generation failed"))
                    }
                },
                onFailure = { Result.failure(it) }
            )
        }
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
            // Use suggestedItems verbatim so the AI-detected folder structure (parentId/type)
            // reaches the persisted template. foldersEnabled mirrors the parsed flag so the
            // detail screen renders in folder mode. The default fill is created automatically
            // by addChecklist with a row (linked via templateItemId) for EVERY node — folders
            // included — so folder-level reminders/progress (Phase 5) resolve a fill row.
            val newChecklist = Checklist(
                name = name,
                items = result.suggestedItems,
                foldersEnabled = result.hasFolders
            )
            val checklistId = checklistRepository.addChecklist(newChecklist)
            Result.success(newChecklist.copy(id = checklistId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFillFromResult(
        checklistId: Long,
        fillName: String,
        result: AnalyzeResult
    ): Result<Long> {
        return try {
            // Convert checklist items to fill items with checked state from AI analysis.
            // Link each fill item to its template counterpart via the stable ChecklistItem.id.
            val fillItems = result.suggestedItems.map { item ->
                ChecklistFillItem(
                    text = item.text,
                    checked = item.checked,
                    note = null,
                    templateItemId = item.id,
                )
            }

            val newFill = ChecklistFill(
                checklistId = checklistId,
                name = fillName,
                items = fillItems,
                createdAt = currentTimeMillis()
            )

            val fillId = checklistRepository.addFill(newFill)
            Result.success(fillId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isAnalyzerAvailable(): Boolean {
        // Firebase AI service is always available (server-side)
        return true
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun convertInputData(inputData: AnalyzeInputData): Pair<AiInputType, String> {
        return when (inputData) {
            is AnalyzeInputData.Photo -> {
                val bytes = FileReader.readBytes(inputData.filePath)
                if (bytes != null) {
                    AiInputType.IMAGE_BASE64 to Base64.encode(bytes)
                } else {
                    AiInputType.TEXT to "Failed to read image: ${inputData.filePath}"
                }
            }
            is AnalyzeInputData.PdfDocument -> {
                // PDFs would need to be extracted as text or encoded
                AiInputType.TEXT to "PDF content from: ${inputData.fileName}"
            }
            is AnalyzeInputData.TextFile -> {
                val content = inputData.content ?: FileReader.readText(inputData.filePath)
                AiInputType.TEXT to (content ?: "Failed to read text file: ${inputData.filePath}")
            }
            is AnalyzeInputData.WebLink -> {
                AiInputType.URL to inputData.url
            }
            is AnalyzeInputData.RawText -> {
                AiInputType.TEXT to inputData.text
            }
            is AnalyzeInputData.Audio -> {
                val bytes = FileReader.readBytes(inputData.filePath)
                if (bytes != null) {
                    AiInputType.AUDIO_BASE64 to Base64.encode(bytes)
                } else {
                    AiInputType.TEXT to "Failed to read audio: ${inputData.filePath}"
                }
            }
        }
    }

    private fun getPromptFromInput(inputData: AnalyzeInputData): String {
        return when (inputData) {
            is AnalyzeInputData.Photo -> "Create a checklist based on this image"
            is AnalyzeInputData.PdfDocument -> "Create a checklist from this document: ${inputData.fileName}"
            is AnalyzeInputData.TextFile -> "Create a checklist from this text file"
            is AnalyzeInputData.WebLink -> "Create a checklist from the content at: ${inputData.url}"
            is AnalyzeInputData.RawText -> "Create a checklist based on: ${inputData.text}"
            is AnalyzeInputData.Audio -> "Create a checklist from this voice recording"
        }
    }
}
