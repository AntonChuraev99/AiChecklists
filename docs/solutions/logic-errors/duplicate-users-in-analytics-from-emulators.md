---
title: "Duplicate Users in Analytics from Emulators"
category: logic-errors
tags: [amplitude, firebase, analytics, auto-backup, datastore, device-id, log-masking]
module: user, analytics
symptom: "Multiple user IDs from same emulator in Amplitude dashboard"
root_cause: "No debug/release isolation in Amplitude + wrong Auto Backup path for DataStore"
date: 2026-02-22
---

# Duplicate Users in Analytics from Emulators

## Problem

Duplicate users appearing in Amplitude analytics from Android emulators and after app reinstalls. Each reinstall generated a new device UUID, causing duplicate user registrations in the analytics dashboard.

## Symptoms

- Multiple user IDs in Amplitude originating from the same emulator
- Device ID changes after every app reinstall
- Debug build events polluting production analytics data
- Full UUIDs exposed in plaintext in Logcat output

## Root Causes

Four separate issues contributed to the problem:

1. **No debug/release isolation in Amplitude** -- The Amplitude SDK had no `optOut` configuration for debug builds, unlike Firebase which has built-in debug detection. All debug events went directly to the production Amplitude project.

2. **Wrong Auto Backup path for DataStore** -- Android Auto Backup rules pointed to `datastore/` instead of `user/`. The DataStore name `"user/datastore"` creates its file at `files/user/datastore.preferences_pb`, NOT at `files/datastore/`. This meant the device ID was never backed up and restored, so every reinstall generated a new UUID.

3. **Full IDs logged in plaintext** -- `deviceId` and `userId` were logged as complete UUIDs to Logcat, creating a security concern.

4. **Redundant DataStore reads in getUserData()** -- `getUserData()` made 4 sequential DataStore reads instead of using the existing `StateFlow`, adding unnecessary latency on every call.

## Solution

The fix was applied across 4 commits, each addressing one root cause.

### Fix 1 -- Amplitude sandbox via separate API key

Replaced the `optOut` approach with per-build-type Amplitude API keys so debug events go to a completely separate Amplitude project.

**Files changed:**
- `composeApp/build.gradle.kts`
- `composeApp/src/androidMain/.../Analytics.kt`

**What changed:**

In `build.gradle.kts`, added per-build-type Amplitude keys:

```kotlin
buildTypes {
    debug {
        buildConfigField("String", "AMPLITUDE_API_KEY", "\"${findProperty("AMPLITUDE_DEBUG_KEY") ?: ""}\"")
    }
    release {
        buildConfigField("String", "AMPLITUDE_API_KEY", "\"${findProperty("AMPLITUDE_KEY") ?: ""}\"")
    }
}
```

In `Analytics.kt`, removed the `optOut` flag. Debug events now route to a separate Amplitude project instead of being silenced:

```kotlin
// Before (broken): debug events silenced but still generated device IDs
Amplitude(
    Configuration(
        apiKey = AMPLITUDE_API_KEY,
        optOut = AppBuildConfig.isDebug  // REMOVED
    )
)

// After: debug events go to separate project
Amplitude(
    Configuration(
        apiKey = BuildConfig.AMPLITUDE_API_KEY  // different key per build type
    )
)
```

### Fix 2 -- Auto Backup with correct DataStore path

The DataStore name `"user/datastore"` creates the file at `files/user/datastore.preferences_pb`. The backup rules must use `path="user/"` to include this directory.

**Files changed:**
- `composeApp/src/androidMain/res/xml/backup_rules.xml` (new, API 31+)
- `composeApp/src/androidMain/res/xml/backup_rules_legacy.xml` (new, API 23-30)
- `composeApp/src/androidMain/AndroidManifest.xml`

**backup_rules.xml** (API 31+):

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <include domain="file" path="user/" />
    </cloud-backup>
    <device-transfer>
        <include domain="file" path="user/" />
    </device-transfer>
</data-extraction-rules>
```

**backup_rules_legacy.xml** (API 23-30):

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <include domain="file" path="user/" />
</full-backup-content>
```

