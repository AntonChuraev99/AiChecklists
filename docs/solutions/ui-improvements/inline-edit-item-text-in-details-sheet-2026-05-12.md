---
title: "Inline Item Text Edit in ModalBottomSheet"
date: 2026-05-12
type: feature
modules: [feature/home, feature/checklist, core/designsystem]
keywords: [inline-edit, item-details-sheet, basicTextfield, focusRequester, dual-update, @consistentCopyVisibility, modalbottomsheet, edit-item-text]
project: Checklists
---

# Inline Item Text Edit in ModalBottomSheet

## Проблема / Контекст

Пользователь хочет быстро отредактировать текст элемента чеклиста без открытия отдельного Edit-экрана. Текущий UX требует: Detail → тап на элемент → open ItemDetailsSheet → close → open Edit Screen → найти элемент → change → save → return to Detail. Это 6 шагов вместо 2.

**Решение:** Двухрежимный заголовок в `ItemDetailsSheet`:
- **View-mode:** Clickable Text
- **Edit-mode:** BasicTextField с прозрачным стилем

## Решение

### Архитектура

```
ItemDetailsSheet (ModalBottomSheet)
  ├── Header (двухрежимный)
  │   ├── View-mode: Text (clickable) → OnStartItemTextEdit
  │   └── Edit-mode: BasicTextField → IME Done / blur → OnConfirmItemTextEdit
  ├── Note row
  ├── Reminder row
  └── Delete row
```

### 1. Domain Layer — Helper метод

**Файл:** `feature/checklist/src/commonMain/kotlin/.../domain/model/Checklist.kt`

```kotlin
@Immutable
data class ChecklistFillItem(
    val id: String,
    val text: String,
    val checked: Boolean = false,
    val note: String? = null,
    // ... другие поля
) {
    fun withText(newText: String): ChecklistFillItem {
        if (newText.isBlank()) return this
        return copy(text = newText.trim())
    }

    fun withChecked(checked: Boolean) = copy(checked = checked)
    fun withNote(note: String?) = copy(note = note)
    // ... остальные helpers
}
```

**Почему так:** `@ConsistentCopyVisibility` guard обеспечивает безопасное копирование immutable data class. Паттерн идентичен существующим `withChecked()/withNote()/withWeekday()/withPriority()` — не новое изобретение. Blank-валидация (`newText.isBlank()`) делает отмену неявной: попытка оставить элемент без текста просто игнорируется.

### 2. State & Intent

**Файл:** `feature/home/src/commonMain/.../detail/ChecklistDetailScreenContract.kt`

```kotlin
sealed interface ChecklistDetailScreenContract {
    sealed interface State {
        data class Content(
            val checklist: Checklist,
            val checklistFill: ChecklistFill,
            val fills: List<ChecklistFill>,
            // ...
            val editingItemTextFor: String? = null,     // fillItemId или null
            val editingItemTextDraft: String = "",
        ) : State
        // Loading, Error, ...
    }

    sealed interface Intent {
        data class OnStartItemTextEdit(val itemId: String) : Intent
        data class OnItemTextDraftChange(val text: String) : Intent
        data object OnConfirmItemTextEdit : Intent
        data object OnCancelItemTextEdit : Intent
        // ... остальные intents
    }
}
```

### 3. ViewModel Handlers

**Файл:** `feature/home/src/commonMain/.../detail/ChecklistDetailViewModel.kt`

