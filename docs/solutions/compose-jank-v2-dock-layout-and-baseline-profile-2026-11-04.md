---
title: "Лаги Compose: покадровый layout в V2-доках, wiggle вне edit mode, Baseline Profile"
summary: "Jank при открытии дока, панели добавления и шторки шёл из покадрового measureAndLayout и JIT первого открытия, а не из рекомпозиций; стабильный content slot + Baseline Profile + замер Perfetto."
date: 2026-11-04
type: pattern
modules: [feature/home, composeApp, feature/aichat/impl, androidApp, baselineprofile]
keywords: [performance, jank, recomposition, measureAndLayout, perfetto, baseline-profile, startup-profile, stability, WindowInsets, consumeWindowInsets]
project: Checklists
---

# Лаги Compose: где на самом деле был jank и как его искать

**Суть:** лаг «при открытии» дока, панели добавления, шторки, табов — это JIT первого открытия (лечит Baseline Profile, модуль `:baselineprofile`) и покадровый `AndroidOwner:measureAndLayout`, когда анимированный нижний слот сжимает content slot `AppScaffold`. Мерить Perfetto, повторяя действие 3 раза в одном процессе.

## Проблема / Контекст

Статический аудит нашёл реальные рекомпозиции (wiggle вне edit mode, IME-инсет в теле экрана, весь `ChatScreenState` на корне `App`); фикс вдвое снизил число кадров, но медленных осталось столько же (111 → 111, источник: `dumpsys gfxinfo`). Владелец на V2-шелле, а фиксы попали в v1 `ChecklistDetailScreen`.

## Решение

- **Метод:** кадры `Choreographer#doFrame` по окнам действий, действие ×3 в процессе. Худший кадр падает на 2-м проходе → JIT; число кадров за бюджетом не падает → работа на кадр; доля `Recomposer:recompose` против `measureAndLayout` называет слой.
- **Layout:** `consumeWindowInsets(WindowInsets.ime)` на content box `V2NavigationShell.kt`, пока клавиатурой владеет чат; `holdContentHeightWhileSlotMoves` в `InboxScreen.kt` — страница на удержанной высоте, снап после 2 неподвижных кадров; `BoxWithConstraints` вокруг списка → `Box`.
- **Рекомпозиции:** `rememberEditModeWiggle` (угол в `graphicsLayer`), `rememberImeOpenState` (инсеты внутри `derivedStateOf`; старый вариант застывал — баг, `ImeOpenStateTest`), узкие `derivedStateOf` в `App.kt`.
- **Стабильность:** `compose_compiler_config.conf` — `List<*>`/`Set<*>`/`Map<*,*>`, не `kotlin.collections.*` (иначе стабилен `MutableList`).

## Почему именно так

- `gfxinfo` не видит содержимое кадра; проценты после фикса «ухудшились» из-за ушедших дешёвых кадров.
- Trace-зонд через `Modifier.layout {}` сам вызывает перемеры — делать `Modifier.Node`.
- Генератор профиля работает на свежей установке: онбординг, пустой Inbox — задачу он создаёт сам; кнопка «+» панели без `contentDescription`.

## Связанные файлы

- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/inbox/InboxScreen.kt`
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/navigation/V2NavigationShell.kt`
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/EditModeWiggle.kt`
- `compose_compiler_config.conf`, `androidApp/build.gradle.kts`
- `baselineprofile/src/main/java/com/antonchuraev/aichecklists/baselineprofile/GistiJourneys.kt`
- `docs/archive/perf-compose-recomposition-2026-11-04.md` — лог итераций, замеры, команды генерации
