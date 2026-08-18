---
title: Per-item createdAt timestamp for Layer 3 chat context (absolute-time questions)
date: 2026-06-17
status: deferred
parent_task: docs/todos/2026-05-17-aichat-deep-thinking-no-item-history.md (resolved — context expanded with item text + position)
blocking_reason: needs Room migration + Firestore sync field — out of scope of the 2026-06-17 context-expansion
resume_trigger: User says «чтобы AI отвечал КОГДА я добавил пункт / absolute time в чате» OR a user reports the chat can't answer "when did I add X"
estimated_complexity: Standard
keywords: [ai-chat, layer-3, chatcontext, item-timestamp, room-migration, firestore-sync, createdAt]
---

# Per-item createdAt for Layer 3 — absolute-time questions

## Что отложено

Расширение контекста Layer 3 (2026-06-17, see resolved `2026-05-17-aichat-deep-thinking-no-item-history.md`) добавило в `ChecklistContext.recentItems` текст пунктов + `position` (индекс списка как прокси свежести). Это закрывает **«что я последнее добавлял»** (хвост списка) и **«найди задачу про X»** (текст теперь есть). НЕ закрывает **«когда именно я добавил Y»** — у `ChecklistItem` нет timestamp создания.

## Контекст

`ChecklistItem` поля: `text, checked, id, weekday, priority, type, parentId` — без `createdAt`. `id` встраивает `currentTimeMillis()`, но регенерируется на `updateChecklist()` → ненадёжен как время. `position` в списке — текущий прокси свежести (работает для «последнее», не для абсолютного времени).

## Что нужно при возобновлении

- [ ] Добавить `createdAt: Long` в `ChecklistItem` (domain) + Room entity + **миграция** (schema bump).
- [ ] Firestore sync: добавить поле в **ОБА** `toMap` (upload) + `toChecklistSyncData` (fetch) **КАЖДОГО** `FirestoreSyncDataSource` impl — Android пишет hand-written Map отдельно от commonMain mappers (см. project-memory `android-firestore-manual-map-sync-fields`, иначе поле теряется только на Android).
- [ ] `buildChecklistsSummary()` (`ChatViewModel`) → передавать `addedAt` вместо/вместе с `position` в `recentItems`.
- [ ] CF `_format_checklists_summary` (`firebase-functions/main.py`) + prompt-инструкция в gitignored `prompts_private.py` → использовать timestamp («можешь отвечать на вопросы о времени добавления»).
- [ ] Тест на новый формат summary.

## Как возобновить

Базовый слой (item text в Layer 3, recentItems, лимиты 6/30) уже в коде — см. resolved todo `2026-05-17-aichat-deep-thinking-no-item-history.md`. Здесь добавляется только timestamp-слой поверх. Главная стоимость — Room-миграция + двусторонний Firestore-mapping (не забыть Android hand-written Map).
