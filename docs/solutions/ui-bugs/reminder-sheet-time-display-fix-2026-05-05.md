---
title: "ReminderSheet Time Display Fix — Uninitialized pendingRepeatConfig"
date: 2026-05-05
type: bug-fix
modules: [feature/home, core/common]
keywords: [reminder-sheet, repeat-rule, time-picker, state-initialization, per-item-reminder, uninitialized-state, modal-sheet]
project: Checklists
---

# ReminderSheet Time Display Bug — Auto-Open Default Tab Initialization

## Symptom

User opens **ReminderSheet** on a checklist item with an existing reminder (e.g., 12:00 daily). Sheet appears with **Repeat tab pre-selected** (default). The CurrentRepeatCard and Time-of-day picker show **09:00** (default/fallback value) instead of the actual saved time (12:00).

External reminder chip on the item card shows the **correct time** (12:00). The bug manifests only inside the sheet's Repeat tab.

User reported via screenshots on both checklist-level and per-item reminder flows.

## Root Causes (Two Similar Code Paths)

### Path 1: Checklist-Level Reminder (`handleReminderClick`)

When `reminderAt != null` or `repeatNextAt != null`, `handleReminderClick` sets:
```kotlin
showReminderSheet = true
defaultTab = ReminderTab.REPEAT  // auto-open to REPEAT tab
```

However, `pendingRepeatConfig` remained `null`. The sheet's `CurrentRepeatCard` composable did:
```kotlin
val config = state.pendingRepeatConfig ?: PendingRepeatConfig()  // fallback to defs
```

This fallback produced 09:00 (default from `PendingRepeatConfig()` constructor).

**Why it happened:** Existing `initRepeatTabIfNeeded()` correctly loaded the config from the saved rule, BUT it was only triggered on user's explicit click of the REPEAT tab (`OnReminderTabSelected` intent). When the tab was pre-selected as `defaultTab` in the sheet state, no user click fired → no tab-switch intent → `initRepeatTabIfNeeded()` never ran.

### Path 2: Per-Item Reminder (`handleItemReminderClick`)

Same issue, worse:
- `initItemRepeatTabIfNeeded()` did not exist at all
- `screen.kt` passed `pendingRepeatConfig = null` as a hardcoded parameter, ignoring the VM-state
- No initialization path existed for item-level `pendingRepeatConfig`

Result: per-item reminder sheet ALWAYS showed 09:00, even if the item had a saved time.

## Solution

### Changes to `ChecklistDetailViewModel.kt`

1. **New helper method `initItemRepeatTabIfNeeded(itemId: String)`:**
   - Reads `item.repeatRule` and `item.repeatTimeOfDayMinutes` from `defaultFill.items.firstOrNull { it.id == itemId }`
   - Builds `PendingRepeatConfig` from the saved rule
   - Computes `repeatRuleSummary` via `buildRepeatSummary(config)`
   - Updates state in a single `updateContentState` call

2. **`handleReminderClick` (checklist-level):**
   - After `showReminderSheet = true` with `defaultTab = REPEAT`, immediately calls `initRepeatTabIfNeeded()`
   - Ensures `pendingRepeatConfig` is populated before sheet renders

3. **`handleItemReminderClick` (per-item):**
   - After `showItemReminderSheet = true` with `defaultTab = REPEAT`, calls `initItemRepeatTabIfNeeded(itemReminderSheetFor)`
   - Populates both `pendingRepeatConfig` and `repeatRuleSummary`

4. **`handleNotificationPermissionResult` and `handleNotificationPermissionSkip`:**
   - If `activeReminderTab == REPEAT`, call `initRepeatTabIfNeeded()` or `initItemRepeatTabIfNeeded()` respectively
   - These paths also auto-open the Repeat tab without triggering a user click

5. **`OnItemReminderTabSelected` intent handler:**
   - If user switches to REPEAT tab on an item reminder sheet, call `initItemRepeatTabIfNeeded(itemReminderSheetFor)` if not already done
   - Mirrors the existing logic for checklist-level `OnReminderTabSelected`

6. **Cleanup on close/save/remove:**
   - `OnDismissItemReminderSheet`: Reset `pendingRepeatConfig = null`, `repeatRuleSummary = null`, `showEndConditionPicker = false`
   - `saveItemReminder` and `removeItemReminder`: Same cleanup after the save/remove intent completes
   - Prevents `pendingRepeatConfig` from "leaking" between different item reminder sheets

### Changes to `ChecklistDetailScreen.kt`

For per-item ReminderSheet, pass the actual state values instead of hardcoded `null`:
```kotlin
ReminderSheetState(
    pendingRepeatConfig = state.pendingRepeatConfig,  // was: null
    showEndConditionPicker = state.showEndConditionPicker,  // was: false
    repeatRuleSummary = state.repeatRuleSummary  // no fallback to enum.name
)
```

