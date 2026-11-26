---
title: "Credits are the only AI gate; an empty wallet is a banner above the chat input"
summary: "No daily request cap anywhere: the server refuses only with 402 for credits, the chat answers it with a pinned out-of-credits banner (not a bubble), Analyze offers the paywall."
date: 2026-11-26
type: decision
modules: [firebase-functions, feature/aichat, feature/analyze, feature/user, core/designsystem]
keywords: [paywall, credits, out-of-credits-banner, OutOfCreditsBannerState, emitInsufficientCredits, CreditsConfigProvider, StoreEntitlementSource, NoticeBannerLayout, daily-limit, http-402, android, wasmjs]
project: Checklists
---

# Credits are the only AI gate; an empty wallet is a banner above the chat input

**Суть:** AI is gated by credits alone — no daily request counter on server or client; never reintroduce a 429. A chat 402 adds no assistant bubble: `ChatScreenState.outOfCredits` drives `OutOfCreditsBanner` above the input on all three chat surfaces. Analyze answers a 402 with a "Get Pro" CTA for Free accounts.

## Проблема / Контекст

Owner, 2026-11-26: «никакой логики лимитов, всё через кредиты»; out-of-credits as a system message like the Claude app. A server daily counter was enforced only in Analyze/Generate while chat incremented it unchecked, so a newcomer got a 429 with only «OK». In chat, the empty wallet was an assistant bubble.

## Решение

- Server: request-limit gate removed; `increment_usage` is telemetry only; empty wallet = 402.
- Chat: `OutOfCreditsBannerState` (`Free`/`Premium`/`Guest`) from the pure `resolveOutOfCreditsBanner`. Shown at balance `<= 0` or while the refusal latch (balance snapshot at the last 402) holds. Every 402, voice included, goes through `emitInsufficientCredits`: latch + `OutOfCreditsRefused` nudge, no bubble, no Layer 3, `credits_used=0`. Send is never blocked client-side.
- CTA number only from `premium_daily_credits_cap` via `CreditsConfigProvider`; no config → no number.
- Premium = store || Firestore via the `StoreEntitlementSource` interface in `feature/user`.
- Analyze: `FailedCta.Upgrade` → paywall (`analyze_insufficient_credits`); `@JsonNames("DAILY_LIMIT")` on `UNKNOWN` keeps old persisted jobs decodable.
- `NoticeBannerLayout` moved from `VoiceCallBanner` into `core/designsystem`.

## Почему именно так

- The wallet is account state, not the assistant's reply — a pinned notice stays while the user decides and clears itself.
- A stale client "0" already shipped once (#84); a refused turn costs nothing, so the server decides.
- Latch covers a refusal with some credits left; balance covers opening at zero.
- The interface avoids an `aichat → paywall` module cycle.
- Deploy the server before the client: the client no longer understands 429.

## Связанные файлы

- `firebase-functions/main.py`
- `feature/aichat/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/aichat/impl/presentation/OutOfCreditsBannerResolver.kt`
- `feature/aichat/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/aichat/impl/presentation/components/OutOfCreditsBanner.kt`
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/components/NoticeBannerLayout.kt`
- `feature/analyze/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/analyze/domain/model/AiFailureReason.kt`
- `.claude/rules/ai-chat.md`
