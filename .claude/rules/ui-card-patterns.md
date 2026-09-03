---
paths:
  - "**/*Card*.kt"
  - "**/*ItemDetailsSheet*.kt"
  - "**/*Pager*.kt"
  - "**/feature/home/**"
---

# UI best practices — cards, pagers, per-item actions

## Text in HorizontalPager — MUST `fillMaxWidth()`

Text inside `HorizontalPager` MUST have `fillMaxWidth()` or it overflows:

```kotlin
Text(text = "...", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
```

## Avoid double padding

When a parent already has horizontal padding, children must NOT add their own:

```kotlin
Column(modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal)) {
    Text(modifier = Modifier.fillMaxWidth())               // Correct
    Text(modifier = Modifier.padding(horizontal = 16.dp)) // Wrong — double padding
}
```

## Per-item actions belong in `ItemDetailsSheet`, NOT on `ChecklistItemCard`

**RULE:** Any new per-item action/toggle/setting (priority/star, due date, tags, color, archive…) MUST be a row in `ItemDetailsSheet` — never a button on `ChecklistItemCard`.

**Why:** `ChecklistItemCard` uses a 30/70 hit-zone split (left 30% toggles checkbox, right 70% opens `ItemDetailsSheet`). New clickable elements on the card break this, eat touch targets, and Frankenstein the card. See `docs/solutions/ui-improvements/checklist-item-card-sheet-redesign-2026-05-05.md`.

On the card itself **only lightweight read-only visual indicators** are allowed (reminder chip, priority/star icon, future tag stripe) — they MUST NOT have their own `clickable` modifier or hit-zone.

```kotlin
// CORRECT: indicator is visual only, sheet handles toggle
Row {
    Text(item.text)
    if (item.priority > 0) Icon(Icons.Filled.Star, null)  // no clickable
}

// WRONG: button on card, breaks 30/70 hit-zone
Row {
    Text(item.text)
    IconButton(onClick = onToggleStar) { Icon(Icons.Filled.Star, null) }
}
```

The action itself goes in the sheet:

```kotlin
ItemDetailsSheet(...) {
    Row(...)  // Reminder
    Row(...)  // Note
    Row(onClick = onTogglePriority) {  // ← new actions go HERE
        Icon(Icons.Filled.Star, null)
        Text(if (item.priority > 0) "Remove importance" else "Mark as important")
    }
    Row(...)  // Delete
}
```
