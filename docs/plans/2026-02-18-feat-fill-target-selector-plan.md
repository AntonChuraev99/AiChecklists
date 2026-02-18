---
title: "feat: Add fill target selector (main checklist vs new fill)"
type: feat
date: 2026-02-18
reviewed: true
---

# feat: Add fill target selector (main checklist vs new fill)

## Overview

When the user taps "Fill via AI" on `ChecklistDetailScreen`, a `ModalBottomSheet` appears with two options:

1. **Fill main checklist** — AI updates the checked states and notes of the existing default fill items. No new fill is created.
2. **Create new fill** — Current behavior. A new `ChecklistFill` is created from AI analysis.

This gives users flexibility: use the main checklist as a living document updated by AI, or keep separate fill instances for comparison.

## Problem Statement / Motivation

Currently, "Fill via AI" **always creates a new fill**. Users who want to simply update their main checklist (default fill) with AI analysis have no way to do so — they must create a new fill and manually copy changes back. This is unintuitive for use cases like:

- A home buyer updating the same "Apartment Inspection" checklist after each viewing
- A student marking off syllabus items as they progress through lectures
- A project manager updating task completion based on a new status report

## Proposed Solution

### User Flow

```
ChecklistDetailScreen
  ↓ Tap "Fill via AI"
ModalBottomSheet (two options)
  ├─ "Fill main checklist"
  │    ↓ (no fill limit check)
  │    AnalyzeScreen (fillDefault = true)
  │    ↓ AI processes input
  │    AnalyzeResultPreview (simplified: no name field, button = "Apply to Checklist")
  │    ↓ Tap "Apply"
  │    Update default fill in Room DB
  │    ↓ Navigate back to ChecklistDetailScreen via navigateToChecklistDetail(popUpTo)
  │
  └─ "Create new fill"
       ↓ fill limit check
       ├─ Limit reached → FillLimitDialog
       └─ Allowed → AnalyzeScreen (fillDefault = false)
            ↓ AI processes input
            AnalyzeResultPreview (current behavior: name field, editable items)
            ↓ Tap "Create Fill"
            Create new ChecklistFill in Room DB
            ↓ Navigate to FillDetailScreen
```

### UI: ModalBottomSheet

```
┌──────────────────────────────────────┐
│  How would you like to fill?         │  ← Title
│                                      │
│  ┌──────────────────────────────┐    │
│  │  Fill main checklist         │    │  ← Option 1
│  │  Update checked states of    │    │
│  │  your main checklist via AI  │    │
│  └──────────────────────────────┘    │
│                                      │
│  ┌──────────────────────────────┐    │
│  │  Create new fill             │    │  ← Option 2
│  │  Create a separate fill to   │    │
│  │  track a new instance        │    │
│  └──────────────────────────────┘    │
│                                      │
└──────────────────────────────────────┘
```

- Option 2 is tappable even at fill limit — tapping it triggers `FillLimitDialog` (consistent with existing behavior)

## Technical Approach

### Phase 1: BottomSheet + Navigation Plumbing

#### 1.1 Update `Analyze` navigation route

**File:** `core/navigation/api/.../AppNavRoute.kt`

```kotlin
@Serializable
data class Analyze(
    val checklistId: Long? = null,
    val fillDefault: Boolean = false  // true = update default fill mode
) : AppNavRoute
```

#### 1.2 Update `AppNavigator` + `AppNavigatorImpl`

**File:** `core/navigation/api/.../AppNavigator.kt`

```kotlin
fun navigateToAnalyzeScreen(checklistId: Long? = null, fillDefault: Boolean = false)
```

Update `AppNavigatorImpl` to pass `fillDefault` through.

#### 1.3 Update `ChecklistDetailScreenContract.kt`

Add to `ChecklistDetailState.Content`:

```kotlin
val showFillTargetSheet: Boolean = false
```

Add intents:

```kotlin
data object OnFillTargetSheetDismiss : ChecklistDetailIntent
data object OnFillMainChecklistSelected : ChecklistDetailIntent
data object OnCreateNewFillSelected : ChecklistDetailIntent
```

#### 1.4 Update `ChecklistDetailViewModel.kt`

