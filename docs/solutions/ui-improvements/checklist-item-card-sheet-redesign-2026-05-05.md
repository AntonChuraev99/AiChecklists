---
title: "ChecklistItemCard Redesign — Sheet-Based Details (30/70 Hit-Zone Split)"
date: 2026-05-05
type: ui-redesign
modules: [feature/home, core/designsystem]
keywords: [30-70-hit-split, modal-bottom-sheet, item-details-sheet, reminder-chip, hit-zone-pattern, things-style-ux, checkbox-toggle, sheet-routing]
project: Checklists
---

# ChecklistItemCard Redesign — Sheet-Based Item Actions

## Problem / Context

Prior card layout displayed inline action buttons (bottom row with [📄 Note][🔔 Remind]) alongside the checkbox and text, creating visual clutter and reducing focus on the primary action (checkbox toggle + read text). Users expect this interaction pattern in modern iOS/macOS apps (Things, Apple Reminders, Fantastical) — sheet-based details on item tap.

## Solution

Replaced inline button row with unified sheet-based detail panel:
- **30% left zone** (hitbox): checkbox toggle (no visual changes)
- **70% right zone** (hitbox): item text + optional reminder chip + tap to open ItemDetailsSheet
- **ItemDetailsSheet** (ModalBottomSheet): title, Reminder row (with state preview), Note row (with preview), Delete button (destructive)

### Architecture: 30/70 Hit-Zone Split

```kotlin
Box(modifier = Modifier.weight(0.30f).clickable { onCheckClick() }) {
    Checkbox(
        checked = item.checked,
        onCheckedChange = null,  // Tap handled by parent Box
        modifier = Modifier.size(24.dp)
    )
}
Box(
    modifier = Modifier
        .weight(0.70f)
        .clickable { onItemTap() }
) {
    Column {
        Text(item.text)
        if (item.reminderAt != null) {
            ReminderChip()  // Bonus: state-at-a-glance
        }
    }
}
```

**Why 30/70 split?**
- Checkbox visual footprint ≈ 24dp with padding
- Text column takes remaining space (natural layout)
- Hit zones scale with content (no fixed dp breakpoints)
- Each Box gets independent ripple
- Touch target inherently respects visual boundaries

### ItemDetailsSheet Structure

```kotlin
ModalBottomSheet(
    onDismissRequest = { dismissSheet() },
    skipPartiallyExpanded = true  // Full-screen modal on open
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(item.text, style = headlineSmall)
        Divider()
        ItemDetailsSheetRow(
            icon = Icons.Outlined.Notifications,
            title = "Reminder",
            subtitle = formatItemReminderLabel(item),
            onClick = { dismissSheet(); onItemReminderClick() }
        )
        ItemDetailsSheetRow(
            icon = Icons.Outlined.NoteAdd,
            title = "Note",
            subtitle = if (item.note != null) item.note else "Tap to add",
            onClick = { dismissSheet(); onAddNoteClick() }
        )
        ItemDetailsSheetRow(
            icon = Icons.Outlined.Delete,
            title = "Delete item",
            titleColor = Color.Red,
            onClick = { dismissSheet(); onDeleteItemFromSheet() }
        )
    }
}
```

### Routing Pattern: Approach A (Close → Fire)

When user taps Reminder/Note/Delete from sheet:

```kotlin
// Handler in ChecklistDetailViewModel
fun onItemReminderClick(itemId: String) {
    updateContentState { it.copy(itemDetailsSheetFor = null) }  // Close sheet
    // Existing reminder logic follows
}
```

**Benefits:**
- Atomic state mutation (sheet always closed before action)
- Clean modal stacking (no nested ReminderSheet + ItemDetailsSheet overlaps)
- Reuses all existing intents (OnItemReminderClick, OnAddNoteClick, OnDeleteItem)
- Free-tier paywall gate on ReminderSheet still works (downstream flow unchanged)

### Delete Path: OnDeleteItemFromSheet (not reuse OnSwipeDeleteItem)

