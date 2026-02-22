---
title: "feat: Add checklist reminders and deadlines"
type: feat
date: 2026-02-22
deepened: 2026-02-22
reviewed: 2026-02-22
---

# feat: Add checklist reminders and deadlines

## Enhancement Summary

**Deepened on:** 2026-02-22
**Research agents used:** Architecture Strategist, Data Integrity Guardian, Performance Oracle, Security Sentinel, Spec Flow Analyzer, Best Practices Researcher, Framework Docs Researcher, Learnings Researcher, Code Simplicity Reviewer

### Critical Fixes (from research)
1. **Switch to inexact alarms** — `setAndAllowWhileIdle()` instead of `setExactAndAllowWhileIdle()`. Avoids `SCHEDULE_EXACT_ALARM` permission which is **denied by default on Android 14+** and triggers Google Play policy review
2. **Manual Room migration** — `AutoMigration` requires `exportSchema = true` (currently `false`) + Room Gradle Plugin. Manual `ALTER TABLE` is one line and KMP-safe
3. **Fix requestCode collision** — `checklistId.toInt()` silently overwrites PendingIntents when IDs share lower 32 bits. Use XOR-folding
4. **Use `goAsync()` in BroadcastReceiver** — DB query in `onReceive()` without it risks ANR (10-second limit)
5. **Define post-fire behavior** — auto-clear reminder from DB after notification fires

### Simplifications Applied
1. **No `expect/actual`** — iOS not released; plain Android class in `composeApp/androidMain/` (like widget infrastructure)
2. **No `BootReceiver` for MVP** — edge case; add in follow-up iteration
3. **Merge `ReminderNotificationHelper` into `ReminderReceiver`** — single-caller abstraction eliminated
4. **Collapse picker state** — `showDatePicker` + `showTimePicker` → derived from `selectedDateMillis`
5. **Don't modify `UserLimits`** — count active reminders directly in ViewModel via repository

### New Considerations Discovered
- DatePicker `selectedDateMillis` is **UTC midnight** — must convert to local date via kotlinx-datetime
- `ModalBottomSheet` content must include `navigationBarsPadding()`
- Notification needs `VISIBILITY_PRIVATE` + `setPublicVersion()` for lock screen
- Use `TaskStackBuilder` for proper back navigation from notification
- Analytics events needed for measuring feature adoption
- App updates also clear AlarmManager alarms (not just reboots) — re-schedule in `Application.onCreate()`

### Review Fixes (from plan_review — 3 agents)
1. **🔴 Create `ChecklistReminderScheduler` interface in commonMain** — ViewModel in commonMain cannot reference Android-only `ReminderScheduler` class. Won't compile without interface (Dependency Inversion)
2. **🔴 Update `toDomain()`/`toEntity()` mappings** — without `reminderAt` in mapping, every `updateChecklist()` call silently overwrites `reminderAt` with `null`
3. **🔴 Fix deep link cold start** — `onNewIntent()` is not called on cold start from notification. Must also check `intent` in `onCreate()` and defer navigation until NavController is ready
4. **🐛 Add optimistic state update** — `saveReminder()`/`removeReminder()` must update `state.checklist.reminderAt` immediately, otherwise bell icon won't toggle
5. **🐛 Show `deleteChecklist()` code** with `reminderScheduler.cancel()` explicitly
6. **🔧 Define `applicationScope`** in `GistiApplication` — property does not exist currently
7. **🔧 Add new methods to `ChecklistRepository` interface** — not just the impl class
8. **🔧 Fix `getDefaultFill()`** — current repository returns `Flow`, not suspend. Use `.first()` in receiver
9. **🔧 Inject repository into `ReminderScheduler` via constructor** — not as method parameter
10. **🔧 Fix notification channel description** — hardcoded English string → use `context.getString()`
11. **✂️ Merge dismiss intents** — `OnDismissReminderSheet` + `OnDismissCustomPicker` → single `OnDismissReminderUI`
12. **✂️ Reduce analytics** — from 7 to 3 events for MVP (`reminder_set`, `reminder_notification_tapped`, `reminder_cancelled`)

---

## Overview

Add the ability to set a deadline and push notification reminder for any checklist. Users pick a time via a bottom sheet with quick presets ("In 1 hour", "Tomorrow morning") or a custom date/time picker. At the scheduled time, a push notification shows the checklist name and remaining unchecked items count (computed at fire time from the default fill).

**Monetization:** Free users get 1 active reminder (total); Premium users get unlimited reminders.

**Platform:** Android-only for v1. iOS stubs not needed (no `expect/actual`).

## Problem Statement / Motivation

- Users create checklists for time-sensitive tasks (apartment inspections, packing lists, project deadlines) but have no way to be reminded
- Push notifications are the **#1 driver of daily active usage** — without them, users forget the app exists
- Competitors (Google Keep, Todoist, Any.do) all have reminders as a core feature
- Adding reminders increases perceived value of Premium subscriptions and gives a clear Free→Premium upsell moment

## User Flow