```kotlin
private fun handleAddFillViaAiClick() {
    // No limit check here — moved to OnCreateNewFillSelected
    updateContentState { it.copy(showFillTargetSheet = true) }
}

private fun handleFillMainChecklistSelected() {
    updateContentState { it.copy(showFillTargetSheet = false) }
    navigator.navigateToAnalyzeScreen(checklistId, fillDefault = true)
}

private fun handleCreateNewFillSelected() {
    updateContentState { it.copy(showFillTargetSheet = false) }
    withFillLimitCheck {
        navigator.navigateToAnalyzeScreen(checklistId, fillDefault = false)
    }
}
```

Add `when` branches in `onIntent()` for the 3 new intents.

#### 1.5 Add `FillTargetBottomSheet` composable (inline cards, no separate component)

**File:** `feature/home/presentation/detail/ChecklistDetailScreen.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FillTargetBottomSheet(
    onFillMainChecklist: () -> Unit,
    onCreateNewFill: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXl)
        ) {
            Text(
                text = stringResource(Res.string.fill_target_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            // Option 1: Fill main checklist — inline Card
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onFillMainChecklist
            ) {
                Column(modifier = Modifier.padding(AppDimens.SpacingLg)) {
                    Text(stringResource(Res.string.fill_target_main), style = titleSmall)
                    Text(stringResource(Res.string.fill_target_main_description), style = bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            // Option 2: Create new fill — inline Card
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCreateNewFill
            ) {
                Column(modifier = Modifier.padding(AppDimens.SpacingLg)) {
                    Text(stringResource(Res.string.fill_target_new), style = titleSmall)
                    Text(stringResource(Res.string.fill_target_new_description), style = bodySmall)
                }
            }
        }
    }
}
```

#### 1.6 String resources

**File:** `core/designsystem/src/commonMain/composeResources/values/strings.xml`

```xml
<string name="fill_target_title">How would you like to fill?</string>
<string name="fill_target_main">Fill main checklist</string>
<string name="fill_target_main_description">Update checked states of your main checklist via AI</string>
<string name="fill_target_new">Create new fill</string>
<string name="fill_target_new_description">Create a separate fill to track a new instance</string>
<string name="fill_apply">Apply to Checklist</string>
```

#### 1.7 Update Koin module, AnalyzeScreen composable, and App.kt

These files need `fillDefault` parameter threading:

- **`AnalyzeFeatureModule.kt`**: Update Koin viewModel definition to accept `fillDefault: Boolean` parameter
- **`AnalyzeScreen.kt`**: Add `fillDefault: Boolean = false` to composable signature, pass to ViewModel
- **`App.kt`**: Extract `route.fillDefault` from navigation and pass to `AnalyzeScreen`

### Phase 2: Preview + Save Logic

#### 2.1 Update `AnalyzeViewModel.kt` — pass `fillDefault` through

```kotlin
class AnalyzeViewModel(
    private val checklistId: Long?,
    private val fillDefault: Boolean,
    ...
) {
    init {
        _screenState.update {
            it.copy(
                isFillMode = checklistId != null,
                fillDefault = fillDefault
            )
        }
        if (checklistId != null) {
            loadTargetChecklist(checklistId)
        }
        // ...
    }
}
```

#### 2.2 Update `AnalyzeScreenContract.kt`

Keep existing `isFillMode: Boolean`, add:

```kotlin
val fillDefault: Boolean = false
```

No `FillMode` enum needed — derive mode from `isFillMode` + `fillDefault`.

#### 2.3 Update `AnalyzeResultHolder`

Add `fillDefault: Boolean` and `fillDefaultItems: List<ChecklistFillItem>? = null`:

```kotlin
object AnalyzeResultHolder {
    fun set(
        items: List<ChecklistItem>,
        suggestedName: String,
        summary: String,
        isFillMode: Boolean,        // Unchanged
        fillDefault: Boolean,       // NEW
        targetChecklistId: Long?,
        targetChecklistName: String?,
        fillDefaultItems: List<ChecklistFillItem>? = null  // NEW: for FILL_DEFAULT mode
    )
}
```

**No `FillItemResult` model** — reuse existing `ChecklistFillItem` which already has `text`, `checked`, `note`.

#### 2.4 Update `AnalyzeRepositoryImpl.analyzeData()` — separate notes from text

When `fillDefault == true`, do NOT concatenate note into text. Instead, build `List<ChecklistFillItem>`:

