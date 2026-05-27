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

> **Web platform**: Full production platform developed in parallel with Android. Uses Compose Multiplatform wasmJs renderer (Skiko canvas), Room 3.0 with SQLite OPFS Web Worker driver for persistence, and Firebase JS SDK (ESM modules) for Auth/Remote Config/Analytics. AI flow goes through CORS-enabled Cloud Functions; direct Gemini calls from the browser are not allowed. Deployed to Cloudflare Workers Static Assets.

### Repository Visibility

This repository is **private** (switched from public on 2026-05-24 after a Gemini API key leak incident). External secret scanners no longer index the history, but old leaked secrets stay in git history forever — treat **everything that has ever been committed** as if it could be public again.

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

**Safe to commit** (not secrets, public by design):
- Firebase project ID (`aichecklists-40230`) — visible in every Firebase URL and in the APK
- `.firebaserc` — project alias mapping
- `firebase.json` — hosting/functions config (no keys)

Before committing any new file, verify it does not contain patterns like `AIzaSy*`, hardcoded tokens, or credentials. `.gitleaks.toml` is configured for the project; wire it into a pre-commit hook before re-opening the repo to the public.

### Product Concept

Gisti turns anything into a checklist with AI. It works on **Android** and **in the browser** (web) — same features, same data. Web is a full platform developed in parallel with Android, not a companion or lite version.

**The AI Chat Assistant is the flagship interaction layer** — users converse naturally and the app manages their lists, reminders, and schedule. Secondary AI flows (Create/Fill) transform any content into structured checklists.

> Full product feature catalog with tier details, platform parity matrix, and gap analysis: `docs/product-features.md`

#### Killer Features (Tier 1 — lead with these in onboarding & marketing)

| Feature | Description | Tier |
|---------|-------------|------|
| **AI Chat Assistant** | Natural-language assistant — add/edit/find items, set reminders, plan your week through conversation. Text + voice input. Three-tier routing: free local commands (0 credits), cloud classifier (1 credit), full reasoning (3 credits). | Free (limited) / Premium |
| **Create via AI** | Generate a checklist from any content: Photo, PDF, Text, Link, Voice. AI detects content language. | Free (limited) / Premium |
| **Reminders & Smart Scheduling** | Per-checklist and per-item reminders. One-time or recurring (daily/weekly/weekdays/monthly/custom). Smart date parsing in AI Chat. Today view for daily focus. | Free (1 recurring) / Premium |
| **Works Everywhere** | Android + Web (browser). Google Sign-In syncs data across devices. Web unlocks 100 free AI credits on sign-in. | Free / Premium |

#### Strong Features (Tier 2 — drive retention)

| Feature | Description | Tier |
|---------|-------------|------|
| **Calendar View** | Day grid with all checklists and reminders. Tap a day to expand, swipe between weeks. | Premium |
| **Weekly Mode** | Any checklist as weekly planner — items grouped by weekday. | Premium |
| **Fill via AI** | Auto-fill existing checklist from new content (Photo, PDF, Text, Link, Voice). | Free (limited) / Premium |
| **47 Templates** | Ready-made checklists by category: travel, work, health, cooking, fitness, study. | Free |
| **Item Attachments** | Attach photos to any item — receipts, labels, references. | Free (3/item) / Premium |
| **Home Screen Widget** | Pin any checklist to Android home screen, tick items without opening app. | Free (Android only) |
| **Dark Theme & Material You** | Light/dark/system. Dynamic Color on Android 12+. | Free |

#### Utility Features (Tier 3 — table stakes)

Inline input & rename, drag-to-reorder, swipe-to-delete, priority stars, auto-delete completed, bulk delete, export (PDF/text), language switcher (EN/RU/System), cross-device sync, interactive onboarding.

### Business Model

- **Free tier**: 4 checklists, 5 fills/checklist, 10 AI credits/day, 1 recurring reminder
- **Premium** ($1.99/mo): Unlimited checklists/fills/reminders, 300 AI credits/day, Calendar, Weekly mode
- 3-day free trial for new users

## Project Language

**All code comments, documentation, and commit messages must be in English.**

### Marketing & Localization Priority

