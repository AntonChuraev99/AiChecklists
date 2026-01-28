---
date: 2026-01-29
category: ui-bugs
module: widget
tags: [glance, widget, refresh, android, state-management, collectAsState, flow, room]
symptoms:
  - Widget UI not updating after toggle
  - Data saves to Room but widget shows stale state
  - Updates with 10-30 second delays
  - provideGlance() not called after update()
status: solved
solution: collectAsState() pattern from official Glance documentation
---

# Glance Widget Not Refreshing After Toggle

## Problem

Android Glance widget для чек-листа не обновляет UI после toggle элемента. Данные корректно сохраняются в Room базу данных, но визуальное состояние виджета остаётся неизменным или обновляется с большой задержкой.

## Symptoms

1. Тап на элемент виджета → данные сохраняются в Room
2. `provideGlance()` вызывается, но данные устаревшие
3. Виджет показывает старое состояние
4. Иногда обновление происходит с задержкой 10-30 секунд

## Root Cause

**Основная проблема:** Данные загружались ДО `provideContent`, а не внутри него.

```kotlin
// ❌ НЕПРАВИЛЬНО - данные кэшируются до provideContent
override suspend fun provideGlance(context: Context, id: GlanceId) {
    val widgetData = repository.getChecklistWithDefaultFill(checklistId)  // Загрузка здесь!

    provideContent {
        // widgetData уже загружен и закэширован
        // При recomposition используются старые данные
        ChecklistWidgetContent(data = widgetData)
    }
}
```

Когда `update()` вызывается, Glance может перерисовать composable, но `widgetData` уже содержит старые данные, потому что загрузка произошла за пределами реактивной области.

## Working Solution

