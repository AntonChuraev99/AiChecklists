---
title: Overflow Menu + Collapsible Completed Section + Quick Add Item
type: feat
date: 2026-02-26
deepened: 2026-02-26
---

# feat: Overflow menu, collapsible completed section, quick add item

## Enhancement Summary

**Deepened on:** 2026-02-26
**Research agents used:** Learnings researcher, Compose best practices, Architecture strategist, Code simplicity reviewer, UI designer

### Key Improvements (vs original plan)
1. **Bug fix**: `noteDialogItemIndex` must also migrate to id-based — partition causes wrong-item note save
2. **Dropped dual-write**: Add item to `defaultFill` ONLY, not to checklist template — avoids ID mismatch bug in `updateChecklist()` sync
3. **`derivedStateOf`** instead of plain `remember` for partition — only recomputes on actual list change
4. **`completedSectionExpanded`** moved from MVI state to local `remember` — pure visual state doesn't need ViewModel
5. **Dropped `BackHandler`** in AddItemInput — not needed, standard Compose handles it
6. **Analytics simplified**: 2 events instead of 4
7. **`animateItem()`** added to all LazyColumn items — smooth cross-section movement animation
8. **FocusRequester**: added `awaitFrame()` after scroll for reliable keyboard opening
9. **Completed section expanded by default** — users need to discover the pattern first
10. **Added empty state** for "all completed + collapsed" scenario

### Decisions That Need User Input
- **Toolbar layout**: "+" in toolbar (user request) vs inline list row (reviewer recommendation). See "Quick Add" section.
- **DataStore (global)** vs Room column (per-checklist). See "Technical Approach" section.

---

## Overview

Declutter the ChecklistDetail toolbar by introducing an overflow menu (⋮), add quick item addition via "+" button, and implement a collapsible "Completed" section that groups checked items separately for better focus on uncompleted tasks.

## Problem Statement / Motivation

1. **Toolbar clutter**: 4 action icons (Reminder, Share, Edit, Delete) crowd the toolbar — Delete is destructive and rarely used, doesn't belong as a primary action
2. **No quick add**: Adding items requires navigating to the Edit screen — too many taps for a simple action
3. **Visual noise**: Completed items mix with uncompleted ones, making it hard to focus on remaining tasks in long checklists

## Proposed Solution

### 1. Overflow Menu (⋮)

Replace the Delete icon with a "⋮" (More) icon. Tapping opens a `ModalBottomSheet` with:
- **Separate completed** — toggle switch (off by default, persisted in DataStore)
- **Delete Checklist** — red text with delete icon, triggers existing confirmation dialog

> **Research Insight:** Switch toggle should NOT dismiss the sheet. `onDismissRequest` only fires on swipe-down/scrim-tap, not on in-sheet interactions. Sheet stays open after toggle.

### 2. Quick Add Item (+)

**Option A (user request):** Add "+" icon to toolbar. Tapping reveals an inline `TextField` at the bottom of the items list.

**Option B (reviewer recommendation):** No toolbar icon. Instead, a persistent "Add item..." row as the last item in `LazyColumn` (like Todoist/Notion). Tapping expands into the TextField in-place. More discoverable, eliminates `showAddItemInput` state field.

Both options share the same behavior:
- Auto-scrolls LazyColumn to the input + auto-focuses keyboard
- Enter/Done adds the item and clears the field (ready for next item)
- New item is added to `defaultFill.items` **ONLY** (not checklist template)

> **Research Insight (dropped dual-write):** The original plan added items to both fill and template. This is wrong because `updateChecklist()` sync logic associates items by `text`, not `id` — creating mismatched IDs between template and fill. Adding only to `defaultFill` avoids this entirely. Template changes belong to the Edit screen.

### 3. Collapsible Completed Section

When "Separate completed" toggle is ON:
- Unchecked items displayed at the top (preserving their relative order from original list)
- `HorizontalDivider` + "Completed (N)" clickable header with expand/collapse chevron below
- Checked items **expanded by default** (so users discover the pattern), collapsible on tap
- Checking an item moves it from unchecked section to completed section (animated via `animateItem()`)
- Unchecking restores item to unchecked section

When OFF (default): current behavior, all items in their stored order.

> **Research Insight (expanded by default):** Start expanded on first encounter so users see the feature before they opt into collapsing. Hiding completed items on first view is confusing — progress seems to disappear.