```
ChecklistDetail Screen
    ├── Top bar: [...] [🔔] [Share] [Edit] [Delete]
    │                   ↓
    │           Tap bell icon
    │                   ↓
    │    ┌──────────────────────────┐
    │    │   Set Reminder           │
    │    ├──────────────────────────┤
    │    │  ⏰  In 1 hour           │
    │    │  🌅  Tomorrow morning    │  ← 09:00 local time
    │    │  🌆  Tomorrow evening    │  ← 18:00 local time
    │    │  📅  Pick date & time... │  ← opens DatePicker → TimePicker
    │    │                          │
    │    │  [Remove reminder]       │  ← shown only if reminder is set
    │    └──────────────────────────┘
    │                   ↓
    │         Reminder saved
    │         Bell icon → filled 🔔
    │         Subtitle appears: "Reminder: Tomorrow, 9:00 AM"
    │
    ╰── At reminder time:
         Push notification:
         "Apartment Inspection — 3 items remaining"
         Tap → opens Main → ChecklistDetail (proper back stack)
         Reminder auto-cleared from DB after firing
```

### Second tap on filled bell
Opens the same bottom sheet with current reminder shown + "Remove reminder" option. User can overwrite with a new time or remove entirely.

### Post-fire behavior
After the notification fires, `reminderAt` is set to `null` in the DB. The bell icon returns to outlined state. The reminder does not persist or repeat.

### Edge Cases