**Primary language: English only.** All marketing copy, ad texts, store listings, onboarding copy, and feature descriptions — produce in English only. Russian localization is NOT a default task — do it only when explicitly requested by the user, or when fixing bugs in existing RU strings.

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

### ⛔ ABSOLUTE RULE: NEVER `adb uninstall` before installing — data loss

**Why this rule exists:** On 2026-05-24 an agent did `adb uninstall` + `adb install` "for a clean state" before smoke-testing the new APK on a physical device. This wiped the user's local Room database — **all their saved checklists, fills, and reminders were permanently lost**. Auto-Backup did not save them: `backup_rules.xml` only includes `files/user/` (DataStore preferences), it does **NOT** include the Room database. Any uninstall = permanent data loss.

**Rules:**

- ❌ **NEVER** run `adb uninstall com.antonchuraev.aichecklists` (or `pm uninstall`) on any device that holds real user data.
- ❌ **NEVER** wipe app data via `adb shell pm clear com.antonchuraev.aichecklists`.
- ❌ **NEVER** factory-reset / wipe a connected device without explicit user permission.
- ✅ For re-install on the same device with the same signing key, **always** use `adb install -r <apk>` (reinstall, keep data). This is what `./gradlew installDebug` and the project's `/install-device` skill do by default.
- ✅ Uninstall is allowed **only** when ADB returns a specific error that requires it (`INSTALL_FAILED_VERSION_DOWNGRADE`, `INSTALL_FAILED_UPDATE_INCOMPATIBLE` = signature mismatch). Even then, **first ask the user via `AskUserQuestion`** — never pre-emptively, never "to make sure it's clean".
- ✅ Before any potentially destructive ADB command, name the side effect: "this will wipe checklists / reminders / chat history — proceed?"

This rule overrides any "clean state" intuition. A "dirty" device with the user's real data is **always** preferable to a clean device with their data wiped. If you need a clean state for testing, use an emulator (`Pixel_9`, `Medium_Phone_API_36.1`) — never the user's personal device.

**Backup gap to fix (P0 product issue, not blocker for this rule):** `androidApp/src/main/res/xml/backup_rules.xml` and `backup_rules_legacy.xml` currently include only `files/user/`. The Room database (`databases/`) is excluded, so even Google Auto-Backup cannot save users' checklists. Until backup_rules is widened to include `databases/checklist_database`, this absolute rule is the only safeguard against catastrophic data loss.

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

## Adaptive UI Architecture

The app renders correctly on phone (Compact), tablet (Medium), and desktop/foldable (Expanded) using a single `AdaptiveNavigationShell` composable that picks the navigation chrome — ModalDrawer, NavigationRail, or PermanentDrawer — based on `WindowSizeClass`. Navigation uses Navigation 3 (`NavDisplay` + `NavBackStack`) instead of the former Nav 2 `NavController`; there is no async channel, and all stack mutations are synchronous. `ListDetailSceneStrategy` enables two-pane layouts on Expanded for four screen pairs. Full details: `docs/adaptive-architecture.md`.

### WindowSizeClass Breakpoints

| Class | Width | Navigation chrome | Layout |
|---|---|---|---|
| Compact | < 600dp | ModalNavigationDrawer (hamburger) | Single column |
| Medium | 600–839dp | NavigationRail (left rail) | 2-column grid on grid screens |
| Expanded | ≥ 840dp | PermanentNavigationDrawer | 3-col grid + two-pane list/detail |

### Navigation Model

Navigation 3 alpha. No `NavController`. The back stack is `NavBackStack` (`SnapshotStateList<NavKey>`), mutated directly by `AppNavigatorImpl`. `NavDisplay` recomposes synchronously on every mutation — no Channel race, no `LaunchedEffect` timing issues.

Migration guide and before/after concept table: `docs/navigation3-migration.md`.

### Key Adaptive Composables

