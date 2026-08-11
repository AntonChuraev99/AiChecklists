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

**MCP server** (`mcp-server/`): a remote Cloudflare Worker exposing each signed-in user's checklists (read + AI-generate + CRUD) to any MCP client over Google OAuth — live at `https://gisti-mcp.gisti.workers.dev/mcp`. Full reference: `mcp-server/README.md`.

**Product:** the AI Chat Assistant is the flagship interaction layer; secondary AI flows (Create/Fill) turn content into checklists. UI languages: EN · RU · HI.
**→ Start here for anything product-shaped: `docs/PRODUCT.md`** — positioning, competitors, business model, live A/B, decisions-not-to-reopen, current metrics, open forks. Feature-level detail (per-feature UX, parity matrix, version history): `docs/product-features.md`. Both gitignored.

**Business model:** Free (5 checklists, 5 fills each, 10 recurring reminders, 1 weekly, 3 attachments/item) · Premium $1.99/mo (unlimited + Calendar + Weekly) · 3-day trial · entitlement `"AiChecklists Pro"`, premium = `revenueCat || firestore`.
⚠️ **The binding Free limit is the credit wallet, not the daily cap:** 100 lifetime credits at 20/action = **5 AI generations ever**, no Free refill, 0 on web until Google sign-in (Firestore `remote_config/current`, not Firebase RC). `ai_daily_limit_free=10` is effectively unreachable.

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

**Pre-deploy: ALWAYS verify project ↔ account link FIRST (mandatory, 2026-07-01).** Before ANY `wrangler deploy`/`versions upload`, run `npx wrangler@4 whoami` and confirm the login owns the target zone/worker. The `gisti-ai.com` zone + `checklists` worker live in the **`churaevanton@gmail.com`** CF account (id `2c9dfaadbca94b44f59f13dbf70519d7`, workers.dev subdomain `gisti`) — the local wrangler is often logged in as **`a.churaev@swapify.dev`** (for swapfaceandroid), whose 2 accounts have **neither** the zone nor the worker. Wrong login → the script uploads into the wrong account and the route fails (`Could not find zone` / `register a workers.dev subdomain`). Confirm the config's `account_id` matches the intended account before deploying; if `whoami` shows swapify, STOP and have the user re-auth (`wrangler login` gmail) or supply a `CLOUDFLARE_API_TOKEN` scoped to the gmail account. Precedent: a probe deploy landed a throwaway `gisti-app-stage` worker in both swapify accounts before erroring on the zone (deleted after). Details: project memory `cloudflare-deploy-accounts-and-workers-dev-default`.

**Web (wasmJs): verify on :9090 BEFORE pushing — never debug via prod CI (recurring time-sink, 2026-06-24).** For any wasmJs feature/bugfix, run `/web-dev-run` (:9090) and exercise the **real** path (open screen, pick file, render, click) before `git push`. `compile*` + `commonTest` do **NOT** cover Compose-runtime / Coil / JS-interop — they stay green while the live path is broken. Each prod CI deploy is **~12–15 min**; never push to add a log or test a fix — diagnose locally. Precedent: attachment add shipped compiling + tests-green but the FilePicker callback AND the Coil `opfs://` fetcher were both broken, surfaced only on the first real run → ~5 wasted prod deploys. Coil/FilePicker traps: project memory `coil3-custom-scheme-fetcher-uri-not-string`, `filepicker-rememberupdatedstate-closure-trap`.

### `adb uninstall` — acceptable now that data is Google-synced

Google-account sync (Firestore) makes a reinstall recoverable: for a **signed-in** user, checklists/fills/reminders restore from the cloud by `google_uid`. So `adb uninstall` for a clean slate is fine on a dev device you're signed into. This supersedes the old absolute ban (after the 2026-05-24 Room-wipe incident, before sync covered the data).

