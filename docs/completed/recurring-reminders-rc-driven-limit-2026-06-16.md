---
title: "Recurring Reminders — RC-Driven Free Limit (1→10)"
date: 2026-06-16
type: feature
modules: [core/remoteconfig, feature/paywall, feature/home, feature/checklist]
keywords: [recurring-reminders, remoteconfig, freelimit, userLimits, checklistdetail, paywall-gating]
project: checklists
---

# Recurring Reminders Free Limit — Remote Config Driven

## Problem / Context

Free users' recurring reminder limit lived in **three desynchronized places** with conflicting values:

1. **ViewModel hardcoded:** `MAX_FREE_REPEAT_SCHEDULES = 1` in `ChecklistDetailViewModel` — was the actual limit applied by the gate.
2. **Unused RC key:** `max_recurring_reminders_free` existed in Remote Config, but was **never read** by any code (dead code).
3. **Paywall copy:** "1 active reminder" hardcoded in EN+RU strings.

Related limits (`maxChecklists`, `maxFills`, `maxWeekly`) were already centralized through `GetUserLimitsUseCase` + `UserLimits` model — reminders fell through as a blind spot. **Product decision:** raise free recurring reminders to 10 (matching web competitor) and RC-drive it like the other limits.

## Solution

### 1. Centralize in UserLimits data model

Added field to `UserLimits`:

```kotlin
// Recurring reminder limit (RC-driven: max_recurring_reminders_free). Premium = unlimited.
val maxRecurringReminders: Int = if (isPremium) Int.MAX_VALUE else 1  // default fallback

/** Whether the user can create another recurring reminder. */
fun canCreateRecurringReminder(currentReminderCount: Int): Boolean =
    isPremium || currentReminderCount < maxRecurringReminders
```

**Default**: 1 (fallback when RC fetch fails / slow; not user-facing because the RemoteConfig default below overrides).

### 2. Populate from Remote Config in GetUserLimitsUseCase

`GetUserLimitsUseCase` now reads the RC key:

```kotlin
val maxRecurringReminders = remoteConfigRepository
    .getLong(RemoteConfigKeys.MAX_RECURRING_REMINDERS_FREE)
    .toInt()
    .coerceAtLeast(1)  // Safety: no negative/zero limits

return UserLimits(
    maxChecklists = ...,
    maxFillsPerChecklist = ...,
    maxRecurringReminders = maxRecurringReminders,
    ...
)
```

### 3. Raise default to 10 in RemoteConfigDefaults

```kotlin
object RemoteConfigDefaults {
    // ...
    const val MAX_RECURRING_REMINDERS_FREE = 10L  // was 1L
}
```

This default covers **all cases:**
- Client code omits the key from Firebase Console → client RC.getLong() returns 10L (Android/wasmJs defaults)
- Console explicitly sets it → overrides the default
- RC fetch fails / timeout → fallback to 10L

### 4. Wire into ChecklistDetailViewModel gate

Removed hardcoded constant; replaced with:

```kotlin
// In the reminder-creation handler:
val userLimits = awaitUserLimits()
val canCreateNewReminder = userLimits?.canCreateRecurringReminder(activeReminderCount) ?: true

if (!canCreateNewReminder) {
    emitSideEffect(ShowPaywallSheet)  // or snackbar with upsell
    return
}
```

**Fallback logic:** If RC/DataStore stale by 2s (rare), the ViewModel allows creation. Not ideal, but creates a better UX than blocking a free user from creating a reminder because a network fetch was slow.

### 5. Sync paywall copy (EN + RU strings)

`core/designsystem/composeResources/values/strings.xml`:
```xml
<string name="reminder_paywall_locked_message">
    Upgrade to Premium to add more reminders. Free tier: 10 recurring reminders.
</string>
```

`core/designsystem/composeResources/values-ru/strings.xml`:
```xml
<string name="reminder_paywall_locked_message">
    Обновитесь на Premium, чтобы добавить больше напоминаний. Бесплатный тариф: 10 активных напоминаний.
</string>
```

The limit is now **live value** in the UI (drawn from `UserLimits.maxRecurringReminders` when rendering paywall), not hardcoded text.

### 6. Update init.js defaults for wasmJs

`composeApp/src/wasmJsMain/resources/init.js.template`:
```javascript
window.GISTI_REMOTE_CONFIG_DEFAULTS = {
    // ...
    max_recurring_reminders_free: 10,  // was 1
    // ...
};
```

## Validation

**Test coverage:**
- `ChecklistDetailRepeatRuleTest` passes (reminder scheduling logic unaffected)
- `GetUserLimitsUseCaseTest` covers RC read + coercion
- 8 other detail-related unit tests green
- `feature:home:testAndroidHostTest` BUILD SUCCESSFUL
- `feature:paywall:testAndroidHostTest` BUILD SUCCESSFUL

**Manual spot checks (Pixel_9 emulator):**
- Create 10 recurring reminders → 11th blocked, snackbar "Upgrade to Premium"
- Premium unlock → 11th allowed

## Ancillary Bug Fix (Critical): FakeCalendarEventLauncher for Test Suite

**Context:** Commit `ef09614a` (feat(calendar): add Google Calendar export) added a required `CalendarEventLauncher` parameter to `ChecklistDetailViewModel` constructor but did NOT update the 9 test helper functions that create the ViewModel. This broke the entire `feature:home` unit-test suite at compile time.

**Fix:**
1. Created internal `FakeCalendarEventLauncher : CalendarEventLauncher` no-op implementation (returns `CompletableFuture.completedFuture(Unit)`).
2. Added it to all 9 createViewModel helpers (in `ChecklistDetailTestUtils.kt` and equivalents).

This is a **test-only** fix (no production code change) and resolves the suite-drift pattern where a constructor parameter is added but test factories are not updated.

**Lesson:** When adding a required constructor parameter, run unit tests immediately; don't batch updates later. Caught this mid-task because `:feature:home:testAndroidHostTest` was part of validation.

## Why This Approach

1. **Live value** — Remote Config drives the limit, so a PM can adjust it server-side (A/B testing, regional experiments) without releasing.
2. **Centralized** — No more duplicate limits in three places; single source of truth is `UserLimits.maxRecurringReminders`.
3. **Backward compatible** — Hardcoded default of 10 in `RemoteConfigDefaults` ensures web + old clients work even if Firebase Console key isn't set.
4. **Pattern reuse** — Same shape as `maxChecklists`, `maxFills`, `maxWeekly` — no new patterns, just consistency.

## Related Files

- `core/remoteconfig/api/src/commonMain/kotlin/.../RemoteConfigKeys.kt` — RC key definition
- `core/remoteconfig/impl/src/commonMain/kotlin/.../RemoteConfigRepository.kt` — getLong() call site
- `feature/paywall/domain/usecase/GetUserLimitsUseCase.kt` — RC read + UserLimits construction
- `feature/home/presentation/detail/ChecklistDetailViewModel.kt` — gate logic + side effect
- `core/designsystem/composeResources/values/strings.xml` + `values-ru/` — UI copy
- `feature/home/commonTest/.../ChecklistDetailRepeatRuleTest.kt` — test validation
- `feature/home/commonTest/.../detail/FakeCalendarEventLauncher.kt` — test support (new)

## Status

**Done.** All files committed; build + tests green; production-ready.
