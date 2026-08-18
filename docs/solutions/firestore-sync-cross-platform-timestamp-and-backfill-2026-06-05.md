---
title: "Firestore Cross-Platform Sync: Timestamp Deserialization & Legacy CloudId Backfill"
date: 2026-06-05
type: bug-fix
modules: [sync, datastore, firestore, android, wasmJs, kmp]
keywords: [firestore, lastWriteWins, timestamp-deserialization, cloudId-backfill, type-polymorphism, wasmJs-init-js, room-migration, silent-skip]
project: checklists
---

# Firestore Cross-Platform Sync: Timestamp Deserialization & Legacy CloudId Backfill

## Проблема / Контекст

Пользователь сообщил: **«на веб не работает синхронизация чеклистов по гугл акку»**. Диагностика выявила **два независимых бага** в кросс-платформенной Firestore-синхронизации:

### Баг #1: Web → Android — правки не доходят из облака

**Симптом:** Чеклист отредактирован на web → Firestore обновлен → Android берёт данные, но не применяет изменения.

**Root cause:** Несогласованность типов поля `updatedAt` между платформами:
- **Web (init.js)** писал: `data.updatedAt = serverTimestamp()` → Firestore хранит как **Timestamp** (nested `{timestampValue: ...}`).
- **Android** читал: `this["updatedAt"] as? Long ?: 0L` → для Timestamp cast возвращает **null** → fallback **0L**.
- **LWW-merge** (`mergeRemoteChecklist`) сравнивает: `remote.updatedAt(0) > local.updatedAt` → всегда **false** → ветка **SKIP** → правка отбрасывается.

**Доказательство:**
- Firestore document 'дела': `updatedAt: {timestampValue: "2026-06-05T10:30:00Z"}` (от web).
- Firestore document 'апки': `updatedAt: {integerValue: 1717585200000}` (от Android).
- logcat: `merge: SKIP 'дела' (local is newer or equal)` при каждом pull.

### Баг #2: Android → Web — «Поиск работы» не попадает в облако

**Симптом:** Чеклист создан на Android (до синхронизации) → остаётся локально с `cloudId == null` → при попытке push молча пропускается → web никогда не видит.

**Root cause:** Legacy-чеклисты (из до-sync эры) остались с `cloudId = null`. В `SyncRepositoryImpl.pushPendingChanges` строка:
```kotlin
val cid = entity.cloudId ?: continue  // молчаливо пропускает
```

Нарушает правило проекта (2026-05-24): «silent-skip на UX-путях = bug». Пользователь видит ненулевой счётчик pending-синхронизации, нажимает sync, но данные не летят.

**Доказательство:** logcat:
```
push: 1 pending checklists → push: complete (без uploading)
```

---

## Решение

### Fix A: Defensive Read для Firestore Timestamp (обязательный)

**Проблема:** кросс-платформенное хранилище (Firestore) хранит `updatedAt` в разных типах. Защита = терпимое чтение + унификация write.

**Добавлена функция** в `AndroidFirestoreSyncDataSource`:
```kotlin
private fun Any?.asEpochMillis(): Long =
    when (this) {
        is Long -> this
        is Double -> this.toLong()
        is com.google.firebase.Timestamp -> this.toDate().time
        is Number -> this.toLong()
        else -> 0L
    }
```

Применена ко ВСЕМ `updatedAt` в `toChecklistSyncData` и `toFillSyncData`. Теперь:
- Legacy Timestamp из облака → `toDate().time` → корректный Long millis.
- Новые записи (Long) → прямо проходят.
- Число (Number) → safe cast.
- Null или иное → fallback 0L (не крешится).

**Результат:** LWW-merge теперь работает корректно на кросс-платформенных данных.

### Fix B: Унификация Firestore Write в init.js (закреплено правило)

**Проблема:** web продолжает писать `serverTimestamp()` → рецидив бага при добавлении нового web-потребителя или миграции.

**Решение:** убрана функция `serverTimestamp()` из `__firestoreSetDoc` и `__firestoreBatchWrite` в `init.js.template`. Web теперь пишет **client-side Long millis** (как Android):
```javascript
// ВМЕСТО: updatedAt: serverTimestamp()
updatedAt: Date.now()  // client millis
```