- ⚠️ Sync restores by **Google identity only**. An anonymous (not-signed-in) install keys data to a device-registration id that changes on reinstall — its local-only data does NOT come back. On a device holding real *anonymous* data, confirm sign-in first (or accept the loss).
- `adb install -r <apk>` still preserves data with no caveats — the default for `/install-device`.

### Device interaction — never drive the UI yourself unless explicitly asked

This **overrides** the global "do it yourself via tools" autonomy rule for the physical device. Build, install (`adb install -r`), read logs (`adb logcat`), `screencap`, and `dumpsys` are fine without asking. But do **NOT** drive the app's UI yourself — no `adb shell input tap`/`swipe`/`text`/`keyevent`, no auto-clicking buttons or navigating screens — unless the user **explicitly** asks. It is the user's device; they may be using it. To verify a change, hand the test to the user (they reproduce; you may capture `adb logcat` while they click) or ask first.

## Architecture

```
composeApp/  commonMain (activation, csat, appupdate, deeplink, mcp, aichat/ToolCallDispatcher, sync)
             androidMain (widget, retention/push, notifications, AlarmManager, calendar, attribution, ProcessText, review)
             iosMain (built, not released) · wasmJsMain (Firebase JS, OPFS driver, pickers; init.js.template)
core/        common(api|impl) · designsystem · datastore(api|impl) · navigation(api|impl) · remoteconfig(api|impl) · auth(api|impl) · filepicker(api)
feature/     aichat(api|impl) · analyze · checklist · create · debug · home · onboarding · paywall · settings · sharing · splash · updatefeed · user
```
Outside Gradle: `firebase-functions/` (Python CFs) · `mcp-server/` (TS Cloudflare Worker) · `landing/` + `landing-src/` (SEO site) · `data/checklists/` (81 gallery JSONs) · `hosting/` · `e2e/` + `playwright/`.

- **API/impl split:** core modules expose interfaces in `api`, impls in `impl`. Reference modules as `projects.core.common.api`.
- **MVI:** ViewModels extend `AppViewModel<State, Intent, SideEffect>` (`core:common:api`). Files: `*ScreenContract.kt` (State/Intent sealed), `*ViewModel.kt` (`onIntent()`), `*Screen.kt` (observes `screenState`, calls `sendIntent()`).
- **DI:** Koin; each module has its own module, aggregated in `appModule`; ViewModels via `koinViewModel()`.
- **expect/actual:** logging, database, file pickers, audio, build config (`AppBuildConfig`), reminders, in-app review.
- **StateFlow** for all reactive state. Navigation 3 (no `NavController`) — see rule `adaptive-navigation`.

**Reuse the v1 surface, don't re-invent it in v2 (owner rule, 2026-08-07).** The classic layout is years of shipped, debugged UX; the v2 shell is a new arrangement of the SAME product. When a v2 screen needs a sheet, a row, an input or a flow that v1 already has, **lift the v1 component and extend it** — never write a thinner v2 twin. A reduced re-implementation reads to the user as lost features, and it is: it re-opens the defects v1 already closed and doubles every future fix. If the v1 component genuinely does not fit, say which part and why *before* writing a replacement. Precedent: the v2 Inbox shipped a 3-action triage sheet (move / complete / delete) beside v1's full `ItemDetailsSheet` (reminder, repeat, note, attachments, priority) — the owner's verdict was «пользователю просто не даёшь выбрать и настроить». The fix is v1's sheet plus a "move to project" row, not a second sheet.

### Error Logging — Mandatory for all new code

Every error path **MUST** use `AppLogger.error(tag, message, throwable)` — never silent catch, never `println`. The `throwable` param triggers Crashlytics `recordException` (Android) / `console.error` (wasmJs).
- Catch blocks: `logger.error(TAG, "context: ${e.message}", e)`.
- Silent fallback (default value on error): log `warning` with the reason. Silent-skip on a UX path is a bug — give feedback (snackbar/toast), don't `return` quietly.
- Tag = class or feature area (`"Sync"`, `"UserApi"`, `"Analyze"`).

## Features

