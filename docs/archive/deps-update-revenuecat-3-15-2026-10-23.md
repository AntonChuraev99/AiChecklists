# Обновление всех зависимостей + RevenueCat 3.0.0 → 3.15.0

**Статус:** Done
**Дата старта:** 2026-10-23
**Start SHA:** 03975876a88a7ea46f54401d1ca3844b8b45a672
**Project:** Checklists
**Тип:** refactor
**Затронутые модули:** gradle/libs.versions.toml, gradle wrapper, androidApp, feature/paywall, composeApp (`App.kt`, `Analytics.kt`, wasmJs `init.js.template`), core/common/api, feature/aichat/impl (`ChatViewModel`, тесты), sqlite-wasm-worker, kotlin-js-store, docs/backlog, docs/solutions

## Цель (продуктовая)

Play Console прислал предупреждение по SDK `com.revenuecat.purchases:purchases-kmp-core` 3.0.0, затронуты сборки vc60 (1.17.10) и vc88 (1.22.0). В 3.0.0–3.9.0 есть баг: пользователя может разлогинить при обновлении с версии ниже 3.0.0, и тогда ему приходится восстанавливать покупки. Начиная с 3.10.0 SDK сам восстанавливает сессию. Задача — поднять RevenueCat до ≥ 3.10.0 и одним PR обновить все зависимости приложения до последних стабильных версий (решение владельца: один PR, а не два).

## Технический план

1. Скауты: пины проекта (knowledge-scout), последние stable-версии Google-стека и JetBrains/сторонних библиотек (два best-practices-scout).
2. Целевые версии (таблица — в брифе специалисту):
   - сборочная цепочка, выбранная по документированной матрице Kotlin: Gradle 9.7.x + AGP 9.3.1 + Kotlin 2.4.21 + KSP 2.3.12; AGP 9.4.1 не берём — он выше проверенного диапазона Kotlin 2.4.21;
   - CMP 1.12.2;
   - RevenueCat 3.15.0; billing остаётся 8.3.0, потому что purchases-android 10.28.1 сам тянет 8.3.0;
   - Firebase BoM 35, Firebase JS SDK 13.1.0;
   - Haze остаётся на 1.x (правило designsystem).
3. Веб-строки версий в `init.js.template` правит главный. Gradle и починку компиляции делает android-platform-expert.
4. Проверка: `:androidApp:assembleDebug`, `:composeApp:wasmJsBrowserDistribution`, `testAndroidHostTest`, Roborazzi verify, release classpath, веб smoke на :9090.

## Лог итераций

### Итерация 1 — 2026-10-23 — главный: Firebase JS SDK 12.11.0 → 13.1.0, Amplitude Browser SDK 2.44.1 → 2.48.1 в `init.js.template` → применено

### Итерация 2 — 2026-10-23 — android-platform-expert: версии из плана применены, сборка починена → debug/release/web зелёные, 9 тестов `ChatPreviewContentTest` красные

- **Отступления от плана.** `playServicesAuth` придержан на 21.6.0: в 22.0.0 нет `GoogleSignIn`, а на нём держится legacy-fallback в `AndroidGoogleAuthProvider.kt`. `compileSdk` поднят 36 → 37: этого требует `checkDebugAarMetadata` (AGP-таск; источник: падение сборки в этой задаче) у coil 3.6.3, Compose 1.12 и core 1.19. `targetSdk` остался 36.
- **Механические правки кода.**
  - `App.kt`: в adaptive-navigation3 1.3.0 `NavDisplay` принимает `sceneStrategies = listOf(...)` вместо одиночного параметра.
  - `core/common/api`: в wasmJsMain добавлен `kotlinx-browser`, потому что sqlite-web 2.7.1 отдаёт `org.w3c.dom.Worker` только в runtime-варианте.
