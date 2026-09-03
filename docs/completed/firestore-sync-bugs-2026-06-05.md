# Firestore Cross-Platform Sync — Timestamp & CloudId Bugs

**Статус:** Done
**Дата старта:** 2026-06-05
**Start SHA:** unknown (no INIT phase)
**Project:** checklists
**Тип:** bug-fix
**Сложность:** Complex
**Impact:** High
**Затронутые модули:** sync, datastore, firestore, android, wasmJs, kmp

⚠ INIT phase was skipped — minimal active doc reconstructed during COMPLETE. Counters могут быть неточными.

## Цель (продуктовая)

Пользователь сообщил, что синхронизация чеклистов по Google акку не работает на веб. Исправить оба бага, чтобы:
- Web → Android: правки из облака доходили через LWW-merge (были пропусканы из-за type mismatch).
- Android → Web: legacy-чеклисты без cloudId синхронизировались (были молчаливо пропускаются).

## Технический план

**Шаг 1: Диагностика и Fix A+B**
1. Выявить root cause Баг #1: несоответствие типов `updatedAt` (web: Timestamp, Android: Long).
2. Реализовать `asEpochMillis()` для defensive read в AndroidFirestoreSyncDataSource.
3. Убрать `serverTimestamp()` из init.js.template, заменить на `Date.now()` (client millis).
4. Верификация: `compileKotlinWasmJs` + `compileDebugKotlin`.

**Шаг 2: Fix #2 + TDD**
1. Выявить root cause Баг #2: `cloudId ?: continue` молчаливо пропускает legacy-чеклисты.
2. Реализовать `assignCloudId(id, cloudId)` в ChecklistDao + ChecklistFillDao.
3. Добавить backfill-логику в pushPendingChanges с DAO-вызовом + логированием.
4. Написать TDD-тест `push_backfillsCloudIdForLegacyChecklistAndUploads`.
5. Верификация: `testAndroidHostTest` + `compileDebugKotlin` + `compileKotlinWasmJs`.

## Лог итераций

### Итерация 1 — 2026-06-05 — main-agent (no specialist trace)

**Что сделано:** Диагностика обоих багов и реализация Fix A+B+#2.
- Выявлены root causes: (A) type mismatch Timestamp vs Long в updatedAt; (B) cloudId == null backfill gap.
- Реализована `asEpochMillis()` в AndroidFirestoreSyncDataSource для терпимого чтения Timestamp.
- Удалена `serverTimestamp()` из init.js.template, заменена на `Date.now()`.
- Реализована backfill-логика в pushPendingChanges с DAO assignCloudId.
- Добавлен TDD-тест `push_backfillsCloudIdForLegacyChecklistAndUploads`.

**Почему так:** 
- Fix A+B: defensive typing (читатель терпим) + унификация write (писатель строг) для кросс-платформенной сериализации.
- Fix #2: backfill при first-push, а не миграция (безопаснее для sync-систем).

**Баги/проблемы:** Нет (validation PASS).

**Решение:** Все компоненты пройдены (compileDebugKotlin, compileKotlinWasmJs, testAndroidHostTest).

## Выводы

Две независимые синхронизационные ошибки, исправленные в один цикл:

1. **Баг #1 (Timestamp type-mismatch):** Web писала serverTimestamp, Android читал как Long — LWW-merge пропускал правки. Фикс: asEpochMillis() + Date.now() в init.js.
2. **Баг #2 (legacy cloudId null):** Старые чеклисты без cloudId молчаливо не синхронизировались. Фикс: backfill cloudId при pushPendingChanges.

**E2E-проверка требует:** Android-релиз (новая сборка) + web-redeploy (новый init.js) + user-валидация на Play Store.

## Предложения по улучшению агентов

- [ ] (none specific — диагностика и реализация покрыли весь scope)

