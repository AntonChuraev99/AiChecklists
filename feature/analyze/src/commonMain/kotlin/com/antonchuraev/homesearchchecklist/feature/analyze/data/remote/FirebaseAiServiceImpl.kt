package com.antonchuraev.homesearchchecklist.feature.analyze.data.remote

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Implementation of FirebaseAiService using Ktor HTTP client.
 * Calls Firebase Cloud Functions for AI operations.
 */
class FirebaseAiServiceImpl(
    private val baseUrl: String = DEFAULT_BASE_URL
) : FirebaseAiService {

    companion object {
        // Default Cloud Functions URL - update after deployment
        private const val DEFAULT_BASE_URL = "https://us-central1-aichecklists-40230.cloudfunctions.net"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    override suspend fun analyzeAndFillChecklist(
        userId: String,
        isPremium: Boolean,
        checklist: Checklist,
        inputType: AiInputType,
        inputData: String
    ): Result<AiServiceResponse<FillChecklistResult>> = runCatching {
        val request = FillChecklistRequest(
            userId = userId,
            isPremium = isPremium,
            checklist = ChecklistDto(
                id = checklist.id,
                name = checklist.name,
                items = checklist.items.map { ItemDto(it.text, it.checked) }
            ),
            inputType = inputType.toApiString(),
            inputData = inputData
        )

        val response: HttpResponse = httpClient.post("$baseUrl/analyze_and_fill_checklist") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val responseBody = response.body<FillChecklistResponseDto>()

        if (responseBody.success) {
            AiServiceResponse(
                success = true,
                data = FillChecklistResult(
                    filledItems = responseBody.filledItems?.map {
                        FilledItem(it.index, it.text, it.checked, it.note)
                    } ?: emptyList(),
                    summary = responseBody.summary ?: "",
                    confidence = responseBody.confidence ?: 0.8f,
                    aiCredits = responseBody.aiCredits ?: -1
                ),
                usage = responseBody.usage?.toUsageInfo()
            )
        } else {
            AiServiceResponse(
                success = false,
                error = responseBody.error ?: "Unknown error"
            )
        }
    }

    override suspend fun generateChecklist(
        userId: String,
        isPremium: Boolean,
        prompt: String,
        inputType: AiInputType,
        inputData: String?
    ): Result<AiServiceResponse<GenerateChecklistResult>> = runCatching {
        val request = GenerateChecklistRequest(
            userId = userId,
            isPremium = isPremium,
            prompt = prompt,
            inputType = inputType.toApiString(),
            inputData = inputData
        )

        val response: HttpResponse = httpClient.post("$baseUrl/generate_checklist") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val responseBody = response.body<GenerateChecklistResponseDto>()

        if (responseBody.success) {
            AiServiceResponse(
                success = true,
                data = GenerateChecklistResult(
                    checklistName = responseBody.checklistName ?: "New Checklist",
                    items = responseBody.items?.map {
                        ChecklistItem(text = it.text, checked = it.checked)
                    } ?: emptyList(),
                    summary = responseBody.summary ?: "",
                    confidence = responseBody.confidence ?: 0.8f,
                    aiCredits = responseBody.aiCredits ?: -1
                ),
                usage = responseBody.usage?.toUsageInfo()
            )
        } else {
            AiServiceResponse(
                success = false,
                error = responseBody.error ?: "Unknown error"
            )
        }
    }

    override suspend fun getUsageStats(
        userId: String,
        isPremium: Boolean
    ): Result<AiServiceResponse<UsageInfo>> = runCatching {
        val request = UsageStatsRequest(
            userId = userId,
            isPremium = isPremium
        )

        val response: HttpResponse = httpClient.post("$baseUrl/get_usage_stats") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val responseBody = response.body<UsageStatsResponseDto>()

        if (responseBody.success && responseBody.usage != null) {
            AiServiceResponse(
                success = true,
                data = responseBody.usage.toUsageInfo()
            )
        } else {
            AiServiceResponse(
                success = false,
                error = responseBody.error ?: "Failed to get usage stats"
            )
        }
    }

    override suspend fun registerUser(
        deviceId: String,
        appVersion: String?,
        platform: String?
    ): Result<AiServiceResponse<RegisterUserResult>> = runCatching {
        val request = RegisterUserRequest(
            deviceId = deviceId,
            appVersion = appVersion,
            platform = platform
        )

        val response: HttpResponse = httpClient.post("$baseUrl/register_user") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val responseBody = response.body<RegisterUserResponseDto>()

        if (responseBody.success && responseBody.userId != null) {
            AiServiceResponse(
                success = true,
                data = RegisterUserResult(
                    userId = responseBody.userId,
                    isNewUser = responseBody.isNewUser ?: true,
                    isPremium = responseBody.isPremium ?: false,
                    aiCredits = responseBody.aiCredits ?: 0,
                    createdAt = responseBody.createdAt ?: ""
                )
            )
        } else {
            AiServiceResponse(
                success = false,
                error = responseBody.error ?: "Failed to register user"
            )
        }
    }

    private fun AiInputType.toApiString(): String = when (this) {
        AiInputType.TEXT -> "text"
        AiInputType.URL -> "url"
        AiInputType.IMAGE_BASE64 -> "image_base64"
        AiInputType.NONE -> "none"
    }

    private fun UsageDto.toUsageInfo() = UsageInfo(
        count = today ?: count ?: 0,
        limit = limit ?: 10,
        remaining = remaining ?: (limit ?: 10) - (today ?: count ?: 0)
    )
}

// ============================================================================
// DTOs for API communication
// ============================================================================

@Serializable
private data class FillChecklistRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("is_premium") val isPremium: Boolean,
    val checklist: ChecklistDto,
    @SerialName("input_type") val inputType: String,
    @SerialName("input_data") val inputData: String
)

