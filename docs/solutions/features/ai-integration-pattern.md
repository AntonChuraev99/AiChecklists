---
title: AI/Gemini Integration Pattern
description: Architecture and implementation guide for AI-powered analysis features using Firebase Cloud Functions and Gemini API
category: Architecture
tags:
  - AI
  - Gemini
  - Firebase Functions
  - API Integration
  - Input Processing
  - Design Patterns
date: 2025-01-25
author: Architecture Team
---

# AI/Gemini Integration Pattern

## Overview

The Gisti application implements a **server-side AI integration pattern** that leverages Firebase Cloud Functions to mediate between the client app and Google's Gemini AI API. This architecture provides security, cost control, usage tracking, and flexible AI model selection.

### Key Benefits

- **Secure API keys**: API keys stored on backend, never exposed to client
- **Usage control**: Enforce daily quotas for free vs. premium users
- **Cost optimization**: Server can select optimal Gemini model variants
- **Abuse prevention**: User registration by device ID prevents reinstalls
- **Flexible switching**: Change AI provider without app updates
- **Comprehensive logging**: All AI operations tracked for analytics

## Architecture Layers

### 1. Domain Layer (Business Logic)

The domain layer defines the contracts and models that represent AI operations:

**File**: `feature/analyze/domain/analyzer/AiAnalyzer.kt`

```kotlin
interface AiAnalyzer {
    /**
     * Analyzes provided input data and returns suggested checklist items
     * @param inputData The input to analyze (photo, PDF, text, link)
     * @param targetChecklist Optional existing checklist for context
     * @return AnalyzeResult with suggested items and metadata
     */
    suspend fun analyze(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist? = null
    ): Result<AnalyzeResult>

    /**
     * Checks if analyzer is available and properly configured
     */
    suspend fun isAvailable(): Boolean

    /**
     * Returns supported input types by this implementation
     */
    fun getSupportedInputTypes(): Set<KClass<out AnalyzeInputData>>
}
```

This interface allows multiple implementations:
- `GeminiAiAnalyzer`: Production client-side implementation (legacy fallback)
- `StubAiAnalyzer`: Mock for testing/development

**File**: `feature/analyze/domain/model/AnalyzeInputData.kt`

Sealed interface supporting multiple input types:

```kotlin
sealed interface AnalyzeInputData {
    data class Photo(val filePath: String, val mimeType: String = "image/jpeg")
    data class PdfDocument(val filePath: String, val fileName: String)
    data class TextFile(val filePath: String, val content: String? = null)
    data class WebLink(val url: String)
    data class RawText(val text: String)
    data class Audio(val filePath: String, val mimeType: String = "audio/m4a")
}
```

**File**: `feature/analyze/domain/model/AnalyzeResult.kt`

Result model carrying AI analysis output:

```kotlin
data class AnalyzeResult(
    val suggestedItems: List<ChecklistItem>,  // AI-extracted checklist items
    val confidence: Float = 0.0f,             // 0.0-1.0 confidence score
    val summary: String? = null,              // Human-readable summary
    val warnings: List<String> = emptyList()  // Warnings/limitations
)
```

### 2. Data Layer

#### Repository Pattern

**File**: `feature/analyze/domain/repository/AnalyzeRepository.kt`

Public repository interface hiding implementation details:

```kotlin
interface AnalyzeRepository {
    suspend fun analyzeData(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist? = null
    ): Result<AnalyzeResult>

    suspend fun applyToChecklist(
        checklistId: Long,
        result: AnalyzeResult
    ): Result<Checklist>

    suspend fun createChecklistFromResult(
        name: String,
        result: AnalyzeResult
    ): Result<Checklist>

    suspend fun createFillFromResult(
        checklistId: Long,
        fillName: String,
        result: AnalyzeResult
    ): Result<Long>

    suspend fun isAnalyzerAvailable(): Boolean
}
```

**File**: `feature/analyze/data/repository/AnalyzeRepositoryImpl.kt`

Implementation that:
1. Converts `AnalyzeInputData` to Firebase-compatible format (text, base64-encoded images/audio)
2. Routes through `FirebaseAiService` (preferred) or local `GeminiAiAnalyzer` (fallback)
3. Tracks AI credit usage for quota management
4. Creates database records (Checklist, ChecklistFill) from results

Key conversion logic:

