---
title: "Апгрейд на Kotlin 2.4 и CMP 1.12: что ломается и чем закрыто"
summary: "В CMP 1.12 getString действительно приостанавливается: синхронно читающие state VM-тесты краснеют, а запись снимка state, взятого до suspend-вызова, затирает чужие поля. Плюс механические поломки бампа."
date: 2026-10-23
type: pattern
modules: [gradle, feature/aichat/impl, composeApp, core/common/api, core/auth/impl]
keywords: [upgrade, migration, compose-multiplatform, getString, MutableStateFlow.update, race-condition, kotlin-2.4, agp-9.3, play-services-auth, sqlite-web, kotlinx-browser, revenuecat]
project: Checklists
---

# Апгрейд на Kotlin 2.4 и CMP 1.12

**Суть:**
- В ViewModel не писать `_state.value = _state.value.copy(x = suspendCall())`. Правильно: `val x = suspendCall()`, затем `_state.update { it.copy(x = x) }`.
- В Robolectric-тестах ждать нужный state (`screenState.first { … }`), а не читать его сразу после интента.
- Сборочную связку брать из матрицы KGP: Kotlin 2.4.21 ↔ AGP ≤ 9.3.1 ↔ Gradle ≤ 9.7.0.

## Проблема / Контекст

Все зависимости обновлялись ради RevenueCat ≥ 3.10.0: этого требовал Play Console. После механических правок сборка позеленела, но 9 тестов `ChatPreviewContentTest` упали. Бисект показал, что падают они только на CMP 1.12, на 1.11.0 тесты зелёные.

## Решение

- **Тест.** Хелпер `sendAndAwaitQuestion` ждёт `pendingChoice` по реальным часам. Asserts не тронуты.
- **VM.** В 4 местах `ChatViewModel` значения вычисляются до записи, запись идёт через `update {}`. Red-репро: `ChatStateSnapshotRaceTest`. Suspend-фейк форматтера пишет новый баланс посреди построения вопроса.
- **Механические поломки бампа:**
  - adaptive-navigation3 1.3.0: вызов теперь `NavDisplay(sceneStrategies = listOf(...))`;
  - sqlite-web 2.7.1 отдаёт `kotlinx-browser` только в runtime. Без явной зависимости в wasmJsMain `core/common/api` компиляция падает с `Cannot access class org.w3c.dom.Worker`;
  - coil 3.6.3, Compose 1.12 и core 1.19 через `checkDebugAarMetadata` требуют compileSdk 37 (источник: AGP-таск, падение в сборке этой задачи). targetSdk остаётся 36;
  - в play-services-auth 22.0.0 нет `GoogleSignIn`, поэтому держим 21.6.0 (запись в backlog).

## Почему именно так

До 1.12 ресурс читался в контексте вызывающего, и между снимком state и записью не успевал выполниться ни один кадр Main. Теперь `AsyncCache` запускает чтение в собственном scope на `Dispatchers.Default` (источник: `ResourceCaches.kt`, тег v1.12.2; исходник читал специалист в этой задаче, главный его не открывал). В образовавшееся окно коллектор `getUserDataFlow` может записать новый `creditBalance`, и устаревший снимок его затрёт. `update {}` перечитывает state атомарно, а его лямбда не `suspend`, поэтому значения нужно вычислить заранее.

## Связанные файлы

- `feature/aichat/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/aichat/impl/presentation/ChatViewModel.kt`
- `feature/aichat/impl/src/androidHostTest/kotlin/com/antonchuraev/homesearchchecklist/feature/aichat/impl/presentation/ChatPreviewContentTest.kt`
- `feature/aichat/impl/src/commonTest/kotlin/com/antonchuraev/homesearchchecklist/feature/aichat/impl/presentation/ChatStateSnapshotRaceTest.kt`
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/App.kt`
- `core/common/api/build.gradle.kts`
- `gradle/libs.versions.toml`
- [Документ задачи](../../archive/deps-update-revenuecat-3-15-2026-10-23.md) <!-- docs-leak-scan: reviewed — имя файла с версией SDK RevenueCat, не выручка -->
- [Backlog: play-services-auth 22](../../backlog/2026-10-23-play-services-auth-22-legacy-signin.md)
