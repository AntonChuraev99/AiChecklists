package com.antonchuraev.homesearchchecklist.feature.analyze.data.remote

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem

/**
 * Response from Firebase AI functions.
 */
data class AiServiceResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val usage: UsageInfo? = null,
    // Server-driven AI-model A/B assignment (dimensions only, never affect behaviour). Null =
    // experiment off / older server. Flow-agnostic, so it lives on the shared response wrapper
    // (both analyze_and_fill and generate_checklist carry it), mirroring the chat_agent response.
    val modelVariant: String? = null,
    val modelId: String? = null,
    val aiFlow: String? = null,
    /** Machine-readable `error_code` of a failed response; null on success and on older servers. */
    val errorCode: String? = null,
)

/** `get_credits_info`: what one Analyze run costs on the server right now. */
data class CreditsInfo(
    val actionCost: Int,
    val userCredits: Int?,
)

data class UsageInfo(
    val count: Int,
    val limit: Int,
    val remaining: Int = limit - count
)

/**
 * Result of user registration.
 */
data class RegisterUserResult(
    val userId: String,
    val isNewUser: Boolean,
    val isPremium: Boolean,
    val aiCredits: Int,
    val createdAt: String
)

/**
 * Result of auto-filling a checklist.
 */
data class FillChecklistResult(
    val filledItems: List<FilledItem>,
    val summary: String,
    val confidence: Float,
    val aiCredits: Int = 0 // Remaining credits after action
)

data class FilledItem(
    val index: Int,
    val text: String,
    val checked: Boolean,
    val note: String?
)

/**
 * Result of generating a new checklist.
 *
 * [items] is a FLAT list even when the AI returned a nested folder structure: folder nodes are
 * interleaved with their children in DFS pre-order, and parent/child links are carried via
 * [ChecklistItem.parentId] / [ChecklistItem.type]. [hasFolders] is true iff any node is a folder,
 * so the presentation layer knows to create the checklist with `foldersEnabled = true`.
 */
data class GenerateChecklistResult(
    /**
     * AI-suggested checklist name (CF `checklist_name`). Nullable: the server may omit it, in
     * which case the presentation layer applies the localized default. NOT defaulted to an
     * English literal here — that would suppress the localized fallback.
     */
    val checklistName: String?,
    val items: List<ChecklistItem>,
    val summary: String,
    val confidence: Float,
    val aiCredits: Int = 0, // Remaining credits after action
    val hasFolders: Boolean = false
)

/**
 * Input types supported by the AI service.
 */
enum class AiInputType(
    /** Value of `input_type` on the wire. Change only together with the Cloud Function. */
    val wire: String,
) {
    TEXT("text"),
    URL("url"),
    IMAGE_BASE64("image_base64"),
    AUDIO_BASE64("audio_base64"),

    /** Base64 of the PDF file bytes; the server sends it to the model as `application/pdf`. */
    PDF_BASE64("pdf_base64"),
    NONE("none"),
}

/**
 * Interface for Firebase AI service.
 * All AI operations go through Firebase Functions for usage control.
 */
interface FirebaseAiService {

    /**
     * Register a new user or retrieve existing user by device ID.
     * This prevents abuse by reinstalling the app.
     *
     * @param deviceId Unique device identifier
     * @param appVersion Optional app version for analytics
     * @param platform Optional platform identifier (android/ios)
     * @return Result with user data or error
     */
    suspend fun registerUser(
        deviceId: String,
        appVersion: String? = null,
        platform: String? = null
    ): Result<AiServiceResponse<RegisterUserResult>>

    /**
     * Auto-fill an existing checklist based on user-provided data.
     *
     * @param userId User identifier for usage tracking
     * @param isPremium Whether user has premium subscription
     * @param checklist The checklist to fill
     * @param inputType Type of input data
     * @param inputData The actual data (text, URL, or base64 image)
     * @return Result with filled items or error
     */
    suspend fun analyzeAndFillChecklist(
        userId: String,
        isPremium: Boolean,
        checklist: Checklist,
        inputType: AiInputType,
        inputData: String
    ): Result<AiServiceResponse<FillChecklistResult>>

    /**
     * Generate a new checklist from user prompt and optional data.
     *
     * @param userId User identifier for usage tracking
     * @param isPremium Whether user has premium subscription
     * @param prompt User's description of what checklist they need
     * @param inputType Type of additional input data (optional)
     * @param inputData Additional context data (optional)
     * @return Result with generated checklist or error
     */
    suspend fun generateChecklist(
        userId: String,
        isPremium: Boolean,
        prompt: String,
        inputType: AiInputType = AiInputType.NONE,
        inputData: String? = null
    ): Result<AiServiceResponse<GenerateChecklistResult>>

    /**
     * [analyzeAndFillChecklist] with a caller-owned idempotency key. A background job passes its id
     * on every attempt so the server reserves credits once per job; null = a fresh key per call.
     * The default ignores the key so test doubles keep compiling; the real service overrides it.
     */
    suspend fun analyzeAndFillChecklist(
        userId: String,
        isPremium: Boolean,
        checklist: Checklist,
        inputType: AiInputType,
        inputData: String,
        requestId: String?,
    ): Result<AiServiceResponse<FillChecklistResult>> =
        analyzeAndFillChecklist(userId, isPremium, checklist, inputType, inputData)

    /** [generateChecklist] with a caller-owned idempotency key; see the fill overload. */
    suspend fun generateChecklist(
        userId: String,
        isPremium: Boolean,
        prompt: String,
        inputType: AiInputType,
        inputData: String?,
        requestId: String?,
    ): Result<AiServiceResponse<GenerateChecklistResult>> =
        generateChecklist(userId, isPremium, prompt, inputType, inputData)

    /**
     * `get_credits_info`: the server's current `action_cost` (Remote Config on the server). Never
     * hardcode the cost on the client. Default = unsupported, for test doubles.
     */
    suspend fun getCreditsInfo(userId: String): Result<CreditsInfo> =
        Result.failure(UnsupportedOperationException("getCreditsInfo is not implemented by this service"))

    /**
     * Get user's AI usage statistics.
     *
     * @param userId User identifier
     * @param isPremium Whether user has premium subscription
     * @return Usage stats or error
     */
    suspend fun getUsageStats(
        userId: String,
        isPremium: Boolean
    ): Result<AiServiceResponse<UsageInfo>>
}