One-liner map; deep rules load when you edit the feature. Full catalog: `docs/product-features.md`.

- **AI Chat** (`feature/aichat/`) — flagship. **TWO active layers, not three:** L2 classifier CF `classify_chat_intent` (1 credit) → L3 agent CF `chat_agent` (3 flat/turn, 10 tools, max 5 rounds). **L1 `LocalIntentRouterImpl` is DISCONNECTED** (no Koin binding, `AiChatFeatureModule.kt:43`, decision 2026-07-15) — code + 168 tests parked; ⛔ never widen its lexicon. Legacy `chat_completion` still deployed, gets 0 calls. Hard rules → rule `ai-chat`; skill `/ai-chat-feedback-fixer`.
- **Analyze** (`feature/analyze/`) — Gemini via Cloud Functions (Photo/PDF/Text/Link/Voice). `GeminiAiAnalyzer`, `AnalyzeViewModel`.
- **Templates** (`feature/create/`) — **81 templates / 15 categories** in a bundled Compose Resource read by `TemplatesRepositoryImpl` (the `templates_json` RC key is dead/unread); mirrored as `data/checklists/*.json` for the SEO gallery. Templates → TemplatePreview → Use.
- **Paywall** (`feature/paywall/`) — RevenueCat. Credit-restore flow → rule `credit-restore`.
- **Sharing** (`feature/sharing/`) — `ShareFormat.Text`/`.Pdf`; `ShareLauncher`/`PdfGenerator` expect/actual.
- **Updates Feed** (`feature/updatefeed/`) — in-code release feed. Hard rules → rule `updates-feed`; skill `/create-release`.
- **Reminders / Checklist domain** — template-vs-fill, repeat rules, KMP constraints → rule `checklist-domain`.
- **Widget** (`composeApp/androidMain/widget/`) — Glance, binds a checklist, WorkManager sync, deep-links to detail.
- **CSAT** (`composeApp/commonMain/csat/`) — `CsatManager` survey + Play in-app review (`InAppReviewLauncher`).
- **Debug** (`feature/debug/`) — debug builds; unlock Volume Up→Down→Up.
- **Onboarding** (`feature/onboarding/`) — 4 arms via RC `onboarding`: `default` (slides+paywall) · `interactive` · `none` · `ai_welcome`. ⚠️ RC-fail fallback is `ai_welcome` since 1.18.6, which **has no paywall step** (deliberate, [ADR 07-28]).
- **MCP** (`mcp-server/` + `composeApp/commonMain/mcp/`) — remote Cloudflare Worker, 16 tools, Google OAuth; in-app connection screen. Reference: `mcp-server/README.md`.
- **Retention / push** (`composeApp/androidMain/retention/`, `push/`) — day-1 comeback + habit nudges, `PushTimingResolver`. Note `push_timing_arm` exists in **no** RC template → everyone runs `behavioral`.
- **Gallery deep-link** (`composeApp/commonMain/deeplink/`) — SEO page → create-from-template via App Link `app.gisti-ai.com`. `gisti://` is **internal only**, not in the manifest.

## Diagnostics

"AI не ответил" / "AI processing failed" — diagnose, don't blind-patch the client. Server runbook: `docs/cloud-functions-diagnostics.md` (gcloud logs → smoke test → symptom table). Client HTTP layer: `docs/client-diagnostics.md`. Skill `/test-firebase-function` automates the smoke test.

## Analytics & A/B — keep the source-of-truth docs current