```kotlin
private fun startItemTextEdit(state: Content, intent: OnStartItemTextEdit) {
    val item = state.checklistFill.items.find { it.id == intent.itemId } ?: return
    updateState {
        it.copy(
            editingItemTextFor = intent.itemId,
            editingItemTextDraft = item.text
        )
    }
}

private fun updateItemTextDraft(state: Content, intent: OnItemTextDraftChange) {
    updateState { it.copy(editingItemTextDraft = intent.text) }
}

private fun confirmItemTextEdit(state: Content) {
    val currentState = state as? Content ?: return
    val itemId = currentState.editingItemTextFor ?: return  // guard: double-fire
    val newText = currentState.editingItemTextDraft.trim()

    // Blank или same-text → cancel
    val oldItem = currentState.checklistFill.items.find { it.id == itemId } ?: return
    if (newText.isBlank() || newText == oldItem.text) {
        cancelItemTextEdit()
        return
    }

    // Dual-update: fill + template
    val updatedFill = currentState.checklistFill.copy(
        items = currentState.checklistFill.items.map { item ->
            if (item.id == itemId) item.withText(newText) else item
        }
    )
    val updatedTemplate = currentState.checklist.copy(
        items = currentState.checklist.items.map { templateItem ->
            // Match по text (item IDs между fill и template не совпадают)
            if (templateItem.text == oldItem.text) {
                templateItem.copy(text = newText)
            } else {
                templateItem
            }
        }
    )

    launchIO {
        checklistRepository.updateFill(updatedFill)
        checklistRepository.updateChecklistTemplate(updatedTemplate)
        analyticsHelper.logEvent("item_text_edited")
    }

    cancelItemTextEdit()
}

private fun cancelItemTextEdit() {
    val currentState = state.value as? Content ?: return
    updateState { it.copy(editingItemTextFor = null, editingItemTextDraft = "") }
}

// В main onIntent():
Intent.OnStartItemTextEdit -> startItemTextEdit(state, intent)
Intent.OnItemTextDraftChange -> updateItemTextDraft(state, intent)
Intent.OnConfirmItemTextEdit -> confirmItemTextEdit(state)
Intent.OnCancelItemTextEdit -> cancelItemTextEdit()

// Также обработать закрытие sheet:
Intent.OnDismissItemDetailsSheet -> cancelItemTextEdit()
```

### 4. UI Layer — ItemDetailsSheet

**Файл:** `feature/home/src/commonMain/.../detail/ChecklistDetailScreen.kt`

```kotlin
@Composable
fun ItemDetailsSheet(
    item: ChecklistFillItem,
    editingItemTextFor: String?,
    editingItemTextDraft: String,
    onStartItemTextEdit: (String) -> Unit,
    onItemTextDraftChange: (String) -> Unit,
    onConfirmItemTextEdit: () -> Unit,
    onCancelItemTextEdit: () -> Unit,
    // ... остальные callbacks
) {
    val focusRequester = remember { FocusRequester() }
    val isEditing = editingItemTextFor == item.id

    ModalBottomSheet(
        onDismissRequest = {
            if (isEditing) onCancelItemTextEdit()
            // ... close sheet
        }
    ) {
        Column(modifier = Modifier.padding(AppDimens.SpacingLg)) {
            // Двухрежимный заголовок
            if (isEditing) {
                BasicTextField(
                    value = editingItemTextDraft,
                    onValueChange = onItemTextDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onConfirmItemTextEdit()
                            focusRequester.freeFocus()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        innerTextField()
                    }
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStartItemTextEdit(item.id) }
                        .padding(vertical = AppDimens.SpacingMd)
                )
            }

            Divider(modifier = Modifier.padding(vertical = AppDimens.SpacingMd))

            // Note row
            ItemDetailsSheetRow(
                icon = Icons.Filled.Note,
                title = "Note",
                value = item.note ?: "Add a note",
                onClick = { /* open note edit */ }
            )

            // Reminder row
            ItemDetailsSheetRow(
                icon = Icons.Filled.Alarm,
                title = "Reminder",
                value = "Set reminder",
                onClick = { /* open reminder */ }
            )

            // Delete row
            ItemDetailsSheetRow(
                icon = Icons.Filled.Delete,
                title = "Delete",
                onClick = { /* delete item */ }
            )
        }
    }
}
```

**Focus и IME:**
- `FocusRequester` + `LaunchedEffect(Unit)` обеспечивает автофокус при переходе в edit-mode
- `KeyboardActions(onDone = { onConfirmItemTextEdit() })` сохраняет по IME Done
- `BasicTextField` используется вместо `AppTextField` потому что:
  - Нет рамки (transparent border)
  - Прямой копирование стиля из `headlineSmall` (не AppTextField wrapper)
  - Inline-контекст требует минимума UI-overhead

**onFocusChanged guard:**
Дополнительная защита от двойного commit'а — обёрнут в `onValueChange`/`onDone`:

