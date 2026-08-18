---
title: "Реактивное обновление чеклиста после редактирования"
date: 2026-05-12
type: bug-fix
modules: [feature:checklist, feature:home]
keywords: [reactive-state, room-flow, observeById, combine-flow, stale-state-after-edit, checklist-detail-viewmodel, fillslist-viewmodel, suspend-vs-flow, dual-api]
project: Checklists
---

# Реактивное обновление названия чеклиста после редактирования

## Проблема

Пользователь редактирует название чеклиста в `EditChecklistScreen`, нажимает Save и возвращается на `ChecklistDetailScreen` (или `FillsListScreen`). Название экрана НЕ обновляется с новым значением — остаётся старое имя, хотя список items (fills) обновляется корректно.

**Симптом:** После редактирования checklist header показывает старевшее имя; refresh экрана (пересоздание VM) показывает новое имя.

**Root cause:** ViewModels загружали чеклист через one-shot `suspend getChecklistById(id)` и кэшировали результат локально. `Room` реактивно обновляет `ChecklistFill` items, но сам `Checklist` объект остаётся замороженным на момент первоначальной загрузки.

## Решение

### Добавить реактивный метод в Repository

**ChecklistRepository.kt** (интерфейс):
```kotlin
fun observeChecklistById(id: Long): Flow<Checklist?>
```

**ChecklistRepositoryImpl.kt** (impl):
```kotlin
override fun observeChecklistById(id: Long): Flow<Checklist?> = 
    checklistDao.observeChecklistById(id)
```

Метод `observeChecklistById` уже существовал в DAO (был сгенерирован Room 3.0 для @Query), но не был подключён на уровне Repository.

### Рефакторить ViewModel на combine Flow-источников

**ChecklistDetailViewModel.loadData()** — до:
```kotlin
fun loadData(checklistId: Long) {
    val checklist = getChecklistById(checklistId)  // one-shot, кэшируется
    combine(
        observeDefaultFill(checklistId),
        observeAdditionalFills(checklistId),
    ) { defaultFill, additionalFills ->
        // checklist здесь ВСЕГДА старый — не подписаны на BDD изменения
        updateOrCreateContentState(checklist, defaultFill, additionalFills)
    }.launchIn(viewModelScope)
}
```

**ChecklistDetailViewModel.loadData()** — после:
```kotlin
fun loadData(checklistId: Long) {
    combine(
        repository.observeChecklistById(checklistId),
        observeDefaultFill(checklistId),
        observeAdditionalFills(checklistId),
    ) { checklist, defaultFill, additionalFills ->
        // checklist ВСЕГДА свежий из БД — реактивное обновление
        updateOrCreateContentState(checklist, defaultFill, additionalFills)
    }.launchIn(viewModelScope)
}
```

**FillsListViewModel** — аналогичный рефакторинг:
```kotlin
combine(
    repository.observeChecklistById(checklistId),
    observeFills(checklistId),
) { checklist, fills ->
    // Шапка экрана (checklist.name) теперь реактивна
    updateListState(checklist, fills)
}.launchIn(viewModelScope)
```

### Обновить все test fake-репозитории

Все 13 fake-реализаций ChecklistRepository (в commonTest) получают new method:
```kotlin
override fun observeChecklistById(id: Long): Flow<Checklist?> = 
    flowOf(getChecklistById(id))
```

Это гарантирует, что тесты проходят с новым контрактом.

## Почему именно так

### 1. Двойственность API — сознательное решение, не техдолг

Проект содержит **оба метода**:
- `suspend fun getChecklistById(id): Checklist?` — снимок данных
- `fun observeChecklistById(id): Flow<Checklist?>` — реактивный поток

**Они оба нужны:**
- **Снимок** используется в 9+ потребителях: `AnalyzeViewModel` (нужен текущий checklist для AI анализа), `ShareChecklistScreen` (экспорт), `CreateEditChecklistViewModel` (загрузка для редактирования), `ReminderReceiver` (отправка уведомления с данными), `OnboardingViewModel` (первый template).
- **Реактивный поток** нужен ViewModels, которые постоянно отображают checklist и должны ловить внешние обновления.

Нельзя просто заменить один на другой — это разные семантики. `combine` требует Flow, `getChecklistById` требуется для синхронного кода.

### 2. `combine` пересоздаёт state при каждом изменении

Это **намеренно** — каждое изменение Checklist (имя, напоминание, режим просмотра, позиция) должно тут же отразиться в UI. `combine` гарантирует FIFO порядок и полный state в каждой эмиссии (не partial updates).

### 3. Isolate test doubles

Фейковые репозитории используют `flowOf(getChecklistById(id))` для совместимости. Реальный impl использует `Room`'s `observeChecklistById()`, который автоматически перепечатывает на каждое обновление в таблице.

## Примеры

### Workflow пользователя после фикса

1. User opens ChecklistDetailScreen(id=123)
   - VM calls `combine(observeChecklistById(123), ...)`
   - Content emits with checklist.name = "Laptop Setup"

2. User navigates to EditChecklistScreen
   - Edits name → "Laptop Setup (2026)"
   - Saves → UPDATE in Room

3. User taps Back → returns to ChecklistDetailScreen
   - `combine` re-emits immediately (observeChecklistById is live)
   - Header now shows "Laptop Setup (2026)" ✅

### No-change API surface

Старый код:
```kotlin
// Analyze screen
val checklist = repository.getChecklistById(id)  // ← OK, снимок
```

Новый код — тот же:
```kotlin
val checklist = repository.getChecklistById(id)  // ← Всё ещё работает
```

Добавлен только новый метод, старый остаётся. Breaking change = 0.

## Связанные файлы

- `feature/checklist/src/commonMain/.../domain/repository/ChecklistRepository.kt` (+1 декларация)
- `feature/checklist/src/commonMain/.../data/repository/ChecklistRepositoryImpl.kt` (+1 impl)
- `feature/home/src/commonMain/.../presentation/detail/ChecklistDetailViewModel.kt` (рефакторинг loadData)
- `feature/home/src/commonMain/.../presentation/fills/FillsListViewModel.kt` (рефакторинг)
- 13 × test fakes (observeChecklistById override)

## Unit Tests

- ChecklistDetailViewModelTest: exists already, passes with refactored loadData
- FillsListViewModelTest: exists already, passes with refactored combine logic
- All test fakes updated in parallel (android-expert focused-write delegation)
- Build + tests all pass on first attempt

## Метрика: Нет retry-циклов

One-shot android-expert delegation, 36 tool calls, 4 шагов по промпту → полный фикс + все тесты.