Swipe-delete has undo flow with snackbar. Sheet-delete is final, intentional action. Separate intents allow different UX:

```kotlin
// Swipe path: undo snackbar
fun onSwipeDeleteItem(itemId: String) { 
    showUndoSnackbar() 
}

// Sheet path: direct confirmation
fun onDeleteItemFromSheet(itemId: String) {
    deleteItem(itemId)  // Immediate, no snackbar
    scheduler.cancelItemReminder(itemId)
}
```

Both paths call `scheduler.cancel*()` cleanup.

## Why This Approach

1. **Pattern alignment:** Things, Apple Reminders, Fantastical use sheet-based details on item tap. Users expect this UX.

2. **Hit-zone pattern scales:** Applicable to any list card with dual action types (checkbox + details). Reusable for future tasks (Settings preferences, Note-taking app, Task lists).

3. **Reminder chip bonus:** Moved reminder/note state from inline buttons to card-level chip (readable at-a-glance, no tap needed). Addresses "I can't see my reminder until I tap the card" UX complaint.

4. **Sheet routing keeps ReminderSheet/NoteDialog independent:** No re-architecting of existing flows. Sheet is dumb details panel; actual logic lives in downstream dialogs.

## Examples

### Before (Inline Buttons)
```
┌─────────────────────────────────────┐
│ ☑ Buy groceries [📄 Note][🔔 Remind]│
└─────────────────────────────────────┘
```

### After (Sheet-Based)
```
┌─────────────────────────────────────┐
│ ☑ Buy groceries    🔔 Tomorrow 9am   │  ← chip optional
└─────────────────────────────────────┘
        ↓ tap 70% zone
╔═════════════════════════════════════╗
║ Buy groceries                       ║ (sheet full-screen)
║───────────────────────────────────────
║ 🔔 Reminder       Tomorrow 9am → >  ║
║ 📝 Note           Tap to add      → ║
║ 🗑️  Delete item                      ║
╚═════════════════════════════════════╝
```

## Related Files

- `feature/home/src/commonMain/kotlin/.../ChecklistItemCard.kt` — 30/70 Box split + reminder chip
- `feature/home/src/commonMain/kotlin/.../ChecklistDetailScreen.kt` — ItemDetailsSheet + ModalBottomSheet setup
- `feature/home/src/commonMain/kotlin/.../ChecklistDetailViewModel.kt` — Approach A routing, close-then-fire pattern
- `feature/home/src/commonTest/kotlin/.../ChecklistDetailItemDetailsSheetTest.kt` — 13 unit tests (routing, gate logic, delete cleanup)
- `core/designsystem/src/commonMain/composeResources/values/strings.xml` — 6 new string keys + 5 retained

## Compound Effect & Iterations

- **Total iterations:** 2 (mobile-design-expert Phase 1, android-expert Phase 2)
- **Compile errors:** 0 (smart-cast pattern from prior task internalized; cross-module nullable fields guarded explicitly)
- **Effect:** Baseline=2, actual=2, but zero fix-cycles. Smart-cast pattern effective across multiple tasks.

## Testing

- **Unit:** 13 new tests (routing sequencing, free-tier gate intact, delete cancels reminders)
- **Manual smoke:** Visual check on Pixel_9 API 36.1 — sheet open/close, item tap, reminder/note/delete flows ✅

## Visuals / Polish (Follow-Up)

- ItemDetailsSheet title needs 16-24dp padding-start (currently flush left)
- Subtitles "Tap to set" / "Tap to add" redundant given chevron — show only when state exists
- 16dp spacing before Delete row (visual separation from actions above)
- Icons.Outlined.NoteAdd → AutoMirrored variant (deprecation warning)

---

**Keywords:** 30-70-hit-split, modal-bottom-sheet, item-details-sheet, reminder-chip, hit-zone-pattern, things-style-ux, checkbox-toggle, sheet-routing, approach-a-pattern, reusable-pattern
