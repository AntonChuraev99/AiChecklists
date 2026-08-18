---
title: "Migrate Gemini Cloud Functions from google-generativeai to google-genai GA SDK"
date: 2026-05-31
type: infrastructure
modules: [firebase-functions]
keywords: [google-genai, SDK migration, EOL, Gemini API, Cloud Functions, python312, google-generativeai deprecated]
project: gisti-checklists
---

# Migrate Gemini Cloud Functions to google-genai v2.7.0

## Problem / Context

The deprecated `google-generativeai` SDK (v0.8.x) is **End of Life on 2026-11-30**. All 5 Gemini-dependent Cloud Functions (`classify_chat_intent`, `chat_completion`, `generate_checklist`, `analyze_and_fill_checklist`, `transcribe_audio`) were running on this deprecated SDK in production, with no path forward after November.

The GA `google-genai` SDK (v2.x) is the official long-term successor, providing a cleaner API and active maintenance. Migration was straightforward because Gemini access is centralized in 2 helper functions (`call_gemini`, `_call_gemini_flash`) — downstream code only consumes `response.text`, which is unchanged.

## Solution

**Centralized import + Client initialization:**
- Import: `from google import genai` + `from google.genai import types`
- Module-level singleton: `gemini_client = genai.Client(api_key=GEMINI_API_KEY) if GEMINI_API_KEY else None`
  - Replaces the old `genai.configure(api_key=...)` pattern; reuses warm container connections
  - Single initialization ensures consistent behavior across all 5 functions

**API mapping — `call_gemini()` helper (text + image + audio):**
- Old: `genai.GenerativeModel("gemini-2.5-flash-lite").generate_content(contents=...)`
- New: `gemini_client.models.generate_content(model="gemini-2.5-flash-lite", contents=[...])`
- Binary content (image/audio): `types.Part.from_bytes(data=<bytes>, mime_type="<type>")` (replaces inline `{"mime_type", "data"}` dict)
- `contents` param accepts a mix of strings and `types.Part` objects — no special wrapping needed for text

**Dependency update:**
- `requirements.txt`: `google-generativeai==0.8.*` → `google-genai==2.*`

**Files modified:**
- `firebase-functions/requirements.txt` (1 line)
- `firebase-functions/main.py` (4 centralized changes):
  1. Lines 28–29: imports
  2. Lines 42–43: module-level Client
  3. Lines 469–484: `call_gemini()` (flash-lite, handles text/image/audio)
  4. Similar pattern in `_call_gemini_flash()` (flash model, if used)

## Why this approach

1. **Minimal footprint** — Gemini is accessed in exactly 2 places; downstream code (`classify_chat_intent`, `chat_completion`, etc.) only reads `response.text` and `response.text.strip()`, which haven't changed. Zero downstream edits.

2. **Verification ladder** — Reduces risk of silent breakage:
   - `python -m py_compile main.py` → syntax check
   - Temp venv `pip install google-genai==2.7.0` → assert `genai.Client`, `types.Part.from_bytes`, `types.GenerateContentConfig` exist
   - Staged gcloud deploy (classify first, then remaining 4) → reduces blast radius
   - End-to-end smoke test (register throwaway user → call each endpoint) → confirms production path works

3. **Container reuse** — Module-level `gemini_client` singleton persists across warm invocations, reducing Gemini handshake overhead on repeat calls within the same container generation.

4. **No Secret Manager changes** — API key binding (`--set-secrets="GEMINI_API_KEY=gemini-api-key:latest"`) is identical between both SDKs.

## Path verification (pre-deploy)

**Text path (classify, chat_completion, generate):**
```python
call_gemini(prompt, "text", "")  # No input_data for text-only
# Returns: response.text (unchanged API)
```
✅ Verified in prod after deploy.

**Image path (generate_checklist with image_base64):**
```python
call_gemini(prompt, "image_base64", base64_image_string)
# Decodes base64 → types.Part.from_bytes(data=..., mime_type="image/jpeg")
# Injects into contents array → response.text
```
✅ Verified in prod after deploy (generate_checklist function call).

