---
title: "AppFunctions (Android MCP): операции с чек-листами для Gemini через ToolCallDispatcher"
summary: "Jetpack AppFunctions 1.0.0 в :androidApp: сервис поверх диспетчера AI-чата отдаёт системному ассистенту список, создание, пункты, напоминания; в Gemini — только после одобрения Connected Apps."
date: 2026-11-27
type: feature
modules: [androidApp, feature/aichat/api]
keywords: [appfunctions, android-mcp, AppFunctionService, AppFunctionDeclaration, ToolCallDispatcher, connected-apps, gemini, ksp, permission]
project: Checklists
---

# AppFunctions (Android MCP) в Gisti

**Суть:** функции живут в `androidApp/src/main/kotlin/com/antonchuraev/homesearchchecklist/appfunctions/BaseChecklistAppFunctionService.kt` и вызывают тот же `ToolCallDispatcher`, что AI-чат, — лимиты Free, синхронизация и разрешение списка по имени/id общие. Новая функция = метод с `@AppFunctionDeclaration(isDescribedByKDoc = true)` + маппинг в `ChecklistAppFunctionMapper`. Gemini начнёт их вызывать только после одобрения заявки в Connected Apps Platform и создания connected app типа «Android MCP»; локально — adb-командами из «Примеры» (источник: developer.android.com/ai/appfunctions/add-appfunctions).

## Проблема / Контекст

AppFunctions (Android 16+, API 36) — функции приложения для системного ассистента на устройстве. Вызывают их только авторизованные ассистенты (`EXECUTE_APP_FUNCTIONS`, источник: developer.android.com/ai/appfunctions); для Gemini нужен connected app, доступ — по заявке (подана 2026-11-27 из Play Console).

Скилл `~/.claude/skills/appfunctions` описывает alpha10. В 1.0.0: `@AppFunctionDeclaration` вместо `@AppFunction`, `instructions` вместо `description`, явный `isDescribedByKDoc`, без `appfunctions:aggregateAppFunctions` (источник: androidx release notes appfunctions).

## Решение

- `gradle/libs.versions.toml`: `appfunctions = "1.0.0"`, библиотека + `appfunctions-compiler` (KSP).
- `androidApp/build.gradle.kts`: плагин KSP, `projects.feature.aichat.api`. KSP — только в application-модуле: `composeApp` — KMP-библиотека, там KSP для Android-таргета подключается иначе и не нужен.
- `BaseChecklistAppFunctionService` — абстрактный `AppFunctionService` с `@AppFunctionServiceEntryPoint(serviceName = "ChecklistAppFunctionService", appFunctionXmlFileName = "gisti_app_function_service")`, `@RequiresApi(36)`; KSP генерирует конкретный сервис и `assets/gisti_app_function_service.xml`. Зависимости — Koin `by inject()` (Koin поднимает `GistiAndroidApplication` до привязки сервиса).
- Функции: `listChecklists`, `createChecklist`, `addItems`, `completeItem`, `setItemReminder`, `readChecklist`, `findItems`, `renameChecklist`, `moveItem`. Id — `com.antonchuraev.homesearchchecklist.appfunctions.BaseChecklistAppFunctionService#<метод>`.
- `ChecklistAppFunctionMapper` строит `ToolCall` и переводит `DispatchOutcome` в результат или типизированный `AppFunctionException` (неоднозначное имя → invalid argument со списком кандидатов, не найдено → element not found, лимит Free → limit exceeded). Тест: `androidApp/src/test/kotlin/com/antonchuraev/homesearchchecklist/appfunctions/ChecklistAppFunctionMapperTest.kt` (Robolectric: конструкторы исключений читают `Bundle.EMPTY`).
- `AndroidManifest.xml`: сервис с `BIND_APP_FUNCTION_SERVICE` + property `android.app.appfunctions.app_metadata` → `res/xml/app_metadata.xml` (инструкции для агента).

## Почему именно так

- **Через диспетчер:** скопированный гейт расходится; диспетчер уже держит лимит Free и «id важнее имени».
- **Без удалений, вложений и AI-генерации** — агент не меняет данные необратимо и не тратит кредиты.
- **Необязательный `checklistId`:** имя ищется по подстроке, «Shopping» рядом с «Shopping for party» неоднозначно навсегда.
- **Будильник ставит сервис:** диспетчер пишет время, но `ChecklistReminderScheduler` не вызывает — тот же дефект у AI-чата, чинится отдельно.

## Примеры

```bash
adb shell cmd app_function list-app-functions --package com.antonchuraev.aichecklists  # источник: developer.android.com/ai/appfunctions/add-appfunctions, проверено на Pixel 9 2026-11-27
adb shell "cmd app_function execute-app-function --package com.antonchuraev.aichecklists --function 'com.antonchuraev.homesearchchecklist.appfunctions.BaseChecklistAppFunctionService#listChecklists' --parameters '{}'"  # источник: та же страница
```

После одобрения заявки (не проверено — доступа ещё нет): Connected Console → Create app → Connected app → категория «Работа» → тип «Android MCP» → привязать Play-приложение → метаданные (иконка 512×512, описание < 30 слов, 3 capabilities, 3 примера промптов с `@Gisti`, ToS и Privacy URL) → AAB в internal testing → «Submit connected app and continue» → тестеры `@gmail.com` → проверка в Gemini.

## Связанные файлы

- `androidApp/src/main/kotlin/com/antonchuraev/homesearchchecklist/appfunctions/BaseChecklistAppFunctionService.kt`
- `androidApp/src/main/kotlin/com/antonchuraev/homesearchchecklist/appfunctions/ChecklistAppFunctionMapper.kt`
- `androidApp/src/main/kotlin/com/antonchuraev/homesearchchecklist/appfunctions/ChecklistAppFunctionModels.kt`
- `androidApp/src/main/res/xml/app_metadata.xml`
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/aichat/ToolCallDispatcherImpl.kt`
