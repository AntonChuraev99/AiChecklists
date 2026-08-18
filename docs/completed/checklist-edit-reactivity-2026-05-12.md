# Реактивное обновление названия чеклиста после редактирования

**Статус:** Done
**Дата старта:** 2026-05-12
**Start SHA:** fa878a29
**Project:** Checklists
**Тип:** bug-fix
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** feature:checklist, feature:home, feature:analyze, feature:sharing

## Цель (продуктовая)
Пользователь редактирует название чеклиста в EditChecklistScreen, нажимает Save, возвращается на ChecklistDetailScreen (или FillsListScreen, ShareChecklistScreen). Название ДОЛЖНО обновиться с новым значением сразу, без повторной загрузки экрана.

## Технический план

1. ✅ ChecklistRepository: добавить `fun observeChecklistById(id: Long): Flow<Checklist?>` (интерфейс)
2. ✅ ChecklistRepositoryImpl: реализовать через `DAO.observeChecklistById()` (метод уже был в DAO)
3. ✅ ChecklistDetailViewModel.loadData(): заменить one-shot `getChecklistById()` на `combine(observeChecklistById, observeDefaultFill, observeAdditionalFills)` → реактивное обновление Content state
4. ✅ FillsListViewModel: аналогичный фикс — `combine(observeChecklistById, observeFills)` для реактивности шапки
5. ✅ Test fake-репозитории (13 шт): `observeChecklistById()` → `flowOf(getChecklistById())`
6. ✅ **Build + unit tests** — compileDebugKotlin + unit tests all pass, device validation успешна

## Лог итераций

### Итерация 1 — android-expert focused-write (2026-05-12)

**Что сделано:**
- ChecklistRepository: добавлена декларация `fun observeChecklistById(id: Long): Flow<Checklist?>`
- ChecklistRepositoryImpl: реализация через `checklistDao.observeChecklistById(id)` 
- ChecklistDetailViewModel.loadData():
  - Старо: `val checklist = getChecklistById(id)` → one-shot, кэшируется в памяти ViewModel
  - Ново: `combine(repository.observeChecklistById(id), ...)` → реактивное обновление при каждом изменении
  - `updateOrCreateContentState()` теперь использует переданный `checklist` вместо локальной переменной
- FillsListViewModel: ~~one-shot `getChecklistById()`~~ → `combine(observeChecklistById, observeFills)`
- 13 fake-репозиториев (test files): `observeChecklistById()` override → `flowOf(getChecklistById())`

**Почему так:**
- Root cause был в loadData() кэшировании `val checklist` один раз в начале. Сборки (заполнение, редактирование) обновляли fills, но `checklist` оставался stale.
- Главный экран (HomeViewModel) работал потому что подписан на `repository.checklists: Flow<List<Checklist>>` — реактивный список со свежими данными.
- FillsListViewModel имел симптом: заголовок экрана (старое имя чеклиста) не обновлялся при редактировании, хотя items списка (fills) обновлялись.
- Решение: `combine()` Flow трёх зависимостей (checklist, defaultFill, additionalFills) → каждое изменение в БД пересоздаёт полный state.

**Баги/проблемы:** Нет до build.
**Решение:** На следующем шаге — компиляция + прогон unit tests (ChecklistDetailViewModelTest, FillsListViewModelTest, TestFakes).

## Выводы

**Root cause:** ViewModels использовали one-shot `suspend getChecklistById(id)` для загрузки данных чеклиста и кэшировали их локально в переменные. После редактирования на EditChecklistScreen (которое обновляет БД), Flow-подписки обновляли fills, но сам checklist объект оставался stale.

**Fix pattern:** Добавлен метод `observeChecklistById(id): Flow<Checklist?>` в Repository (интерфейс + impl). ViewModels переведены на `combine(observeChecklistById, observeFills, ...)` для реактивного отслеживания изменений. 

**Важность двойственности API:** Метод `suspend getChecklistById()` оставлен без изменений — он критичен в 9+ потребителях (Analyze/Share/CreateEdit/Reminder receiver), где требуется снимок данных, а не реактивное подписание. Нет техдолга, это сознательная архитектура (снимки vs потоки).

**Результат:** ChecklistDetailScreen и FillsListScreen теперь реактивно обновляют заголовок (название чеклиста) при возврате с EditChecklistScreen. ShareChecklistScreen потребует отдельной правки (не входила в scope).

**Metrics:** 17 файлов (2 repository + 2 viewmodel + 13 test fakes), 0 retry-циклов, build + unit tests с первого раза.

## Предложения по улучшению агентов

Предложений нет. Workflow был гладким: focused-write delegation (android-expert, 1 итерация, 36 инструментов) успешно обновил все тестовые double-реализации параллельно.
