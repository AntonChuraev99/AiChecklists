---
title: "feat: Add Android Home Screen Widget for Checklist"
type: feat
date: 2026-01-28
tags: [android, widget, glance, ui]
---

# feat: Add Android Home Screen Widget for Checklist

## Overview

Создание Android-виджета для домашнего экрана, который позволяет пользователю удобно просматривать и отмечать элементы чеклиста без открытия приложения. Виджет использует Jetpack Glance (Compose для виджетов) и интегрируется с существующей Room базой данных.

## Problem Statement / Motivation

**Текущая ситуация:**
- Чтобы отметить элемент чеклиста, пользователь должен открыть приложение → найти чеклист → открыть fill → отметить элемент
- Это 4+ действия для простой операции "поставить галочку"

**Решение:**
- Виджет на домашнем экране показывает чеклист с возможностью отмечать элементы одним tap'ом
- Пользователь видит прогресс прямо на домашнем экране
- Быстрый доступ к приложению через tap на заголовок

**Ценность для пользователя:**
- Экономия времени на рутинных операциях
- Визуальное напоминание о незавершённых задачах
- Повышение engagement с приложением

## Proposed Solution

### Функциональность виджета

| Функция | Описание |
|---------|----------|
| **Отображение чеклиста** | Название + список items из Default Fill |
| **Toggle элементов** | Tap на item = checked/unchecked |
| **Прогресс** | "X/Y выполнено" в заголовке |
| **Прокрутка** | LazyColumn для чеклистов с 8+ items |
| **Deep link** | Tap на заголовок → ChecklistDetail в приложении |
| **Конфигурация** | Выбор чеклиста при добавлении виджета |

### Архитектура

```
composeApp/src/androidMain/
├── kotlin/.../widget/
│   ├── ChecklistWidget.kt              # GlanceAppWidget
│   ├── ChecklistWidgetReceiver.kt      # GlanceAppWidgetReceiver
│   ├── ChecklistWidgetContent.kt       # UI Composables
│   ├── config/
│   │   └── WidgetConfigActivity.kt     # Configuration Activity
│   ├── data/
│   │   ├── WidgetRepository.kt         # Room access for widget
│   │   └── WidgetStateManager.kt       # DataStore for widget config
│   ├── actions/
│   │   ├── ToggleItemAction.kt         # ActionCallback for toggle
│   │   ├── OpenChecklistAction.kt      # ActionCallback for deep link
│   │   └── RefreshAction.kt            # ActionCallback for refresh
│   └── worker/
│       └── WidgetUpdateWorker.kt       # WorkManager for sync
└── res/
    ├── xml/
    │   └── checklist_widget_info.xml   # Widget metadata
    ├── layout/
    │   └── widget_loading.xml          # Loading state layout
    └── drawable/
        └── widget_preview.png          # Preview image
```

### Data Flow

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Room DB       │────▶│  WidgetRepository │────▶│ ChecklistWidget │
│ (ChecklistFill) │     │  (singleton)      │     │ (GlanceAppWidget)│
└─────────────────┘     └──────────────────┘     └─────────────────┘
        ▲                                                │
        │                                                │ toggle
        │                                                ▼
        │                                        ┌─────────────────┐
        └────────────────────────────────────────│ ToggleItemAction│
                      update                     │ (ActionCallback)│
                                                 └─────────────────┘
```

## Technical Considerations

### Зависимости (gradle/libs.versions.toml)

```toml
[versions]
glance = "1.1.1"

[libraries]
glance = { module = "androidx.glance:glance", version.ref = "glance" }
glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
```

### Критичные паттерны из кодовой базы

1. **Прогресс из Fill, НЕ из Template**
   ```kotlin
   // ✅ ПРАВИЛЬНО
   val progress = defaultFill.items.count { it.checked } / defaultFill.items.size

   // ❌ НЕПРАВИЛЬНО - template items всегда unchecked
   val progress = checklist.items.count { it.checked } / checklist.items.size
   ```

2. **Race condition при удалении** — отменять Flow-наблюдение перед удалением чеклиста

3. **Koin в виджете** — использовать `GlobalContext.get()` для доступа к DI

### Доступ к Room из виджета

```kotlin
class WidgetRepository private constructor(context: Context) : KoinComponent {
    private val checklistDao: ChecklistDao by inject()
    private val fillDao: ChecklistFillDao by inject()

    suspend fun getChecklistWithDefaultFill(checklistId: Long): ChecklistWidgetData? {
        val checklist = checklistDao.getById(checklistId) ?: return null
        val defaultFill = fillDao.getDefaultFillByChecklistId(checklistId)

        return ChecklistWidgetData(
            checklistId = checklist.id,
            name = checklist.name,
            items = defaultFill?.items ?: checklist.items.map {
                ChecklistFillItem(text = it.text, checked = false)
            },
            fillId = defaultFill?.id
        )
    }