1. **Reminder in the past**: If user picks a date/time that already passed → show Snackbar "Please select a future time" (project uses Snackbar, not Toast)
2. **All items checked**: Notification says "All items completed!" instead of "X items remaining"
3. **Checklist deleted**: Cancel pending alarm + alarm cancelled implicitly since `reminderAt` column is deleted with the row
4. **App update**: AlarmManager alarms are cleared on app update — re-schedule active reminders in `Application.onCreate()`
5. **Device reboot**: (MVP: not handled — TODO for next iteration via `BOOT_COMPLETED` receiver)
6. **Notification permission denied** (Android 13+): Save reminder anyway, show info Snackbar "Enable notifications in Settings to receive reminders"
7. **Free user with 1 reminder tries to add another**: Show paywall before opening bottom sheet
8. **Reminder fires while app is in foreground**: Still show notification (don't suppress)
9. **Timezone change**: `reminderAt` is stored as absolute epoch millis — fires at correct absolute moment. Subtitle re-renders in new local time on screen reopen
10. **"In 1 hour" selected after delay**: Timestamp computed at moment of tap, not at sheet-open time. Always validated > now before scheduling

### Intentional omissions (MVP)
- **No snooze** — notification has no action buttons, only tap-to-open
- **No reminder during checklist creation** — only from ChecklistDetail screen
- **No Main screen indicator** — bell badge on cards deferred to v2

## Proposed Solution

### Architecture

No new feature module. Changes spread across existing modules following the widget infrastructure precedent:

```
feature/checklist/          ← Model + DB changes (add reminderAt field)
                            ← ChecklistReminderScheduler interface (commonMain)
feature/home/               ← UI changes (bell icon, bottom sheet, subtitle)
composeApp/androidMain/     ← ReminderScheduler (implements interface), ReminderReceiver, notification channel
core/designsystem/          ← New string resources
```

> **Research insight (Architecture Strategist):** `composeApp/androidMain/` is the established home for Android-only infrastructure — widget code (`ChecklistWidgetReceiver`, `WidgetUpdateWorker`, `WidgetRepository`) already lives here. Notification infrastructure follows the same precedent.

> **Review fix (Dependency Inversion):** `ChecklistDetailViewModel` lives in `feature/home/src/commonMain/`. It **cannot** reference `ReminderScheduler` from `composeApp/androidMain/` directly — this would be a circular dependency and won't compile. Solution: introduce a `ChecklistReminderScheduler` interface in `feature/checklist/` (commonMain), implement it in `composeApp/androidMain/`, and register via `platformModule()`.

**File:** `feature/checklist/.../domain/scheduler/ChecklistReminderScheduler.kt`

```kotlin
/**
 * Abstraction for scheduling/cancelling checklist reminders.
 * Implemented by Android's AlarmManager-based ReminderScheduler.
 * iOS can provide a no-op implementation when needed.
 */
interface ChecklistReminderScheduler {
    fun schedule(checklistId: Long, triggerAtMillis: Long)
    fun cancel(checklistId: Long)
    suspend fun rescheduleAllActive()
}
```

### 1. Domain Model Changes

**File:** `feature/checklist/.../domain/model/Checklist.kt`

```kotlin
@Serializable
data class Checklist(
    val id: Long = 0L,
    val name: String,
    val items: List<ChecklistItem>,
    val reminderAt: Long? = null  // epoch millis, null = no reminder
)
```

> **Note (@Serializable):** `Checklist` is annotated with `@Serializable`. Adding `reminderAt: Long? = null` with a default value is backward-compatible — existing serialized forms without `reminderAt` will deserialize correctly.

### 2. Database Changes

**File:** `feature/checklist/.../data/db/ChecklistEntity.kt`

```kotlin
@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val items: List<ChecklistItem>,
    val reminderAt: Long? = null  // NEW COLUMN
)

// CRITICAL: Update mapping functions to include reminderAt!
// Without this, every updateChecklist() call silently overwrites reminderAt with null.
fun ChecklistEntity.toDomain() = Checklist(id, name, items, reminderAt)
fun Checklist.toEntity() = ChecklistEntity(id, name, items, reminderAt)
```

**Migration:** Manual `Migration(3, 4)` — NOT AutoMigration.

> **Research insight (Data Integrity Guardian):** `AutoMigration` requires `exportSchema = true` (currently `false`), the Room Gradle Plugin, and schema JSON files committed to source control. Manual migration is one line of SQL, works reliably across KMP targets, and doesn't require build system changes.
>
> **CRITICAL:** `fallbackToDestructiveMigration(dropAllTables = false)` without a migration will **drop and recreate the `checklists` table**, wiping all user data. A manual `Migration` prevents this.

```kotlin
// In ChecklistDatabase.kt or a separate Migrations.kt
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        // Safe: ALTER TABLE ADD COLUMN with NULL default never touches existing rows
        connection.execSQL("ALTER TABLE checklists ADD COLUMN reminderAt INTEGER DEFAULT NULL")
    }
}

@Database(
    entities = [ChecklistEntity::class, ChecklistFillEntity::class],
    version = 4,
    exportSchema = false  // unchanged — no AutoMigration needed
)
@TypeConverters(ChecklistItemConverters::class)
@ConstructedBy(ChecklistDatabaseConstructor::class)
abstract class ChecklistDatabase : RoomDatabase() {
    // ...
    companion object {
        fun getRoomDatabase(builder: Builder<ChecklistDatabase>): ChecklistDatabase {
            return builder
                .addMigrations(MIGRATION_3_4)  // explicit, safe, cross-platform
                .fallbackToDestructiveMigration(dropAllTables = false)  // safety net for v1/v2 users
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
    }
}
```

> **KMP note:** Room KMP uses `SQLiteConnection` (not `SupportSQLiteDatabase`) in `Migration.migrate()`. The API is `connection.execSQL(String)`.

### 3. Repository Changes

**File:** `feature/checklist/.../data/db/ChecklistDao.kt`

```kotlin
@Query("UPDATE checklists SET reminderAt = :reminderAt WHERE id = :id")
suspend fun updateReminder(id: Long, reminderAt: Long?)

@Query("SELECT COUNT(*) FROM checklists WHERE reminderAt IS NOT NULL AND reminderAt > :nowMillis")
suspend fun countActiveReminders(nowMillis: Long = System.currentTimeMillis()): Int

// Minimal projection for re-scheduling — no need for full entity with JSON items blob
data class ChecklistReminderInfo(val id: Long, val name: String, val reminderAt: Long)

@Query("SELECT id, name, reminderAt FROM checklists WHERE reminderAt IS NOT NULL AND reminderAt > :nowMillis")
suspend fun getActiveReminders(nowMillis: Long = System.currentTimeMillis()): List<ChecklistReminderInfo>
```

> **Research insight (Performance Oracle):** Use minimal projection (`id, name, reminderAt`) instead of `SELECT *` to avoid deserializing the large `items` JSON blob when only scheduling data is needed.

**File:** `feature/checklist/.../domain/repository/ChecklistRepository.kt` (interface — add these methods)

```kotlin
// Add to ChecklistRepository interface:
suspend fun setReminder(checklistId: Long, reminderAt: Long?)
suspend fun countActiveReminders(): Int
suspend fun getActiveReminders(): List<ChecklistReminderInfo>
suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill?
```

> **Review fix:** Methods must appear on the interface, not just the impl. The ViewModel depends on the interface via Koin.

**File:** `feature/checklist/.../data/repository/ChecklistRepositoryImpl.kt`

```kotlin
override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {
    checklistDao.updateReminder(checklistId, reminderAt)
}

override suspend fun countActiveReminders(): Int {
    return checklistDao.countActiveReminders()
}

override suspend fun getActiveReminders(): List<ChecklistReminderInfo> {
    return checklistDao.getActiveReminders()
}

override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? {
    return checklistFillDao.getDefaultFillByChecklistId(checklistId).first()
}
```

> **Review fix (R4):** The current `getDefaultFillByChecklistId()` returns `Flow<ChecklistFill?>`, not a suspend function. `ReminderReceiver` needs a one-shot call. Use `.first()` on the Flow in the repository impl.

### 4. Reminder Scheduling (Android-only)

**File:** `composeApp/src/androidMain/.../notification/ReminderScheduler.kt`

> **Research insight (Code Simplicity):** No `expect/actual` needed. iOS is not released and has no timeline. The widget module follows the same pattern — Android-only code in `composeApp/androidMain/`.

> **Research insight (Security Sentinel):** Use `setAndAllowWhileIdle()` (inexact) instead of `setExactAndAllowWhileIdle()`. `SCHEDULE_EXACT_ALARM` is **denied by default on Android 14+** for apps targeting API 33+, and requires Google Play policy justification. Inexact alarms fire within a few minutes — perfectly acceptable for checklist reminders.

```kotlin
class ReminderScheduler(
    private val context: Context,
    private val repository: ChecklistRepository  // constructor injection, not method param
) : ChecklistReminderScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(checklistId: Long, triggerAtMillis: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_CHECKLIST_ID, checklistId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            checklistIdToRequestCode(checklistId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Inexact alarm — fires within a few minutes of target. No SCHEDULE_EXACT_ALARM needed.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
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

    /**
     * Re-schedule all active reminders from DB.
     * Called from Application.onCreate() after app update clears alarms.
     */
    override suspend fun rescheduleAllActive() {
        val reminders = repository.getActiveReminders()
        reminders.forEach { reminder ->
            schedule(reminder.id, reminder.reminderAt)
        }
    }

    companion object {
        /**
         * XOR-fold Long to Int for PendingIntent requestCode.
         * Prevents collision when checklistId > Int.MAX_VALUE.
         */
        fun checklistIdToRequestCode(checklistId: Long): Int {
            return (checklistId xor (checklistId ushr 32)).toInt()
        }
    }
}
```

> **Review fix:** `ReminderScheduler` now implements `ChecklistReminderScheduler` interface and receives `ChecklistRepository` via constructor injection (consistent with Koin pattern). The ViewModel depends on the interface, not the concrete class.

### 5. Android Notification Infrastructure

**File:** `composeApp/src/androidMain/.../notification/ReminderReceiver.kt`

> **Research insight (Performance Oracle):** Must use `goAsync()` to extend receiver lifetime for DB query. Without it, the process can be killed before the coroutine completes, or the 10-second ANR timer fires.

> **Research insight (Learnings — datastore-multiple-instances-crash):** Access repository via Koin `GlobalContext.get()` (same pattern as widget's `ToggleItemAction`), never instantiate directly.

```kotlin
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE = "com.antonchuraev.aichecklists.ACTION_REMINDER_FIRE"
        const val EXTRA_CHECKLIST_ID = "checklist_id"
        private const val CHANNEL_ID = "checklist_reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val checklistId = intent.getLongExtra(EXTRA_CHECKLIST_ID, -1L)
        if (checklistId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository: ChecklistRepository = GlobalContext.get().get()

                // 1. Get checklist data at fire time (accurate item count)
                val checklist = repository.getChecklistById(checklistId) ?: return@launch
                val defaultFill = repository.getDefaultFillOneShot(checklistId)  // suspend, not Flow
                val uncheckedCount = defaultFill?.items?.count { !it.checked } ?: 0
                val totalCount = defaultFill?.items?.size ?: 0

                // 2. Auto-clear reminder from DB
                repository.setReminder(checklistId, null)

                // 3. Show notification
                showNotification(context, checklistId, checklist.name, uncheckedCount, totalCount)
            } catch (e: Exception) {
                // Log to Crashlytics, do not crash the receiver
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        checklistId: Long,
        checklistName: String,
        uncheckedCount: Int,
        totalCount: Int
    ) {
        val body = if (uncheckedCount == 0) {
            context.getString(R.string.reminder_all_completed)
        } else {
            context.getString(R.string.reminder_items_remaining, uncheckedCount)
        }

        // Build deep link with proper back stack (Main → ChecklistDetail)
        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.antonchuraev.aichecklists.action.OPEN_CHECKLIST"
            putExtra("navigate_to_checklist", checklistId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(deepLinkIntent)
            getPendingIntent(
                ReminderScheduler.checklistIdToRequestCode(checklistId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )!!
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO: proper notification icon
            .setContentTitle(checklistName.take(200)) // sanitize length
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.reminder_notification_tap))
                    .build()
            )
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            ReminderScheduler.checklistIdToRequestCode(checklistId),
            notification
        )
    }

    /**
     * Create the notification channel. Call from Application.onCreate().
     */
    companion object {
        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_notification_channel),
                NotificationManager.IMPORTANCE_HIGH // sound + heads-up
            ).apply {
                description = context.getString(R.string.reminder_notification_channel_desc)
                enableLights(true)
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
```

### 6. AndroidManifest Changes

> **Research insight (Security Sentinel):** No `SCHEDULE_EXACT_ALARM` needed — using inexact alarms. No `RECEIVE_BOOT_COMPLETED` for MVP.

```xml
<!-- Push notifications (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Reminder receiver — NOT exported, only AlarmManager sends to it -->
<receiver
    android:name=".notification.ReminderReceiver"
    android:exported="false">
    <!-- No intent-filter: driven exclusively by AlarmManager explicit intents -->
</receiver>
```

### 7. UI Changes — ChecklistDetailScreen

**Bell icon in toolbar:**

```kotlin
// In TopAppBar actions, before Share icon
IconButton(onClick = { sendIntent(OnReminderClick) }) {
    Icon(
        imageVector = if (state.checklist.reminderAt != null)
            Icons.Filled.Notifications    // filled = reminder set
        else
            Icons.Outlined.Notifications, // outlined = no reminder
        contentDescription = stringResource(
            if (state.checklist.reminderAt != null)
                Res.string.reminder_set_for  // "Reminder set for {time}"
            else
                Res.string.reminder_set_reminder  // "Set Reminder"
        )
    )
}
```

> **Research insight (Spec Flow Analyzer — Accessibility):** `contentDescription` must announce the state ("Reminder set for Tomorrow, 9:00 AM" vs "Set Reminder") for screen readers, matching the existing Share/Edit/Delete pattern.

**Bottom sheet — `ReminderBottomSheet`:**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderBottomSheet(
    currentReminder: Long?,
    onPresetSelected: (Long) -> Unit,
    onCustomDateRequested: () -> Unit,
    onRemoveReminder: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()  // required per framework docs research
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl)
        ) {
            Text(
                text = stringResource(Res.string.reminder_set_reminder),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            // Presets — use AppDimens spacing, design system patterns
            ReminderPresetRow(Icons.Default.Schedule, Res.string.reminder_in_one_hour) {
                onPresetSelected(Clock.System.now().plus(1.hours).toEpochMilliseconds())
            }
            ReminderPresetRow(Icons.Default.WbSunny, Res.string.reminder_tomorrow_morning) {
                onPresetSelected(tomorrowAt(hour = 9, minute = 0))
            }
            ReminderPresetRow(Icons.Default.WbTwilight, Res.string.reminder_tomorrow_evening) {
                onPresetSelected(tomorrowAt(hour = 18, minute = 0))
            }
            ReminderPresetRow(Icons.Default.CalendarMonth, Res.string.reminder_pick_date_time) {
                onCustomDateRequested()
            }

            if (currentReminder != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = AppDimens.SpacingSm))
                AppButtonText(
                    text = stringResource(Res.string.reminder_remove),
                    onClick = onRemoveReminder,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
```

**Preset time calculation with kotlinx-datetime:**

> **Research insight (Framework Docs):** Use `Instant.plus(1, DateTimeUnit.DAY, tz)` for calendar-aware arithmetic. Never use `+ 24.hours` — DST transitions make days 23 or 25 hours.

```kotlin
import kotlinx.datetime.*

fun tomorrowAt(hour: Int, minute: Int): Long {
    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val tomorrowDate = now.plus(1, DateTimeUnit.DAY, tz).toLocalDateTime(tz).date
    val targetDateTime = LocalDateTime(tomorrowDate, LocalTime(hour, minute))
    return targetDateTime.toInstant(tz).toEpochMilliseconds()
}
```

**Custom date/time picker — DatePicker + TimePicker:**

> **Research insight (Framework Docs):** `DatePickerState.selectedDateMillis` returns **UTC midnight**. Must convert to local date before combining with time. There is no `TimePickerDialog` composable — wrap `TimePicker` in `AlertDialog`.

```kotlin
@Composable
fun ReminderDateTimePicker(
    selectedDateMillis: Long?,  // null = show DatePicker, non-null = show TimePicker
    onDateSelected: (Long) -> Unit,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (selectedDateMillis == null) {
        // Step 1: Date picker
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                AppButtonText(text = stringResource(Res.string.next)) {
                    dateState.selectedDateMillis?.let(onDateSelected)
                }
            },
            dismissButton = {
                AppButtonText(text = stringResource(Res.string.cancel), onClick = onDismiss)
            }
        ) {
            DatePicker(state = dateState)
        }
    } else {
        // Step 2: Time picker
        val timeState = rememberTimePickerState(initialHour = 9, initialMinute = 0)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.reminder_select_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                AppButtonText(text = stringResource(Res.string.ok)) {
                    onTimeSelected(timeState.hour, timeState.minute)
                }
            },
            dismissButton = {
                AppButtonText(text = stringResource(Res.string.cancel), onClick = onDismiss)
            }
        )
    }
}

