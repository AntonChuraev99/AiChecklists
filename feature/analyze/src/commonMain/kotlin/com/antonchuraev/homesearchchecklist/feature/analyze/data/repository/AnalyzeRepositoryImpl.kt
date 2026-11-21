package com.antonchuraev.homesearchchecklist.feature.analyze.data.repository

import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.AiInputType
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.AiServiceResponse
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.PreparedAnalyzeInput
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiService
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiAnalyzeError
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.NEW_CHECKLIST_SEPARATE_COMPLETED
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.analyze.data.util.FileReader
import kotlinx.coroutines.flow.first
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class AnalyzeRepositoryImpl(
    private val firebaseAiService: FirebaseAiService,
    private val checklistRepository: ChecklistRepository,
    private val userDataRepository: UserDataRepository,
    /** File bytes by path; injectable so the PDF / unreadable-file paths are testable without a filesystem. */
    private val readFileBytes: (String) -> ByteArray? = FileReader::readBytes,
    private val readFileText: (String) -> String? = FileReader::readText,
) : AnalyzeRepository {

    override suspend fun analyzeData(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist?
    ): Result<AnalyzeResult> = analyzeData(inputData, targetChecklist, requestId = null)

    override suspend fun analyzeData(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist?,
        requestId: String?,
    ): Result<AnalyzeResult> {
        // Cheap guard first (no file read for a user who cannot send anyway), then the local read.
        if (userDataRepository.getUserData().userId.isBlank()) return Result.failure(AiAnalyzeError.UserNotReady)
        val prepared = prepare(inputData) ?: return Result.failure(AiAnalyzeError.InputUnreadable)
        return analyzePrepared(prepared, targetChecklist, requestId)
    }

    override suspend fun analyzePrepared(
        input: PreparedAnalyzeInput,
        targetChecklist: Checklist?,
        requestId: String?,
    ): Result<AnalyzeResult> {
        val userData = userDataRepository.getUserData()
        val userId = userData.userId
        val isPremium = userData.isPremium

        // Guard: a blank userId means the user isn't registered yet (new user / web-before-sign-in
        // timing). Calling the AI function with an empty user_id is a guaranteed server 400
        // ("user_id is required") that historically accounted for ~12% of ai_analyze_failed. Bail
        // early with a typed, terminal reason so presentation can prompt "signing you in… try again"
        // instead of a silent generic error.
        if (userId.isBlank()) {
            return Result.failure(AiAnalyzeError.UserNotReady)
        }

        val (inputType, inputDataString) = toWire(input)

        return if (targetChecklist != null) {
            // Use analyze_and_fill_checklist for existing checklists
            firebaseAiService.analyzeAndFillChecklist(
                userId = userId,
                isPremium = isPremium,
                checklist = targetChecklist,
                inputType = inputType,
                inputData = inputDataString,
                requestId = requestId,
            ).fold(
                onSuccess = { response ->
                    if (response.success && response.data != null) {
                        saveBalance(response.data.aiCredits)
                        // An older server answers an empty fill with success; the new one says
                        // nothing_found itself. Either way there is nothing to review.
                        if (response.data.filledItems.isEmpty()) return Result.failure(AiAnalyzeError.NothingFound)

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
                                fillItems = fillItems,
                                modelVariant = response.modelVariant,
                                modelId = response.modelId,
                                aiFlow = response.aiFlow,
                            )
                        )
                    } else {
                        Result.failure(response.toError("Analysis failed"))
                    }
                },
                onFailure = { Result.failure(it) }
            )
        } else {
            // Use generate_checklist for new checklists
            firebaseAiService.generateChecklist(
                userId = userId,
                isPremium = isPremium,
                prompt = promptFor(input),
                inputType = inputType,
                inputData = inputDataString,
                requestId = requestId,
            ).fold(
                onSuccess = { response ->
                    if (response.success && response.data != null) {
                        saveBalance(response.data.aiCredits)
                        // Never create an empty checklist (older server: empty success; the new one
                        // answers 422 nothing_found and refunds).
                        if (response.data.items.isEmpty()) return Result.failure(AiAnalyzeError.NothingFound)

                        Result.success(
                            AnalyzeResult(
                                suggestedItems = response.data.items,
                                confidence = response.data.confidence,
                                summary = response.data.summary,
                                suggestedName = response.data.checklistName,
                                hasFolders = response.data.hasFolders,
                                modelVariant = response.modelVariant,
                                modelId = response.modelId,
                                aiFlow = response.aiFlow,
                            )
                        )
                    } else {
                        Result.failure(response.toError("Generation failed"))
                    }
                },
                onFailure = { Result.failure(it) }
            )
        }
    }

    /**
     * The server's post-action balance, as ONE key. `update(snapshot.copy(...))` from a snapshot taken
     * before a long analysis would roll premium / onboarding back to stale values.
     */
    private suspend fun saveBalance(credits: Int) {
        if (credits >= 0) userDataRepository.setAiCredits(credits)
    }

    /** A 2xx `success:false` body: keep the machine code so the classifier does not guess from text. */
    private fun AiServiceResponse<*>.toError(fallback: String): Throwable {
        val message = error ?: fallback
        return when (errorCode) {
            "nothing_found" -> AiAnalyzeError.NothingFound
            null -> AiAnalyzeError.ServerRefusal(message)
            else -> AiAnalyzeError.Http(statusCode = 200, serverMessage = message, errorCode = errorCode)
        }
    }

    override suspend fun applyToChecklist(
        checklistId: Long,
        result: AnalyzeResult
    ): Result<Checklist> {
        return try {
            // Project-filtered on purpose: AI analysis never targets the v2 arm's system Inbox, and
            // reading the same flow as AnalyzeViewModel keeps both halves of the feature consistent
            // (a split source is how the Inbox leak would creep back in on the next edit).
            val checklists = checklistRepository.projects.first()
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
                foldersEnabled = result.hasFolders,
                separateCompleted = NEW_CHECKLIST_SEPARATE_COMPLETED,
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

    /**
     * Reads file material from its path. Null = unreadable (cache cleared, permission revoked): the
     * caller fails locally with [AiAnalyzeError.InputUnreadable] — never send a "Failed to read …"
     * text to the model and charge for it.
     */
    private fun prepare(inputData: AnalyzeInputData): PreparedAnalyzeInput? = when (inputData) {
        is AnalyzeInputData.Photo -> readFileBytes(inputData.filePath)?.takeIf { it.isNotEmpty() }
            ?.let { PreparedAnalyzeInput.Image(it) }
        is AnalyzeInputData.PdfDocument -> readFileBytes(inputData.filePath)?.takeIf { it.isNotEmpty() }
            ?.let { PreparedAnalyzeInput.Pdf(it, inputData.fileName) }
        is AnalyzeInputData.TextFile -> (inputData.content ?: readFileText(inputData.filePath))
            ?.takeIf { it.isNotBlank() }
            ?.let { PreparedAnalyzeInput.Text(it) }
        is AnalyzeInputData.WebLink -> PreparedAnalyzeInput.Url(inputData.url)
        is AnalyzeInputData.RawText -> PreparedAnalyzeInput.Text(inputData.text)
        is AnalyzeInputData.Audio -> readFileBytes(inputData.filePath)?.takeIf { it.isNotEmpty() }
            ?.let { PreparedAnalyzeInput.Audio(it) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun toWire(input: PreparedAnalyzeInput): Pair<AiInputType, String> = when (input) {
        is PreparedAnalyzeInput.Text -> AiInputType.TEXT to input.text
        is PreparedAnalyzeInput.Url -> AiInputType.URL to input.url
        is PreparedAnalyzeInput.Image -> AiInputType.IMAGE_BASE64 to Base64.encode(input.bytes)
        // The file itself, not its name: the model reads the PDF (server: application/pdf part).
        is PreparedAnalyzeInput.Pdf -> AiInputType.PDF_BASE64 to Base64.encode(input.bytes)
        is PreparedAnalyzeInput.Audio -> AiInputType.AUDIO_BASE64 to Base64.encode(input.bytes)
    }

    private fun promptFor(input: PreparedAnalyzeInput): String = when (input) {
        is PreparedAnalyzeInput.Image -> "Create a checklist based on this image"
        is PreparedAnalyzeInput.Pdf -> "Create a checklist from this document: ${input.fileName}"
        is PreparedAnalyzeInput.Url -> "Create a checklist from the content at: ${input.url}"
        is PreparedAnalyzeInput.Text -> "Create a checklist based on: ${input.text}"
        is PreparedAnalyzeInput.Audio -> "Create a checklist from this voice recording"
    }
}
