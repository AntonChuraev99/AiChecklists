---
status: deferred
blocking_reason: pre-existing instrumented infra broken — Compose never idle (timeout) + androidTest didn't compile
resume_trigger: User says "чиним instrumented / androidTest не проходит / Compose UI тесты таймаут / compose idle"
keywords: [instrumented, connectedAndroidTest, ComposeNotIdleException, idling resource, androidTest, FolderFlowTest, compose-espresso, no compose hierarchies]
---

# Instrumented test infra broken — Compose never idle

`androidApp/src/androidTest/.../FolderFlowTest.kt` (7 Compose-UI тестов фичи папок) написан и компилируется, НО instrumented-окружение проекта нерабочее (pre-existing — androidTest давно не гонялся, был сломан компиляцией). Это НЕ про папки.

## Симптомы (прогон 2026-06-13, Pixel 9, ANDROID_SERIAL=один)
- 5/7: `ComposeNotIdleException: Idling resource timed out — compose being busy` + `IdlingResourceTimeoutException: Wait for [Compose-Espresso link] to become idle timed out`.
- 1: `IllegalStateException: No compose hierarchies found in the app` (Activity/setContent не стартует в тесте?).
- 2 прошли (create, rename) — значит сам тест-код и semantics в основном верны; падает синхронизация idle.
Любой Compose-UI тест таймаутится → Compose в приложении **никогда не становится idle**.

## Уже сделано (enablers, в working tree, не закоммичено отдельно от фикса)
- `androidApp/.../GistiAndroidApplication.kt`: `class` → `open` (был final; `TestApplication extends` его → НЕ компилировался ВЕСЬ androidTest). Без этого instrumented не собирается вовсе.
- Wi-Fi mDNS дубль одного устройства → `connectedAndroidTest` ставит APK на «обе» записи → `INSTALL_FAILED_DUPLICATE_PACKAGE`. Обход: `ANDROID_SERIAL=<один serial>` или эмулятор/USB (один transport).

## Resume (фикс инфры)
1. Найти, что держит Compose busy (idling resource never idle): бесконечная анимация (indeterminate `CircularProgressIndicator`/`LinearProgressIndicator`, `rememberInfiniteTransition`, shimmer, recording-pulse), постоянная рекомпозиция, `LaunchedEffect`-loop — на пути Splash→Main→Detail.
2. Прогнать существующий `EditChecklistFlowTest` — если тоже compose-idle timeout, подтверждено что инфра (не папки).
3. Фикс-варианты: `composeTestRule.mainClock.autoAdvance=false` + ручной advance + `waitUntil`; отключить бесконечную анимацию в test build; убрать постоянный idling источник.
4. «No compose hierarchies» — проверить, что Compose Activity реально стартует под `TestApplication`/`TestRunner`.
5. После фикса: `ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.antonchuraev.aichecklists.FolderFlowTest`.

## Покрытие папок без instrumented (сейчас)
unit ~88 (дерево/link/folder-actions/AI-parser/CF-sanitizer) + Roborazzi screenshot 11 (FolderCard/sheets/диалоги) + ручная device-проверка (rename/delete подтверждены пользователем).