```kotlin
keyboardActions = KeyboardActions(
    onDone = {
        // Сначала commit
        onConfirmItemTextEdit()
        // Потом освобождаем фокус — это может вызвать onFocusChanged
        // но confirmItemTextEdit() уже сбросил editingItemTextFor → будет no-op
        focusRequester.freeFocus()
    }
)
```

### 5. Localization

**Файл:** `core/designsystem/src/commonMain/composeResources/values/strings.xml`

```xml
<string name="detail_item_sheet_edit_text_hint">Item name</string>
```

### 6. Unit Tests

**Файл:** `feature/home/src/commonTest/.../ChecklistDetailItemDetailsSheetTest.kt`

```kotlin
class ChecklistDetailItemDetailsSheetTest {
    @Test
    fun startItemTextEdit_setsEditingItemId() {
        val viewModel = ChecklistDetailViewModel(...)
        val initialState = createContentState()
        viewModel.sendIntent(OnStartItemTextEdit("item-1"))
        
        val state = viewModel.screenState.filterIsInstance<Content>().first()
        assertEquals("item-1", state.editingItemTextFor)
    }

    @Test
    fun updateItemTextDraft_changesText() {
        val viewModel = ChecklistDetailViewModel(...)
        viewModel.sendIntent(OnStartItemTextEdit("item-1"))
        viewModel.sendIntent(OnItemTextDraftChange("New text"))
        
        val state = viewModel.screenState.filterIsInstance<Content>().first()
        assertEquals("New text", state.editingItemTextDraft)
    }

    @Test
    fun confirmItemTextEdit_callsDualUpdate() {
        val mockRepository = mockk<ChecklistRepository>(relaxed = true)
        val viewModel = ChecklistDetailViewModel(..., repository = mockRepository)
        
        viewModel.sendIntent(OnStartItemTextEdit("item-1"))
        viewModel.sendIntent(OnItemTextDraftChange("Updated text"))
        viewModel.sendIntent(OnConfirmItemTextEdit)
        
        verify(exactly = 1) { mockRepository.updateFill(any()) }
        verify(exactly = 1) { mockRepository.updateChecklistTemplate(any()) }
    }

    @Test
    fun confirmItemTextEdit_blankText_cancels() {
        val viewModel = ChecklistDetailViewModel(...)
        viewModel.sendIntent(OnStartItemTextEdit("item-1"))
        viewModel.sendIntent(OnItemTextDraftChange("   "))  // blank
        viewModel.sendIntent(OnConfirmItemTextEdit)
        
        val state = viewModel.screenState.filterIsInstance<Content>().first()
        assertNull(state.editingItemTextFor)
    }

    @Test
    fun confirmItemTextEdit_sameText_cancels() {
        val viewModel = ChecklistDetailViewModel(...)
        viewModel.sendIntent(OnStartItemTextEdit("item-1"))
        viewModel.sendIntent(OnItemTextDraftChange("Original text"))  // no change
        viewModel.sendIntent(OnConfirmItemTextEdit)
        
        val state = viewModel.screenState.filterIsInstance<Content>().first()
        assertNull(state.editingItemTextFor)
    }

    @Test
    fun dismissSheet_whileEditing_cancels() {
        val viewModel = ChecklistDetailViewModel(...)
        viewModel.sendIntent(OnStartItemTextEdit("item-1"))
        viewModel.sendIntent(OnDismissItemDetailsSheet)
        
        val state = viewModel.screenState.filterIsInstance<Content>().first()
        assertNull(state.editingItemTextFor)
    }
}
```

## Почему именно так

### Dual-update паттерн

`ChecklistFill` и `Checklist` (template) — это разные сущности:
- `Checklist` — template (без состояния)
- `ChecklistFill` — экземпляр с состоянием (checked, notes, fill-specific data)

При редактировании текста обновлять нужно оба:
1. `fill.items[id].text` → Detail Screen обновится через reactive Flow `observeChecklistById()`
2. `template.items[text-match].text` → Edit Screen покажет изменение

Без dual-update: Edit Screen откроется со старым текстом → UX confusion.

### BasicTextField вместо AppTextField