/**
 * Combine DatePicker UTC midnight millis + TimePicker hour/minute
 * into a local-timezone epoch millis for scheduling.
 */
fun combinePickerResults(datePickerMillis: Long, hour: Int, minute: Int): Long {
    val utcMidnight = Instant.fromEpochMilliseconds(datePickerMillis)
    val localDate = utcMidnight.toLocalDateTime(TimeZone.UTC).date
    val localDateTime = LocalDateTime(localDate, LocalTime(hour, minute))
    return localDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}
```

### 8. State & Intent Changes

**File:** `ChecklistDetailScreenContract.kt`

> **Research insight (Code Simplicity):** Collapse `showDatePicker` + `showTimePicker` into a single `selectedDateMillis`. When `showReminderSheet = false` and `selectedDateMillis == null` → show DatePicker; when `selectedDateMillis != null` → show TimePicker.

```kotlin
data class Content(
    // ... existing fields ...
    val showReminderSheet: Boolean = false,
    val customPickerDateMillis: Long? = null, // null = show DatePicker, non-null = show TimePicker
    val showCustomPicker: Boolean = false,
    val reminderError: String? = null,  // for Snackbar errors (past time, etc.)
)

sealed interface ChecklistDetailIntent {
    // ... existing intents ...
    data object OnReminderClick : ChecklistDetailIntent
    data class OnReminderPresetSelected(val triggerAtMillis: Long) : ChecklistDetailIntent
    data object OnCustomDateRequested : ChecklistDetailIntent
    data class OnDateSelected(val dateMillis: Long) : ChecklistDetailIntent
    data class OnTimeSelected(val hour: Int, val minute: Int) : ChecklistDetailIntent
    data object OnRemoveReminder : ChecklistDetailIntent
    data object OnDismissReminderUI : ChecklistDetailIntent  // single dismiss for sheet + picker
}
```

> **Review fix:** Two dismiss intents merged into `OnDismissReminderUI`. ViewModel knows which UI is showing from state and resets accordingly. Added `reminderError: String?` for Snackbar errors (since SideEffect type is `Nothing`).

### 9. ViewModel Logic

**File:** `ChecklistDetailViewModel.kt`

> **Research insight (Code Simplicity):** Count active reminders directly via repository, don't modify `UserLimits`. The limit check mirrors the existing fill-limit pattern.

> **Research insight (Learnings — cascade delete):** Cancel the data-loading Flow before deleting a checklist with a reminder, to prevent race condition writes.

```kotlin
// ViewModel depends on the interface, not the concrete class
private val reminderScheduler: ChecklistReminderScheduler = get()

