---
title: "Delete button on Checklist NotFound screen (recover from broken/restored checklists)"
status: done
date: 2026-06-06
start_sha: a8b53ecc
type: feature
complexity: Standard
impact: Medium
modules: [feature/home, core/designsystem]
keywords: [checklist-not-found, delete, soft-delete, restored-checklists, dead-end, ChecklistDetailViewModel]
---

# Delete button on Checklist NotFound screen

## Цель

Восстановленные/битые чеклисты не открываются — экран `ChecklistDetailState.NotFound`
(«Checklist not found»). Удалить их штатно нельзя (удаление живёт ВНУТРИ экрана детали,
в который не зайти) → dead-end. Добавить кнопку **Delete checklist** на NotFound-экран,
чтобы пользователь мог убрать нерабочий чеклист.

## Технический план

- `ChecklistDetailIntent.OnDeleteCorruptedChecklist` (новый data object).
- ViewModel `deleteCorruptedChecklist()`: `repository.getChecklistById(checklistId)` →
  cancel reminders → `repository.deleteChecklist(checklist)` (soft-delete + PENDING_DELETE,
  переиспользует проверенный путь) → analytics(source=not_found_screen) → `navigator.onBack()`.
  checklist==null → log/skip delete, всё равно onBack.
- `NotFoundContent`: описание + `AppButtonDestructive` + локальный confirm `AlertDialog`.
- Strings EN+RU: `checklist_not_found_description`, `checklist_not_found_delete_message`.
- Тест на новый Intent (стиль существующих state-тестов).

## Ключевое решение

Удаление по `checklistId` (поле конструктора VM), а НЕ по `state.checklist` (его нет в NotFound).
`deleteChecklist(checklist)` использует только `.id` → soft-delete корректно ставит PENDING_DELETE →
tombstone улетит в Firestore → чеклист НЕ воскреснет при следующем sync (главный pitfall).

## Лог итераций

### Итерация 1 — 2026-06-06 — main-agent

**Что сделано:**
- AddChecklistDetailIntent.OnDeleteCorruptedChecklist() — new data object
- ChecklistDetailViewModel.deleteCorruptedChecklist(checklistId): getChecklistById(id) → cancelReminders() → repository.deleteChecklist(checklist) → analytics(template_found=false) → navigator.onBack()
- NotFoundContent: title + description (string) + AppButtonDestructive + confirm AlertDialog (local remember mutableStateOf)
- Strings (EN/RU): checklist_not_found_description, checklist_not_found_delete_message
- ChecklistDetailViewModelTest: test covering OnDeleteCorruptedChecklist intent + view-model state transition
- ChecklistDetailScreenContract: updated when-block to route NotFound state

**Почему так:**
1. **Dead-end pattern.** ChecklistDetailState.NotFound doesn't carry loaded checklist — deleteChecklist(checklist) inside regular Content won't work. New intent + ViewModel handler overcomes this by using checklistId from constructor, not state.checklist.
2. **Soft-delete correctness.** deleteChecklist() only reads .id from the checklist param — perfectly safe to construct a synthetic object or pass any checklist with matching id. Reusing existing repository.deleteChecklist() ensures PENDING_DELETE syncStatus is set correctly, which tombstones the record in Firestore on next sync. (Contrast: raw DAO delete would leave syncStatus=SYNCED → reconcileDeletedRemotely skips it → checklists resurrect at next pullAndMerge.)
3. **Zero friction.** deleteChecklist(checklist: Checklist) uses only .id → getChecklistById(id) is sufficient. No fake construction needed in tests; existing test fake-repos handle it transparently.
4. **Edge case — checklist==null after deletion race.** Template already deleted before screen opens (orphaned fill link). Handle gracefully: analytics flag template_found=false for visibility; unconditional onBack() regardless (no AppLogger in VM to avoid constructor bloat + cascading test updates).
5. **Confirm dialog — local state.** ChecklistDetailState.NotFound has no fields → MVI round-trip for dismiss-state is overkill. Prefer Composable-local remember { mutableStateOf(showDialog) } + AlertDialog; delete intent still goes through ViewModel (async-safe).
6. **Build result: :androidApp:compileDebugKotlin + :composeApp:compileKotlinWasmJs + :feature:home:testAndroidHostTest → BUILD SUCCESSFUL 51s.** No warnings, all tests green.

