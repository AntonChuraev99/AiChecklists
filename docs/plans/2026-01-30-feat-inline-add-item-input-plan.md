---
title: feat: Replace item dialogs with inline input
type: feat
date: 2026-01-30
---

# feat: Replace Item Dialogs with Inline Input

## Overview

Переработать UX добавления элементов в чеклист: заменить модальные диалоги на inline-поле ввода сверху списка. Это улучшит пользовательский опыт, сделав добавление элементов более быстрым и естественным (как в iOS Reminders).

## Problem Statement / Motivation

**Текущее поведение:**
- При добавлении элемента в чеклист открывается `AlertDialog`
- Пользователь вводит текст, нажимает "Save"
- Диалог закрывается, элемент появляется в списке

**Проблемы:**
1. **Прерывание потока** — модальный диалог отвлекает от контекста списка
2. **Лишние клики** — нужно нажать кнопку "Add", затем "Save" в диалоге
3. **Нет instant feedback** — пользователь не видит, куда добавится элемент
4. **Inconsistency** — в `AnalyzeResultPreviewScreen` уже используется inline-ввод

**Целевое поведение:**
- Поле ввода всегда видно сверху списка элементов
- Пользователь печатает текст и нажимает Enter или иконку "+"
- Элемент мгновенно появляется **сверху списка** (сразу под полем ввода)
- Поле очищается для следующего ввода
- При нажатии "Сохранить" несохранённый текст из поля **авто-добавляется** как элемент

## Design Decisions (User Confirmed)

| Решение | Выбор |
|---------|-------|
| Позиция поля ввода | Сверху списка элементов |
| Где появляется новый элемент | Сверху списка (под полем ввода) |
| Unsaved text при Save | Авто-добавляется |
| Консистентность | Унифицировать на всех экранах (везде сверху)

## Scope Analysis

### Экраны для изменения:

| Экран | Файл | Текущий диалог | Изменение |
|-------|------|----------------|-----------|
| CreateChecklistScreen | `feature/create/.../CreateChecklistScreen.kt` | AlertDialog (строки 143-186) | → Inline input сверху списка |

### Диалоги, которые остаются:

| Диалог | Экран | Причина |
|--------|-------|---------|
| DeleteConfirmationDialog | ChecklistDetailScreen | Защита от случайного удаления |
| AddFillDialog | ChecklistDetailScreen | Создание Fill — важное действие с именем |
| FillLimitDialog | ChecklistDetailScreen | Информационный диалог с upsell |
| NoteDialog | ChecklistDetailScreen, FillDetailScreen | Редактирование заметок (можно рассмотреть inline позже) |

### Существующий паттерн (референс):

**AnalyzeResultPreviewScreen.kt** (строки 286-327) — `AddItemField`:
```kotlin
@Composable
private fun AddItemField(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(Res.string.analyze_preview_add_item_hint)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onAdd() }),
            shape = RoundedCornerShape(12.dp)
        )

        IconButton(
            onClick = onAdd,
            enabled = text.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(Res.string.analyze_preview_add_item),
                tint = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

## Proposed Solution

### Подход 1: Переиспользуемый компонент

Создать общий компонент `AddItemInputField` в design system и использовать его на всех экранах.

**Преимущества:**
- Консистентный UX
- Единая точка изменения
- Соответствует архитектуре проекта

**Расположение:** `core/designsystem/src/commonMain/.../components/AddItemInputField.kt`

### Архитектура изменений:

```
core/designsystem/
  components/
    AddItemInputField.kt       # NEW - переиспользуемый компонент

feature/create/
  presentation/create/
    CreateChecklistScreen.kt   # MODIFY - заменить AlertDialog на AddItemInputField
    CreateChecklistScreenContract.kt  # MODIFY - добавить newItemText в State
    CreateChecklistViewModel.kt       # MODIFY - добавить Intent для ввода
