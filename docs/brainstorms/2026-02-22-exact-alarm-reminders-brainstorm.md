# Brainstorm: Exact Alarm Reminders

**Date:** 2026-02-22
**Status:** Decided
**Feature:** Upgrade checklist reminders to use exact alarms with graceful fallback

---

## What We're Building

Upgrade the current reminder system from **inexact alarms** (`setAndAllowWhileIdle()`) to **exact alarms** (`setExactAndAllowWhileIdle()`) with:

1. **`SCHEDULE_EXACT_ALARM` permission** in AndroidManifest
2. **Instruction Bottom Sheet** shown each time user creates a reminder (if permission is off and not suppressed)
3. **Graceful fallback** to inexact alarms when permission is denied
4. **BOOT_COMPLETED receiver** to reschedule alarms after device reboot
5. **Permission state change receiver** to auto-reschedule when permission is granted

---

## Why This Approach

### Problem

Current `setAndAllowWhileIdle()` may delay reminders by **up to ~10 minutes** on Android 14+ due to Doze mode batching. Users expect reminders at the exact time they set.

### Solution

Use `setExactAndAllowWhileIdle()` when `SCHEDULE_EXACT_ALARM` is granted — fires at the precise moment. Fall back to current behavior when denied.

### Why `SCHEDULE_EXACT_ALARM` (not `USE_EXACT_ALARM`)

| Aspect | `SCHEDULE_EXACT_ALARM` | `USE_EXACT_ALARM` |
|--------|----------------------|-------------------|
| **Protection** | User-granted (Settings toggle) | Auto-granted at install |
| **Google Play** | Safe for any app | **Restricted** — only alarm/calendar apps |
| **Gisti eligible?** | Yes | **No — would be rejected** |
| **Android 14+** | Denied by default (fresh install) | Auto-granted |

Google Play explicitly states that note-taking/checklist apps with reminders are NOT eligible for `USE_EXACT_ALARM`.

---

## Key Decisions

### 1. When to show permission instruction

**Decision:** Show Bottom Sheet **every time** user creates a reminder AND exact alarm permission is off, **unless** user checked "Don't show again".

- Store "don't show again" flag in DataStore preferences
- Always schedule the reminder (exact or inexact) regardless of what user chooses in the sheet

### 2. Instruction UI

**Decision:** Step-by-step Bottom Sheet with:

```
┌───────────────────────────────┐
│  🔔 Enable Precise Reminders  │
│                               │
│  Step 1: Tap "Open Settings"  │
│  Step 2: Find "Alarms &       │
│          reminders"           │
│  Step 3: Toggle ON for Gisti  │
│                               │
│  Without this, reminders may  │
│  be delayed by a few minutes. │
│                               │
│  [ ] Don't show again         │
│                               │
│  [  Open Settings  ]          │
│  [    Skip          ]         │
└───────────────────────────────┘
```

- "Open Settings" → opens `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`
- "Skip" → closes sheet, schedules with inexact alarm

### 3. After returning from Settings

**Decision:** Show Snackbar with result:
- Permission granted → "Precise reminders enabled!" + reschedule all active reminders as exact
- Permission NOT granted → "Reminders may be delayed by a few minutes"

### 4. BOOT_COMPLETED receiver

**Decision:** Yes, add it. All AlarmManager alarms are lost on device reboot. Currently only `GistiApplication.onCreate()` reschedules — but it only runs when user opens the app.

### 5. Permission state change receiver

**Decision:** Add `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` receiver to auto-reschedule all reminders as exact when permission is granted (e.g., user enables it from system Settings without going through the app).

### 6. Fallback behavior

**Decision:** Tiered approach:
```
if canScheduleExactAlarms() → setExactAndAllowWhileIdle()  // exact
else                        → setAndAllowWhileIdle()        // ~10 min window
```

Never block the user from creating reminders. Inexact is always available.

---

## Scope

### In Scope
- [x] Add `SCHEDULE_EXACT_ALARM` permission to manifest
- [x] Add `RECEIVE_BOOT_COMPLETED` permission to manifest
- [x] Update `ReminderScheduler.schedule()` with exact/inexact logic
- [x] Add `BootCompletedReceiver` (reschedule after reboot)
- [x] Add `ExactAlarmPermissionReceiver` (reschedule when permission granted)
- [x] Add instruction Bottom Sheet composable
- [x] Add "don't show again" preference to DataStore
- [x] Add Snackbar feedback after returning from Settings
- [x] Add localization strings

### Out of Scope
- Repeating/recurring reminders (separate feature)
- Settings screen toggle (YAGNI — sheet handles it)
- iOS implementation (not actively released)

---

## Open Questions

None — all decisions resolved.

---

## Technical Notes

### Permission check
```kotlin
fun canScheduleExactAlarms(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
    } else {
        true // Below API 31, exact alarms don't need permission
    }
}
```

### Open exact alarm settings
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
}
```

### New manifest entries
```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<receiver android:name=".notification.BootCompletedReceiver" android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>

<receiver android:name=".notification.ExactAlarmPermissionReceiver" android:exported="false">
    <intent-filter>
        <action android:name="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" />
    </intent-filter>
</receiver>
```

---

## Next Step

Run `/workflows:plan` to create implementation plan.