**AndroidManifest.xml** additions:

```xml
<application
    android:dataExtractionRules="@xml/backup_rules"
    android:fullBackupContent="@xml/backup_rules_legacy"
    ... >
```

### Fix 3 -- Log masking with `.take(8)`

All log statements now truncate device IDs and user IDs to the first 8 characters.

**Files changed:**
- `feature/user/src/commonMain/.../UserDataRepositoryImpl.kt`
- `feature/user/src/commonMain/.../UserApiService.kt`
- `feature/analyze/src/commonMain/.../FirebaseAiServiceImpl.kt`

**Pattern applied:**

```kotlin
// Before: full UUID in logs
logger.d { "Device registered: deviceId=$deviceId, userId=$userId" }

// After: truncated to 8 chars
logger.d { "Device registered: deviceId=${deviceId.take(8)}..., userId=${userId.take(8)}..." }
```

Example output: `deviceId=f310b4f3...` instead of the full UUID.

### Fix 4 -- getUserData() StateFlow optimization

Replaced 4 sequential DataStore reads with a fast path using the existing `StateFlow`.

**File changed:**
- `feature/user/src/commonMain/.../UserDataRepositoryImpl.kt`

```kotlin
// Before: 4 sequential DataStore reads every time
suspend fun getUserData(): UserData {
    val deviceId = dataStore.read(DEVICE_ID_KEY)
    val userId = dataStore.read(USER_ID_KEY)
    val isPremium = dataStore.read(IS_PREMIUM_KEY)
    val credits = dataStore.read(CREDITS_KEY)
    return UserData(deviceId, userId, isPremium, credits)
}

// After: fast path from StateFlow, cold start fallback with timeout
suspend fun getUserData(): UserData {
    // Fast path: return immediately if userId is already loaded
    val cached = userDataFlow.value
    if (cached.userId.isNotBlank()) return cached

    // Cold start: wait up to 100ms for first emission with userId
    return withTimeoutOrNull(100) {
        userDataFlow.first { it.userId.isNotBlank() }
    } ?: userDataFlow.value
}
```

## Verification

Tested on a physical Pixel 9 device:

1. Launch app, note `deviceId=2c9ff524...`
2. Force backup: `adb shell bmgr backupnow com.antonchuraev.aichecklists` -- Success
3. Uninstall: `adb shell pm uninstall com.antonchuraev.aichecklists`
4. Reinstall the app
5. Restore: `adb shell bmgr restore com.antonchuraev.aichecklists`
6. Launch app, confirm same `deviceId=2c9ff524...`

## Tests

11 unit tests covering the changes:

- **5 tests in `DeviceIdProviderTest`** -- device ID generation and persistence
- **6 tests in `UserDataRepositoryImplTest`** -- `getUserData()` fast path, cold start fallback, and timeout behavior

## Key Gotcha

DataStore path in backup rules **must** match the actual file path created by `createDataStore(name)`.

| DataStore name | Actual file path | Backup rule `path=` |
|----------------|-----------------|---------------------|
| `"user/datastore"` | `files/user/datastore.preferences_pb` | `user/` |
| `"datastore"` | `files/datastore.preferences_pb` | `.` or `datastore.preferences_pb` |

To verify the actual path on a device:

```bash
adb shell run-as com.antonchuraev.aichecklists find . -name "*.preferences_pb"
# Output: ./files/user/datastore.preferences_pb
```

## Prevention

- **New analytics SDKs**: Always configure debug/release isolation. Use separate API keys or projects for debug builds, not just `optOut` flags.
- **Auto Backup rules**: Verify actual file paths with `adb shell run-as <pkg> find . -name "*.preferences_pb"` before writing backup XML.
- **Logging IDs**: Use the `.take(8)` pattern for any identifier logged to Logcat. Never log full UUIDs, tokens, or keys.
- **DataStore reads**: Prefer `StateFlow.value` over repeated `dataStore.read()` calls when the data is already being observed.
