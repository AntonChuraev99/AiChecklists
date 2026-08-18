---
title: "feat: Show locked state on Create Checklist button at limit"
type: feat
date: 2026-02-02
---

# Show Locked State on Create Checklist Button at Limit

## Overview

When a free user reaches the maximum of 3 checklists, the "Create Checklist" button should visually change to indicate the locked state:
- **Text**: "Unlock More Checklists" (instead of "Create Checklist")
- **Icon**: Lock icon (instead of Add icon)
- **Style**: Same blue `AppButton` (consistent with current design)
- **Behavior**: Navigates to Paywall when clicked (current behavior preserved)

## Problem Statement

Currently, when a free user reaches the 3-checklist limit:
1. Button still shows "Create Checklist" with Add (+) icon
2. Only when user taps the button, they're redirected to Paywall
3. **No visual indication** that the action is restricted

This creates a confusing UX — users don't know they've hit a limit until they try to create.

## Proposed Solution

Update `MainScreen.kt` to conditionally render button content based on `UserLimits.canCreateChecklist`:

```kotlin
// Current (always shows same button)
AppButton(
    text = stringResource(Res.string.main_create_checklist),
    onClick = { viewModel.sendIntent(MainScreenIntent.OnAddChecklistClick) },
    icon = Icons.Filled.Add,
    modifier = Modifier.fillMaxWidth()
)

// Proposed (conditional rendering)
val canCreate = screenState.userLimits?.canCreateChecklist ?: true

AppButton(
    text = stringResource(
        if (canCreate) Res.string.main_create_checklist
        else Res.string.main_create_checklist_locked
    ),
    onClick = { viewModel.sendIntent(MainScreenIntent.OnAddChecklistClick) },
    icon = if (canCreate) Icons.Filled.Add else Icons.Outlined.Lock,
    modifier = Modifier.fillMaxWidth()
)
```

## Technical Approach

### Files to Modify

| File | Change |
|------|--------|
| `core/designsystem/.../values/strings.xml` | Add new string resource |
| `feature/home/.../presentation/MainScreen.kt` | Conditional button rendering |

### Implementation Steps

#### Step 1: Add String Resource

**File**: `core/designsystem/src/commonMain/composeResources/values/strings.xml`

```xml
<!-- Existing -->
<string name="main_create_checklist">Create Checklist</string>

<!-- New -->
<string name="main_create_checklist_locked">Unlock More Checklists</string>
```

#### Step 2: Update MainScreen Button

**File**: `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/MainScreen.kt`

**Location**: Lines 70-86 (bottom bar content)

