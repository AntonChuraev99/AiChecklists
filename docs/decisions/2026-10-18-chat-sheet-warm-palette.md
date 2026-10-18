---
title: "Шторка AI-чата в тёплой палитре темы (вариант A)"
summary: "Фон v2-шторки чата переведён с холодного gistiDockColor() на AppSurface.chatSheet() (surface / surfaceContainerLow), пузырь пользователя в light — #D6E4F7."
date: 2026-10-18
type: decision
modules: [core/designsystem, composeApp, feature/aichat/impl]
keywords: [theme, darkmode, chat-sheet, GistiGlassChatDock, GistiFullChatOverlay, AppSurface.chatSheet, AppChatColors.userBubble, V2ChatDockOverlay]
project: Checklists
---

# Шторка AI-чата в тёплой палитре темы

**Суть:** фон шторки чата в v2 берётся из `AppSurface.chatSheet()` (light `surface`, dark `surfaceContainerLow`), а не из `gistiDockColor()`. Пузырь пользователя красится `AppChatColors.userBubble()` / `onUserBubble()`. Холодный тон нижнего хрома, nav-bar, capture-dock и control-арм не тронуты. Тон контента в шторке обязан остаться `BottomChrome`.

## Проблема / Контекст

После редизайна B тема стала тёплой, а шторка чата продолжала краситься холодным тоном нижнего хрома (`gistiDockColor()`). Внутри шторки лежали тёплые пузыри, чипы и композер — смешение двух температур. Пузырь пользователя в light (`primaryContainer` `#E3F2FD`) почти не отделялся от песочной страницы.

`@design-expert` предложил три варианта (A «Тёплый лист», B «Синяя AI-зона», C «Сливки + фирменный синий») с прототипом. Владелец выбрал **A**.

## Решение

| Токен | Light | Dark |
|---|---|---|
| Фон шторки, полоса системной навигации под ней | `surface` `#F4EFE7` | `surfaceContainerLow` `#221F1B` |
| Пузырь пользователя / текст | `#D6E4F7` / `onPrimaryContainer` | `primaryContainer` / `onPrimaryContainer` (без изменений) |
| Скрим, пузырь ИИ, чипы, композер | без изменений | без изменений |

- `GistiGlassChatDock` и `GistiFullChatOverlay` получили параметр `containerColor: Color = gistiDockColor()`. Новый цвет передают только `V2ChatDockOverlay` и `App.kt` (оверлей при `NavVariant.V2`). Control-арм рисует прежний цвет по умолчанию.
- Глобальные `LightColorScheme` / `DarkColorScheme` и роль `primaryContainer` не менялись, поэтому goldens перезаписаны только в чатовых сьютах: `ChatDockVoiceHoldScreenshotTest`, `ChatScreenRedesignScreenshotTest`, `AiChatVariantsScreenshotTest`, `ChatSurfacePlanesScreenshotTest`.

## Почему именно так

- A — один новый hex, остальное — роли темы; B и C вводили новые цвета и перекрашивали больше.
- `containerColor` параметром, а не правкой `gistiDockColor()`: тот же аксессор красит nav-bar и capture-dock.

**Грабли:**
- Dark: шторка `#221F1B` == `AppSurface.card()`. Тон `Page` вместо `BottomChrome` утопит пузыри ИИ.
- Light: шторка == страница, край держат скрим, скругление и хайрлайн.
- Композер полноэкранного `ChatScreen` остался на холодном chrome — вне скоупа.

## Связанные файлы

- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/Elevation.kt` — `chatSheet()`
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/theme/ChatColors.kt` — `userBubble()`, `onUserBubble()`
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/components/gisti/GistiChatDock.kt`
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/components/gisti/GistiFullChatOverlay.kt`
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/navigation/V2ChatDockOverlay.kt`
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/App.kt`
- `feature/aichat/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/aichat/impl/presentation/components/ChatMessageBubble.kt`
