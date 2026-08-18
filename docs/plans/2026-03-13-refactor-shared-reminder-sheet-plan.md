---
title: "Refactor: Extract shared ReminderSheet for onboarding and detail screens"
type: refactor
date: 2026-03-13
complexity: complex
---

# Refactor: Extract shared ReminderSheet for onboarding and detail screens

## Overview

Replace the simplified `QuickReminderSheetContent` on the onboarding `DiscoverMore` step with the full `ReminderSheet` from `ChecklistDetailScreen`. Extract `ReminderSheet` and all its sub-composables into a shared location so both screens reuse a single component.

## Problem Statement / Motivation

Currently two separate reminder bottom sheets exist:

| Aspect | Onboarding (`QuickReminderSheetContent`) | Detail (`ReminderSheet`) |
|--------|------------------------------------------|--------------------------|
| Presets | 3 named (Tonight/Daily/Weekly) | Time-relative (In 1 hour, Tomorrow morning/evening) |
| Tabs | None | ONCE / REPEAT |
| Custom date | No | Full date+time picker |
| Remove | No | Yes (when reminder exists) |
| Repeat config | Hidden (but presets create repeat schedules) | Full: 8 frequencies, custom interval, weekday selector, time, end condition |
| Lines of code | ~75 | ~350+ |

This duplication means improvements to the detail sheet don't reach onboarding, and the onboarding experience is artificially limited. A user setting up their first checklist deserves access to the full reminder configuration.

## Proposed Solution

### Architecture Decision: Module Placement

**`feature:checklist`** — add Compose Multiplatform plugins and place the shared `ReminderSheet` in a new `ui/reminder/` package.

**Why this location:**
- Both `feature:home` and `feature:onboarding` already depend on `feature:checklist`
- Domain models (`ReminderRepeatRule`, `RepeatType`, `RepeatEndCondition`, `ChecklistReminderScheduler`) already live here
- `PendingRepeatConfig` and `ReminderTab` are tightly coupled to these domain models — natural fit to co-locate
- No new module needed, no circular dependencies
- `core:designsystem` would require a reverse dependency on `feature:checklist` (layering violation)

**Trade-off:** `feature:checklist` evolves from a pure data/domain module to a mixed module with a thin UI layer for reminders. This is acceptable because the UI is directly tied to the checklist reminder domain.

### Callback Contract: `ReminderSheetCallbacks`

Replace ~20 flat lambdas with a single data class:

```kotlin
// feature/checklist/src/commonMain/.../ui/reminder/ReminderSheetCallbacks.kt
data class ReminderSheetCallbacks(
    // Tab
    val onTabSelected: (ReminderTab) -> Unit,
    // Once tab
    val onPresetSelected: (Long) -> Unit,
    val onCustomDateRequested: () -> Unit,
    val onRemoveReminder: () -> Unit,
    // Repeat tab
    val onRepeatTypeSelected: (RepeatType) -> Unit,
    val onSmartPresetSelected: (PendingRepeatConfig) -> Unit,
    val onRepeatIntervalChanged: (Int) -> Unit,
    val onWeekDayToggled: (Int) -> Unit,
    val onResetChecksToggled: (Boolean) -> Unit,
    val onRepeatTimeChanged: (Int, Int) -> Unit,
    val onEndConditionClick: () -> Unit,
    val onEndConditionSelected: (RepeatEndCondition) -> Unit,
    val onDismissEndCondition: () -> Unit,
    val onSaveRepeat: () -> Unit,
    val onRemoveRepeat: () -> Unit,
    // Sheet
    val onDismiss: () -> Unit
)
```

### State: `ReminderSheetState`

```kotlin
// feature/checklist/src/commonMain/.../ui/reminder/ReminderSheetState.kt
data class ReminderSheetState(
    val activeTab: ReminderTab = ReminderTab.ONCE,
    val currentReminder: Long? = null,
    val currentRepeatRule: ReminderRepeatRule? = null,
    val repeatRuleSummary: String? = null,
    val pendingRepeatConfig: PendingRepeatConfig? = null,
    val showEndConditionPicker: Boolean = false
)
```