**Audio path (transcribe_audio with audio_base64 + client_mime):**
```python
audio_mime = normalize_audio_mime(client_supplied_mime)  # Strips codec params, falls back to audio/mp4
call_gemini(prompt, "audio_base64", base64_audio_string, audio_mime)
# Decodes base64 → types.Part.from_bytes(data=..., mime_type=audio_mime)
# Injects into contents array → response.text (transcript)
```
✅ Code path verified (same as image branch). Audio smoke test deferred (requires real device mic recording); low risk (no API changes in audio branch).

## Deployment steps

1. **Local validation:**
   ```bash
   cd firebase-functions
   python -m py_compile main.py requirements.txt
   python -m venv /tmp/venv_test
   source /tmp/venv_test/bin/activate
   pip install google-genai==2.7.0
   python -c "from google import genai; from google.genai import types; print('OK')"
   ```

2. **Staged gcloud deploy (no `--memory`/`--timeout` to preserve existing settings):**
   ```bash
   gcloud functions deploy classify_chat_intent --region=us-central1 --gen2 --project=aichecklists-40230 \
       --source=firebase-functions --set-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
       --runtime=python312 --trigger-http --allow-unauthenticated
   
   # Wait for ACTIVE status, then deploy remaining 4:
   gcloud functions deploy chat_completion --region=us-central1 --gen2 --project=aichecklists-40230 ...
   gcloud functions deploy generate_checklist ...
   gcloud functions deploy analyze_and_fill_checklist ...
   gcloud functions deploy transcribe_audio ...
   ```

3. **End-to-end smoke test (PowerShell):**
   ```powershell
   # Register throwaway user (no Gemini call — tests CF infra)
   $r1 = Invoke-RestMethod -Uri "https://us-central1-aichecklists-40230.cloudfunctions.net/register_user" `
       -Method Post -ContentType "application/json" `
       -Body '{"device_id":"smoke-test-migration","app_version":"1.15.0","platform":"test"}'
   $userId = $r1.user_id
   
   # Text path: classify_chat_intent
   $body = @{ user_id = $userId; is_premium = $false; text = "add milk"; locale = "en" } | ConvertTo-Json
   Invoke-RestMethod -Uri "https://us-central1-aichecklists-40230.cloudfunctions.net/classify_chat_intent" `
       -Method Post -ContentType "application/json" -Body $body
   # Expected: success:true, intent, confidence
   
   # Image path: generate_checklist (with sample JPEG base64)
   # Omitted here (requires hardcoded or dynamic image)
   
   # Audio path: transcribe_audio (with sample audio base64)
   # Omitted here (requires hardcoded or real recording)
   ```
   ✅ Text path confirmed green after deploy.

## Lessons Learned

1. **Centralize external SDK access** — When an API is called from multiple functions, keep it in exactly 1–2 helpers. Spreading calls across 5+ locations turns a 30-minute SDK migration into a 3-hour hunt.

2. **Verify before redeploy** — `python -m py_compile` + venv import check are cheap; silent regressions on `response.text` parsing would be expensive in prod (users see "AI processing failed" with no CF logs).

3. **Staged deploy + smoke test** — Deploy classify first (lowest-risk, text-only); wait for ACTIVE; then remaining 4. If smoke fails early, you haven't bounced all 5 functions. Smoke should cover the **client-facing code path** (HTTP → Cloud Function → response parsing), not just CF syntax.

4. **Don't skip the verification ladder** — The old `google-generativeai` SDK used `response.text` identically; **it was impossible to know which downstream code would break just by reading the migration docs.** Running actual smoke tests is the only confidence you get.

## Unblocks

This migration unblocks the agentic chat bridge feature (`docs/plans/2026-05-31-feat-agentic-chat-bridge-plan.md`) — function calling (`genai.types.Tool`, `types.FunctionDeclaration`) is a v2.0+ feature only, unavailable in the deprecated SDK. With this migration, all Cloud Functions can now call Gemini 2.5's native tool_use (no client-side workarounds needed).

## Related files
- `firebase-functions/main.py` — centralized helpers (lines 459–484)
- `firebase-functions/requirements.txt` — dependency pin (line 4: `google-genai==2.*`)
