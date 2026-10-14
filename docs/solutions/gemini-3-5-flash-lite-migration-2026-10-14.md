# Gemini 3.1 Flash-Lite → 3.5 Flash-Lite: `thinking_budget=0` is a 400

**Date:** 2026-10-14 · **Category:** migration · **Area:** `firebase-functions/main.py`

## Problem

Moving every AI flow to `gemini-3.5-flash-lite` (GA 2026-07-21). Just flipping the server RC params `ai_model_<flow>` would have broken the chat: `chat_agent` sent `ThinkingConfig(thinking_budget=0)`, which 3.5 Flash-Lite rejects with `400 INVALID_ARGUMENT`. Google's migration page does not say the old field became a hard error.

## Live probe (2026-10-14, raw REST `generationConfig` field names — источник: Gemini REST API, one function tool)

| Config | 3.1 Flash-Lite | 3.5 Flash-Lite |
|---|---|---|
| `thinking_budget=0` | 200 | **400** |
| `temperature: 0.4` only | 200 | 200 (ignored, deprecated) |
| `thinking_level` minimal | 200 | 200 |
| turn 2, function call with thought signature round-tripped | 200 | 200 |
| turn 2, signature stripped | 400 | 400 |
| plain prompt, no config | 200 | 200 |

## Fix

- `chat_agent`: `thinking_level=types.ThinkingLevel.MINIMAL`, `temperature` removed. `minimal` is valid on both models, so RC rollback stays safe.
- `MODEL_OVERRIDE_ALLOWLIST` gains 3.5 Flash-Lite and keeps 3.1 for rollback; all in-code defaults → 3.5 Flash-Lite.

## Rollout order (matters)

1. Deploy the Cloud Functions first. RC still says 3.1, so prod runs 3.1 with the new `minimal` config, which the probe showed works.
2. Smoke test.
3. Switch the server template (`firebase-server`) `ai_model_*` params to `gemini-3.5-flash-lite`. The CF caches the template for `_RC_TTL_SECONDS`.
4. Smoke test again. Rollback = set the params back to `gemini-3.1-flash-lite`, with no redeploy.

## Trade-offs

- Price (ai.google.dev pricing, 2026-10-14): text input and output cost more than on 3.1, audio input costs less. Recheck the gitignored unit-economics doc.
- For multi-step tool agents Google advises `medium` thinking; it bills thinking tokens as output. `minimal` stays until chat quality says otherwise.
- `gemini-2.5-*` were dropped from the allowlist two days before their EOL: Gemini docs list `thinking_level` for Gemini 3 only, so a 2.5 RC value or eval override would 400 `chat_agent`.

## Lesson

An RC model swap needs no redeploy only if the request config is valid for both models. Probe the **exact** prod request shape (tools + thinking + sampling) first, not a plain prompt.
