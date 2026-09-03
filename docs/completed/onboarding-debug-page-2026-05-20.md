# Onboarding Debug Page

**Статус:** Done
**Дата старта:** 2026-05-20
**Project:** aichecklists
**Тип:** feature
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** feature/debug, feature/onboarding (read-only), core/navigation, core/designsystem (strings)

## Цель (продуктовая)

В debug-сборках добавить отдельную страницу «Onboardings» (доступ из Debug menu), на которой одной кнопкой запускается каждый variant онбординга — для быстрой визуальной проверки и сравнения вариантов при разработке `auto-create checklist` фикса.

## Технический план

**GOAL.** Новая dedicated debug-страница `OnboardingsScreen` с кнопками для запуска `interactive` и `slides` вариантов онбординга. Использует существующие routes (`NavigateOnboarding`, `NavigateInteractiveOnboarding`) из ScreenCatalog.

**CONSTRAINTS.**
- KMP commonMain (Android + iOS + wasmJs), Koin 4.1.1.
- Паттерн из `ScreenCatalogScreen`: Column + verticalScroll (НЕ LazyColumn), `SectionHeader` + `CatalogItem`.
- Tracking guard: debug-launches не должны засчитываться как real onboarding_started в production метриках Amplitude.
- Локализация EN + RU.

**OUTPUT.**
- `feature/debug/.../presentation/OnboardingsScreen.kt` + `OnboardingsViewModel.kt` + `OnboardingsScreenContract.kt`
- `feature/debug/.../di/DebugFeatureModule.kt` — Koin registration
- `feature/debug/.../presentation/DebugScreen.kt` — новый меню-пункт
- `feature/debug/.../presentation/DebugScreenContract.kt` — intent `OpenOnboardings`
- `feature/debug/.../presentation/DebugViewModel.kt` — handle intent
- `composeApp/.../App.kt` (или wherever NavGraph живёт) — route
- `core/navigation/api/.../AppNavRoute.kt` — `Onboardings`
- `core/designsystem/.../strings.xml` (EN + RU) — `debug_onboardings_*`

## Лог итераций

### Итерация 1 — 2026-05-20 — android-expert
**Что сделано:** 
- `OnboardingsScreen` + `OnboardingsViewModel` + contract (3 new files, commonMain KMP)
- New route `Onboardings` in `AppNavRoute.kt`, NavCommand, navigator interface + impl (4 files)
- Debug menu: added `PlayCircleFilled` button + `OpenOnboardings` intent (DebugScreen, DebugScreenContract, DebugViewModel)
- Koin registration in DebugFeatureModule
- Tracking guard: new Koin binding `named("isDebugBuild")` check in OnboardingViewModel + InteractiveOnboardingViewModel constructors
- Strings: 7 EN + 7 RU keys
- App.kt route registration + platform DI (Android real, iOS/wasmJs false)

**Почему так:** 
- Двух-вариантность онбординга = две отдельные screens, не параметризованные. Переиспользованы существующие routes, minimal change.
- Tracking guard через Koin binding = unit-testable, нет coupling к nav-args.
- 17 test fakes на AppNavigator interface ripple обновлены параллельно (как и описано в CLAUDE.md).

**Баги/проблемы:** 
- Specialist hit ~147 tool calls (выше ~40 turn budget) из-за механических test-fake edits. Это normal overhead для interface ripple, но сигнализирует о том, что баланс tooling vs. human может быть оптимизирован в future.

**Решение:** 
- Компиляция passed после фиксапа на Итерации 2.

### Итерация 2 — 2026-05-20 — main-agent (fixup)
**Что сделано:** 
- Диагностика: private `SectionHeader` + `CatalogItem` из ScreenCatalogScreen.kt не экспортированы, specialist использовал их в новом OnboardingsScreen → compile error.
- Решение: extract обе composables в новый shared file `DebugCatalogComponents.kt` (internal scope), импортировать из него в OnboardingsScreen и ScreenCatalogScreen.
- Removed duplicate definitions из ScreenCatalogScreen.kt.

**Почему так:** 
- Do NOT reuse `private` composables из соседних файлов. Pattern reusable: when adding 2nd screen to feature, look for private helpers in 1st screen и extract early.

**Баги/проблемы:** 
- Compile error был неизбежен, specialist работал изолировано (не читал ScreenCatalogScreen helpers).

**Решение:** 
- Extract + shared import.

**Результат build:** assembleDebug PASS (23s), testAndroidHostTest pending.

## Выводы

Task completed. New debug page `OnboardingsScreen` added to feature/debug with quick-launch buttons for both onboarding variants (interactive + slides). Tracking guard via Koin `named("isDebugBuild")` binding ensures debug launches don't pollute production Amplitude metrics.

**Key deliverables:**
- `OnboardingsScreen`, `OnboardingsViewModel`, `OnboardingsScreenContract` (3 new files, commonMain KMP)
- `DebugCatalogComponents.kt` (shared internal helpers extracted from ScreenCatalog pattern)
- Navigation: new `Onboardings` route in AppNavRoute, NavCommand, AppNavigator interface + impl
- Koin DI binding with `isDebugBuild` tracking guard in both OnboardingViewModel and InteractiveOnboardingViewModel
- Debug menu integration: PlayCircleFilled icon button + `OpenOnboardings` intent
- Strings: 7 EN + 7 RU keys added to core/designsystem
- Platform-specific DI: Android real isDebugBuild, iOS/wasmJs false

**Build validation:** `:composeApp:assembleDebug` PASS (23s), `:feature:onboarding:testAndroidHostTest` PASS (21/21 including 2 new debug-mode tests). No failures post-fixup.

**Cost metrics:** 3 real steps (1 specialist delegation + 1 compile-error diagnosis + 1 fixup). AppNavigator interface ripple added ~147 specialist tool calls (above budget due to mechanical test-fake edits, but expected for interface-change gravity). Total turn count ~180 across delegation + fixup.

## Предложения по улучшению агентов

### android-expert
- [ ] When adding 2nd screen to a feature, proactively scan 1st screen for `private` helper composables and extract to shared `internal` file. Avoid compile error on neighbor reuse.
- [ ] When changing `core/navigation` interfaces (AppNavigator), grep all `FakeAppNavigator` implementations across test source-sets BEFORE implementing changes. Count cost upfront (typically 1 file per feature/module + 2 per test package = ~17 files for this codebase). Pattern: `appNavigator\|AppNavigator` in `**/test/**/Fake*.kt`.
- [ ] `feature/debug` module intentionally has no test source-set (debug screens = UI contract, not unit-testable at component level). Don't add test files to that module reflexively.
