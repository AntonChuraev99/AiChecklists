---
paths:
  - "**/feature/checklist/**"
  - "**/*Checklist*.kt"
  - "**/*Fill*.kt"
  - "**/*Reminder*.kt"
---

# Checklist domain — template vs fill, reminders, KMP constraints

## Template vs Fill

`Checklist` (template) defines items; `ChecklistFill` stores checked/note state per session (also `coverImagePath`). The default fill mirrors the template.

**When adding items from the detail screen, update BOTH:**

```kotlin
repository.updateFill(updatedFill)                    // fill — for detail screen
repository.updateChecklistTemplate(updatedChecklist)  // template — for edit screen
```

- `updateChecklist()` — updates template AND re-syncs fill (regenerates all fill item IDs). Use for Edit-screen saves.
- `updateChecklistTemplate()` — updates template ONLY, no fill sync. Use when the fill is already updated separately.

## Reminders

Checklist model reminder/preference fields:

```kotlin
val reminderAt: Long? = null,               // One-shot reminder timestamp
val repeatRule: ReminderRepeatRule? = null, // Recurring schedule
val repeatTimeOfDayMinutes: Int? = null,    // Time of day for repeat
val repeatNextAt: Long? = null,             // Next repeat fire time
val repeatOccurrenceCount: Int = 0,         // How many times repeated
val separateCompleted: Boolean = false,     // Group completed items separately
val position: Int = 0,                       // Drag-and-drop ordering
val autoDeleteCompleted: Boolean = false     // Auto-remove checked items
```

`ReminderRepeatRule` (`feature/checklist/domain/model/`): Daily, Weekly, Monthly, Weekdays, Biweekly, Quarterly, Yearly, Custom. Android impl: `ReminderReceiver`, `ReminderScheduler` (AlarmManager), `BootCompletedReceiver`. Free users: 1 recurring reminder. <!-- docs-leak-scan: reviewed — repeat-rule enum, no audience figure -->

## KMP platform constraints — verify before using in `commonMain`

| API | Status in KMP | Alternative |
|-----|---------------|-------------|
| `BackHandler` | Android-only (`activity-compose`) | `WindowInsets.ime` for keyboard back |
| `onFocusChanged` for keyboard hide | Focus stays `true` when keyboard hides | Track `WindowInsets.ime.getBottom()` |
| `Switch` + `Row(clickable)` | Double-toggle bug | `AppSwitch(onCheckedChange = null)`, Row handles click |

Per-entity preferences (`separateCompleted`, `autoDeleteCompleted`, `position`) belong in **Room, not DataStore**. DataStore is for global app preferences only.

## Database

Room 3.0 with KSP across all targets. Database classes in `feature:checklist`. Platform `DatabaseBuilder` via expect/actual. Web uses `WebWorkerSQLiteDriver` over OPFS (survives reload); Android/iOS use bundled SQLite driver.
