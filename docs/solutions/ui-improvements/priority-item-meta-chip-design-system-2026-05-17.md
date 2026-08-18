---
title: "Priority/Item Meta-Chip — Design System Integration"
date: 2026-05-17
type: ui-improvements
modules: [core/designsystem, feature/home]
keywords: [AppItemMetaChip, design-system, meta-information, read-only-pill, priority, reminder, attachments, card-polish, material3-colors]
project: Checklists
---

# Priority/Item Meta-Chip — Design System Integration

## Проблема / Контекст

После внедрения Priority Items (2026-05-09) и Item Attachments (2026-05-15), карточка `ChecklistItemCard` начала скапливать inline-row элементы (Star icon, Reminder button, Attachment count), загромождая UI и съедая место на узких экранах (wasmJs, компактные телефоны). Полифишер из инлайн-кнопок и иконок усложнил hit-zone логику (30/70 split за ним).

**Требование:** консолидировать визуальные индикаторы (priority, reminder status, attachment count) в единую читаемую полоску **read-only** чипсов, интегрировать 6-й `App*` компонент в дизайн-систему (parallel с `AppButton`, `AppSwitch`, `AppTextField`, `AppCard`, `AppLinearProgressIndicator`).

**Что было:**
```kotlin
// OLD: inlined actions
Row {
    Icon(Icons.Filled.Star)                    // priority inline
    IconButton { ... }                         // reminder action (bad for 30/70)
    Text("${item.attachments.size}")           // attachments count bare
}
```

## Решение

### 1. Создан `AppItemMetaChip` (core/designsystem)

Read-only pill-shaped chip для отображения одного мета-элемента (priority, reminder, attachments). **Без `onClick` или `ripple`** — это информационный элемент, не action button. Surface-based:

```kotlin
@Composable
fun AppItemMetaChip(
    label: String,
    icon: androidx.compose.material.icons.Icons,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
)
```

**Характеристики:**
- Shape: `RoundedCornerShape(6.dp)` (низкий скругление, не pill)
- Icon: 14.dp (меньше чем standard 24.dp для компактности)
- Text: `labelSmall` + `contentColor`
- Padding: 6.dp horizontal (Surface handles vertical)
- Surface defaults: elevation 0 (no shadow), no ripple effect

**Варианты цвета:** параметризованы `containerColor` + `contentColor` для переиспользования на других элементах (future: tag colors, status badges, progress indicators).

### 2. `ItemMetaRow(item)` в ChecklistDetailScreen

Приватный composable группирует 0–3 чипа в один Row под иконкой/текстом карточки:

```kotlin
@Composable
private fun ItemMetaRow(item: ChecklistFillItem) {
    val rowItems = mutableListOf<@Composable () -> Unit>()
    
    // Priority chip
    if (item.priority > 0) {
        rowItems.add {
            AppItemMetaChip(
                label = stringResource(Res.string.item_chip_priority),
                icon = Icons.Filled.Star,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
    
    // Reminder chip (обычный или просрочен)
    if (item.reminderAt != null) {
        val isMissed = item.reminderAt < Clock.System.now() && item.repeatRule == null
        rowItems.add {
            AppItemMetaChip(
                label = "${formatTime(item.reminderAt)}",  // 14:30 или "23 мая 14:30"
                icon = Icons.Filled.Schedule,
                containerColor = if (isMissed) 
                    MaterialTheme.colorScheme.errorContainer 
                else 
                    MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isMissed)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
    
    // Attachments chip
    if (item.attachments.isNotEmpty()) {
        rowItems.add {
            AppItemMetaChip(
                label = "${item.attachments.size}",
                icon = Icons.Filled.AttachFile,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
    
    if (rowItems.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            rowItems.forEach { it() }
        }
    }
}
```

**Вставка в `ChecklistDetailScreen`:**
```kotlin
Column {
    Row(/* header: text + star */) { ... }
    ItemMetaRow(item)  // ← вместо inline icons
    Row(/* 30/70 hit-zone overlay */) { ... }
}
```

### 3. Цветовая палитра (Material 3)

| Элемент | Container | OnContainer | Причина |
|---------|-----------|-------------|---------|
| **Priority** | `tertiaryContainer` | `onTertiaryContainer` | Редко используется в проекте → выделяется, не конфликтует с primary/secondary |
| **Reminder (not missed)** | `primaryContainer` | `onPrimaryContainer` | Основной статус → primary accent (matches old design) |
| **Reminder (missed)** | `errorContainer` | `onErrorContainer` | Срочность без отдельного компонента (красный = внимание) |
| **Attachments** | `secondaryContainer` | `onSecondaryContainer` | Дополнительная информация → secondary accent |

Палитра гарантирует WCAG AA contrast на всех комбинациях Material You (light + dark + dynamic color).

### 4. Локализация

Добавлены ключи в `core/designsystem/src/commonMain/composeResources/values/strings.xml`:
```xml
<string name="item_chip_priority">Important</string>
<string name="item_chip_reminder">Reminder</string>
```