- **Красное.** Бисект специалиста: 9 тестов `ChatPreviewContentTest` проходят на CMP 1.11.0 и падают на 1.12.0. Ещё 21 голден `feature/analyze` (источник: `verifyRoborazziAndroidHostTest` специалиста) и `compileTestKotlinWasmJs` (Gradle-таск KMP; источник: тот же прогон) в трёх модулях специалист считает дефектами, которые были до бампа.
- **Сверка главного.**
  - Голдены `feature/analyze/src/androidHostTest/roborazzi` последний раз менялись в #57, это раньше смены палитры в #61 и #63 (источник: `git log`).
  - `compileTestKotlinWasmJs` (Gradle-таск KMP; источник: прогон специалиста) падает на JVM-only `System.currentTimeMillis()` и на `@Volatile` без импорта в commonTest. В `composeApp` datastore приходит только через `core.datastore.impl`. От версий библиотек всё это не зависит.
  - CI тестов не запускает: в `.github/workflows` нет ни `testAndroidHostTest`, ни `verifyRoborazzi`, ни `compileTestKotlinWasmJs`.

### Итерация 3 — 2026-10-23 — android-platform-expert: KDoc `Analytics.initialize` переписан под Amplitude 1.36.0 → по исходнику foreground приходит через `ProcessLifecycleOwner`, поэтому инстанс, созданный позже, тоже открывает сессию (вживую не проверено); код не менялся

### Итерация 4 — 2026-10-23 — feature-expert: корень падения `ChatPreviewContentTest` на CMP 1.12 → проблема только в тестовой среде, ожидание исправлено в тесте, 608/608

- **Корень.** В CMP 1.12 `AsyncCache.getOrLoad` (`components/resources/.../ResourceCaches.kt`, тег v1.12.2) запускает чтение строки через `cacheScope.async` в собственном `CoroutineScope(SupervisorJob())`, то есть на `Dispatchers.Default`. В 1.11 то же чтение шло внутри `coroutineScope` вызывающего, и под `UnconfinedTestDispatcher` `getString` завершался синхронно. Теперь VM честно приостанавливается, а `pendingChoice` появляется после возврата `send()`. Исключения и зависания нет: временный лог показал `pendingChoice=null, isProcessing=true`.
- **Правка теста.** Добавлен хелпер `sendAndAwaitQuestion`, он ждёт `screenState.first { it.pendingChoice != null }` по реальным часам. Asserts и ожидаемые значения не тронуты.
- **Побочная находка.** Задержка открыла гонку в продакшн-коде. `_screenState.value = _screenState.value.copy(...)` берёт снимок state ДО suspend-вызовов в аргументах. Если в это окно коллектор `getUserDataFlow` запишет новый `creditBalance`, устаревший снимок его затрёт. До 1.12 окна не было. Чинится в итерации 5.

### Итерация 5 — 2026-10-23 — feature-expert: в 4 местах `ChatViewModel` (`showWriteChoice`, `showWhichListChoice`, `showMoveTargets`, `Options` агента) suspend-значения вычисляются до записи, запись идёт через `_screenState.update { it.copy(...) }` → 608/608, без `getString` внутри `.value.copy(` в других VM

### Итерация 6 — 2026-10-23 — главный: веб-проверка на :9090 (Playwright) → загрузка, запись в базу, перезагрузка и вложение работают

Проверено на `wasmJsBrowserDevelopmentRun`:
- Все 7 модулей `firebasejs/13.1.0` и `@amplitude/analytics-browser@2.48.1` вернули 200, события в `api2.amplitude.com/2/httpapi` уходят.
- Задача во «Входящих» пережила перезагрузку страницы, значит, OPFS + Room3 + sqlite-wasm 3.54.1 работают.
- PNG через «Вложения» отрисовался миниатюрой, значит, работают Coil 3.6.3 и fetcher `opfs://`.

В консоли две ошибки, обе к бампу не относятся:
- `Missing or insufficient permissions` у credit-листенера. Анонимному веб-пользователю это положено: `firestore.rules:17` требует `request.auth != null`. <!-- docs-leak-scan: reviewed — номер строки файла правил, не метрика -->
- `Accessing memory via wasmExports is deprecated` — это предупреждение загрузчика Kotlin 2.4 на обращения `compose.resources` и skiko (источник: консоль браузера и сгенерированный `build/wasm/packages/AIChecklists-composeApp/kotlin/AIChecklists-composeApp.mjs`, не в git). `console.error` в `init.js` не перехватывается, поэтому наружу оно не уходит.

Вопрос-подтверждение в AI-чате на вебе не проверить: до входа через Google кредитов на вебе нет.

