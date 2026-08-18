---
status: resolved
blocking_reason: pre-existing-nav3-migration-debt
resume_trigger: "User says «почини тесты feature/home» / next feature/home logic change / CI host-test gate added"
keywords: [feature-home, test-fakes, FakeAppNavigator, NavCommand, Navigation3, NavBackStack, testAndroidHostTest, MainScreenViewModelTest]
---

# feature/home commonTest не компилируется — устаревшие FakeAppNavigator после Nav3-миграции

## ✅ Resolution (2026-06-02)
Все 11 inline-фейков мигрированы на новый `AppNavigator`: удалён `override val commands` + импорт `NavCommand`; `backStack` → `override val backStack: NavBackStack<NavKey> = NavBackStack()` (как в продакшн `AppNavigatorImpl`). `MainScreenViewModelTest` получил локальные fake `GoogleAuthRepository` (authState=`NotAuthenticated`, методы no-op/`Result.failure`) и `SyncRepository` (syncState=`Disabled`, `AppResult.Success`/`flowOf()`), прокинутые в новый конструктор `MainScreenViewModel`. Разблокировано попутно при фиксе per-item reminder scope-бага (red→green TDD требовал рабочего сьюта). Валидация: `:feature:home:testAndroidHostTest` BUILD SUCCESSFUL (весь модуль зелёный, +5 новых reminder-тестов); `:feature:home:compileKotlinWasmJs` BUILD SUCCESSFUL. **Не закоммичено.**

**NB — остаточный scope:** `feature/create` (и, возможно, другие модули) содержат те же устаревшие fake на старом `AppNavigator` — отдельный незакрытый долг, этим фиксом НЕ затронут.

## Что отложено
Весь test source set `:feature:home:commonTest` (≈11 файлов) **не компилируется** →
`:feature:home:testAndroidHostTest` падает на этапе `compileAndroidHostTest`. Это
**pre-existing техдолг, НЕ регрессия редизайна Gisti variant D** (обнаружен 2026-05-30
при настройке Roborazzi screenshot-тестов; редизайн не трогал ViewModel/Navigation/тесты).

## Контекст / корень
Миграция на **Navigation 3** изменила интерфейс `core/navigation/api/AppNavigator`:
- Убрано: `val commands: Flow<NavCommand>` (символ `NavCommand` удалён).
- Изменено: `val backStack: StateFlow<List<AppNavRoute>>` → `val backStack: NavBackStack<NavKey>`
  (`androidx.navigation3.runtime.NavBackStack` / `NavKey`).
- `MainScreenViewModel` (и, вероятно, другие VM) получили новые конструкторные параметры
  (`googleAuthRepository`, `syncRepository`).

11 тест-файлов содержат **inline-фейки** `private class FakeNavigator : AppNavigator`, каждый
из которых всё ещё переопределяет старый `commands`/`backStack` → ошибки:
`Unresolved reference 'NavCommand'`, `'commands' overrides nothing`, type mismatch на `backStack`,
`No value passed for parameter 'googleAuthRepository'/'syncRepository'`.

Почему накопилось незаметно: AGP 9 переименовал `testDebugUnitTest` → `testAndroidHostTest`;
host-тесты, похоже, перестали запускаться в обычном флоу после миграции (silent test rot).

Затронутые файлы (`feature/home/src/commonTest/.../`):
MainScreenViewModelTest, CalendarViewModelTest, today/TodayViewModelTest,
detail/ChecklistDetailAnalyticsTest, ChecklistDetailAttachmentsTest,
ChecklistDetailItemDetailsSheetTest, ChecklistDetailItemReminderTest,
ChecklistDetailPriorityTest, ChecklistDetailReminderGateTest, ChecklistDetailRepeatRuleTest,
ChecklistDetailSmartAddTest.

## Шаги возобновления
1. Создать ОДИН общий `FakeAppNavigator` в `feature/home/src/commonTest/.../testutil/`,
   реализующий текущий Nav3-интерфейс: убрать `commands`/`NavCommand`; `backStack` сделать
   `NavBackStack<NavKey>` (посмотреть как конструирует production `AppNavigatorImpl`); застабить
   все ~35 `navigateToXxx()`. Заменить 11 inline-фейков на общий (убрать дубли).
2. Добавить фейки для новых VM-зависимостей (`GoogleAuthRepository`, `SyncRepository`) и передать
   их в конструкторы во всех тестах, где создаётся `MainScreenViewModel`/др.
3. Прогнать `:feature:home:testAndroidHostTest` → зелёный. Учесть возможный каскад: после
   успешной компиляции могут всплыть runtime-падения отдельных тестов (НЕ маскировать сменой
   ассертов — чинить причину или фиксировать отдельным todo).
4. Рассмотреть добавление `testAndroidHostTest` в CI-гейт, чтобы silent test rot не повторился.

## Связано
- Редизайн: [redesign-gisti-variant-d-2026-05-30](../active/redesign-gisti-variant-d-2026-05-30.md)
- Memory: Navigation 3 SavedStateHandle pitfall, web single-pane layout.
