# Blur Under Chat Dock — Haze 1.7.2 Downgrade + MainScreen Placement

**Статус:** Done
**Дата старта:** 2026-06-07
**Start SHA:** 19e42873c4acc6872cc664832ba2d6de095d4be9
**Project:** checklists
**Тип:** bugfix + feature
**Сложность:** Complex
**Impact:** Medium
**Затронутые модули:** core/designsystem, feature/home, gradle (version catalog), composeApp/commonMain (screenshot tests)

## Цель (продуктовая)

Добиться РЕАЛЬНО ВИДИМОГО blur контента под AI чат-доком на MainScreen (главный экран) и заодно починить тот же блюр на ChecklistDetailScreen. Корень бага: проект сидит на нестабильной haze 2.0.0-alpha02 (модуль haze-blur, API `blurEffect{}`/`HazeColorEffect`), где backdrop-blur не рендерится. Рабочий эталон — соседний проект swapfaceandroid на стабильной haze 1.7.2 (API `HazeStyle`/`HazeTint`, `hazeEffect(state, style)`).

## Технический план

1. **Откат версии Haze:** `gradle/libs.versions.toml` `haze = "2.0.0-alpha02"` → `"1.7.2"`; убрать алиас `haze-blur` и модуль из catalog; в `core/designsystem/build.gradle.kts` удалить `implementation(libs.haze.blur)`.

2. **Переписать GistiChatDock (core/designsystem/.../gisti/GistiChatDock.kt):** миграция с 2.0 API (`blurEffect{ colorEffects = HazeColorEffect.tint() }`) на 1.7.2 API: `hazeEffect(state, style = HazeStyle(blurRadius = ..., backgroundColor = ..., tint = HazeTint(color = ..., alpha = ...)))`. Тинт должен быть достаточно прозрачным, чтобы blur был ВИДЕН (не прятался под сплошным Surface цветом).

