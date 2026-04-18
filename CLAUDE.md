# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Gisti - AI Checklists** is a Kotlin Multiplatform (KMP) application for Android and iOS. Built with Jetpack Compose Multiplatform.

> **iOS release strategy**: iOS version will be published after Android revenue covers the Apple Developer Program fee ($99/year). Until then, iOS target exists in code but is not actively released.

### Public Repository

This is an **open-source public repository**. All code, commits, and PR history are visible to everyone.

**NEVER commit:**
- API keys, tokens, passwords, or secrets of any kind
- `google-services.json` / `GoogleService-Info.plist` (Firebase config with API keys)
- Service account JSON files
- `.env` files with real values
- Security audit documents that reference real credentials

**Already in `.gitignore`:**
- `.claude/` — local Claude Code config
- `docs/` — local working notes (some contain real API keys)
- `commonMain/` — generated/vendored stubs
- `SECURITY.md`, `SECURITY_DELIVERY_SUMMARY.txt` — security docs with real keys
- `hosting/.firebase/` — Firebase cache

**Safe to commit** (not secrets):
- Firebase project ID (`aichecklists-40230`) — public by design, visible in every Firebase URL and in the APK
- `.firebaserc` — project alias mapping
- `firebase.json` — hosting/functions config (no keys)

Before committing any new file, verify it does not contain patterns like `AIzaSy*`, hardcoded tokens, or credentials.

### Product Concept

Gisti transforms any content into actionable checklists using AI:

| Feature | Description | Input Formats |
|---------|-------------|---------------|
| **1. Create via AI** | Generate a new checklist from any content | Photo, PDF, Text, Link, Voice |
| **2. Fill via AI** | Auto-fill an existing checklist based on new content | Photo, PDF, Text, Link, Voice |
| **3. Export** | Share your checklist in convenient formats | PDF, Plain Text |

### Business Model

- **Free tier**: Limited checklists and fills, daily AI credits
- **Premium** ($1.99/mo): Unlimited checklists/fills, 300 AI credits daily, priority support
- 3-day free trial for new users

## Project Language

**All code comments, documentation, and commit messages must be in English.**

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
$ANDROID_SDK/emulator/emulator -list-avds          # List emulators
$ANDROID_SDK/emulator/emulator -avd Pixel_9 &      # Start emulator
./gradlew composeApp:installDebug                   # Build and install
adb shell am start -n com.antonchuraev.aichecklists/com.antonchuraev.homesearchchecklist.MainActivity
```

Available emulators: `Pixel_9`, `Medium_Phone_API_36.1`

## Architecture

### Module Structure

```
composeApp/              # Main application entry points (Android/iOS)
  widget/                # Glance home screen widget
  csat/                  # Customer satisfaction survey + in-app review
  notification/          # Reminder scheduling

core/
  common/api|impl        # AppViewModel, AppDispatchersProvider, AppLogger
  designsystem/          # Compose theme and reusable UI components
  datastore/api|impl     # Preferences persistence
  navigation/api|impl    # AppNavigator and AppNavRoute definitions
  remoteconfig/api|impl  # Firebase Remote Config abstraction

feature/
  checklist/             # Domain models, Room database, repository, reminder repeat rules
  create/                # Create checklist + templates screens
  home/                  # Main screen, checklist detail, fills, reminders UI
  onboarding/            # First-run experience
  splash/                # Launch screen
  analyze/               # AI-powered analysis (Gemini integration)
  paywall/               # Subscriptions (RevenueCat)
  sharing/               # Export checklists (text/PDF)
  user/                  # User profile, device ID, credits API
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
Analyze(checklistId?, fillDefault?), AnalyzeResultPreview
Paywall(source), SubscriptionStatus(showSuccessMessage?)
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

### Component Naming Convention

| Category | Prefix | Location | Examples |
|----------|--------|----------|----------|
| Material3 wrappers | `App` | `components/` | `AppButton`, `AppCard`, `AppSwitch` |
| Layout containers | `App` | `containers/` | `AppScaffold` |
| Compound/semantic | Descriptive name | `components/` | `EmptyState`, `AddItemInputField` |
| Feature illustrations | Descriptive name | `illustrations/` | `CreateViaAiIllustration` |

