# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Gisti - AI Checklists** is a Kotlin Multiplatform (KMP) application for Android and iOS. Built with Jetpack Compose Multiplatform.

### Product Concept

Gisti transforms any content into actionable checklists using AI. The app has **three core AI-powered features**:

| Feature | Description | Input Formats |
|---------|-------------|---------------|
| **1. Create via AI** | Generate a new checklist from any content | Photo, PDF, Text, Link, Voice |
| **2. Fill via AI** | Auto-fill an existing checklist based on new content | Photo, PDF, Text, Link, Voice |
| **3. Export** | Share your checklist in convenient formats | PDF, Plain Text |

### Use Cases

- **Home buyer**: Create a checklist "Apartment Inspection", then fill it via AI for each property visited (photo of listing → auto-checked items)
- **Student**: Create a checklist from syllabus PDF, fill progress from lecture notes
- **Project manager**: Generate task list from meeting recording, export as PDF for team

### Key Value Proposition

> "Get the gist of anything — turn chaos into a clear checklist"

- **Any input** → Photo, PDF, text, web link, voice recording
- **AI extracts** → Key points, action items, requirements
- **You check off** → Track progress across multiple "fills"
- **Share results** → Export as PDF or text

### Business Model

- **Free tier**: Limited checklists and fills, daily AI credits
- **Premium** ($1.99/mo): Unlimited checklists/fills, 300 AI credits daily, priority support
- 3-day free trial for new users

## Build Commands

```bash
./gradlew build                           # Full build for all targets
./gradlew composeApp:assembleDebug        # Android debug APK
./gradlew composeApp:bundleRelease        # Android release bundle
./gradlew composeApp:connectedAndroidTest # Run Android instrumented tests
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode.

### Running on Emulator

```bash
# List available emulators
$ANDROID_SDK/emulator/emulator -list-avds

# Start emulator
$ANDROID_SDK/emulator/emulator -avd Pixel_9 &

# Build and install
./gradlew composeApp:installDebug

# Launch app
adb shell am start -n com.antonchuraev.aichecklists/com.antonchuraev.homesearchchecklist.MainActivity
```

Available emulators: `Pixel_9`, `Medium_Phone_API_36.1`

## Architecture

### Module Structure

The project follows a modular architecture with API/impl separation:

```
composeApp/              # Main application entry points (Android/iOS)

core/
  common/api|impl        # AppViewModel, AppDispatchersProvider, AppLogger
  designsystem/          # Compose theme and reusable UI components
  datastore/api|impl     # Preferences persistence
  navigation/api|impl    # AppNavigator and AppNavRoute definitions
  remoteconfig/api|impl  # Firebase Remote Config abstraction

feature/
  checklist/             # Domain models, Room database, repository
  create/                # Create checklist + templates screens
  home/                  # Main screen, checklist detail, fills
  onboarding/            # First-run experience
  splash/                # Launch screen
  analyze/               # AI-powered analysis (Gemini integration)
  paywall/               # Subscriptions (RevenueCat)
  sharing/               # Export checklists (text/PDF)
  user/                  # User profile, device ID
  debug/                 # Developer tools
```

### MVI Pattern

ViewModels extend `AppViewModel<State, Intent, SideEffect>` from `core:common:api`:

- `*ScreenContract.kt` - defines `State` and `Intent` sealed interfaces
- `*ViewModel.kt` - extends `AppViewModel`, implements `onIntent()`
- `*Screen.kt` - Composable observing `screenState`, calls `sendIntent()`

### Dependency Injection

Uses Koin 4.1. Each module defines its own Koin module, aggregated in `appModule`. ViewModels injected via `koinViewModel()`.

### Navigation

Type-safe navigation using Kotlinx Serialization. Routes defined as `@Serializable` objects implementing `AppNavRoute`:

```kotlin
// Main routes
Splash, Onboarding, Main, Debug

// Checklist routes
ChecklistDetail(checklistId), FillDetail(fillId), FillsList(checklistId)

// Create routes
CreateChecklistRoute.CreateChecklist(templateId?, editChecklistId?)
CreateChecklistRoute.Templates
CreateChecklistRoute.TemplatePreview(templateId)