override fun onIntent(intent: ChecklistDetailIntent) {
    when (intent) {
        is OnReminderClick -> {
            viewModelScope.launch {
                val state = currentContentState ?: return@launch
                val isPremium = state.userLimits?.isPremium ?: false
                val currentChecklistHasReminder = state.checklist.reminderAt != null

                if (!isPremium && !currentChecklistHasReminder) {
                    // Check if free user already has 1 reminder elsewhere
                    val activeCount = repository.countActiveReminders()
                    if (activeCount >= 1) {
                        navigator.navigate(AppNavRoute.Paywall)
                        return@launch
                    }
                }
                updateContentState { it.copy(showReminderSheet = true) }
            }
        }

        is OnReminderPresetSelected -> {
            val now = Clock.System.now().toEpochMilliseconds()
            if (intent.triggerAtMillis <= now) {
                updateContentState { it.copy(reminderError = "Please select a future time") }
                return
            }
            saveReminder(intent.triggerAtMillis)
            updateContentState { it.copy(showReminderSheet = false) }
        }

        is OnCustomDateRequested -> {
            updateContentState {
                it.copy(showReminderSheet = false, showCustomPicker = true, customPickerDateMillis = null)
            }
        }

        is OnDateSelected -> {
            updateContentState { it.copy(customPickerDateMillis = intent.dateMillis) }
        }

        is OnTimeSelected -> {
            val dateMillis = currentContentState?.customPickerDateMillis ?: return
            val triggerAt = combinePickerResults(dateMillis, intent.hour, intent.minute)
            val now = Clock.System.now().toEpochMilliseconds()
            if (triggerAt <= now) {
                updateContentState { it.copy(reminderError = "Please select a future time") }
                return
            }
            saveReminder(triggerAt)
            updateContentState { it.copy(showCustomPicker = false, customPickerDateMillis = null) }
        }

        is OnRemoveReminder -> {
            removeReminder()
            updateContentState { it.copy(showReminderSheet = false) }
        }

        // Single dismiss intent for both sheet and picker
        is OnDismissReminderUI -> {
            updateContentState {
                it.copy(showReminderSheet = false, showCustomPicker = false, customPickerDateMillis = null)
            }
        }
    }
}

