package com.antonchuraev.homesearchchecklist.feature.aichat.impl.data

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ─── Fake logger ─────────────────────────────────────────────────────────────

private object NoOpLogger : AppLogger {
    override fun debug(tag: String, message: String) = Unit
    override fun info(tag: String, message: String) = Unit
    override fun warning(tag: String, message: String) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** Wraps a [MockEngine] in the same content-negotiation config as production. */
private fun mockHttpClient(handler: MockRequestHandler): HttpClient =
    HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
    }

private fun makeService(handler: MockRequestHandler): ChatCompletionApiServiceImpl =
    ChatCompletionApiServiceImpl(logger = NoOpLogger, httpClient = mockHttpClient(handler))

private fun sampleMessages() = listOf(
    ChatMessage(id = "1", role = ChatRole.User, content = "Привет!", timestamp = 1000L),
    ChatMessage(id = "2", role = ChatRole.Assistant, content = "Чем помочь?", timestamp = 2000L),
)

private fun sampleChecklists() = listOf(
    ChecklistContext(name = "Покупки", totalItems = 10, doneItems = 3),
)

// ─── Tests ────────────────────────────────────────────────────────────────────

class ChatCompletionApiServiceImplTest {

    // ── 1. HTTP 200 success=true → Success ────────────────────────────────────

    @Test
    fun complete_200Success_returnsSuccessWithContentAndCredits() = runTest {
        val service = makeService {
            respond(
                content = """{"success":true,"content":"Привет!","credits_remaining":42}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.complete(
            userId = "user-123",
            messages = sampleMessages(),
            locale = ChatLocale.Ru,
            checklistsSummary = sampleChecklists(),
        )

        assertIs<RemoteCompletionResult.Success>(result)
        assertEquals("Привет!", result.content)
        assertEquals(42, result.creditsRemaining)
    }

    // ── 2. HTTP 200 success=false → ServiceError ──────────────────────────────

    @Test
    fun complete_200SuccessFalse_returnsServiceError() = runTest {
        val service = makeService {
            respond(
                content = """{"success":false}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.complete(
            userId = "user-123",
            messages = sampleMessages(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<RemoteCompletionResult.ServiceError>(result)
    }

    // ── 3. HTTP 200 success=true but content=null → ServiceError ──────────────

    @Test
    fun complete_200NullContent_returnsServiceError() = runTest {
        val service = makeService {
            respond(
                content = """{"success":true,"content":null,"credits_remaining":5}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.complete(
            userId = "user-123",
            messages = sampleMessages(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<RemoteCompletionResult.ServiceError>(result)
    }

    // ── 4. HTTP 402 → InsufficientCredits ────────────────────────────────────

    @Test
    fun complete_402_returnsInsufficientCredits() = runTest {
        val service = makeService {
            respond(
                content = """{"error":"insufficient credits"}""",
                status = HttpStatusCode.PaymentRequired,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.complete(
            userId = "user-123",
            messages = sampleMessages(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        assertIs<RemoteCompletionResult.InsufficientCredits>(result)
    }

    // ── 5. HTTP 500 → ServiceError ────────────────────────────────────────────

    @Test
    fun complete_500_returnsServiceError() = runTest {
        val service = makeService {
            respond(
                content = """{"error":"internal server error"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = service.complete(
            userId = "user-123",
            messages = sampleMessages(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<RemoteCompletionResult.ServiceError>(result)
    }

    // ── 6. Engine throws IOException → NetworkError ───────────────────────────

    @Test
    fun complete_engineThrows_returnsNetworkError() = runTest {
        val service = makeService {
            throw Exception("boom — simulated network failure")
        }

        val result = service.complete(
            userId = "user-123",
            messages = sampleMessages(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<RemoteCompletionResult.NetworkError>(result)
    }

    // ── 7. Request serialization: path, method, Content-Type, body fields ─────

    @Test
    fun complete_request_isPostToCompletionEndpoint() = runTest {
        var capturedMethod: String? = null
        var capturedPath: String? = null
        var capturedBodyContentType: String? = null

        val service = makeService { request ->
            capturedMethod = request.method.value
            capturedPath = request.url.encodedPath
            // Ktor serializes Content-Type on the body descriptor, not in headers map,
            // when using contentType() + setBody(). Read it from body.contentType.
            capturedBodyContentType = request.body.contentType?.toString()
            respond(
                content = """{"success":true,"content":"ok","credits_remaining":10}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        service.complete(
            userId = "user-abc",
            messages = sampleMessages(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertEquals("POST", capturedMethod)
        assertTrue(
            capturedPath?.contains("chat_completion") == true,
            "Path must contain 'chat_completion', got: $capturedPath",
        )
        assertTrue(
            capturedBodyContentType?.startsWith("application/json") == true,
            "Body Content-Type must be application/json, got: $capturedBodyContentType",
        )
    }

    @Test
    fun complete_request_bodyContainsUserId() = runTest {
        var capturedBody: String? = null

        val service = makeService { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"success":true,"content":"ok","credits_remaining":10}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        service.complete(
            userId = "my-user-id",
            messages = emptyList(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertContains(capturedBody ?: "", "my-user-id")
        assertContains(capturedBody ?: "", "user_id")
    }

    @Test
    fun complete_request_bodyContainsMessageRoles() = runTest {
        var capturedBody: String? = null

        val service = makeService { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"success":true,"content":"ok","credits_remaining":10}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        service.complete(
            userId = "u1",
            messages = listOf(
                ChatMessage(id = "1", role = ChatRole.User, content = "hello", timestamp = 1L),
                ChatMessage(id = "2", role = ChatRole.Assistant, content = "hi", timestamp = 2L),
            ),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        val body = capturedBody ?: ""
        assertContains(body, "\"user\"")
        assertContains(body, "\"assistant\"")
    }

    @Test
    fun complete_request_bodyContainsLocaleAndTimezone() = runTest {
        var capturedBody: String? = null

        val service = makeService { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"success":true,"content":"ok","credits_remaining":10}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        service.complete(
            userId = "u1",
            messages = emptyList(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        val body = capturedBody ?: ""
        // Locale serialized as "ru"
        assertContains(body, "\"ru\"")
        // timezone_offset_minutes field is present (value is device-dependent — we only assert presence)
        assertContains(body, "timezone_offset_minutes")
    }

    @Test
    fun complete_request_bodyContainsChecklistSummaryFields() = runTest {
        var capturedBody: String? = null

        val service = makeService { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"success":true,"content":"ok","credits_remaining":10}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        service.complete(
            userId = "u1",
            messages = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = listOf(
                ChecklistContext(name = "Groceries", totalItems = 5, doneItems = 2),
            ),
        )

        val body = capturedBody ?: ""
        assertContains(body, "checklists_summary")
        assertContains(body, "Groceries")
        assertContains(body, "totalItems")
        assertContains(body, "doneItems")
    }
}