```kotlin
// In the targetChecklist != null branch:
if (fillDefault) {
    val fillDefaultItems = response.data.filledItems.map { filled ->
        ChecklistFillItem(
            text = filled.text,
            checked = filled.checked,
            note = filled.note?.takeIf { it.isNotBlank() }  // Normalize empty → null
        )
    }
    // Pass fillDefaultItems through AnalyzeResultHolder
} else {
    // Current behavior: concatenate note into text
}
```

> **Bug fix from review**: Use `.takeIf { it.isNotBlank() }` to normalize empty notes to null, matching existing `saveNote()` behavior in `ChecklistDetailViewModel`.

#### 2.5 Update `AnalyzeResultPreviewScreenContract.kt`

```kotlin
data class AnalyzeResultPreviewScreenState(
    // ... existing fields ...
    val fillDefault: Boolean = false,
    val fillDefaultItems: List<ChecklistFillItem> = emptyList()
) : State
```

#### 2.6 Update `AnalyzeResultPreviewScreen.kt` — 3 conditionals

Minimal changes to existing screen:

1. **Hide name field** when `fillDefault == true`
2. **Change button text** to `stringResource(Res.string.fill_apply)` when `fillDefault == true`
3. **Change save action** to call `applyToDefaultFill()` when `fillDefault == true`

All other UI (item list, item editing) stays the same. The add/remove controls can remain — save logic matches by index and ignores extras.

#### 2.7 Update `AnalyzeResultPreviewViewModel.kt` — add `applyToDefaultFill()`

```kotlin
private fun applyToDefaultFill() {
    viewModelScope.launch {
        _screenState.update { it.copy(isCreating = true) }
        try {
            val checklistId = targetChecklistId ?: run {
                _screenState.update { it.copy(isCreating = false, error = "Checklist not found") }
                return@launch
            }

            // getDefaultFillByChecklistId returns Flow — use .first()
            val defaultFill = checklistRepository
                .getDefaultFillByChecklistId(checklistId)
                .first()

            if (defaultFill == null) {
                _screenState.update { it.copy(isCreating = false, error = "Default fill not found") }
                return@launch
            }

            // Match AI results to existing items by index
            val fillDefaultItems = _screenState.value.fillDefaultItems
            val updatedItems = defaultFill.items.mapIndexed { index, existingItem ->
                val aiResult = fillDefaultItems.getOrNull(index)
                if (aiResult != null) {
                    existingItem
                        .withChecked(aiResult.checked)
                        .let { item ->
                            aiResult.note?.let { note -> item.withNote(note) } ?: item
                        }
                } else {
                    existingItem // No AI result — keep as is
                }
            }

            val updatedFill = defaultFill.copy(items = updatedItems)
            checklistRepository.updateFill(updatedFill)

            analyticsTracker.event("default_fill_updated")

            AnalyzeResultHolder.clear()
            // Navigate back to ChecklistDetail, popping Analyze and Preview from back stack
            appNavigator.navigateToChecklistDetail(checklistId)
        } catch (e: Exception) {
            _screenState.update { it.copy(isCreating = false, error = e.message) }
        }
    }
}
```

> **Bug fixes from review**:
> - Guard clause instead of `!!` on `targetChecklistId`
> - `.first()` on Flow since `getDefaultFillByChecklistId` returns `Flow<ChecklistFill?>`
> - `navigateToChecklistDetail(checklistId)` instead of non-existent `popBackStack()`
> - Simple `event("default_fill_updated")` without computed analytics params

## Acceptance Criteria

- [x] Tapping "Fill via AI" on `ChecklistDetailScreen` opens a `ModalBottomSheet` with two options
- [x] "Fill main checklist" navigates to `AnalyzeScreen` with `fillDefault = true` (no fill limit check)
- [x] "Create new fill" performs fill limit check, then navigates with `fillDefault = false`
- [x] In fill-default mode, `AnalyzeResultPreview` hides name field and shows "Apply to Checklist" button
- [x] In fill-default mode, saving updates the default fill's `checked` + `note` fields in Room DB
- [x] In fill-default mode, after save, user navigates back to `ChecklistDetailScreen`
- [x] Create-new-fill mode works identically to current behavior
- [x] Bottom sheet dismisses on swipe down / tap outside with no action
- [x] Items with no AI result keep their current state unchanged
- [x] Empty AI notes normalized to `null` (matching existing `saveNote()` behavior)

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| No `FillMode` enum | Two booleans: `isFillMode` + `fillDefault` | Avoids new type, reuses existing pattern, zero file renames |
| No `FillItemResult` model | Reuse `ChecklistFillItem` | Already has `text`, `checked`, `note` — no duplication |
| AI can uncheck items | Yes | User chose "update checked states" — AI makes independent decisions |
| Note conflict resolution | AI note replaces existing | Deliberate "fill" action; user sees changes in preview |
| Item matching | By index (position) | Server returns `filledItems` indexed to originals |
| Fill limit on Option 2 | Check on tap, not disable | Consistent with existing `FillLimitDialog` |
| Preview for fill-default | Minimal changes (3 conditionals) | Hide name, change button, change save action |
| Navigation back | `navigateToChecklistDetail()` | Properly pops Analyze + Preview off back stack |