private fun saveReminder(triggerAtMillis: Long) {
    viewModelScope.launch {
        val checklist = currentChecklist ?: return@launch
        repository.setReminder(checklist.id, triggerAtMillis)
        reminderScheduler.schedule(checklist.id, triggerAtMillis)
        // Optimistic state update — bell icon toggles immediately
        updateContentState {
            it.copy(checklist = it.checklist.copy(reminderAt = triggerAtMillis))
        }
        analyticsTracker.track("reminder_set", mapOf(
            "checklist_id" to checklist.id.toString(),
            "preset" to determinePresetType(triggerAtMillis)
        ))
    }
}

private fun removeReminder() {
    viewModelScope.launch {
        val checklist = currentChecklist ?: return@launch
        repository.setReminder(checklist.id, null)
        reminderScheduler.cancel(checklist.id)
        // Optimistic state update — bell icon returns to outlined
        updateContentState {
            it.copy(checklist = it.checklist.copy(reminderAt = null))
        }
        analyticsTracker.track("reminder_cancelled", mapOf(
            "checklist_id" to checklist.id.toString()
        ))
    }
}

// REVIEW FIX: Explicit deleteChecklist() with alarm cancellation
private fun deleteChecklist() {
    val state = _screenState.value
    if (state !is ChecklistDetailState.Content) return
    updateContentState { it.copy(showDeleteConfirmation = false) }
    loadDataJob?.cancel()  // prevent race condition writes
    viewModelScope.launch {
        reminderScheduler.cancel(state.checklist.id)  // cancel pending alarm
        repository.deleteChecklist(state.checklist)
        navigator.onBack()
    }
}
```

> **Review fixes applied:**
> - `reminderScheduler` typed as `ChecklistReminderScheduler` interface (compiles from commonMain)
> - Optimistic state update in `saveReminder()`/`removeReminder()` — bell icon toggles immediately
> - `deleteChecklist()` shown explicitly with `reminderScheduler.cancel()` + `loadDataJob?.cancel()`
> - Single `OnDismissReminderUI` intent replaces two separate dismiss intents
> - Error state via `reminderError` field (no SideEffect needed)

### 10. Koin DI Registration

**File:** `composeApp/src/androidMain/.../di/PlatformModule.android.kt`

```kotlin
actual fun platformModule(): Module = module {
    includes(widgetModule)
    single { AppContextHolder.context }
    single<ChecklistReminderScheduler> { ReminderScheduler(get(), get()) }  // binds interface to Android impl
    // ... existing registrations ...
}
```

> **Review fix:** Registers `ReminderScheduler` as `ChecklistReminderScheduler` interface. The ViewModel in commonMain depends on the interface. `get(), get()` resolves `Context` and `ChecklistRepository` via constructor injection.

### 11. App Startup — Channel & Re-scheduling

**File:** `composeApp/src/androidMain/.../GistiApplication.kt`

```kotlin
// REVIEW FIX: Define applicationScope (does not exist in current GistiApplication)
private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

override fun onCreate() {
    super.onCreate()
    // ... existing init ...

    // Create notification channel
    ReminderReceiver.createNotificationChannel(this)

    // Re-schedule active reminders (cleared by app updates)
    applicationScope.launch {
        val scheduler: ChecklistReminderScheduler = GlobalContext.get().get()
        scheduler.rescheduleAllActive()  // repository injected via constructor
    }
}
```

> **Review fix:** `applicationScope` defined as property. `rescheduleAllActive()` no longer takes `repository` — it's injected via constructor.

### 12. Deep Link Handling

**File:** `MainActivity.kt`

> **Review fix (R3 — cold start):** `onNewIntent()` is NOT called on cold start from a notification. The deep-link intent arrives in `onCreate()`. Calling `navigator.navigate()` before the Compose NavController is installed is a race condition. Solution: store the pending deep link and consume it after navigation graph is ready.

```kotlin
// Store pending deep link for cold-start case
private var pendingChecklistId: Long? = null

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Check for deep link in launch intent (cold start from notification)
    extractDeepLinkChecklistId(intent)?.let { id ->
        pendingChecklistId = id
    }
    // ... existing setContent { } ...
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    // Warm start — NavController is already ready
    extractDeepLinkChecklistId(intent)?.let { id ->
        navigator.navigate(AppNavRoute.ChecklistDetail(id))
        analyticsTracker.track("reminder_notification_tapped", mapOf(
            "checklist_id" to id.toString()
        ))
    }
}