    suspend fun toggleItem(fillId: Long, itemIndex: Int) {
        val fill = fillDao.getById(fillId) ?: return
        val updatedItems = fill.items.toMutableList()
        updatedItems[itemIndex] = updatedItems[itemIndex].copy(
            checked = !updatedItems[itemIndex].checked
        )
        fillDao.update(fill.copy(items = updatedItems))
    }
}
```

### Синхронизация данных

**Стратегия:** Двойной механизм обновления

1. **Мгновенное обновление** — при toggle в виджете или изменении в приложении
   ```kotlin
   // После toggle
   ChecklistWidget().update(context, glanceId)
   ```

2. **Периодическое обновление** — WorkManager каждые 15 минут (fallback)
   ```kotlin
   PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES)
   ```

3. **Уведомление из приложения** — при изменении данных в Repository
   ```kotlin
   // В ChecklistRepositoryImpl
   suspend fun updateFill(fill: ChecklistFill) {
       fillDao.update(fill.toEntity())
       WidgetUpdateWorker.enqueueOneTimeWork(context)
   }
   ```

### Размеры виджета

```xml
<!-- res/xml/checklist_widget_info.xml -->
<appwidget-provider
    android:minWidth="250dp"      <!-- 4 cells -->
    android:minHeight="110dp"     <!-- 2 cells -->
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:resizeMode="vertical"
    android:maxResizeHeight="400dp"
    ... />
```

### Error States

| Состояние | UI | Действие |
|-----------|-----|----------|
| Чеклист удалён | "Checklist not found" + кнопка "Select another" | Открыть Configuration Activity |
| Нет default fill | Показать items из template (all unchecked) | Автосоздать fill при первом toggle |
| Database error | "Unable to load" + кнопка "Retry" | Повторить запрос |
| Пустой чеклист | "No items" | — |

## Acceptance Criteria

### Functional Requirements

- [x] Виджет отображает название чеклиста в заголовке
- [x] Виджет отображает прогресс "X/Y выполнено"
- [x] Tap на элемент переключает его состояние (checked/unchecked)
- [x] Изменения сохраняются в Room базу данных
- [x] LazyColumn позволяет прокручивать длинные чеклисты
- [x] Tap на заголовок открывает ChecklistDetail в приложении
- [x] Configuration Activity позволяет выбрать чеклист при добавлении виджета
- [x] При удалении чеклиста виджет показывает error state

### Non-Functional Requirements

- [ ] Виджет загружается менее чем за 500ms
- [ ] Toggle элемента отрабатывает менее чем за 200ms
- [ ] Поддержка Dark/Light theme (следует системной теме)
- [ ] Touch target минимум 48dp height для accessibility
- [ ] Виджет корректно работает при отсутствии сети (offline-first)

### Testing Requirements

- [ ] Unit-тесты для WidgetRepository
- [ ] Unit-тесты для ActionCallbacks
- [ ] Glance testing для UI компонентов
- [ ] Manual testing на разных размерах экрана

## Success Metrics

| Метрика | Цель |
|---------|------|
| Widget добавлен | ≥10% активных пользователей добавили виджет |
| Retention | Пользователи с виджетом открывают приложение на 20% чаще |
| Task completion | Увеличение количества toggle операций на 30% |

## Dependencies & Risks

### Dependencies

| Зависимость | Тип | Статус |
|-------------|-----|--------|
| Jetpack Glance 1.1.1 | Library | Stable |
| Room database | Internal | ✅ Существует |
| ChecklistRepository | Internal | ✅ Существует |
| Default Fill logic | Internal | ✅ Существует (`getDefaultFillByChecklistId`) |

### Risks

| Риск | Вероятность | Impact | Mitigation |
|------|-------------|--------|------------|
| Glance ограничения UI | Medium | Medium | Использовать только поддерживаемые composables |
| Battery drain от частых обновлений | Low | High | Debounce updates, минимум 15 мин для periodic |
| Race condition при удалении | Medium | High | Проверять existence перед операциями |
| Touch targets слишком маленькие | Medium | Medium | Использовать resizable widget, min 48dp per item |

## Implementation Plan

### Phase 1: Core Widget (MVP)

**Файлы:**
- `ChecklistWidget.kt` — GlanceAppWidget с базовым UI
- `ChecklistWidgetReceiver.kt` — BroadcastReceiver
- `ChecklistWidgetContent.kt` — UI composables
- `WidgetRepository.kt` — доступ к Room
- `checklist_widget_info.xml` — метаданные виджета
- `AndroidManifest.xml` — регистрация receiver

**Функциональность:**
- Отображение чеклиста с прогрессом
- Toggle элементов
- LazyColumn для прокрутки

### Phase 2: Configuration & Actions

**Файлы:**
- `WidgetConfigActivity.kt` — выбор чеклиста
- `WidgetStateManager.kt` — сохранение конфигурации
- `ToggleItemAction.kt` — ActionCallback для toggle
- `OpenChecklistAction.kt` — deep link в приложение

**Функциональность:**
- Configuration Activity при добавлении виджета
- Deep link в приложение
- Сохранение выбранного checklistId

### Phase 3: Sync & Polish

**Файлы:**
- `WidgetUpdateWorker.kt` — WorkManager для sync
- Обновление `ChecklistRepositoryImpl` — notify widget on changes
- Error state composables

**Функциональность:**
- Синхронизация с приложением
- Error states (deleted checklist, no fill)
- Dark/Light theme support

## References & Research

### Internal References

- Модели данных: `feature/checklist/src/commonMain/.../domain/model/Checklist.kt`
- Repository: `feature/checklist/src/commonMain/.../domain/repository/ChecklistRepository.kt`
- Default Fill logic: `ChecklistRepository.getDefaultFillByChecklistId()`
- Паттерн прогресса: `docs/solutions/logic-errors/progress-bar-shows-zero-using-template-instead-of-fill.md`
- Race condition fix: `docs/solutions/database-issues/room-cascade-delete-flow-race-condition.md`

### External References

- [Jetpack Glance Overview](https://developer.android.com/develop/ui/compose/glance)
- [Create App Widget with Glance](https://developer.android.com/develop/ui/compose/glance/create-app-widget)
- [Glance State Management](https://developer.android.com/develop/ui/compose/glance/glance-app-widget)
- [Widget Configuration](https://developer.android.com/develop/ui/views/appwidgets/configuration)
- [Glance Release Notes](https://developer.android.com/jetpack/androidx/releases/glance) — version 1.1.1

### Code Examples

#### ChecklistWidget.kt (основной виджет)

```kotlin
class ChecklistWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 110.dp),  // 4x2 default
            DpSize(250.dp, 200.dp),  // 4x3 medium
            DpSize(250.dp, 300.dp)   // 4x4 large
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val koin = GlobalContext.get()
        val repository: WidgetRepository = koin.get()
        val stateManager: WidgetStateManager = koin.get()

        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val checklistId = stateManager.getSelectedChecklistId(appWidgetId)

        provideContent {
            GlanceTheme {
                if (checklistId != null) {
                    val data by repository.observeChecklistWithDefaultFill(checklistId)
                        .collectAsState(initial = null)

                    when {
                        data == null -> LoadingContent()
                        data.notFound -> NotFoundContent(appWidgetId)
                        else -> ChecklistWidgetContent(data, appWidgetId)
                    }
                } else {
                    NotConfiguredContent()
                }
            }
        }
    }
}
```

#### ToggleItemAction.kt (обработка toggle)

```kotlin
class ToggleItemAction : ActionCallback {

