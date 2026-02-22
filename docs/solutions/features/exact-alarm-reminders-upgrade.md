---
title: Upgrade Checklist Reminders to Exact Alarms with Permission Flow
date: 2026-02-22
category: feature
tags:
  - android
  - alarms
  - notifications
  - permissions
  - reminders
module: feature/home, composeApp/androidMain
symptoms:
  - Reminders delivered 5-10 minutes late on Android 14+
  - Inexact alarm batching due to Doze mode
  - Users expecting precise reminder delivery
severity: medium
status: implemented
---

# Exact Alarm Reminders with Permission Flow

## Problem Statement

Android's `setAndAllowWhileIdle()` method (previously used for checklist reminders) batches alarms into inexact time windows on Android 12+, with delays up to ~10 minutes on Android 14+ due to Doze mode optimization. Users expect reminders to fire at the exact requested time, not minutes later.

### Root Cause

- **Doze mode**: Power-saving feature groups background alarms into batches
- **AlarmManager batching**: `setAndAllowWhileIdle()` is inherently inexact; AlarmManager groups similar requests
- **Android 14+ stricter batching**: System more aggressive about grouping alarms

### Why Not `USE_EXACT_ALARM`?

Google Play restricts `USE_EXACT_ALARM` permission to:
- Alarm clock apps
- Calendar apps
- Calling apps
- Messaging apps
- Timer apps
- System apps

**Checklist apps are NOT in this list.** Using this permission would result in Google Play rejection. The solution instead uses `SCHEDULE_EXACT_ALARM` (Android 12+), which has broader availability.

## Solution Overview

Upgrade reminders to use exact alarms (`setExactAndAllowWhileIdle()`) when the `SCHEDULE_EXACT_ALARM` permission is granted, with graceful fallback to inexact alarms otherwise. Guide users through a permission grant flow with:

1. **Instruction bottom sheet** explaining why precise reminders help
2. **"Don't show again" preference** stored in DataStore
3. **Automatic rescheduling** when permission state changes
4. **Lifecycle handling** for device reboots (alarms lost on power off)
5. **Snackbar feedback** when permission is granted/denied

## Implementation Details

### 1. AndroidManifest.xml

Add permissions and broadcast receivers:

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Reminder Notification Receiver -->
<receiver
    android:name="com.antonchuraev.homesearchchecklist.notification.ReminderReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.antonchuraev.aichecklists.ACTION_REMINDER_FIRE" />
    </intent-filter>
</receiver>

<!-- Boot Completed Receiver: reschedule reminders after device reboot -->
<receiver
    android:name="com.antonchuraev.homesearchchecklist.notification.BootCompletedReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>

<!-- Exact Alarm Permission Receiver: reschedule as exact when permission granted -->
<receiver
    android:name="com.antonchuraev.homesearchchecklist.notification.ExactAlarmPermissionReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" />
    </intent-filter>
