# PR #12 Review & Merge — ChecklistDetail four bugs

**Статус:** Done
**Дата старта:** 2026-07-15
**Start SHA:** d41e3832
**End SHA:** 783820c1
**Project:** gisti-ai-checklists
**Тип:** bug-fix
**Сложность:** Standard
**Impact:** High
**Затронутые модули:** core/common (DI), feature/home (ChecklistDetail), feature/checklist (domain)

## Цель (продуктовая)
Ревью и мердж открытого PR #12 (6 коммитов) с четырьмя багфиксами ChecklistDetail; выявление и фикс критических дефектов инфраструктуры.

## Технический план
1. Прочитать 6 коммитов PR
2. Выполнить ревью с фокусом на scope, DI, persists
3. Обнаружить дефект app-wide CoroutineScope (Job vs SupervisorJob)
4. Применить фикс через коммит a4abf621
5. Верифицировать тесты (286/0 androidHostTest, wasmJs compile)
6. Мерджить в master (783820c1)

## Лог итераций

### Итерация 1 — 2026-07-15 — main-agent + bug-pattern-reviewer (no specialist trace)

**Что сделано:**
1. Разобран PR #12 — 6 коммитов автора (37b30bd4..836eaa3c):
   - 37b30bd4: fix(checklist): hide delete slab on completion pop
   - ad8d0d7c: fix(checklist): restore undone item into its folder
   - 1430fac2: feat(checklist): delete folder from overflow sheet
   - 592756ec: fix(checklist): persist folder delete on a surviving scope
   - 5078a229 + 836eaa3c: docs(checklist) — два write-up'а

   Добавлено по итогам ревью и мердж:
   - a4abf621: fix(core): supervise the app-wide coroutine scope [главный фикс DI]
   - 783820c1: Merge pull request #12 → master

2. РЕВЬЮ выявило КРИТИЧЕСКИЙ дефект в DI (severity HIGH):
   - Исходное состояние: `CommonCoreModule.kt:15` → `single<CoroutineScope> { CoroutineScope(Dispatchers.Default) }`
   - Проблема: Koin-фабрика подставляет обычный `Job()`, когда контекст не содержит Job. Одно упавшее дитя (throw в child launch{}) отменяет ВСЮ app-wide scope.
   - Потребители: sync (checklistRepository), credits convergence, user-data writes (5 мест)
   - Достижимость: PR #12 перенёс операцию удаления папки на this scope, но без runCatching/логирования → uncaught throw → процесс ронится или scope становится неживым на остаток сессии.

3. Применён фикс (a4abf621):
   - `single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }`
   - `runCatching` + `AppLogger.error` вокруг persist в confirmFolderDelete

4. Верификация:
   - `:feature:home:testAndroidHostTest` → 286 passed / 0 failed
   - wasmJs компилируется без ошибок
   - PR отставал от master на 8 коммитов → переиндексированы все зависимости на СМЕРДЖЁННОМ дереве

**Почему так:**
- SupervisorJob обеспечивает иерархию scope: одно упавшее дитя отменяет СЕБЯ, не sibling'ов
- Это паттерн Kotlin Coroutines для «пула независимых работников» (sync + credits + writes — все независимы)
- Без runCatching — uncaught throw в platform-specific коде (Android FileSystem) может остановить весь scope

**Баги/проблемы:**
- wasmJs вживую на :9090 НЕ прогонялся перед мерджем (все 4 фикса в commonMain, но риск)
- Две операции persist (`updateFill` + `updateChecklistTemplate` в delete folder) не в одной транзакции → может залипнуть наполовину на crash между ними

**Решение:**
- Фикс применён через коммит a4abf621 (вручную добавлен в ветку PR перед мерджем)
- Деferred: atomic-persist-transaction → docs/todos/

**Итог итерации:** смерджено в master (783820c1) с критическим фиксом SupervisorJob.

## Выводы

### Критическое исправление DI
Дефект `CoroutineScope(Dispatchers.Default)` без SupervisorJob был скрытым и высокоimpact:
- **Manifestация:** любой uncaught throw в child launch{} отменял весь app-wide scope, делая остальные операции (sync, credits, writes) no-op до конца процесса
- **Скрыто на master:** дефект был ДО PR #12, но PR #12 сделал его достижимым (перенёс delete folder с обработкой ошибок)
- **Потребители:** 5 независимых операций → нужна SupervisorJob для изоляции failure

### Осознанные риски, принятые пользователем
- wasmJs не прогонялась на :9090 перед мерджем → риск регрессии на веб при деплое
- Folder delete persist не atomic → deferred в todos

### Аналитика — ступенька, не рост
- `folder_deleted` событие может теряться на пути «я в папке, удаляю её» (последний statement в отменяемом scope после двух suspend-точек)
- На релизе это даст СТУПЕНЬКУ в метриках, не органический рост

## Предложения по улучшению агентов

### kotlin-expert
- [ ] Добавить в раздел "Coroutine Scope Design": SupervisorJob как load-bearing паттерн для пулов независимых работников; обязателен для app-wide scope

### compose-feature-expert
- [ ] Добавить в раздел "Long-lived operations": запись, которая должна пережить ViewModel, требует app-wide scope с SupervisorJob + runCatching + AppLogger.error

## Связанные документы
- Решение: `docs/solutions/di-supervise-app-wide-scope-2026-07-15.md`
- Deferred work: `docs/todos/2026-07-15-folder-delete-persist-not-atomic.md`
- Аналитика: Project Memory `analytics-folder-deleted-step-not-growth-2026-07-15.md`