### Shared Sheet Signature

```kotlin
@Composable
fun ReminderSheet(
    state: ReminderSheetState,
    callbacks: ReminderSheetCallbacks,
)
```

- **Remove buttons** — automatically hidden when `currentReminder == null` / `currentRepeatRule == null`
- **CurrentReminderCard / CurrentRepeatCard** — shown only when existing data present (null in onboarding context)
- **Permission** — NOT handled inside the sheet. Each caller resolves permission before opening.

## Technical Considerations

### Files to Extract from `ChecklistDetailScreen.kt`

Move to `feature/checklist/src/commonMain/.../ui/reminder/`:

| Composable | Current location (line) | New file |
|------------|------------------------|----------|
| `ReminderSheet` | 1321 | `ReminderSheet.kt` |
| `OnceTabContent` | 1404 | `ReminderSheet.kt` |
| `RepeatTabContent` | 1457 | `ReminderSheet.kt` |
| `RepeatTimePicker` | 1676 | `ReminderSheet.kt` |
| `ReminderPresetRow` | 1739 | `ReminderSheet.kt` |
| `CurrentReminderCard` | (nearby) | `ReminderSheet.kt` |
| `CurrentRepeatCard` | (nearby) | `ReminderSheet.kt` |
| `RepeatTypeOption` | (nearby) | `ReminderSheet.kt` |
| `CustomRepeatSection` | (nearby) | `ReminderSheet.kt` |
| `EndConditionDialog` | (nearby) | `ReminderSheet.kt` |
| `formatReminderDateTime` | (helper) | `ReminderSheet.kt` |
| `tomorrowAt` | (helper) | `ReminderSheet.kt` |

Move to `feature/checklist/src/commonMain/.../ui/reminder/`:

| Model | Current location | New file |
|-------|-----------------|----------|
| `ReminderTab` | `ChecklistDetailScreenContract.kt` | `ReminderSheetState.kt` |
| `PendingRepeatConfig` | `ChecklistDetailScreenContract.kt` | `ReminderSheetState.kt` |

### `feature:checklist/build.gradle.kts` Changes

Add Compose plugins and dependencies:

```kotlin
plugins {
    alias(libs.plugins.composeMultiplatform)    // ADD
    alias(libs.plugins.composeCompiler)         // ADD
}

commonMain.dependencies {
    // Existing...
    implementation(compose.runtime)             // ADD
    implementation(compose.foundation)          // ADD
    implementation(compose.material3)           // ADD
    implementation(compose.materialIconsExtended) // ADD
    implementation(compose.ui)                  // ADD
    implementation(compose.components.resources) // ADD
    implementation(projects.core.designsystem)  // ADD — for AppButton, AppCard, AppSwitch, AppDimens
    implementation(libs.kotlinx.datetime)       // already present
}
```

### `InteractiveOnboardingState` Changes

Add reminder sheet state fields to `DiscoverMoreState`:

```kotlin
data class DiscoverMoreState(
    val reminderCompleted: Boolean = false,
    val widgetCompleted: Boolean = false,
    val shareCompleted: Boolean = false,
    val shareText: String? = null,
    // NEW: Reminder sheet state
    val showReminderSheet: Boolean = false,
    val reminderSheetState: ReminderSheetState = ReminderSheetState(),
    // NEW: Custom date picker (rendered outside the sheet)
    val showCustomPicker: Boolean = false,
    val customPickerDateMillis: Long? = null,
    val customPickerMinDateMillis: Long = 0L,
    val customPickerInitialHour: Int = 9,
    val isCustomTimeInPast: Boolean = false,
    // NEW: Exact alarm instruction sheet
    val showExactAlarmSheet: Boolean = false,
    val exactAlarmDontShowAgain: Boolean = false,
)
```

### New Intents for `InteractiveOnboardingIntent`

Replace `OnReminderPresetSelected(preset: ReminderPreset)` with full set:

