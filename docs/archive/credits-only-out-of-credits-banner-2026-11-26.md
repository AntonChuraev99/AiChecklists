# Credits-only AI gate + out-of-credits banner in the AI chat

**Статус:** Done
**Дата старта:** 2026-11-26
**Start SHA:** 085aac3a
**Project:** Checklists
**Тип:** feature
**Затронутые модули:** firebase-functions, feature/aichat, feature/analyze, feature/user, feature/paywall, core/designsystem, core/remoteconfig, feature/voicecall, composeApp

## Цель (продуктовая)

Owner, 2026-11-26: «у нас вообще не должно быть никакой логики лимитов, всё через кредиты» and «выводить системное сообщение в чат, референс как у claude приложения: твои кредиты кончились, купи подписку».

Trigger: a newcomer on 1.25.0 chatted (the chat CFs bump the shared daily request counter but never check it), then Analyze refused with HTTP 429 «Daily limit» and only an «OK» button — no paywall. In the chat itself an empty wallet was an assistant bubble with a CTA chip.

Target:
- the server gates AI only by credits (HTTP 402), no daily request counter;
- the chat shows a pinned banner above the input (Free → Get Pro, Premium → refill time, web guest → Sign in) instead of an assistant bubble;
- Analyze on 402 offers the paywall to Free accounts.

## Технический план

1. Server (`firebase-functions/main.py`): remove `check_usage_limit` and both 429 gates (analyze, generate); `increment_usage` stays as telemetry. Deploy analyze + generate after owner OK.
2. Client logic (@feature-expert): `OutOfCreditsBannerState` (Free / Premium / Guest with `lowBalance`), refusal latch, `emitInsufficientCredits` → banner + `OutOfCreditsRefused` instead of a bubble, voice 402 same path, `CreditsConfigProvider` in `feature/user` (number only from `premium_daily_credits_cap`), Analyze `FailedCta.Upgrade`, remove client daily-limit traces (`AiFailureReason.DAILY_LIMIT` with a `@JsonNames` fallback for the persisted job queue, RC keys `ai_daily_limit_free` / `ai_daily_limit_premium`).
3. Banner UI (@compose-expert): `NoticeBannerLayout` moved from `VoiceCallBanner` into the design system; `OutOfCreditsBanner` per DESIGN_SPEC; then placement in the three input slots (full-screen chat, v2 dock, full-screen overlay) + screenshot matrix.
4. Docs: `CLAUDE.md` limits, rule `ai-chat` § 402.

Key decision: the banner never blocks Send — the server stays the gate, a refusal costs 0 credits, and a stale client balance (the #84 bug) must not lock out a user who has credits.

## Лог итераций

### Итерация 1 — 2026-11-26 — general-purpose: server daily-limit removal + tests → done, not deployed (4 pre-existing `test_main.py` failures unchanged)
### Итерация 2 — 2026-11-26 — feature-expert SPEC + design-expert DESIGN_SPEC → contract compiles; specs reconciled by the main agent
### Итерация 3 — 2026-11-26 — main: `CLAUDE.md` + rule `ai-chat` § 402 rewritten for credits-only + banner
### Итерация 4 — 2026-11-26 — compose-expert: `NoticeBannerLayout` in designsystem (VoiceCall Roborazzi verify unchanged) + `OutOfCreditsBanner` per DESIGN_SPEC with a Roborazzi matrix → matches reference shots
### Итерация 5 — 2026-11-26 — feature-expert IMPLEMENT: banner logic + latch, voice 402, `CreditsConfigProvider` + `StoreEntitlementSource` (premium = store || Firestore without a module cycle), Analyze `FailedCta.Upgrade`, client daily-limit removal with a `@JsonNames("DAILY_LIMIT")` fallback → tests green, debug APK build + wasm compile green
### Итерация 6 — 2026-11-26 — compose-expert phase B: banner in the three input slots (full-screen chat, v2 dock, full-screen overlay), dock chips hidden, nudge wired, Analyze Upgrade branch, screenshot matrix → aichat/composeApp/voicecall/designsystem verify clean
**Итог итерации:** `feature:analyze` Roborazzi verify is red on `AnalyzeSourcePickerScreenshotTest` + `AnalyzePlaybackErrorScreenshotTest` — pre-existing: their baselines date from `214d2a53`, and `a276fe26` (#84) rewrote `AnalyzeScreen.kt` (cost from `get_credits_info` instead of a hardcoded value) without re-recording them; this branch changes one line of `AnalyzeScreen.kt`.

### Итерация 7 — 2026-11-26 — main: `/task-gate full` — Standards + Spec review; decoder test re-pinned to a non-served `action_cost`; `firebase-functions/README.md` Limits section rewritten
### Итерация 8 — 2026-11-26 — main: CF deploy analyze + generate (owner OK) → prod smoke 200 / 402, no 429, no ERROR logs

## Выводы

- Server and client gate AI by credits only; the chat answers a 402 with the pinned banner on all three surfaces; Analyze offers "Get Pro" to Free accounts on a 402. Decision: `docs/decisions/2026-11-26-credits-only-ai-gate-and-out-of-credits-banner.md`.
- Verified: gradle tests `feature:aichat:impl` / `feature:analyze` / `feature:user`, `:composeApp:compileKotlinWasmJs`, `:androidApp:assembleDebug` (gradle tasks — источник: gradle runs in this session), Roborazzi verify `feature:aichat:impl` / `composeApp` / `feature:voicecall` — green; offline pytest — only the pre-existing failures.
- Deployed 2026-11-26 before the client (the client no longer handles 429): revisions `analyze-and-fill-checklist-00042-xuq`, `generate-checklist-00047-seb` (источник: `gcloud functions deploy` output). Prod smoke on a throwaway user: generate and analyze answer 200 while credits last, the next action gets 402 `insufficient_credits` on both, no 429; no ERROR logs on the new revisions (источник: curl + `gcloud logging read` in this session).
- Pre-existing, not touched: `feature:analyze` Roborazzi baselines for `AnalyzeSourcePickerScreenshotTest` / `AnalyzePlaybackErrorScreenshotTest` are stale since `a276fe26`; paywall copy still hardcodes the premium credit number; the Analyze form barrier has no CTA; the Telegram bot keeps a dead `dailyLimit` branch; Firebase RC console still holds `ai_daily_limit_free` / `ai_daily_limit_premium`.