// Feature routes
Analyze(checklistId?), AnalyzeResultPreview
Paywall, SubscriptionStatus
ShareChecklist(checklistId)
```

### Database

Room 2.8 with KSP. Database classes in `feature:checklist`. Platform-specific `DatabaseBuilder` uses `expect/actual`.

## Design System

Located in `core/designsystem/`. Style: **Minimal & Clean** with white background and blue accents.

### Colors (theme/Color.kt)
- **Primary**: Blue (#2196F3)
- **Background/Surface**: White (#FFFFFF)
- **Text Primary**: Gray900 (#212121)
- **Text Secondary**: Gray600 (#757575)
- **Outline**: Gray300 (#E0E0E0)

### Spacing (theme/Dimens.kt)
`AppDimens` constants: `SpacingXs` (4dp), `SpacingSm` (8dp), `SpacingMd` (12dp), `SpacingLg` (16dp), `SpacingXl` (24dp), `SpacingXxl` (32dp).

### Components
- `AppButton` / `AppButtonSecondary` / `AppButtonText`
- `AppCard` - 12dp corners, 2dp elevation
- `AppTextField` - outlined text field
- `EmptyState` - centered icon + title + description
- `AppScaffold` - screen wrapper with top bar (auto-handles system insets)

### System Insets (Edge-to-Edge)

**IMPORTANT**: All screens MUST properly handle system bars to avoid UI overlapping status bar or navigation bar.

#### Screens WITH AppScaffold
`AppScaffold` uses Material3 `Scaffold` which **automatically** handles system insets via `WindowInsets`. No extra work needed.

#### Screens WITHOUT AppScaffold (fullscreen, custom layouts)
**MUST** add these modifiers to the root container:
```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()      // ← Prevents overlap with status bar
        .navigationBarsPadding()  // ← Prevents overlap with navigation bar
        .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
)
```

Required imports:
```kotlin
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
```

#### Examples of screens WITHOUT AppScaffold:
- `OnboardingScreen` - fullscreen pager
- `PaywallScreen` - fullscreen purchase flow
- `SplashScreen` - centered content (insets optional)

## Key Patterns

- **API/impl split**: Core modules expose interfaces in `api`, implementations in `impl`
- **expect/actual**: Platform-specific code (logging, database, file pickers, audio)
- **StateFlow**: All reactive state management
- **Typesafe project accessors**: Reference modules as `projects.core.common.api`

## UI Best Practices

### Text in HorizontalPager

**IMPORTANT**: Text elements inside `HorizontalPager` MUST have `fillMaxWidth()` modifier to prevent overflow.

```kotlin
// ✅ Correct
Text(
    text = stringResource(Res.string.description),
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth()  // Required!
)

// ❌ Wrong - text may overflow on swipe
Text(
    text = stringResource(Res.string.description),
    textAlign = TextAlign.Center,
    modifier = Modifier.padding(horizontal = 16.dp)  // Not enough!
)
```

**Why**: `textAlign = TextAlign.Center` only works correctly when the text knows its width. Without `fillMaxWidth()`, text in a pager can extend beyond screen boundaries.

### Avoid Double Padding

When a parent container already has horizontal padding (e.g., `ScreenPaddingHorizontal`), child elements should NOT add their own horizontal padding:

```kotlin
// Parent already has padding
Column(modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal)) {
    // ✅ Correct - use fillMaxWidth, no extra padding
    Text(modifier = Modifier.fillMaxWidth())

    // ❌ Wrong - double padding reduces available space
    Text(modifier = Modifier.padding(horizontal = AppDimens.SpacingLg))
}
```

## Feature: AI Analyze

Located in `feature/analyze/`. Generates checklist items from various inputs using Gemini AI.

### Architecture

```
feature/analyze/
  domain/
    model/               # AnalyzeInputData, AnalyzeResult, AnalyzeResultHolder
    analyzer/            # AiAnalyzer interface
    repository/          # AnalyzeRepository interface
  data/
    analyzer/            # GeminiAiAnalyzer (production), StubAiAnalyzer (mock)
    config/              # GeminiConfig, ApiKeyProvider
    remote/              # FirebaseAiService
    repository/          # AnalyzeRepositoryImpl
    util/                # FileReader
  presentation/
    AnalyzeScreen.kt     # Input selection UI
    AnalyzeViewModel.kt
    picker/              # FilePicker (expect/actual)
    recorder/            # AudioRecorder, AudioPlayer (expect/actual)
    preview/             # AnalyzeResultPreviewScreen
  di/
    AnalyzeFeatureModule.kt
