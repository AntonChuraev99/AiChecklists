# CLAUDE.md

Guidance for Claude Code in this repo. Keep this file **≤200 lines** (Anthropic memory limit — longer files reduce adherence). Detailed, file-scoped rules live in `.claude/rules/*.md` (auto-loaded only when you edit matching files — see the map at the bottom); deep reference lives in `docs/`.

## Project Overview

**Gisti — AI Checklists** is a Kotlin Multiplatform app (Jetpack Compose Multiplatform). Turns anything into a checklist with AI; same features and data on Android and Web.

| Target | Status | Where |
|--------|--------|-------|
| Android | Production | Google Play |
| Web (wasmJs) | Production | <https://checklists.gisti.workers.dev/> (Cloudflare Workers Static Assets) |
| iOS | Code-only, not released | published after Android revenue covers the $99/yr Apple fee |

**Web** is a full parallel platform (not a lite companion): Compose wasmJs renderer (Skiko canvas), Room 3.0 over SQLite OPFS Web Worker, Firebase JS SDK (ESM). AI flow goes through CORS-enabled Cloud Functions — direct Gemini calls from the browser are forbidden.

**Product:** the AI Chat Assistant is the flagship interaction layer; secondary AI flows (Create/Fill) turn content into checklists. Full feature catalog, tiers, platform-parity matrix: `docs/product-features.md`.

**Business model:** Free (4 checklists, 5 fills each, 10 AI credits/day, 1 recurring reminder) · Premium $1.99/mo (unlimited + 300 credits/day + Calendar + Weekly) · 3-day trial.

## Repository Visibility & Security

Repo is **private** (was public until a 2026-05-24 Gemini key leak). Old leaked secrets stay in git history forever — treat **everything ever committed** as potentially public. Full playbook: `docs/security-playbook.md`.

- **NEVER commit:** API keys/tokens/passwords; `google-services.json` / `GoogleService-Info.plist`; service-account JSON; `.env` with real values; security docs referencing real credentials. Before committing a new file, verify no `AIzaSy*` / hardcoded tokens (`.gitleaks.toml` is configured).
- **Already gitignored:** `.claude/`, `docs/`, `commonMain/` (stubs), `SECURITY.md`, `hosting/.firebase/`.
- **Safe to commit** (public by design): Firebase project id `aichecklists-40230`, `.firebaserc`, `firebase.json` (no keys).
- Firebase API keys restricted by package + SHA-1; Gemini key only in Google Cloud Secret Manager (`gemini-api-key:latest`), never in client/env/BuildConfig.

## Project Language

All code comments, docs, commit messages in **English**. Marketing/store/onboarding copy: **English only** — RU localization only on explicit request or when fixing existing RU strings. Commit style: Conventional Commits (skill `git-commit-conventions`).

## Build Commands

**AGP 9 module split (since 2026-05-10):** `composeApp` is a **KMP library**; the Android **application** is `:androidApp`. Use `:androidApp` for any `assemble*`/`bundle*`/`install*`/`connectedAndroidTest`; KMP/iOS/wasmJs tasks stay on `:composeApp`. Details: `docs/solutions/build-system/agp-9-migration-2026-05-10.md`.

```bash
./gradlew build                                       # All targets
./gradlew androidApp:assembleDebug                    # Android debug APK
./gradlew androidApp:bundleRelease                    # Android release AAB
./gradlew androidApp:connectedAndroidTest             # Instrumented tests
./gradlew composeApp:wasmJsBrowserDevelopmentRun --continuous  # Web dev server :9090
./gradlew composeApp:wasmJsBrowserDistribution        # Web prod bundle
```

iOS: open `iosApp/iosApp.xcodeproj` in Xcode. Emulators: `Pixel_9`, `Medium_Phone_API_36.1`. Install on device/emulator via skills `/install-device`, `/install-emulator` (reinstall, keep data).

**Deploy Web:** `./gradlew composeApp:wasmJsBrowserDistribution` then `npx wrangler@4 deploy`. CI: push to `master` → prod, other branches → preview. Needs `local.properties` `FIREBASE_WEB_API_KEY` + `FIREBASE_WEB_APP_ID`; CFs must deploy with CORS handlers. Config in `wrangler.jsonc`.

### ⛔ ABSOLUTE RULE: NEVER `adb uninstall` before installing — permanent data loss

On 2026-05-24 an `adb uninstall` "for a clean state" **wiped a user's real Room database** (all checklists/fills/reminders gone). Auto-Backup does NOT cover the Room DB (`backup_rules.xml` includes only `files/user/`). Any uninstall = permanent loss.

- ❌ NEVER `adb uninstall` / `pm uninstall` / `pm clear` / factory-reset a device holding real data.
- ✅ Re-install with `adb install -r <apk>` (what `installDebug` and `/install-device` do).
- ✅ Uninstall only when ADB demands it (`INSTALL_FAILED_VERSION_DOWNGRADE` / `UPDATE_INCOMPATIBLE`) — and **first ask via `AskUserQuestion`**, naming the side effect. Need a clean slate? Use an emulator, never the user's device.

> P0 backlog: widen `backup_rules.xml` to include `databases/checklist_database` — until then this rule is the only safeguard.

## Architecture

```
composeApp/  androidMain (widget, notifications, AlarmManager, review) · iosMain · wasmJsMain (Firebase JS, OPFS driver, pickers; init.js.template)
core/        common(api|impl) · designsystem · datastore(api|impl) · navigation(api|impl) · remoteconfig(api|impl)
feature/     checklist · create · home · onboarding · splash · analyze · paywall · sharing · user · debug
```