## New Toolbar Layout

**Option A (user request, 5 icons):**
```
[←]                    [🔔] [↗] [✏] [+] [⋮]
 Back                  Bell Share Edit Add More
```

**Option B (reviewer recommendation, 4 icons):**
```
[←]                    [🔔] [↗] [✏] [⋮]
 Back                  Bell Share Edit More
```
"+" becomes inline list row instead. Material 3 recommends max 3 action icons; 4 is the practical upper limit.

## Technical Approach

### Files to Modify

| File | Changes |
|------|---------|
| `ChecklistDetailScreenContract.kt` | New state fields + new intents + **refactor ALL index-based intents to id-based** (including notes) |
| `ChecklistDetailViewModel.kt` | Overflow/toggle/add-item logic, DataStore read/write, id-based item operations |
| `ChecklistDetailScreen.kt` | Toolbar, overflow sheet, collapsible section, inline add input, `animateItem()` |
| `strings.xml` | New string resources |

### Critical Refactor: Index-Based → ID-Based Item Operations

Current intents use list index which breaks when items are partitioned into unchecked/checked groups.

> **Research Insight (bug found):** `noteDialogItemIndex: Int?` must also be migrated — with partition active, saving a note writes to the WRONG item because the visual index in the partitioned list doesn't match the index in `defaultFill.items`.

```kotlin
// BEFORE (breaks with partitioned lists)
data class OnItemCheckedChange(val index: Int, val checked: Boolean)
data class OnAddNoteClick(val index: Int)
// noteDialogItemIndex: Int? = null  ← in Content state

// AFTER (robust, works with any list arrangement)
data class OnItemCheckedChange(val itemId: String, val checked: Boolean)
data class OnAddNoteClick(val itemId: String)
// noteDialogItemId: String? = null  ← in Content state
```

ViewModel handlers change from index lookup to id lookup:

```kotlin
// AFTER — updateItemChecked
private fun updateItemChecked(itemId: String, checked: Boolean) {
    val state = _screenState.value as? ChecklistDetailState.Content ?: return
    val fill = state.defaultFill ?: return
    val updatedItems = fill.items.map { item ->
        if (item.id == itemId) item.withChecked(checked) else item
    }
    val updatedFill = fill.copy(items = updatedItems)
    viewModelScope.launch { repository.updateFill(updatedFill) }
}

// AFTER — openNoteDialog
private fun openNoteDialog(itemId: String) {
    val state = _screenState.value as? ChecklistDetailState.Content ?: return
    val fill = state.defaultFill ?: return
    val currentNote = fill.items.firstOrNull { it.id == itemId }?.note.orEmpty()
    updateContentState { it.copy(noteDialogItemId = itemId, editingNote = currentNote) }
}

// AFTER — saveNote
private fun saveNote() {
    val state = _screenState.value as? ChecklistDetailState.Content ?: return
    val fill = state.defaultFill ?: return
    val itemId = state.noteDialogItemId ?: return
    val updatedItems = fill.items.map { item ->
        if (item.id == itemId) item.withNote(state.editingNote.takeIf { it.isNotBlank() })
        else item
    }
    val updatedFill = fill.copy(items = updatedItems)
    viewModelScope.launch {
        repository.updateFill(updatedFill)
        updateContentState { it.copy(noteDialogItemId = null, editingNote = "") }
    }
}
```

> **Research Insight:** `ChecklistFillItem` uses private constructor + `withChecked()`/`withNote()` helpers. Cannot use `copy()` directly from ViewModel — must use these helper methods.

### State Changes (ChecklistDetailScreenContract.kt)

Add to `Content`:

```kotlin
data class Content(
    // ... existing fields ...

    // Overflow menu
    val showOverflowSheet: Boolean = false,
    val separateCompleted: Boolean = false,

    // Quick add item
    val showAddItemInput: Boolean = false,  // Option A only; Option B uses local remember
    val newItemText: String = "",
) : ChecklistDetailState
```

> **Research Insight (dropped from MVI):** `completedSectionExpanded` is purely visual transient state. Use local `var completedExpanded by remember { mutableStateOf(true) }` in the composable instead. Saves ~8 lines of Contract/ViewModel boilerplate.

