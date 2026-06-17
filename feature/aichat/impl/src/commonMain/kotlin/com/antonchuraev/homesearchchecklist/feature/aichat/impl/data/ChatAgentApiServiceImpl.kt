package com.antonchuraev.homesearchchecklist.feature.aichat.impl.data

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatAgentApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * Ktor implementation of [ChatAgentApiService].
 *
 * POSTs the full conversation transcript to [AGENT_URL] and maps the response to [AgentStepResult].
 * The Cloud Function is stateless — the client maintains the transcript and sends it each round.
 * Credits are deducted server-side only on the first round of a turn (no tool entries yet).
 *
 * Error mapping:
 *   - HTTP 402 → [AgentStepResult.InsufficientCredits]
 *   - Other non-2xx → [AgentStepResult.ServiceError]
 *   - Network / timeout / parse failure → [AgentStepResult.NetworkError]
 *
 * [httpClient] is injected to allow [MockEngine] substitution in tests.
 * Production code uses [defaultHttpClient] which preserves the original config.
 */
internal class ChatAgentApiServiceImpl(
    private val logger: AppLogger,
    private val httpClient: HttpClient = defaultHttpClient(),
) : ChatAgentApiService {

    companion object {
        internal const val TAG = "ChatAgentApi"
        internal const val AGENT_URL =
            "https://us-central1-aichecklists-40230.cloudfunctions.net/chat_agent"

        private val sharedJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        }

        fun defaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(sharedJson)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000  // 60s — agentic round-trips can be slow
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    override suspend fun step(
        userId: String,
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
        contextChecklistName: String?,
    ): AgentStepResult = runCatching {
        logger.debug(TAG, "step: userId=${userId.take(8)}... transcript=${transcript.size} entries locale=$locale checklists=${checklistsSummary.size} context=${contextChecklistName ?: "none"}")

        val response: HttpResponse = httpClient.post(AGENT_URL) {
            contentType(ContentType.Application.Json)
            setBody(
                AgentRequest(
                    userId = userId,
                    transcript = transcript.map { it.toDto() },
                    locale = locale.toApiString(),
                    timezoneOffsetMinutes = currentTimezoneOffsetMinutes(),
                    checklistsSummary = checklistsSummary.map { ctx ->
                        ChecklistSummaryDto(
                            name = ctx.name,
                            totalItems = ctx.totalItems,
                            doneItems = ctx.doneItems,
                            recentItems = ctx.recentItems.map {
                                ChecklistItemSummaryDto(text = it.text, checked = it.checked, position = it.position)
                            },
                        )
                    },
                    // Only send the context object when a checklist is actually focused.
                    // explicitNulls=false drops the field entirely when null, so the server
                    // sees no `context_checklist` key (treated as "home screen, no focus").
                    contextChecklist = contextChecklistName?.let { ContextChecklistDto(name = it) },
                )
            )
        }

        logger.debug(TAG, "step: response status=${response.status.value}")

        when (response.status.value) {
            402 -> {
                logger.info(TAG, "step: InsufficientCredits (402)")
                AgentStepResult.InsufficientCredits
            }
            in 200..299 -> {
                val dto = response.body<AgentResponseDto>()
                logger.debug(TAG, "step: success=${dto.success} type=${dto.type} credits_remaining=${dto.creditsRemaining}")

                if (!dto.success) {
                    logger.warning(TAG, "step: success=false — treating as ServiceError. error=${dto.error}")
                    return@runCatching AgentStepResult.ServiceError
                }

                when (dto.type) {
                    "tool_calls" -> {
                        val calls = dto.toolCalls
                        if (calls.isNullOrEmpty()) {
                            logger.warning(TAG, "step: type=tool_calls but tool_calls is null/empty — ServiceError")
                            return@runCatching AgentStepResult.ServiceError
                        }
                        AgentStepResult.ToolCalls(
                            calls = calls.map { AgentToolCall(id = it.id, name = it.name, args = it.args) },
                            creditsRemaining = dto.creditsRemaining,
                        )
                    }
                    "final" -> {
                        val content = dto.content
                        if (content.isNullOrBlank()) {
                            logger.warning(TAG, "step: type=final but content is null/blank — ServiceError")
                            return@runCatching AgentStepResult.ServiceError
                        }
                        AgentStepResult.Final(
                            content = content,
                            creditsRemaining = dto.creditsRemaining,
                        )
                    }
                    else -> {
                        logger.warning(TAG, "step: unknown type=${dto.type} — ServiceError")
                        AgentStepResult.ServiceError
                    }
                }
            }
            else -> {
                logger.warning(TAG, "step: unexpected status=${response.status.value}")
                AgentStepResult.ServiceError
            }
        }
    }.getOrElse { e ->
        logger.error(TAG, "step: network/parse error — ${e.message}", e)
        AgentStepResult.NetworkError
    }

    // ─── Transcript mapping ───────────────────────────────────────────────────

    private fun AgentTranscriptEntry.toDto(): TranscriptEntryDto = when (this) {
        is AgentTranscriptEntry.UserText -> TranscriptEntryDto(
            role = "user",
            text = text,
        )
        is AgentTranscriptEntry.ModelText -> TranscriptEntryDto(
            role = "model",
            text = text,
        )
        is AgentTranscriptEntry.ModelToolCalls -> TranscriptEntryDto(
            role = "model",
            toolCalls = calls.map { ToolCallDto(id = it.id, name = it.name, args = it.args) },
        )
        is AgentTranscriptEntry.ToolResults -> TranscriptEntryDto(
            role = "tool",
            toolResults = results.map { ToolResultDto(id = it.id, name = it.name, result = it.result) },
        )
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────────

    @Serializable
    private data class AgentRequest(
        @SerialName("user_id") val userId: String,
        val transcript: List<TranscriptEntryDto>,
        val locale: String,
        @SerialName("timezone_offset_minutes") val timezoneOffsetMinutes: Int,
        @SerialName("checklists_summary") val checklistsSummary: List<ChecklistSummaryDto>,
        // Optional: present only when the dock was opened with a checklist in focus.
        // Server contract: top-level `context_checklist: { name: "<name>" }`.
        @SerialName("context_checklist") val contextChecklist: ContextChecklistDto? = null,
    )

    @Serializable
    private data class ContextChecklistDto(
        val name: String,
    )

    @Serializable
    private data class TranscriptEntryDto(
        val role: String,
        val text: String? = null,
        @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
        @SerialName("tool_results") val toolResults: List<ToolResultDto>? = null,
    )

    @Serializable
    private data class ToolCallDto(
        val id: String,
        val name: String,
        val args: JsonObject,
    )

    @Serializable
    private data class ToolResultDto(
        val id: String,
        val name: String,
        val result: JsonObject,
    )

    @Serializable
    private data class ChecklistSummaryDto(
        val name: String,
        @SerialName("totalItems") val totalItems: Int,
        @SerialName("doneItems") val doneItems: Int,
        // Bounded tail-of-list slice of recent item text — lets Layer 3 answer
        // "what did I add recently / find the task about X". Omitted when empty
        // (explicitNulls=false drops it; the server treats absent as no recent items).
        @SerialName("recentItems") val recentItems: List<ChecklistItemSummaryDto> = emptyList(),
    )

    @Serializable
    private data class ChecklistItemSummaryDto(
        val text: String,
        val checked: Boolean,
        // 0-based list index — recency proxy (no per-item timestamp in the domain model).
        val position: Int,
    )

    @Serializable
    private data class AgentResponseDto(
        val success: Boolean = false,
        val type: String? = null,
        @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
        val content: String? = null,
        @SerialName("credits_remaining") val creditsRemaining: Int = 0,
        val error: String? = null,
    )

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun ChatLocale.toApiString(): String = when (this) {
        ChatLocale.Ru -> "ru"
        ChatLocale.En -> "en"
    }

    /**
     * Current device's timezone offset from UTC, in minutes.
     * Mirrors [ChatCompletionApiServiceImpl.currentTimezoneOffsetMinutes].
     */
    private fun currentTimezoneOffsetMinutes(): Int {
        val now = Clock.System.now()
        val offsetSeconds = now.offsetIn(TimeZone.currentSystemDefault()).totalSeconds
        return offsetSeconds / 60
    }
}
