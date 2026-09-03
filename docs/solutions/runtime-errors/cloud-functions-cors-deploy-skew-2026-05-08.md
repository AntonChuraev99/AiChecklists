---
title: "Cloud Functions CORS Deploy Skew — Prod Code ≠ Deployed Artifact"
date: 2026-05-08
type: bug-fix
modules: [firebase-functions, composeApp/wasmJsMain]
keywords: [cors, cloud-functions, deploy-skew, firebase-deploy, registration-failure, onboarding-loop, preflight-error, operational-bug]
project: Checklists
---

# Cloud Functions CORS Deploy Skew — Production Code Mismatch

## Problem

**Symptom (Web Frontend):**
- New user visits https://checklists.churaevanton.workers.dev/
- Onboarding screen loads
- User completes onboarding, taps "Get Started"
- Network call to `POST /register_user` fails with CORS error
- Page reloads, onboarding replays indefinitely

**Root Cause:** Cloud Functions deployed to production do NOT include the CORS preflight handler, even though the code in the repository (`firebase-functions/main.py`) has had it since commit `c3761e53` (2026-05-07). **Code in git ≠ Code in production.**

## Diagnostic Curl Test (5 seconds)

```bash
curl -X OPTIONS https://us-central1-aichecklists-40230.cloudfunctions.net/register_user \
  -H "Origin: https://checklists.churaevanton.workers.dev" \
  -H "Access-Control-Request-Method: POST" \
  -v
```

**Before deploy:** HTTP 400, no CORS headers (old code, not redeployed)
**After deploy:** HTTP 204, full CORS headers present (new code is live)

## Why This Happens: Deployment Skew

1. Code merged to `master` with CORS fix
2. Wasm artifact auto-deploys via Cloudflare Workers Builds
3. Cloud Functions NOT auto-deployed (no CI/CD, manual `firebase deploy` required)
4. Result: Frontend expects CORS headers, backend doesn't provide them (running old code)

## Fix

Run from repo root:

```bash
firebase deploy --only functions:register_user,functions:analyze_and_fill_checklist,functions:generate_checklist,functions:restore_credits_after_purchase,functions:get_usage_stats,functions:get_credits_info
```

Then verify immediately with the curl test above (should return 204).

## Why This Will Happen Again

- No CI/CD for Cloud Functions
- No post-deploy smoke test
- Unclear ownership (should frontend deploy also trigger functions deploy?)

## Guardrails (Recommended)

### 1. Deploy Script with Smoke Test

Create `firebase-functions/deploy.ps1` to deploy + automatically verify CORS headers with curl.

### 2. PR Checklist Reminder

Add checkbox: "If I edited `firebase-functions/main.py`, did I run `firebase deploy` and verify with curl?"

### 3. Code Comment

Add warning in `firebase-functions/main.py` header: "After editing this file, must run `firebase deploy` and verify with curl OPTIONS preflight test."

## Symptom Chain: Why Onboarding Loops

SplashViewModel.ensureUserRegistered() → POST /register_user → CORS fails → userId not persisted to localStorage → next app start reads empty userId → onboarding replays.

## Related Issues

- wasmjs-web-target-cloudflare-2026-05-08.md: Deployment plan mentioned this risk
- Deploy skew is general operational pattern: auto-deploy (wasmJs) vs manual-deploy (functions) create weak point

