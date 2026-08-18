---
title: "Calendar Item Deeplink & Auto-Focus to ChecklistDetail"
date: 2026-05-18
type: bug-fix
modules: [core/navigation, feature/home, composeApp]
keywords: [calendar, deeplink, navigation, focus, scroll-to-item, highlight-animation, reminder]
project: Checklists
---

# Calendar Item Reminder Deeplink & ChecklistDetail Auto-Focus

## Problem / Context

When a user tapped on an item-level reminder in Calendar view, the app navigated to `FillDetail` screen instead of the full `ChecklistDetail` screen. This gave users a fragmented experience — they couldn't see the template structure, edit the item text, or manage item metadata (priority, attachments, notes). Additionally, there was no visual indication of which item the reminder was about.

**Before:** Calendar reminder item → FillDetail (limited, temporary view)  
**After:** Calendar reminder item → ChecklistDetail (full editor) + scroll to item + 1sec highlight

## Solution

### Navigation Layer Extension

Extended `ChecklistDetail` route to accept an optional `focusItemId` parameter:

```kotlin
@Serializable
data class ChecklistDetail(
    val checklistId: String,
    val focusItemId: String? = null  // New optional parameter
) : AppNavRoute
```

Updated `NavCommand` and `AppNavigator` signatures to propagate `focusItemId`:

```kotlin
sealed class NavCommand {
    data class ToChecklistDetail(
        val checklistId: String,
        val focusItemId: String? = null
    ) : NavCommand()
}

interface AppNavigator {
    fun navigateToChecklistDetail(checklistId: String, focusItemId: String? = null)
}
```

### ViewModel Intent → SideEffect Chain

Modified `CalendarViewModel` to emit navigation SideEffect with focus metadata:

```kotlin
// Old (incorrect)
CalendarViewModel.onIntent(ReminderClick.ItemLevel(itemId, checklistId)) {
    navigateToFillDetail(fillId)  // ❌ Wrong screen
}

// New (correct)
CalendarViewModel.onIntent(ReminderClick.ItemLevel(itemId, checklistId)) {
    emitSideEffect(NavigateToChecklistDetail(checklistId, focusItemId = itemId))
}
```

**Why SideEffect?** Decouples ViewModel from NavController; aligns with established deeplink pattern from AI Chat (see related pattern file `deeplink_sideeffect_chain_pattern.md`).

### App Router Handler

`App.kt` receives `focusItemId` and passes it to screen content:

```kotlin
composable<ChecklistDetail> { backStackEntry ->
    val route = backStackEntry.toRoute<ChecklistDetail>()
    ChecklistDetailScreen(
        checklistId = route.checklistId,
        focusItemId = route.focusItemId  // ← Propagate
    )
}
```

### Screen-Level Scroll & Highlight Logic

`ChecklistDetailScreen` implements one-shot scroll + animation:

```kotlin
@Composable
fun ChecklistDetailScreen(
    checklistId: String,
    focusItemId: String? = null,
    // ... other params
) {
    val state = viewModel.screenState.collectAsState()
    var highlightedItemId by remember { mutableStateOf<String?>(null) }
    var didFocusScroll by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(focusItemId, state.defaultFill?.items) {
        if (focusItemId != null && state.defaultFill != null && !didFocusScroll) {
            val index = state.defaultFill.items.indexOfFirst { it.id == focusItemId }
            if (index >= 0) {
                lazyListState.scrollToItem(index, offset = 0)
                highlightedItemId = focusItemId
                didFocusScroll = true
                
                // Fade out highlight after 1 second
                delay(1000)
                highlightedItemId = null
            }
        }
    }

    LazyColumn(state = lazyListState) {
        items(items = state.defaultFill?.items ?: emptyList()) { item ->
            ChecklistItemCard(
                item = item,
                isHighlighted = (item.id == highlightedItemId),
                highlightColor = animateColorAsState(
                    targetValue = if (item.id == highlightedItemId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(280)
                ).value
            )
        }
    }
}
```

**Key patterns applied:**
1. **One-shot guard via `rememberSaveable`** — prevents re-scroll on recomposition (similar to IME-viewport-anchor fix from 2026-05-18)
2. **`LaunchedEffect` with multi-key deps** — waits for both `focusItemId` AND `state.defaultFill?.items` to stabilize before scrolling
3. **Animated highlight** — `animateColorAsState(tween(280ms))` for smooth 280ms fade in/out, consistent with calendar date-header highlight patterns

### Test Coverage

Updated 19 test files:
- **CalendarViewModelTest** +1 new test: `itemLevelReminder_emitsFocusNavigateCommand()`
- **AppNavigatorImplTest** +1 new test: `navigateToChecklistDetail_withFocusItemId()`
- **18 fake navigator files** batch-updated for new signature (default `focusItemId = null` in mocks)

All tests pass: `testAndroidHostTest` 140+ pass, `compileDebugKotlin` clean.

## Why This Approach

1. **Backward compatible** — default `focusItemId = null` doesn't break existing 18+ call-sites of `navigateToChecklistDetail()`
2. **Single responsibility** — scroll & highlight logic isolated to `ChecklistDetailScreen`, not scattered across features
3. **UX consistency** — highlight animation matches existing calendar date-header pattern (280ms tween, 1sec hold)
4. **Deferred (not blocking):** Weekly viewMode (`WeeklyChecklistDetailContent`) doesn't yet support focusItemId; this is MVP Standard view only. Can be added in future PR if needed.

## Related Files

- `deeplink_sideeffect_chain_pattern.md` — ViewModel SideEffect → App.kt routing pattern
- `lazyscroll-reverselayout-ime-viewport-anchor-fix-2026-05-18.md` — scroll-anchor and LaunchedEffect keys pattern
- `calendar-agenda-view-2026-05-13.md` Section 6 — highlight animation precedent

## Commit

`bfe1c67e: fix(calendar): scroll to item from reminder tap`

**Scope:** 25 files (10 production, 15 test)  
**Validation:** assembleDebug PASS, testAndroidHostTest 140+ PASS

---

**Keywords for future search:**  
Calendar · Deeplink · Navigation · Focus · Scroll-to-item · Highlight · Reminder · ChecklistDetail
