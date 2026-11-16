---
title: "Агент layout-debug в debug-сборке Android: мост в androidMain, сервер в src/debug, только loopback"
summary: "Живое Compose-дерево с file:line для инструмента layout-debug: инертный мост в composeApp androidMain, агент и ui-tooling только в androidApp/src/debug, сокет на 127.0.0.1."
date: 2026-11-16
type: pattern
modules: [composeApp, androidApp]
keywords: [performance, layout-debug, LayoutDebugBridge, LayoutDebugRoot, LocalInspectionTables, SubcomposeLayout, ui-tooling, debug-source-set, adb-forward, ServerSocket]
project: Checklists
---

# Агент layout-debug в debug-сборке Android

**Суть:** debug-APK поднимает агент на `127.0.0.1:8790`; инструмент `~/.claude/layout-debug-mcp`
через `adb forward` читает `/tree`, `/screenshot` и шлёт `/override`. В release агента нет
по source set, мост в `androidMain` — no-op.

## Проблема / Контекст

Агент жил в ветке `feat/layout-debug-agent` (коммит `84409a0d`), отставшей от `master`
на 105 коммитов (источник: `git rev-list --count feat/layout-debug-agent..master`), —
debug-сборка из транка инструменту была недоступна. Перенесён cherry-pick'ом без
конфликтов; на Pixel 9 `/tree` отдал 196 ссылок `.kt` (источник: `Invoke-WebRequest /tree`
+ подсчёт `\.kt`).

## Решение

Два слоя:

- `LayoutDebugBridge.kt` (composeApp androidMain) — `LayoutDebugBridge`
  (флаг `enabled`, набор slot-таблиц) и `LayoutDebugRoot { }`. Зависит только от
  `compose-runtime`. Пока `enabled == false` — просто `content()`. `MainActivity`
  оборачивает `App()` в `LayoutDebugRoot`.
- `androidApp/src/debug/` — `LayoutDebugAgent` (HTTP-сервер, override), `TreeCapture`
  (разбор дерева через ui-tooling), автостарт `LayoutDebugInstaller` через
  `ContentProvider` в `src/debug/AndroidManifest.xml`. Ставит `enabled = true`.

Сокет — `ServerSocket(PORT, 0, InetAddress.getLoopbackAddress())`. Без
`Access-Control-Allow-Origin`; запрос с `Origin` или `Sec-Fetch-Site` (кроме `none`) → 403:
браузер ставит их сам, Node-клиент инструмента (undici) — нет. Живые сдвиги хранятся по
экземпляру LayoutNode (`IdentityHashMap`), не по id: позиционный id меняется между снимками.
Порт фиксированный: другое debug-приложение с той же копией агента может занять 8790 —
владельца видно по uid в `/proc/net/tcp6` (порт `:2256`).

## Почему именно так

- **Мост в androidMain, разбор в debug.** Хук должен видеть `MainActivity` (она в
  `composeApp`); `ui-tooling` и сервер в release отсутствуют физически, а не флагом.
- **`LocalInspectionTables`.** `Scaffold`, lazy-списки и `NavDisplay` композят в отдельные
  субкомпозиции — один корневой `CompositionData` теряет большую часть экрана.
- **Loopback.** `ServerSocket(PORT)` слушает все интерфейсы: дерево с чек-листами и
  `/override` видны всей Wi-Fi сети. `adb forward` приходит на loopback.

## Связанные файлы

- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/layoutdebug/LayoutDebugBridge.kt`
- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/MainActivity.kt`
- `androidApp/src/debug/kotlin/com/antonchuraev/aichecklists/app/layoutdebug/LayoutDebugAgent.kt`
- `androidApp/src/debug/kotlin/com/antonchuraev/aichecklists/app/layoutdebug/TreeCapture.kt`
- `androidApp/src/debug/AndroidManifest.xml`
- Инструмент и запуск — скилл `layout-debug` (`~/.claude/skills/layout-debug/SKILL.md`, вне репозитория (не проверено — вне репо)).