Also migrate existing field:
```kotlin
// BEFORE
val noteDialogItemIndex: Int? = null,
// AFTER
val noteDialogItemId: String? = null,
```

### New Intents

```kotlin
// Overflow menu
data object OnOverflowMenuClick : ChecklistDetailIntent
data object OnDismissOverflowSheet : ChecklistDetailIntent
data object OnToggleSeparateCompleted : ChecklistDetailIntent

// Quick add item
data object OnAddItemClick : ChecklistDetailIntent        // Option A only
data class OnNewItemTextChanged(val text: String) : ChecklistDetailIntent
data object OnConfirmAddItem : ChecklistDetailIntent
data object OnDismissAddItemInput : ChecklistDetailIntent  // Option A only
```

> **Research Insight (dropped):** `OnToggleCompletedSection` intent removed — handled by local `remember` state.

### ViewModel Logic (ChecklistDetailViewModel.kt)

**DataStore preference:**

```kotlin
companion object {
    private const val PREF_SEPARATE_COMPLETED = "separate_completed_items"
    // existing:
    const val PREF_EXACT_ALARM_DONT_SHOW = "exact_alarm_dont_show"
}
```

> **Research Insight (DataStore scope):** Global preference (same for all checklists) is simpler but means toggling for one checklist affects all. For v1 this is acceptable. Future improvement: migrate to per-checklist Room column via `ALTER TABLE checklists ADD COLUMN separateCompleted INTEGER NOT NULL DEFAULT 0`.

**Init — load preference (add to existing `init` block):**

```kotlin
viewModelScope.launch {
    datastore.observeBoolean(PREF_SEPARATE_COMPLETED, false).collect { value ->
        updateContentState { it.copy(separateCompleted = value) }
    }
}
```

> **Research Insight:** Reuse the existing injected `datastore: AppDatastore` (already used for `PREF_EXACT_ALARM_DONT_SHOW`). No new DI changes needed. Singleton pattern is already enforced via `UserAppDatastoreProvider.instance`.

**Toggle handler:**

```kotlin
private fun toggleSeparateCompleted() {
    val current = (_screenState.value as? ChecklistDetailState.Content)?.separateCompleted ?: false
    viewModelScope.launch {
        datastore.saveBoolean(PREF_SEPARATE_COMPLETED, !current)
        // DataStore Flow will update state automatically via collector above
    }
    analyticsTracker.event(
        "separate_completed_toggled",
        mapOf("enabled" to (!current).toString())
    )
}
```

**Add item handler (simplified — fill only, no template write):**

```kotlin
private fun addItem() {
    val state = _screenState.value as? ChecklistDetailState.Content ?: return
    val text = state.newItemText.trim()
    if (text.isEmpty()) return

    val fill = state.defaultFill ?: return
    val newItem = ChecklistFillItem.create(text = text, checked = false)
    val updatedFill = fill.copy(items = fill.items + newItem)

    viewModelScope.launch {
        repository.updateFill(updatedFill)
        updateContentState { it.copy(newItemText = "") }
        analyticsTracker.event("item_added_quick")
    }
}
```

### UI Changes (ChecklistDetailScreen.kt)

#### Toolbar (Option A — with "+")

```kotlin
actions = {
    // Reminder bell (existing — unchanged)
    IconButton(onClick = { onIntent(OnReminderClick) }) {
        Icon(
            imageVector = if (state.checklist.reminderAt != null)
                Icons.Filled.Notifications else Icons.Outlined.Notifications,
            contentDescription = stringResource(Res.string.reminder_set_reminder),
            tint = if (state.checklist.reminderAt != null)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    // Share (existing — unchanged)
    IconButton(onClick = { onIntent(OnShareClick) }) {
        Icon(Icons.Outlined.Share, contentDescription = stringResource(Res.string.share))
    }
    // Edit (existing — unchanged)
    IconButton(onClick = { onIntent(OnEditChecklistClick) }) {
        Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.checklist_edit))
    }
    // NEW: Add item
    IconButton(onClick = { onIntent(OnAddItemClick) }) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = stringResource(Res.string.checklist_add_item)
        )
    }
    // NEW: Overflow menu (replaces Delete icon)
    IconButton(onClick = { onIntent(OnOverflowMenuClick) }) {
        Icon(
            Icons.Default.MoreVert,
            contentDescription = stringResource(Res.string.more_options)
        )
    }
}
```