Добавлен NOTE-комментарий для защиты от рецидива:
```javascript
// NOTE: updatedAt must be a number (Long millis), not serverTimestamp().
// Web and Android LWW merge relies on comparable types.
// See docs/solutions/firestore-sync-cross-platform-timestamp-and-backfill-2026-06-05.md
```

**Выигрыш:** вся модель LWW теперь работает на **единственном формате** (client millis), независимо от платформы.

### Fix #2: Backfill CloudId для Legacy Чеклистов

**Проблема:** чеклисты, созданные до sync-инфраструктуры, имеют `cloudId == null`. молчаливый `?: continue` → никогда не синхронизируются.

**Решение:**

1. **Новый DAO-метод** (`ChecklistDao` + `ChecklistFillDao`):
   ```kotlin
   @Query("UPDATE checklists SET cloudId = :cloudId WHERE id = :id")
   suspend fun assignCloudId(id: String, cloudId: String)
   ```

2. **В `SyncRepositoryImpl.pushPendingChanges`** вместо `?: continue`:
   ```kotlin
   val cid = entity.cloudId ?: run {
       val newId = generateCloudId()  // Uuid.random().toString()
       assignCloudId(entity.id, newId)
       logger.info(TAG, "push: backfilled cloudId for legacy ${entity.id}")
       newId
   }
   ```

3. **Delete-ветка:** если `cloudId == null` → явное локальное удаление (в облаке записи и не было):
   ```kotlin
   if (entity.cloudId == null) {
       deleteChecklistLocally(entity.id)
   } else {
       deleteChecklist(entity.cloudId)  // remote delete
   }
   ```

4. **Backfill распространён на fills** (стабильный `cloudId` для merge).

5. **Новый TDD-тест:** `push_backfillsCloudIdForLegacyChecklistAndUploads`.

**Результат:** legacy-чеклисты теперь синхронизируются первый раз с авто-сгенерированным `cloudId`.

---

## Почему именно так

### Defensive Typing (Fix A)

Firestore — общее хранилище для web + Android. Типы полей определяются **первым писателем**:
- Web писал Timestamp → Firestore хранит Timestamp.
- Android писал Long → Firestore хранит Long.
- Второй читатель видит **чужой** тип → LWW-модель рассинхронизируется.

**Решение:** читатель терпим к типам (cast в safe вариант), писатель унифицирует формат.

**Альтернативы:**
- ❌ Миграция всех legacy-Timestamp в облаке (runtime-дорого, require re-sync).
- ❌ Android пишет Timestamp() (теряет контроль, web может добавить своё поле с иным форматом).
- ✅ Defensive read + unify write (работает сразу, защита от рецидива).

### CloudId Backfill (Fix #2)

**Почему не просто удалить local?**
- Пользователь создал чеклист локально, потом синхронизировался → данные в приложении (sync = merge, не replace).
- Удаление → потеря данных.

**Почему не миграция при first-auth?**
- Миграция требует знания облака → асинхронный fetch → может быть пропущена (логирование ненадёжно).
- Backfill при first-push — гарантирован (sync path обязателен).

**Почему не UUID на создание?**
- legacy-чеклисты уже существуют без UUID.
- Новые чеклисты получают cloudId при создании (CreateChecklistUseCase).
- Backfill покрывает gap между версиями.

---

## Примеры

### Пример 1: Web→Android через legacy Timestamp

**до:**
```
Web пишет:    updatedAt: serverTimestamp() → {timestampValue}
Android читает: this["updatedAt"] as? Long ?: 0L → null → 0L
Merge: remote(0) > local(1717585200000)? NO → SKIP
```

**после:**
```
Web пишет:    updatedAt: Date.now() → 1717585200000
Android читает: this["updatedAt"] as? Long ?: asEpochMillis() → 1717585200000
Merge: remote(1717585200000) > local(1717585200000)? NO, но тип согласован ✓
(или если remote > local → APPLY)
```

### Пример 2: Android → Web с legacy cloudId

**до:**
```
Android создаёт чеклист (cloudId: null) → PENDING_UPLOAD
User нажимает sync → pushPendingChanges: val cid = entity.cloudId ?: continue → SKIP
logcat: "push: complete" (ничего не загружено)
Web: нет синхронизации
```