```kotlin
private fun convertInputData(inputData: AnalyzeInputData): Pair<AiInputType, String> {
    return when (inputData) {
        is AnalyzeInputData.Photo -> {
            val bytes = FileReader.readBytes(inputData.filePath)
            AiInputType.IMAGE_BASE64 to Base64.encode(bytes)
        }
        is AnalyzeInputData.Audio -> {
            val bytes = FileReader.readBytes(inputData.filePath)
            AiInputType.AUDIO_BASE64 to Base64.encode(bytes)
        }
        is AnalyzeInputData.WebLink -> AiInputType.URL to inputData.url
        is AnalyzeInputData.RawText -> AiInputType.TEXT to inputData.text
        // ... TextFile, PdfDocument
    }
}
```

#### Firebase AI Service (Preferred Path)

**File**: `feature/analyze/data/remote/FirebaseAiService.kt`

High-level service interface for server-side AI operations:

```kotlin
interface FirebaseAiService {
    suspend fun registerUser(
        deviceId: String,
        appVersion: String? = null,
        platform: String? = null
    ): Result<AiServiceResponse<RegisterUserResult>>

    suspend fun analyzeAndFillChecklist(
        userId: String,
        isPremium: Boolean,
        checklist: Checklist,
        inputType: AiInputType,
        inputData: String
    ): Result<AiServiceResponse<FillChecklistResult>>

    suspend fun generateChecklist(
        userId: String,
        isPremium: Boolean,
        prompt: String,
        inputType: AiInputType = AiInputType.NONE,
        inputData: String? = null
    ): Result<AiServiceResponse<GenerateChecklistResult>>

    suspend fun getUsageStats(
        userId: String,
        isPremium: Boolean
    ): Result<AiServiceResponse<UsageInfo>>
}
```

**File**: `feature/analyze/data/remote/FirebaseAiServiceImpl.kt`

Implementation using Ktor HTTP client:

```kotlin
class FirebaseAiServiceImpl(
    private val logger: AppLogger,
    private val baseUrl: String = "https://us-central1-aichecklists-40230.cloudfunctions.net"
) : FirebaseAiService {

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000  // 2 minutes for AI operations
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }

    override suspend fun analyzeAndFillChecklist(...): Result<...> = runCatching {
        val request = FillChecklistRequest(...)
        val response = httpClient.post("$baseUrl/analyze_and_fill_checklist") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        // Parse and return response
    }
}
```

**Endpoints Called**:
- `/register_user` - Create/retrieve user by device ID
- `/generate_checklist` - Create new checklist from input
- `/analyze_and_fill_checklist` - Auto-fill existing checklist
- `/get_usage_stats` - Get daily AI credit usage

**Request/Response DTOs**: All DTOs use `@SerialName` for snake_case JSON field mapping:

```kotlin
@Serializable
private data class FillChecklistRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("is_premium") val isPremium: Boolean,
    val checklist: ChecklistDto,
    @SerialName("input_type") val inputType: String,  // "text", "url", "image_base64", "audio_base64"
    @SerialName("input_data") val inputData: String
)

@Serializable
private data class FillChecklistResponseDto(
    val success: Boolean,
    val error: String? = null,
    @SerialName("filled_items") val filledItems: List<FilledItemDto>? = null,
    val summary: String? = null,
    val confidence: Float? = null,
    @SerialName("ai_credits") val aiCredits: Int? = null
)
```

#### Local Gemini Analyzer (Fallback)

**File**: `feature/analyze/data/analyzer/GeminiAiAnalyzer.kt`

Direct Gemini API integration using Google's Generative AI SDK (for offline operation):

