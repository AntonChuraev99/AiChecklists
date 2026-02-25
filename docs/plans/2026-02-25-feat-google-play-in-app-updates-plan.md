---
title: "feat: Add Google Play In-App Updates with Analytics"
type: feat
date: 2026-02-25
---

# Google Play In-App Updates with Analytics

## Overview

Add Google Play In-App Updates API to prompt users to update the app. Two modes:

- **Immediate** (blocking) — forced for versions below `min_app_version` (Remote Config)
- **Flexible** (background) — suggested when an update is available but not critical

This solves the problem of users staying on old buggy versions (e.g., v1.9.1 with `MOCK_PRODUCT` crash).

## Problem Statement

Users on old versions (e.g., 1.9.1) continue hitting bugs that are already fixed in newer releases. Analytics shows `purchase_failed` with `mock_premium_monthly` — a bug fixed in 1.9.2. There is no mechanism to nudge or force users to update.

The `MIN_APP_VERSION` Remote Config key is **already declared** but never consumed.

## Proposed Solution

Follow the existing `InAppReviewLauncher` expect/actual composable pattern:

1. **`InAppUpdateLauncher`** — expect/actual composable in `composeApp`
2. **Android actual** — wraps `AppUpdateManager` from `play-app-update-ktx`
3. **iOS actual** — no-op stub
4. **Remote Config** — `MIN_APP_VERSION` controls force update threshold
5. **Analytics** — focused events for every update lifecycle step

## Technical Approach

### Architecture

```
composeApp/
  src/commonMain/.../update/
    InAppUpdateLauncher.kt          # expect composable
    InAppUpdateState.kt             # sealed class for update states
  src/androidMain/.../update/
    InAppUpdateLauncher.android.kt  # actual — AppUpdateManager logic
  src/iosMain/.../update/
    InAppUpdateLauncher.ios.kt      # actual — no-op stub
```

### Phase 1: Dependencies & Config

**`gradle/libs.versions.toml`** — add:
```toml
[versions]
playAppUpdate = "2.1.0"

[libraries]
play-app-update = { module = "com.google.android.play:app-update", version.ref = "playAppUpdate" }
play-app-update-ktx = { module = "com.google.android.play:app-update-ktx", version.ref = "playAppUpdate" }
```

**`composeApp/build.gradle.kts`** — in `androidMain.dependencies`:
```kotlin
implementation(libs.play.app.update.ktx)
```

### Phase 2: expect/actual InAppUpdateLauncher

#### Common (expect)

**`composeApp/src/commonMain/.../update/InAppUpdateLauncher.kt`**:
```kotlin
@Composable
expect fun InAppUpdateLauncher(
    minRequiredVersion: String,
    currentVersion: String,
    analyticsTracker: AnalyticsTracker,
)
```

#### Android (actual)

**`composeApp/src/androidMain/.../update/InAppUpdateLauncher.android.kt`**:

Logic:
1. `AppUpdateManagerFactory.create(context)`
2. `appUpdateManager.appUpdateInfo` → check `updateAvailability()`
3. Compare `currentVersion` with `minRequiredVersion` (semver):
   - `currentVersion < minRequiredVersion` → **Immediate** update (blocking fullscreen)
   - Otherwise → **Flexible** update (download in background)
4. Handle `ActivityResult` for update flow completion
5. In `onResume`: if flexible update downloaded → call `completeUpdate()`

Key implementation details:
- Use `registerForActivityResult(IntentSenderRequest)` via `rememberLauncherForActivityResult`
- For **Immediate** mode: `AppUpdateType.IMMEDIATE` — Google Play handles the UI
- For **Flexible** mode: `AppUpdateType.FLEXIBLE` — register `InstallStateUpdatedListener`
- On `InstallStatus.DOWNLOADED` → show Snackbar "Update ready" → `completeUpdate()`

#### iOS (actual)

**`composeApp/src/iosMain/.../update/InAppUpdateLauncher.ios.kt`**:
```kotlin
@Composable
actual fun InAppUpdateLauncher(
    minRequiredVersion: String,
    currentVersion: String,
    analyticsTracker: AnalyticsTracker,
) {
    // No-op: iOS uses App Store auto-updates
}
```

### Phase 3: Integration in App.kt

**`composeApp/src/commonMain/.../App.kt`** — add after `InAppReviewLauncher`:
```kotlin
// In-App Update — side-effect composable, no UI
val remoteConfig: RemoteConfigProvider = koinInject()
val analyticsTracker: AnalyticsTracker = koinInject()

InAppUpdateLauncher(
    minRequiredVersion = remoteConfig.getString(
        RemoteConfigKeys.MIN_APP_VERSION,
        RemoteConfigDefaults.MIN_APP_VERSION
    ),
    currentVersion = AppBuildConfig.versionName,
    analyticsTracker = analyticsTracker,
)
```

**Note:** `AppBuildConfig.versionName` may need to be added as a new expect/actual property. Currently only `isDebug` exists. Alternative: pass version from `BuildConfig.VERSION_NAME` via Koin.

