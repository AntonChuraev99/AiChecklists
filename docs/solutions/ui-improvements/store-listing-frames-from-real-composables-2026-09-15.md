---
title: "Google Play listing frames rendered from real composables"
summary: "Store screenshots drifted to v1 because they were hand-drawn mockups; a Roborazzi test now composes all 8 frames and the feature graphic from real screens on fake state, alpha-free."
date: 2026-09-15
type: pattern
modules: [composeApp, feature/analyze, feature/create, feature/home]
keywords: [image, store-screenshots, google-play, roborazzi, robolectric, feature-graphic, alpha-channel, stateless-seam, StoreListingFramesScreenshotTest]
project: store-screenshots-v2
---

# Google Play listing frames rendered from real composables

**Суть:** скриншоты стора генерирует `composeApp/src/androidHostTest/kotlin/com/antonchuraev/homesearchchecklist/store/StoreListingFramesScreenshotTest.kt` командой `./gradlew :composeApp:testAndroidHostTest --tests "*StoreListingFramesScreenshotTest*" --rerun`. Готовый RGB-набор лежит в `composeApp/build/store-listing/final/`. Нужен новый кадр — добавить сырой проход `a*` на реальном stateless-экране и проход `b*` с компоновкой. Рисовать интерфейс руками не нужно.

## Проблема / Контекст

Прошлый набор собирался из рисованных мокапов в debug-экране (документ `store-screenshots-in-app-mockup-pipeline-2026-05-09` в `docs/solutions/ui-improvements/` основного checkout (не проверено — файл не отслеживается git и в worktree отсутствует)). Это копия интерфейса, и её никто не пересобирает при редизайне. После перехода на v2-шелл листинг продолжал показывать гамбургер v1, шесть карточек Analyze вместо пилюль и старое число шаблонов. Кадр с пэйволом нёс цены, «free trial» и скидку, а политика графических ассетов Play это запрещает. Текущие файлы вдобавок были RGBA, хотя Play требует изображения без альфа-канала.

## Решение

Один Robolectric-тест в три прохода, порядок которых задаёт `@FixMethodOrder(MethodSorters.NAME_ASCENDING)`, потому что каждый проход читает файлы предыдущего:

- **`a*` — сырой экран.** Реальный composable 1.21.0 на фейковом состоянии, qualifiers `w360dp-h660dp-port-xxxhdpi`. Окно своё, поэтому экран, читающий `LocalConfiguration.screenWidthDp`, считает себя телефоном (Compact). Для веб-части кадра 7 окно `w1280dp-h900dp-land-xhdpi`: wasmJs — тот же Compose UI, так что desktop-ширина на JVM честно показывает веб.
- **`b*` — кадр стора.** `w720dp-h1280dp-port-xxhdpi`: градиент, заголовок, корпус устройства, сырой снимок внутри экрана.
- **`c*` — feature graphic.** `w512dp-h250dp-land-xxxhdpi`.
- **Экспорт.** Roborazzi пишет ARGB, поэтому `exportFlattened` перерисовывает снимок в `BufferedImage.TYPE_INT_RGB` и проверяет `check(!ImageIO.read(out).colorModel.hasAlpha())`.

Marketing-copy (заголовки, плашка «filled by AI», рамка браузера, «Synced») захардкожен в тестовом source set. Это компоновка кадра, а не UI приложения, в APK она не попадает, поэтому правило `strings.xml` на неё не распространяется.

**Seams в прод-коде.** Из `composeApp` не видны `internal`/`private` Content-функции фич, поэтому добавлены stateless-входы. Поведение не меняется: прод-путь вызывает их же.

- `AnalyzeScreen` → публичный `AnalyzeScreenContent(screenState, onIntent)`; VM-вариант передаёт `viewModel::sendIntent`.
- `TemplatesScreen` (VM) → stateless перегрузка `TemplatesScreen(state, onIntent, useProjectTitle, onCreateWithAi)`; аналитика и подписка на состояние остались в VM-варианте.
- `ChecklistDetailContent` → публичная перегрузка без типов док-панели, делегирует в единственную реализацию с `chatDockContent = null`. Необязательный `weeklyTodayWeekday` (по умолчанию `null` = часы устройства) делает недельный кадр детерминированным.

## Почему именно так

- **Реальные экраны вместо рисованных.** Кадр совпадает с приложением по построению: правка экрана меняет снимок при следующем прогоне, и расхождение видно на картинке, а не через месяцы в листинге.
- **Перегрузка, а не копия разметки.** Stateless-вход, который вызывает и прод, и тест, — единственный вариант, при котором тест показывает то, что увидит пользователь.
- **Скрининг JVM, а не устройство.** `adb screencap` зависит от данных на устройстве и от ручных тапов (правило проекта запрещает водить UI устройства без просьбы). Фейковое состояние в тесте воспроизводимо.

## Связанные файлы

- `composeApp/src/androidHostTest/kotlin/com/antonchuraev/homesearchchecklist/store/StoreListingFramesScreenshotTest.kt`
- `feature/analyze/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/analyze/presentation/AnalyzeScreen.kt`
- `feature/create/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/create/presentation/templates/TemplatesScreen.kt`
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/detail/ChecklistDetailScreen.kt`
- `docs/store-screenshots/store-listing-en.md` — источник правды текста листинга
- `docs/archive/play-store-screenshots-v2-2026-09-15.md` — документ задачи