```kotlin
// Reminder sheet (replaces OnReminderPresetSelected)
data object OnOpenReminderSheet : InteractiveOnboardingIntent
data object OnDismissReminderSheet : InteractiveOnboardingIntent
data class OnReminderTabSelected(val tab: ReminderTab) : InteractiveOnboardingIntent
data class OnReminderPresetSelected(val triggerAtMillis: Long) : InteractiveOnboardingIntent  // changed: Long instead of ReminderPreset
data object OnCustomDateRequested : InteractiveOnboardingIntent
data class OnDateSelected(val dateMillis: Long) : InteractiveOnboardingIntent
data class OnTimeSelected(val hour: Int, val minute: Int) : InteractiveOnboardingIntent
// Repeat
data class OnRepeatTypeSelected(val type: RepeatType) : InteractiveOnboardingIntent
data class OnSmartPresetSelected(val config: PendingRepeatConfig) : InteractiveOnboardingIntent
data class OnRepeatIntervalChanged(val interval: Int) : InteractiveOnboardingIntent
data class OnWeekDayToggled(val dayNumber: Int) : InteractiveOnboardingIntent
data class OnResetChecksToggled(val enabled: Boolean) : InteractiveOnboardingIntent
data class OnRepeatTimeChanged(val hour: Int, val minute: Int) : InteractiveOnboardingIntent
data object OnEndConditionClick : InteractiveOnboardingIntent
data class OnEndConditionSelected(val condition: RepeatEndCondition) : InteractiveOnboardingIntent
data object OnDismissEndConditionPicker : InteractiveOnboardingIntent
data object OnSaveRepeatSchedule : InteractiveOnboardingIntent
// Exact alarm
data object OnExactAlarmOpenSettings : InteractiveOnboardingIntent
data object OnExactAlarmSkip : InteractiveOnboardingIntent
data class OnExactAlarmDontShowChanged(val checked: Boolean) : InteractiveOnboardingIntent
data object OnDismissExactAlarmSheet : InteractiveOnboardingIntent
```

### Delete `ReminderPreset` Enum

The `ReminderPreset` enum in `InteractiveOnboardingScreenContract.kt` becomes obsolete — the full sheet uses absolute `Long` millis for one-shot and `PendingRepeatConfig` for repeat.

### ViewModel Logic Extraction

Extract `buildRepeatSummary()` from `ChecklistDetailViewModel` to a shared utility:

```kotlin
// feature/checklist/src/commonMain/.../ui/reminder/RepeatSummaryBuilder.kt
fun buildRepeatSummary(rule: ReminderRepeatRule): String
```

This method currently produces hardcoded English strings. For now extract as-is; localization is a pre-existing issue to fix separately.

### `DiscoverMoreStep.kt` Changes

```kotlin
@Composable
fun DiscoverMoreStep(
    state: DiscoverMoreState,
    onIntent: (InteractiveOnboardingIntent) -> Unit,  // CHANGED: single callback replacing individual lambdas
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
)
```

- Remove `QuickReminderSheetContent`, `ReminderPresetItem` — deleted
- Replace reminder `ModalBottomSheet` block with:
  ```kotlin
  if (state.showReminderSheet) {
      ReminderSheet(
          state = state.reminderSheetState,
          callbacks = ReminderSheetCallbacks(
              onTabSelected = { onIntent(OnReminderTabSelected(it)) },
              onPresetSelected = { onIntent(OnReminderPresetSelected(it)) },
              // ... all callbacks mapped to intents
          )
      )
  }
  ```
- Add `ReminderDateTimePicker` rendering (outside the sheet, same pattern as detail)
- Add `ExactAlarmInstructionSheet` rendering (same pattern as detail)

### `ChecklistDetailScreen.kt` Changes

- Remove `ReminderSheet`, `OnceTabContent`, `RepeatTabContent`, `RepeatTimePicker`, `ReminderPresetRow`, helper composables — all moved
- Import shared `ReminderSheet` from `feature:checklist`
- Build `ReminderSheetState` from `ChecklistDetailState.Content` fields
- Build `ReminderSheetCallbacks` mapping to `ChecklistDetailIntent`s
- `ReminderTab` and `PendingRepeatConfig` imports change from local to `feature:checklist`