```kotlin
class GeminiAiAnalyzer(
    private val apiKeyProvider: ApiKeyProvider
) : AiAnalyzer {

    private val textModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKeyProvider.getGeminiApiKey()
        )
    }

    private val visionModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKeyProvider.getGeminiApiKey()
        )
    }

    override suspend fun analyze(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist?
    ): Result<AnalyzeResult> {
        return try {
            val response = when (inputData) {
                is AnalyzeInputData.RawText -> analyzeText(inputData.text, targetChecklist)
                is AnalyzeInputData.WebLink -> analyzeWebLink(inputData.url, targetChecklist)
                is AnalyzeInputData.Photo -> analyzePhoto(inputData, targetChecklist)
                is AnalyzeInputData.Audio -> analyzeAudio(inputData, targetChecklist)
                // ... other types
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun analyzePhoto(
        photo: AnalyzeInputData.Photo,
        targetChecklist: Checklist?
    ): AnalyzeResult {
        val imageBytes = readFileBytes(photo.filePath)
        val inputContent = content {
            image(PlatformImage(imageBytes))
            text("""
                Проанализируй это изображение и создай список пунктов для проверки (чек-лист).
                ...
                Требования:
                1. Каждый пункт должен быть конкретным и проверяемым
                2. Пункты должны быть на русском языке
                3. Формат ответа - каждый пункт с новой строки, начиная с "- "
                4. Максимум 10 пунктов
            """.trimIndent())
        }

        val response = visionModel.generateContent(inputContent)
        return parseResponse(response.text ?: "")
    }

    private fun parseResponse(responseText: String): AnalyzeResult {
        val items = responseText
            .lines()
            .filter { it.startsWith("-") || it.startsWith("•") }
            .map { line ->
                val text = line.removePrefix("-").trim()
                ChecklistItem(text = text, checked = false)
            }
            .take(15)

        val confidence = when {
            items.size >= 5 -> 0.9f
            items.size >= 3 -> 0.75f
            items.size >= 1 -> 0.5f
            else -> 0.1f
        }

        return AnalyzeResult(
            suggestedItems = items,
            confidence = confidence,
            summary = "Найдено ${items.size} пунктов для проверки"
        )
    }
}
```

**Model**: `gemini-1.5-flash` - Cost-optimized for vision and text tasks

#### API Key Configuration

**File**: `feature/analyze/data/config/ApiKeyProvider.kt`

Simple abstraction for securing API key injection:

```kotlin
data class GeminiConfig(
    val apiKey: String
)

interface ApiKeyProvider {
    fun getGeminiApiKey(): String
}

class DefaultApiKeyProvider(private val config: GeminiConfig) : ApiKeyProvider {
    override fun getGeminiApiKey(): String = config.apiKey
}
```

**Initialization in App Module**: The API key is:
1. Stored in `local.properties` (git-ignored)
2. Passed to `GeminiConfig` during Koin setup
3. **Never exposed to client** - passed to Gemini SDK only for fallback

### 3. Presentation Layer

**File**: `feature/analyze/domain/repository/AnalyzeRepository.kt` contracts are used by:

- `AnalyzeViewModel` - Observes UI state, calls repository
- `AnalyzeResultPreviewViewModel` - Displays analysis results

**Usage Example**:

```kotlin
class AnalyzeViewModel(...) : AppViewModel<State, Intent, SideEffect>() {
    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.AnalyzePhoto -> {
                viewModelScope.launch {
                    val inputData = AnalyzeInputData.Photo(filePath)
                    analyzeRepository.analyzeData(inputData).fold(
                        onSuccess = { result ->
                            updateState { it.copy(analysisResult = result) }
                        },
                        onFailure = { error ->
                            sendSideEffect(SideEffect.ShowError(error.message))
                        }
                    )
                }
            }
        }
    }
}
```

## Dependency Injection

**File**: `feature/analyze/di/AnalyzeFeatureModule.kt`

```kotlin
val analyzeFeatureModule = module {
    // Firebase AI Service (preferred)
    single<FirebaseAiService> { FirebaseAiServiceImpl(logger = get()) }

    // Repository implementation
    single<AnalyzeRepository> {
        AnalyzeRepositoryImpl(
            firebaseAiService = get(),
            checklistRepository = get(),
            userDataRepository = get()
        )
    }

    // ViewModels
    viewModel { (checklistId: Long?) ->
        AnalyzeViewModel(
            checklistId = checklistId,
            analyzeRepository = get(),
            checklistRepository = get(),
            appNavigator = get(),
            userDataRepository = get(),
            getSubscriptionStatusUseCase = get()
        )
    }
}
```

**In App Module**:

```kotlin
val appModule = module {
    // Provide API key for local Gemini analyzer (fallback)
    single {
        GeminiConfig(apiKey = BuildConfig.GEMINI_API_KEY)
    }

    single<ApiKeyProvider> { DefaultApiKeyProvider(get()) }

    // Include feature modules
    includes(analyzeFeatureModule)
}
```

## Data Flow: Creating a Checklist from Photo