```kotlin
// Before (line ~75-82)
bottomBar = {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppDimens.ScreenPaddingHorizontal)
            .padding(bottom = AppDimens.SpacingMd)
    ) {
        AppButton(
            text = stringResource(Res.string.main_create_checklist),
            onClick = { viewModel.sendIntent(MainScreenIntent.OnAddChecklistClick) },
            icon = Icons.Filled.Add,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// After
bottomBar = {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppDimens.ScreenPaddingHorizontal)
            .padding(bottom = AppDimens.SpacingMd)
    ) {
        val canCreateChecklist = screenState.userLimits?.canCreateChecklist ?: true

        AppButton(
            text = stringResource(
                if (canCreateChecklist) Res.string.main_create_checklist
                else Res.string.main_create_checklist_locked
            ),
            onClick = { viewModel.sendIntent(MainScreenIntent.OnAddChecklistClick) },
            icon = if (canCreateChecklist) Icons.Filled.Add else Icons.Outlined.Lock,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

**Required Import**:
```kotlin
import androidx.compose.material.icons.outlined.Lock
```

## Acceptance Criteria

### Functional Requirements

- [ ] Free user with 0-2 checklists sees "Create Checklist" button with Add icon
- [ ] Free user with 3 checklists sees "Unlock More Checklists" button with Lock icon
- [ ] Premium user always sees "Create Checklist" button with Add icon
- [ ] Button click behavior unchanged (Templates for unlocked, Paywall for locked)

### Edge Cases

- [ ] User deletes checklist while at limit → button reverts to normal state
- [ ] User upgrades to Premium → button shows normal state on return
- [ ] Expired Premium user with >3 checklists → button shows locked state
- [ ] Loading state (userLimits is null) → button shows normal state (optimistic UI)

### Non-Functional Requirements

- [ ] No layout issues on small screens (320dp width)
- [ ] Lock icon visible and clear at different screen densities
- [ ] Accessibility: TalkBack announces button text correctly

## User Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        MainScreen                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                    Checklists List                      │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              Bottom Bar (Create Button)                 │ │
│  │                                                          │ │
│  │   canCreateChecklist = true:                            │ │
│  │   ┌──────────────────────────────────────────────────┐  │ │
│  │   │  [+]  Create Checklist                           │  │ │
│  │   └──────────────────────────────────────────────────┘  │ │
│  │                         │                                │ │
│  │                         ▼                                │ │
│  │                  Templates Screen                        │ │
│  │                                                          │ │
│  │   canCreateChecklist = false:                           │ │
│  │   ┌──────────────────────────────────────────────────┐  │ │
│  │   │  [🔒]  Unlock More Checklists                    │  │ │
│  │   └──────────────────────────────────────────────────┘  │ │
│  │                         │                                │ │
│  │                         ▼                                │ │
│  │                   Paywall Screen                         │ │
│  │                                                          │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## State Transitions

```
┌───────────────────┐     Delete Checklist     ┌───────────────────┐
│   Free User       │ ◄──────────────────────► │   Free User       │
│   0-2 Checklists  │     Create Checklist     │   3 Checklists    │
│                   │                          │   (At Limit)      │
│   [Normal Button] │                          │   [Locked Button] │
└───────────────────┘                          └───────────────────┘
         │                                              │
         │ Upgrade to Premium          Upgrade to Premium │
         ▼                                              ▼
┌───────────────────────────────────────────────────────────────┐
│                      Premium User                              │
│                   Any Checklist Count                          │
│                    [Normal Button]                             │
└───────────────────────────────────────────────────────────────┘
         │
         │ Subscription Expires
         ▼
┌───────────────────┐
│   Expired Premium │
│   >3 Checklists   │
│   [Locked Button] │
└───────────────────┘
```

## Testing Scenarios

| Scenario | Expected Button State |
|----------|----------------------|
| Free user, 0 checklists | "Create Checklist" + Add icon |
| Free user, 2 checklists | "Create Checklist" + Add icon |
| Free user, 3 checklists | "Unlock More Checklists" + Lock icon |
| Premium user, 0 checklists | "Create Checklist" + Add icon |
| Premium user, 10 checklists | "Create Checklist" + Add icon |
| Expired premium, 5 checklists | "Unlock More Checklists" + Lock icon |
| UserLimits loading (null) | "Create Checklist" + Add icon (optimistic) |

## Dependencies

- `UserLimits.canCreateChecklist` property (already exists)
- `GetUserLimitsUseCase` (already integrated in MainScreenViewModel)
- `MainScreenState.userLimits` (already in state)

No new dependencies required.

## References

### Internal References

- **Current button implementation**: `feature/home/.../MainScreen.kt:70-86`
- **UserLimits model**: `feature/paywall/.../UserLimits.kt`
- **Limit check logic**: `feature/home/.../MainScreenViewModel.kt:82-95`
- **AppButton component**: `core/designsystem/.../AppButton.kt`
- **Existing limit strings**: `core/designsystem/.../strings.xml` (`limit_reached_title`, etc.)

### Similar Patterns in Codebase

- **FillLimitDialog**: `feature/home/.../ChecklistDetailScreen.kt:571-598` — shows dialog when fill limit reached
- **PremiumBanner**: `feature/home/.../PremiumBanner.kt` — conditional UI based on premium status
