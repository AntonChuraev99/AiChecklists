---
title: "Prod-Only RC & Auth Failures — Signing/Key Restrictions Diagnostics"
date: 2026-06-19
type: bug-fix
modules: [core/auth, core/remoteconfig, feature/splash, feature/home]
keywords: [Firebase Remote Config, Google Sign-In, signing key, API restrictions, App Check FIS, Identity Toolkit, prod-only bug, diagnostics, rc_error analytics]
project: checklists
---

# Prod-Only RC & Auth Failures — Signing/Key Restrictions Diagnostics

## Problem / Context

**Two separate prod-only bugs revealed themselves only on Play-signed builds** — debug builds (with debug SHA-1 registered in Firebase) masked both:

1. **Remote Config always returned default** (`onboarding=slides`), despite server having correct configuration. Users hit the A/B test always seeing slides variant.
2. **Google Sign-In always failed** with "An internal error has occurred," blocking new-user onboarding entirely on first sign-in.

Both bugs were **signing/key-infrastructure related**, not code logic. The bugs vanished in debug builds because:
- Debug SHA-1 is auto-registered in Firebase (either by Gradle on first run or via console).
- API key restrictions apply differently (dev key vs Play Signing key).
- Testing on emulator masks OAuth-client and API-allowlist misconfigurations.

## Solution

### Part 1: Remote Config Silent Failures → rc_error Diagnostics

**Root Cause:** `FirebaseRemoteConfigProvider.fetchAndActivate()` was catching and silently swallowing network/auth errors. On prod-signed builds, the error was **"authentication denied"** from Firebase Installations (FIS) service — the app's SHA-1 was not registered as an authorized signer for Remote Config.

**Why it failed on prod:** Google Play signs the APK with a different SHA-1 than debug builds. The Firebase console lists only the debug SHA-1 under "App Signing" → prod SHA-1 is not recognized → FIS denies the RC fetch request.

**Diagnostics Added:**

1. **`RemoteConfigProvider` interface** (commonMain) adds optional method:
   ```kotlin
   suspend fun lastFetchError(): Throwable? = null
   ```
   Default no-op for platforms that don't implement it. AndroidFirebaseRemoteConfigProvider overrides to expose the last caught error.

2. **`FirebaseRemoteConfigProvider` (androidMain)** now stores caught exceptions:
   ```kotlin
   private var lastFetchException: Throwable? = null
   
   override suspend fun fetchAndActivate(): Boolean = withContext(dispatcher) {
       try {
           remoteConfig.fetchAndActivate().await()
       } catch (e: Exception) {
           lastFetchException = e  // Capture for diagnostic
           logger.error(TAG, "fetchAndActivate() failed: ${e.message}", e)
           false
       }
   }
   ```

3. **Analytics event `onboarding_rc_resolved`** now includes:
   - `rc_activated: Boolean` (did fetch succeed)
   - `rc_value_empty: Boolean` (does the server have the config)
   - `rc_error: String?` (error message if fetch failed, null otherwise)
   - `fetch_ms: Long` (how long the fetch took)

   This single event **proved the root cause in one prod run:** `fetch_ms ∈ [1, 317]ms` (instant failure, not a slow-network timeout), `rc_error="FirebaseException: [500 INTERNAL]"` (authentication denied).

4. **`SplashViewModel` emits the event** before deciding slides/none/interactive:
   ```kotlin
   val fetchMs = measureTimeMillis { 
       val activated = remoteConfigProvider.fetchAndActivate() 
   }
   val rcError = (remoteConfigProvider as? AndroidRemoteConfigProvider)
       ?.lastFetchError()?.message
   
   analyticsHelper.logEvent("onboarding_rc_resolved", mapOf(
       "rc_activated" to activated,
       "rc_value_empty" to onboardingValue.isEmpty(),
       "rc_error" to rcError,
       "fetch_ms" to fetchMs
   ))
   ```

