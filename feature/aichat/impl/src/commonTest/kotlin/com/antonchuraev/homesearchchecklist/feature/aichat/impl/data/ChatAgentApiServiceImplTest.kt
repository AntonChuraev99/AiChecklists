package com.antonchuraev.homesearchchecklist.feature.aichat.impl.data

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistItemContext
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** Wraps a [MockEngine] in the same content-negotiation config as production. */
private fun mockHttpClient(handler: MockRequestHandler): HttpClient =
    HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
                explicitNulls = false
            })
        }
    }

private fun makeService(handler: MockRequestHandler): ChatAgentApiServiceImpl =
    ChatAgentApiServiceImpl(logger = NoOpLogger, httpClient = mockHttpClient(handler))

/** Sample transcript with all three roles to exercise full serialization path. */
private fun sampleTranscript() = listOf(
    AgentTranscriptEntry.UserText("добавь молоко"),
    AgentTranscriptEntry.ModelToolCalls(
        calls = listOf(
            AgentToolCall(
                id = "c1",
                name = "add_items",
                args = buildJsonObject {
                    put("checklist_hint", "покупки")
                    put("item_texts", buildJsonArray { add("молоко") })
                },
            )
        )
    ),
    AgentTranscriptEntry.ToolResults(
        results = listOf(
            AgentToolResult(
                id = "c1",
                name = "add_items",
                result = buildJsonObject { put("status", "success") },
            )
        )
    ),
)

private fun sampleChecklists() = listOf(
    ChecklistContext(name = "Покупки", totalItems = 10, doneItems = 3),
)

// ─── Tests ────────────────────────────────────────────────────────────────────

class ChatAgentApiServiceImplTest {

    // ── 1. 200 type=tool_calls → ToolCalls ────────────────────────────────────