@Serializable
private data class ChecklistDto(
    val id: Long,
    val name: String,
    val items: List<ItemDto>
)

@Serializable
private data class ItemDto(
    val text: String,
    val checked: Boolean
)

@Serializable
private data class FillChecklistResponseDto(
    val success: Boolean,
    val error: String? = null,
    @SerialName("filled_items") val filledItems: List<FilledItemDto>? = null,
    val summary: String? = null,
    val confidence: Float? = null,
    @SerialName("ai_credits") val aiCredits: Int? = null,
    val usage: UsageDto? = null
)

@Serializable
private data class FilledItemDto(
    val index: Int,
    val text: String,
    val checked: Boolean,
    val note: String? = null
)

@Serializable
private data class GenerateChecklistRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("is_premium") val isPremium: Boolean,
    val prompt: String,
    @SerialName("input_type") val inputType: String,
    @SerialName("input_data") val inputData: String? = null
)

@Serializable
private data class GenerateChecklistResponseDto(
    val success: Boolean,
    val error: String? = null,
    @SerialName("checklist_name") val checklistName: String? = null,
    val items: List<ItemDto>? = null,
    val summary: String? = null,
    val confidence: Float? = null,
    @SerialName("ai_credits") val aiCredits: Int? = null,
    val usage: UsageDto? = null
)

@Serializable
private data class UsageStatsRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("is_premium") val isPremium: Boolean
)

@Serializable
private data class UsageStatsResponseDto(
    val success: Boolean,
    val error: String? = null,
    val usage: UsageDto? = null
)

@Serializable
private data class UsageDto(
    val today: Int? = null,
    val count: Int? = null,
    val limit: Int? = null,
    val remaining: Int? = null,
    val requests: List<RequestLogDto>? = null
)

@Serializable
private data class RequestLogDto(
    val function: String? = null,
    @SerialName("input_type") val inputType: String? = null,
    val timestamp: String? = null
)

@Serializable
private data class RegisterUserRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("app_version") val appVersion: String? = null,
    val platform: String? = null
)

@Serializable
private data class RegisterUserResponseDto(
    val success: Boolean,
    val error: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("is_new_user") val isNewUser: Boolean? = null,
    @SerialName("is_premium") val isPremium: Boolean? = null,
    @SerialName("ai_credits") val aiCredits: Int? = null,
    @SerialName("created_at") val createdAt: String? = null
)