**Rules:**
- Wrapping a single Material3 widget -> `App` + widget name (`AppSwitch`, `AppTextField`)
- Composing multiple widgets into a reusable pattern -> descriptive name (`EmptyState`, `AddItemInputField`)
- Always use design system components instead of raw Material3 when available (e.g. `AppSwitch` not `Switch`)

### Components
- `AppButton` / `AppButtonSecondary` / `AppButtonText` / `AppButtonDestructive`
- `AppCard` - 12dp corners, 2dp elevation
- `AppSwitch` - switch with visible unchecked track (outlineVariant)
- `AppTextField` - outlined text field with keyboard options support
- `EmptyState` - centered icon + title + description
- `AddItemInputField` - inline text input + add button
- `AppScaffold` - screen wrapper with top bar (auto-handles system insets)

### System Insets (Edge-to-Edge)

**IMPORTANT**: All screens MUST properly handle system bars to avoid UI overlapping status bar or navigation bar.

**Screens WITH AppScaffold**: Automatic via `WindowInsets`. No extra work needed.

**Screens WITHOUT AppScaffold** (e.g. `OnboardingScreen`, `PaywallScreen`, `SplashScreen`) **MUST** add:
```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
)
```

## Key Patterns

- **API/impl split**: Core modules expose interfaces in `api`, implementations in `impl`
- **expect/actual**: Platform-specific code (logging, database, file pickers, audio, build config, reminders, in-app review)
- **StateFlow**: All reactive state management
- **Typesafe project accessors**: Reference modules as `projects.core.common.api`
- **AppBuildConfig**: Debug/release build detection via expect/actual pattern

### Checklist Template vs Fill

`Checklist` (template) defines items; `ChecklistFill` stores checked/note state per session. The default fill mirrors the template. `ChecklistFill` also supports `coverImagePath` for fill cover images.

**When adding items from the detail screen**, update BOTH:
```kotlin
repository.updateFill(updatedFill)                   // fill — for detail screen
repository.updateChecklistTemplate(updatedChecklist)  // template — for edit screen
```

- `updateChecklist()` — updates template AND re-syncs fill (regenerates all fill item IDs). Use for Edit screen saves.
- `updateChecklistTemplate()` — updates template ONLY, no fill sync. Use when fill is already updated separately.

### KMP Platform Constraints

APIs that do NOT work in `commonMain` — verify availability before using:

| API | Status in KMP | Alternative |
|-----|---------------|-------------|
| `BackHandler` | Android-only (`activity-compose`) | `WindowInsets.ime` for keyboard back |
| `onFocusChanged` for keyboard hide | Focus stays `true` when keyboard hides | Track `WindowInsets.ime.getBottom()` |
| `Switch` + `Row(clickable)` | Double-toggle bug | `AppSwitch(onCheckedChange = null)`, Row handles click |

Per-entity preferences (e.g., `separateCompleted`, `autoDeleteCompleted`, `position`) belong in Room, not DataStore. DataStore is for global app preferences only.

## UI Best Practices

### Text in HorizontalPager

**IMPORTANT**: Text elements inside `HorizontalPager` MUST have `fillMaxWidth()` modifier to prevent overflow.
```kotlin
// CORRECT: Text knows its width, centers properly
Text(text = "...", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
```

### Avoid Double Padding

When a parent already has horizontal padding, children should NOT add their own:
```kotlin
Column(modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal)) {
    Text(modifier = Modifier.fillMaxWidth())                    // Correct
    Text(modifier = Modifier.padding(horizontal = 16.dp))      // Wrong — double padding
}
```

## Features

### AI Analyze (`feature/analyze/`)
Generates checklist items from various inputs (Photo, PDF, Text, WebLink, Voice) using Gemini AI. Key classes: `GeminiAiAnalyzer`, `AnalyzeViewModel`, `AnalyzeResultPreviewScreen`. API key via `GeminiConfig` injected from app module.