```
User selects photo
    ↓
AnalyzeScreen.kt captures file path
    ↓
AnalyzeViewModel.onIntent(AnalyzePhoto(filePath))
    ↓
analyzeRepository.analyzeData(AnalyzeInputData.Photo)
    ↓
[Try Firebase first]
  AnalyzeRepositoryImpl.convertInputData()
    → Reads photo file as ByteArray
    → Base64 encodes it
    → Returns (AiInputType.IMAGE_BASE64, base64String)
    ↓
  firebaseAiService.generateChecklist(
      userId, isPremium, prompt,
      inputType="image_base64",
      inputData=base64String
  )
    ↓
  [HTTP POST to Cloud Function]
    → Sends request to /generate_checklist
    → Timeout: 2 minutes (for Gemini processing)
    → Returns GenerateChecklistResponseDto
    ↓
  [Parse response]
    → Extract items, summary, confidence
    → Update user's aiCredits
    → Return AnalyzeResult(suggestedItems, confidence, summary)
    ↓
[Fallback: Local Gemini if network failure]
  GeminiAiAnalyzer.analyzePhoto(inputData)
    → Reads image bytes
    → Builds prompt with context
    → Calls visionModel.generateContent(content { image(...); text(...) })
    → Parses response lines starting with "-" or "•"
    ↓
AnalyzeViewModel receives Result<AnalyzeResult>
    ↓
[Success] Display preview with suggested items
    ↓
User confirms → analyzeRepository.createChecklistFromResult()
    → Creates new Checklist with suggested items
    → Saves to Room database
    → Navigate to ChecklistDetail
```

## Data Flow: Filling Existing Checklist

```
User taps "Fill via AI" on checklist
    ↓
AnalyzeViewModel receives checklistId
    ↓
analyzeRepository.analyzeData(
    inputData,
    targetChecklist = existingChecklist
)
    ↓
AnalyzeRepositoryImpl.analyzeAndFillChecklist() instead of generateChecklist()
    ↓
firebaseAiService.analyzeAndFillChecklist(
    userId, isPremium, checklist,
    inputType, inputData
)
    ↓
[Cloud Function]
    → Compares new analysis with existing items
    → Marks matching items as checked
    → Returns filled items with checked/unchecked state
    ↓
ParseResponse → AnalyzeResult with checked=true for matched items
    ↓
User confirms → analyzeRepository.createFillFromResult()
    → Creates ChecklistFill entry
    → Stores filled items with checked states
    → Navigate to FillDetail
```

## Input Type Handling

### Photo & Audio
- Client reads file bytes
- Converts to Base64 string
- Sends `image_base64` or `audio_base64` to Firebase
- Firebase sends original bytes or base64 to Gemini

### Text & Web Link
- Text sent as-is (or extracted from file)
- URL sent as-is (Cloud Function may fetch)
- Firebase may add instruction context

### PDF Document
- Currently: Fallback to text-based analysis using document name
- Future: Extract PDF text server-side before sending to Gemini

## Error Handling

### Network Failures
- If Firebase unreachable → Fall back to local `GeminiAiAnalyzer`
- If local analyzer unavailable → Show user-friendly error

### Quota Exceeded
- Firebase returns `aiCredits: 0`
- AnalyzeViewModel checks `UserLimits`
- Displays paywall if free user exceeds quota

### Parse Failures
- Gemini response doesn't match expected format → `AnalyzeResult.warnings`
- `AnalyzeViewModel` displays confidence and warnings to user

## Cost Optimization

### Unit Economics

| Model | Input Cost | Output Cost | Use Case |
|-------|-----------|-----------|----------|
| gemini-1.5-flash | $0.075/1M | $0.30/1M | Default (cost-optimized) |
| gemini-1.5-pro | $1.50/1M | $6.00/1M | Complex analysis only |
| gemini-2.0-flash | $0.10/1M | $0.40/1M | Vision-heavy analysis |

**Estimated Cost per Request**:
- Typical checklist generation: 3,000 input + 1,000 output tokens
- Cost: (3,000 × $0.075/1M) + (1,000 × $0.30/1M) ≈ **$0.0003 per request**

**Daily Quota Economics**:
- Free: 10 requests/day × $0.0003 = **$0.003/day** cost
- Premium ($1.99/mo): 300 requests/day × $0.0003 = **$0.09/day** cost
- **Profit margin**: 65-90% even at maximum usage

### Server-Side Optimization

By using Firebase Cloud Functions as middleware, the backend can:

1. **Model Selection**: Choose cheaper model for simple requests
2. **Prompt Optimization**: Pre-process user input to reduce token count
3. **Caching**: Cache frequent patterns (e.g., common checklist templates)
4. **Batch Processing**: Group multiple requests in idle times
5. **Smart Routing**: Route complex requests to pro model, simple to flash