private fun extractDeepLinkChecklistId(intent: Intent): Long? {
    if (intent.action != "com.antonchuraev.aichecklists.action.OPEN_CHECKLIST") return null
    val id = intent.getLongExtra("navigate_to_checklist", -1L)
    return if (id != -1L) id else null
}
```

**Consuming pending deep link in Compose (after NavController is ready):**

```kotlin
// Inside setContent { }, after NavHost is set up:
LaunchedEffect(Unit) {
    (context as? MainActivity)?.pendingChecklistId?.let { id ->
        navigator.navigate(AppNavRoute.ChecklistDetail(id))
        analyticsTracker.track("reminder_notification_tapped", mapOf(
            "checklist_id" to id.toString()
        ))
        (context as? MainActivity)?.pendingChecklistId = null
    }
}
```

> **Research insight (Spec Flow Analyzer):** Use `TaskStackBuilder` in the notification PendingIntent (see ReminderReceiver code above) to ensure Back button navigates to Main screen, not exits the app.

### 13. Localization (strings.xml)

```xml
<!-- Reminder Bottom Sheet -->
<string name="reminder_set_reminder">Set Reminder</string>
<string name="reminder_in_one_hour">In 1 hour</string>
<string name="reminder_tomorrow_morning">Tomorrow morning</string>
<string name="reminder_tomorrow_evening">Tomorrow evening</string>
<string name="reminder_pick_date_time">Pick date &amp; time…</string>
<string name="reminder_remove">Remove reminder</string>
<string name="reminder_select_time">Select time</string>
<string name="reminder_select_future_time">Please select a future time</string>
<string name="reminder_set_for">Reminder set for %s</string>

<!-- Notification -->
<string name="reminder_notification_channel">Checklist Reminders</string>
<string name="reminder_notification_channel_desc">Reminders for your checklists</string>
<string name="reminder_items_remaining">%d items remaining</string>
<string name="reminder_all_completed">All items completed!</string>
<string name="reminder_notification_tap">Tap to view</string>

