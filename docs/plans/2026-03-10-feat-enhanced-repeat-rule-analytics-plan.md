---
title: "feat: Add analytics for repeat rule picker"
type: feat
date: 2026-03-10
complexity: Standard
status: implemented
---

# feat: Analytics for Repeat Rule Picker

Analytics tracking for smart presets, YEARLY repeat type, and boot recovery.

Phase 2 (Enhanced Monthly) and Phase 3 (After Completion) were **descoped** —
baseline repeat functionality covers 90%+ of user needs at current product stage.

## Current State (after implementation)

8 events tracked. Extended: `repeat_schedule_set` (+4 params), `repeat_schedule_cancelled`
(+1 param). New: `recurring_reminder_recovered`, `recurring_reminder_ended` during boot recovery.

## Events

### 1. `repeat_schedule_set` (extended)

| Parameter | Type | Values |
|-----------|------|--------|
| `type` | String | `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY` |
| `interval` | String | number |
| `time_of_day` | String | minutes since midnight |
| `reset_checks` | String | `"true"` / `"false"` |
| **`preset`** | String | `daily`, `weekdays`, `weekly`, `biweekly`, `monthly`, `quarterly`, `yearly`, `custom` |
| **`week_days`** | String | comma-separated ISO days, e.g. `"1,2,3,4,5"` (only when present) |
| **`end_condition`** | String | `Never`, `UntilDate`, `AfterCount` |
| **`is_edit`** | String | `"true"` if modifying existing rule, `"false"` if new |

**Preset mapping:**

| UI Label | `preset` value | Config signature |
|----------|---------------|------------------|
| Every day | `daily` | type=DAILY, interval=1 |
| Weekdays | `weekdays` | type=WEEKLY, interval=1, weekDays={1,2,3,4,5} |
| Every week | `weekly` | type=WEEKLY, interval=1, no weekDays |
| Every 2 weeks | `biweekly` | type=WEEKLY, interval=2 |
| Every month | `monthly` | type=MONTHLY, interval=1 |
| Every 3 months | `quarterly` | type=MONTHLY, interval=3 |
| Every year | `yearly` | type=YEARLY, interval=1 |
| Custom… | `custom` | isCustom=true |

### 2. `repeat_schedule_cancelled` (extended)

| Parameter | Type | Values | Status |
|-----------|------|--------|--------|
| `checklist_id` | String | ID | existing |
| **`total_occurrences`** | String | count (0 if never fired) | NEW |

### 3. `recurring_reminder_recovered` (new — boot recovery)

Fires in `RecoverRecurringRemindersUseCase` when past-due schedules are
fast-forwarded after device reboot.

| Parameter | Type | Values |
|-----------|------|--------|
| `checklist_id` | String | ID |
| `skipped_occurrences` | String | number of occurrences fast-forwarded |
| `next_at` | String | epoch ms of rescheduled trigger |

### 4. `recurring_reminder_ended` (now fires during boot recovery too)

Previously only fired from `ReminderReceiver`. Now also fires from
`RecoverRecurringRemindersUseCase` when end condition is reached during recovery.

### 5. No changes

- `recurring_reminder_fired` — unchanged
- `recurring_reminder_cancelled` — unchanged
- `recurring_checks_reset` — unchanged
- `recurring_limit_hit` — unchanged

## Bug Fix: `isCustom` not updating on preset modification

Fixed: `isCustom` now set to `true` when user modifies interval or weekdays
after selecting a preset. This ensures `resolvePresetName()` correctly returns
`"custom"` instead of the original preset name.

## Files Modified

| File | Changes |
|------|---------|
| `ChecklistDetailViewModel.kt` | Extended `repeat_schedule_set` params, added `total_occurrences` to `repeat_schedule_cancelled`, added `resolvePresetName()`, fixed `isCustom` |
| `RecoverRecurringRemindersUseCase.kt` | Injected `AnalyticsTracker?`, added `recurring_reminder_recovered` and `recurring_reminder_ended` events |
| `ChecklistFeatureModule.kt` | Koin: `getOrNull()` for AnalyticsTracker |
| `ChecklistDetailRepeatRuleTest.kt` | +7 tests (preset name, is_edit, week_days, custom detection, total_occurrences, isCustom fix) |
| `RecoverRecurringRemindersUseCaseTest.kt` | +3 tests (recovered event, ended event, null tracker safety) |

## References

- Analytics interface: `core/common/api/.../AnalyticsTracker.kt`
- Recurring reminders plan: `docs/plans/2026-03-04-feat-recurring-reminders-plan.md`