## Testing Strategies

### Unit Tests
- `StubAiAnalyzer` returns mock data (5 items, 0.85 confidence)
- `GeminiAiAnalyzer.parseResponse()` tested with various formats
- Repository layer tested with mock `FirebaseAiService`

### Integration Tests
- Mock Firebase endpoints
- Test full `analyzeData()` flow with various input types
- Verify Room database updates after successful analysis

### E2E Tests
- Real Firebase deployment or Firebase Emulator
- Test actual Gemini API responses
- Verify quota tracking and user credit updates

**Stub Implementation** (for development without API key):

```kotlin
class StubAiAnalyzer : AiAnalyzer {
    override suspend fun analyze(
        inputData: AnalyzeInputData,
        targetChecklist: Checklist?
    ): Result<AnalyzeResult> {
        delay(2000)  // Simulate processing
        val items = generateMockItems(inputData)
        return Result.success(
            AnalyzeResult(
                suggestedItems = items,
                confidence = 0.85f,
                summary = "Mock analysis"
            )
        )
    }
}
```

## Security Considerations

### API Key Protection
- API keys NEVER stored in app code
- Stored in `local.properties` (git-ignored)
- Only passed to Gemini SDK at app startup
- For prod: Use Firebase App Check + custom claims

### User Identification
- Each device gets unique `deviceId` (from user preferences)
- Firebase tracks usage per user (prevents abuse via reinstalls)
- Premium status fetched from RevenueCat at runtime

### Request Validation
- Firebase validates `isPremium` against RevenueCat backend
- Server enforces daily quotas before calling Gemini
- All requests logged with timestamp and user ID

### Input Sanitization
- Base64 encoding for binary data prevents injection
- Text inputs trimmed and length-limited before sending
- PDF/file parsing done server-side (not on client)

## Maintenance & Monitoring

### Logging Points
- `FirebaseAiServiceImpl`: All HTTP requests/responses at DEBUG level
- Error handling at ERROR level with user-friendly messages
- Success at INFO level with token usage

### Metrics to Monitor
- Average response time per input type
- Success/failure rate by input type
- Free vs. premium quota usage distribution
- Cost per daily active user

### Troubleshooting

**Issue**: Firebase endpoint returns 403
- **Check**: API key in Cloud Functions environment
- **Check**: CORS headers if accessed from web
- **Check**: Firebase project ID in base URL

**Issue**: Gemini API quota exceeded
- **Check**: Firebase quota settings
- **Check**: Token usage in API dashboard
- **Contact**: Google Cloud support

**Issue**: Local fallback not working
- **Check**: `BuildConfig.GEMINI_API_KEY` is set
- **Check**: API key has Generative AI API enabled
- **Check**: Device has internet connection for API calls

## Design Patterns Summary

| Pattern | Implementation | Purpose |
|---------|---|---|
| **Sealed Class** | `AnalyzeInputData` | Type-safe input variants |
| **Repository** | `AnalyzeRepository` + `AnalyzeRepositoryImpl` | Hide complexity of Firebase/Local switching |
| **Strategy** | `AiAnalyzer` (Gemini vs Stub) | Swap implementations without code changes |
| **Service Layer** | `FirebaseAiService` | Mediate HTTP communication |
| **DTOs** | All response models | Serialization/deserialization |
| **Lazy Initialization** | `GenerativeModel by lazy` | Defer API initialization until needed |
| **Result Type** | `Result<T>` | Explicit error handling without exceptions |

## Future Enhancements

1. **Multi-Modal Analysis**: Accept mixed input (photo + voice together)
2. **Streaming Responses**: Show checklist items as they're generated
3. **User Feedback Loop**: Learn from accepted/rejected items
4. **Template Suggestions**: Recommend templates based on analysis
5. **Batch Analysis**: Analyze multiple photos in single request
6. **Offline Fallback**: Cache recent analyses for offline access
7. **Advanced Models**: Support GPT-4V or Claude Vision as alternatives

## References

- [Google Generative AI SDK](https://ai.google.dev/tutorials/kotlin_quickstart)
- [Firebase Cloud Functions](https://firebase.google.com/docs/functions)
- [Ktor HTTP Client](https://ktor.io/docs/client.html)
- [Kotlin Serialization](https://kotlinlang.org/docs/serialization.html)
- Project CLAUDE.md - Feature: AI Analyze section
