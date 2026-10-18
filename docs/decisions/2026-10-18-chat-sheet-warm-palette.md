---
title: "Шторка AI-чата и нижний хром в тёплой палитре темы"
summary: "v2-шторка чата → AppSurface.chatSheet() без верхней линии, пузырь пользователя light #D6E4F7; нижний хром → тёплый песок surfaceContainerHighest / surfaceContainerLow без шва."
date: 2026-10-18
type: decision
modules: [core/designsystem, composeApp, feature/aichat/impl, feature/home]
keywords: [theme, darkmode, chat-sheet, bottom-chrome, AppSurface.bottomChrome, AppSurface.chatSheet, AppChatColors.userBubble, GistiGlassChatDock, showTopHairline, V2ChatDockOverlay]
project: Checklists
---

# Шторка AI-чата и нижний хром в тёплой палитре темы

**Суть:** v2-шторка чата красится `AppSurface.chatSheet()` (light `surface`, dark `surfaceContainerLow`) и рисуется без верхней линии (`showTopHairline = false`). Пузырь пользователя — `AppChatColors.userBubble()`. Нижний хром (`AppSurface.bottomChrome()`: навбар, плита Ask Gisti, capture-dock) — тёплый песок `surfaceContainerHighest` / `surfaceContainerLow`; существующие линии `bottomChromeSeam()` остались, нового шва и тени нет. `GistiColors.chrome` удалён. Тон контента в шторке обязан остаться `BottomChrome`.

## Проблема / Контекст

После редизайна B тема стала тёплой, а шторка чата и нижний хром остались холодными (`#DCE2EC` / `#191D25`) под тёплыми элементами. Пузырь пользователя `#E3F2FD` сливался с песочной страницей.

Шторка: владелец выбрал вариант A из трёх. Хром: A внедрён как рекомендованный, ответа владельца не было. Верхнюю линию шторки владелец попросил убрать.

## Решение

| Токен | Light | Dark |
|---|---|---|
| v2-шторка, полоса навигации под ней | `surface` `#F4EFE7` | `surfaceContainerLow` `#221F1B` |
| Пузырь пользователя / текст | `#D6E4F7` / `onPrimaryContainer` | `primaryContainer` / `onPrimaryContainer` |
| Нижний хром `bottomChrome()` | `surfaceContainerHighest` `#E4DCD0` | `surfaceContainerLow` `#221F1B` |

- `GistiGlassChatDock` получил `containerColor` и `showTopHairline`, `GistiFullChatOverlay` — `containerColor`. v2 передаёт `chatSheet()` и `false`; control-арм рисует значения по умолчанию.
- `bottomChrome()` не ссылается на `chatSheet()`: в dark они совпадают случайно.

## Почему именно так

- Шторка A: один новый hex, остальное — роли темы.
- Хром A: отделяется от страницы тоном без линии; B в light сливается со страницей, C сливается со срезанной карточкой.

**Грабли:**
- Dark: шторка, хром и `AppSurface.card()` одного тона. Тон `Page` вместо `BottomChrome` утопит пузыри ИИ. `ComposerCard` (`card()`) на плите держит только обводка.
- Light: шторка == страница, без линии край держат только скрим и скругление.
- `BOTTOM_CHROME_IDLE_ALPHA_LIGHT` не понижать: на новой плите запас AA меньше.

## Связанные файлы

- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/Elevation.kt` — `chatSheet()`, `bottomChrome()`
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/ChatColors.kt` — `userBubble()`, `onUserBubble()`
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/components/gisti/GistiChatDock.kt`
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/components/gisti/GistiFullChatOverlay.kt`
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/navigation/V2ChatDockOverlay.kt`
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/App.kt`
- `feature/aichat/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/aichat/impl/presentation/components/ChatMessageBubble.kt`