    @Test
    fun step_200ToolCalls_returnsToolCallsWithCallsAndCredits() = runTest {
        val service = makeService {
            respond(
                content = """{"success":true,"type":"tool_calls","tool_calls":[{"id":"c2","name":"read_checklist","args":{"name":"покупки"}}],"credits_remaining":7}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "user-123",
            transcript = sampleTranscript(),
            locale = ChatLocale.Ru,
            checklistsSummary = sampleChecklists(),
        )

        assertIs<AgentStepResult.ToolCalls>(result)
        assertEquals(1, result.calls.size)
        assertEquals("read_checklist", result.calls.first().name)
        assertEquals("c2", result.calls.first().id)
        assertNotNull(result.calls.first().args["name"])
        assertEquals(7, result.creditsRemaining)
    }

    // ── 2. 200 type=final → Final ─────────────────────────────────────────────

    @Test
    fun step_200Final_returnsFinalWithContentAndCredits() = runTest {
        val service = makeService {
            respond(
                content = """{"success":true,"type":"final","content":"Готово — добавил 8 пунктов.","credits_remaining":4}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "user-123",
            transcript = listOf(AgentTranscriptEntry.UserText("да добавь все")),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.Final>(result)
        assertEquals("Готово — добавил 8 пунктов.", result.content)
        assertEquals(4, result.creditsRemaining)
    }

    // ── 3. 200 success=false → ServiceError ───────────────────────────────────

    @Test
    fun step_200SuccessFalse_returnsServiceError() = runTest {
        val service = makeService {
            respond(
                content = """{"success":false,"error":"gemini failed"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "user-123",
            transcript = sampleTranscript(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.ServiceError>(result)
    }

    // ── 4. 200 type=tool_calls with missing tool_calls → ServiceError ─────────

    @Test
    fun step_200ToolCallsWithEmptyList_returnsServiceError() = runTest {
        val service = makeService {
            respond(
                content = """{"success":true,"type":"tool_calls","tool_calls":[],"credits_remaining":7}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "user-123",
            transcript = sampleTranscript(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.ServiceError>(result)
    }

    @Test
    fun step_200ToolCallsWithNullToolCalls_returnsServiceError() = runTest {
        val service = makeService {
            respond(
                content = """{"success":true,"type":"tool_calls","credits_remaining":7}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "user-123",
            transcript = sampleTranscript(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.ServiceError>(result)
    }

    // ── 5. 200 type=final with null/blank content → ServiceError ─────────────

    @Test
    fun step_200FinalWithNullContent_returnsServiceError() = runTest {
        val service = makeService {
            respond(
                content = """{"success":true,"type":"final","credits_remaining":7}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "user-123",
            transcript = sampleTranscript(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.ServiceError>(result)
    }

    @Test
    fun step_200FinalWithBlankContent_returnsServiceError() = runTest {
        val service = makeService {
            respond(
                content = """{"success":true,"type":"final","content":"   ","credits_remaining":7}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "user-123",
            transcript = sampleTranscript(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.ServiceError>(result)
    }

    // ── 6. HTTP 402 → InsufficientCredits ────────────────────────────────────

    @Test
    fun step_402_returnsInsufficientCredits() = runTest {
        val service = makeService {
            respond(
                content = """{"success":false,"error":"insufficient credits"}""",
                status = HttpStatusCode.PaymentRequired,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "user-123",
            transcript = sampleTranscript(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.InsufficientCredits>(result)
    }

    // ── 7. HTTP 500 → ServiceError ────────────────────────────────────────────

    @Test
    fun step_500_returnsServiceError() = runTest {
        val service = makeService {
            respond(
                content = """{"error":"internal server error"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "user-123",
            transcript = sampleTranscript(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.ServiceError>(result)
    }

    // ── 8. Engine throws → NetworkError ──────────────────────────────────────

    @Test
    fun step_engineThrows_returnsNetworkError() = runTest {
        val service = makeService {
            throw Exception("boom — simulated network failure")
        }

        val result = service.step(
            userId = "user-123",
            transcript = sampleTranscript(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.NetworkError>(result)
    }

    // ── 9. Request shape: method, path, Content-Type ──────────────────────────

    @Test
    fun step_request_isPostToChatAgentEndpoint() = runTest {
        var capturedMethod: String? = null
        var capturedPath: String? = null
        var capturedBodyContentType: String? = null

        val service = makeService { request ->
            capturedMethod = request.method.value
            capturedPath = request.url.encodedPath
            capturedBodyContentType = request.body.contentType?.toString()
            respond(
                content = """{"success":true,"type":"final","content":"ok","credits_remaining":10}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        service.step(
            userId = "user-abc",
            transcript = listOf(AgentTranscriptEntry.UserText("hello")),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertEquals("POST", capturedMethod)
        assertTrue(
            capturedPath?.contains("chat_agent") == true,
            "Path must contain 'chat_agent', got: $capturedPath",
        )
        assertTrue(
            capturedBodyContentType?.startsWith("application/json") == true,
            "Body Content-Type must be application/json, got: $capturedBodyContentType",
        )
    }

    // ── 10. Request body: user_id, transcript roles, tool_calls/tool_results keys

    @Test
    fun step_request_bodyContainsUserIdAndTranscriptRoles() = runTest {
        var capturedBody: String? = null

        val service = makeService { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"success":true,"type":"final","content":"ok","credits_remaining":5}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        service.step(
            userId = "my-agent-user",
            transcript = sampleTranscript(),   // UserText + ModelToolCalls + ToolResults
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        val body = capturedBody ?: ""
        assertContains(body, "my-agent-user")
        assertContains(body, "user_id")
        assertContains(body, "\"user\"")
        assertContains(body, "\"model\"")
        assertContains(body, "\"tool\"")
        assertContains(body, "tool_calls")
        assertContains(body, "tool_results")
    }

    @Test
    fun step_request_bodyContainsLocaleAndTimezone() = runTest {
        var capturedBody: String? = null

        val service = makeService { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"success":true,"type":"final","content":"ok","credits_remaining":5}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        service.step(
            userId = "u1",
            transcript = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        val body = capturedBody ?: ""
        assertContains(body, "\"en\"")
        assertContains(body, "timezone_offset_minutes")
    }

    @Test
    fun step_request_bodyContainsChecklistsSummary() = runTest {
        var capturedBody: String? = null

        val service = makeService { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"success":true,"type":"final","content":"ok","credits_remaining":5}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        service.step(
            userId = "u1",
            transcript = emptyList(),
            locale = ChatLocale.Ru,
            checklistsSummary = listOf(
                ChecklistContext(
                    name = "Поход",
                    totalItems = 7,
                    doneItems = 1,
                    recentItems = listOf(
                        ChecklistItemContext(text = "палатка", checked = false, position = 5),
                    ),
                ),
            ),
        )

        val body = capturedBody ?: ""
        assertContains(body, "checklists_summary")
        assertContains(body, "Поход")
        assertContains(body, "totalItems")
        assertContains(body, "doneItems")
        // Recent item text must reach the wire on the LIVE agent path (the new feature).
        assertContains(body, "recentItems")
        assertContains(body, "палатка")
    }

    @Test
    fun step_request_toolCallArgsSerializedVerbatim() = runTest {
        var capturedBody: String? = null

        val service = makeService { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"success":true,"type":"final","content":"ok","credits_remaining":5}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val transcript = listOf(
            AgentTranscriptEntry.UserText("да добавь все"),
            AgentTranscriptEntry.ModelToolCalls(
                calls = listOf(
                    AgentToolCall(
                        id = "c1",
                        name = "add_items",
                        args = buildJsonObject {
                            put("checklist_hint", "поход")
                            put("item_texts", buildJsonArray {
                                add("палатка")
                                add("спальник")
                            })
                        },
                    )
                )
            ),
            AgentTranscriptEntry.ToolResults(
                results = listOf(
                    AgentToolResult(
                        id = "c1",
                        name = "add_items",
                        result = buildJsonObject {
                            put("status", "success")
                            put("added", 2)
                            put("checklist", "Поход")
                        },
                    )
                )
            ),
        )

        service.step(
            userId = "u1",
            transcript = transcript,
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        val body = capturedBody ?: ""
        // Tool call args preserved verbatim
        assertContains(body, "поход")
        assertContains(body, "палатка")
        assertContains(body, "спальник")
        // Tool result preserved verbatim
        assertContains(body, "\"success\"")
        assertContains(body, "\"added\"")
        assertContains(body, "Поход")
    }

    // ── 11. context_checklist present in body when contextChecklistName is non-null ──

    @Test
    fun step_request_bodyContainsContextChecklistWhenProvided() = runTest {
        var capturedBody: String? = null

        val service = makeService { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"success":true,"type":"final","content":"ok","credits_remaining":5}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        service.step(
            userId = "u1",
            transcript = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
            contextChecklistName = "Groceries",
        )

        val body = capturedBody ?: ""
        // Server contract: top-level `context_checklist: { name: "<name>" }`.
        assertContains(body, "context_checklist")
        assertContains(body, "Groceries")
    }

    // ── 12. context_checklist OMITTED from body when contextChecklistName is null ──

    @Test
    fun step_request_omitsContextChecklistWhenNull() = runTest {
        var capturedBody: String? = null

        val service = makeService { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"success":true,"type":"final","content":"ok","credits_remaining":5}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        service.step(
            userId = "u1",
            transcript = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
            // contextChecklistName defaults to null
        )

        val body = capturedBody ?: ""
        // explicitNulls=false → the field must NOT appear at all when null.
        assertTrue(
            !body.contains("context_checklist"),
            "context_checklist must be omitted when no checklist is focused, got: $body",
        )
    }

    // ── Phase 2: type=options ──────────────────────────────────────────────────

    @Test
    fun step_200Options_returnsOptionsWithPromptAndLabels() = runTest {
        val service = makeService {
            respond(
                content = """{"success":true,"type":"options","prompt":"What kind of trip?","options":["Beach","City","Hiking"],"credits_remaining":4}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "u1",
            transcript = sampleTranscript(),
            locale = ChatLocale.En,
            checklistsSummary = sampleChecklists(),
        )

        assertIs<AgentStepResult.Options>(result)
        assertEquals("What kind of trip?", result.prompt)
        assertEquals(listOf("Beach", "City", "Hiking"), result.options)
        assertEquals(4, result.creditsRemaining)
    }

    @Test
    fun step_200Options_sanitizesTrimsDedupsAndCapsAtFour() = runTest {
        val service = makeService {
            respond(
                // Whitespace, a blank, a case-insensitive dup ("beach"/"Beach"), and 5 candidates.
                content = """{"success":true,"type":"options","prompt":"Pick one","options":["  Beach ","","beach","City","Hiking","Roadtrip"],"credits_remaining":3}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "u1",
            transcript = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.Options>(result)
        // Trimmed, blank dropped, "beach" deduped (first-wins "Beach"), capped at 4.
        assertEquals(listOf("Beach", "City", "Hiking", "Roadtrip"), result.options)
    }

    @Test
    fun step_200Options_tooFewValidOptions_fallsBackToFinal() = runTest {
        val service = makeService {
            respond(
                // Only one valid option after sanitizing → not enough to render chips.
                content = """{"success":true,"type":"options","prompt":"Only one choice here","options":["Beach","",""],"credits_remaining":2}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "u1",
            transcript = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        // Degrades gracefully to a plain final answer carrying the prompt — never fails the turn.
        assertIs<AgentStepResult.Final>(result)
        assertEquals("Only one choice here", result.content)
        assertEquals(2, result.creditsRemaining)
    }

    @Test
    fun step_request_advertisesSupportsOptions() = runTest {
        var capturedBody: String? = null

        val service = makeService { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"success":true,"type":"final","content":"ok","credits_remaining":5}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        service.step(
            userId = "u1",
            transcript = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        val body = capturedBody ?: ""
        // The backward-compat gate flag must be present and true so the server may emit options.
        assertContains(body, "supports_options")
        assertContains(body, "\"supports_options\":true")
    }

    @Test
    fun step_200Options_blankPrompt_isServiceError() = runTest {
        val service = makeService {
            respond(
                content = """{"success":true,"type":"options","prompt":"","options":["A","B"],"credits_remaining":1}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "u1",
            transcript = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.ServiceError>(result)
    }

    // ── Phase 3: AI-model A/B dimensions (model_variant / model_id / ai_flow) ──────

    @Test
    fun step_200Final_carriesModelVariantIdAndFlow() = runTest {
        val service = makeService {
            respond(
                content = """{"success":true,"type":"final","content":"ok","credits_remaining":4,"model_variant":"variant_b","model_id":"gemini-3.1-flash-lite","ai_flow":"chat_agent"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "u1",
            transcript = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.Final>(result)
        assertEquals("variant_b", result.modelVariant)
        assertEquals("gemini-3.1-flash-lite", result.modelId)
        assertEquals("chat_agent", result.aiFlow)
    }

    @Test
    fun step_200Final_withoutModelFields_variantIsNull_backwardCompat() = runTest {
        // Older server (experiment off) omits the fields entirely — must not break parsing.
        val service = makeService {
            respond(
                content = """{"success":true,"type":"final","content":"ok","credits_remaining":4}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "u1",
            transcript = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.Final>(result)
        assertNull(result.modelVariant)
        assertNull(result.modelId)
        assertNull(result.aiFlow)
    }

    @Test
    fun step_200ToolCalls_carriesModelVariant() = runTest {
        val service = makeService {
            respond(
                content = """{"success":true,"type":"tool_calls","tool_calls":[{"id":"c2","name":"find_items","args":{"query":"milk"}}],"credits_remaining":7,"model_variant":"control","model_id":"gemini-2.5-flash","ai_flow":"chat_agent"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.step(
            userId = "u1",
            transcript = sampleTranscript(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        assertIs<AgentStepResult.ToolCalls>(result)
        assertEquals("control", result.modelVariant)
        assertEquals("gemini-2.5-flash", result.modelId)
        assertEquals("chat_agent", result.aiFlow)
    }
}
