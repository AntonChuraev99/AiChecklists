---
date: 2026-01-29
topic: widget-refresh-fix
---

# Widget Refresh Fix

## Problem
Glance widget не обновляется после toggle элемента, хотя данные в Room сохраняются корректно.

## Root Cause Analysis

1. **Данные сохраняются правильно** - логи подтверждают что `toggleItem` работает
2. **`provideGlance()` вызывается** - но Glance кэширует композицию
3. **Glance не видит изменений** - потому что мы читаем из Room напрямую, без `GlanceStateDefinition`

Glance использует state-based обновление. Когда мы вызываем `update()`, Glance проверяет изменился ли state. Если state тот же, перерисовка не происходит.

## Solution: PreferencesGlanceStateDefinition + Refresh Trigger

Добавить `GlanceStateDefinition` с `lastUpdated` timestamp:

```kotlin
// В ChecklistWidget.kt
override val stateDefinition = PreferencesGlanceStateDefinition

// В ToggleItemAction.kt
updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
    prefs.toMutablePreferences().apply {
        this[REFRESH_KEY] = System.currentTimeMillis()
    }
}
ChecklistWidget().update(context, glanceId)
```

Когда `REFRESH_KEY` меняется, Glance видит изменение state и перерисовывает виджет.

## Key Decisions

- **PreferencesGlanceStateDefinition**: Используем встроенный механизм, не создаём кастомный
- **Timestamp как триггер**: Простой способ гарантировать уникальное значение
- **Данные из Room**: Продолжаем читать реальные данные из Room, preferences только для триггера

## Next Steps

1. Добавить `stateDefinition` в `ChecklistWidget`
2. Добавить `updateAppWidgetState` в `ToggleItemAction`
3. Протестировать на эмуляторе