```

### Input Types (AnalyzeInputData)
- `Photo` - Image analysis
- `PdfDocument` - PDF extraction
- `TextFile` - Text file parsing
- `WebLink` - URL content analysis
- `RawText` - Direct text input
- `VoiceRecording` - Audio transcription

### Gemini Integration
`GeminiAiAnalyzer` uses the Generative AI SDK. API key provided via `GeminiConfig` injected from app module. Results displayed in `AnalyzeResultPreviewScreen`.

## Feature: Templates

Located in `feature/create/`. Pre-defined checklist templates from Firebase Remote Config.

### Template Model
```kotlin
@Serializable
data class ChecklistTemplate(
    val id: String,
    val name: String,
    val icon: String,        // Material icon name
    val category: String,
    val items: List<String>
)
```

### Flow
1. User opens Templates screen
2. Categories displayed with horizontal scrolling cards
3. Tap card → TemplatePreviewScreen (full-screen preview)
4. "Use Template" → creates checklist, navigates to detail

### Remote Config
- Key: `templates_json`
- Falls back to default templates if unavailable

## Feature: Paywall

Located in `feature/paywall/`. Premium subscriptions via RevenueCat.

### Architecture

```
feature/paywall/
  domain/
    model/               # SubscriptionStatus, PurchaseResult, UserLimits, PaywallProduct
    repository/          # PaywallRepository interface
    usecase/             # GetSubscriptionStatus, GetOfferings, Purchase, Restore, GetUserLimits
  data/
    repository/          # PaywallRepositoryImpl (includes PurchasesDelegate for pending transactions)
    RevenueCatInitializer.kt  # Platform-specific init (expect/actual)
    PaywallConfig.kt     # Product IDs, URLs, support email
  presentation/
    PaywallScreen.kt     # Subscription purchase UI with pager
    PaywallViewModel.kt
    SubscriptionStatusScreen.kt  # Current subscription info
    DateFormatter.kt     # Platform-specific date formatting
  di/
    PaywallFeatureModule.kt
```

### User Limits
Free users have limits on checklists and fills per checklist. Premium users have unlimited access.

```kotlin
data class UserLimits(
    val maxChecklists: Int,
    val maxFillsPerChecklist: Int,
    val currentChecklistCount: Int,
    val isPremium: Boolean
)
```

## Feature: Sharing

Located in `feature/sharing/`. Export checklists to share with others.

### Formats
- `ShareFormat.Text` - Plain text list
- `ShareFormat.Pdf` - PDF document

### Architecture
```
feature/sharing/
  domain/
    model/               # ShareFormat
    formatter/           # ChecklistFormatter
  presentation/
    ShareScreen.kt       # Format selection UI
    share/               # ShareLauncher (expect/actual)
    pdf/                 # PdfGenerator (expect/actual)
  di/
    SharingFeatureModule.kt
```

## Localization

Strings in `core/designsystem/src/commonMain/composeResources/values/strings.xml`.

```kotlin
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// Usage
stringResource(Res.string.your_key)
```

### Naming Convention
- Prefix with screen: `main_`, `create_`, `analyze_`, `paywall_`
- Common: `save`, `cancel`, `ok`, `error`
- Use snake_case

## Dependencies

Versions in `gradle/libs.versions.toml`:

| Dependency | Version | Purpose |
|------------|---------|---------|
| Kotlin | 2.3.0 | Language |
| Compose Multiplatform | 1.9.3 | UI Framework |
| Koin | 4.1.1 | Dependency Injection |
| Room | 2.8.4 | Database |
| Navigation Compose | 2.9.1 | Navigation |
| RevenueCat | 2.2.17 | Subscriptions |
| Firebase BOM | 33.7.0 | Analytics, Crashlytics, Remote Config |
| Ktor | 3.0.3 | HTTP Client |
| Generative AI KMP | 0.9.0 | Gemini AI |

## Navigation Flow

```
Splash → Onboarding → Main
                        ├─→ ChecklistDetail → FillDetail
                        │                   → FillsList
                        │                   → ShareChecklist
                        ├─→ Templates → TemplatePreview
                        ├─→ CreateChecklist
                        ├─→ Analyze → AnalyzeResultPreview
                        ├─→ Paywall
                        ├─→ SubscriptionStatus
                        └─→ Debug
```

## Copy Guidelines

### Tone
- Simple, clear, benefit-focused
- Action-oriented button labels
- No jargon

### Button Labels
| Do | Don't |
|----|-------|
| Create Checklist | Add New |
| Fill via AI | AI Analyze |
| Save | Submit |
| Get Started | Continue |

---

## Unit-экономика подписки

### Ключевые параметры

| Параметр | Значение |
|----------|----------|
| Цена подписки | $1.99/мес |
| Комиссия Google (Small Business) | 15% |
| Чистый доход | $1.69/мес |
| AI модель | gemini-1.5-flash (в коде) |

### Лимиты (Remote Config)

| Параметр | Free | Premium |
|----------|------|---------|
| AI запросов/день | 10 | 300 |
| Max чек-листов | 3 | ∞ |
| Max fills/чек-лист | 5 | ∞ |

### Себестоимость AI

Расчёт на основе gemini-2.0-flash (консервативная оценка):
- Input: $0.10/1M tokens
- Output: $0.40/1M tokens
- **~$0.0002 за запрос** (5,000 запросов на $1)

### Сценарии прибыльности

| Использование | Себестоимость AI | Прибыль | Маржа |
|---------------|------------------|---------|-------|
| 100 req/день (MAX) | $0.60/мес | $1.09 | 65% |
| 30 req/день (средний) | $0.18/мес | $1.51 | 89% |
| 10 req/день (лёгкий) | $0.06/мес | $1.63 | 96% |

**Вывод:** Unit-экономика положительная даже при максимальном использовании