### Templates (`feature/create/`)
Pre-defined checklist templates from Firebase Remote Config (key: `templates_json`). Model: `ChecklistTemplate(id, name, icon, category, items)`. Flow: Templates -> TemplatePreview -> "Use Template" -> creates checklist.

### Paywall (`feature/paywall/`)
Premium subscriptions via RevenueCat. Free user limits: `UserLimits(maxChecklists, maxFillsPerChecklist, currentChecklistCount, isPremium)`. Includes trial timeline (Blinkist style). `PurchasesDelegate` handles pending transactions. See `docs/solutions/ui-improvements/paywall-trial-timeline.md`.

### Sharing (`feature/sharing/`)
Export checklists as `ShareFormat.Text` or `ShareFormat.Pdf`. Platform-specific `ShareLauncher` and `PdfGenerator` via expect/actual.

### Updates Feed (`feature/updatefeed/`)
Version-grouped release feed shown from the drawer ("Updates"). Content sourced from Firebase Remote Config (key: `update_feed_json`) with in-code fallback in `RemoteConfigDefaults`. Posts use **main-version** format only (`1.X`, never `1.X.Y`); patch versions fold to main at the repository layer. Store release notes (Google Play copy) and feature posts are de-duplicated — the same line never appears twice across the feed.

**Hard rules** (break at your peril):
- Action buttons only for **non-obvious** features. Currently the only allowed deeplink is `gisti://widget_instruction`. CTAs pointing at home/analyze/templates/create duplicate the bottom nav and drawer — **do not add them**.
- `ReleaseCard` state MUST use `rememberSaveable` (not `remember`) — LazyColumn recycles items; plain `remember` loses expanded state on scroll.
- Card tap target MUST be lifted to the outer `AppCard` modifier (not the inner header `Row`), otherwise `CardPadding` eats the edges.
- Spacing between header and `AnimatedVisibility` body MUST live **inside** the animated content (as `padding(top = SpacingMd)` on the inner column), not as outer `Arrangement.spacedBy` — otherwise the collapse animation snaps by 12dp at the end.
- After merging changes to `UPDATE_FEED_JSON`, mirror the same JSON into Firebase RC Console — the in-code value is fallback only.

Full feature playbook (content rules, icon whitelist, publication flow, anti-patterns): **`docs/guidelines/updates-feed.md`**.

### Reminders

Checklist model includes reminder and preference fields:
```kotlin
val reminderAt: Long? = null,              // One-shot reminder timestamp
val repeatRule: ReminderRepeatRule? = null, // Recurring schedule (daily, weekly, etc.)
val repeatTimeOfDayMinutes: Int? = null,    // Time of day for repeat
val repeatNextAt: Long? = null,             // Next repeat fire time
val repeatOccurrenceCount: Int = 0,         // How many times repeated
val separateCompleted: Boolean = false,      // Group completed items separately
val position: Int = 0,                       // Drag-and-drop ordering
val autoDeleteCompleted: Boolean = false     // Auto-remove checked items
```

`ReminderRepeatRule` (in `feature/checklist/domain/model/`): Daily, Weekly, Monthly, Weekdays, Biweekly, Quarterly, Yearly, Custom. Android impl: `ReminderReceiver`, `ReminderScheduler` (AlarmManager), `BootCompletedReceiver`. Free users: 1 recurring reminder.

### Widget (`composeApp/src/androidMain/.../widget/`)
Android home screen widget (Jetpack Glance). Binds to specific checklist via `WidgetConfigActivity`. Items togglable from home screen. `WorkManager` syncs state. Deep-links to `ChecklistDetail`.

### CSAT Survey (`composeApp/src/commonMain/.../csat/`)
3-step survey (rating -> chips -> free text) triggered by `CsatManager` at usage milestones. Offers Google Play in-app review via `InAppReviewLauncher` (expect/actual).