#### LazyColumn with Collapsible Section

```kotlin
// Use derivedStateOf — recomputes only when items or separateCompleted actually change
val (uncheckedItems, checkedItems) by remember {
    derivedStateOf {
        if (state.separateCompleted) {
            defaultFill.items.partition { !it.checked }
        } else {
            defaultFill.items to emptyList()
        }
    }
}

// Local state for expand/collapse (not in ViewModel — purely visual)
var completedExpanded by remember { mutableStateOf(true) }

val listState = rememberLazyListState()

LazyColumn(state = listState, ...) {
    item { /* Checklist title */ }
    item { ProgressHeader(fill = defaultFill) }

    if (state.additionalFillsCount > 0) {
        item { ViewAllFillsCard(...) }
    }

    // Unchecked items (or all items when separateCompleted is OFF)
    itemsIndexed(uncheckedItems, key = { _, item -> item.id }) { _, item ->
        ChecklistItemCard(
            item = item,
            modifier = Modifier.animateItem(),  // smooth position animation
            onCheckedChange = { checked ->
                onIntent(OnItemCheckedChange(item.id, checked))
            },
            onNoteClick = { onIntent(OnAddNoteClick(item.id)) }
        )
    }

    // Inline add item input (Option A: toggled by toolbar "+")
    if (state.showAddItemInput) {
        item(key = "add_item_input") {
            AddItemInput(...)
        }
    }

    // Empty state: all items completed + section collapsed
    if (state.separateCompleted && uncheckedItems.isEmpty() && checkedItems.isNotEmpty() && !completedExpanded) {
        item(key = "all_completed_message") {
            Text(
                text = stringResource(Res.string.all_items_completed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppDimens.SpacingXl)
            )
        }
    }

    // Completed section (only when separateCompleted ON and has checked items)
    if (state.separateCompleted && checkedItems.isNotEmpty()) {
        item(key = "completed_divider") {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppDimens.SpacingSm),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        item(key = "completed_header") {
            CompletedSectionHeader(
                count = checkedItems.size,
                expanded = completedExpanded,
                onClick = { completedExpanded = !completedExpanded }
            )
        }

        if (completedExpanded) {
            itemsIndexed(checkedItems, key = { _, item -> item.id }) { _, item ->
                ChecklistItemCard(
                    item = item,
                    modifier = Modifier.animateItem(),
                    onCheckedChange = { checked ->
                        onIntent(OnItemCheckedChange(item.id, checked))
                    },
                    onNoteClick = { onIntent(OnAddNoteClick(item.id)) }
                )
            }
        }
    }

    item { Spacer(modifier = Modifier.height(AppDimens.SpacingMd)) }
}
```

> **Research Insight (keys):** CRITICAL: Use same `item.id` as key in BOTH sections. Never prefix keys like `"completed_${item.id}"` — this breaks cross-section movement animation. LazyColumn needs the same key to animate the item sliding from one position to another.

> **Research Insight (animateItem):** `Modifier.animateItem()` is stable since Compose 1.7 — no `@OptIn` needed. Handles fade-in, fade-out, and placement animation. The existing `item.id` UUID keys provide the stable identity required.

#### Auto-scroll + Focus after "+" tap

```kotlin
// After showing the add input, scroll to it and focus
LaunchedEffect(state.showAddItemInput) {
    if (state.showAddItemInput) {
        // Wait for composition to include the new item
        kotlinx.coroutines.delay(100) // small delay for layout
        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }
}
```

> **Research Insight:** `FocusRequester.requestFocus()` inside a `LazyColumn` item should use `LaunchedEffect(Unit)` which runs after composition. If combining with scroll, add `awaitFrame()` after scroll to ensure the item is laid out before focusing.

#### New Composables