| Composable | Location | Purpose |
|---|---|---|
| `AdaptiveNavigationShell` | `composeApp/.../navigation/` | Selects Compact / Medium / Expanded chrome |
| `AppScaffold` | `core/designsystem/.../containers/` | `CenterAlignedTopAppBar` on Compact, `MediumTopAppBar` on Medium/Expanded + `exitUntilCollapsedScrollBehavior` |
| `AdaptiveContentWidth` | `core/designsystem/.../containers/` | `Modifier.adaptiveContentWidth(maxWidthDp=720)` — clamps single-column content on wide screens |
| `AdaptiveSheetOrDialog` | `core/designsystem/.../containers/` | `ModalBottomSheet` on Compact, `AlertDialog` on Expanded |
| `AppWindowSizeClass` | `core/designsystem/.../adaptive/` | `expect/actual` — `androidx.window` (Android), `window.innerWidth` (wasmJs), `LocalWindowInfo` (iOS) |

### Rule: Adding a New Top-Level Destination

When adding a new drawer destination, update **all three** of the following or the shell will not render it:

1. `DrawerDestination` sealed class — add the new entry
2. `AdaptiveNavigationShell` — add it to the destination list for all three layout variants (Compact/Medium/Expanded)
3. `App.kt` `entryProvider { }` — add the `entry<NewRoute> { }` block

Failure to update all three results in the destination being unreachable from the navigation shell even if the route is defined.

---

## Key Patterns

- **API/impl split**: Core modules expose interfaces in `api`, implementations in `impl`
- **expect/actual**: Platform-specific code (logging, database, file pickers, audio, build config, reminders, in-app review)
- **StateFlow**: All reactive state management
- **Typesafe project accessors**: Reference modules as `projects.core.common.api`
- **AppBuildConfig**: Debug/release build detection via expect/actual pattern

### Error Logging — Mandatory for New Features

All error paths in new code **MUST** use `AppLogger.error(tag, message, throwable)` — never silent catch, never `println`. On Android this feeds Crashlytics non-fatal exceptions (`recordException`); on wasmJs it goes to `console.error`. Key rules:

- **Catch blocks:** always `logger.error(TAG, "context: ${e.message}", e)` — the `throwable` param triggers `recordException` on Android
- **Failed network/sync results:** log with the original exception from `AppResult.Error`
- **Silent fallbacks:** if returning a default value on error, log `warning` with the reason
- Tag convention: class name or feature area (e.g. `"Sync"`, `"UserApi"`, `"Analyze"`)

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

### AI Chat Assistant (`feature/aichat/`)
Natural-language assistant with tiered routing: Layer 1 (local parser, 0 credits) → Layer 2 (cloud classifier `classify_chat_intent`, 1 credit) → Layer 3 (full chat `chat_completion`, 3 credits). The Layer 3 system prompt lives in `firebase-functions/main.py` (`CHAT_COMPLETION_PROMPT_TEMPLATE` + `FEATURE_CATALOG_RU/EN`).

**Hard rule:** any user-facing feature shipped in the app MUST be added to `FEATURE_CATALOG_RU` and `FEATURE_CATALOG_EN` in the same release, before redeploying `chat_completion`. Without an entry, the model will reply "I can't help with that" to how-to questions about the feature — a UX bug, not a limitation.

Full enforcement guide (catalog rules, deploy steps, format-test, anti-patterns): **`docs/guidelines/ai-chat-feature-coverage.md`**.

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

### Google Play Store Listing

App title, short description, and full description for Google Play Console: `docs/store-screenshots/store-listing-en.md`. Keep in sync with actual product features and pricing when shipping new versions.

## Cloud Functions Diagnostics

All AI inference goes through Cloud Functions in `aichecklists-40230` (`analyze_and_fill_checklist`, `generate_checklist`, `chat_completion`, `classify_chat_intent`, `transcribe_audio`). When users report "AI не смог ответить" / "AI processing failed", follow this runbook instead of patching the client.

### Step 1 — Read logs first

```bash
gcloud functions logs read <fn> --region=us-central1 --project=aichecklists-40230 --gen2 --limit=30
```

Where `<fn>` is one of the 5 above. The function that the user hit is identifiable from the error context: short commands → `classify_chat_intent`; free-form chat → `chat_completion`; "Create via AI" → `generate_checklist`; "Fill via AI" → `analyze_and_fill_checklist`; voice mic → `transcribe_audio`.

