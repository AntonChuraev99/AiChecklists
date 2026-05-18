# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Gisti - AI Checklists** is a Kotlin Multiplatform (KMP) application for Android, iOS, and Web. Built with Jetpack Compose Multiplatform.

| Target | Status | Where |
|--------|--------|-------|
| Android | Production | Google Play |
| Web (wasmJs) | Production | <https://checklists.gisti.workers.dev/> (Cloudflare Workers Static Assets) |
| iOS | Code-only, not released | — |

> **iOS release strategy**: iOS version will be published after Android revenue covers the Apple Developer Program fee ($99/year). Until then, iOS target exists in code but is not actively released.

> **Web release strategy**: web target uses Compose Multiplatform's wasmJs renderer (Skiko canvas), Room 3.0 with the SQLite OPFS Web Worker driver for persistence, and the Firebase JS SDK (loaded as ESM modules) for Auth/Remote Config/Analytics. AI flow goes through CORS-enabled Cloud Functions; direct Gemini calls from the browser are not allowed.

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

Gisti transforms any content into actionable checklists using AI. **The AI Chat Assistant is the flagship interaction layer** — users converse naturally and the app reasons over their lists.

| Feature | Description | Input Formats |
|---------|-------------|---------------|
| **1. AI Chat Assistant** | Natural-language assistant — add/edit/find items, set reminders, plan your week through conversation. Tiered routing: free local commands (0 credits), cheap classifier (1 credit), full reasoning (3 credits). Persists conversation history across sessions. | Text (multi-turn dialogue) |
| **2. Create via AI** | Generate a new checklist from any content | Photo, PDF, Text, Link, Voice |
| **3. Fill via AI** | Auto-fill an existing checklist based on new content | Photo, PDF, Text, Link, Voice |
| **4. Export** | Share your checklist in convenient formats | PDF, Plain Text |

### Business Model

- **Free tier**: Limited checklists and fills, daily AI credits
- **Premium** ($1.99/mo): Unlimited checklists/fills, 300 AI credits daily, priority support
- 3-day free trial for new users

## Project Language

**All code comments, documentation, and commit messages must be in English.**

## Build Commands

> **AGP 9 module split (since 2026-05-10):** `composeApp` is now a **KMP library** (commonMain + androidMain + iosMain + wasmJsMain). The Android **application** lives in `:androidApp`. Use `:androidApp` for any `assemble*` / `bundle*` / `install*` / `connectedAndroidTest` task. KMP/iOS/wasmJs tasks stay on `:composeApp`. See `docs/solutions/build-system/agp-9-migration-2026-05-10.md`.

```bash
./gradlew build                                       # Full build for all targets
./gradlew androidApp:assembleDebug                    # Android debug APK
./gradlew androidApp:bundleRelease                    # Android release bundle (AAB)
./gradlew androidApp:connectedAndroidTest             # Run Android instrumented tests
./gradlew composeApp:wasmJsBrowserDevelopmentRun --continuous  # Web dev server on http://localhost:9090/
./gradlew composeApp:wasmJsBrowserDistribution        # Web production bundle (Cloudflare Workers asset dir)
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode.

### Running on Emulator

```bash
$ANDROID_SDK/emulator/emulator -list-avds          # List emulators
$ANDROID_SDK/emulator/emulator -avd Pixel_9 &      # Start emulator
./gradlew androidApp:installDebug                   # Build and install (NB: :androidApp, not :composeApp)
adb shell am start -n com.antonchuraev.aichecklists/com.antonchuraev.homesearchchecklist.MainActivity
```

Available emulators: `Pixel_9`, `Medium_Phone_API_36.1`

### Deploying Web (Cloudflare Workers)

Production site: <https://checklists.gisti.workers.dev/>

```bash
./gradlew composeApp:wasmJsBrowserDistribution        # Build production wasm bundle (~26 MB)
npx wrangler@4 deploy                                 # Push to Cloudflare Workers (Static Assets only)
```

CI/CD: pushes to `master` trigger `npx wrangler deploy` → production; pushes to other branches trigger `npx wrangler versions upload` → preview URL. Configuration lives in `wrangler.jsonc` (Worker name `checklists`, asset directory `composeApp/build/dist/wasmJs/productionExecutable`, SPA fallback via `not_found_handling: single-page-application`).

Before deploying: ensure `local.properties` has `FIREBASE_WEB_API_KEY` + `FIREBASE_WEB_APP_ID` so the build-time `generateWasmInitJs` Gradle task can substitute them into `init.js`. Cloud Functions must be deployed with CORS-aware handlers (`firebase deploy --only functions:analyze_and_fill_checklist,functions:generate_checklist,functions:register_user,functions:restore_credits_after_purchase,functions:get_usage_stats,functions:get_credits_info`).

## Architecture

### Module Structure

```
composeApp/              # Main application entry points (Android/iOS/wasmJs)
  androidMain/           # Android-specific (widget, notifications, AlarmManager, in-app review)
  iosMain/               # iOS-specific (UIKit bridges, native sharing)
  wasmJsMain/            # Web-specific (Firebase JS SDK init, OPFS Room driver, browser pickers)
    resources/
      init.js.template   # Firebase JS SDK ESM init + globalThis bridges (file picker, share, print)
  widget/                # Glance home screen widget (Android only)
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

### Compose Resources in KMP-Android Library Modules

