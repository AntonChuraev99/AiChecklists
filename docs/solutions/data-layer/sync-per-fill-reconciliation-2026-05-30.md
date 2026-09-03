---
title: "Per-Fill Deletion Reconciliation in Server-Authoritative Sync"
date: 2026-05-30
type: bug-fix
modules: [feature/checklist, core/datastore]
keywords: [sync, reconciliation, fills, firestore, server-authoritative, cross-device, delete-propagation, ChecklistFillDao, SyncRepositoryImpl]
project: checklists
---

# Per-Fill Deletion Reconciliation Across Devices

## Проблема / Контекст

В **server-authoritative sync архитектуре** (Firestore = источник истины) чеклист-уровень reconciliation обрабатывает удаление целых чеклистов. Но удаление **отдельного fill** одного чеклиста на одном устройстве не распространяется на другие устройства.

Сценарий:
- Пользователь открывает чеклист C на устройстве A, удаляет fill F.
- На устройстве B этот же чеклист C сохраняется (его облачный состояние не менялось).
- Но fill F, которого уже нет в облаке, продолжает висеть локально на устройстве B, пока не произойдёт чеклист-уровневых изменений.

**Контекст:** часть рефакторинга 2026-05-30 `docs/active/sync-server-authoritative-2026-05-30.md`. Чеклист-уровень reconciliation (удалять SYNCED чеклисты отсутствующие в облаке) уже реализована; это решение расширяет паттерн на per-fill granularity.

## Решение

### 1. DAO запрос

`ChecklistFillDao.getSyncedFillCloudIds(checklistId): List<String>`

```sql
SELECT cloudId
FROM checklist_fills
WHERE checklistId = :checklistId
  AND syncStatus = 0         -- SYNCED only
  AND cloudId IS NOT NULL
```

Зеркало `ChecklistDao.getSyncedCloudIds()` (чеклист-уровень). Извлекает список облачных ID всех SYNCED fills этого чеклиста.

### 2. Reconciliation логика

Новый приватный метод `SyncRepositoryImpl.reconcileDeletedFills(localChecklistId, remoteFills)`:

```
1. Получить список localSyncedCloudIds = dao.getSyncedFillCloudIds(localChecklistId)
2. Для каждого cloudId в localSyncedCloudIds:
   - если cloudId НЕ присутствует в remoteFills (по cloudId):
     → fillDao.deleteById(localId)  // this local fill was deleted on another device
3. Если какой-то fill был в remoteFills но с другим содержимым:
   → обновляется в обычной UPDATE-ветке mergeRemoteChecklist
```

Вызывается **в конце UPDATE-ветки** `mergeRemoteChecklist`, после обработки `remote.fills`:

```kotlin
// Merge fills normally (insert new / update newer)
for (remoteFill in remote.fills) { ... }

// Then reconcile fills that were deleted on another device
reconcileDeletedFills(localChecklistId = result.id, remoteFills = remote.fills)
```

### 3. SKIP-ветка: критическое архитектурное решение

Fill-reconciliation выполняется **ТОЛЬКО в UPDATE-ветке** (remote более новый).

**В SKIP-ветке (local более новый) — НЕ выполняется.** Почему:

- SKIP-ветка означает, что локальное состояние чеклиста приоритизировано (local timestamp > remote timestamp).
- Устаревший snapshot `remote.fills` не полно отражает то, что произошло локально.
- Если вызвать reconciliation на устаревшем snapshot, мы стёрли бы локальные fill-правки, которые как раз и привели к SKIP (обратный data-loss баг).

**Решение:** reconciliation вызывается только при `UPDATE` (условие в `mergeRemoteChecklist`):

```kotlin
when {
    isLocalOlderThanRemote -> {
        // UPDATE branch: remote is authoritative
        val updated = mergeRemoteChecklist(...)
        reconcileDeletedFills(updated.id, remote.fills)  // ← safe here
        return@launch
    }
    else -> {
        // SKIP branch: local is authoritative, no reconciliation
        return@launch
    }
}
```

### 4. Safety гарантии (зеркало чеклист-уровня)