```

## Technical Approach

### Phase 1: Создание общего компонента

**Файл:** `core/designsystem/src/commonMain/kotlin/.../components/AddItemInputField.kt`

```kotlin
@Composable
fun AddItemInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    placeholder: String = stringResource(Res.string.add_item_placeholder),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(placeholder) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onAdd() }),
            shape = RoundedCornerShape(12.dp)
        )

        IconButton(
            onClick = onAdd,
            enabled = text.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(Res.string.add_item),
                tint = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

### Phase 2: Модификация CreateChecklistScreen

**Изменения в State (CreateChecklistScreenContract.kt):**
```kotlin
data class CreateChecklistState(
    val name: String = "",
    val nameError: String? = null,
    val items: List<ChecklistItem> = emptyList(),
    val isEditMode: Boolean = false,
    val newItemText: String = ""  // NEW
)
```

**Новые Intent:**
```kotlin
sealed interface CreateChecklistIntent {
    // ... existing
    data class OnNewItemTextChange(val text: String) : CreateChecklistIntent  // NEW
    data object OnAddItemFromInput : CreateChecklistIntent  // NEW
}
```

**Изменения в ViewModel:**
```kotlin
override fun onIntent(intent: CreateChecklistIntent) {
    when (intent) {
        // ... existing
        is CreateChecklistIntent.OnNewItemTextChange -> {
            updateState { copy(newItemText = intent.text) }
        }
        is CreateChecklistIntent.OnAddItemFromInput -> {
            val text = screenState.value.newItemText.trim()
            if (text.isNotBlank()) {
                val newItem = ChecklistItem(text = text, checked = false)
                updateState {
                    copy(
                        items = items + newItem,
                        newItemText = ""
                    )
                }
            }
        }
    }
}
```

**Изменения в Screen:**
```kotlin
// REMOVE: showDialog, dialogText state
// REMOVE: AlertDialog composable

// ADD: Inline input at top of items section
Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
    Text(
        text = stringResource(Res.string.create_items_section),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
    )

    // NEW: Inline input field (replaces AppButtonSecondary)
    AddItemInputField(
        text = screenState.newItemText,
        onTextChange = { viewModel.sendIntent(CreateChecklistIntent.OnNewItemTextChange(it)) },
        onAdd = { viewModel.sendIntent(CreateChecklistIntent.OnAddItemFromInput) },
        placeholder = stringResource(Res.string.create_add_item_placeholder)
    )

    // Existing items list
    screenState.items.forEach { item ->
        AppCard { ... }
    }
}
```

### Phase 3: Рефакторинг AnalyzeResultPreviewScreen

Заменить локальный `AddItemField` на общий `AddItemInputField` из design system.

## Acceptance Criteria

### Functional Requirements

- [ ] Поле ввода отображается сверху списка элементов на CreateChecklistScreen
- [ ] При вводе текста и нажатии Enter/Done элемент добавляется в список
- [ ] При нажатии иконки "+" элемент добавляется в список
- [ ] После добавления поле очищается
- [ ] Кнопка "+" активна только когда текст не пустой
- [ ] Диалоги DeleteConfirmation, AddFillDialog, FillLimitDialog работают как прежде

### Non-Functional Requirements

- [ ] Компонент AddItemInputField переиспользуется на всех экранах
- [ ] Соответствует design system (цвета, отступы, скругления)
- [ ] Клавиатура корректно работает с ImeAction.Done

### Quality Gates

- [ ] Код следует MVI паттерну проекта
- [ ] Нет регрессий в существующей функциональности
- [ ] Локализация placeholder текста добавлена в strings.xml

## Implementation Checklist

### Task 1: Создать AddItemInputField компонент
- [ ] Создать файл `AddItemInputField.kt` в design system
- [ ] Добавить строку `add_item_placeholder` в strings.xml
- [ ] Экспортировать компонент

### Task 2: Модифицировать CreateChecklistScreen
- [ ] Добавить `newItemText` в State
- [ ] Добавить Intent'ы для ввода
- [ ] Обновить ViewModel
- [ ] Заменить AlertDialog на AddItemInputField в Screen
- [ ] Удалить локальный state для диалога

### Task 3: Рефакторинг AnalyzeResultPreviewScreen
- [ ] Заменить локальный AddItemField на общий компонент
- [ ] Удалить локальную реализацию AddItemField

### Task 4: Тестирование
- [ ] Проверить добавление элемента через Enter
- [ ] Проверить добавление через кнопку
- [ ] Проверить очистку поля
- [ ] Проверить состояние кнопки при пустом вводе

## Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `core/designsystem/.../components/AddItemInputField.kt` | CREATE | Новый компонент |
| `core/designsystem/.../values/strings.xml` | MODIFY | Добавить placeholder строку |
| `feature/create/.../CreateChecklistScreenContract.kt` | MODIFY | Добавить newItemText в State |
| `feature/create/.../CreateChecklistViewModel.kt` | MODIFY | Обработка новых Intent'ов |
| `feature/create/.../CreateChecklistScreen.kt` | MODIFY | Заменить диалог на inline |
| `feature/analyze/.../AnalyzeResultPreviewScreen.kt` | MODIFY | Использовать общий компонент |

## ERD Diagram

N/A — изменения не затрагивают модели данных

## References

### Internal References
- Существующий inline паттерн: `feature/analyze/.../AnalyzeResultPreviewScreen.kt:286-327`
- Design system компоненты: `core/designsystem/.../components/`
- MVI паттерн: `docs/solutions/architecture/mvi-pattern.md`

### External References
- Material3 OutlinedTextField: https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#OutlinedTextField
- iOS Reminders inline input reference
