---
title: "Открыть док чата или звонок из экрана до шелла (онбординг)"
summary: "Экран вне шелла просит открыть чат/звонок после перехода: липкий ShellLaunchRequests + гейт стабильности шелла; иначе док закрывается подменой корня Main → Inbox."
date: 2026-11-04
type: pattern
modules: [composeApp, core/navigation, feature/onboarding]
keywords: [navigation, ShellLaunchRequests, LaunchedEffect, race-condition, v2ChatDockOpen, setDefaultRootRoute, Navigation3, onboarding, android, wasmjs]
project: Checklists
---

# Открыть док чата или звонок из экрана до шелла

**Суть:** экран, который живёт вне шелла (онбординг, Splash), не может открыть док чата или звонок событием навигатора — его некому принять. Запрос кладётся в липкий `ShellLaunchRequests` (`core/navigation/api/ShellLaunch.kt`), шелл в `App.kt` обрабатывает его только когда `isShellStableForLaunch(...)` истинно, а закрыватель доков по смене маршрута щадит маршрут, на котором док открыл запрос (`v2DockLaunchRoute`).

## Проблема / Контекст

Онбординг завершается кнопками Chat / Call → Inbox с открытым доком или звонком; оба живут в шелле `App.kt`. Ловушки: (1) событие навигатора с `replay = 0` теряется до монтирования шелла; (2) в v2 на первом запуске стек `[Main]` в том же кадре подменяется на `Inbox`, и `LaunchedEffect(currentTopRoute)` ставит `v2ChatDockOpen = false` — открытый док закрывается сам.

## Решение

- `ShellLaunchRequests` — `StateFlow<ShellLaunch?>` с `request` / `consume` (`core/navigation/impl/ShellLaunchRequestsImpl.kt`, Koin в `NavigationModule`). Онбординг кладёт запрос **после** успешного `completeOnboardingUseCase()`, прямо перед навигацией — неудачное завершение не оставляет висящего запроса.
- Эффект в `App.kt` ключуется на `pendingShellLaunch`, `armResolved`, `showV2Shell`, `showShell`, `v2RootApplied`, `v2InboxRooted`, `currentTopRoute` и выходит, пока `isShellStableForLaunch(...)` ложно: в v2 — пока подмена корня не прошла и Inbox нет в стеке.
- Открывая док, эффект запоминает `v2DockLaunchRoute = currentTopRoute`; закрыватель доков по смене маршрута пропускает ровно этот маршрут и сбрасывает маркер на первой же смене. Порядок двух эффектов в кадре перестаёт влиять на результат.
- v1 (classic): `OpenChat` → `navigateToAiChat()`; `ChatRoute` берёт тот же Activity-scoped `ChatViewModel`, prefill доходит.

## Почему именно так

- Не метод `AppNavigator`: у навигатора около 27 тестовых фейков (подсчёт feature-expert, (приблизительно)), и событие с `replay = 0` всё равно теряется до монтирования шелла.
- Не «запустить эффект позже» (`delay`, `snapshotFlow` по кадрам): полагается на порядок эффектов и ломается при следующей перестановке кода. Гейт по состоянию шелла проверяется юнит-тестом (`ShellLaunchGateTest`), сам порядок эффектов — только на устройстве.

## Связанные файлы

- `core/navigation/api/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/core/navigation/api/ShellLaunch.kt`
- `core/navigation/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/core/navigation/impl/ShellLaunchRequestsImpl.kt`
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/App.kt` — `isShellStableForLaunch`, `v2DockLaunchRoute`, `v2RootApplied`
- `composeApp/src/commonTest/kotlin/com/antonchuraev/homesearchchecklist/ShellLaunchGateTest.kt`
- Задача: `docs/active/onboarding-refresh-2026-11-04.md`
