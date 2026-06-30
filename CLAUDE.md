# CLAUDE.md

Guidance for Claude Code in this repo. Keep this file **≤200 lines** (Anthropic memory limit — longer files reduce adherence). Detailed, file-scoped rules live in `.claude/rules/*.md` (auto-loaded only when you edit matching files — see the map at the bottom); deep reference lives in `docs/`.

## Project Overview

**Gisti — AI Checklists** is a Kotlin Multiplatform app (Jetpack Compose Multiplatform). Turns anything into a checklist with AI; same features and data on Android and Web.

| Target | Status | Where |
|--------|--------|-------|
| Android | Production | Google Play |
| Web (wasmJs) | Production | <https://gisti-ai.com/> (Cloudflare Workers Static Assets; legacy checklists.gisti.workers.dev 301-redirects) |
| iOS | Code-only, not released | published after Android revenue covers the $99/yr Apple fee |

**Web** is a full parallel platform (not a lite companion): Compose wasmJs renderer (Skiko canvas), Room 3.0 over SQLite OPFS Web Worker, Firebase JS SDK (ESM). AI flow goes through CORS-enabled Cloud Functions — direct Gemini calls from the browser are forbidden.

**Product:** the AI Chat Assistant is the flagship interaction layer; secondary AI flows (Create/Fill) turn content into checklists. Full feature catalog, tiers, platform-parity matrix: `docs/product-features.md`.

**Business model:** Free (5 checklists, 5 fills each, 10 AI credits/day, 10 recurring reminders) · Premium $1.99/mo (unlimited + 300 credits/day + Calendar + Weekly) · 3-day trial.

## Repository Visibility & Security

Repo is **PUBLIC** since 2026-06-16 (history was rewritten with `git filter-repo` to purge every secret/IP before going public — audit: `docs/completed/repo-public-preparation-2026-06-16.md`; playbook: `docs/security-playbook.md`). **Public = every push is instantly world-visible and indexed; there is no undo.** Treat each commit as a permanent publication.

- **NEVER commit:** API keys/tokens/passwords; `google-services.json` / `GoogleService-Info.plist`; service-account JSON; `.env` with real values; security docs referencing real credentials. Before committing a new file, verify no `AIzaSy*` / hardcoded tokens (`.gitleaks.toml` + pre-commit configured).
- **Server AI prompts are IP — keep them OUT of git.** The 8 prompts live in gitignored `firebase-functions/prompts_private.py` (redacted `prompts_private_example.py` is tracked); `main.py` imports via `try/except ImportError` fallback. ⚠️ `prompts_private.py` MUST exist locally at `firebase deploy` — otherwise stub prompts deploy and the AI breaks.
- **Already gitignored (NOT in public):** `.claude/`, `docs/`, `commonMain/` (stubs), `SECURITY.md`, `hosting/.firebase/`, `prompts_private.py`, `.firebaserc`, `extensions/*.env`, `graphify-out/`, `claude_design/`, `google_play_translate/` — each ships a tracked `*.example` where a template helps.
- **Safe to commit** (semi-public by design): Firebase project id `aichecklists-40230`, `firebase.json`, Cloud Functions base URL — defended by App Check + key restrictions (package + SHA-1), not by secrecy. Gemini key only in Google Cloud Secret Manager (`gemini-api-key:latest`), never in client/env/BuildConfig.

## Project Language

All code comments, docs, commit messages in **English**. Marketing/store/onboarding copy: **English only** — RU localization only on explicit request or when fixing existing RU strings. Commit style: Conventional Commits (skill `git-commit-conventions`).

**No hardcoded user-facing strings (recurring bug, 2026-06-07):** every user-visible string MUST come from `core/designsystem` `strings.xml` — `stringResource(Res.string.x)` in a `@Composable`, `getString(Res.string.x)` (suspend) in a ViewModel/coroutine. **Never** a string literal in Kotlin: a literal hardcodes one language (this bug shipped the Russian error "Введите название чек-листа" on the English UI). Applies equally to **default names** ("New Checklist", "AI Fill") and error/snackbar text. `getString` is `suspend` — wrap a non-coroutine call site in `viewModelScope.launch { }`. The **domain layer** (UseCase) must NOT touch Compose Resources — pass the resolved string in as a parameter from presentation. NOT user-facing, leave as literals: parser lexicons (`RuIntentLexicon`, `RuDateLexicon`), regex, log tags, analytics event keys. Details: rule `compose-resources-kmp`.