`ItemDetailsSheet` — это lightweight context (one-field edit). `AppTextField` оборачивает Material3 OutlinedTextField — слишком heavy. `BasicTextField`:
- Прозрачный (нет border)
- Прямой копирование стиля (headlineSmall)
- Минимум XML/DSL overhead

Когда inline-edit появится на других sheets, можно будет экстрактить в `AppInlineTextField` компонент. На данный момент это первый use-case — не обобщаем.

### FocusRequester + LaunchedEffect

`ItemDetailsSheet` использует `Column`, не `LazyColumn` → `FocusRequester` работает надёжно. Критичные условия:
- LaunchedEffect **на уровне ItemDetailsSheet** (не nested LaunchedEffect)
- `focusRequester.freeFocus()` только после `onConfirmItemTextEdit()` (иначе trigger фокус-изменение до commit'а)

### Double-fire guard

IME Done и onFocusChanged(false) могут оба сработать в близкой последовательности:
1. User press Done
2. `onDone { onConfirmItemTextEdit(); focusRequester.freeFocus() }`
3. freeFocus() trigger onFocusChanged(false)
4. onFocusChanged handler может вызвать commit ещё раз

Guard: `val itemId = state.editingItemTextFor ?: return` в начале `confirmItemTextEdit()` → второй вызов будет no-op (editingItemTextFor уже сброшен).

### ID matching по text (не по ID)

Template items и Fill items имеют разные ID-наборы:
- Template: автогенерируемые UUID при создании template
- Fill: те же UUID копируются при создании fill

При обновлении item в template, ID may drift (миграции, импорты). Безопаснее matcher по `text`:
```kotlin
// ❌ Неправильно: может не найти item
template.items.find { it.id == fillItem.id }

// ✅ Правильно: text это source of truth
template.items.find { it.text == fillItem.text }
```

Паттерн уже используется в `deleteItemFromSheet()` → не новое изобретение.

## Примеры

### Happy path

```
Detail screen
  └─ Тап на элемент → ItemDetailsSheet opens
       ├─ View-mode: "Buy milk"
       └─ User тапит на текст "Buy milk"
           ├─ Edit-mode: BasicTextField с "Buy milk" selected + focused
           ├─ User изменяет: "Buy 2L milk"
           ├─ Press Done
           ├─ confirmItemTextEdit() вызовется
           ├─ updateFill({ items: [{ text: "Buy 2L milk" }] })
           ├─ updateChecklistTemplate({ items: [{ text: "Buy 2L milk" }] })
           └─ Detail screen обновляется через reactive Flow → sees "Buy 2L milk"
```

### Cancel scenarios

```
A. Blank text → cancel
   User: "Buy milk" → "" → Press Done
   Result: text остаётся "Buy milk", edit-mode закрывается

B. Same text → cancel
   User: "Buy milk" → "Buy milk" → Press Done
   Result: no-op, edit-mode закрывается

C. Swipe down → cancel
   User: "Buy milk" → "Buy 2L milk" (в процессе)
   Sheet закрывается через swipe
   Result: editingItemTextFor сбрасывается, изменения теряются
```

## Связанные файлы

- `feature/checklist/src/commonMain/kotlin/.../domain/model/Checklist.kt` — `ChecklistFillItem.withText()`
- `feature/home/src/commonMain/kotlin/.../detail/ChecklistDetailScreen{,Contract,ViewModel}.kt`
- `feature/home/src/commonTest/kotlin/.../ChecklistDetailItemDetailsSheetTest.kt`
- `core/designsystem/src/commonMain/composeResources/values/strings.xml`

## Reusable pattern

Этот паттерн применим к любому ModalBottomSheet с inline-edit требованием:

1. Добавить `editingFieldFor: String?` + `editingFieldDraft: String` в State
2. Двухрежимный заголовок/content (view vs edit)
3. FocusRequester + LaunchedEffect(Unit) для autofocus
4. Guard в confirmHandler: `?: return` для double-fire
5. Blank/same-text validation → cancel
6. Dismiss sheet → cancel (abandon edits)
7. IME Done → confirm

Скопируй pattern для: Note edit, Reminder edit, Tag edit, Category edit — структура идентична.