**Использовать `collectAsState()` внутри `provideContent`** — официальный паттерн из [документации Android](https://developer.android.com/develop/ui/compose/glance/glance-app-widget).

### 1. Добавить Flow метод в Repository

```kotlin
// WidgetRepository.kt
fun observeChecklistWithDefaultFill(checklistId: Long): Flow<ChecklistWidgetData> {
    return combine(
        checklistDao.observeChecklistById(checklistId),
        fillDao.observeDefaultFillByChecklistId(checklistId)
    ) { checklist, defaultFill ->
        if (checklist == null) {
            return@combine ChecklistWidgetData.notFound(checklistId)
        }

        val items = defaultFill?.items ?: checklist.items.map { templateItem ->
            ChecklistFillItem(text = templateItem.text, checked = false, note = null)
        }

        ChecklistWidgetData(
            checklistId = checklist.id,
            name = checklist.name,
            items = items,
            fillId = defaultFill?.id
        )
    }
}
```

### 2. Добавить observe метод в DAO

```kotlin
// ChecklistDao.kt
@Query("SELECT * FROM checklists WHERE id = :id")
fun observeChecklistById(id: Long): Flow<ChecklistEntity?>
```

### 3. Использовать collectAsState() в виджете

```kotlin
// ChecklistWidget.kt
override suspend fun provideGlance(context: Context, id: GlanceId) {
    val repository: WidgetRepository = koin.get()
    val stateManager: WidgetStateManager = koin.get()
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

    provideContent {
        // ✅ ПРАВИЛЬНО - наблюдаем checklistId
        val checklistIdFlow = remember { stateManager.observeSelectedChecklistId(appWidgetId) }
        val checklistId by checklistIdFlow.collectAsState(initial = null)

        // ✅ При смене checklistId автоматически переключаемся на новый Flow
        val widgetDataFlow = remember(checklistId) {
            if (checklistId != null) {
                repository.observeChecklistWithDefaultFill(checklistId!!)
            } else {
                flowOf(null)
            }
        }
        val widgetData by widgetDataFlow.collectAsState(initial = null)

        GlanceTheme {
            when {
                checklistId == null -> NotConfiguredContent()
                widgetData == null -> LoadingContent()
                widgetData!!.notFound -> NotFoundContent(appWidgetId)
                else -> ChecklistWidgetContent(data = widgetData!!, appWidgetId = appWidgetId)
            }
        }
    }
}
```

### 4. Упростить ToggleItemAction

```kotlin
// ToggleItemAction.kt
class ToggleItemAction : ActionCallback {

    companion object {
        val CHECKLIST_ID_KEY = ActionParameters.Key<Long>("checklist_id")
        val FILL_ID_KEY = ActionParameters.Key<Long>("fill_id")
        val ITEM_INDEX_KEY = ActionParameters.Key<Int>("item_index")
        private val toggleMutex = Mutex()
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val checklistId = parameters[CHECKLIST_ID_KEY] ?: return
        val fillId = parameters[FILL_ID_KEY]?.takeIf { it != -1L }
        val itemIndex = parameters[ITEM_INDEX_KEY] ?: return

        val repository: WidgetRepository = GlobalContext.getOrNull()?.get() ?: return

        toggleMutex.withLock {
            // 1. Обновить данные в Room
            repository.toggleItem(checklistId, fillId, itemIndex)

            // 2. Триггернуть обновление виджета
            // collectAsState() автоматически получит новые данные из Flow
            ChecklistWidget().update(context, glanceId)
        }
    }
}
```

### 5. Добавить observe метод в WidgetStateManager (для смены чек-листа)

```kotlin
// WidgetStateManager.kt
fun observeSelectedChecklistId(appWidgetId: Int): Flow<Long?> {
    val key = longPreferencesKey(keyForWidget(appWidgetId))
    return context.widgetDataStore.data.map { prefs -> prefs[key] }
}
```

## Why This Works

1. **Room Flow** — Room автоматически эмитит новые данные при изменении таблицы
2. **collectAsState()** — подписывается на Flow и триггерит recomposition при новых значениях
3. **remember(key)** — пересоздаёт Flow когда ключ меняется (при смене чек-листа)
4. **update()** — вызывает `provideGlance()`, который запускает composable с `collectAsState()`

## Approaches That Did NOT Work

### 1. Direct update() without Flow
```kotlin
repository.toggleItem(...)
ChecklistWidget().update(context, glanceId)  // ❌ Данные уже загружены, update бесполезен
```

### 2. updateAppWidgetState + REFRESH_TRIGGER_KEY
```kotlin
updateAppWidgetState(context, glanceId) { prefs ->
    prefs[REFRESH_TRIGGER_KEY] = System.currentTimeMillis()
}
ChecklistWidget().update(context, glanceId)  // ❌ State меняется, но данные всё ещё загружаются до provideContent
```

### 3. updateAll()
```kotlin
ChecklistWidget().updateAll(context)  // ❌ Та же проблема - данные кэшируются
```

### 4. WorkManager
```kotlin
WorkManager.enqueueUniqueWork(...)  // ⚠️ Работает но с задержками
```

## Prevention

1. **Всегда использовать `collectAsState()` в Glance виджетах** для данных которые могут меняться
2. **Загружать данные внутри `provideContent`**, не снаружи
3. **Использовать Flow из Room** вместо suspend функций для реактивных данных
4. **Читать официальную документацию** — паттерн описан в [Glance State Management](https://developer.android.com/develop/ui/compose/glance/glance-app-widget)

## Related Files

- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/widget/ChecklistWidget.kt`
- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/widget/actions/ToggleItemAction.kt`
- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/widget/data/WidgetRepository.kt`
- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/widget/data/WidgetStateManager.kt`
- `feature/checklist/src/commonMain/kotlin/.../data/db/ChecklistDao.kt`

## References

- [Android Glance Documentation](https://developer.android.com/develop/ui/compose/glance/glance-app-widget)
- [Glance State Management](https://developer.android.com/develop/ui/compose/glance/glance-app-widget#state)
- [Room Flow Documentation](https://developer.android.com/training/data-storage/room/async-queries#flow)
- Git commits: `ce48de7`, `add5cff`