</receiver>
```

**Why these receivers?**
- **ReminderReceiver**: Fires when alarm triggers (existing)
- **BootCompletedReceiver**: Alarms are cleared when device powers off; reschedule on boot
- **ExactAlarmPermissionReceiver**: Reschedules all alarms when permission state changes (e.g., user grants it), upgrading inexact to exact

### 2. ReminderScheduler (Android Implementation)

Path: `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/notification/ReminderScheduler.kt`

```kotlin
class ReminderScheduler(
    private val context: Context,
    private val repository: ChecklistRepository
) : ChecklistReminderScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(checklistId: Long, triggerAtMillis: Long) {
        val pendingIntent = createPendingIntent(checklistId)

        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    override fun cancel(checklistId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_CHECKLIST_ID, checklistId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            checklistIdToRequestCode(checklistId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    override suspend fun rescheduleAllActive() {
        val reminders = repository.getActiveReminders()
        reminders.forEach { reminder ->
            schedule(reminder.id, reminder.reminderAt)
        }
    }

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Below API 31, exact alarms don't need permission
        }
    }

    override fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    // ... rest of implementation
}
```

**Key behaviors:**
- `canScheduleExactAlarms()`: Returns true if `SCHEDULE_EXACT_ALARM` is granted (API 31+), false otherwise
- `schedule()`: Chooses exact or inexact based on permission state
- `rescheduleAllActive()`: Upgrades all pending alarms to exact when permission is granted

### 3. BootCompletedReceiver

Path: `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/notification/BootCompletedReceiver.kt`

```kotlin
/**
 * Reschedules all active reminders after device reboot.
 * AlarmManager alarms are lost when the device powers off.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler: ChecklistReminderScheduler =
                    GlobalContext.getOrNull()?.get() ?: return@launch
                scheduler.rescheduleAllActive()
            } catch (_: Exception) {
                // Non-critical — reminders will be rescheduled on next app launch
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

**Why `goAsync()`?**
BroadcastReceiver has ~10 seconds to complete before the system kills it. `goAsync()` returns a PendingResult that stays active until `finish()` is called, allowing async work (rescheduling reminders from DB) without timeout.

### 4. ExactAlarmPermissionReceiver

Path: `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/notification/ExactAlarmPermissionReceiver.kt`

```kotlin
/**
 * Reschedules all active reminders when the exact alarm permission state changes.
 * When the user grants SCHEDULE_EXACT_ALARM, this upgrades all pending alarms
 * from inexact to exact. When revoked, they continue as inexact on next schedule.
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler: ChecklistReminderScheduler =
                    GlobalContext.getOrNull()?.get() ?: return@launch
                scheduler.rescheduleAllActive()
            } catch (_: Exception) {
                // Non-critical
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

**Behavior:**
- Fires whenever permission state changes (granted or revoked)
- Reschedules all active reminders with new permission state
- If granted: upgrades to exact; if revoked: continues as inexact

### 5. UI: Exact Alarm Permission Bottom Sheet

Path: `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/detail/ChecklistDetailScreen.kt`

The detail screen shows a bottom sheet when a reminder is saved but the exact alarm permission is not granted:

```kotlin
// Show sheet if exact alarm permission not granted and user hasn't suppressed it
if (screenState.showExactAlarmSheet) {
    ModalBottomSheet(
        onDismissRequest = { sendIntent(ChecklistDetailIntent.OnDismissExactAlarmSheet) },
        sheetState = exactAlarmSheetState,
        modifier = Modifier.navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingXl)
                .verticalScroll(rememberScrollState())
        ) {
            // Title with icon
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

            Text(
                text = stringResource(Res.string.reminder_exact_alarm_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

            Text(
                text = stringResource(Res.string.reminder_exact_alarm_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            // Step 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                verticalAlignment = Alignment.Top
            ) {
                Text("1", style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.reminder_exact_alarm_step1))
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

            // Step 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                verticalAlignment = Alignment.Top
            ) {
                Text("2", style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.reminder_exact_alarm_step2))
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

            // Step 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                verticalAlignment = Alignment.Top
            ) {
                Text("3", style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.reminder_exact_alarm_step3))
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            // "Don't show again" checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        sendIntent(
                            ChecklistDetailIntent.OnExactAlarmDontShowChanged(
                                !screenState.exactAlarmDontShowAgain
                            )
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = screenState.exactAlarmDontShowAgain,
                    onCheckedChange = {
                        sendIntent(ChecklistDetailIntent.OnExactAlarmDontShowChanged(it))
                    }
                )
                Text(stringResource(Res.string.reminder_exact_alarm_dont_show))
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            // Button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
            ) {
                AppButtonSecondary(
                    text = stringResource(Res.string.reminder_exact_alarm_skip),
                    onClick = { sendIntent(ChecklistDetailIntent.OnExactAlarmSkip) },
                    modifier = Modifier.weight(1f)
                )
                AppButton(
                    text = stringResource(Res.string.reminder_exact_alarm_open_settings),
                    onClick = { sendIntent(ChecklistDetailIntent.OnExactAlarmOpenSettings) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
        }
    }
}
```

**Strings used** (from `core/designsystem/src/commonMain/composeResources/values/strings.xml`):

```xml
<string name="reminder_exact_alarm_title">Enable Precise Reminders</string>
<string name="reminder_exact_alarm_step1">Tap "Open Settings" below</string>
<string name="reminder_exact_alarm_step2">Find "Alarms &amp; reminders"</string>
<string name="reminder_exact_alarm_step3">Toggle ON for Gisti</string>
<string name="reminder_exact_alarm_description">Without this, reminders may be delayed by a few minutes.</string>
<string name="reminder_exact_alarm_dont_show">Don't show again</string>
<string name="reminder_exact_alarm_open_settings">Open Settings</string>
<string name="reminder_exact_alarm_skip">Skip</string>
<string name="reminder_exact_alarm_granted">Precise reminders enabled!</string>
<string name="reminder_exact_alarm_denied">Reminders may be delayed by a few minutes</string>
```

### 6. ViewModel: Permission State Management

Path: `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/detail/ChecklistDetailViewModel.kt`

```kotlin
private var wentToExactAlarmSettings = false

private fun handleReminderClick() {
    val state = _screenState.value as? ChecklistDetailState.Content ?: return
    updateContentState { it.copy(showReminderSheet = true) }
}

private fun saveReminder(triggerAtMillis: Long) {
    val state = _screenState.value as? ChecklistDetailState.Content ?: return

    viewModelScope.launch {
        repository.setReminder(state.checklist.id, triggerAtMillis)
        updateContentState { it.copy(checklist = it.checklist.copy(reminderAt = triggerAtMillis)) }
        analyticsTracker.event("reminder_set", mapOf("checklist_id" to state.checklist.id.toString()))

        // Check if exact alarm permission is needed
        val suppressed = datastore.readBoolean(PREF_EXACT_ALARM_DONT_SHOW).first() ?: false
        if (!reminderScheduler.canScheduleExactAlarms() && !suppressed) {
            updateContentState { it.copy(showExactAlarmSheet = true, exactAlarmDontShowAgain = false) }
        }
    }
}

private fun handleExactAlarmOpenSettings() {
    viewModelScope.launch {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
        if (state.exactAlarmDontShowAgain) {
            datastore.saveBoolean(PREF_EXACT_ALARM_DONT_SHOW, true)
        }
        wentToExactAlarmSettings = true
        updateContentState { it.copy(showExactAlarmSheet = false) }
        reminderScheduler.openExactAlarmSettings()
    }
}

private fun handleExactAlarmSkip() {
    viewModelScope.launch {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
        if (state.exactAlarmDontShowAgain) {
            datastore.saveBoolean(PREF_EXACT_ALARM_DONT_SHOW, true)
        }
        updateContentState { it.copy(showExactAlarmSheet = false) }
    }
}

// Called when activity resumes after settings flow
fun handleReturnedFromSettings() {
    if (!wentToExactAlarmSettings) return
    wentToExactAlarmSettings = false

    viewModelScope.launch {
        if (reminderScheduler.canScheduleExactAlarms()) {
            reminderScheduler.rescheduleAllActive()
            updateContentState { it.copy(snackbarMessage = SNACKBAR_EXACT_GRANTED) }
        } else {
            updateContentState { it.copy(snackbarMessage = SNACKBAR_EXACT_DENIED) }
        }
    }
}

companion object {
    private const val PREF_EXACT_ALARM_DONT_SHOW = "pref_exact_alarm_dont_show"
    private const val SNACKBAR_EXACT_GRANTED = "reminder_exact_alarm_granted"
    private const val SNACKBAR_EXACT_DENIED = "reminder_exact_alarm_denied"
}
```

**Key logic:**
- **When reminder is saved**: Check if permission is granted and "don't show" is not set; show sheet
- **Open Settings**: Mark that user navigated to settings, open intent, save "don't show" preference if checked
- **Returned from Settings**: Detect that user came back from settings, check if permission now granted, reschedule all alarms, show snackbar feedback

### 7. Intent & State Contracts

Path: `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/detail/ChecklistDetailScreenContract.kt`

```kotlin
sealed interface ChecklistDetailIntent {
    // ... existing intents ...

    // Exact alarm permission
    data object OnExactAlarmOpenSettings : ChecklistDetailIntent
    data object OnExactAlarmSkip : ChecklistDetailIntent
    data class OnExactAlarmDontShowChanged(val checked: Boolean) : ChecklistDetailIntent
    data object OnDismissExactAlarmSheet : ChecklistDetailIntent
    data object OnReturnedFromSettings : ChecklistDetailIntent
    data object OnSnackbarDismissed : ChecklistDetailIntent
}

data class ChecklistDetailState(
    val checklist: Checklist,
    val defaultFill: ChecklistFill?,
    val additionalFillsCount: Int,
    val userLimits: UserLimits?,

    // ... existing state fields ...

    // Exact alarm permission
    val showExactAlarmSheet: Boolean = false,
    val exactAlarmDontShowAgain: Boolean = false,
    val snackbarMessage: String? = null,
)
```

### 8. Lifecycle Handling in AppScaffold

Path: `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/containers/AppScaffold.kt`

When the detail screen resumes (user returns from settings), call the ViewModel:

```kotlin
LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    viewModel.handleReturnedFromSettings()
}
```

## Testing Strategy

### Unit Tests

1. **ReminderScheduler.canScheduleExactAlarms()**
   - Test API 30: Should return true
   - Test API 31+ with permission granted: Should return true
   - Test API 31+ with permission denied: Should return false

2. **ReminderScheduler.schedule()**
   - When permission granted: Verify `setExactAndAllowWhileIdle()` called
   - When permission denied: Verify `setAndAllowWhileIdle()` called

3. **ReminderScheduler.rescheduleAllActive()**
   - Verify all reminders from DB are rescheduled
   - Verify uses current permission state

### Integration Tests

1. **Save reminder with permission denied**
   - Bottom sheet should appear
   - "Don't show" unchecked by default
   - "Open Settings" button navigates to settings intent

2. **Save reminder with "Don't show" checked**
   - Save preference to DataStore
   - Next reminder saved: Sheet should NOT appear

3. **Return from settings with permission granted**
   - All reminders rescheduled to exact
   - Snackbar shows "Precise reminders enabled!"
   - Next save: Sheet should NOT appear

4. **Device reboot**
   - BootCompletedReceiver triggers
   - All active reminders rescheduled
   - Respects current permission state

5. **Permission state changes (via system settings)**
   - ExactAlarmPermissionReceiver triggers
   - All active reminders rescheduled
   - Upgrade/downgrade to exact/inexact

### E2E Tests

Similar to the Android Instrumented Test Suite patterns:
- Create checklist, set reminder
- Verify exact alarm permission bottom sheet appears
- Tap "Open Settings", navigate back
- Verify snackbar feedback shows permission granted
- Create another reminder: Sheet should not appear

## Performance Considerations

### Battery Impact

- **`setExactAndAllowWhileIdle()`**: Slightly higher battery usage than inexact, but respects Doze mode
- **Inexact fallback**: Allows batching if permission denied, preserving battery
- **Reschedule operations**: Efficient batch reschedule after boot/permission change (all in one loop)

### Database Queries

- **`rescheduleAllActive()`**: Single query to fetch all active reminders, then schedule in loop
- **No N+1 queries**: Single batch operation, not per-reminder

## Backward Compatibility

- **API < 31**: Always returns true for `canScheduleExactAlarms()` (permission not required)
- **API 31-12**: Permission required but available for non-specialized apps
- **Graceful fallback**: Always works without permission (just inexact)

## Troubleshooting

### Reminders Still Delayed

1. **Verify permission granted**: Open Settings > Apps > Gisti > Alarms & reminders
2. **Check DataStore preference**: May have "don't show" set; clear it:
   - Go to Settings > Apps > Gisti > Storage > Clear Cache & Data
   - Or in-app: Reset via debug menu if available
3. **Device Doze**: Even with exact alarm, Doze can still throttle for a brief moment; verify reminder is within next 5 minutes
4. **App not in foreground**: Exact alarms fire in background, but notification delivery may be delayed by system

### ExactAlarmPermissionReceiver Not Firing

1. Verify `android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` in manifest
2. Verify receiver is exported=false (should not be exported)
3. Check device logs: `adb logcat | grep ExactAlarmPermissionReceiver`

### Reminders Lost After Reboot

1. Verify `RECEIVE_BOOT_COMPLETED` permission in manifest
2. Verify `BootCompletedReceiver` has intent filter
3. Check logs: `adb logcat | grep BootCompletedReceiver`
4. **Note**: Some devices (Samsung, etc.) require whitelisting in battery optimization settings

## Related Documentation

- [DataStore Singleton Pattern](../runtime-errors/datastore-multiple-instances-crash.md)
- [CSAT Bottom Sheet Pattern](./csat-survey-with-in-app-review.md)
- [Brainstorm: Exact Alarm Design Decisions](../../brainstorms/2026-02-22-exact-alarm-reminders-brainstorm.md)
- [Implementation Plan: Checklist Reminders](../../plans/2026-02-22-feat-checklist-reminders-and-deadlines-plan.md)
- [Android AlarmManager Best Practices](https://developer.android.com/training/scheduling/alarms)
- [Doze and App Standby Documentation](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Google Play Policy: Alarms and Reminders](https://support.google.com/googleplay/android-developer/answer/9888170)

## Commit History

- **905b76c**: `feat(reminders): upgrade to exact alarms with permission flow`
  - Initial implementation with all components
  - Adds exact alarm permission request and reschedule logic
  - Instruction bottom sheet with "don't show again" preference

- **41b9fd6**: `feat(reminders): add checklist reminders with notifications`
  - Original reminder system with inexact alarms

## Summary

This solution provides:
1. ✅ **Exact alarm delivery** when permission available (< 1 second vs 5-10 min)
2. ✅ **Graceful fallback** to inexact alarms if permission denied
3. ✅ **User-friendly permission flow** with in-app instruction sheet
4. ✅ **No constant prompting** via "don't show again" preference
5. ✅ **Automatic upgrading** when permission granted later
6. ✅ **Lifecycle resilience** (reboots, permission changes)
7. ✅ **Google Play compliant** (SCHEDULE_EXACT_ALARM, not USE_EXACT_ALARM)
