package com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.voice

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.voice.api.ClientTurnId
import com.antonchuraev.homesearchchecklist.core.voice.api.InterruptedTurn
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceAgentAction
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallError
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionId
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnProgress
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnRequest
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatScreenSnapshot
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentVoiceContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatAgentApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatAgentVoiceTurnGatewayTest {

    private class StepCall(
        val transcript: List<AgentTranscriptEntry>,
        val requestId: String?,
        val voice: AgentVoiceContext?,
        val responseLanguage: String?,
        val locale: ChatLocale,
    )

    /** Answers the scripted results in order; records every request. */
    private class FakeAgentApi(vararg results: AgentStepResult) : ChatAgentApiService {
        val script = ArrayDeque(results.toList())
        val calls = mutableListOf<StepCall>()

        override suspend fun step(
            userId: String,
            transcript: List<AgentTranscriptEntry>,
            locale: ChatLocale,
            checklistsSummary: List<ChecklistContext>,
            contextChecklistName: String?,
            screenSnapshot: ChatScreenSnapshot?,
            requestId: String?,
            responseLanguage: String?,
            voice: AgentVoiceContext?,
        ): AgentStepResult {
            calls += StepCall(transcript.toList(), requestId, voice, responseLanguage, locale)
            return script.removeFirstOrNull() ?: AgentStepResult.ServiceError
        }
    }

    private class FakeDispatcher(private val outcome: (ToolCall) -> DispatchOutcome) : ToolCallDispatcher {
        val dispatched = mutableListOf<ToolCall>()
        override suspend fun dispatch(toolCall: ToolCall): DispatchOutcome {
            dispatched += toolCall
            return outcome(toolCall)
        }

        override suspend fun undo(handle: UndoHandle): DispatchOutcome = error("not used")
        override suspend fun moveAddedItem(handle: UndoHandle.AddedItem, targetChecklistName: String): DispatchOutcome =
            error("not used")
    }

    private object NoLog : AppLogger {
        override fun debug(tag: String, message: String) = Unit
        override fun info(tag: String, message: String) = Unit
        override fun warning(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }

    private fun gateway(api: ChatAgentApiService, dispatcher: ToolCallDispatcher, userId: String = "user-1") =
        ChatAgentVoiceTurnGateway(
            agentApi = api,
            dispatcher = dispatcher,
            userId = { userId },
            checklistsSummary = { emptyList() },
            logger = NoLog,
        )

    private fun request(
        text: String,
        turnId: String = "turn-1",
        interrupted: InterruptedTurn? = null,
    ) = VoiceTurnRequest(
        clientTurnId = ClientTurnId(turnId),
        sessionId = VoiceSessionId("sess-1"),
        text = text,
        listenLocale = SpeechLocale("ru-RU"),
        uiLocale = "ru",
        sttConfidence = 0.9f,
        sttOnDevice = true,
        interrupted = interrupted,
        stream = false,
    )

    private val createTrip = AgentToolCall(
        id = "c1",
        name = "create_checklist",
        args = buildJsonObject {
            put("name", "Trip")
            put("initial_items", buildJsonArray { add("Tickets") })
        },
    )

    private val addThree = AgentToolCall(
        id = "c2",
        name = "add_items",
        args = buildJsonObject {
            put("checklist_hint", "Trip")
            put("item_texts", buildJsonArray { add("a"); add("b"); add("c") })
        },
    )

    private fun final(text: String) = AgentStepResult.Final(content = text, creditsRemaining = 0)
    private fun toolCalls(vararg calls: AgentToolCall) = AgentStepResult.ToolCalls(calls = calls.toList(), creditsRemaining = 0)

    private val dispatcherOk = FakeDispatcher { call ->
        when (call) {
            is ToolCall.CreateChecklist -> DispatchOutcome.Success("chat_dispatch_created_with_one", listOf(call.name), linkedChecklistId = 7)
            is ToolCall.AddItems -> DispatchOutcome.Success("chat_dispatch_added_many_to", listOf("3", "Trip"), linkedChecklistId = 7)
            else -> DispatchOutcome.NotFound("chat_dispatch_find_no_match")
        }
    }

    @Test
    fun toolRoundsThenFinal_dispatchesEachCallOnce_reportsActionsAndSpeaksFinal() = runTest {
        val api = FakeAgentApi(toolCalls(createTrip), toolCalls(addThree), final("Created Trip and added three tasks."))
        val events = gateway(api, dispatcherOk).turn(request("create a trip list")).toList()

        val done = assertIs<VoiceTurnEvent.Done>(events.last())
        assertEquals("Created Trip and added three tasks.", done.spoken?.text)
        assertEquals(
            listOf(
                VoiceAgentAction(title = "Trip", kind = VoiceActionMapper.KIND_CHECKLIST_CREATED, targetId = "7"),
                VoiceAgentAction(title = "Trip", kind = VoiceActionMapper.KIND_ITEMS_ADDED, targetId = "7", count = 3),
            ),
            done.actions,
        )
        assertEquals(2, dispatcherOk.dispatched.size)
        assertEquals(3, api.calls.size)
        // The live rounds ride on the next request: [user][calls][results] → [user][calls][results][calls][results].
        assertEquals(5, api.calls.last().transcript.size)
        // The tool the agent runs is surfaced while thinking.
        assertTrue(events.contains(VoiceTurnEvent.Status(VoiceTurnProgress.Acting, toolName = "create_checklist")))
        assertTrue(events.contains(VoiceTurnEvent.Status(VoiceTurnProgress.Acting, toolName = "add_items")))
    }

    @Test
    fun everyRound_sendsVoiceSessionAndClientTurnIdAsRequestId() = runTest {
        val api = FakeAgentApi(toolCalls(createTrip), final("Done."))
        gateway(api, dispatcherOk).turn(request("create a trip list", turnId = "turn-77")).toList()

        assertEquals(listOf("turn-77", "turn-77"), api.calls.map { it.requestId })
        assertEquals(listOf("sess-1", "sess-1"), api.calls.map { it.voice?.sessionId })
        assertEquals("ru", api.calls.first().responseLanguage)
        assertEquals(ChatLocale.Ru, api.calls.first().locale)
    }

    @Test
    fun sameTurnIdAfterDone_isReplayedFromCache_toolsDoNotRunAgain() = runTest {
        val api = FakeAgentApi(toolCalls(createTrip), final("Created Trip."))
        val gateway = gateway(api, dispatcherOk)
        gateway.turn(request("create a trip list")).toList()

        val replay = gateway.turn(request("create a trip list")).toList().last()

        val done = assertIs<VoiceTurnEvent.Done>(replay)
        assertTrue(done.replayed)
        assertEquals("Created Trip.", done.spoken?.text)
        assertEquals(1, dispatcherOk.dispatched.size)
        assertEquals(2, api.calls.size)
    }

    @Test
    fun retryAfterFailureMidTurn_resumesAfterLastRound_withoutRedispatching() = runTest {
        val api = FakeAgentApi(toolCalls(createTrip), AgentStepResult.NetworkError, final("Created Trip."))
        val gateway = gateway(api, dispatcherOk)

        val first = gateway.turn(request("create a trip list")).toList().last()
        assertEquals(VoiceTurnEvent.Failed(VoiceCallError.Unreachable), first)

        val retry = assertIs<VoiceTurnEvent.Done>(gateway.turn(request("create a trip list")).toList().last())
        assertEquals(1, dispatcherOk.dispatched.size)
        assertEquals(listOf(VoiceAgentAction("Trip", VoiceActionMapper.KIND_CHECKLIST_CREATED, "7")), retry.actions)
        // The resumed round still carries the completed tool round.
        assertEquals(3, api.calls.last().transcript.size)
    }

    @Test
    fun nextTurn_carriesPreviousTurnOfTheCall() = runTest {
        val api = FakeAgentApi(final("Hi! How can I help?"), final("Sure."))
        val gateway = gateway(api, dispatcherOk)
        gateway.turn(request("hello", turnId = "t1")).toList()
        gateway.turn(request("add milk", turnId = "t2")).toList()

        assertEquals(
            listOf(
                AgentTranscriptEntry.UserText("hello"),
                AgentTranscriptEntry.ModelText("Hi! How can I help?"),
                AgentTranscriptEntry.UserText("add milk"),
            ),
            api.calls.last().transcript,
        )
    }

    @Test
    fun interruptedReply_isRememberedOnlyAsFarAsItWasHeard() = runTest {
        val api = FakeAgentApi(final("First sentence. Second sentence."), final("Ok."))
        val gateway = gateway(api, dispatcherOk)
        gateway.turn(request("tell me", turnId = "t1")).toList()
        gateway.turn(request("stop", turnId = "t2", interrupted = InterruptedTurn(ClientTurnId("t1"), spokenChars = 15))).toList()

        assertEquals(AgentTranscriptEntry.ModelText("First sentence. …"), api.calls.last().transcript[1])
    }

    @Test
    fun errors_mapToVoiceCallErrors() = runTest {
        suspend fun failureOf(result: AgentStepResult): VoiceTurnEvent =
            gateway(FakeAgentApi(result), dispatcherOk).turn(request("x")).toList().last()

        assertEquals(VoiceTurnEvent.Failed(VoiceCallError.Unreachable), failureOf(AgentStepResult.NetworkError))
        assertEquals(VoiceTurnEvent.Failed(VoiceCallError.Unavailable), failureOf(AgentStepResult.ServiceError))
        assertEquals(VoiceTurnEvent.Failed(VoiceCallError.OutOfCredits), failureOf(AgentStepResult.InsufficientCredits))
        assertEquals(
            VoiceTurnEvent.Failed(VoiceCallError.RateLimited),
            failureOf(AgentStepResult.VoiceRefused(429, "voice_rate_limited", retryAfterSeconds = 1.5)),
        )
        assertEquals(
            VoiceTurnEvent.Failed(VoiceCallError.NotEntitled("closed")),
            failureOf(AgentStepResult.VoiceRefused(409, "voice_session_invalid", reason = "closed")),
        )
        assertEquals(
            VoiceTurnEvent.Failed(VoiceCallError.Other(code = "voice_session_exhausted")),
            failureOf(AgentStepResult.VoiceRefused(409, "voice_session_invalid", reason = "exhausted")),
        )
        assertEquals(
            VoiceTurnEvent.Failed(VoiceCallError.ClientBug("voice_session_malformed")),
            failureOf(AgentStepResult.VoiceRefused(400, "voice_session_invalid", reason = "malformed")),
        )
    }

    @Test
    fun blankUserId_failsWithoutCallingTheServer() = runTest {
        val api = FakeAgentApi(final("never"))
        val event = gateway(api, dispatcherOk, userId = "").turn(request("x")).toList().last()

        assertEquals(VoiceTurnEvent.Failed(VoiceCallError.NotEntitled("user_not_registered")), event)
        assertEquals(0, api.calls.size)
    }

    @Test
    fun unmappableCall_isAnsweredWithErrorResult_andNotDispatched() = runTest {
        val bogus = AgentToolCall(id = "c9", name = "launch_rocket", args = JsonObject(mapOf("x" to JsonPrimitive(1))))
        val api = FakeAgentApi(toolCalls(bogus), final("I can't do that."))
        val done = assertIs<VoiceTurnEvent.Done>(gateway(api, dispatcherOk).turn(request("x")).toList().last())

        assertEquals(0, dispatcherOk.dispatched.size)
        assertEquals(emptyList(), done.actions)
    }

    @Test
    fun roundCap_endsWithFailedAfterFiveToolRounds() = runTest {
        val search = AgentToolCall(id = "s", name = "find_items", args = buildJsonObject { put("query", "milk") })
        val api = FakeAgentApi(*Array(7) { toolCalls(search) })
        val event = gateway(api, dispatcherOk).turn(request("x")).toList().last()

        assertEquals(VoiceTurnEvent.Failed(VoiceCallError.Other(code = "round_cap")), event)
        // Five tool rounds dispatched, the sixth step is the server's chance to answer the cap.
        assertEquals(5, dispatcherOk.dispatched.size)
        assertEquals(6, api.calls.size)
    }

    @Test
    fun actionMapper_noChange_noCard() {
        assertEquals(null, VoiceActionMapper.toAction(DispatchOutcome.Success("chat_dispatch_already_done", listOf("Milk"), 1)))
        assertEquals(null, VoiceActionMapper.toAction(DispatchOutcome.Success("chat_dispatch_find_success", listOf("2", "x"))))
        assertEquals(null, VoiceActionMapper.toAction(DispatchOutcome.NotFound("chat_dispatch_item_not_found")))
        assertEquals(
            VoiceAgentAction("Milk", VoiceActionMapper.KIND_ITEM_ADDED, "3"),
            VoiceActionMapper.toAction(DispatchOutcome.Success("chat_dispatch_added_to", listOf("Milk", "Groceries"), 3)),
        )
        assertEquals(
            VoiceAgentAction("Groceries 2", VoiceActionMapper.KIND_UPDATED, "3"),
            VoiceActionMapper.toAction(DispatchOutcome.Success("chat_dispatch_renamed", listOf("Groceries", "Groceries 2"), 3)),
        )
    }
}