    companion object {
        val FILL_ID_KEY = ActionParameters.Key<Long>("fill_id")
        val ITEM_INDEX_KEY = ActionParameters.Key<Int>("item_index")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val fillId = parameters[FILL_ID_KEY] ?: return
        val itemIndex = parameters[ITEM_INDEX_KEY] ?: return

        val koin = GlobalContext.get()
        val repository: WidgetRepository = koin.get()

        repository.toggleItem(fillId, itemIndex)
        ChecklistWidget().update(context, glanceId)
    }
}
```

#### ChecklistWidgetContent.kt (UI)

```kotlin
@Composable
fun ChecklistWidgetContent(
    data: ChecklistWidgetData,
    appWidgetId: Int
) {
    val checkedCount = data.items.count { it.checked }
    val totalCount = data.items.size

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .padding(12.dp)
    ) {
        // Header with title and progress
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>(
                    actionParametersOf(
                        ActionParameters.Key<Long>("checklist_id") to data.checklistId
                    )
                )),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = data.name,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "$checkedCount/$totalCount",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.secondary
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Items list with scroll
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            itemsIndexed(data.items) { index, item ->
                ChecklistItemRow(
                    item = item,
                    fillId = data.fillId,
                    itemIndex = index
                )
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistFillItem,
    fillId: Long?,
    itemIndex: Int
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(
                onClick = if (fillId != null) {
                    actionRunCallback<ToggleItemAction>(
                        actionParametersOf(
                            ToggleItemAction.FILL_ID_KEY to fillId,
                            ToggleItemAction.ITEM_INDEX_KEY to itemIndex
                        )
                    )
                } else {
                    actionRunCallback<CreateFillAndToggleAction>(...)
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckBox(
            checked = item.checked,
            onCheckedChange = null, // Handled by row click
            modifier = GlanceModifier.size(24.dp)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = item.text,
            style = TextStyle(
                fontSize = 14.sp,
                color = if (item.checked) {
                    GlanceTheme.colors.outline
                } else {
                    GlanceTheme.colors.onSurface
                },
                textDecoration = if (item.checked) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                }
            ),
            maxLines = 2,
            modifier = GlanceModifier.defaultWeight()
        )
    }
}
```
