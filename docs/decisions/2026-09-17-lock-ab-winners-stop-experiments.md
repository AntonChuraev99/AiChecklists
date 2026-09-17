---
title: "Lock A/B winners in Remote Config and stop running experiments"
summary: "Firebase A/B experiments were expiring onto template defaults nobody chose; winners are locked in both RC templates and every code fallback now matches the template."
date: 2026-09-17
type: decision
modules: [core:remoteconfig, feature:paywall, composeApp, firebase-functions]
keywords: [paywall, ab-test, remote-config, firebase-ab-testing, experiment-expiry, onboarding, activation_bundle_v1, ai_model_arm, gemini-3.1-flash-lite]
project: gisti-checklists
---

# Lock A/B winners and stop running experiments

**Summary:** no product experiment runs; each former A/B key holds one value, identical in the RC template and the code fallback. Supersedes the local-only ADR `2026-07-15-keep-revenue-objective-experiments-running`.

## Problem / Context

- Firebase A/B Testing expires an experiment on its own at its maximum run length and silently serves the **template** default.
- The offer experiment expired 2026-09-13, `activation_bundle_v1` 2026-09-16; `OnboardingABC` would have moved Android to slides (source: RC REST `namespaces/firebase/experiments`).
- Template and code fallback disagreed for `activation_bundle_v1`, `paywall_config` and `main.py` models.
- `gemini-2.5-*` shut down 2026-10-16 (source: ai.google.dev Gemini API changelog).

## Decision

| Key | Layer | Locked value |
|---|---|---|
| `onboarding` | client RC | `ai_welcome` (Web → `none`) — the only significant result |
| `activation_bundle_v1` | client RC | `false` |
| `paywall_config` | client RC | `{"currentOffer":"monthAndYear"}` — keeps the 3-day trial |
| `paywall_variant` | client RC | `features_v1` |
| `ai_model_*` (5 flows) | server RC | `gemini-3.1-flash-lite` |
| `ai_model_arm` / `push_ab_arm` | server RC | `variant_b` / `control` |

Applied 2026-09-17: client template v20 → v21, server v3 → v4 with its percentage conditions removed (source: RC server template v3). `OnboardingABC` is stopped in the console.

Code fallbacks aligned: `RemoteConfigDefaults.ACTIVATION_BUNDLE_V1` is false, `PaywallRemoteConfig.DEFAULT_OFFER` is the `monthAndYear` offering, `init.js.template` turns the bundle off, and every model default in `main.py` is the locked model. The control arm label on the RC-failure path stays — it means Remote Config did not decide.

## Why this way

- Locking is setting `defaultValue`, and the code fallback must match, or a failed fetch enrols the user into the losing arm.
- Non-significant verdicts are product calls: keep what is live and the trial path.
- Still in force: no new revenue-objective experiments.
- The local-only ADR `2026-07-28-rc-fallback-ai-welcome-drops-onboarding-paywall` now applies to the whole Android onboarding. Revisit if trial starts from onboarding stay absent for four weeks after the lock.
- Re-read AI chat feedback on 2026-10-15; if it degrades, probe `gemini-3.5-flash` for `chat_agent` first.

## Deploy and rollback

`main.py` reaches prod only via a Cloud Functions redeploy, **before 2026-10-16**. Client fallbacks ship with the next release. Rollback: republish client v20 / server v3 from RC version history.

`transcribe_audio` calls `call_gemini` without `model_id`, so the redeploy moves voice transcription to the locked model, never tested on audio. Smoke-test it after the redeploy; RC rollback does not revert it.