### Итерация 7 — 2026-10-23 — android-platform-expert (находка ревью 2.3b): литералы uiautomator, orchestrator, mockk ×2 и billing перенесены в `libs.versions.toml`, `kotlinx-browser` переведён на `version.ref` → сборка зелёная, в release `billing:8.3.0`, `purchases-kmp-core:3.15.0`

### Итерация 8 — 2026-10-23 — feature-expert (находка ревью 2.3b): red-репро гонки `ChatStateSnapshotRaceTest` → на старом паттерне в `showWriteChoice` падает (`expected:<41> but was:<100>`), на фиксе 609/609; компиляция под wasmJs зелёная

Окно открывается через фейковый `ChatDateFormatter`: он кладёт новый баланс в `HarnessUserDataRepository` и делает `yield()`. Тест покрывает только `showWriteChoice`. Остальные три места закрывает тот же паттерн, отдельного теста у них нет.

## Выводы

- **Что сделано.**
  - RevenueCat 3.0.0 → 3.15.0: в release-классхпасе `purchases-kmp-core:3.15.0` → `purchases:10.28.1` → `billing:8.3.0` (источник: `:androidApp:dependencies --configuration releaseRuntimeClasspath`).
  - Остальные зависимости подняты по таблице целей.
  - На прежних версиях остались: `playServicesAuth` 21.6.0 (запись в backlog), `navigationCompose` 2.9.2 (2.10 только rc), billing 8.3.0 (держим на транзитивной версии RevenueCat), Haze 1.x.
- **Что выяснили.**
  - CMP 1.12 меняет поведение без ошибки компиляции: `getString` действительно приостанавливается. Синхронно читающий VM-тест краснеет, а запись `_screenState.value.copy(suspendCall())` превращается в живую гонку.
  - Разбор и паттерн записаны в [solution-док](../solutions/build-system/kotlin-2-4-cmp-1-12-upgrade-2026-10-23.md).
- **Было красным до задачи и осталось:**
  - 21 голден `feature/analyze`, не перезаписанный после смены палитры;
  - `compileTestKotlinWasmJs` в `composeApp`, `feature/home`, `feature/splash`.
  - CI тестов не запускает.
- **Не проверено:**
  - iOS: на Windows его не собрать;
  - на Android-устройстве: вход через Google, покупка на пейволе, вопрос-подтверждение в AI-чате, пуш, синхронизация на Firestore 27, виджет на WorkManager 2.12;
  - метрика сессий Amplitude после 1.36: уход в фон фиксируется с задержкой около 700 мс (приблизительно; источник: debounce `ProcessLifecycleOwner` по исходнику Amplitude-Kotlin 1.36.0, читал специалист). <!-- docs-leak-scan: reviewed — задержка debounce в SDK, не продуктовая метрика -->
- **Предупреждение Play** снимется только после выпуска Android-релиза с RevenueCat ≥ 3.10.0, который заменит vc60 и vc88 во всех треках. Бамп версии и AAB — отдельный шаг.
- **Вне скоупа:** `mcp-server/`, `firebase-functions/`, `e2e/`, `playwright/`, расширение `firestore-revenuecat-purchases` в `firebase.json`.

## Предложения по улучшению агентов

- **`~/.claude/skills/gradle-deps-update/references/compatibility-matrix.md`: добавить секцию о поведенческих изменениях, при которых компиляция остаётся зелёной.** Первая запись: CMP 1.12 — `getString` приостанавливается на `Dispatchers.Default`. Проверки:
  - Robolectric-тесты, читающие state синхронно после интента;
  - grep по `.value = .*\.value\.copy(` с suspend-вызовом в аргументах.

  Прецедент — эта задача: сборка зелёная, 9 тестов упали, а продакшн-гонку нашли только при разборе. Скилл проверяет одну компиляцию.
- **`~/.claude/skills/task-gate/SKILL.md` § 2.2: в worktree `ast-index changed --base <sha>` возвращает 0 файлов (источник: вывод команды в этом gate),** потому что хук `~/.claude/hooks/ast-index-worktree.js` (не проверено — файл харнесса вне репозитория) берёт индекс основного checkout'а. Добавить fallback: `git diff --name-only <BASE_SHA>` плюс `Grep` по изменённым символам. Прецедент — этот gate, `Changed files against 03975876… (0)`.