**Баги/проблемы:** нет.

**Решение:** первый проход компиляции — успешен.

## Выводы

1. **ChecklistDetailState.NotFound — dead-end for existing delete flow.** Удаление находится ВНУТРИ Content-состояния (selectivelyLoaded checklist.id требуется для repository.deleteChecklist). Битые/восстановленные чеклисты, которые не загружаются → NotFound-состояние → не-доступны существующие delete-действия. Решение: отдельный intent + ViewModel-handler с доступом к checklistId из конструктора.

2. **Soft-delete и reconciliation — критичная деталь для cross-platform sync.** deleteChecklist() устанавливает syncStatus=PENDING_DELETE. На следующем pullAndMerge в SyncRepository, если reconcileDeletedRemotely() найдёт запись в локальной БД но НЕ в облаке (cloudId есть, но отсутствует в Firestore), она УДАЛЯЕТСЯ. Альтернативный scenario — raw DAO delete() оставляет syncStatus=SYNCED → reconciliation skips it (статус=успешно синхронизировано) → следующий sync cloudStorageRepository.pullRemoteChecklist() вернёт запись → ведёт к зомби-чеклистам. Этот поток — root cause восстановленных чеклистов, который задача обходит.

3. **Переиспользование существующей repository.deleteChecklist(checklist: Checklist) — best-practice вместо сырого DAO.** Функция требует только .id для логики, хотя принимает полный объект. Означает: (а) zero новых зависимостей в VM; (б) zero fake-implementations в тестах; (в) unified path для всех delete-операций (детали-экран + not-found-экран).

4. **Confirm-диалог через local Composable-state, не MVI.** ChecklistDetailState.NotFound не имеет полей → добавление showConfirmDialog: Boolean = false в контракт = boilerplate для одной кнопки. Вместо этого: remember { mutableStateOf(false) } в NotFoundContent composable + AlertDialog binding. Delete-action сам проходит через intent (async-безопасно).

5. **Edge case: checklist==null (orphaned fill).** После удаления, если другой процесс (sync/GC) удалит запись из БД перед тем как handler выполнится, getChecklistById() вернёт null. Не логировать ошибку в VM (разбухнет конструктор + все тесты), а вместо этого: аналитика analytics(template_found=false); unconditional onBack(). Это дало нам видимость в Amplitude без шума в логах.

6. **Build green, нет компиляционных surprises (и особенно нет для wasmJs).** Standard-задача, одна итерация, no retry required.

## Предложения по улучшению агентов

- [ ] android-expert: Add pattern to arsenal: **Dead-end state recovery via separate intent**. When primary-flow reaches a state where normal actions are unavailable (Content needs field X, state doesn't have it), emit Intent from outside the state change, routed through ViewModel with access to constructor properties. Example here: OnDeleteCorruptedChecklist(checklistId). Reusable for modal recovery flows, error states, etc. Avoid anti-pattern: synthetic object construction just to call an API (unless explicitly documented as safe).
- [ ] kmp-expert: Document **soft-delete + reconciliation coordination** for Firestore SYNCED-state management. Pattern: soft-delete sets PENDING_DELETE → tombstone syncs → reconcileDeletedRemotely() on next pull checks (cloudId exists BUT cloudId not in remote) → hard-delete locally. Avoid raw DAO delete which leaves SYNCED → reconciliation skips it → zombie resurrections. Applies to all Firestore-backed entities with delete-and-sync lifecycle.
