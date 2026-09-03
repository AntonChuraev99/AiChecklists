---
title: "feat: Add analytics for overflow menu, collapsible completed, quick-add"
type: feat
date: 2026-02-26
---

# feat: Add analytics for overflow menu, collapsible completed, quick-add

## Overview

Add analytics events for three new ChecklistDetailScreen features: overflow menu, "Separate completed" toggle with collapsible section, and quick-add via toolbar "+". Some events already exist but lack parameters; others are missing entirely.

## Current State

Events already tracked in `ChecklistDetailViewModel`:

| Event | Params | Status |
|-------|--------|--------|
| `separate_completed_toggled` | `enabled` | ✅ Exists |
| `item_added_quick` | — | ⚠️ Missing params |
| `checklist_deleted` | — | ⚠️ Missing params |
| `reminder_set` | `checklist_id` | ✅ OK |
| `reminder_cancelled` | `checklist_id` | ✅ OK |

## Proposed Events

### New events

| Event | When | Params |
|-------|------|--------|
| `overflow_menu_opened` | User taps MoreVert (⋮) | — |
| `completed_section_expanded` | User expands "Completed (N)" | `completed_count: Int` |
| `completed_section_collapsed` | User collapses "Completed (N)" | `completed_count: Int` |
| `quick_add_opened` | User taps toolbar "+" | — |
| `quick_add_cancelled` | Input dismissed (back press or "+" toggle) | `had_text: Boolean` |

### Enhanced existing events

| Event | Added Params | Why |
|-------|-------------|-----|
| `item_added_quick` | `checklist_id: String`, `item_count: Int` | Track which checklists get quick-add usage and resulting list size |
| `checklist_deleted` | `checklist_id: String`, `item_count: Int`, `source: String` | Know list size at deletion; `source = "overflow_menu"` (future-proof for other delete paths) |

### Unchanged events

| Event | Reason |
|-------|--------|
| `separate_completed_toggled` | Already has `enabled` param — sufficient |

## Implementation

### Phase 1: ViewModel events

#### ChecklistDetailViewModel.kt

```kotlin
// In onIntent() — OnOverflowMenuClick
ChecklistDetailIntent.OnOverflowMenuClick -> {
    updateContentState { it.copy(showOverflowSheet = true) }
    analyticsTracker.event("overflow_menu_opened")
}

// In addItem()
analyticsTracker.event("item_added_quick", mapOf(
    "checklist_id" to checklistId.toString(),
    "item_count" to (fill.items.size + 1).toString()
))

// In deleteChecklist()
analyticsTracker.event("checklist_deleted", mapOf(
    "checklist_id" to state.checklist.id.toString(),
    "item_count" to (state.defaultFill?.items?.size ?: 0).toString(),
    "source" to "overflow_menu"
))
```

### Phase 2: Screen-level events (Composable callbacks)

Expand/collapse of the completed section and quick-add open/cancel are UI-local state (not in ViewModel). Two options:

**Option A (recommended): Pass analyticsTracker to Screen via Koin**

Not needed — the Screen already calls `onIntent()` for everything that goes through ViewModel. For UI-local events, create new intents:

```kotlin
// ChecklistDetailScreenContract.kt — add intents
data class OnCompletedSectionToggle(val expanded: Boolean, val completedCount: Int) : ChecklistDetailIntent
object OnQuickAddOpened : ChecklistDetailIntent
data class OnQuickAddCancelled(val hadText: Boolean) : ChecklistDetailIntent
```

```kotlin
// ChecklistDetailViewModel.kt — handle analytics-only intents
ChecklistDetailIntent.OnCompletedSectionToggle -> {
    val eventName = if (intent.expanded) "completed_section_expanded" else "completed_section_collapsed"
    analyticsTracker.event(eventName, mapOf("completed_count" to intent.completedCount.toString()))
}
ChecklistDetailIntent.OnQuickAddOpened -> {
    analyticsTracker.event("quick_add_opened")
}
is ChecklistDetailIntent.OnQuickAddCancelled -> {
    analyticsTracker.event("quick_add_cancelled", mapOf("had_text" to intent.hadText.toString()))
}
```

```kotlin
// ChecklistDetailScreen.kt — send intents from UI

// Toolbar "+" onClick:
onIntent(ChecklistDetailIntent.OnQuickAddOpened)

// InlineAddItemInput onClose:
onIntent(ChecklistDetailIntent.OnQuickAddCancelled(hadText = text.isNotBlank()))

// CompletedSectionHeader onClick:
onIntent(ChecklistDetailIntent.OnCompletedSectionToggle(
    expanded = !completedExpanded,
    completedCount = completedItems.size
))
```

## Files to modify

| File | Changes |
|------|---------|
| `ChecklistDetailScreenContract.kt` | Add 3 new intents |
| `ChecklistDetailViewModel.kt` | Handle 3 new intents + enhance 2 existing events |
| `ChecklistDetailScreen.kt` | Send new intents from UI callbacks |

## Acceptance Criteria

- [ ] `overflow_menu_opened` fires when tapping MoreVert
- [ ] `completed_section_expanded` / `collapsed` fires with correct `completed_count`
- [ ] `quick_add_opened` fires when tapping "+"
- [ ] `quick_add_cancelled` fires with `had_text` on dismiss
- [ ] `item_added_quick` includes `checklist_id` and `item_count`
- [ ] `checklist_deleted` includes `checklist_id`, `item_count`, `source`
- [ ] `separate_completed_toggled` unchanged (already has `enabled`)
- [ ] All event names follow `snake_case` convention
- [ ] All param values are strings (consistent with existing pattern)
- [ ] Unit tests for each new intent handler in `ChecklistDetailViewModelTest`

## Event Naming Convention (existing pattern)

```
{action}_{object}      → "overflow_menu_opened", "item_added_quick"
{object}_{action}      → "checklist_deleted", "completed_section_expanded"
```

Both patterns exist in the codebase. For this feature: `{object}_{action}` for sections/objects, `{action}_{object}` for user actions.

## References

- AnalyticsTracker: `core/common/api/.../AnalyticsTracker.kt`
- Existing detail events: `feature/home/.../ChecklistDetailViewModel.kt`
- Analytics implementation: `composeApp/.../Analytics.kt` (dual Firebase + Amplitude)
- Testing mandate: `docs/solutions/ui-bugs/checklist-detail-overflow-menu-quick-add-bugs.md`
