---
title: "Compose Inset Leakage & Overlay State Isolation Patterns"
date: 2026-07-27
type: bug-fix
modules: [core/designsystem, composeApp/navigation, feature/home]
keywords: [AppScaffold, WindowInsets, ime, navigationBars, bottomBar, AnchoredDraggableState, state-leakage, Modifier.aspectRatio, Compose-layout]
project: Gisti
---

# Compose Inset Leakage & Overlay State Isolation Patterns

## Проблема / Контекст

Две неочевидные ловушки, найденные только на live-run (эмулятор + устройство), не воспроизводятся in vitro:

### 1. Inset Leakage: контент-контейнер, не достигающий низа окна

Когда контейнер (например, `Column` в `AppScaffold.content`) не расширяется до низа окна (кончается выше `navigationBar`), всё, что внутри считает инсеты от низа **окна**, а не от низа контейнера. Результат: элементы, привязанные к `navigationBars` или `ime`, промахиваются ровно на высоту части окна, которую не занимает контейнер.

**Симптом:** капча-док с `imePadding()` вставал на 80dp выше клавиатуры; между инпутом и клавиатурой зияла полоса ровно в высоту нижнего навигационного бара (56dp на Android).

### 2. State Leakage: контекст overlay переживает закрытие

`AnchoredDraggableState` и связанные с ним данные (контекст, ID списка) живут дольше видимости самого overlay. Закрытие dock не сбрасывает контекст.

**Симптом:** `chatSheetContextId` оставался привязан к первому экрану (например, Inbox) даже после закрытия дока и свайпа на Projects. Открытие чата с FAB на Projects молча возвращало контекст Inbox → чат задавал вопрос о неправильном списке.

## Решение

### 1. Inset Fix: контейнер консюмит разницу

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .consumeWindowInsets(navigationBars + BottomBarHeight)
) {
    // content here
}
```

`consumeWindowInsets()` убирает размер из расчёта для вложенных элементов, которые используют `Modifier.imePadding()` / `Modifier.navigationBarsPadding()`. Контейнер обязан явно сказать «я кончаюсь здесь, учитывайте это».

**Правило обобщения:** любой контейнер, который не достаёт до края (navigationBar, ime, statusBar), обязан консюмить в `consumeWindowInsets()` ровно то, что он **не занимает**. В `AppScaffold`:
- `content` консюмит ровно то, сколько `bottomBar` высотой (т.е. `BottomBarHeight`, который может быть `navigationBarHeight + ime`)
- `bottomBar` автоматически поднимается на `ime`, но только если `content` выше не воровал инсеты

### 2. State Fix: явный reset при входе в overlay

```kotlin
var chatSheetContextId by remember { mutableStateOf<String?>(null) }

FloatingActionButton(
    onClick = {
        chatSheetContextId = null  // <-- MUST reset
        v2ChatDockOpen = true
    }
)
```

Если FAB или иная точка входа не обязана знать о конкретном контексте (например, FAB на главных табах), то контекст обязан быть `null`. `onOpenChatSheet` коллбэк (из `ChecklistDetail`) может заполнить контекст; FAB — очищает.

**Правило обобщения:** контекст/ID, который живёт дольше той поверхности, которая его задаёт, должен быть сброшен **той точкой входа, которая его не создаёт**. Иначе — тихое переиспользование старого контекста.

## Почему именно так

### Inset Design в Compose

Insets (statusBars, navigationBars, ime) — это свойства **окна**, не контента. `Modifier.imePadding()` спрашивает окно: «где нижняя граница клавиатуры?» и не интерпретирует «выше/ниже моего контейнера».

При наследовании: если контейнер X потребляет половину высоты навигационного бара в `consumeWindowInsets()`, то дочерний элемент в X, спрашивающий `navigationBars`, получит `0.dp` (остаток). Но если X это не делает, то дочерний получит полный размер бара — даже если X его не отображает.

### Overlay State Isolation

`AnchoredDraggableState` не может иметь состояние «скрыт» (только `Peek`/`Expanded`). Видимость dock — это отдельный `Boolean`, которым владеет родитель. Если dock можно открыть с разных точек входа (FAB с Inbox, action из detail, долгий-тап), то каждая точка входа должна явно сбросить контекст, если она не создаёт его.

Альтернатива (не рекомендуется): хранить контекст в VM вместо recomposition-state — но это усложняет teardown и усугубляет проблему.

## Примеры

### Правильно: FAB на табах

```kotlin
Button(
    onClick = {
        // FAB на Inbox/Projects/Overview — не знает о контексте
        v2ChatContext = null
        v2ChatDockOpen = true
    }
) { Text("AI") }
```

### Неправильно: dock сохраняет контекст

```kotlin
// ❌ После закрытия дока остаётся старый контекст
var context by remember { mutableStateOf("ChecklistA") }

V2ChatDockOverlay(
    isOpen = v2ChatDockOpen,
    onClose = { v2ChatDockOpen = false }  // context не сбрасывается
)
```

### Правильно: AppScaffold + content

```kotlin
AppScaffold(
    topBar = { /* ... */ },
    bottomBar = { AddItemInputField() },  // consumes ime
    content = { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .consumeWindowInsets(navigationBars)  // ← важно
        ) {
            LazyColumn {
                // ime-aware элементы здесь получат правильный сигнал
            }
        }
    }
)
```

## Связанные файлы

- `composeApp/src/commonMain/kotlin/.../V2ChatDockOverlay.kt` — реализация overlay с state reset
- `composeApp/src/commonMain/kotlin/.../V2NavigationShell.kt` — структура с `overlayContent` слотом
- `feature/home/src/commonMain/.../inbox/InboxScreen.kt` — FAB с контекст-ресетом
- `core/designsystem/src/commonMain/.../components/AddItemInputField.kt` — использование `size(56.dp)` вместо `height.aspectRatio`
