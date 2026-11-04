package com.antonchuraev.homesearchchecklist.feature.voicecall.data

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEndReason
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallEntry
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallError
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceHeartbeat
import com.antonchuraev.homesearchchecklist.core.voice.api.VoicePlan
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceQuota
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceResult
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionEnd
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionEndRequest
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionId
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionStart
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionStartRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceSessionGatewayImplTest {

    private object NoLog : AppLogger {
        override fun debug(tag: String, message: String) = Unit
        override fun info(tag: String, message: String) = Unit
        override fun warning(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }

    private val bodies = mutableListOf<String>()

    private fun gateway(userId: String = "user-1", handler: MockRequestHandler) = VoiceSessionGatewayImpl(
        userId = { userId },
        logger = NoLog,
        httpClient = HttpClient(MockEngine { request ->
            bodies += request.body.toByteArray().decodeToString()
            handler(request)
        }) {
            install(ContentNegotiation) { json(VoiceSessionGatewayImpl.wireJson) }
        },
    )

    private fun answer(status: HttpStatusCode, body: String): MockRequestHandler = {
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }

    private val startRequest = VoiceSessionStartRequest(
        sessionId = VoiceSessionId("client-1"),
        entry = VoiceCallEntry("home"),
        listenLocale = SpeechLocale("en-US"),
        sttOnDevice = true,
    )

    private val endRequest = VoiceSessionEndRequest(
        sessionId = VoiceSessionId("srv-1"),
        reason = CallEndReason.HangUp,
        elapsedSec = 30,
        turns = 2,
        interruptions = 0,
        interrupted = null,
    )

    @Test
    fun start200_isStarted_withAccountAndCallSecondsAndHeartbeat() = runTest {
        val result = gateway(
            handler = answer(
                HttpStatusCode.OK,
                """{"success":true,"session_id":"srv-1","remaining_seconds":300,"call_remaining_seconds":300,"max_call_seconds":600,"heartbeat_interval_seconds":15,"is_premium":false}""",
            ),
        ).start(startRequest)

        assertEquals(
            VoiceResult.Success(
                VoiceSessionStart.Started(
                    sessionId = VoiceSessionId("srv-1"),
                    quota = VoiceQuota(VoicePlan.Free, secondsLeft = 300, secondsTotal = 300, resetsAtEpochMs = null, callSecondsLeft = 300),
                    heartbeatSec = 15,
                    stream = false,
                ),
            ),
            result,
        )
        val body = Json.parseToJsonElement(bodies.single()).jsonObject
        assertEquals("user-1", body["user_id"]?.jsonPrimitive?.content)
        assertEquals("start", body["action"]?.jsonPrimitive?.content)
        assertEquals("client-1", body["client_session_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun start402_isLimitReached_withThePlan() = runTest {
        val result = gateway(
            handler = answer(HttpStatusCode.PaymentRequired, """{"error":"voice_quota_exhausted","is_premium":true,"remaining_seconds":0}"""),
        ).start(startRequest)

        assertEquals(
            VoiceResult.Success(
                VoiceSessionStart.LimitReached(VoiceQuota(VoicePlan.Premium, secondsLeft = 0, secondsTotal = 0, resetsAtEpochMs = null)),
            ),
            result,
        )
    }

    @Test
    fun start503_isDisabled() = runTest {
        val result = gateway(handler = answer(HttpStatusCode.ServiceUnavailable, """{"error":"voice_disabled"}""")).start(startRequest)
        assertEquals(VoiceResult.Success(VoiceSessionStart.Disabled), result)
    }

    @Test
    fun start404UserNotFound_isNotEntitled() = runTest {
        val result = gateway(handler = answer(HttpStatusCode.NotFound, """{"error":"user_not_found"}""")).start(startRequest)
        assertEquals(VoiceResult.Failure(VoiceCallError.NotEntitled("user_not_found")), result)
    }

    @Test
    fun start409_isOtherWithTheServerError() = runTest {
        val result = gateway(handler = answer(HttpStatusCode.Conflict, """{"error":"voice_session_closed"}""")).start(startRequest)
        assertEquals(VoiceResult.Failure(VoiceCallError.Other(code = "voice_session_closed")), result)
    }

    @Test
    fun start500_isUnavailable() = runTest {
        val result = gateway(handler = answer(HttpStatusCode.InternalServerError, """{"error":"boom"}""")).start(startRequest)
        assertEquals(VoiceResult.Failure(VoiceCallError.Unavailable), result)
    }

    @Test
    fun transportFailure_isUnreachable() = runTest {
        val result = gateway(handler = { error("socket closed") }).start(startRequest)
        assertEquals(VoiceResult.Failure(VoiceCallError.Unreachable), result)
    }

    @Test
    fun blankUser_failsWithoutARequest() = runTest {
        val result = gateway(userId = "", handler = answer(HttpStatusCode.OK, "{}")).start(startRequest)
        assertEquals(VoiceResult.Failure(VoiceCallError.NotEntitled("user_not_registered")), result)
        assertTrue(bodies.isEmpty())
    }

    @Test
    fun heartbeatEnded_carriesQuotaAndEnded_andSendsTheSessionId() = runTest {
        val result = gateway(
            handler = answer(
                HttpStatusCode.OK,
                """{"success":true,"ended":true,"reason":"quota_exhausted","remaining_seconds":0,"call_remaining_seconds":250,"charged_seconds":15}""",
            ),
        ).heartbeat(VoiceSessionId("srv-1"), elapsedSec = 45)

        assertEquals(
            VoiceResult.Success(
                VoiceHeartbeat(VoiceQuota(VoicePlan.Free, 0, 0, null, callSecondsLeft = 250), ended = true),
            ),
            result,
        )
        val body = Json.parseToJsonElement(bodies.single()).jsonObject
        assertEquals("heartbeat", body["action"]?.jsonPrimitive?.content)
        assertEquals("srv-1", body["session_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun heartbeat404_isOther() = runTest {
        val result = gateway(handler = answer(HttpStatusCode.NotFound, """{"error":"voice_session_not_found"}"""))
            .heartbeat(VoiceSessionId("srv-1"), elapsedSec = 15)
        assertEquals(VoiceResult.Failure(VoiceCallError.Other(code = "voice_session_not_found")), result)
    }

    @Test
    fun end200_carriesChargedSecondsAndQuota() = runTest {
        val result = gateway(
            handler = answer(
                HttpStatusCode.OK,
                """{"success":true,"ended":true,"reason":"client_end","remaining_seconds":270,"call_remaining_seconds":570,"charged_seconds":30,"already_closed":false}""",
            ),
        ).end(endRequest)

        assertEquals(
            VoiceResult.Success(VoiceSessionEnd(VoiceQuota(VoicePlan.Free, 270, 270, null, callSecondsLeft = 570), secondsCharged = 30)),
            result,
        )
        assertEquals("end", Json.parseToJsonElement(bodies.single()).jsonObject["action"]?.jsonPrimitive?.content)
    }

    @Test
    fun unreadable2xx_isUnavailable() = runTest {
        val result = gateway(handler = answer(HttpStatusCode.OK, "<html>oops</html>")).end(endRequest)
        assertEquals(VoiceResult.Failure(VoiceCallError.Unavailable), result)
    }
}
