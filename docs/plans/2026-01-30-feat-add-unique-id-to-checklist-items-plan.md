---
title: Add Unique ID to ChecklistItem for Stable LazyColumn Keys
type: feat
date: 2026-01-30
---

# Add Unique ID to ChecklistItem for Stable LazyColumn Keys

## Overview

Добавить автогенерируемый уникальный `id` в `ChecklistItem` и `ChecklistFillItem` для использования в качестве стабильных ключей в LazyColumn. Это обеспечит корректную работу анимаций при добавлении/удалении элементов.

## Problem Statement

**Текущая проблема:**
- LazyColumn использует index-based ключи (`key = { index, _ -> "item_$index" }`)
- При добавлении/удалении элементов индексы меняются
- Compose думает что все элементы изменились → анимации ломаются
- Стабильный ключ должен быть уникальным идентификатором элемента, не его позицией

**Почему `text` не подходит как ключ:**
- Пользователь может создать два элемента с одинаковым текстом
- `ChecklistFillItem.equals` сравнивает только text → коллизии

## Proposed Solution

Добавить `id: String` с автоматической генерацией UUID в оба класса:

```kotlin
@Serializable
data class ChecklistItem(
    val id: String = uuid4().toString(),
    val text: String,
    val checked: Boolean = false
)

@Serializable
data class ChecklistFillItem(
    val id: String = uuid4().toString(),
    val text: String,
    val checked: Boolean,
    val note: String? = null
)
```

## Technical Considerations

### UUID Generation
- Использовать `com.benasher44:uuid` библиотеку (уже стандарт для KMP)
- Генерация при создании объекта через default parameter
- Сохраняется в JSON через kotlinx.serialization

### Database Migration
- Items сериализуются в JSON внутри `ChecklistEntity.items` и `ChecklistFillEntity.items`
- TypeConverters используют `Json { ignoreUnknownKeys = true }` → старые записи без `id` будут работать
- При десериализации старых записей `id` получит default значение (новый UUID)
- **Миграция Room не требуется** - JSON хранится как String

### Equals/HashCode
- Оставить кастомный `equals` в `ChecklistItem` и `ChecklistFillItem` который сравнивает только `text` (для Set uniqueness)
- **ID НЕ участвует в equals** - это только для UI ключей

## Acceptance Criteria

- [ ] `ChecklistItem` имеет поле `id: String` с автогенерацией UUID
- [ ] `ChecklistFillItem` имеет поле `id: String` с автогенерацией UUID
- [ ] LazyColumn в `ChecklistDetailScreen` использует `key = { item.id }`
- [ ] LazyColumn в `FillDetailScreen` использует `key = { item.id }`
- [ ] LazyColumn в `CreateChecklistScreen` использует `key = { item.id }`
- [ ] Анимации `animateItem()` работают корректно при добавлении/удалении
- [ ] Старые данные в БД загружаются без ошибок (получают новые UUID)

## Implementation

### 1. Добавить UUID зависимость

```kotlin
// gradle/libs.versions.toml
[versions]
uuid = "0.8.2"

[libraries]
uuid = { module = "com.benasher44:uuid", version.ref = "uuid" }
```

```kotlin
// feature/checklist/build.gradle.kts
commonMain.dependencies {
    implementation(libs.uuid)
}
```

### 2. Обновить ChecklistItem

```kotlin
// Checklist.kt
import com.benasher44.uuid.uuid4

@Serializable
data class ChecklistItem(
    val id: String = uuid4().toString(),
    val text: String,
    val checked: Boolean = false
) {
    // equals/hashCode остаются - сравнивают только text
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChecklistItem) return false
        return text.equals(other.text, ignoreCase = true)
    }

    override fun hashCode(): Int = text.lowercase().hashCode()
}
```

### 3. Обновить ChecklistFillItem

```kotlin
@Serializable
data class ChecklistFillItem(
    val id: String = uuid4().toString(),
    val text: String,
    val checked: Boolean,
    val note: String? = null
) {
    // equals/hashCode остаются - сравнивают только text
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChecklistFillItem) return false
        return text.equals(other.text, ignoreCase = true)
    }

    override fun hashCode(): Int = text.lowercase().hashCode()
}
```

### 4. Обновить LazyColumn ключи

**ChecklistDetailScreen.kt:**
```kotlin
itemsIndexed(
    items = defaultFill.items.toList(),
    key = { _, item -> item.id }  // ← Стабильный ключ
) { index, item ->
    ChecklistItemCard(...)
}
```

**FillDetailScreen.kt:**
```kotlin
itemsIndexed(
    items = state.fill.items.toList(),
    key = { _, item -> item.id }  // ← Стабильный ключ
) { index, item ->
    FillItemCard(...)
}
```

**CreateChecklistScreen.kt:**
```kotlin
itemsIndexed(
    items = screenState.items.toList(),
    key = { _, item -> item.id }  // ← Стабильный ключ
) { index, item ->
    // ...
}
```

### 5. Обновить создание Fill из Checklist

При создании `ChecklistFill` из `Checklist` нужно генерировать новые ID для `ChecklistFillItem`:

```kotlin
// ChecklistRepositoryImpl.kt
val defaultFill = ChecklistFill(
    checklistId = checklistId,
    name = "",
    items = checklist.items.map { item ->
        ChecklistFillItem(
            id = uuid4().toString(),  // Новый уникальный ID
            text = item.text,
            checked = false,
            note = null
        )
    }.toSet(),
    createdAt = currentTimeMillis(),
    isDefault = true
)
```

## Files to Modify

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Добавить uuid библиотеку |
| `feature/checklist/build.gradle.kts` | Добавить зависимость |
| `Checklist.kt` | Добавить `id` в оба класса |
| `ChecklistDetailScreen.kt` | Использовать `key = { _, item -> item.id }` |
| `FillDetailScreen.kt` | Использовать `key = { _, item -> item.id }` |
| `CreateChecklistScreen.kt` | Использовать `key = { _, item -> item.id }` |
| `ChecklistRepositoryImpl.kt` | Генерировать id при создании Fill из Checklist |
| `WidgetRepository.kt` | Генерировать id при создании Fill items |
| `CreateChecklistViewModel.kt` | id автогенерируется через default parameter |

## References

- `Checklist.kt:19-31` - ChecklistItem definition
- `Checklist.kt:44-58` - ChecklistFillItem definition
- `ChecklistDetailScreen.kt:209` - itemsIndexed usage
- `FillDetailScreen.kt:177` - itemsIndexed usage
- `CreateChecklistScreen.kt:120` - itemsIndexed with index-based key
- KMP UUID library: https://github.com/benasher44/uuid
