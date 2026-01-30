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

A Kotlin data class pattern that **guarantees** automatic unique ID generation when creating objects. Used for stable LazyColumn keys.

## Problem

### Why index-based keys don't work

```kotlin
// ❌ BAD: Index-based keys break animations
LazyColumn {
    itemsIndexed(items, key = { index, _ -> "item_$index" }) { ... }
}
```

When an item is deleted:
- Item at index 3 becomes index 2
- Compose thinks it's the same item (key "item_2")
- Animations break, state gets confused

### Why regular default parameter is not enough

```kotlin
// ⚠️ RISKY: Custom id can be passed
data class ChecklistItem(
    val text: String,
    val id: String = generateId()
)

// Developer can break it:
ChecklistItem("Task", id = "duplicate-id")  // Collision!
item.copy(id = "hacked")                    // Bypass generation!
```

## Solution

### Key components

| Component | Purpose |
|-----------|---------|
| `private constructor` | Prevents creation with custom id |
| `@ConsistentCopyVisibility` | Makes `copy()` also private (Kotlin 1.9+) |
| Public secondary constructor | Only way to create object |

### Implementation

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

### Usage in LazyColumn

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
                modifier = Modifier.animateItem()  // Animations work!
            )
        }
    }
}
```

## Why It Works

### @ConsistentCopyVisibility

Without this annotation `copy()` is always public:

```kotlin
// Without @ConsistentCopyVisibility:
val item = ChecklistItem("Task")
val hacked = item.copy(id = "duplicate")  // ✅ Compiles - BAD!

// With @ConsistentCopyVisibility:
val hacked = item.copy(id = "duplicate")  // ❌ ERROR: copy is private
```

### Serialization works

`@Serializable` uses primary constructor directly via compiler plugin:

```kotlin
val json = """{"text":"Task","checked":false,"id":"123_456"}"""
val item = Json.decodeFromString<ChecklistItem>(json)
// item.id = "123_456" (preserved from JSON!)
```

Serialization bypasses Kotlin visibility — this is **expected behavior**.

### Room Database also works

Room uses reflection to access the primary constructor.

## ID Generation Strategy

```kotlin
id = "${currentTimeMillis()}_${Random.nextInt(0, 10000)}"
```

- **`currentTimeMillis()`** — millisecond precision
- **`Random.nextInt(0, 10000)`** — collision protection for rapid creation in loops

### KMP Compatibility

Use expect/actual for `currentTimeMillis()`:

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

| Aspect | Benefit | Cost |
|--------|---------|------|
| ID uniqueness | Guaranteed | Requires Kotlin 1.9+ |
| Immutability | Cannot accidentally modify id | Need helper methods instead of copy() |
| Serialization | Works transparently | — |
| Testing | Predictable behavior | Cannot create with specific id |

## When to Use

✅ **Use when:**
- Objects are displayed in LazyColumn/LazyRow
- Need stable keys for animations
- ID must be unique and immutable

❌ **Don't use when:**
- ID comes from server (need control over value)
- Simple value objects without collections
- Need ability to test with specific IDs

## Files

- `feature/checklist/src/commonMain/kotlin/.../domain/model/Checklist.kt` — ChecklistItem, ChecklistFillItem
- `core/common/api/src/commonMain/kotlin/.../Time.kt` — currentTimeMillis() expect/actual

## Related

- [MVI Pattern](../architecture/mvi-pattern.md) — State data classes
- [KMP Patterns](../architecture/kmp-patterns.md) — expect/actual
- [Room Cascade Delete](../database-issues/room-cascade-delete-flow-race-condition.md) — Entity relationships