- **API/impl split:** core modules expose interfaces in `api`, impls in `impl`. Reference modules as `projects.core.common.api`.
- **MVI:** ViewModels extend `AppViewModel<State, Intent, SideEffect>` (`core:common:api`). Files: `*ScreenContract.kt` (State/Intent sealed), `*ViewModel.kt` (`onIntent()`), `*Screen.kt` (observes `screenState`, calls `sendIntent()`).
- **DI:** Koin 4.1; each module has its own module, aggregated in `appModule`; ViewModels via `koinViewModel()`.
- **expect/actual:** logging, database, file pickers, audio, build config (`AppBuildConfig`), reminders, in-app review.
- **StateFlow** for all reactive state. Navigation 3 (no `NavController`) — see rule `adaptive-navigation`.

### Error Logging — Mandatory for all new code

Every error path **MUST** use `AppLogger.error(tag, message, throwable)` — never silent catch, never `println`. The `throwable` param triggers Crashlytics `recordException` (Android) / `console.error` (wasmJs).
- Catch blocks: `logger.error(TAG, "context: ${e.message}", e)`.
- Silent fallback (default value on error): log `warning` with the reason. Silent-skip on a UX path is a bug — give feedback (snackbar/toast), don't `return` quietly.
- Tag = class or feature area (`"Sync"`, `"UserApi"`, `"Analyze"`).

## Features

One-liner map; deep rules load when you edit the feature. Full catalog: `docs/product-features.md`.

- **AI Chat** (`feature/aichat/`) — 3-tier routing, flagship. Hard rules (FEATURE_CATALOG, TDD bad-answer fixes) → rule `ai-chat`; skill `/ai-chat-feedback-fixer`.
- **Analyze** (`feature/analyze/`) — Gemini via Cloud Functions (Photo/PDF/Text/Link/Voice). `GeminiAiAnalyzer`, `AnalyzeViewModel`.
- **Templates** (`feature/create/`) — from Remote Config key `templates_json`; Templates → TemplatePreview → Use.
- **Paywall** (`feature/paywall/`) — RevenueCat. Credit-restore flow → rule `credit-restore`.
- **Sharing** (`feature/sharing/`) — `ShareFormat.Text`/`.Pdf`; `ShareLauncher`/`PdfGenerator` expect/actual.
- **Updates Feed** (`feature/updatefeed/`) — in-code release feed. Hard rules → rule `updates-feed`; skill `/create-release`.
- **Reminders / Checklist domain** — template-vs-fill, repeat rules, KMP constraints → rule `checklist-domain`.
- **Widget** (`composeApp/androidMain/widget/`) — Glance, binds a checklist, WorkManager sync, deep-links to detail.
- **CSAT** (`composeApp/commonMain/csat/`) — `CsatManager` survey + Play in-app review (`InAppReviewLauncher`).
- **Debug** (`feature/debug/`) — debug builds; unlock Volume Up→Down→Up.

## Diagnostics

"AI не ответил" / "AI processing failed" — diagnose, don't blind-patch the client. Server runbook: `docs/cloud-functions-diagnostics.md` (gcloud logs → smoke test → symptom table). Client HTTP layer: `docs/client-diagnostics.md`. Skill `/test-firebase-function` automates the smoke test.

## Copy Guidelines

Simple, clear, benefit-focused, action-oriented. Do: "Create Checklist", "Fill via AI", "Save". Don't: "Add New", "Submit", "Continue". Store listing: `docs/store-screenshots/store-listing-en.md`.

## Dependencies & Economics

Versions in `gradle/libs.versions.toml`: Kotlin 2.3.0, Compose MP 1.9.3, Koin 4.1.1, Room 2.8.4, RevenueCat 2.2.17, Firebase BOM 33.7.0. **Gemini SDK is intentionally NOT a client dependency** — all AI inference is server-side. Unit economics: `docs/unit-economics.md` (gemini-2.5-flash-lite ~$0.0002/req, positive at max usage).

| Limit (Remote Config) | Free | Premium |
|---|---|---|
| AI requests/day | 10 | 300 |
| Max checklists | 4 | unlimited |
| Max fills/checklist | 5 | unlimited |
| Recurring reminders | 1 | unlimited |

## `.claude/rules/` map (file-scoped, auto-loaded on matching edits)

| Rule | Loads when you edit |
|---|---|
| `designsystem` | `core/designsystem/**`, `*Screen.kt` — colors, spacing, components, edge-to-edge insets |
| `compose-resources-kmp` | `composeResources/**`, `build.gradle.kts`, `strings.xml` — androidResources opt-in, localization |
| `ui-card-patterns` | `*Card*.kt`, `*ItemDetailsSheet*.kt`, `*Pager*.kt`, `feature/home/**` — hit-zone, pager, double-padding |
| `adaptive-navigation` | `navigation/**`, `App.kt`, `*Navigator*.kt` — WindowSizeClass, Nav 3, adding destinations |
| `credit-restore` | `*Purchase*.kt`, `*Credits*.kt`, `*Restore*.kt`, `*Paywall*.kt` — restore flow, Firestore |
| `ai-chat` | `feature/aichat/**`, `firebase-functions/**` — tier routing, FEATURE_CATALOG, TDD rule |
| `checklist-domain` | `feature/checklist/**`, `*Checklist*.kt`, `*Fill*.kt`, `*Reminder*.kt` — template vs fill, reminders, KMP |
| `updates-feed` | `feature/updatefeed/**` — post rules, CTA whitelist |

For wasmJs interop, use `C:\Users\Admin\StudioProjects\swapfaceandroid` as the reference project.
