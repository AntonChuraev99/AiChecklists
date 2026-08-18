---
title: Request POST_NOTIFICATIONS Permission Before Setting Reminder
date: 2026-02-22
category: feature
tags:
  - android
  - notifications
  - permissions
  - reminders
  - android-13
  - runtime-permission
module: feature/home, feature/checklist, composeApp/androidMain
symptoms:
  - Reminder set successfully but notification never appears on Android 13+
  - User sees no error or warning about missing permission
  - AlarmManager fires but NotificationManager.notify() silently fails
severity: high
status: implemented
---

# Request POST_NOTIFICATIONS Permission Before Setting Reminder

## Problem Statement

On Android 13+ (API 33), the `POST_NOTIFICATIONS` permission became a **runtime permission** (like camera or microphone). The app declared it in `AndroidManifest.xml` but never requested it at runtime. As a result, when a reminder alarm fired, `NotificationManager.notify()` silently dropped the notification — the user never received it.

### Root Cause

- `POST_NOTIFICATIONS` was declared in manifest but **not requested via `ActivityResultContracts.RequestPermission()`**
- On Android 12 and below, no runtime request is needed — notifications work by default
- On Android 13+, the system requires explicit user consent before any notification can be posted
- The `ReminderReceiver.showNotification()` call silently fails without the permission — no crash, no error log

### Impact

- **All Android 13+ users** who set reminders never received notifications
- Users had no indication that notifications were disabled
- The reminder was "set" (alarm scheduled) but effectively useless

## Solution

### 1. Added `hasNotificationPermission()` to Scheduler Interface

```kotlin
// feature/checklist/.../ChecklistReminderScheduler.kt
interface ChecklistReminderScheduler {
    // ... existing methods ...

    /** Returns true if the app can post notifications (always true below API 33). */
    fun hasNotificationPermission(): Boolean = true
}
```

### 2. Android Implementation

```kotlin
// composeApp/androidMain/.../ReminderScheduler.kt
override fun hasNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // Below API 33, no runtime permission needed
    }
}
```

### 3. Expect/Actual Permission Requester (KMP)

Since the screen is in `commonMain` but `ActivityResultContracts` is Android-only, we use expect/actual:

```kotlin
// commonMain — NotificationPermission.kt
@Composable
expect fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit

// androidMain — NotificationPermission.android.kt
@Composable
actual fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }

    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onResult(true)
        }
    }
}

// iosMain — NotificationPermission.ios.kt
@Composable
actual fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit = { onResult(true) }
```

### 4. ViewModel Flow

```kotlin
// ChecklistDetailViewModel.kt
private fun handleReminderClick() {
    viewModelScope.launch {
        // ... existing premium/limit check ...

        if (!reminderScheduler.hasNotificationPermission()) {
            updateContentState { it.copy(showNotificationPermissionSheet = true) }
        } else {
            updateContentState { it.copy(showReminderSheet = true) }
        }
    }
}
```

Key design decision: **user is never blocked**. Whether they grant, deny, or skip — the reminder sheet always opens next. They can still set reminders; notifications just won't appear without permission.

### 5. UI: Notification Permission Bottom Sheet

A friendly bottom sheet explains the value of notifications before triggering the system dialog:

- Large bell icon in a circular container
- Title: "Enable Notifications"
- Description explaining the benefit
- Three feature bullets with icons:
  - Get notified at the exact time you set
  - See your checklist name and remaining items
  - Tap the notification to jump right in
- "Enable Notifications" primary button → triggers system permission dialog
- "Not Now" text button → skips to reminder sheet

## User Flow

```
Tap bell icon
  → hasNotificationPermission()?
    → YES → Show "Set Reminder" sheet (normal flow)
    → NO  → Show "Enable Notifications" sheet
              → "Enable Notifications" → System dialog
                → Allow/Don't allow → Show "Set Reminder" sheet
              → "Not Now" → Show "Set Reminder" sheet
```

On subsequent taps (permission already granted): goes directly to "Set Reminder".

## Files Changed

| File | Change |
|------|--------|
| `ChecklistReminderScheduler.kt` | Added `hasNotificationPermission()` interface method |
| `ReminderScheduler.kt` | Android implementation with `ContextCompat.checkSelfPermission` |
| `NotificationPermission.kt` | expect composable for permission request |
| `NotificationPermission.android.kt` | actual — `ActivityResultContracts.RequestPermission` |
| `NotificationPermission.ios.kt` | actual — no-op (always true) |
| `ChecklistDetailScreenContract.kt` | Added `showNotificationPermissionSheet` state + 3 intents |
| `ChecklistDetailViewModel.kt` | Permission check in `handleReminderClick()`, handlers |
| `ChecklistDetailScreen.kt` | `NotificationPermissionSheet` composable + `NotificationFeatureRow` |
| `strings.xml` | 7 new string resources |

## Prevention Strategies

1. **Runtime permission checklist**: Any feature that uses `NotificationManager`, camera, location, etc. must request permission before use
2. **Test on Android 13+ emulator**: Always verify notification features on API 33+ where permission is required
3. **ADB testing shortcut**: Use `adb shell pm revoke <package> android.permission.POST_NOTIFICATIONS` to simulate denied state

## Testing

### Manual Test Steps

1. Revoke permission: `adb shell pm revoke com.antonchuraev.aichecklists android.permission.POST_NOTIFICATIONS`
2. Open a checklist → tap bell icon
3. Verify "Enable Notifications" sheet appears with feature bullets
4. Tap "Enable Notifications" → system dialog should appear
5. Tap "Allow" → "Set Reminder" sheet should open
6. Set a reminder → verify notification arrives

### Edge Cases

- **Permission already granted**: Bell → directly opens "Set Reminder" (no permission sheet)
- **User taps "Not Now"**: Opens "Set Reminder" anyway (non-blocking)
- **User denies in system dialog**: Opens "Set Reminder" anyway (reminder still scheduled, just no notification)
- **Android 12 and below**: No permission sheet shown (not needed)

## Related Documentation

- [Exact Alarm Reminders Upgrade](exact-alarm-reminders-upgrade.md) — companion permission flow for `SCHEDULE_EXACT_ALARM`
- [MVI Pattern](../architecture/mvi-pattern.md) — state management pattern used
- [Design System Patterns](../ui/design-system-patterns.md) — bottom sheet UI conventions
