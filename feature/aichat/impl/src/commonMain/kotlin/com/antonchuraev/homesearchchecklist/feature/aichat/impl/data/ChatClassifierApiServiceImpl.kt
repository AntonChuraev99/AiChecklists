package com.antonchuraev.homesearchchecklist.feature.aichat.impl.data

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatClassifierApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteClassificationResult
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
import kotlin.time.Duration.Companion.hours

/**
 * Ktor implementation of [ChatClassifierApiService].
 *
 * POSTs to [CLASSIFY_URL] and maps the response to [RemoteClassificationResult].
 * Each successful call deducts 1 AI credit from the user's Firestore balance.
 *
 * Error mapping:
 *   - HTTP 402 → [RemoteClassificationResult.InsufficientCredits]
 *   - Other non-2xx → [RemoteClassificationResult.ServiceError] (logged, not thrown)
 *   - Network / timeout → [RemoteClassificationResult.NetworkError] (logged, not thrown)
 */
internal class ChatClassifierApiServiceImpl(
    private val logger: AppLogger,
) : ChatClassifierApiService {

    companion object {
        private const val TAG = "ChatClassifierApi"
        private const val CLASSIFY_URL =
            "https://us-central1-aichecklists-40230.cloudfunctions.net/classify_chat_intent"
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
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000  // 30s — Gemini flash-lite is fast
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    override suspend fun classify(
        userId: String,
        text: String,
        locale: ChatLocale,
    ): RemoteClassificationResult = runCatching {
        logger.debug(TAG, "classify: userId=${userId.take(8)}... text='${text.take(40)}' locale=$locale")

        val response: HttpResponse = httpClient.post(CLASSIFY_URL) {
            contentType(ContentType.Application.Json)
            setBody(
                ClassifyRequest(
                    userId = userId,
                    text = text,
                    locale = locale.toApiString(),
                    timezoneOffsetMinutes = currentTimezoneOffsetMinutes(),
                )
            )
        }

        logger.debug(TAG, "classify: response status=${response.status.value}")

        when (response.status.value) {
            402 -> {
                logger.info(TAG, "classify: InsufficientCredits (402)")
                RemoteClassificationResult.InsufficientCredits
            }
            in 200..299 -> {
                val dto = response.body<ClassifyResponseDto>()
                logger.debug(TAG, "classify: intent=${dto.intent} confidence=${dto.confidence} credits_remaining=${dto.creditsRemaining}")

                if (!dto.success || dto.intent == null) {
                    logger.warning(TAG, "classify: success=false or null intent — treating as ServiceError")
                    return@runCatching RemoteClassificationResult.ServiceError
                }

                val intent = dto.intent.toChatIntent()
                val toolCall = dto.entities?.toToolCall(intent)
                RemoteClassificationResult.Success(
                    intent = intent,
                    toolCall = toolCall,
                    confidence = dto.confidence,
                    creditsRemaining = dto.creditsRemaining,
                )
            }
            else -> {
                logger.warning(TAG, "classify: unexpected status=${response.status.value}")
                RemoteClassificationResult.ServiceError
            }
        }
    }.getOrElse { e ->
        logger.error(TAG, "classify: network/parse error — ${e.message}", e)
        RemoteClassificationResult.NetworkError
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────────

    @Serializable
    private data class ClassifyRequest(
        @SerialName("user_id") val userId: String,
        val text: String,
        val locale: String,
        // Phase C.4.a — server uses this to resolve relative dates ("tomorrow",
        // "в пятницу в 18") into ISO timestamps with the user's local offset.
        @SerialName("timezone_offset_minutes") val timezoneOffsetMinutes: Int,
    )

    @Serializable
    private data class ClassifyResponseDto(
        val success: Boolean = false,
        val intent: String? = null,
        val entities: EntitiesDto? = null,
        val confidence: Float = 0f,
        @SerialName("credits_remaining") val creditsRemaining: Int = 0,
        val error: String? = null,
    )

    @Serializable
    private data class EntitiesDto(
        @SerialName("itemText") val itemText: String? = null,
        @SerialName("checklistHint") val checklistHint: String? = null,
        @SerialName("checklistName") val checklistName: String? = null,
        @SerialName("dateIso") val dateIso: String? = null,
        val query: String? = null,
    )

    // ─── Intent string → ChatIntent ───────────────────────────────────────────

    private fun String.toChatIntent(): ChatIntent = when (this) {
        "create_item" -> ChatIntent.CreateItem
        "delete_item" -> ChatIntent.DeleteItem
        "complete_item" -> ChatIntent.CompleteItem
        "create_checklist" -> ChatIntent.CreateChecklist
        "set_reminder" -> ChatIntent.SetReminder
        "move_reminders" -> ChatIntent.MoveReminders
        "find_items" -> ChatIntent.FindItems
        "free_form" -> ChatIntent.FreeForm
        "unknown" -> ChatIntent.Unknown(rawText = "")
        else -> {
            logger.warning(TAG, "toChatIntent: unknown intent string '$this'")
            ChatIntent.Unknown(rawText = "")
        }
    }

    // ─── ChatLocale → API string ──────────────────────────────────────────────

    private fun ChatLocale.toApiString(): String = when (this) {
        ChatLocale.Ru -> "ru"
        ChatLocale.En -> "en"
    }

    // ─── EntitiesDto → ToolCall ───────────────────────────────────────────────

    /**
     * Converts server-extracted entities into a concrete [ToolCall].
     *
     * Returns null for free_form / unknown intents or when required entities are absent.
     * Date parsing: uses [parseIsoOrTomorrow] for reminder timestamp resolution.
     * Full date-range parsing for [ChatIntent.MoveReminders] is deferred.
     * Pending: docs/todos/2026-05-17-ai-chat-layer-2-cloud-classifier.md (full date-range parsing)
     */
    private fun EntitiesDto.toToolCall(intent: ChatIntent): ToolCall? = when (intent) {
        ChatIntent.CreateItem -> {
            val text = itemText?.ifBlank { null } ?: return null
            ToolCall.AddItem(checklistHint = checklistHint, itemText = text)
        }
        ChatIntent.DeleteItem -> {
            val text = itemText?.ifBlank { null } ?: return null
            ToolCall.DeleteItem(checklistHint = checklistHint, itemText = text)
        }
        ChatIntent.CompleteItem -> {
            val text = itemText?.ifBlank { null } ?: return null
            ToolCall.CompleteItem(checklistHint = checklistHint, itemText = text)
        }
        ChatIntent.CreateChecklist -> {
            val name = checklistName?.ifBlank { null } ?: return null
            ToolCall.CreateChecklist(name = name, initialItems = emptyList())
        }
        ChatIntent.SetReminder -> {
            val text = itemText?.ifBlank { null } ?: return null
            ToolCall.SetItemReminder(
                checklistHint = checklistHint,
                itemText = text,
                at = parseIsoOrTomorrow(dateIso),
            )
        }
        ChatIntent.MoveReminders -> {
            // Full date-range parsing from entities is Phase B+ work.
            // Pending: docs/todos/2026-05-17-ai-chat-layer-2-cloud-classifier.md
            val nowMs = Clock.System.now().toEpochMilliseconds()
            val oneDayMs = 24 * 60 * 60 * 1000L
            ToolCall.MoveAllReminders(
                fromDayStartMs = nowMs - (nowMs % oneDayMs),
                fromDayEndMs = nowMs - (nowMs % oneDayMs) + oneDayMs - 1,
                toDayStartMs = nowMs - (nowMs % oneDayMs) + oneDayMs,
            )
        }
        ChatIntent.FindItems -> {
            val q = query?.ifBlank { null } ?: return null
            ToolCall.FindItemsQuery(query = q)
        }
        ChatIntent.FreeForm -> null
        is ChatIntent.Unknown -> null
    }

    /**
     * Parses an ISO-8601 instant string (e.g. "2026-05-18T09:00:00Z") to epoch millis.
     * Falls back to now + 24h if the string is null, blank, or unparseable.
     */
    private fun parseIsoOrTomorrow(iso: String?): Long {
        if (iso.isNullOrBlank()) return tomorrowMillis()
        return runCatching {
            Instant.parse(iso).toEpochMilliseconds()
        }.getOrElse {
            logger.warning(TAG, "parseIsoOrTomorrow: failed to parse '$iso', using tomorrow")
            tomorrowMillis()
        }
    }

    private fun tomorrowMillis(): Long =
        (Clock.System.now() + 1.hours * 24).toEpochMilliseconds()

    /**
     * Current device's timezone offset from UTC, in minutes.
     * Used by Phase C.4.a so Gemini can render `dateIso` in the user's local time.
     * Example: Moscow (UTC+3) → 180, Los Angeles (UTC-8) → -480.
     */
    private fun currentTimezoneOffsetMinutes(): Int {
        val now = Clock.System.now()
        val offsetSeconds = now.offsetIn(TimeZone.currentSystemDefault()).totalSeconds
        return offsetSeconds / 60
    }
}