After AGP 9 migration the new AKMP DSL (`kotlin { android { ... } }`) **does not enable Android resources by default**. Any KMP-library module that ships its own `composeResources/` (files, drawables, fonts, values) **must** opt in explicitly — otherwise `Res.readBytes`/`Res.getDrawable` will throw `MissingResourceException` at runtime because the assets never get packed into the APK.

```kotlin
kotlin {
    android {
        namespace = "..."
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
        androidResources {           // ← REQUIRED for any module with composeResources/
            enable = true
        }
    }
    // ...
}
```

Currently the block is present in `composeApp`, `core/designsystem`, and `feature/create` (the only modules with their own `composeResources/`). The other 19 KMP-library modules don't need it today, but **add it the moment you add `src/commonMain/composeResources/`** — see `docs/solutions/build-system/agp9-feature-module-androidresources-fix-2026-05-11.md` for the full root-cause analysis.

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

Room 3.0 with KSP across all targets (Android, iOS, wasmJs). Database classes in `feature:checklist`. Platform-specific `DatabaseBuilder` uses `expect/actual`. Web target uses `WebWorkerSQLiteDriver` over OPFS for persistence (survives page reload). Android/iOS use the standard bundled SQLite driver.

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

### Per-Item Actions Belong in `ItemDetailsSheet`

**RULE**: Any new per-item action, toggle, or setting (priority/star, due date, tags, color, archive, etc.) MUST be added as a row in `ItemDetailsSheet` — NOT as a button on `ChecklistItemCard`.

**Why**: `ChecklistItemCard` uses a 30/70 hit-zone split (left 30% toggles checkbox, right 70% opens `ItemDetailsSheet`). Adding new clickable elements to the card breaks this pattern, eats touch targets, and turns the card into Frankenstein UI as features accumulate. See `docs/solutions/ui-improvements/checklist-item-card-sheet-redesign-2026-05-05.md`.

**On the card itself, only lightweight visual indicators are allowed:**
- Reminder chip (existing)
- Priority/star icon (read-only — tap opens sheet to toggle)
- Tag color stripe (future)

**These indicators MUST NOT have their own `clickable` modifier or hit-zone.** They are read-only signals; toggling happens inside the sheet.

```kotlin
// CORRECT: indicator is visual only, sheet handles toggle
Row {
    Text(item.text)
    if (item.priority > 0) Icon(Icons.Filled.Star, null)  // no clickable
}

// WRONG: button on card, breaks 30/70 hit-zone
Row {
    Text(item.text)
    IconButton(onClick = onToggleStar) { Icon(Icons.Filled.Star, null) }
}
```

**For the action itself — add a row in `ItemDetailsSheet`:**
```kotlin
ItemDetailsSheet(...) {
    Row(...)  // Reminder
    Row(...)  // Note
    Row(onClick = onTogglePriority) {  // ← new actions go HERE
        Icon(Icons.Filled.Star, null)
        Text(if (item.priority > 0) "Remove importance" else "Mark as important")
    }
    Row(...)  // Delete
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
Version-grouped release feed shown from the drawer ("Updates"). Content is **bundled in-code** at `feature/updatefeed/.../data/UpdateFeedContent.kt` — no Remote Config override. Each post is tied to a specific app version, so editing the feed requires a code change + new APK release. Posts use **main-version** format only (`1.X`, never `1.X.Y`); patch versions fold to main at the repository layer. Store release notes (Google Play copy) and feature posts are de-duplicated — the same line never appears twice across the feed.

**Hard rules** (break at your peril):
- **Every shipped product change earns a post** — features, big bug fixes, notable perf wins. Skip invisible internal work (refactors, deps, build infra) **and** localization / new-language additions (i18n plumbing is not a product feature — every user who needed the new language just sees the app working). The Updates Feed is where returning users learn what's new; don't make them hunt.
- **Posts about important user-facing features SHOULD carry a CTA button.** Allowed deeplinks (resolved by `UpdateFeedDeepLinkHandler` → `AppNavigator`): `gisti://ai_chat`, `gisti://calendar`, `gisti://create?viewMode=weekly`, `gisti://widget_instruction`, `gisti://templates`, `gisti://analyze`, `gisti://create`, `gisti://home`. To add a new host: extend `AppNavigator` interface + impl, extend the handler, cover with a unit test in `UpdateFeedDeepLinkHandlerTest`, then document it in `docs/guidelines/updates-feed.md` §4. Premium gates live on the destination screen / use case, not in the handler.
- **Skip CTA** when the change is visual polish, a perf win, a bug fix, or an in-place behavior with no standalone destination — the post text alone is the notification.
- **CTA label must name the destination** ("Open AI Chat", "Open Calendar", "Add widget") — never generic ("Open", "Try it now"). Generic labels were tried in PR `f56ec05` and produced measurable-zero click-through.
- `ReleaseCard` state MUST use `rememberSaveable` (not `remember`) — LazyColumn recycles items; plain `remember` loses expanded state on scroll.
- Card tap target MUST be lifted to the outer `AppCard` modifier (not the inner header `Row`), otherwise `CardPadding` eats the edges.
- Spacing between header and `AnimatedVisibility` body MUST live **inside** the animated content (as `padding(top = SpacingMd)` on the inner column), not as outer `Arrangement.spacedBy` — otherwise the collapse animation snaps by 12dp at the end.
- New posts must ship together with the version they describe — never reference a feature that is not yet in the released APK.

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