**OverflowMenuSheet:**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverflowMenuSheet(
    separateCompleted: Boolean,
    onToggleSeparateCompleted: () -> Unit,
    onDeleteChecklist: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl)
        ) {
            // Toggle: Separate completed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSeparateCompleted() }
                    .padding(vertical = AppDimens.SpacingLg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(Res.string.separate_completed),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Switch(
                    checked = separateCompleted,
                    onCheckedChange = { onToggleSeparateCompleted() }
                )
            }

            HorizontalDivider()

            // Delete checklist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDeleteChecklist() }
                    .padding(vertical = AppDimens.SpacingLg),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    stringResource(Res.string.delete_checklist),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
```

> **Research Insight:** Use `rememberModalBottomSheetState()` even if not programmatically hiding. The Row for "Separate completed" must also be `clickable` (the whole row toggles, not just the Switch) — matches 48dp min touch target.

**CompletedSectionHeader:**

```kotlin
@Composable
private fun CompletedSectionHeader(
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppDimens.SpacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.completed_count, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess
                         else Icons.Default.ExpandMore,
            contentDescription = if (expanded)
                stringResource(Res.string.collapse)
            else stringResource(Res.string.expand),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

> **Research Insight:** Chevron on the right side (SpaceBetween), text on the left. `titleSmall` at `onSurfaceVariant` (Gray600) signals secondary content. The completed items' existing strikethrough + Gray600 text is sufficient visual distinction — no additional opacity reduction needed.

**AddItemInput:**

```kotlin
@Composable
private fun AddItemInput(
    text: String,
    onTextChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    listState: LazyListState
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Item is inside LazyColumn — wait for layout before focusing
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            // FocusRequester not yet initialized — safe to ignore
        }
    }

    // No BackHandler — standard Compose back handling is sufficient

    AppTextField(
        value = text,
        onValueChange = onTextChanged,
        placeholder = stringResource(Res.string.checklist_add_item_placeholder),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onConfirm() }),
        trailingIcon = {
            IconButton(onClick = onConfirm) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = stringResource(Res.string.save),
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
```

> **Research Insight (dropped BackHandler):** Not needed. The inline TextField is not a modal — when the user presses back, normal Compose navigation handles it. Adding BackHandler on an inline field conflicts with sheet dismissal logic if any sheet is open.

> **Research Insight (FocusRequester safety):** Wrap `requestFocus()` in try-catch for `IllegalStateException` — the composable may not be laid out yet in LazyColumn. Adding a small delay (100ms) before focus is more reliable than `awaitFrame()` for this use case.

### String Resources

```xml
<string name="more_options">More options</string>
<string name="separate_completed">Separate completed</string>
<string name="completed_count">Completed (%d)</string>
<string name="checklist_add_item">Add item</string>
<string name="checklist_add_item_placeholder">Add new item…</string>
<string name="delete_checklist">Delete Checklist</string>
<string name="collapse">Collapse</string>
<string name="expand">Expand</string>
<string name="all_items_completed">All items completed</string>
```

### Analytics Events

| Event | When | Params |
|-------|------|--------|
| `separate_completed_toggled` | Toggle changed in overflow | `enabled: Boolean` |
| `item_added_quick` | Item added via quick-add | — |

> **Research Insight (simplified):** Dropped `completed_section_toggled` and `overflow_menu_opened` — pure UI state events with no product decision value. Two events are enough to track feature adoption.

## Acceptance Criteria

### Overflow Menu
- [ ] Delete icon removed from toolbar, replaced with "⋮" (MoreVert) icon
- [ ] Tapping "⋮" opens ModalBottomSheet with toggle + delete options
- [ ] "Separate completed" shows as toggle (Switch), off by default
- [ ] Toggle does NOT dismiss the sheet — sheet stays open
- [ ] "Delete Checklist" shown in red, triggers existing `DeleteConfirmationDialog`
- [ ] Sheet dismisses on swipe-down and outside-tap

### Collapsible Completed Section
- [ ] When toggle ON: unchecked items at top, divider + "Completed (N)" header below
- [ ] Completed section **expanded** by default on every screen open
- [ ] Tapping header collapses/expands completed items
- [ ] Checking an item moves it from unchecked to completed section (animated via `animateItem()`)
- [ ] Unchecking a completed item moves it back to unchecked section
- [ ] Toggle preference persists across app restarts (DataStore)
- [ ] When toggle OFF: all items in original stored order (current behavior)
- [ ] When all items completed + section collapsed: show "All items completed" message

### Quick Add Item
- [ ] Quick add mechanism available (toolbar "+" or inline row — TBD)
- [ ] Tapping shows inline TextField at end of unchecked items
- [ ] TextField auto-focused with keyboard open
- [ ] Enter/Done adds item to `defaultFill` only (NOT checklist template)
- [ ] Field clears after adding, ready for next item
- [ ] Empty text does nothing on confirm

### Index → ID Refactor (all three)
- [ ] `OnItemCheckedChange` uses `itemId: String` instead of `index: Int`
- [ ] `OnAddNoteClick` uses `itemId: String` instead of `index: Int`
- [ ] `noteDialogItemIndex: Int?` → `noteDialogItemId: String?` in Content state
- [ ] `openNoteDialog()` and `saveNote()` use id-based lookup
- [ ] All existing check/uncheck and note functionality works correctly after refactor

## Edge Cases

1. **All items completed + separate ON + collapsed**: Show "All items completed" text + collapsed header
2. **All items completed + separate ON + expanded**: Only "Completed (N)" header + checked items visible
3. **No items completed + separate ON**: All items shown normally, no "Completed" header or divider
4. **Toggle OFF while section expanded**: Items return to original mixed order seamlessly
5. **Quick add while separate ON**: New item appears at bottom of unchecked section
6. **Delete from overflow**: Sheet dismisses first, then confirmation dialog shows
7. **Quick add empty text**: Confirm button and Enter do nothing
8. **Very long checklist (50+ items)**: `derivedStateOf` + `partition` is O(n) per list change, LazyColumn virtualizes rendering
9. **Item with same text but different id**: ID-based lookup is correct; text-based would match wrong item
10. **FocusRequester not ready**: try-catch prevents crash if LazyColumn hasn't laid out the input item yet

## Dependencies & Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| 5 toolbar icons cramped on small screens | UI overflow/truncation | Test on 320dp. Fallback: move Edit to overflow OR use inline add row |
| Item animation between sections | Janky if keys don't match | Use same `item.id` key in BOTH sections. NEVER prefix keys by section |
| Index→ID refactor breaks existing tests | Test failures | Update all test assertions from index to id-based. Scope is small: 3 intents + 2 VM methods |
| DataStore global pref affects all checklists | User surprise | Acceptable for v1. Future: per-checklist Room column migration |
| `updateChecklist()` text-based sync creates ID mismatch | Broken animation keys | AVOIDED by only writing to fill, not template. Edit screen handles template changes |
| FocusRequester crash in LazyColumn | App crash | try-catch with delay for reliable focus |

## Future Improvements

1. **Per-checklist toggle**: Migrate `separateCompleted` from DataStore to Room column on `ChecklistEntity`
2. **Overlay state refactor**: Group 12+ `show*` boolean flags in `Content` into a nested `OverlayState` data class
3. **Option B for quick add**: Replace toolbar "+" with persistent inline "Add item..." row in LazyColumn
4. **Move Edit to overflow**: If 5 icons are too many, Edit can join the overflow sheet

## References

### Codebase Patterns
- Existing ModalBottomSheet pattern: `ChecklistDetailScreen.kt` (FillTargetSheet, ReminderSheet, ExactAlarmSheet)
- DataStore preference pattern: `ChecklistDetailViewModel.kt` (PREF_EXACT_ALARM_DONT_SHOW)
- Item model with stable IDs: `feature/checklist/domain/model/Checklist.kt`
- `withChecked()`/`withNote()` helpers: private constructor pattern from `enforced-auto-generated-id-pattern.md`
- `updateContentState()` helper: reuse for all new intent handlers

### Documented Learnings Applied
- `docs/solutions/runtime-errors/datastore-multiple-instances-crash.md` — use injected singleton AppDatastore
- `docs/solutions/features/enforced-auto-generated-id-pattern.md` — item ID pattern, `withChecked()` usage
- `docs/solutions/features/csat-survey-with-in-app-review.md` — ModalBottomSheet canonical pattern
- `docs/solutions/features/exact-alarm-reminders-upgrade.md` — DataStore pref in same ViewModel
- `docs/solutions/ui/design-system-patterns.md` — AppScaffold actions slot, typography, spacing
- `docs/solutions/architecture/mvi-pattern.md` — intent/state conventions

### External Research
- `Modifier.animateItem()` — stable since Compose 1.7, no @OptIn needed
- `derivedStateOf` for partition — Android performance best practices
- `BackHandler` in KMP — available via `ui-backhandler` artifact since CMP 1.7
- `FocusRequester` in LazyColumn — needs delay/awaitFrame after scroll
- ModalBottomSheet + Switch — sheet stays open on toggle, only dismisses on swipe/scrim
