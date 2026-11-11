package com.antonchuraev.homesearchchecklist.feature.voicecall.data

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallError
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceHeartbeat
import com.antonchuraev.homesearchchecklist.core.voice.api.VoicePlan
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceQuota
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceResult
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionEnd
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionEndReason
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionEndRequest
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionGateway
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionId
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionStart
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionStartRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [VoiceSessionGateway] over the `voice_session` Cloud Function: start / heartbeat / end of a call's
 * server session. The server clock counts the minutes; the client only reports and reads back.
 *
 * Wire → models:
 *  - `remaining_seconds` → [VoiceQuota.secondsLeft] (the account's minutes), `call_remaining_seconds`
 *    → [VoiceQuota.callSecondsLeft] (the per-call maximum), `is_premium` → [VoiceQuota.plan]. The
 *    server sends no reset time, so [VoiceQuota.resetsAtEpochMs] is `null`.
 *  - `chat_agent` does not stream sentences, so [VoiceSessionStart.Started.stream] is `false`: the
 *    engine speaks the final text of each turn.
 *
 * Status mapping (`start`): 200 → Started · 402 `voice_quota_exhausted` → LimitReached · 503
 * `voice_disabled` → Disabled · 404 `user_not_found` → NotEntitled · other 4xx → ClientBug / Other ·
 * 5xx → Unavailable · transport → Unreachable. Never throws.
 *
 * [httpClient] is injected so tests can use a `MockEngine`.
 */
internal class VoiceSessionGatewayImpl(
    private val userId: suspend () -> String,
    private val logger: AppLogger,
    private val httpClient: HttpClient = defaultHttpClient(),
    private val url: String = SESSION_URL,
) : VoiceSessionGateway {

    override suspend fun start(r: VoiceSessionStartRequest): VoiceResult<VoiceSessionStart> =
        call("start", SessionRequest(action = "start", clientSessionId = r.sessionId.value)) { status, body ->
            when (status) {
                in 200..299 -> {
                    val dto = wireJson.decodeFromString<SessionResponseDto>(body)
                    VoiceResult.Success(
                        VoiceSessionStart.Started(
                            sessionId = VoiceSessionId(dto.sessionId?.takeIf { it.isNotBlank() } ?: r.sessionId.value),
                            quota = dto.toQuota(),
                            heartbeatSec = dto.heartbeatIntervalSeconds ?: DEFAULT_HEARTBEAT_SEC,
                            stream = false,
                        ),
                    )
                }
                402 -> VoiceResult.Success(VoiceSessionStart.LimitReached(errorOf(body).toQuota()))
                503 -> VoiceResult.Success(VoiceSessionStart.Disabled)
                else -> VoiceResult.Failure(errorFor(status, body))
            }
        }

    override suspend fun heartbeat(id: VoiceSessionId, elapsedSec: Int): VoiceResult<VoiceHeartbeat> =
        call("heartbeat", SessionRequest(action = "heartbeat", sessionId = id.value)) { status, body ->
            if (status in 200..299) {
                val dto = wireJson.decodeFromString<SessionResponseDto>(body)
                val endReason = if (dto.ended) endReasonOf(dto.reason) else null
                VoiceResult.Success(VoiceHeartbeat(quota = dto.toQuota(), ended = dto.ended, endReason = endReason))
            } else {
                VoiceResult.Failure(errorFor(status, body))
            }
        }

    override suspend fun end(r: VoiceSessionEndRequest): VoiceResult<VoiceSessionEnd> =
        call("end", SessionRequest(action = "end", sessionId = r.sessionId.value)) { status, body ->
            if (status in 200..299) {
                val dto = wireJson.decodeFromString<SessionResponseDto>(body)
                VoiceResult.Success(VoiceSessionEnd(quota = dto.toQuota(), secondsCharged = dto.chargedSeconds ?: 0))
            } else {
                VoiceResult.Failure(errorFor(status, body))
            }
        }

    /**
     * `voice_session` has no read-only quota action yet: the entry point does not show minutes before
     * a call, so nothing calls this in v1. Answered as an explicit failure, never a made-up quota.
     */
    override suspend fun quota(): VoiceResult<VoiceQuota> {
        logger.warning(TAG, "quota: not supported by voice_session")
        return VoiceResult.Failure(VoiceCallError.Other(code = "quota_not_supported"))
    }

    private suspend fun <T> call(
        action: String,
        request: SessionRequest,
        map: (status: Int, body: String) -> VoiceResult<T>,
    ): VoiceResult<T> {
        val uid = userId()
        if (uid.isBlank()) {
            logger.warning(TAG, "$action skipped: userId blank (user not registered yet)")
            return VoiceResult.Failure(VoiceCallError.NotEntitled("user_not_registered"))
        }
        val response: HttpResponse = try {
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(request.copy(userId = uid))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "$action: network error — ${e.message}", e)
            return VoiceResult.Failure(VoiceCallError.Unreachable)
        }
        val status = response.status.value
        return try {
            val body = response.bodyAsText()
            map(status, body).also { result ->
                if (result is VoiceResult.Failure) logger.warning(TAG, "$action: status=$status error=${result.error.code}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A 2xx we cannot read is the server's fault, not the network's.
            logger.error(TAG, "$action: unreadable response status=$status — ${e.message}", e)
            VoiceResult.Failure(VoiceCallError.Unavailable)
        }
    }

    /**
     * Server `reason` of a closed session → [VoiceSessionEndReason]; parsed here once. A missing or
     * unknown reason (an older server, `closed`) is `null`: the engine then falls back to the minutes
     * left, and the reason is logged so the gap is visible.
     */
    private fun endReasonOf(wire: String?): VoiceSessionEndReason? {
        val reason = VoiceSessionEndReason.ofWire(wire)
        if (reason == null) {
            logger.warning(TAG, "heartbeat: the server closed the session with an unknown reason=$wire")
        } else {
            logger.info(TAG, "heartbeat: the server closed the session, reason=${reason.wireValue}")
        }
        return reason
    }

    private fun errorOf(body: String): SessionResponseDto =
        runCatching { wireJson.decodeFromString<SessionResponseDto>(body) }.getOrDefault(SessionResponseDto())

    private fun errorFor(status: Int, body: String): VoiceCallError {
        val error = errorOf(body).error
        return when {
            status == 404 && error == ERROR_USER_NOT_FOUND -> VoiceCallError.NotEntitled(error)
            status == 400 -> VoiceCallError.ClientBug(error ?: "bad_request")
            status in 500..599 -> VoiceCallError.Unavailable
            else -> VoiceCallError.Other(code = error ?: "http_$status")
        }
    }

    private fun SessionResponseDto.toQuota() = VoiceQuota(
        plan = if (isPremium) VoicePlan.Premium else VoicePlan.Free,
        secondsLeft = (remainingSeconds ?: 0).coerceAtLeast(0),
        // The server reports what is left, never the allowance it started from.
        secondsTotal = (remainingSeconds ?: 0).coerceAtLeast(0),
        resetsAtEpochMs = null,
        callSecondsLeft = callRemainingSeconds?.coerceAtLeast(0),
    )

    @Serializable
    private data class SessionRequest(
        @SerialName("user_id") val userId: String = "",
        val action: String,
        @SerialName("session_id") val sessionId: String? = null,
        @SerialName("client_session_id") val clientSessionId: String? = null,
    )

    @Serializable
    private data class SessionResponseDto(
        val success: Boolean = false,
        @SerialName("session_id") val sessionId: String? = null,
        @SerialName("remaining_seconds") val remainingSeconds: Int? = null,
        @SerialName("call_remaining_seconds") val callRemainingSeconds: Int? = null,
        @SerialName("max_call_seconds") val maxCallSeconds: Int? = null,
        @SerialName("heartbeat_interval_seconds") val heartbeatIntervalSeconds: Int? = null,
        @SerialName("is_premium") val isPremium: Boolean = false,
        val ended: Boolean = false,
        val reason: String? = null,
        @SerialName("charged_seconds") val chargedSeconds: Int? = null,
        val error: String? = null,
    )

    companion object {
        private const val TAG = "VoiceSession"
        internal const val SESSION_URL = "https://us-central1-aichecklists-40230.cloudfunctions.net/voice_session"
        private const val DEFAULT_HEARTBEAT_SEC = 15
        private const val ERROR_USER_NOT_FOUND = "user_not_found"

        internal val wireJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        }

        fun defaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) { json(wireJson) }
            install(HttpTimeout) {
                // Session calls are tiny; a heartbeat that hangs longer than its interval is lost anyway.
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
        }
    }
}