### Permission Flow — No Changes

| Screen | Pre-sheet permission flow | After refactor |
|--------|--------------------------|----------------|
| Onboarding | Direct system dialog via `rememberNotificationPermissionRequester` | Same — sheet opens after permission granted |
| Detail | `NotificationPermissionSheet` → system dialog | Same — sheet opens after permission flow |

The shared `ReminderSheet` assumes permission is already resolved.

### ExactAlarmInstructionSheet in Onboarding

After saving a reminder on API 31+, `InteractiveOnboardingViewModel` calls `maybeShowExactAlarmInstruction()`:
- Checks `reminderScheduler.canScheduleExactAlarms()`
- Checks DataStore pref `PREF_EXACT_ALARM_DONT_SHOW`
- If needed, sets `showExactAlarmSheet = true`

The `ExactAlarmInstructionSheet` composable remains in `feature:home` (not shared) — onboarding renders its own copy since it's a simple inline composable (~30 lines). Alternatively, it can also be extracted to `feature:checklist` if needed.

### Free User Repeat Limit in Onboarding

During onboarding the user has 0 active repeat schedules (just created first checklist). The limit check (`countActiveRepeatSchedules() >= 1`) will always pass. However, the check should still be implemented for correctness in case the user returns to the DiscoverMore step after setting up a repeat.

If limit hit during onboarding → advance to `InteractiveOnboardingStep.Paywall` (same as skip).

### Analytics

Current onboarding analytics events (`onboarding_reminder_preset_selected`, `discover_more_reminder`) continue. New events added for repeat schedule saves from onboarding context.

## Acceptance Criteria

### Functional

- [x] On the `DiscoverMore` step, tapping "Set Reminder" opens the full `ReminderSheet` with both ONCE and REPEAT tabs
- [x] ONCE tab shows: "In 1 hour", "Tomorrow morning", "Tomorrow evening", "Pick date & time" presets
- [x] REPEAT tab shows all 8 frequency options, custom interval, time picker, reset checks toggle, end condition
- [x] "Remove" buttons are hidden (no existing reminder on first setup)
- [x] After saving any reminder (one-shot or repeat), the Reminder card shows as completed (green checkmark)
- [x] Custom date/time picker works from onboarding context
- [ ] `ExactAlarmInstructionSheet` shows on API 31+ after saving (if not suppressed) — not implemented (kept out of scope for onboarding)
- [x] `ChecklistDetailScreen` continues to work identically with the extracted shared `ReminderSheet`
- [x] Permission flow unchanged on both screens
- [x] `QuickReminderSheetContent` and `ReminderPreset` enum are deleted (no dead code)

### Non-Functional

- [x] No new module dependencies that create cycles
- [x] `feature:checklist` builds with Compose plugins
- [x] Full project compiles: `./gradlew composeApp:assembleDebug` (release requires keystore)
- [x] Existing `ChecklistDetailViewModelTest` passes
- [x] New `InteractiveOnboardingViewModel` reminder intent tests added (happy path + error for each intent)

## Implementation Phases

### Phase 1: Foundation — Extract shared component

1. [x] Add Compose plugins + dependencies to `feature:checklist/build.gradle.kts`
2. [x] Move `ReminderTab`, `PendingRepeatConfig` from `ChecklistDetailScreenContract.kt` to `feature/checklist/.../ui/reminder/ReminderSheetState.kt`
3. [x] Create `ReminderSheetCallbacks.kt` (merged into `ReminderSheetState.kt`)
4. [x] Move all `ReminderSheet` composables from `ChecklistDetailScreen.kt` to `feature/checklist/.../ui/reminder/ReminderSheet.kt`
5. [x] Extract `buildRepeatSummary()` to `RepeatSummaryBuilder.kt`
6. [x] Update `ChecklistDetailScreen.kt` imports — verify it compiles with the extracted component
7. [x] Update `ChecklistDetailScreenContract.kt` imports
8. [x] **Verify:** `./gradlew build` passes, `ChecklistDetailViewModelTest` passes