**Strings escaping (recurring bug):** in `composeResources/**/strings.xml` write apostrophes & quotes **literally** (`can't`, `"quoted"`) — **never** Android-style `\'`. Compose Resources is parsed by `org.jetbrains.compose.resources`, not AAPT: `\'` renders the backslash on screen as `can\'t`. Only `\n` / `\t` / `\uXXXX` are real escapes; XML metachars use `&amp;` / `&lt;` / `&gt;`. Match existing strings (`don't`, `What's`, `You've`). Details: rule `compose-resources-kmp`.

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

**Web (wasmJs): verify on :9090 BEFORE pushing — never debug via prod CI (recurring time-sink, 2026-06-24).** For any wasmJs feature/bugfix, run `/web-dev-run` (:9090) and exercise the **real** path (open screen, pick file, render, click) before `git push`. `compile*` + `commonTest` do **NOT** cover Compose-runtime / Coil / JS-interop — they stay green while the live path is broken. Each prod CI deploy is **~12–15 min**; never push to add a log or test a fix — diagnose locally. Precedent: attachment add shipped compiling + tests-green but the FilePicker callback AND the Coil `opfs://` fetcher were both broken, surfaced only on the first real run → ~5 wasted prod deploys. Coil/FilePicker traps: project memory `coil3-custom-scheme-fetcher-uri-not-string`, `filepicker-rememberupdatedstate-closure-trap`.

### `adb uninstall` — acceptable now that data is Google-synced

Google-account sync (Firestore) makes a reinstall recoverable: for a **signed-in** user, checklists/fills/reminders restore from the cloud by `google_uid`. So `adb uninstall` for a clean slate is fine on a dev device you're signed into. This supersedes the old absolute ban (after the 2026-05-24 Room-wipe incident, before sync covered the data).

- ⚠️ Sync restores by **Google identity only**. An anonymous (not-signed-in) install keys data to a device-registration id that changes on reinstall — its local-only data does NOT come back. On a device holding real *anonymous* data, confirm sign-in first (or accept the loss).
- `adb install -r <apk>` still preserves data with no caveats — the default for `/install-device`.

### Device interaction — never drive the UI yourself unless explicitly asked

This **overrides** the global "do it yourself via tools" autonomy rule for the physical device. Build, install (`adb install -r`), read logs (`adb logcat`), `screencap`, and `dumpsys` are fine without asking. But do **NOT** drive the app's UI yourself — no `adb shell input tap`/`swipe`/`text`/`keyevent`, no auto-clicking buttons or navigating screens — unless the user **explicitly** asks. It is the user's device; they may be using it. To verify a change, hand the test to the user (they reproduce; you may capture `adb logcat` while they click) or ask first.

## Architecture

```
composeApp/  androidMain (widget, notifications, AlarmManager, review) · iosMain · wasmJsMain (Firebase JS, OPFS driver, pickers; init.js.template)
core/        common(api|impl) · designsystem · datastore(api|impl) · navigation(api|impl) · remoteconfig(api|impl)
feature/     checklist · create · home · onboarding · splash · analyze · paywall · sharing · user · debug
```

- **API/impl split:** core modules expose interfaces in `api`, impls in `impl`. Reference modules as `projects.core.common.api`.
- **MVI:** ViewModels extend `AppViewModel<State, Intent, SideEffect>` (`core:common:api`). Files: `*ScreenContract.kt` (State/Intent sealed), `*ViewModel.kt` (`onIntent()`), `*Screen.kt` (observes `screenState`, calls `sendIntent()`).
- **DI:** Koin; each module has its own module, aggregated in `appModule`; ViewModels via `koinViewModel()`.
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

All dependency versions live in `gradle/libs.versions.toml` — the single source of truth; check it, don't trust a number duplicated in prose. **Gemini SDK is intentionally NOT a client dependency** — all AI inference is server-side. Unit economics: `docs/unit-economics.md` (gemini-2.5-flash-lite ~$0.0002/req, positive at max usage). Geo-tiered pricing & organic-growth strategy (India + low-ARPU markets priced at minimal markup to drive organic installs/ratings; **gitignored, business-sensitive**): `docs/pricing-strategy.md`.

| Limit (Remote Config) | Free | Premium |
|---|---|---|
| AI requests/day | 10 | 300 |
| Max checklists | 5 | unlimited |
| Max fills/checklist | 5 | unlimited |
| Recurring reminders | 10 | unlimited |

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
