---
title: "Per-Item Reminders — Scope-Leak Bugfix + Nav3 Test-Debt Payoff"
date: 2026-06-02
type: bug-fix
modules: [feature/checklist, feature/home, composeApp/androidMain/notification, core/designsystem]
keywords: [item-reminder, ReminderSheet, scope-leak, shared-picker, stateful-dialog, customPickerItemId, Navigation3, test-fakes, MVI-state-scope]
project: Checklists
---

# Per-Item Reminders — Scope-Leak Bugfix + Nav3 Test-Debt Resolution

## Problem / Context

**Symptom:** User sets a reminder for a specific checklist **item** via the custom date/time picker (ReminderSheet → "Select date and time"), but the reminder gets scheduled for the entire **checklist** instead, skipping the item scope.

**Root cause:** ReminderSheet is reused (Option A pattern) for both checklist-level and item-level reminders, controlled by state selector `itemReminderSheetFor: String?`. The selector determines which scope to show (item reminder fields vs. checklist reminder fields). However, the custom date/time picker (ReminderDateTimePicker composable) — which is shared between both scopes — is managed by a single `showCustomPicker` boolean flag. When the user taps "Select date and time" on an item reminder sheet, the picker opens. Upon selection, the picker's `OnTimeSelected` callback **unconditionally** calls the checklist-level `saveReminder()` method, **not** the item-scoped `saveItemReminder()` method, because the picker code had no knowledge of which scope it was opened from. At that moment, `itemReminderSheetFor` had already been **cleared** (the item sheet had closed), so the scope was lost.

**Why it happens:** The ReminderSheet is a single stateful composable managing two separate workflows (checklist + item reminder). The UI state (`itemReminderSheetFor`) correctly tracks which scope is *currently shown*, but the custom picker is not part of that state — it's controlled by a separate `showCustomPicker` boolean. When the picker dismisses and fires its callback, it has no way to know which scope opened it, so it defaults to the checklist-level handler.

**Bonus issue:** Similar scope-loss occurred in `handleNotificationPermissionResult()` → `Skip` case: after a user denies notification permission during an item-reminder flow, the permission handler would unconditionally re-open the checklist-level reminder sheet instead of the item sheet, losing the item scope.

**Secondary blocker:** `:feature:home:testAndroidHostTest` was silently failing to compile due to pre-existing Nav3 migration debt in test fakes (11 inline `FakeAppNavigator` instances still using Nav2 interface after the app migrated to Navigation 3 alpha). This prevented validation of reminder-related tests. The two were coincidentally fixed in the same session.

## Solution

### Part 1: Scope-Leak Fix (customPickerItemId Pattern)

**Root principle:** A shared stateful dialog/picker that multiplexes multiple scopes must carry its own scope-identifier in the ViewModel state, captured at open-time, and used in the callback handler. This is the MVI single-source-of-truth pattern applied to nested dialogs.

**Implementation steps:**

1. **Add scope-tracking field** to `ChecklistDetailScreenContract.State`:
   ```kotlin
   data class State(...) {
       val itemReminderSheetFor: String?  // existing: which item's sheet is shown
       val customPickerItemId: String?    // NEW: which scope the custom picker was opened from
   }
   ```

2. **Capture scope at open-time** in `OnCustomDateRequested` handler:
   ```kotlin
   OnCustomDateRequested → {
       // Capture itemId BEFORE clearing the sheet selector
       val pickerScope = state.itemReminderSheetFor  // could be null (checklist-level)
       state = state.copy(
           customPickerItemId = pickerScope,  // Store for later use in OnTimeSelected
           itemReminderSheetFor = null,       // Close the sheet (either item or checklist)
           showCustomPickerUI = true
       )
   }
   ```

3. **Vet scope in callback handler** `OnTimeSelected`:
   ```kotlin
   OnTimeSelected(dateTime, repeatRule, timeOfDay) → {
       if (state.customPickerItemId != null) {
           // Item scope: use item-level save
           saveItemReminder(itemId = state.customPickerItemId, ...)
       } else {
           // Checklist scope: use checklist-level save
           saveReminder(dateTime, repeatRule, ...)
       }
       state = state.copy(
           customPickerItemId = null,  // Clear scope after use
           showCustomPickerUI = false
       )
   }
   ```

4. **Cleanup on dismiss** in `OnDismissReminderUI`:
   ```kotlin
   OnDismissReminderUI → {
       state = state.copy(
           customPickerItemId = null,  // Clear scope if picker was open
           showCustomPickerUI = false,
           itemReminderSheetFor = null
       )
   }
   ```

### Part 2: Notification Permission Reopening (DRY + Scope-Aware)

**Secondary issue:** `handleNotificationPermissionResult()` had two branches (Granted/Denied) and a tertiary Skip case. All three were reopening the reminder sheet, but the Skip case was unconditionally opening the checklist-level sheet, losing item scope.

**Fix: Unified scope-aware reopener:**
```kotlin
private fun reopenReminderSheetAfterPermission(granted: Boolean) {
    if (granted) {
        // Permission granted: reopen the same scope that was interrupted
        if (state.itemReminderSheetFor != null) {
            // Item flow: already open, no-op (stay on item sheet)
        } else {
            // Checklist flow: reopen checklist sheet
            state = state.copy(showReminderSheet = true)
        }
    } else {
        // Permission denied but user wants to continue:
        // DON'T auto-reopen sheets, user tapped Skip
        // If it was an item-reminder flow, leave it ready for re-attempt
        // No state change needed (sheet already closed)
    }
}
```