**Fixer (User's Action):** Add the **Play App Signing SHA-1** to Firebase Console:
1. Google Play Console → App → Release → Setup → App Signing (note the SHA-1).
2. Firebase Console → Project Settings → App (Android) → "SHA certificate fingerprints" → Add the Play App Signing SHA-1 from step 1.
3. Release a new build and upload to Play → Firebase FIS recognizes the signer → RC fetches successfully.

Confirmed: user added the SHA-1, re-uploaded 1.17.4(53) → RC activated on next user install.

### Part 2: Google Sign-In Silent Failures → login_* Analytics

**Root Cause:** `FirebaseAuth.signInWithCredential(idToken)` was throwing `FirebaseAuthException` with message: `"An internal error has occurred. [ Requests to this API identitytoolkit method google.cloud.identitytoolkit.v1.AuthenticationService.SignInWithIdp are blocked. ]"`.

This error **only appears on prod-signed builds** because:
- Credential Manager (Android system OAuth) works fine on both debug and prod.
- OAuth client ID in `google-services.json` is correct on both.
- But **FirebaseAuth uses a different API key** (from `google-services.json`) to call Identity Toolkit service.
- The API key has **API restrictions** (to prevent quota-abuse, valid security practice).
- The API restrictions **allowlist was missing Identity Toolkit API** → prod build hits the restriction → 403/blocked error.

**Why debug succeeded:** The `google-services.json` in the repo is the dev Firebase app (different API key, different restrictions — or none).

**Diagnostics Added:**

1. **`AnalyticsEvents.Auth` sealed class** (commonMain) with explicit events:
   ```kotlin
   sealed class Auth : AnalyticsEvent("auth") {
       object SignInStarted : Auth()
       data class SignInSuccess(val provider: String) : Auth()
       data class SignInFailed(val errorCode: String?, val errorMessage: String?) : Auth()
   }
   ```

2. **`AndroidGoogleAuthProvider` captures and logs both idToken path and error path:**
   ```kotlin
   override suspend fun signInWithGoogle(...): Result<SignInResult> = withContext(dispatcher) {
       try {
           val credential = credentialManager.getCredential(...)
           val idToken = (credential.credential as GoogleIdTokenCredential).idToken
           
           if (idToken == null) {
               analyticsHelper.logEvent(Auth.SignInFailed(
                   errorCode = "no_idToken",
                   errorMessage = "Credential Manager returned null idToken"
               ))
               return@withContext Result.failure(...)
           }
           
           val authResult = firebaseAuth.signInWithCredential(
               GoogleAuthProvider.getCredential(idToken, null)
           ).await()
           
           analyticsHelper.logEvent(Auth.SignInSuccess(provider = "google"))
           return@withContext Result.success(SignInResult(...))
       } catch (e: GetCredentialException) {
           // Credential Manager itself failed (rare)
           analyticsHelper.logEvent(Auth.SignInFailed(
               errorCode = "get_credential_failed",
               errorMessage = e.type  // e.g., GetCredentialException.Type.NO_CREDENTIALS
           ))
           Result.failure(e)
       } catch (e: FirebaseAuthException) {
           // FirebaseAuth or Identity Toolkit failed
           analyticsHelper.logEvent(Auth.SignInFailed(
               errorCode = "firebase_auth_exception",
               errorMessage = e.message  // Includes "identitytoolkit... blocked" hint
           ))
           Result.failure(e)
       }
   }
   ```

3. **`MainScreenViewModel` tracks idToken reception:**
   ```kotlin
   private fun onSignIn(...) {
       viewModelScope.launch {
           val result = authRepository.signInWithGoogle(...)
           
           // If result succeeds but idToken is somehow null, log it explicitly
           if (result.isSuccess && result.getOrNull()?.idToken == null) {
               analyticsHelper.logEvent(Auth.SignInFailed(
                   errorCode = "signed_in_but_no_token",
                   errorMessage = "Auth succeeded but idToken missing"
               ))
           }
           // ...
       }
   }
   ```

**What the Single Run Revealed:** One user prod-run with added analytics showed `login_failed.error_code=firebase_auth_exception` and `login_failed.error_message="...identitytoolkit...blocked"` → immediately identifiable as an API allowlist issue, not OAuth or Credential Manager.

**Fixer (Server, No Client Rebuild):** Add missing APIs to the API key restrictions:
1. Google Cloud Console → APIs & Services → Credentials (find the API key used by Firebase).
2. Click the key → "API restrictions" → "Select APIs" → add **Identity Toolkit API** and **Token Service API**.
3. Save → FirebaseAuth now passes Identity Toolkit API-key checks → `signInWithCredential` succeeds.

Confirmed: user added the APIs to allowlist → no code changes needed → existing 1.17.4(53) build signs in successfully on next app restart.

## Why Exactly This Happened

| Component | Debug Build | Prod Build (Play Signed) |
|---|---|---|
| **APK signer** | Debug keystore (auto-generated or developer-provided) | Google Play Signing key (managed by Google) |
| **SHA-1 registered in Firebase** | Yes (either auto or manual setup during onboarding) | **Historically no** — requires manual add |
| **Remote Config fetch attempt** | Signed with registered debug SHA → FIS accepts → fetch succeeds | Signed with unregistered Play SHA → FIS denies → silent catch |
| **API Key restrictions** | Dev key (likely fewer restrictions) | Prod key (allowlist is strict) |
| **Identity Toolkit in allowlist** | Usually unrestricted in dev | **Historically missing** — requires manual add |
| **Sign-In result** | Success | 403 "blocked" error, silently caught |

**Why One User Triggered Both:** The user hit onboarding (RC needed) → got stuck in sign-in (Auth failure) → stuck in splash loop. The second fix (API allowlist) was independent but equally critical.

## Lessons Learned

### L1: Instrument Concrete Errors, Not Hypotheses
All pre-diagnostic guesses were red herrings:
- "RC timeout due to slow network" ← Refuted by `fetch_ms ∈ [1, 317]ms`.
- "OAuth client is misconfigured" ← Refuted by Credential Manager succeeding.
- "App Check is blocking the request" ← No App Check configured; never the culprit.

The moment `rc_error` and `login_failed.error_message` were logged, the real causes (FIS SHA / API allowlist) were named instantly. **Add logging first, theorize second.**

### L2: Prod Signing ≠ Debug Signing
Firebase + Google Play integration has multiple registration points:
- **Firebase Console:** needs the Play Signing SHA-1, not debug SHA.
- **Google Cloud API key:** needs API allowlist, not open to all APIs.
- **App Check (if enabled):** needs Play cert configured.
- **Dynamic Links, FCM, etc.:** each has its own signer registration.

Emulator/local debug masks all of these. **Any signing-gated Google Cloud service accessed by your app must be tested on a Play-signed build once before shipping.** The list: Remote Config, Firebase Auth (via Identity Toolkit), App Check, Dynamic Links, Cloud Messaging, Analytics, Installations. One test matrix: debug (works), ad-hoc/release-key (works/fails), Play (fail if not registered).

### L3: Silent Catches Hide Prod Bugs
```kotlin
// ❌ Anti-pattern (what was there)
override suspend fun fetchAndActivate(): Boolean = try {
    remoteConfig.fetchAndActivate().await()
} catch (e: Exception) {
    false  // Silent — error lost forever
}

// ✅ Pattern (what was added)
override suspend fun fetchAndActivate(): Boolean = try {
    remoteConfig.fetchAndActivate().await()
} catch (e: Exception) {
    lastFetchException = e  // Store for diagnostic
    logger.error(TAG, "…", e)  // Log immediately
    false
}
```

Silent catches are acceptable for fallback scenarios (e.g., "fail gracefully to defaults"). But when the "success" path and the "failure" path both produce visible results (RC value = default vs requested), the fallback itself becomes a bug. **Store the exception and log it;** analytics or crash reporting will surface it on prod.

### L4: Debug Secret vs. Prod Secret Is a Category of Bugs
This class recurs across Google Cloud services. Spotting it:
- Feature works in local builds / emulator → breaks on Play store.
- Error only appears when signing is involved (Play APK).
- Error message includes service names (Identity Toolkit, Remote Config, App Check).
- Root cause is always: "signer not registered" or "API key misconfigured."

Pre-release checklist (post AGP 9):
1. Extract debug `.gradle/` from one build.
2. Extract Play-signed APK from Play Console (or build with Play key locally if you have it).
3. Decompile both (apktool / bundletool).
4. Diff the signing certificates (`META-INF/MANIFEST.MF` signer).
5. Verify the prod signer is registered in:
   - Firebase Console (all apps using Firebase).
   - Google Cloud Console (API key restrictions if using non-Firebase Google APIs).
   - Developer Console (if using Play licensing, in-app billing, etc.).

## Code Changes Summary

**Files modified (7):**
1. `core/remoteconfig/api/RemoteConfigProvider.kt` — added `lastFetchError()` optional method.
2. `core/remoteconfig/impl/FirebaseRemoteConfigProvider.kt` — capture and store fetch exception.
3. `feature/splash/SplashViewModel.kt` — emit `onboarding_rc_resolved` event with diagnostics.
4. `core/auth/impl/AndroidGoogleAuthProvider.kt` — capture and log auth exceptions with error code/message.
5. `core/common/api/AnalyticsEvents.kt` — add `Auth` sealed class with SignInStarted/Success/Failed.
6. `feature/home/MainScreenViewModel.kt` — track idToken nullness explicitly.
7. `androidApp/build.gradle.kts` — version bump to 1.17.4(53).

**Tests:** No test changes (fakes are unaffected; SignInFailed can be logged as enum or string in tests).

**Backward compatibility:** New analytics events are additive; RemoteConfigProvider default method is no-op on non-Android platforms.

## Verification

- **RC Diagnostics:** Deployed 1.17.4(53) → user added Play Signing SHA-1 to Firebase → next user install: `onboarding_rc_resolved.rc_activated=true`, `rc_error=null` → A/B test fires correctly.
- **Auth Diagnostics:** Added event tracking → existing 1.17.4(53) tested on prod device → user added Identity Toolkit API to allowlist → `login_success` event fires → sign-in completes.

Both issues confirmed resolved by user after server-side fixes (no client rebuild required for Auth fix).

## Related / Follow-up

- **Dependency:** Project memory [[reactive-rc-fetch-ab-timeout-2026-06-18]] — full chronology of RC A/B test hypothesis vs. data-driven correction.
- **Pattern:** [[prod-signing-key-rc-auth-diagnostics]] is a permanent reference for future signing/key bugs; add analogous diagnostics to any Google Cloud service integration (App Check, Dynamic Links, etc.) before shipping.
- **Monitoring:** `onboarding_rc_resolved.rc_error` and `auth.login_failed.error_message` are now permanent prod signals; alert on non-null values.
