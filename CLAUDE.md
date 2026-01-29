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
                         # Contains AppBuildConfig (expect/actual) for debug detection

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
  debug/                 # Developer tools (debug builds only)
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
Splash, Onboarding, Main, Debug, StoreScreenshot

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
- **expect/actual**: Platform-specific code (logging, database, file pickers, audio, build config)
- **StateFlow**: All reactive state management
- **Typesafe project accessors**: Reference modules as `projects.core.common.api`
- **AppBuildConfig**: Debug/release build detection via expect/actual pattern

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

## Feature: Debug

Located in `feature/debug/`. Developer tools available **only in debug builds**.

### Debug Menu Access

**Android**: Volume Up → Volume Down → Volume Up (within 500ms)
**iOS**: Called from Swift code via `navigateToDebugMenu()`

### AppBuildConfig (expect/actual)

Controls debug-only features across platforms:

```kotlin
// commonMain
expect object AppBuildConfig {
    val isDebug: Boolean
}

// androidMain - uses BuildConfig.DEBUG
actual object AppBuildConfig {
    actual val isDebug: Boolean = BuildConfig.DEBUG
}

// iosMain - uses Platform.isDebugBinary
actual object AppBuildConfig {
    actual val isDebug: Boolean = Platform.isDebugBinary
}
```

### Debug Screens

| Screen | Purpose |
|--------|---------|
| `DebugScreen` | Reset onboarding, clear data, create test checklists |
| `StoreScreenshotScreen` | Preview 4 pages for App Store/Play Store screenshots |

### Store Screenshots

`StoreScreenshotScreen` displays 4 swipeable pages for marketing screenshots:

1. **Create via AI** - `CreateViaAiIllustration`
2. **Fill via AI** - `FillViaAiIllustration`
3. **Export & Share** - `ExportShareIllustration`
4. **Unlock Your Full Potential** - `PremiumBenefitsIllustration`

Screenshots are saved to `store_screenshots/` folder.

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
                        └─→ Debug → StoreScreenshot (debug only)
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
| AI модель | gemini-1.5-flash |

### Система кредитов

| Параметр | Free | Premium |
|----------|------|---------|
| Начальные кредиты | 100 | 100 |
| Ежедневное пополнение | — | до 300 |
| Max чек-листов | 3 | ∞ |
| Max fills/чек-лист | 5 | ∞ |

### Стоимость AI операций (дифференцированная)

Разные типы входных данных используют разное количество токенов Gemini.

#### Расчёт себестоимости по типам

| Тип ввода | Input tokens | Себестоимость | Множитель |
|-----------|--------------|---------------|-----------|
| WebLink | ~350 | $0.00006 | 0.86× |
| PDF* | ~400 | $0.00006 | 0.86× |
| RawText | ~500 | $0.00007 | 1.0× (база) |
| TextFile | ~600 | $0.00008 | 1.14× |
| **Photo** | ~760 (560 img + 200 prompt) | $0.00009 | **1.29×** |
| **Audio ≤30s** | ~950 (750 audio + 200 prompt) | $0.0001 | **1.43×** |
| **Audio >30s** | ~1700+ | $0.00016 | **2.29×** |

*PDF сейчас не парсится полностью, работает как текст.

#### Стоимость в кредитах

| Тип операции | Кредитов | Обоснование |
|--------------|----------|-------------|
| Текст (RawText) | 30 | Базовая операция |
| Ссылка (WebLink) | 30 | ≈ текст |
| Текст. файл (TextFile) | 30 | ≈ текст |
| PDF | 30 | Пока без полного парсинга |
| **Фото (Photo)** | **45** | 1.5× дороже (токены изображения) |
| **Голос (Audio)** | **60** | 2× дороже (токены аудио) |

#### Операций в день при 300 кредитах

| Тип | Кредитов | Операций/день |
|-----|----------|---------------|
| Только текст | 30 | 10 |
| Только фото | 45 | 6-7 |
| Только голос | 60 | 5 |
| Микс (50/30/20) | — | ~7-8 |

### Gemini 1.5 Flash Pricing

| Компонент | Стоимость |
|-----------|-----------|
| Input tokens | $0.075 / 1M |
| Output tokens | $0.30 / 1M |
| Image | 560 tokens/изображение |
| Audio | 25 tokens/секунда |

### Сценарии прибыльности

При 300 кредитах/день для Premium:

| Сценарий | Операций/день | Расход AI/мес | Маржа |
|----------|---------------|---------------|-------|
| Только текст (30 кр) | 10 | $0.021 | 98.8% |
| Только фото (45 кр) | 6.7 | $0.018 | 98.9% |
| Только голос (60 кр) | 5 | $0.020 | 98.8% |
| **Реалистичный микс** | ~7-8 | ~$0.020 | **~98.8%** |

### Worst-case анализ

Если увеличить лимит до 1000 кредитов/день (гипотетически):

| Сценарий | Операций/день | Расход AI/мес | Маржа |
|----------|---------------|---------------|-------|
| Только текст | 33 | $0.069 | 95.9% |
| Только голос | 16 | $0.077 | 95.4% |

**Вывод:** Дифференцированная система кредитов обеспечивает:
1. Справедливость — пользователи платят пропорционально расходу
2. Высокую маржу — минимум 63% даже при агрессивном использовании
3. Гибкость — можно увеличить лимиты без убытков

### Remote Config: ai_credits_cost_json

JSON для удалённой настройки стоимости кредитов по типам ввода и моделям AI.

**Ключ в Remote Config:** `ai_credits_cost_json`

```json
{
  "default_model": "gemini-1.5-flash",
  "models": {
    "gemini-1.5-flash": {
      "raw_text": 30,
      "web_link": 30,
      "text_file": 30,
      "pdf": 30,
      "photo": 45,
      "voice": 60
    },
    "gemini-2.0-flash": {
      "raw_text": 25,
      "web_link": 25,
      "text_file": 25,
      "pdf": 25,
      "photo": 40,
      "voice": 50
    },
    "gemini-1.5-pro": {
      "raw_text": 60,
      "web_link": 60,
      "text_file": 60,
      "pdf": 60,
      "photo": 90,
      "voice": 120
    }
  },
  "fallback_cost": 30
}
```

**Поля:**
| Поле | Описание |
|------|----------|
| `default_model` | Модель по умолчанию |
| `models` | Словарь моделей с ценами по типам |
| `models.<model>.<input_type>` | Стоимость в кредитах для типа ввода |
| `fallback_cost` | Цена по умолчанию, если тип не найден |

**Типы ввода (соответствуют `InputDataType` enum):**
- `raw_text` — ручной ввод текста
- `web_link` — URL ссылка
- `text_file` — текстовый файл
- `pdf` — PDF документ
- `photo` — фотография
- `voice` — голосовая запись