### Debug (`feature/debug/`)
Debug builds only. Access: Volume Up -> Down -> Up (Android). Screens: `DebugScreen` (reset/test data), `StoreScreenshotScreen` (4 marketing pages). `AppBuildConfig` (expect/actual) controls debug features.

## Localization

Strings in `core/designsystem/src/commonMain/composeResources/values/strings.xml`.

```kotlin
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource

stringResource(Res.string.your_key)
```

Naming: prefix with screen (`main_`, `create_`, `analyze_`, `paywall_`), snake_case.

## Credit Restore Architecture

After purchase/restore, credits must be explicitly restored via Cloud Function `restore_credits_after_purchase` (NOT automatic).

```
PurchaseProductUseCase / RestorePurchasesUseCase
  -> UserDataRepository.restoreCreditsAfterPurchase()
    -> UserDataRepositoryImpl (retry up to 3 times, backoff 2s/4s)
      -> UserApiService -> POST /restore_credits_after_purchase
        -> Cloud Function verifies premium via RevenueCat REST API
        -> Writes to Firestore: is_premium=true, ai_credits=300
      -> On success: saves to DataStore (local cache)
```

### Critical Rules
- **Always use UseCase** for restoring purchases — `RestorePurchasesUseCase` (not `paywallRepository.restorePurchases()` directly)
- **SplashViewModel.linkWithPaywall()** must use `RestorePurchasesUseCase` for returning users
- **Retry is in the repository** (`UserDataRepositoryImpl`) — all callers get retry automatically
- **`PurchaseProductUseCase`** ignores `Result<Int>` from `restoreCreditsAfterPurchase()` — purchase already successful

### Firestore Collections

| Collection | Purpose |
|-----------|---------|
| `users/{userId}` | `is_premium`, `ai_credits`, `credits_restored_at` |
| `credits_restore_log` | Log of successful restore operations |
| `credits_refill_log` | Log of daily premium credit refills |

## Navigation Flow

```
Splash -> Onboarding -> Main
                        |-> ChecklistDetail -> FillDetail / FillsList / ShareChecklist / Reminder sheet
                        |-> Templates -> TemplatePreview
                        |-> CreateChecklist
                        |-> Analyze -> AnalyzeResultPreview
                        |-> Paywall(source) / SubscriptionStatus
                        |-> CSAT Survey (bottom sheet)
                        +-> Debug -> StoreScreenshot (debug only)

Widget: WidgetConfigActivity -> select checklist -> toggle items / deep-link to detail
```

## Copy Guidelines

- Simple, clear, benefit-focused. Action-oriented labels. No jargon.
- Do: "Create Checklist", "Fill via AI", "Save", "Get Started"
- Don't: "Add New", "AI Analyze", "Submit", "Continue"

## Dependencies

Versions managed in `gradle/libs.versions.toml`. Key: Kotlin 2.3.0, Compose Multiplatform 1.9.3, Koin 4.1.1, Room 2.8.4, RevenueCat 2.2.17, Firebase BOM 33.7.0, Generative AI KMP 0.9.0-1.1.0.

## Security

Full security playbook: `docs/security-playbook.md` (git-secrets, TruffleHog, GitLeaks, API key restrictions, rotation schedule).

Key rules (also in Public Repository section above):
- **NEVER commit** `google-services.json`, API keys, `.env` files
- Verify new files don't contain `AIzaSy*` patterns before committing
- Firebase API keys restricted by package + SHA-1
- Gemini API key in Google Cloud Secret Manager

## Unit Economics

Details in `docs/unit-economics.md`. Summary: $1.99/mo subscription, 15% Google commission, ~$0.0002/AI request (gemini-2.5-flash-lite). Positive unit economics even at max usage (300 req/day = 65% margin).

### Limits (Remote Config)

| Param | Free | Premium |
|-------|------|---------|
| AI requests/day | 10 | 300 |
| Max checklists | 4 | unlimited |
| Max fills/checklist | 5 | unlimited |
| Recurring reminders | 1 | unlimited |
