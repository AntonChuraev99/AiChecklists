---
title: "Firebase Custom authDomain requires GCP OAuth client Authorized redirect URIs"
date: 2026-07-02
type: bug-fix
modules: [web, infrastructure, firebase-auth]
keywords: [firebase-auth, authdomain, gcp-oauth, redirect-uri-mismatch, google-signin, custom-domain, cloudflare, wasmjs, oauth]
project: Checklists
---

# Firebase Custom authDomain + GCP OAuth Redirect URI Registration

## Проблема / Контекст

When migrating a web app (especially Compose wasmJs) from one origin to another (e.g., `gisti-ai.com` → `app.gisti-ai.com`), Firebase Auth's `authDomain` config must follow the app to the new origin. However, simply updating Firebase Console authorized domains is insufficient — the **GCP OAuth 2.0 client** (separate from Firebase Console) must ALSO be updated with the new redirect URIs.

**Symptom:** User clicks "Sign in with Google" → Google OAuth flow starts → browser returns:
```
error=redirect_uri_mismatch
error_description=The redirect_uri parameter does not match any registered URI
```

The proxy (`/__/auth/*`) may pass traffic checks; DNS/TLS may resolve; theoretical authorization may appear complete — but OAuth fails at the exchange.

**Root cause:** Firebase Console's "Authorized domains" is ONE registration point. The **GCP OAuth 2.0 client** (created in Google Cloud → APIs & Services → Credentials) is a SEPARATE authorization point. Firebase Console does NOT automatically sync OAuth client config; you must update both.

## Решение

**Two-step registration:**

1. **Firebase Console** → Authentication → Settings → Authorized domains:
   - Add the new origin: `https://app.gisti-ai.com`
   - No expiry; can leave old origins during transition.

2. **Google Cloud Console** → APIs & Services → Credentials → OAuth 2.0 Client (`Web application`):
   - Locate the client ID used by your Firebase project (visible in `GoogleService-Info.plist` / `google-services.json` / Firebase config).
   - **Authorized JavaScript origins:** Add the new origin (e.g., `https://app.gisti-ai.com`).
   - **Authorized redirect URIs:** Add **BOTH**:
     - `https://app.gisti-ai.com` (JS origin)
     - `https://app.gisti-ai.com/__/auth/handler` (Firebase Auth helper callback)
   - Save.

**In code:** Set `FIREBASE_WEB_AUTH_DOMAIN=app.gisti-ai.com` (build-time config) and ensure the reverse-proxy serving `/__/auth/*` is reachable at that origin (same-origin requirement in browsers with Privacy Sandbox, Chrome 130+).

**Verification:**
```bash
# On the app's new origin, verify the proxy is same-origin:
curl -i https://app.gisti-ai.com/__/auth/handler
# Should return 200 or 307 (proxied), NOT 404 (from a different worker).
```

Drive the actual OAuth handshake with a real browser or e2e test (Playwright):
```typescript
// Playwright example: assert sign-in reaches Google consent screen (no redirect_uri_mismatch)
await page.goto('https://app.gisti-ai.com/');
await page.click('button[aria-label="Sign in with Google"]');
// Expect Google's consent page; if redirect_uri_mismatch → GCP OAuth client not updated.
```

## Почему именно так

**Firebase Console vs. GCP OAuth client — two separate gates:**

1. **Firebase Console authorized domains** — gate for the SDK to accept the origin as legitimate (prevents accidental misconfiguration). Validated by `firebase-js-sdk` when calling `initializeAuth()`.

2. **GCP OAuth 2.0 client** — gate for Google's OAuth server. When `firebase-auth` initiates the OAuth flow, it builds a redirect URL (`app.gisti-ai.com/__/auth/handler`). Google OAuth server validates:
   - Request origin and client ID match (same request).
   - Redirect URI in the request is registered on the client.
   
   If redirect URI is not registered, OAuth exchange fails with `redirect_uri_mismatch` *regardless* of Firebase Console settings.

**Why both matter:** Firebase Console prevents SDK errors on unregistered domains; GCP OAuth client prevents Google from accepting the request. Updating only one leaves a half-broken state — the proxy works, DNS works, but OAuth handshake fails.

**Chrome 130+ Privacy Sandbox:** Third-party iframe storage (including OAuth helper iframes) is partitioned by first-party site. Firebase Auth's `/__/auth/*` helper MUST be same-origin with the app to avoid cross-origin restrictions. This requirement made the authDomain change necessary; the GCP OAuth client update is the consequence.

## Примеры

**Before (app on `gisti-ai.com`):**
```
Firebase Console authorized domains:
  - gisti-ai.com

GCP OAuth 2.0 client:
  JS origins: https://gisti-ai.com
  Redirect URIs: https://gisti-ai.com/__/auth/handler
```

**After (app migrated to `app.gisti-ai.com`):**
```
Firebase Console authorized domains:
  - gisti-ai.com (keep during transition)
  - app.gisti-ai.com (NEW)

GCP OAuth 2.0 client:
  JS origins: https://app.gisti-ai.com (CHANGED)
  Redirect URIs: https://app.gisti-ai.com/__/auth/handler (CHANGED)
```

## Связанные файлы

- `composeApp/build.gradle.kts:193` — `FIREBASE_WEB_AUTH_DOMAIN` build-time config (default must match app origin).
- `src/redirect.js` — reverse-proxy for `/__/auth/*` (must be reachable at the app's origin).
- Cloudflare Worker `wrangler.jsonc` — route claim for the app origin.

## Lessons Learned

**Stage/Playwright OAuth testing pattern:** Theoretical checks (DNS, TLS, proxy routes, Firebase Console settings) can all pass while OAuth still fails due to GCP OAuth client misconfiguration. **Structural verification is not sufficient for OAuth.**

**Solution:** Before cutover to production, deploy a STAGE version of the app (e.g., a preview worker) and run e2e tests that drive the real `globalThis.__googleSignIn` bridge. Assert:
1. Proxy `/__/auth/*` returns 200/307 (same-origin, not 404).
2. No `unauthorized-domain` error from Firebase SDK.
3. OAuth reaches Google's consent screen (no `redirect_uri_mismatch`).

A single Playwright spec can verify all three in one pass *before* production cutover, saving deployment cycles when config is incomplete.

**Mitigation for future custom-domain migrations:** Document both console points (Firebase + GCP OAuth) as required steps, not optional. Include an e2e test in the migration runbook — don't rely on curl/browser manual checks to catch OAuth failures.