**Important:** No fallback on `repeatRuleSummary` to `itemReminderItem.repeatRule?.type?.name`. If the summary is not available, the field is hidden entirely (as per user feedback: "лучше ничего не показывать, чем обманывать").

## Pattern: Auto-Open Default Tab Must Trigger Same Init Logic as User Tab-Click

**Rule:** When a modal/bottom-sheet opens with a pre-selected tab (via `defaultTab` parameter), the initialization logic for that tab must run **before** the composable tree renders. Otherwise, state remains stale and the UI shows defaults.

**Implementation:**
1. Separate **init logic** from **user-action logic** into dedicated helpers (e.g., `initRepeatTabIfNeeded()`, `initItemRepeatTabIfNeeded()`)
2. Call these helpers in:
   - The intent handler that sets `defaultTab` (e.g., `handleReminderClick`)
   - The intent handler for user tab switches (e.g., `OnReminderTabSelected`)
   - Any other code path that may skip the tab-switch intent (e.g., permission callbacks that auto-open the sheet)
3. Single `updateContentState` call to ensure atomicity — all dependent fields (`pendingRepeatConfig`, `repeatRuleSummary`, etc.) update together

This pattern avoids the classic "first render sees null, second render sees correct value" flicker and is reusable across any modal-sheet with tabs.

## Pattern: Shared Mutable State Across Modal Sheet Siblings — Cleanup on Dismiss/Save/Remove

**Rule:** When a modal sheet siblings can access the same mutable state (e.g., `pendingRepeatConfig` is shared between checklist-level and per-item sheets), the state must be cleaned up when switching between sheets or dismissing.

**Symptom if not applied:** Open item A's reminder sheet, switch to item B without dismissing — item B's sheet shows item A's pending config.

**Implementation:**
1. Store per-entity **pending state** with a key or sentinel (e.g., `itemReminderSheetFor: String?` to track which item is being edited)
2. On `OnDismiss`, `onSave`, or `onRemove`, reset all pending state: `pendingRepeatConfig = null`, `repeatRuleSummary = null`, `showEndConditionPicker = false`
3. On re-open of the same or different sheet, re-initialize from the model: `initRepeatTabIfNeeded()` or `initItemRepeatTabIfNeeded(newId)`

This prevents stale state from one editing session bleeding into the next.

## Tests

Three new unit tests, all passing:

### `ChecklistDetailRepeatRuleTest.onReminderClick_withActiveRepeat_opensRepeatTabWithSavedTime`
Checklist-level:
- Setup: checklist with `repeatNextAt = now + 1 day`, `repeatTimeOfDayMinutes = 720` (12:00)
- Action: single `onIntent(HandleReminderClick)`
- Assertion: `state.pendingRepeatConfig.timeHour == 12` (not 9), `state.defaultTab == ReminderTab.REPEAT`

### `ChecklistDetailItemReminderTest.onItemReminderClick_withRepeatRule_populatesPendingConfigWithSavedTime`
Per-item:
- Setup: fill item with `repeatRule = Daily`, `repeatTimeOfDayMinutes = 720`
- Action: single `onIntent(HandleItemReminderClick(itemId))`
- Assertion: `state.pendingRepeatConfig.timeHour == 12` (not 9), `state.itemReminderSheetFor == itemId`

### `ChecklistDetailItemReminderTest.onItemReminderClick_thenDismiss_clearsPendingConfig`
Per-item cleanup:
- Setup: item with saved repeat rule
- Action: `onIntent(HandleItemReminderClick)` → `onIntent(OnDismissItemReminderSheet)`
- Assertion: `state.pendingRepeatConfig == null`, `state.repeatRuleSummary == null`
- Guards against state leakage when switching items or dismissing the sheet

## Validation

- APK rebuilt and installed on physical Pixel 9 (Wi-Fi debugging) three times: after iteration 1 (checklist-level fix), iteration 2 (per-item fix), and iteration 3 (cosmetic / rule addition)
- Final install: user confirmed fic works on both checklist and per-item reminder flows
- Reminder sheet Repeat tab now displays saved time (12:00) instead of dafault (09:00)

## Related

This is a **bug-fix follow-up to the per-item reminders feature** (`per-item-reminders-2026-05-05.md`). The feature was shipped with correct state management on initial render, but missed the edge case of opening the sheet with a pre-selected REPEAT tab (which skips the user-click initialization path).

## User Feedback

User feedback on fallback handling: "лучше ничего не показывать, чем обманывать" (better to show nothing than to lie). This led to the creation of global rule `feedback_no_misleading_ui_fallback.md` — no fallback to raw enum names for user-facing strings.

## Connections

- **Memory:** `memory/feedback_no_misleading_ui_fallback.md` — rule formalizing the "no raw enum fallback" principle
- **Pattern library:** `docs/solutions/ui-bugs/` for similar modal-sheet state management bugs
- **KMP gotchas:** Smart-cast across module boundaries (see per-item-reminders) — not a factor here, but related pattern