`docs/marketing/ab-tests-overview-2026-06-18.md` is the source of truth for A/B experiments and Remote Config params; its §7 metrics block is a **dated snapshot, not current state** — for that read `docs/PRODUCT.md` §9. **Update it whenever you** change an RC key (both `RemoteConfigKeys` and `RemoteConfigDefaults` live in `RemoteConfigKeys.kt` — there is no separate `RemoteConfigDefaults.kt` — or the Firebase RC template), start/stop a Firebase A/B experiment, or pull a fresh Amplitude snapshot — otherwise it silently drifts from prod.
**Config has FOUR layers** — check all before concluding: Firebase RC client template · server template `firebase-server` (all percentage conditions) · Firestore `remote_config/current` (credits) · web `init.js` (drifts). Mandatory measurement filters: `products_load_failed = 0` (strips review emulators; without it RC-fail reads 40% instead of ~4%) and `rc_activated = True`; compare **raw** shares across runs, never cleaned ones. **No new revenue-objective experiments** while purchases run ~4/month ([ADR 07-15]). Keep the RC-limits table in `docs/unit-economics.md` in sync too. Both docs are gitignored (local-only). Prod truth = Firebase Console (RC + A/B Tests) + Amplitude project `786722`.

**Starting/stopping a Firebase A/B experiment ALSO requires syncing the daily Telegram analytics routine.** The Claude cloud routine "Gisti — Daily Analytics → Telegram" emails a morning report whose A/B block carries a **hardcoded experiment registry inside the routine prompt** — Firebase A/B results have no public API, so it cannot auto-refresh. When you start or stop an experiment, tell Claude **"обнови A/B в рутине"** and it will sync the registry (via the `RemoteTrigger` tool / `/schedule`) so the report stops showing a stale experiment set. Bot token/chat_id live ONLY in the routine prompt (never commit them). Details: project memory `telegram-daily-analytics-routine`.

## Copy Guidelines

Simple, clear, benefit-focused, action-oriented. Do: "Create Checklist", "Fill via AI", "Save". Don't: "Add New", "Submit", "Continue".

**Play Store listing — source of truth: `docs/store-screenshots/store-listing-en.md`** (tracked in git via force-add; the rest of `docs/` stays ignored). Any listing change starts in this doc (title/short/full with char limits 30/80/4000, keyword-coverage table), then is pasted into Play Console → Main store listing — and any console edit must be mirrored back, otherwise the doc silently drifts from prod (this happened before the 2026-07-02 ASO audit).

## SEO / Organic Growth

Strategy + phased plan: **`docs/plans/2026-07-14-seo-organic-growth-strategy.md`** (Tier 1 programmatic-SEO checklist gallery · Tier 2 Pinterest · Tier 3 GEO). Read it before any organic-traffic / landing / gallery / indexing work.

- **Indexable pages are static files under `landing/`, served by worker `gisti-landing` (`wrangler.landing.jsonc`, apex + www). NEVER an app route** — `app.gisti-ai.com` is wasmJs Compose (Skiko `<canvas>`) = empty DOM for crawlers = SEO-zero. Replicate the `landing/mcp/index.html` pattern for new pages.
- **Public gallery = curated/opt-in content ONLY** — never expose Firestore user checklists (private data under `users/{google_uid}`).
- Phase 0 foundation LIVE: `robots.txt` + `sitemap.xml` (**196 URLs, 97 hi + ~96 en**, verified 2026-08-03) + IndexNow, all serving; GSC domain property verified. Pages **are** indexed — the gap is **ranking / domain authority** (0 backlinks, position ~72), not infra or indexing. Current GSC state + owner actions live in the SEO plan's verification block (gitignored).
- Landing infra + deploy account trap (gmail acct, `wrangler whoami` first): `docs/plans/2026-07-01-landing-root-swap-migration-plan.md`; SEO-landing history: `docs/completed/seo-landing-page-2026-07-01.md`.
- **Legal pages live on Firebase Hosting, NOT on `gisti-ai.com`:** `https://gisti-app.web.app/privacy-policy` and `/terms` (files `hosting/public/*.html`, `firebase.json` sets `cleanUrls: true`). Both the landing footer and the app (`PaywallConfig.kt:12-13`) already point there. ⚠️ `gisti-ai.com/privacy` has never existed — its 404 is not a defect. **A 404 is only a finding once you have grepped who links that URL**; no linker, no finding (precedent 2026-08-11: typical paths were probed blind and the 404s reported as a broken landing page).