- **SYNCED(0) fills only:** только fills со статусом `syncStatus == 0` участвуют в reconciliation. PENDING_UPLOAD(1) защищены (новый локальный fill, ещё не в облаке, переживает удаление).
- **PENDING_DELETE(2) не трогаются:** эти fills уже в процессе pushPendingChanges (будут удалены в облаке).
- **Success-only execution:** reconciliation вызывается только на полном успешном fetch (унаследовано от `pullAndMerge` → вызов в `AppResult.Success` ветке).

### 5. Тесты

4 новых unit-теста в `SyncRepositoryImplTest` (`commonTest`):

1. `reconcileFills_removesSyncedAbsentFromCloud` — locales SYNCED fill удаляется, если его cloudId отсутствует в remote.
2. `reconcileFills_keepsPendingUploadFill` — PENDING_UPLOAD fill выживает (ещё не в облаке).
3. `reconcileFills_skippedWhenChecklistSkipped` — SKIP-ветка (local newer) не вызывает reconciliation.
4. `reconcileFills_scopedToOwningChecklist` — reconciliation чеклист-specific (не трогает fills других чеклистов).

All 15 tests pass (11 existing + 4 new), no regressions.

## Почему именно так

### Почему reconciliation в UPDATE-ветке?

Remote более новый → авторитетен для этого чеклиста. Отсутствие fill в remote.fills = удаление на другом устройстве. Это дозволено распространять.

### Почему НЕ в SKIP-ветке?

SKIP означает local timestamp > remote timestamp. Локальное состояние приоритизировано. На устаревшем snapshot нельзя полагаться для deletion — рискуешь стереть локальные правки.

**Аналогия:** если я редактирую A.txt локально, а в облаке есть старая версия с deleted paragraph B, я не хочу, чтобы при синхе мой редакт A терял приоритет ради удаления B. SKIP защищает от этого.

### Почему DAO + приватный метод?

- `getSyncedFillCloudIds` = reusable query (можно использовать в других местах).
- `reconcileDeletedFills` = приватный (implementation detail, используется только внутри `mergeRemoteChecklist`).
- Паттерн зеркалит checklist-level reconciliation, делая код coherent и поддерживаемым.

## Примеры

### Сценарий 1: удаление fill на другом устройстве

```
Локально:  Checklist C { Fill F1 (cloudId=cf1), Fill F2 (cloudId=cf2) }
В облаке:  Checklist C { Fill F1 (cloudId=cf1) }  ← F2 удалена

pullAndMerge UPDATE-ветка:
  1. mergeRemoteChecklist() обновляет F1, игнорирует F2 (не в remote.fills)
  2. reconcileDeletedFills():
     - localSyncedIds = [cf1, cf2]
     - remoteFillIds = [cf1]
     - cf2 отсутствует в remote → fillDao.deleteById(F2.localId)
  3. Результат: локально остаётся только F1 ✓
```

### Сценарий 2: новый fill на локальном устройстве

```
Локально:  Checklist C { F1 (cloudId=cf1, SYNCED), F3 (cloudId=null, PENDING_UPLOAD) }
В облаке:  Checklist C { F1 (cloudId=cf1) }

pullAndMerge UPDATE-ветка:
  reconcileDeletedFills():
    - localSyncedIds = [cf1]  ← только SYNCED fills
    - cf1 есть в remote → не удалять
    - F3 (PENDING_UPLOAD) не участвует в проверке
  3. Результат: F3 переживает (будет отправлен в облако на следующем pushPendingChanges) ✓
```

### Сценарий 3: SKIP-ветка (local newer)

```
Локально:  Checklist C (timestamp 2026-05-30 10:00) { F1, F2 }
В облаке:  Checklist C (timestamp 2026-05-30 09:00) { F1 }  ← старое состояние

pullAndMerge SKIP-ветка:
  - local newer → SKIP (не вызываем mergeRemoteChecklist вообще)
  - reconcileDeletedFills не вызывается
  3. Результат: локальное состояние { F1, F2 } сохраняется (приоритет local) ✓
```

## Связанные файлы

- `feature/checklist/src/commonMain/kotlin/.../data/db/ChecklistFillDao.kt` — DAO query
- `feature/checklist/src/commonMain/kotlin/.../data/sync/SyncRepositoryImpl.kt` — reconciliation logic
- `feature/checklist/src/commonTest/kotlin/.../data/sync/SyncRepositoryImplTest.kt` — tests (4 new)
- Parent: `docs/active/sync-server-authoritative-2026-05-30.md` — чеклист-уровень reconciliation (Итерация 1)
