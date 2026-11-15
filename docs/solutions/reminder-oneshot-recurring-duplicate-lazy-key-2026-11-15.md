---
title: "Одно напоминание из двух источников: дубль ключа LazyColumn на Today"
summary: "Одноразовое reminderAt и вхождение повтора в один момент давали две записи и FATAL «Key was already used»; убираем дубль в buildRemindersInRange, побеждает повтор."
date: 2026-11-15
type: bug-fix
modules: [feature/checklist, feature/home]
keywords: [crash, IllegalArgumentException, LazyColumn, key, buildRemindersInRange, expandInRange, reminderAt, repeatNextAt, TodayReminderInfo]
project: Checklists
---

# Одно напоминание из двух источников: дубль ключа LazyColumn на Today

**Суть:** одноразовое `reminderAt` и вхождение повтора в тот же момент давали две записи с
одним ключом — Today падал. Дубль убирается в `buildRemindersInRange`: одноразовое не
эмитится, если повтор даёт тот же момент; новый источник напоминаний — та же сверка.

## Проблема / Контекст

Crashlytics issue `159e2535…`, v1.23.0, FATAL `Key "cl_1_1794652200000"
was already used`. Ключ строки в `TodayViewModel`: `"cl_${checklistId}_${reminderAt}"` /
`"il_${fillId}_${itemId}_${reminderAt}"`. Ветки одноразового и
`expandInRange(startTriggerMillis = repeatNextAt)` независимы, а `expandInRange` включает
сам `startTriggerMillis` — при `reminderAt == repeatNextAt` две записи. То же у пунктов.

## Решение

В `buildRemindersInRange` для каждой сущности сначала считается развёртка повтора
(`occurrences` / `itemOccurrences`), затем одноразовое добавляется только при
`reminderAt !in occurrences`, затем все вхождения повтора. Порядок «одноразовое, потом
повторы» сохранён.

Red-репро до фикса — два теста в `ChecklistRepositoryImplTest`:
`observeRemindersInRange_checklistOneShotAndRecurringAtSameInstant_emitsOneEntry` и
`observeRemindersInRange_itemOneShotAndRecurringAtSameInstant_emitsOneEntry` (падали
`expected:<1> but was:<2>`, после фикса зелёные).

## Почему именно так

- **Корень в данных, не в UI.** `distinctBy` в `TodayViewModel` спрятал бы дубль только на
  одном экране; тот же поток читают `CalendarViewModel`, `ToolCallDispatcherImpl` и снимок
  Agenda в `ChatViewModel` (последний считал дубль дважды в `totalItems`).
- **Побеждает повтор:** он несёт индикатор повторения, а будильник срабатывает один раз в
  любом случае. Одноразовое в уникальный момент по-прежнему эмитится.

## Связанные файлы

- `feature/checklist/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/checklist/data/repository/ChecklistRepositoryImpl.kt`
- `feature/checklist/src/commonTest/kotlin/com/antonchuraev/homesearchchecklist/feature/checklist/data/repository/ChecklistRepositoryImplTest.kt`
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/today/TodayViewModel.kt`