### Step 2 — If logs are silent, force them

`main.py` swallows exceptions with bare `except Exception:` blocks (lines ~708, ~843). Real Gemini errors never reach logs. To unmask:

```python
except Exception as e:
    import traceback
    print(f"GEMINI_ERROR: {type(e).__name__}: {e}", flush=True)
    print(traceback.format_exc(), flush=True)
    return create_error_response("AI processing failed. Please try again.", 500)
```

Redeploy ONE function with this patch, retest, read logs, then **revert the patch before commit**. The traceback prints the actual gRPC status code that points at the real cause.

### Step 3 — End-to-end smoke test (PowerShell)

Register a throwaway user, then call a Gemini-using endpoint with the returned `user_id`:

```powershell
# 1. Register throwaway user (no Gemini call — tests CF infra)
$r1 = Invoke-RestMethod -Uri "https://us-central1-aichecklists-40230.cloudfunctions.net/register_user" `
    -Method Post -ContentType "application/json" `
    -Body '{"device_id":"smoke-test","app_version":"1.15.0","platform":"test"}'
$userId = $r1.user_id  # save for next call

# 2. Call Gemini-using endpoint
$body = @{ user_id = $userId; is_premium = $false; text = "добавь молоко"; locale = "ru" } | ConvertTo-Json
Invoke-RestMethod -Uri "https://us-central1-aichecklists-40230.cloudfunctions.net/classify_chat_intent" `
    -Method Post -ContentType "application/json" -Body $body -TimeoutSec 60
```

`success: true` means Gemini key + Secret Manager + CF wiring all work end-to-end. The `test-firebase-function` skill automates this curl-based pattern.

### Step 4 — Diagnose by symptom

| Symptom in logs / response | Root cause | Fix |
|---|---|---|
| `Illegal header value` (gRPC) | Secret Manager value has trailing CRLF or whitespace | Re-add secret with `printf '%s' '<key>' \| gcloud secrets versions add gemini-api-key --data-file=- --project=aichecklists-40230` (use `printf`, NOT PowerShell `echo` which adds CRLF) |
| `Consumer 'api_key:...' has been suspended` | Key was created in a suspended GCP project (e.g. `gen-lang-client-*` auto-provisioned by AI Studio) | Create key in `aichecklists-40230` directly: `gcloud services api-keys create --display-name=... --api-target=service=generativelanguage.googleapis.com --project=aichecklists-40230`. Never create production keys via AI Studio UI. |
| `API_KEY_INVALID` | Key revoked or wrong format | Verify key exists in `aichecklists-40230` (`gcloud services api-keys list`) and Generative Language API is enabled (`gcloud services list --enabled --project=aichecklists-40230 \| grep generativelanguage`) |
| `QUOTA_EXCEEDED` | User out of credits or hit per-key quota | For user: refill in Firestore `users/{userId}` → `ai_credits`. For project: check quota in Cloud Console |
| HTTP 402 "insufficient credits" | Pre-Gemini credit gate failed (user has 0 credits) | This is correct behavior, not a bug. User needs credits |
| HTTP timeout (60s) | Container stuck retrying Gemini with bad key | Same as `Illegal header value` — fix secret + redeploy |

### Step 5 — Force CF cold restart after secret change

`--set-secrets="...:latest"` reads the new value only on container startup. Existing warm containers keep the old value until they scale down (~15 min idle). To force immediate re-read, redeploy:

```bash
gcloud functions deploy <fn> --region=us-central1 --gen2 --project=aichecklists-40230 \
    --source=firebase-functions \
    --set-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
    --runtime=python312 --trigger-http --allow-unauthenticated