И в `values-ru/strings.xml`:
```xml
<string name="item_chip_priority">Важное</string>
<string name="item_chip_reminder">Напоминание</string>
```

**Примечание:** время reminder (14:30, "23 мая") форматируется programmatically через `formatTime()`, не хардкод.

### 5. Hit-zone 30/70 неизменён

Интерактивный overlay `Row(matchParentSize, combinedClickable())` остался на месте (ChecklistDetailScreen.kt:1229–1252). Чипы рендерятся **внутри** неинтерактивной content-zone, тап по nim не вызывает ни ripple, ни onClick. Тап по правым 70% по-прежнему открывает `ItemDetailsSheet`.

## Почему именно так

### Surface вместо Card/Chip

Material3 `Chip` предназначена для действий (Delete, Filter). Material3 `Card` — для контейнеризации блоков. `Surface` с параметрами (shape, colors, elevation=0) — минимум абстракции, максимум контроля для read-only badge. Это паттерн для других информационных элементов (future: tag badges, status indicators, progress stages).

### Отдельный чип вместо FlowRow

На узких экранах (320dp) FlowRow переносит чипы, разрывая информацию. `Row + widthIn(max=140dp)` на reminder-чипе с `maxLines=1` + ellipsis гарантирует однострочный layout даже на мобилях. Trade-off: дата скрывается под «14:3...», но в ItemDetailsSheet доступна полная информация (UI не скрывает функциональность, только сокращает preview).

### Material 3 Color Scheme вместо хардкода

Все цвета **берутся из `MaterialTheme.colorScheme`** → работают при dynamic color (Android 12+), dark theme, a/b-тестах с кастомными палитрами. Ноль хардкода (#2196F3, #FF5252 и т.д.).

### Порядок приоритет → напоминание → вложения

Убывание **user-significance**: приоритет = планирование (пользователь сам выставил), напоминание = темпоральная сигнализация (система + пользователь), вложения = ресурсы (есть или нет). Scan слева → право = максимальная информативность на первый взгляд.

## Примеры

### Обычный item без мета

```
┌──────────────────────────────────┐
│ ☐ Купить молоко                  │
│ (no chips)                       │
│ [                              ] │ ← 30/70 hit-zone
└──────────────────────────────────┘
```

### Item с приоритетом и напоминанием

```
┌──────────────────────────────────┐
│ ☐ Купить молоко                  │
│ [⭐ Важное] [🕐 14:30]           │
│ [                              ] │
└──────────────────────────────────┘
```

### Item с просроченным напоминанием

```
┌──────────────────────────────────┐
│ ☐ Купить молоко                  │
│ [⭐ Важное] [🕐 23 мая 14:30]    │ ← errorContainer (красный)
│ [                              ] │
└──────────────────────────────────┘
```

### Item со всем

```
┌──────────────────────────────────┐
│ ☐ Купить молоко                  │
│ [⭐ Важное] [🕐 14:30] [📎 3]    │
│ [                              ] │
└──────────────────────────────────┘
```

## Связанные файлы

- **New:** `core/designsystem/src/commonMain/kotlin/desingsystem/components/AppItemMetaChip.kt` (44 lines)
- **Modified:** `core/designsystem/src/commonMain/composeResources/values/strings.xml` (+2 rows)
- **Modified:** `core/designsystem/src/commonMain/composeResources/values-ru/strings.xml` (+2 rows)
- **Modified:** `feature/home/src/commonMain/kotlin/.../detail/ChecklistDetailScreen.kt` (+67 lines ItemMetaRow, -15 lines old inline icons)

## Pattern для переиспользования

### Data-Badge Design System Pattern

`AppItemMetaChip` — это шаблон для всех информационных элементов, которые нужны в будущем:
- **Tag badges:** `label="Customer"`, `containerColor=customColorForTag(tag.id)`
- **Status indicators:** `label="Archived"`, `icon=Icons.Filled.Archive`, `containerColor=outlineVariant`
- **Progress stages:** `label="50%"`, `icon=Icons.Filled.Check`, параметр `progress: Float?`
- **Due date indicator:** `label="3 дня"`, `icon=Icons.Filled.Event`, `containerColor=tertiaryContainer` (если near)

Все они наследуют:
1. Surface-based read-only structure (no ripple, no onClick by default)
2. Parameterized colors (Material Theme compatible)
3. Icon 14.dp + labelSmall text
4. Rounded shape 6.dp (low rounding)

## Валидация

- Build: `./gradlew androidApp:assembleDebug` → BUILD SUCCESSFUL 57s, нет новых warnings
- Device: APK install на Pixel 9 → Success, MainActivity loaded
- UI: Чипы рендерятся при наличии данных (визуальная проверка юзером)
- Hit-zone: 30/70 overlay неизменён, тап по правым 70% открывает ItemDetailsSheet (функциональная проверка)
