# Client Diagnostics (Android / iOS / wasmJs)

> Runbook extracted from `CLAUDE.md` (2026-06-02 refactor) to keep the main memory file under the 200-line limit. Referenced from `CLAUDE.md` → "Diagnostics". Pair with `docs/cloud-functions-diagnostics.md`.

When the user reports "AI не отвечает" but Cloud Functions are confirmed healthy (smoke tests in `docs/cloud-functions-diagnostics.md` pass), the bug is in the **client HTTP layer**, not the backend. Symptoms that point here: request never leaves the device (no entry in CF logs), response shape mismatch on a working CF (`KotlinxSerializationException` in logcat), short-circuit `catch` swallowing a network exception, wrong Content-Type, missing field.

## How to tell CF vs Client side

| Sanity check | If yes → | If no → |
|---|---|---|
| Run the PowerShell smoke test in `docs/cloud-functions-diagnostics.md` — does the same endpoint succeed? | Backend works. **Client side.** Reproduce with logcat-level Ktor logging | **Server side.** Follow Step 4 table there |
| Does the request show up in `gcloud functions logs read` for the function the app called? | Reached server. Look at response parsing on client | Never left device. Look at HttpClient config, baseUrl, headers, body serialization |
| Does logcat show `HttpRequestTimeoutException` / `HttpRequestException`? | Network / DNS / firewall on device. Test wifi vs mobile | Different bug — read full stacktrace |

## Current test inventory (as of 2026-05-24)

| Component | Test coverage |
|---|---|
| `LocalIntentRouterImpl` (Layer 1 parser) | ✅ `LocalIntentRouterImplTest` (130+ cases, all 7 intents + collisions) |
| `ChatViewModel` state machine | ✅ `ChatViewModelTest` (18 tests; 1 pre-existing failure: `onFeedbackSubmit_blankText_emitsHintSnackbar`) |
| `AiChatRepositoryImpl` (Layer 1→2→3 routing) | ✅ `AiChatRepositoryImplTest` |
| `ChecklistHintExtractor` | ✅ |
| **`FirebaseAiServiceImpl` (HTTP layer)** | ❌ **NONE — gap.** No tests verify request body shape, response parsing, error mapping, or timeout behavior. This is the layer that broke today's incident debug; without tests the next CF-protocol drift will go unnoticed |
| `AnalyzeRepositoryImpl` | ❌ NONE |

## Scaffold pattern for `FirebaseAiServiceImpl` tests

Use Ktor `MockEngine` — no real network, deterministic, runs on JVM via `commonTest`. To enable in `feature/analyze/build.gradle.kts`, add `commonTest.dependencies` block with `libs.ktor.client.mock`, `libs.kotlinx.coroutines.test`, `libs.kotlin.test`, and `withHostTest {}` on the Android target.

Example test shape:

```kotlin
class FirebaseAiServiceImplTest {

    @Test
    fun classify_serializesRequestCorrectly() = runTest {
        val mockEngine = MockEngine { request ->
            // Assertions on outgoing request
            assertEquals("POST", request.method.value)
            assertEquals("application/json", request.body.contentType.toString())
            assertContains(request.url.encodedPath, "/classify_chat_intent")
            respond(
                content = """{"success":true,"intent":"create_item","confidence":1.0}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(mockEngine) { install(ContentNegotiation) { json() } }
        val service = FirebaseAiServiceImpl(logger = NoopLogger, httpClient = client)

        val result = service.classifyChatIntent(userId = "u1", isPremium = false, text = "test")

        assertTrue(result.isSuccess)
        assertEquals("create_item", result.getOrThrow().data?.intent)
    }

    @Test
    fun classify_mapsHttp402ToInsufficientCredits() = runTest { /* ... */ }

    @Test
    fun classify_handlesTimeout() = runTest { /* ... */ }
}
```

**Refactor required first:** `FirebaseAiServiceImpl` currently creates its own `HttpClient` internally — inject it via constructor so `MockEngine` can be substituted. Tracked as backlog when first new test is added.

## Integration test option (slower, optional)

For full E2E confidence, write an `androidTest` (instrumented) that calls a real deployed CF against a throwaway user. Slow (~5s per test, hits prod billing for ~1 credit per call), but proves end-to-end the way the PowerShell smoke does — just from the Android device's network stack. Place under `feature/analyze/src/androidTest/...`. Skip in PR-blocking CI; run nightly or on-demand.