### Phase 4: Analytics Events

| Event | When | Params |
|-------|------|--------|
| `in_app_update_check` | Update check starts | `current_version` |
| `in_app_update_available` | Update found | `available_version`, `update_type` (immediate/flexible), `staleness_days` |
| `in_app_update_not_available` | No update found | `current_version` |
| `in_app_update_started` | User accepted / flow started | `update_type` |
| `in_app_update_downloaded` | Flexible download complete | — |
| `in_app_update_completed` | `completeUpdate()` called | `update_type` |
| `in_app_update_failed` | Error occurred | `error_code`, `update_type` |
| `in_app_update_dismissed` | User declined flexible update | — |

Total: **8 events**. Each answers a specific question:
- How many users have updates available? (`available` vs `not_available`)
- What % accept updates? (`started` / `available`)
- What % complete? (`completed` / `started`)
- What % dismiss? (`dismissed` / `available`)
- What errors occur? (`failed` with `error_code`)

### Phase 5: Version Comparison Utility

Simple semver comparison — no library needed:

```kotlin
fun isVersionBelow(current: String, minimum: String): Boolean {
    val c = current.split(".").map { it.toIntOrNull() ?: 0 }
    val m = minimum.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(c.size, m.size)) {
        val cv = c.getOrElse(i) { 0 }
        val mv = m.getOrElse(i) { 0 }
        if (cv < mv) return true
        if (cv > mv) return false
    }
    return false
}
```

## Remote Config Setup

In Firebase Console, set `min_app_version`:
- Default: `"1.0.0"` (no forced updates)
- To force update for v1.9.1 users: set to `"1.9.2"`
- To force everyone to latest: set to current version

## Testing Strategy

### Internal Testing Track (BEFORE production)

1. Upload current APK to Internal Testing Track in Google Play Console
2. Upload a newer version (versionCode + 1)
3. Install the older version on a test device
4. Verify:
   - Flexible update flow shows download prompt
   - Setting `min_app_version` above installed version triggers Immediate flow
   - All analytics events fire correctly
   - Snackbar "Update ready" appears for flexible downloads
   - `completeUpdate()` restarts the app

### Edge Cases to Test

- No internet → update check silently fails, no crash
- Update already downloaded → `onResume` triggers install
- User dismisses flexible update → `in_app_update_dismissed` fires
- Google Play not available (Huawei, emulator) → silent no-op
- `min_app_version` not set in Remote Config → uses default "1.0.0" (no force)

## Acceptance Criteria

- [ ] `play-app-update-ktx` dependency added to `libs.versions.toml` and `build.gradle.kts`
- [ ] `InAppUpdateLauncher` expect/actual created (Android + iOS stub)
- [ ] Immediate mode triggers when `currentVersion < min_app_version`
- [ ] Flexible mode triggers when update available but not forced
- [ ] All 8 analytics events fire correctly (verified in Amplitude debug)
- [ ] Flexible download shows "Update ready" Snackbar with install action
- [ ] No crash when Google Play is unavailable
- [ ] Tested via Internal Testing Track before production release
- [ ] iOS stub compiles without errors

## Files to Create/Modify

| File | Action | Description |
|------|--------|-------------|
| `gradle/libs.versions.toml` | Modify | Add `playAppUpdate` version and library entries |
| `composeApp/build.gradle.kts` | Modify | Add `play-app-update-ktx` to androidMain deps |
| `composeApp/src/commonMain/.../update/InAppUpdateLauncher.kt` | Create | expect composable |
| `composeApp/src/androidMain/.../update/InAppUpdateLauncher.android.kt` | Create | actual — AppUpdateManager logic |
| `composeApp/src/iosMain/.../update/InAppUpdateLauncher.ios.kt` | Create | actual — no-op |
| `composeApp/src/commonMain/.../App.kt` | Modify | Add InAppUpdateLauncher call |
| `core/remoteconfig/api/.../RemoteConfigKeys.kt` | No change | `MIN_APP_VERSION` already declared |

## Dependencies & Risks

| Risk | Mitigation |
|------|-----------|
| Immediate update blocks app usage | Only trigger via Remote Config — default is "1.0.0" (no force) |
| Infinite update loop if version mismatch | Compare with `availableVersionCode`, not just Remote Config |
| Google Play not available on device | Wrap in try/catch, log `in_app_update_failed` |
| User stuck on update screen | Immediate flow has built-in "back" after timeout |
| Analytics noise from repeated checks | Check once per app cold start only |

## References

- [Google Play In-App Updates documentation](https://developer.android.com/guide/playcore/in-app-updates)
- Existing pattern: `composeApp/src/commonMain/.../csat/InAppReviewLauncher.kt`
- Remote Config key: `core/remoteconfig/api/.../RemoteConfigKeys.kt:11` (`MIN_APP_VERSION`)
- Analytics interface: `core/common/api/.../AnalyticsTracker.kt`
- Trigger: `purchase_failed` with `mock_premium_monthly` on v1.9.1 users (analytics screenshot 2026-02-25)
