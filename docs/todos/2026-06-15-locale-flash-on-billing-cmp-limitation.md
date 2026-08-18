---
status: resolved
blocking_reason: "RESOLVED 2026-06-16 on Android 33+ via LocaleManager.setApplicationLocales (persistAppLocale expect/actual). Android <33 keeps the onResume fallback with a residual one-frame flash, accepted as won't-fix — not worth an AppCompatActivity + Theme.AppCompat migration (Nav3/edge-to-edge regression risk on 100% of devices) for a shrinking minority. Explicit user decision 2026-06-16."
resume_trigger: "only if the <API 33 residual flash later becomes worth an AppCompatActivity per-app-locale migration, OR Compose Multiplatform exposes a public locale CompositionLocal (GitHub compose-multiplatform#4197 / YouTrack CMP-8376)"
keywords: [locale, language, paywall, billing, stringResource, Locale.setDefault, AppLocaleEnvironment, CMP, AppCompatDelegate, setApplicationLocales, LocaleManager, persistAppLocale]
---

# Locale ru→en flash on returning from Google Play Billing sheet

## ✅ Resolved on Android 33+ (2026-06-16)

The visible `ru → en` flash is **gone on Android 33+** (Android 13+, ~majority of devices). Mechanism:
- New `persistAppLocale(tag)` expect/actual (`core/designsystem/.../theme/AppLocale.*`). Android 33+ actual calls
  `LocaleManager.setApplicationLocales(LocaleList.forLanguageTags(tag))` (null → empty list = follow device).
  The OS now owns the per-app locale, keeps `Locale.getDefault()` correct across the entire billing round-trip,
  so the process locale is never `ru` when our Activity resumes → no flash. Below API 33 it is a no-op and the
  existing `MainActivity.onResume` re-assert remains the fallback (the residual one-frame flash there is **accepted**).
- Wired in `App.kt` from a **raw `Flow.collect`** (NOT `collectAsStateWithLifecycle(initialValue=…)`): the placeholder
  caused an infinite Activity-recreate loop (`setApplicationLocales` recreates the Activity; placeholder `[]` ↔ real
  `[en]` flipped forever). Raw collect emits the real value first → converges after ≤1 migration recreate.
- **Verified on Pixel 9** (device locale `ru-KZ`): system app-locale `[]` → `[en]` after launch; `App composable start`
  logged exactly 2× then stable (no loop); MainActivity stays ResumedActivity.
- Cost accepted: one migration recreate on the first launch after this update for users with a non-System language,
  and a recreate on each explicit language change in Settings (standard Android per-app-locale behaviour).

Solution doc: `docs/solutions/runtime-errors/google-play-billing-locale-reset-cmp-2026-06-15.md`.
Loop pitfall memory: `setapplicationlocales-recreate-loop-placeholder-2026-06-16.md`.

### Still won't-fix (by decision)
Android **< 33**: no system per-app-locale API, so the onResume re-assert remains and a residual one-frame flash on
sheet close can still occur. Not worth migrating `MainActivity` to `AppCompatActivity` + swapping the app theme to a
`Theme.AppCompat` descendant (Nav3 / edge-to-edge regression risk on every device) for a shrinking OS-version minority.

---

## Original report (historical)

# Locale ru→en flash on returning from Google Play Billing sheet

## What shipped (the fix in this session)
The **main** bug is fixed: setting language to English, opening the paywall, making/cancelling a purchase, and closing the Google Play sheet **no longer leaves the UI stuck in Russian until app restart**. The language reliably returns to the chosen one.

Mechanism (commit on branch `worktree-stateless-dazzling-twilight`):
- `AppLocaleEnvironment` (`core/designsystem/.../theme/AppLocale.kt`) gained a `localeReassertEpoch` + `reassertAppLocale()`; reading the epoch + `key(customAppLocale, epoch)` lets a re-assert recompose the subtree → `provides()` re-applies `Locale.setDefault`.
- `expect fun isAppLocaleOverrideStale()` / `reapplyAppLocaleNow()` (android actual real; ios/wasmJs no-op).
- `MainActivity.onResume` calls `reapplyAppLocaleNow()` + `reassertAppLocale()` when the override is stale.

## What is still deferred — the ~1s ru flash
When the Google Play Billing sheet is shown it resets the **process-global** `Locale.getDefault()` to the device locale (`ru_KZ`) for the entire time the sheet is up (~2s, confirmed on-device: `onWindowFocusChanged hasFocus=false locale=ru_KZ` immediately on open). While the sheet closes, a frame can paint in `ru` **before** `onResume` hands control back and we restore `en`. So a brief language flash remains.

### Why it can't be fully fixed at the current layer (root cause, verified)
- On Android, `stringResource` → `rememberResourceEnvironment()` → `LocalComposeEnvironment` (**internal** in CMP 1.11.0, cannot be provided) → `androidx.compose.ui.text.intl.Locale.current`, which reads the **global** `Locale` / `LocaleList.getDefault()`, NOT `LocalConfiguration` ([Google issue 240191036](https://issuetracker.google.com/issues/240191036)). On-device proof: `composeLocale` (what `stringResource` reads) only became `en` after `provides → Locale.setDefault(en)`; providing `LocalConfiguration` alone did nothing.
- So the chosen language can only be driven through the global `Locale`, which external Activities (billing) freely reset. Reactive re-assertion (onResume) is inherently one+ frame late during the sheet's close animation.

## Resume options (pick when revisited)
1. **AppCompat per-app locales** — migrate to `AppCompatDelegate.setApplicationLocales(LocaleListCompat)` (Android 13+ native, `autoStoreLocales` backport < 13). This is the platform-blessed, persistent, per-app mechanism not tied to the mutable JVM global; survives external resets. Cost: AppCompat dependency + reworking the `AppLocaleEnvironment` plumbing; verify it drives CMP `stringResource` (still resolves via `Locale.current` → may still need the global synced).
2. **Wait for a public CMP locale API** — `LocalComposeEnvironment` / a `ResourceEnvironment` override becoming public (tracked: GitHub #4197, YouTrack CMP-8376). Then provide locale via CompositionLocal, fully decoupled from the global.
3. **Mask the flash** — hold a brief overlay/freeze on return from an IAP flow until the locale is re-asserted. Cosmetic band-aid, not a real fix.

## Validation done
- Reproduced on physical Pixel 9 with diagnostic logging (now removed).
- Confirmed: main "stuck until restart" bug gone; only the transient flash remains.
- Rotation (full recreate) never reproduced — confirms it's the pause/resume + global-reset path, not cold composition.