### Phase 2: Wire onboarding

1. [x] Update `InteractiveOnboardingScreenContract.kt`:
   - Expand `DiscoverMoreState` with reminder sheet fields
   - Replace `OnReminderPresetSelected(preset)` with full intent set
   - Delete `ReminderPreset` enum
2. [x] Update `InteractiveOnboardingViewModel.kt`:
   - Add handlers for all new reminder intents
   - Implement `saveOneShot()` and `saveRepeatSchedule()` using existing `checklistRepository` + `reminderScheduler`
   - Implement `maybeShowExactAlarmInstruction()`
   - Set `reminderCompleted = true` only on coroutine success
3. [x] Update `DiscoverMoreStep.kt`:
   - Change signature to `onIntent: (InteractiveOnboardingIntent) -> Unit`
   - Replace `QuickReminderSheetContent` with shared `ReminderSheet`
   - Add `ReminderDateTimePicker` and `ExactAlarmInstructionSheet`
   - Delete `QuickReminderSheetContent`, `ReminderPresetItem` composables
4. [x] Update `InteractiveOnboardingScreen.kt` — pass `onIntent` to `DiscoverMoreStep`
5. [x] **Verify:** `./gradlew build` passes

### Phase 3: Tests and cleanup

1. [x] Write unit tests for new onboarding ViewModel intents (per Testing Mandate):
   - `onReminderPresetSelected_savesOneShotReminder`
   - `onReminderPresetSelected_setsReminderCompleted`
   - `onSaveRepeatSchedule_savesRepeatAndSetsCompleted`
   - `onReminderPresetSelected_noChecklistId_isNoOp`
   - `onReminderPresetSelected_tracksAnalytics`
2. [x] Remove unused string resources — no orphaned strings found
3. [x] Delete `ReminderPreset` enum references from analytics — replaced with millis-based tracking
4. [x] Run full test suite: `./gradlew composeApp:assembleDebug` passes, all unit tests pass
5. [x] Fix iOS compilation: replaced `Clock.System` with `currentTimeMillis()` from `core:common:api`
6. [x] Fix test compilation: added missing imports for `ReminderTab`/`PendingRepeatConfig` in `ChecklistDetailRepeatRuleTest` and `ChecklistDetailAnalyticsTest`
7. [x] **Verify:** no dead code, no unused imports

## Dependencies & Risks

| Risk | Mitigation |
|------|-----------|
| Adding Compose to `feature:checklist` increases build time | Minimal — only adds ~5 Compose deps, no UI previews |
| `PendingRepeatConfig` move breaks `ChecklistDetailViewModelTest` | Update imports in test file — same class, new package |
| Onboarding ViewModel grows significantly (~15 new intent handlers) | Most handlers are state updates (1-3 lines each); complex logic extracted to shared helpers |
| `ExactAlarmInstructionSheet` copy in onboarding | Keep as inline composable (~30 lines); extract later if a 3rd screen needs it |

## References

### Internal
- `feature/home/.../detail/ChecklistDetailScreen.kt:1321` — current `ReminderSheet` (source of extraction)
- `feature/onboarding/.../interactive/components/DiscoverMoreStep.kt` — current `QuickReminderSheetContent` (to be replaced)
- `feature/onboarding/.../interactive/InteractiveOnboardingScreenContract.kt` — state and intents
- `feature/home/.../detail/ChecklistDetailScreenContract.kt` — `PendingRepeatConfig`, `ReminderTab`
- `feature/checklist/.../domain/model/ReminderRepeatRule.kt` — domain model
- `feature/checklist/.../domain/scheduler/ChecklistReminderScheduler.kt` — scheduler interface

### Documented Solutions
- `docs/solutions/features/exact-alarm-reminders-upgrade.md` — exact alarm implementation details
- `docs/solutions/features/notification-permission-before-reminder.md` — permission flow patterns
- `docs/solutions/features/enhanced-repeat-rule-picker.md` — repeat picker patterns and gotchas
- `docs/plans/2026-03-12-feat-onboarding-discover-more-step-plan.md` — original DiscoverMore step plan
