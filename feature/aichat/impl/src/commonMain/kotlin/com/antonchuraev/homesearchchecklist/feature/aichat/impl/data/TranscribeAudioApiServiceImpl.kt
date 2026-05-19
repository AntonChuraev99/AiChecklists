package com.antonchuraev.homesearchchecklist.feature.aichat.impl.data

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteTranscriptionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscribeAudioApiService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ktor implementation of [TranscribeAudioApiService].
 *
 * POSTs to [TRANSCRIBE_URL] and maps the response to [RemoteTranscriptionResult].
 * Each successful call deducts 1 AI credit from the user's Firestore balance
 * (server-side via `reserve_chat_credit`; refund on Gemini failure).
 *
 * Error mapping:
 *   - HTTP 402 → [RemoteTranscriptionResult.InsufficientCredits]
 *   - Other non-2xx → [RemoteTranscriptionResult.ServiceError]
 *   - Network / timeout / parse error → [RemoteTranscriptionResult.NetworkError]
 */
internal class TranscribeAudioApiServiceImpl(
    private val logger: AppLogger,
) : TranscribeAudioApiService {

    companion object {
        private const val TAG = "TranscribeAudioApi"
        private const val TRANSCRIBE_URL =
            "https://us-central1-aichecklists-40230.cloudfunctions.net/transcribe_audio"
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
            // Gemini Flash-Lite usually transcribes a 30s clip in well under 5s, but a
            // 5-minute clip can take up to ~25s including upload — keep a 60s ceiling.
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }

    override suspend fun transcribe(
        userId: String,
        audioBase64: String,
        locale: ChatLocale,
    ): RemoteTranscriptionResult = runCatching {
        logger.debug(
            TAG,
            "transcribe: userId=${userId.take(8)}... bytes_b64=${audioBase64.length} locale=$locale"
        )

        val response: HttpResponse = httpClient.post(TRANSCRIBE_URL) {
            contentType(ContentType.Application.Json)
            setBody(
                TranscribeRequest(
                    userId = userId,
                    audioBase64 = audioBase64,
                    locale = locale.toApiString(),
                )
            )
        }

        logger.debug(TAG, "transcribe: response status=${response.status.value}")

        when (response.status.value) {
            402 -> {
                logger.info(TAG, "transcribe: InsufficientCredits (402)")
                RemoteTranscriptionResult.InsufficientCredits
            }
            in 200..299 -> {
                val dto = response.body<TranscribeResponseDto>()
                logger.debug(
                    TAG,
                    "transcribe: transcript_len=${dto.transcript?.length ?: 0} credits_remaining=${dto.creditsRemaining}"
                )

                if (!dto.success || dto.transcript == null) {
                    logger.warning(TAG, "transcribe: success=false or null transcript — ServiceError")
                    return@runCatching RemoteTranscriptionResult.ServiceError
                }

                RemoteTranscriptionResult.Success(
                    transcript = dto.transcript,
                    creditsRemaining = dto.creditsRemaining,
                )
            }
            else -> {
                logger.warning(TAG, "transcribe: unexpected status=${response.status.value}")
                RemoteTranscriptionResult.ServiceError
            }
        }
    }.getOrElse { e ->
        logger.error(TAG, "transcribe: network/parse error — ${e.message}", e)
        RemoteTranscriptionResult.NetworkError
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────────

    @Serializable
    private data class TranscribeRequest(
        @SerialName("user_id") val userId: String,
        @SerialName("audio_base64") val audioBase64: String,
        val locale: String,
    )

    @Serializable
    private data class TranscribeResponseDto(
        val success: Boolean = false,
        val transcript: String? = null,
        @SerialName("credits_remaining") val creditsRemaining: Int = 0,
        val error: String? = null,
    )

    // ─── ChatLocale → API string ──────────────────────────────────────────────

    private fun ChatLocale.toApiString(): String = when (this) {
        ChatLocale.Ru -> "ru"
        ChatLocale.En -> "en"
    }
}
