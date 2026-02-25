# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Gisti - AI Checklists** is a Kotlin Multiplatform (KMP) application for Android and iOS. Built with Jetpack Compose Multiplatform.

> **iOS release strategy**: iOS version will be published after Android revenue covers the Apple Developer Program fee ($99/year). Until then, iOS target exists in code but is not actively released.

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

## Project Language

**All code comments, documentation, and commit messages must be in English.**

This includes:
- Code comments and KDoc/Javadoc
- Documentation in `docs/` folder
- Commit messages
- PR descriptions
- Variable and function names (always English)

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

### Trial Timeline (Blinkist Style)
The paywall includes a visual trial timeline showing:
- **Today**: Free $0.00 (in user's currency)
- **Due date**: Price after trial ends

Key components:
- `TrialTimeline` composable with Canvas-drawn timeline
- `formatZeroPrice()` — currency-aware zero price formatting
- `getTrialEndDateFormatted()` — expect/actual for date calculation

See: [docs/solutions/ui-improvements/paywall-trial-timeline.md](docs/solutions/ui-improvements/paywall-trial-timeline.md)

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

## Credit Restore Architecture

### Flow: Purchase → Credits (300)

After a successful purchase or restore, credits must be explicitly restored via Cloud Function `restore_credits_after_purchase`. This is NOT automatic — the client must call it.

### Call Chain

```
PurchaseProductUseCase / RestorePurchasesUseCase
  → UserDataRepository.restoreCreditsAfterPurchase()
    → UserDataRepositoryImpl (retry up to 3 times, backoff 2s/4s)
      → UserApiService → POST /restore_credits_after_purchase
        → Cloud Function verifies premium via RevenueCat REST API
        → Writes to Firestore: is_premium=true, ai_credits=300
      → On success: saves to DataStore (local cache)
```

### Analytics Events (credits restore)

| Event | When | Key Params |
|-------|------|------------|
| `credits_restore_started` | restoreCreditsAfterPurchase begins | — |
| `credits_restore_success` | Credits successfully restored | `credits`, `attempt` |
| `credits_restore_retry` | Retry attempt after failure | `attempt`, `error` |
| `credits_restore_failed` | All retries exhausted OR no user_id | `error`, `attempts` |

### Critical Rules

- **Always use UseCase** for restoring purchases — `RestorePurchasesUseCase` (not `paywallRepository.restorePurchases()` directly). The UseCase adds `restoreCreditsAfterPurchase()` after RevenueCat restore.
- **SplashViewModel.linkWithPaywall()** must use `RestorePurchasesUseCase` for returning users.
- **Retry is in the repository** (`UserDataRepositoryImpl`) — all callers get retry automatically.
- **Slow networks** (>10s Google Play transactions) may cause Cloud Function timeout — retry with backoff handles this.
- **`PurchaseProductUseCase`** ignores the `Result<Int>` from `restoreCreditsAfterPurchase()` — the purchase is already successful. Analytics captures failures.

### Firestore Collections (credits)

| Collection | Purpose |
|-----------|---------|
| `users/{userId}` | `is_premium`, `ai_credits`, `credits_restored_at` |
| `credits_restore_log` | Log of successful restore operations |
| `credits_refill_log` | Log of daily premium credit refills |

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

---

## Security: API Key & Sensitive Data Prevention

### Current Risk Assessment

**CRITICAL ISSUE DETECTED**: `google-services.json` is currently committed to the repository with Firebase API keys exposed.

**Exposed credentials in repository:**
- Firebase project ID: `aichecklists-40230`
- Firebase API key: `AIzaSyARBOXYuETAF3DNC87YqHDmbQy3rT4CwBI`
- Project number: `27698629989`

**Immediate Actions Required:**
1. Treat Firebase API key as compromised - regenerate via Firebase Console
2. Ensure `google-services.json` is removed from git history (see "Remove from Git History" section)
3. Remove `google-services.json` from working directory
4. Implement all prevention strategies below

### Prevention Strategy 1: Pre-commit Hooks (Git Secrets Scanning)

**Goal**: Prevent accidental commits of API keys and sensitive files.

#### Install git-secrets

```bash
# macOS/Linux
brew install git-secrets

# Windows (via Chocolatey)
choco install git-secrets

# Manual installation (all platforms)
git clone https://github.com/awslabs/git-secrets.git
cd git-secrets
make install
```

#### Setup for This Repository

```bash
# Navigate to project root
cd C:\Users\Admin\StudioProjects\Checklists

# Install hooks in .git/hooks
git secrets --install

# Add patterns for Google services and Firebase files
git secrets --add 'AIzaSy[A-Za-z0-9_-]*'  # Firebase API keys
git secrets --add 'GEMINI_API_KEY'         # Gemini API keys
git secrets --add 'aichecklists-40230'     # Project ID
git secrets --add '"project_number"'       # Firebase project numbers
git secrets --add 'google-services\.json'  # Firebase config files
git secrets --add 'GoogleService-Info\.plist'  # iOS Firebase config

# Verify installation
git secrets --list
```

#### Test Pre-commit Hook

```bash
# This should be blocked by git-secrets
echo "AIzaSyARBOXYuETAF3DNC87YqHDmbQy3rT4CwBI" > test_key.txt
git add test_key.txt
# Expected: ✗ FAIL - secret detected
```

### Prevention Strategy 2: Template Files with Placeholders

**Goal**: Document required setup without exposing actual keys.

#### Create `google-services.json.example`

Location: `composeApp/google-services.json.example`

```json
{
  "project_info": {
    "project_number": "YOUR_PROJECT_NUMBER",
    "project_id": "YOUR_PROJECT_ID",
    "storage_bucket": "YOUR_STORAGE_BUCKET.firebasestorage.app"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:YOUR_PROJECT_NUMBER:android:YOUR_APP_ID",
        "android_client_info": {
          "package_name": "com.antonchuraev.aichecklists"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "YOUR_FIREBASE_API_KEY"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
```

#### Create `local.properties.example`

Location: `local.properties.example` (repository root)

```properties
# Firebase Configuration
# Get google-services.json from Firebase Console:
# https://console.firebase.google.com/project/aichecklists-40230/settings/general

# Gemini API Key (for Cloud Functions deployment)
# Generate from: https://aistudio.google.com/app/apikey
GEMINI_API_KEY=your_key_here

# Optional: Android SDK path
sdk.dir=/Users/Admin/AppData/Local/Android/Sdk
```

#### Create `firebase-functions/.env.example`

Location: `firebase-functions/.env.example`

```bash
# Google Cloud Project ID
PROJECT_ID=aichecklists-40230
REGION=us-central1

# Gemini API Key (store as Google Cloud Secret)
# DO NOT commit this file with actual values
GEMINI_API_KEY=your_key_here

# Firebase Admin SDK (for server-side functions)
# Download from Firebase Console → Project Settings
FIREBASE_SERVICE_ACCOUNT=path/to/service-account.json
```

#### Update `.gitignore` (Already Configured)

The repository `.gitignore` already includes good coverage:

```gitignore
# Firebase config files (contain API keys!)
google-services.json
GoogleService-Info.plist

# Google Service Account keys (NEVER commit!)
*firebase-adminsdk*.json
*-service-account*.json
*serviceaccount*.json
service-account*.json
aichecklists-*.json
```

**Verify nothing is committed:**
```bash
git log --all -S "AIzaSy" -- "*.json"  # Should return no results
```

### Prevention Strategy 3: CI/CD Secrets Management

**Goal**: Inject sensitive values at build/deployment time without storing in repository.

#### GitHub Actions (If Applicable in Future)

Create `.github/workflows/build.yml`:

```yaml
name: Build & Deploy

on:
  push:
    branches: [main, master]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      # Inject Firebase config at build time
      - name: Create google-services.json
        run: |
          echo '${{ secrets.GOOGLE_SERVICES_JSON }}' > composeApp/google-services.json

      # Build Android APK
      - name: Build Android
        run: ./gradlew composeApp:assembleDebug

      # Firebase Cloud Functions deployment
      - name: Deploy Cloud Functions
        env:
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
          GOOGLE_CLOUD_PROJECT: ${{ secrets.GOOGLE_CLOUD_PROJECT }}
        run: |
          cd firebase-functions
          chmod +x deploy.sh
          ./deploy.sh
```

#### Google Cloud Secret Manager (Current Production)

Secrets are properly stored in Google Cloud Secret Manager and accessed by Cloud Functions:

```bash
# Verify secrets are NOT in code
gcloud secrets list
# Output: gemini-api-key (managed separately, not in repo)

# Deploy functions with secret injection
gcloud functions deploy analyze_and_fill_checklist \
    --set-secrets="GEMINI_API_KEY=gemini-api-key:latest"
```

**Best Practice**: Keep `deploy.sh` script generic - actual API key injected by:
1. Environment variable: `export GEMINI_API_KEY=...`
2. Google Cloud Secrets: `--set-secrets=GEMINI_API_KEY=gemini-api-key:latest`
3. Never hardcoded in deploy script

### Prevention Strategy 4: API Key Restrictions

**Goal**: Limit blast radius if a key is compromised.

#### Firebase API Key Restrictions (Console)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Project: `aichecklists-40230`
3. Settings → Project Settings
4. Service Accounts tab → Click on the API key
5. Configure restrictions:

**Android Key Restrictions:**
```
Application restrictions:
  - Android apps
  - Package name: com.antonchuraev.aichecklists
  - SHA-1 fingerprint: [your debug SHA-1]

API restrictions:
  - Only allow these APIs:
    - Cloud Storage API
    - Firebase Analytics
    - Firebase Database API
    - Firebase Remote Config API
```

**Get Android Debug SHA-1:**
```bash
# Windows
keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android | findstr SHA1

# macOS/Linux
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

**Release Key Restrictions:**
```bash
# Get release SHA-1 from keystore
keytool -list -v -keystore release.keystore | grep SHA1
```

#### Gemini API Key Restrictions (AI Studio)

1. Go to [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Click on API key settings
3. Configure:
   - **Application restrictions**: HTTP referrers
   - **API restrictions**: Only `Generative Language API`
   - **Quota & usage limits**: Set daily quota to prevent abuse

### Prevention Strategy 5: Regular Audit with Secret Scanning Tools

**Goal**: Detect leaked credentials in repository history.

#### TruffleHog (Comprehensive Scanning)

```bash
# Install
pip install truffleHog

# Scan entire repository for secrets
truffleHog filesystem . \
    --fail \
    --json \
    --regex \
    --entropy=True

# Scan git history
truffleHog git . \
    --since-commit f999fae \  # Start from recent commits
    --json
```

#### GitLeaks (Git-focused)

```bash
# Install
brew install gitleaks  # macOS
choco install gitleaks  # Windows

# Scan git history
gitleaks detect --source git --recursive --verbose

# Scan specific branch
gitleaks detect --source git --recursive --verbose --log-opts="--all"

# Configuration: create gitleaks-config.toml
cat > gitleaks-config.toml << 'EOF'
[[rules]]
id = "firebase-api-key"
regex = "AIzaSy[A-Za-z0-9_-]{35}"
entropy = 4.0
keywords = ["AIzaSy", "firebase"]

[[rules]]
id = "google-services-json"
regex = "\"api_key\".*?AIzaSy[A-Za-z0-9_-]+"
entropy = 4.0
EOF

gitleaks detect --config gitleaks-config.toml
```

#### SAST Tools (Code Scanning)

- **SonarQube** (local scanning)
- **GitHub Advanced Security** (if using GitHub)
- **GitGuardian** (monitors for leaked secrets)

### Prevention Strategy 6: Documentation in CLAUDE.md

**Goal**: Ensure all team members understand key setup and security requirements.

#### Add to CLAUDE.md Setup Section

```markdown
## Sensitive Data: Setup & Security

### Required Files (NOT in Repository)

These files must be obtained from Firebase/Google Cloud console and stored locally:

| File | Source | Location | Permissions |
|------|--------|----------|-------------|
| `google-services.json` | [Firebase Console](https://console.firebase.google.com/project/aichecklists-40230/settings/general) | `composeApp/` | Add to `.gitignore` ✓ |
| `GoogleService-Info.plist` | Firebase Console (iOS) | `iosApp/` | Add to `.gitignore` ✓ |
| `local.properties` | Generate locally | Repository root | Add to `.gitignore` ✓ |
| `.env` | Generate locally | `firebase-functions/` | Add to `.gitignore` ✓ |

### Setup Steps for New Developers

1. Clone repository
2. Download `google-services.json` from [Firebase Console](https://console.firebase.google.com/)
   - Project: aichecklists-40230
   - Go to Settings → Project Settings → Your apps → Download google-services.json
3. Place in `composeApp/google-services.json`
4. Verify file is ignored: `git status google-services.json` (should show as ignored)
5. For Firebase functions: Set environment variable
   ```bash
   export GEMINI_API_KEY=your_key_from_aistudio
   ```

### API Key Security

- **Firebase API keys**: Restricted to Android package + SHA-1 fingerprint
- **Gemini API key**: Restricted to Cloud Functions deployment only
- **Service Account keys**: Stored in Google Cloud Secret Manager, never in repo
- **Never**: Commit or share actual credentials

### If Key is Compromised

1. **Immediately regenerate** in [Firebase Console](https://console.firebase.google.com/)
2. **Remove from git history** (see below)
3. **Update all local copies** of google-services.json
4. **Alert team** via secure channel
5. **Rotate** Gemini API key in [AI Studio](https://aistudio.google.com/app/apikey)

### Remove Secrets from Git History

If a key was accidentally committed:

```bash
# Option 1: BFG Repo-Cleaner (recommended for large repos)
brew install bfg
bfg --delete-files google-services.json

# Option 2: git-filter-branch (slower but built-in)
git filter-branch --tree-filter 'rm -f composeApp/google-services.json' -- --all

# Option 3: git filter-repo (modern, faster)
pip install git-filter-repo
git filter-repo --path composeApp/google-services.json --invert-paths

# After cleanup
git push origin --force-with-lease master
```
```

### Prevention Strategy 7: Secure Development Workflow

#### During Development

```bash
# ✓ GOOD: Use example files as reference
cp composeApp/google-services.json.example composeApp/google-services.json
# ... fill in actual values from Firebase Console

# ✓ GOOD: Verify file won't be committed
git status composeApp/google-services.json
# Output: (ignored, not tracked)

# ✗ BAD: Commit sensitive files
git add composeApp/google-services.json  # Won't be added (already in .gitignore)

# ✗ BAD: Force-add ignored files
git add -f composeApp/google-services.json  # DON'T DO THIS
```

#### Before Pushing

```bash
# Check for secrets in staged changes
git secrets --scan

# Scan entire branch
git log --oneline -n 50 | while read commit; do
    git show $commit | git secrets --scan
done

# Check for Firebase project ID patterns
git log -p --all | grep -i "aichecklists-40230\|firebase.*key"
```

### Prevention Strategy 8: Secrets Rotation Schedule

| Secret | Rotation Frequency | Method | Owner |
|--------|-------------------|--------|-------|
| Firebase API Key | When compromised + yearly | [Firebase Console](https://console.firebase.google.com/) | Project Owner |
| Gemini API Key | When compromised + yearly | [AI Studio](https://aistudio.google.com/app/apikey) | Dev Team |
| Cloud Functions Secrets | When compromised + yearly | `gcloud secrets versions add gemini-api-key --data-file=-` | DevOps |
| Android Release Keystore | When compromised + app update | Xcode provisioning | Project Owner |

### Monitoring & Alerts

1. **Google Cloud Console**:
   - Enable Cloud Audit Logs for API key usage
   - Set up alerts for API quota exceeded

2. **Firebase Console**:
   - Monitor realtime database access patterns
   - Review Firebase Security Rules regularly

3. **GitHub Security** (if applicable):
   - Enable secret scanning
   - Configure branch protection rules

### Checklist: API Key Security Implementation

- [ ] `.gitignore` includes all credential files (already done ✓)
- [ ] `google-services.json` removed from git history
- [ ] Firebase API key regenerated and restricted
- [ ] `google-services.json.example` created with placeholders
- [ ] `local.properties.example` created and documented
- [ ] `firebase-functions/.env.example` created
- [ ] git-secrets pre-commit hooks installed
- [ ] Team members notified of security setup
- [ ] Documentation added to CLAUDE.md
- [ ] GitLeaks scan runs clean: `gitleaks detect --recursive`
- [ ] Firebase API key restricted by package + SHA-1
- [ ] Gemini API key restricted to Cloud Functions
- [ ] CI/CD configured to inject secrets (no hardcoding)