## Dependencies & Economics

All dependency versions live in `gradle/libs.versions.toml` — the single source of truth; check it, don't trust a number duplicated in prose. **Gemini SDK is intentionally NOT a client dependency** — all AI inference is server-side. Unit economics: `docs/unit-economics.md` (gemini-2.5-flash-lite ~$0.0002/req, positive at max usage). Geo-tiered pricing & organic-growth strategy (India + low-ARPU markets priced at minimal markup to drive organic installs/ratings; **gitignored, business-sensitive**): `docs/pricing-strategy.md`.

**Gemini models are an allowlist, not a constant** (`main.py:89-95`): `gemini-2.5-flash-lite` · `gemini-2.5-flash` · `gemini-3.1-flash-lite` · `gemini-3.5-flash`; anything else 500s. A live server A/B (`ai_model_arm`) runs `gemini-3.1-flash-lite` against control. ⏰ **All `gemini-2.5-*` hit EOL 2026-10-16** — including the control arm.

| Limit (Remote Config) | Free | Premium | Defaults in code |
|---|---|---|---|
| AI requests/day | 10 | 300 | `RemoteConfigKeys.kt:75-76` |
| Max checklists | 5 | unlimited | `:82` |
| Max fills/checklist | 5 | unlimited | `:83` |
| Recurring reminders | 10 | unlimited | `:84` |
| Weekly checklists | 1 | unlimited | `:85` |
| Attachments per item | 3 | unlimited | `:86` |
| Items per checklist | 100 | 100 | `:70` |

⚠️ **Read limits from Remote Config, never from a local constant.** A comment saying "mirrors X" is not checked by the compiler — treat it as a smell, not as documentation. Known stale mirrors, tracked in `docs/todos/` + `docs/backlog/`: `ToolCallDispatcherImpl.kt` still hardcodes `FREE_ATTACH_LIMIT_PER_ITEM = 3` against `max_attachments_per_item_free`, `PaywallScreen.kt:572` carries the same stale comment, and `main.py:292` holds a third value for the premium daily cap.

The shape of that class of defect matters more than any one number: a gate copy-pasted into two handlers drifts, so fixing one site leaves the other wrong — collapse it into one helper. Regression tests must pin **two different** config values; a fake pinned to the served value passes against a hardcoded number just as happily. Reference fix: `ToolCallDispatcherImpl.freeChecklistCeilingReached()` (2026-08-10).

## `.claude/rules/` map (file-scoped, auto-loaded on matching edits)

| Rule | Loads when you edit |
|---|---|
| `designsystem` | `core/designsystem/**`, `*Screen.kt` — colors, spacing, components, edge-to-edge insets |
| `compose-resources-kmp` | `composeResources/**`, `build.gradle.kts`, `strings.xml` — androidResources opt-in, localization |
| `gradle-compose-tooling-deps` | `**/build.gradle.kts` — ui-tooling/test deps must stay debug/test-only, not in release |
| `ui-card-patterns` | `*Card*.kt`, `*ItemDetailsSheet*.kt`, `*Pager*.kt`, `feature/home/**` — hit-zone, pager, double-padding |
| `adaptive-navigation` | `navigation/**`, `App.kt`, `*Navigator*.kt` — WindowSizeClass, Nav 3, adding destinations |
| `credit-restore` | `*Purchase*.kt`, `*Credits*.kt`, `*Restore*.kt`, `*Paywall*.kt` — restore flow, Firestore |
| `ai-chat` | `feature/aichat/**`, `firebase-functions/**` — tier routing, FEATURE_CATALOG, TDD rule |
| `checklist-domain` | `feature/checklist/**`, `*Checklist*.kt`, `*Fill*.kt`, `*Reminder*.kt` — template vs fill, reminders, KMP |
| `updates-feed` | `feature/updatefeed/**` — post rules, CTA whitelist |

For wasmJs interop, use `C:\Users\Admin\StudioProjects\swapfaceandroid` as the reference project.
