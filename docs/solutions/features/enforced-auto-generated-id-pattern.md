---
title: "Enforced Auto-Generated ID for LazyColumn Keys"
category: features
tags:
  - kotlin
  - data-class
  - compose
  - lazycolumn
  - auto-generated-id
  - defensive-design
  - kmp
module: feature/checklist
date: 2026-01-30
symptoms:
  - "LazyColumn animations break when items are added/removed"
  - "List items disappear or render incorrectly after recomposition"
  - "Duplicate keys IllegalArgumentException in LazyColumn"
  - "Need to guarantee unique stable keys for Compose list items"
  - "Accidental ID collision when creating multiple items rapidly"
---

# Enforced Auto-Generated ID Pattern

## Overview

Паттерн для Kotlin data class, который **гарантирует** автоматическую генерацию уникального ID при создании объекта. Используется для стабильных ключей в LazyColumn.

## Problem

### Почему index-based keys не работают

```kotlin
// ❌ BAD: Index-based keys break animations
LazyColumn {
    itemsIndexed(items, key = { index, _ -> "item_$index" }) { ... }
}
```

Когда элемент удаляется:
- Item с index 3 становится index 2
- Compose думает что это тот же элемент (ключ "item_2")
- Анимации ломаются, состояние путается

### Почему обычный default parameter не достаточен

```kotlin
// ⚠️ RISKY: Можно передать кастомный id
data class ChecklistItem(
    val text: String,
    val id: String = generateId()
)

// Разработчик может сломать:
ChecklistItem("Task", id = "duplicate-id")  // Коллизия!
item.copy(id = "hacked")                    // Обход генерации!
```

## Solution

### Ключевые компоненты

| Компонент | Назначение |
|-----------|------------|
| `private constructor` | Запрещает создание с кастомным id |
| `@ConsistentCopyVisibility` | Делает `copy()` тоже private (Kotlin 1.9+) |
| Public secondary constructor | Единственный способ создать объект |

### Реализация

```kotlin
import kotlinx.serialization.Serializable
import kotlin.random.Random

@ConsistentCopyVisibility
@Serializable
data class ChecklistItem private constructor(
    val text: String,
    val checked: Boolean = false,
    val id: String
) {
    /**
     * Creates a new ChecklistItem with auto-generated unique ID.
     * This is the only public way to create new items.
     */
    constructor(text: String, checked: Boolean = false) : this(
        text = text,
        checked = checked,
        id = "${currentTimeMillis()}_${Random.nextInt(0, 10000)}"
    )
}
```

### Использование в LazyColumn

```kotlin
@Composable
fun ChecklistScreen(items: List<ChecklistItem>) {
    LazyColumn {
        itemsIndexed(
            items = items,
            key = { _, item -> item.id }  // ✅ Stable, unique, auto-generated
        ) { index, item ->
            ChecklistItemRow(
                item = item,
                modifier = Modifier.animateItem()  // Анимации работают!
            )
        }
    }
}
```

## Why It Works

### @ConsistentCopyVisibility

Без этой аннотации `copy()` всегда public:

```kotlin
// Без @ConsistentCopyVisibility:
val item = ChecklistItem("Task")
val hacked = item.copy(id = "duplicate")  // ✅ Компилируется - ПЛОХО!

// С @ConsistentCopyVisibility:
val hacked = item.copy(id = "duplicate")  // ❌ ERROR: copy is private
```

### Serialization работает

`@Serializable` использует primary constructor напрямую через compiler plugin:

```kotlin
val json = """{"text":"Task","checked":false,"id":"123_456"}"""
val item = Json.decodeFromString<ChecklistItem>(json)
// item.id = "123_456" (сохранён из JSON!)
```

Сериализация обходит Kotlin visibility — это **ожидаемое поведение**.

### Room Database тоже работает

Room использует reflection для доступа к primary constructor.

## ID Generation Strategy

```kotlin
id = "${currentTimeMillis()}_${Random.nextInt(0, 10000)}"
```

- **`currentTimeMillis()`** — миллисекундная точность
- **`Random.nextInt(0, 10000)`** — защита от коллизий при быстром создании в цикле

### KMP Compatibility

Используем expect/actual для `currentTimeMillis()`:

```kotlin
// commonMain
expect fun currentTimeMillis(): Long

// androidMain
actual fun currentTimeMillis(): Long = System.currentTimeMillis()

// iosMain
actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()
```

## Trade-offs

| Аспект | Плюс | Минус |
|--------|------|-------|
| Уникальность ID | Гарантирована | Требует Kotlin 1.9+ |
| Иммутабельность | Нельзя случайно изменить id | Нужны helper методы вместо copy() |
| Сериализация | Работает прозрачно | — |
| Тестирование | Предсказуемое поведение | Нельзя создать с конкретным id |

## When to Use

✅ **Используйте когда:**
- Объекты отображаются в LazyColumn/LazyRow
- Нужны стабильные ключи для анимаций
- ID должен быть уникальным и неизменяемым

❌ **Не используйте когда:**
- ID приходит с сервера (нужен контроль над значением)
- Простые value objects без коллекций
- Нужна возможность тестирования с конкретными ID

## Files

- `feature/checklist/src/commonMain/kotlin/.../domain/model/Checklist.kt` — ChecklistItem, ChecklistFillItem
- `core/common/api/src/commonMain/kotlin/.../Time.kt` — currentTimeMillis() expect/actual

## Related

- [MVI Pattern](../architecture/mvi-pattern.md) — State data classes
- [KMP Patterns](../architecture/kmp-patterns.md) — expect/actual
- [Room Cascade Delete](../database-issues/room-cascade-delete-flow-race-condition.md) — Entity relationships