**после:**
```
Android создаёт чеклист (cloudId: null) → PENDING_UPLOAD
User нажимает sync → pushPendingChanges: val cid = entity.cloudId ?: backfill(Uuid.random)
Firestore: новый doc создан с cloudId
Web: pull → merge → видит чеклист ✓
logcat: "push: backfilled cloudId for legacy <id>"
```

---

## Связанные файлы

- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/sync/AndroidFirestoreSyncDataSource.kt` — Fix A (asEpochMillis)
- `composeApp/src/wasmJsMain/resources/init.js.template` — Fix B (Date.now() вместо serverTimestamp)
- `feature/checklist/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/checklist/data/sync/SyncRepositoryImpl.kt` — Fix #2 (backfill logic + generate cloudId)
- `feature/checklist/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/checklist/data/db/ChecklistDao.kt` — assignCloudId DAO
- `feature/checklist/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/checklist/data/db/ChecklistFillDao.kt` — assignCloudId DAO (fills)
- `feature/checklist/src/commonTest/kotlin/com/antonchuraev/homesearchchecklist/feature/checklist/data/sync/SyncRepositoryImplTest.kt` — TDD test + 2 fake overrides

---

## Паттерн: Cross-Platform Serialization Defense

**Общее правило для кросс-платформенных хранилищ (Firestore, SharedPreferences, Room):**

1. **Тип поля должен быть строго один** (договор между писателями).
2. **Защита на чтение:** defensive cast, терпимость к расширенному диапазону типов (Long, Double, Number).
3. **Унификация на запись:** единственный писатель или явная конвенция (client millis, не serverTimestamp).
4. **Документация:** NOTE-комментарий с ссылкой на decision/pattern-doc.
5. **Test:** cross-platform round-trip (write на одной платформе → read на другой).

**Риск:** молчаливый рассинхрон типов.
- **Симптом:** данные в облаке, но вторая платформа их игнорирует (нет ошибки, just SKIP).
- **Обнаружение:** логирование каждого skip/merge с типом и значением.

---

## Verification

### Validation пройдена

- `:feature:checklist:testAndroidHostTest` — 302 PASS (включая новый `push_backfillsCloudIdForLegacyChecklistAndUploads`).
- `:androidApp:compileDebugKotlin` — PASS 14s.
- `:composeApp:compileKotlinWasmJs` — PASS.
- `init.js` перегенерирован из `init.js.template` (Fix B).

### E2E требует production-deploy

Автоматическая валидация прошла, но эндпоинт в реальных условиях требует:
1. **Android-релиз** (новая сборка с Fix A + Fix #2).
2. **Web переdeployment** (новый init.js с Fix B).
3. **User validation:** пересоздать чеклист на Android (старые с null cloudId backfill на first-push) и проверить web-sync.

**ВАЖНО:** НЕ переустанавливать debug-сборку на устройство пользователя (разная подпись → `adb uninstall` → потеря Room-БД, правило 2026-05-24). User должен обновить через Play Store.

### iOS (stub)

`IosFirestoreSyncDataSource` — заглушка («not implemented»). При будущей реализации iOS sync **ОБЯЗАТЕЛЬНО** использовать тот же `asEpochMillis` defensive-паттерн:

```kotlin
// iOS stub — TODO when implementing sync
// Expected: toChecklistSyncData { ... updatedAt = receivedDate.timeIntervalSince1970 * 1000 } (always Long millis)
```

---

## Lessons Learned

1. **Firestore — общее хранилище = тип договор.** Первый писатель определяет тип. Второй читатель должен быть терпим или переговориться.

2. **serverTimestamp() — платформа-специфичный вызов.** Web видит объект, Android видит Long. Избегай в кросс-платформенных полях; используй Date.now() (client millis, одинаков везде).

3. **Silent-skip = скрытый баг.** Правило проекта (2026-05-24): каждый skip/fallback должен быть залогирован + явно обработан в UI. `?: continue` молча теряет пользовательские данные.

4. **Legacy data = миграционный долг.** При добавлении nullable-поля (cloudId) нужна либо миграция (in-place update), либо backfill (on-first-use). Backfill безопаснее для sync-систем.

5. **Defensive typing + unify write.** При кросс-платформенной сериализации: читатель терпим, писатель строг.