```

For all 5 Gemini-using CFs at once, wrap in a `for` loop in bash (each deploy ~60-90s, ~5 min total serial). See `firebase-functions/deploy.sh` for the canonical script.

### Architectural invariants (preserve these)

- **Gemini API key lives in Secret Manager `gemini-api-key:latest` only.** Never in env vars (`--set-env-vars`), never in `local.properties`, never in `BuildConfig`, never in client code.
- **The key is owned by `aichecklists-40230`** (same project as CFs, Firestore, Crashlytics). No shadow `gen-lang-client-*` projects.
- **Client code holds zero Gemini credentials.** Android/iOS/wasmJs all call Cloud Functions via Ktor; no `GenerativeModel(apiKey = ...)` anywhere in the codebase.
- **Old broken secret versions are `disabled`** (not destroyed) for audit trail. Only `:latest` is enabled at any time.

## Client Diagnostics (Android / iOS / wasmJs)

When the user reports "AI не отвечает" but Cloud Functions are confirmed healthy (smoke tests above pass), the bug is in the **client HTTP layer**, not the backend. Symptoms that point here: request never leaves the device (no entry in CF logs), response shape mismatch on a working CF (`KotlinxSerializationException` in logcat), short-circuit `catch` swallowing a network exception, wrong Content-Type, missing field.

### How to tell CF vs Client side

| Sanity check | If yes → | If no → |
|---|---|---|
| Run the PowerShell smoke test in "Cloud Functions Diagnostics" — does the same endpoint succeed? | Backend works. **Client side.** Reproduce with logcat-level Ktor logging | **Server side.** Follow Step 4 table above |
| Does the request show up in `gcloud functions logs read` for the function the app called? | Reached server. Look at response parsing on client | Never left device. Look at HttpClient config, baseUrl, headers, body serialization |
| Does logcat show `HttpRequestTimeoutException` / `HttpRequestException`? | Network / DNS / firewall on device. Test wifi vs mobile | Different bug — read full stacktrace |

### Current test inventory (as of 2026-05-24)

| Component | Test coverage |
|---|---|
| `LocalIntentRouterImpl` (Layer 1 parser) | ✅ `LocalIntentRouterImplTest` (130+ cases, all 7 intents + collisions) |
| `ChatViewModel` state machine | ✅ `ChatViewModelTest` (18 tests; 1 pre-existing failure: `onFeedbackSubmit_blankText_emitsHintSnackbar`) |
| `AiChatRepositoryImpl` (Layer 1→2→3 routing) | ✅ `AiChatRepositoryImplTest` |
| `ChecklistHintExtractor` | ✅ |
| **`FirebaseAiServiceImpl` (HTTP layer)** | ❌ **NONE — gap.** No tests verify request body shape, response parsing, error mapping, or timeout behavior. This is the layer that broke today's incident debug; without tests the next CF-protocol drift will go unnoticed |
| `AnalyzeRepositoryImpl` | ❌ NONE |

### Scaffold pattern for `FirebaseAiServiceImpl` tests

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

### Integration test option (slower, optional)

For full E2E confidence, write an `androidTest` (instrumented) that calls a real deployed CF against a throwaway user. Slow (~5s per test, hits prod billing for ~1 credit per call), but proves end-to-end the way the PowerShell smoke does — just from the Android device's network stack. Place under `feature/analyze/src/androidTest/...`. Skip in PR-blocking CI; run nightly or on-demand.

## Web (wasmJs) Reference Project

For any wasmJs-related questions — Firebase JS SDK interop, `globalThis` bridges, `Promise<JsAny>` handling, `@JsFun` field extraction, COOP/COEP headers, OPFS WebWorker setup — **use `C:\Users\Admin\StudioProjects\swapfaceandroid` as the reference project**. It has a production-grade implementation of Google/Apple/Email Firebase Auth with `signInWithPopup` on wasmJs, JS→Kotlin Promise bridging (raw JS objects + `@JsFun` extractors, NOT `JSON.stringify`), and `onAuthStateChanged` state sync. When facing wasmJs interop issues, **read the swapfaceandroid implementation first**.

## Dependencies

Versions managed in `gradle/libs.versions.toml`. Key: Kotlin 2.3.0, Compose Multiplatform 1.9.3, Koin 4.1.1, Room 2.8.4, RevenueCat 2.2.17, Firebase BOM 33.7.0.

**Note:** Gemini SDK is intentionally NOT a client dependency. All AI inference is server-side via Cloud Functions; the client (Android/iOS/wasmJs) holds no Gemini credentials. See "Cloud Functions Diagnostics" below.

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