<!-- Common -->
<string name="next">Next</string>
```

### 14. Analytics Events

> **Research insight (Spec Flow Analyzer):** Without analytics, cannot measure feature adoption or conversion.

> **Review fix:** Reduced from 7 to 3 events for MVP. `reminder_set` + `reminder_notification_tapped` measure adoption and engagement. `reminder_cancelled` tracks churn. Add granular funnel events (sheet_opened, paywall_shown, past_error) in v2 when user volume justifies it.

| Event | Parameters | When |
|-------|-----------|------|
| `reminder_set` | `checklist_id`, `preset` (in_1_hour / tomorrow_morning / tomorrow_evening / custom) | Reminder saved |
| `reminder_cancelled` | `checklist_id` | "Remove reminder" tapped |
| `reminder_notification_tapped` | `checklist_id` | Notification tapped → app opened |

## Technical Considerations

### Why `setAndAllowWhileIdle()` (inexact) instead of `setExactAndAllowWhileIdle()`?

> **Research insight (Security Sentinel + Best Practices):**

- `SCHEDULE_EXACT_ALARM` is **denied by default** on Android 14+ (API 34+) for newly installed apps targeting API 33+
- Google Play requires justification for exact alarms — a checklist app doesn't qualify as an alarm/clock app
- `setAndAllowWhileIdle()` fires within a ~few minute window — perfectly acceptable for "remind me about my grocery list"
- No runtime permission dialog needed, no Settings redirect, no `canScheduleExactAlarms()` check
- Battery-friendly: no special exemptions from Doze needed

### Why not a separate Room table for reminders?

- YAGNI: one reminder per checklist → a nullable column is sufficient
- No need for reminder history, status tracking, or multiple reminders per checklist (yet)
- If recurring checklists are added later (Priority 2 in IDEAS_BACKLOG), extract to a separate table then

### Cascade deletion safety

> **Research insight (Learnings — room-cascade-delete-flow-race-condition):**

When a checklist is deleted:
1. Cancel the data-loading job (`loadDataJob?.cancel()`) to prevent race condition
2. Cancel the pending alarm via `ReminderScheduler.cancel(checklistId)`
3. Call `repository.deleteChecklist()`
4. The `reminderAt` column value is deleted with the row

### DatePicker UTC caveat

> **Research insight (Framework Docs):**

`DatePickerState.selectedDateMillis` returns **UTC midnight** of the selected date. If used directly for scheduling, the alarm fires at midnight UTC (wrong in most timezones). Always use `combinePickerResults()` to re-interpret the date in the local timezone combined with the selected time.

### BroadcastReceiver access to repository

> **Research insight (Learnings — datastore-multiple-instances-crash):**

`ReminderReceiver` accesses `ChecklistRepository` via Koin's `GlobalContext.get().get()` — the same pattern used by the widget's `ToggleItemAction`. Never instantiate the repository directly in a receiver, as two OS process contexts accessing the same Room database without a singleton guard can crash.

## Acceptance Criteria

- [ ] Bell icon appears in ChecklistDetail toolbar (outlined = no reminder, filled = reminder set)
- [ ] Bell icon has accessible `contentDescription` reflecting current state
- [ ] Bell icon toggles immediately after saving/removing (optimistic state update)
- [ ] Tapping bell opens bottom sheet with 3 presets + custom date/time option
- [ ] "Pick date & time" opens DatePicker → then TimePicker sequentially
- [ ] Selecting a preset or custom time saves reminder and closes sheet
- [ ] Reminder subtitle ("Tomorrow, 9:00 AM") appears below checklist name
- [ ] Push notification fires at approximately the scheduled time with checklist name + items remaining
- [ ] Item count in notification is computed at fire time (not scheduling time)
- [ ] Tapping notification opens ChecklistDetail — both cold start and warm start
- [ ] Cold-start deep link deferred until NavController is ready (no race condition)
- [ ] Reminder auto-clears from DB after notification fires
- [ ] Second tap on filled bell re-opens sheet with "Remove reminder" option
- [ ] "Remove reminder" clears the reminder and cancels the alarm
- [ ] Deleting a checklist cancels its pending alarm (via `reminderScheduler.cancel()`)
- [ ] Free users can set 1 active reminder total; attempting more shows paywall
- [ ] Premium users can set unlimited reminders
- [ ] Past date/time selection shows error Snackbar
- [ ] Notification permission is requested on Android 13+ before first reminder
- [ ] Active reminders are re-scheduled on app startup (handles app update clearing alarms)
- [ ] DB migration v3→v4 preserves all existing user checklists (no data loss)
- [ ] `toDomain()`/`toEntity()` mappings include `reminderAt` (editing checklist does not erase reminder)
- [ ] 3 analytics events fire: `reminder_set`, `reminder_cancelled`, `reminder_notification_tapped`

## Success Metrics

| Metric | Target |
|--------|--------|
| Reminder set rate | >20% of active users set at least 1 reminder |
| Notification tap-through rate | >30% of fired notifications are tapped |
| D7 retention lift | +5% vs control (users with reminders vs without) |
| Premium conversion from reminder limit | >3% of free users hitting limit convert |

## Dependencies & Risks

| Dependency | Risk | Mitigation |
|------------|------|------------|
| `POST_NOTIFICATIONS` permission | User may deny (Android 13+) | Degrade gracefully: save reminder, show Settings link |
| Inexact alarm timing | Fires within ~minutes, not seconds | Acceptable for checklist reminders; document in UX |
| DB migration v3→v4 | Data loss if migration fails | Manual `ALTER TABLE` is atomic in SQLite; `fallbackToDestructiveMigration` as safety net |
| App update clears alarms | Users lose reminders silently | Re-schedule from DB in `Application.onCreate()` |
| PendingIntent requestCode | Long→Int collision | XOR-folding prevents collision for practical ID ranges |

## Future Iterations (post-MVP)

- [ ] `BootReceiver` + `RECEIVE_BOOT_COMPLETED` — re-schedule alarms after device reboot
- [ ] Main screen checklist card bell indicator
- [ ] Notification snooze action button
- [ ] Reminder during checklist creation (CreateChecklist flow)
- [ ] Custom notification sound
- [ ] Recurring checklists (Priority 2 in IDEAS_BACKLOG) — builds on reminder infrastructure

## References

### Internal
- Bottom sheet pattern: `feature/home/.../ChecklistDetailScreen.kt:588-665`
- Note dialog pattern: `feature/home/.../ChecklistDetailScreen.kt:465-497`
- Item card UI: `feature/home/.../ChecklistDetailScreen.kt:408-463`
- MVI contract: `feature/home/.../ChecklistDetailScreenContract.kt`
- DB entities: `feature/checklist/.../data/db/ChecklistEntity.kt`
- Widget receiver pattern: `composeApp/src/androidMain/.../widget/ChecklistWidgetReceiver.kt`
- Widget Koin access: `composeApp/src/androidMain/.../widget/actions/ToggleItemAction.kt`
- Cascade delete safety: `docs/solutions/database-issues/room-cascade-delete-flow-race-condition.md`
- Singleton DB access: `docs/solutions/runtime-errors/datastore-multiple-instances-crash.md`
- MVI state-driven dialogs: `docs/solutions/architecture/mvi-pattern.md`
- BottomSheet pattern: `docs/solutions/features/csat-survey-with-in-app-review.md`
- Paywall limit pattern: `docs/solutions/features/paywall-best-practices.md`
- Design system: `docs/solutions/ui/design-system-patterns.md`
- Ideas backlog: `docs/IDEAS_BACKLOG.md` (Priority 1)

### External
- [AlarmManager — Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule)
- [Android 14: Exact alarms denied by default](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [POST_NOTIFICATIONS permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [Notification channels](https://developer.android.com/develop/ui/views/notifications/channels)
- [DatePicker — Compose Multiplatform](https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-date-picker.html)
- [TimePicker — Compose Multiplatform](https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-time-picker.html)
- [ModalBottomSheet — Compose Multiplatform](https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-modal-bottom-sheet.html)
- [kotlinx-datetime — timezone-aware arithmetic](https://github.com/kotlin/kotlinx-datetime)
- [Room KMP migration guide](https://developer.android.com/kotlin/multiplatform/room)
- [TaskStackBuilder for notifications](https://developer.android.com/reference/android/app/TaskStackBuilder)
- [BroadcastReceiver.goAsync()](https://developer.android.com/develop/background-work/background-tasks/broadcasts#effects-on-process-state)
- [Alarmee KMP library (alternative)](https://github.com/Tweener/alarmee)
