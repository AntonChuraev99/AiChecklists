package com.antonchuraev.homesearchchecklist.feature.aichat.impl.data

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatCompletionApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Ktor implementation of [ChatCompletionApiService].
 *
 * POSTs to [COMPLETION_URL] and maps the response to [RemoteCompletionResult].
 * Each successful call deducts 3 AI credits from the user's Firestore balance
 * (enforced server-side). Credits are refunded on server error.
 *
 * Error mapping:
 *   - HTTP 402 → [RemoteCompletionResult.InsufficientCredits]
 *   - Other non-2xx → [RemoteCompletionResult.ServiceError]
 *   - Network / timeout → [RemoteCompletionResult.NetworkError]
 *
 * Message history: send all available messages (server caps at 12).
 * Checklist summary: names + counts only, no item text (privacy by design).
 *
 * [httpClient] is injected to allow [MockEngine] substitution in tests.
 * Production code uses [defaultHttpClient] which preserves the original config.
 */
internal class ChatCompletionApiServiceImpl(
    private val logger: AppLogger,
    private val httpClient: HttpClient = defaultHttpClient(),
) : ChatCompletionApiService {

    companion object {
        internal const val TAG = "ChatCompletionApi"
        internal const val COMPLETION_URL =
            "https://us-central1-aichecklists-40230.cloudfunctions.net/chat_completion"

        private val sharedJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        fun defaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(sharedJson)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000  // 60s — full Gemini completion takes longer than classify
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    private val json = sharedJson

    override suspend fun complete(
        userId: String,
        messages: List<ChatMessage>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
    ): RemoteCompletionResult = runCatching {
        logger.debug(TAG, "complete: userId=${userId.take(8)}... messages=${messages.size} locale=$locale checklists=${checklistsSummary.size}")

        val response: HttpResponse = httpClient.post(COMPLETION_URL) {
            contentType(ContentType.Application.Json)
            setBody(
                CompletionRequest(
                    userId = userId,
                    messages = messages.map { MessageDto(role = it.role.toApiString(), content = it.content) },
                    locale = locale.toApiString(),
                    timezoneOffsetMinutes = currentTimezoneOffsetMinutes(),
                    checklistsSummary = checklistsSummary.map {
                        ChecklistSummaryDto(name = it.name, totalItems = it.totalItems, doneItems = it.doneItems)
                    },
                )
            )
        }

        logger.debug(TAG, "complete: response status=${response.status.value}")

        when (response.status.value) {
            402 -> {
                logger.info(TAG, "complete: InsufficientCredits (402)")
                RemoteCompletionResult.InsufficientCredits
            }
            in 200..299 -> {
                val dto = response.body<CompletionResponseDto>()
                logger.debug(TAG, "complete: success=${dto.success} credits_remaining=${dto.creditsRemaining}")

                if (!dto.success || dto.content == null) {
                    logger.warning(TAG, "complete: success=false or null content — treating as ServiceError")
                    return@runCatching RemoteCompletionResult.ServiceError
                }

                RemoteCompletionResult.Success(
                    content = dto.content,
                    creditsRemaining = dto.creditsRemaining,
                )
            }
            else -> {
                logger.warning(TAG, "complete: unexpected status=${response.status.value}")
                RemoteCompletionResult.ServiceError
            }
        }
    }.getOrElse { e ->
        logger.error(TAG, "complete: network/parse error — ${e.message}", e)
        RemoteCompletionResult.NetworkError
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────────

    @Serializable
    private data class CompletionRequest(
        @SerialName("user_id") val userId: String,
        val messages: List<MessageDto>,
        val locale: String,
        @SerialName("timezone_offset_minutes") val timezoneOffsetMinutes: Int,
        @SerialName("checklists_summary") val checklistsSummary: List<ChecklistSummaryDto>,
    )

    @Serializable
    private data class MessageDto(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class ChecklistSummaryDto(
        val name: String,
        @SerialName("totalItems") val totalItems: Int,
        @SerialName("doneItems") val doneItems: Int,
    )

    @Serializable
    private data class CompletionResponseDto(
        val success: Boolean = false,
        val content: String? = null,
        @SerialName("credits_remaining") val creditsRemaining: Int = 0,
        val error: String? = null,
    )

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun ChatLocale.toApiString(): String = when (this) {
        ChatLocale.Ru -> "ru"
        ChatLocale.En -> "en"
    }

    private fun ChatRole.toApiString(): String = when (this) {
        ChatRole.User -> "user"
        ChatRole.Assistant -> "assistant"
    }

    /**
     * Current device's timezone offset from UTC, in minutes.
     * Mirrors [ChatClassifierApiServiceImpl.currentTimezoneOffsetMinutes].
     */
    private fun currentTimezoneOffsetMinutes(): Int {
        val now = Clock.System.now()
        val offsetSeconds = now.offsetIn(TimeZone.currentSystemDefault()).totalSeconds
        return offsetSeconds / 60
    }
}
