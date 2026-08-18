---
title: Fix Widget Broken After List to Set Change
type: fix
date: 2026-01-30
---

# Fix Widget Broken After List→Set Change

## Overview

Widget worked yesterday with `List<ChecklistFillItem>`, but broke today after changing domain models to use `Set<ChecklistFillItem>`.

## Problem Analysis

### What Changed

1. **Domain models** (`Checklist.kt`):
   - `Checklist.items`: `List<ChecklistItem>` → `Set<ChecklistItem>`
   - `ChecklistFill.items`: `List<ChecklistFillItem>` → `Set<ChecklistFillItem>`
   - Added custom `equals/hashCode` to `Checklist` and `ChecklistFill`

2. **Entity models** (unchanged type, but added equals):
   - `ChecklistEntity.items`: still `List<ChecklistItem>`
   - `ChecklistFillEntity.items`: still `List<ChecklistFillItem>`
   - Added custom `equals/hashCode` to both entities

3. **Widget data**:
   - `ChecklistWidgetData.items`: still `List<ChecklistFillItem>`
   - Added custom `equals/hashCode`

### Widget Data Flow

```
Room DB (List)
    ↓
ChecklistFillEntity.items: List<ChecklistFillItem>
    ↓
WidgetRepository.observeChecklistWithDefaultFill()
    ↓
ChecklistWidgetData.items: List<ChecklistFillItem>
    ↓
ChecklistWidgetContent → Glance UI
```

**Key Observation**: Widget uses Entity directly, NOT domain model. So Set change should NOT affect widget.

### Root Cause Hypothesis

The problem is NOT the List→Set change in domain models. The problem is likely:

**Option A**: Custom `equals/hashCode` in `ChecklistFillEntity` or `ChecklistWidgetData` breaks Room Flow detection or Glance recomposition.

**Option B**: Runtime error somewhere (need to check logs).

**Option C**: The `observeAllChecklists()` in config screen returns domain `Checklist` with `Set<ChecklistItem>` - this works for display but may cause issues if config screen tries to access items by index.

## Solution: Rollback Custom equals from Entities

The safest fix is to **remove custom equals/hashCode from Entity classes and ChecklistWidgetData**. These were added to fix a Compose recomposition issue, but they may be causing unintended side effects with Room/Glance.

### Files to Modify

1. **`ChecklistEntity.kt`** - Remove custom equals/hashCode
2. **`ChecklistFillEntity.kt`** - Remove custom equals/hashCode
3. **`ChecklistWidgetData.kt`** - Remove custom equals/hashCode

### Keep Custom equals In

- `Checklist` (domain) - needed for proper Compose state comparison
- `ChecklistFill` (domain) - needed for proper Compose state comparison
- `ChecklistItem` (for Set uniqueness)
- `ChecklistFillItem` (for Set uniqueness)

## Acceptance Criteria

- [ ] Widget displays checklist items correctly
- [ ] Widget toggle (checkbox) works and persists
- [ ] Widget config screen shows list of checklists
- [ ] Main app checklist screen still updates properly on checkbox toggle

## Implementation Steps

1. Remove custom `equals/hashCode` from `ChecklistEntity`
2. Remove custom `equals/hashCode` from `ChecklistFillEntity`
3. Remove custom `equals/hashCode` from `ChecklistWidgetData`
4. Test widget functionality
5. If widget works but main app breaks, find alternative solution

## Alternative Solution (If Above Doesn't Work)

If removing custom equals doesn't fix widget, the issue may be elsewhere. Next steps:
- Check Android logcat for runtime errors
- Add logging to widget update flow
- Test with minimal reproduction case