3. **Перенести chat-dock на MainScreen:** 
   - из `Scaffold.bottomBar` в floating overlay внутри контентного `Box` главного экрана
   - `HazeSource` на обёртке `MainScreenContent` (контент + список/сетка)
   - измеренная высота дока → `bottomPadding` для `LazyColumn` / `LazyVerticalGrid` внутри `MainScreenContent`
   - Single-owner insets pattern (без дублирования padding'ов)
   - IME-hide как на ChecklistDetailScreen (скрыть dock при клавиатуре)

4. **Убрать временный Haze probe из ChecklistDetailScreen.kt** (~строки 936–957, TEMP-marked комментарии).

5. **Адаптировать screenshot-тест (ChecklistDockGlassScreenshotTest):**
   - переписать под 1.7.2 API
   - добавить покрытие MainScreen chat-dock (новый тест или раздел)
   - прогнать `recordRoborazziAndroidHostTest` + проверить golden PNG на наличие размытых полосок контента (детектор «блюр реально рендерится»)

6. **Валидация:**
   - `:androidApp:assembleDebug` PASS
   - `:composeApp:compileKotlinWasmJs` PASS
   - `:composeApp:recordRoborazziAndroidHostTest` PASS (golden сгенерирован с видимым blur-эффектом)
   - установка APK на устройство + ручная проверка: контент под доком должен быть размыт и виден

## Лог итераций

### Итерация 1 — 2026-06-07 — android-expert + main-agent

**Что сделано:**
- Откат версии (главный): `gradle/libs.versions.toml` `haze = "2.0.0-alpha02"` → `"1.7.2"`; удалён алиас `haze-blur`; из `core/designsystem/build.gradle.kts` удалён `implementation(libs.haze.blur)`.
- Миграция GistiChatDock.kt (@android-expert): 2.0 API `hazeEffect(state){ blurEffect{ colorEffects = HazeColorEffect.tint() } }` → 1.7.2 API `hazeEffect(state, style = HazeStyle(blurRadius=32.dp, backgroundColor=surface, tint=HazeTint(dockTint, alpha=0.4f)))`. Порядок `clip → hazeEffect` (по паттерну swapfaceandroid BottomNavigation.kt). Удалены импорты dev.chrisbanes.haze.blur.*, ExperimentalHazeApi.
- Оптимизация структуры: извлечён generic `GistiGlassChatDock(hazeState, chipsContent?, pillContent)` — единственное место blur-логики; ChecklistDetailChatDock стал тонкой обёрткой (контракт не изменился).
- MainScreen.kt переплан: чат-док перенесён из непрозрачного `Scaffold.bottomBar` в floating overlay внутри content-Box главного экрана; `HazeSource` на обёртке `MainScreenContent` (контент + список/сетка); измеренная высота дока (onSizeChanged) → `contentBottomPadding` для LazyColumn/LazyVerticalGrid; single-owner navbar insets (без дублирования); pill-контент (AskGistiBar + гости-чипсы) в стеклянной обвязке.
- MainScreenContent.kt: +параметр `contentBottomPadding: Dp = SpacingXxl`, проброшен в `bottom` contentPadding обоих путей (LazyColumn + LazyVerticalGrid). `hazeSource` на контейнере, не на items (обход известного Haze #865 bug).
- ChecklistDetailScreen.kt: удалён TEMP на-device Haze probe (~строки 936–957) + осиротевшие импорты (blurEffect, hazeEffect, дублировавшие Color/fillMaxHeight/height).
- Тест ChecklistDockGlassScreenshotTest.kt: вынесен common HighContrastBackdrop; добавлен второй @Test `mainGlassDock_backdropBlur()` (детектор, что blur под main-доком рендерится).

**Почему так:**
- haze 2.0.0-alpha02 — нестабильная версия (backdrop sampling не рендерит); 1.7.2 — booster-verified в production (swapfaceandroid). Alpha версии запрещены для core-паттернов (ненадёжность будет найдена после коммита).
- Перенос дока в overlay + HazeSource на контент решает две проблемы: (1) визуальная интеграция с контентом (не вытеснение из scaffold, а плавающий слой); (2) hazeEffect автоматически blur-энит контент ниже (в z-order) — не нужна ручная clip-логика.
- Прозрачность HazeTint (alpha=0.4f) критична — непрозрачный тинт (alpha=1) маскирует blur (выглядит как сплошной Surface).
- Screenshot-тест с golden PNG — детектор: можно быстро проверить наличие размытых полосок (не полагаясь на ручную проверку).

**Баги/проблемы:** не обнаружены на этапе review кода; validation pending (сборка + устройство).

**Решение:** разработан по плану; шаги 1–5 завершены. На шаге 6 (валидация) — `:androidApp:assembleDebug` + `:composeApp:compileKotlinWasmJs` + `recordRoborazziAndroidHostTest` + установка APK.

**Файлы изменены (7):**
- `gradle/libs.versions.toml` (откат haze)
- `core/designsystem/build.gradle.kts` (remove haze-blur)
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/components/gisti/GistiChatDock.kt` (API migration)
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/MainScreen.kt` (dock → overlay)
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/MainScreenContent.kt` (HazeSource + padding param)
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/detail/ChecklistDetailScreen.kt` (remove TEMP probe)
- `core/designsystem/src/androidHostTest/kotlin/.../gisti/ChecklistDockGlassScreenshotTest.kt` (add mainGlassDock test)

## Выводы

**Задача завершена.** Проект откачен с нестабильной haze 2.0.0-alpha02 на стабильную 1.7.2; blur под чат-доком теперь реально рендерится и видим. 

**Ключевые находки:**

1. **Alpha версии UI-либ опасны:** haze 2.0 имела версию без рендера backdrop-blur (только плоский tint) — обнаружено постфактум после коммита. Проверка с соседним прод-проектом (swapfaceandroid) дала рабочий эталон за минуты.

2. **Видимость blur зависит от прозрачности tint:** HazeTint с alpha=1.0 маскирует размытие под сплошным Surface → выглядит как плашка. Переход на alpha=0.4f + белый фон (#FBFAF8) — blur становится явно видимым при скролле контента.

3. **API 1.7.2 vs 2.0:**
   - 2.0: `hazeEffect { blurEffect { colorEffects = listOf(HazeColorEffect.tint()) } }`
   - 1.7.2: `hazeEffect(state, style = HazeStyle(blurRadius, backgroundColor, tint = HazeTint(c, alpha)))`
   - Общие: `rememberHazeState()`, `hazeSource()`, `hazeEffect()` (переименование произошло в 1.2.0)

4. **架构 hazeSource/hazeEffect — siblings, не вложенность:** hazeSource на LazyColumn контейнере, hazeEffect на GistiGlassChatDock (разные слои z-order). Self-sampling (hazeEffect на контейнере, пытающемся размыть себя) = no-op.

5. **Roborazzi детектор `backdropBlur` бьёт высокий контраст (red/blue) под доком:** полоски размыты → blur ок, резкие → баг. Заменяет субъективную проверку глазами.

6. **AGP 9 KMP:** core/* (designsystem, navigation) не имеют taska compileDebugKotlin — собирать через `:androidApp:compileDebugKotlin` (Android) + `:composeApp:compileKotlinWasmJs` (wasmJs), которые тянут модули транзитивно.

**Validation passed:**
- `:androidApp:assembleDebug` PASS (14s)
- `:composeApp:compileKotlinWasmJs` PASS (46s)
- `:composeApp:recordRoborazziAndroidHostTest` PASS (золото сгенерировано, 2 кейса с видимым blur-эффектом)
- Установка APK на Pixel_9: dock-overlay + glassmorphism blur видим под контентом ✓

**Отложено (pending):** iOS-компиляция не проверена (haze 1.7.2 поддерживает iOS, dock в commonMain, iOS не релизится — низкий риск).

## Предложения по улучшению агентов

### best-practices-scout
- [ ] При выборе версии UI-библиотеки явно флагить alpha/beta как риск: «проект на alpha-версии, не стабилизирована, баг может быть найден только постфактум»
- [ ] Рекомендовать cross-check с соседними prod-проектами того же стека (для KMP — swapfaceandroid, для Next.js — внутренние примеры) как fastest way to validate

### android-expert
- [ ] Добавить в раздел Design System: hazeSource/hazeEffect siblings pattern (не вложенность), важность прозрачности HazeTint (alpha <1 для видимости blur)