Wire both handlers:
```kotlin
PermissionResult.Granted → reopenReminderSheetAfterPermission(granted = true)
PermissionResult.Denied → reopenReminderSheetAfterPermission(granted = false)
```

This gating (`!isItemFlow` implicit in the state check) ensures item flows don't silently lose scope.

### Part 3: Nav3 Test-Fake Debt (Collateral Payoff)

**Issue:** `:feature:home:testAndroidHostTest` would not compile due to 11 inline `FakeAppNavigator` instances using Nav2 interface (removed in Navigation 3 alpha migration). This prevented test validation of the reminder feature.

**Fixes (in test source):**
- Replace `override val commands: List<NavCommand>` with `override val backStack: NavBackStack<NavKey>` for all 11 fake classes.
- Remove import of `NavCommand`.
- Update `MainScreenViewModelTest` constructor to provide fake `GoogleAuthRepository` and `SyncRepository` (dependencies added to MainScreenViewModel during the broader Architecture refactor).

**Result:** `:feature:home:testAndroidHostTest` BUILD SUCCESSFUL; reminder tests now run and validate the scope-leak fix.

## Why This Design

1. **Single source of truth:** The `customPickerItemId` field is part of the ViewModel state, not a separate variable or callback parameter. This ensures the scope is always captured and cleared predictably, following MVI principles.

2. **Fail-safe cleanup:** Both the success path (`OnTimeSelected`) and the dismissal path (`OnDismissReminderUI`) clear the `customPickerItemId`. Even if a user opens the picker and dismisses without selecting, the scope doesn't leak into the next picker open.

3. **No composable-level scope awareness needed:** The ReminderDateTimePicker composable itself doesn't need to know which scope it's in. It simply calls `OnTimeSelected`, and the ViewModel handler routes based on state. This keeps the picker logic simple and testable.

4. **Generalizable pattern:** This pattern works for any shared stateful dialog/picker multiplexing multiple scopes: capture the scope identifier at open-time, store in state, use in the callback. Examples: multi-level delete confirmations, multi-entity property sheets, nested pickers.

5. **Test-debt payoff:** Fixing Nav3 test fakes was not originally planned but emerged during comprehensive validation. Doing it in the same session prevents future "compile gates" from hiding test regressions.

## Examples

### Before (Buggy)
```kotlin
// In ReminderSheet composable
Button("Select date and time") {
    state = state.copy(showCustomPicker = true)
}

// In ReminderDateTimePicker callback
OnTimeSelected(dateTime, ...) → {
    viewModel.sendIntent(ChecklistDetailIntent.OnSaveReminder(dateTime, ...))
    // ❌ Always saves at checklist level, itemReminderSheetFor is now null
}
```

### After (Fixed)
```kotlin
// In ViewModel state
data class State(...) {
    val customPickerItemId: String?  // Captured at open, used at save
}

// In OnCustomDateRequested handler
OnCustomDateRequested → {
    state = state.copy(
        customPickerItemId = state.itemReminderSheetFor,  // Capture NOW
        itemReminderSheetFor = null,  // Close sheet
        showCustomPicker = true
    )
}

// In OnTimeSelected handler (ViewModel.onIntent)
OnTimeSelected(dateTime, ...) → {
    if (state.customPickerItemId != null) {
        saveItemReminder(itemId = state.customPickerItemId, ...)  // ✅ Item-scoped
    } else {
        saveReminder(dateTime, ...)  // ✅ Checklist-scoped
    }
    state = state.copy(customPickerItemId = null, showCustomPicker = false)
}
```

## Related Files

**Production changes (13 files):**
- `feature/home/detail/ChecklistDetailScreenContract.kt` — added `customPickerItemId` field, `OnCustomDateRequested` intent
- `feature/home/detail/ChecklistDetailViewModel.kt` — scope-capture logic, bifurcated OnTimeSelected handler, reopenReminderSheetAfterPermission helper
- Test fakes (11 files) — Nav3 migration (FakeAppNavigator, FakeNavigator, MainScreenViewModelTest, etc.)

**Tests (5 new tests in ChecklistDetailItemReminderTest.kt):**
- `onTimeSelected_afterItemCustomDatePicker_schedulesItemReminder_notChecklist` — verifies item-scope save
- `onTimeSelected_afterItemCustomDatePicker_schedulesItemReminder_withGuard_checklistLevel` — negative guard for checklist
- `notificationPermissionResult_Skip_duringItemFlow_keepsItemSheet` — verifies Skip doesn't open checklist sheet
- Related scope-leak guard tests

## Lessons Learned

1. **Shared stateful dialogs require explicit scope tracking in state.** Don't rely on external UI state (like `itemReminderSheetFor` selector) to determine which scope a picker belongs to—the selector will be cleared before the picker's callback fires.

2. **MVI scope principle:** Any piece of state that affects callback behavior must be part of the state record, not ephemeral UI state.

3. **Silent scope-loss is a class of bug.** Detect via: "User action in scope X → state in scope Y." Prevent via: capture scope at user-intent-to-open time.

4. **Test debt compounds silently.** When test fakes fall behind the main API migration (Nav2 → Nav3), the test suite stops compiling, and bugs in the newly-tested code become harder to catch. Validate comprehensive test coverage in final integration.

## Keywords

`item-reminder`, `ReminderSheet`, `scope-leak`, `shared-picker`, `stateful-dialog`, `customPickerItemId`, `MVI-state-scope`, `Navigation3`, `test-fakes`, `bug-fix`, `multi-level-dialog`