## Dependencies & Risks

| Risk | Mitigation |
|------|------------|
| First `ModalBottomSheet` in codebase | Follow Material3 API, simple boolean visibility |
| AI unchecks manually checked items | Preview screen shows all changes before applying |
| Process death loses `AnalyzeResultHolder` | Existing limitation; preview shows "No data" error |
| Default fill deleted during analyze flow | Null check on `.first()`, show error message |
| `getDefaultFillByChecklistId` returns Flow | Use `.first()` for one-shot access |

## Files to Modify

| File | Change |
|------|--------|
| `core/navigation/api/.../AppNavRoute.kt` | Add `fillDefault: Boolean` to `Analyze` route |
| `core/navigation/api/.../AppNavigator.kt` | Add `fillDefault` param to `navigateToAnalyzeScreen()` |
| `core/navigation/impl/.../AppNavigatorImpl.kt` | Pass `fillDefault` through |
| `feature/home/.../ChecklistDetailScreenContract.kt` | Add `showFillTargetSheet` + 3 intents |
| `feature/home/.../ChecklistDetailViewModel.kt` | Sheet handlers + move limit check |
| `feature/home/.../ChecklistDetailScreen.kt` | Add `FillTargetBottomSheet` composable |
| `feature/analyze/.../AnalyzeScreenContract.kt` | Add `fillDefault: Boolean` |
| `feature/analyze/.../AnalyzeViewModel.kt` | Accept + propagate `fillDefault` |
| `feature/analyze/.../AnalyzeScreen.kt` | Pass `fillDefault` to ViewModel |
| `feature/analyze/.../AnalyzeResultHolder.kt` | Add `fillDefault` + `fillDefaultItems` |
| `feature/analyze/.../AnalyzeRepositoryImpl.kt` | Separate notes from text when `fillDefault` |
| `feature/analyze/.../preview/AnalyzeResultPreviewScreenContract.kt` | Add `fillDefault` + `fillDefaultItems` |
| `feature/analyze/.../preview/AnalyzeResultPreviewScreen.kt` | 3 conditionals (name, button, save) |
| `feature/analyze/.../preview/AnalyzeResultPreviewViewModel.kt` | Add `applyToDefaultFill()` |
| `feature/analyze/di/AnalyzeFeatureModule.kt` | Thread `fillDefault` param through Koin |
| `composeApp/.../App.kt` | Extract `route.fillDefault`, pass to screen |
| `core/designsystem/.../values/strings.xml` | Add 6 string resources |

## References

### Internal References

- `ChecklistDetailViewModel.kt:166-183` — current `handleAddFillViaAiClick()` + `withFillLimitCheck()`
- `AnalyzeViewModel.kt:20-57` — fill mode initialization
- `AnalyzeResultPreviewViewModel.kt:103-167` — save logic (fill vs create)
- `AnalyzeRepositoryImpl.kt:37-72` — `analyzeAndFillChecklist` code path
- `AnalyzeResultHolder.kt` — singleton for passing data between screens
- `ChecklistDetailScreen.kt:454-599` — existing dialog patterns

### Institutional Learnings Applied

- `docs/solutions/logic-errors/progress-bar-shows-zero-using-template-instead-of-fill.md` — always use fill data as source of truth
- `docs/solutions/test-failures/e2e-test-suite-stabilization.md` — avoid ambiguous text selectors
- `docs/solutions/features/enforced-auto-generated-id-pattern.md` — use `withChecked()` / `withNote()` helpers
- `docs/solutions/database-issues/room-cascade-delete-flow-race-condition.md` — null-check on Flow reads
