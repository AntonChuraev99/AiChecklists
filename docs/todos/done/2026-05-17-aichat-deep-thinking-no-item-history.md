---
title: AI Chat Layer 3 (Deep Thinking) не видит item-history / "что добавлял"
date: 2026-05-17
status: resolved
resolution_date: 2026-06-17
parent_task: docs/active/ai-chat-parser-50-cases-2026-05-17.md
blocking_reason: ""
resolution_note: "Context expanded (user chose to widen the privacy boundary). ChecklistContext gained recentItems (text/checked/position); buildChecklistsSummary sends tail last-6-per-checklist, global cap 30 leaf items. CF: extended the SHARED _format_checklists_summary helper (KEY FINDING: live Layer 3 is chat_agent, NOT chat_completion — the latter is dead code). Privacy: privacy-policy.html + chat_settings_deep_thinking_subtitle (EN+RU) updated (item text now leaves device in Deep Thinking only). 3 green tests, :feature:aichat:impl:testAndroidHostTest PASS. DEPLOYED 2026-06-17: usage line added to prompts_private.py (both templates) + gcloud chat_agent (rev chat-agent-00006-joh) + chat_completion redeployed + Firebase Hosting privacy (gisti-app.web.app). Smoke-verified end-to-end: chat_agent 200, model cited a recent item from recentItems ('взять спальник'). Absolute-time questions ('when did I add X') spun off to deferred 2026-06-17-chatcontext-item-timestamp.md (needs Room migration)."
resume_trigger: "N/A — resolved 2026-06-17. Absolute-time follow-up tracked separately."
estimated_complexity: Standard
keywords: [ai-chat, layer-3, freeform, context, privacy-by-design, item-history, deep-thinking]
---

# AI Chat Layer 3 — нет истории добавлений в контексте

## Что отложено

Пользователь в Deep Thinking режиме спросил «что последнее добавлял» — AI ответил «в ваших чеклистах ничего нового не было добавлено». Это **не баг ответа модели**, а ограничение **контекста**, который мы передаём в Layer 3.

Сейчас в `ChatViewModel.buildChecklistsSummary()` (ChatViewModel.kt:314) формируется минимальный context:

```kotlin
ChecklistContext(
    name = checklist.name,
    totalItems = checklist.items.size,
    doneItems = checklist.items.count { it.checked },
)
```

Это privacy-by-design выбор — модель видит только **названия чеклистов и счётчики**, не item-text и не timestamps. На вопросы про «что добавлял», «найди задачу про X», «когда я делал Y» — у модели нет данных, и она честно отвечает «нет информации».

## Контекст

Privacy-by-design введён сознательно в Phase C (см. `docs/solutions/features/ai-chat-phase-c-full-2026-05-17.md`) — чтобы Cloud Function не получала сырой текст items. Решение прошло через explicit-design review.

## Что нужно сделать при возобновлении

- [ ] Решить product question: расширять ли context для Layer 3 / Deep Thinking?
  - Вариант A: per-item content только при включённом Deep Thinking (юзер опт-инит)
  - Вариант B: per-item content всегда (отказ от privacy-by-design)
  - Вариант C: расширить ChecklistContext только timestamps последних N items без текста (компромисс — модель сможет ответить «X items были добавлены в последние 24h», но не процитирует содержание)
  - Вариант D: оставить как есть, добавить hint в Layer 3 system prompt «у тебя нет доступа к item-text — отвечай шире»
- [ ] Если расширяем: обновить `ChecklistContext` (api), `buildChecklistsSummary` (ViewModel), Cloud Function `chat_completion` Python код (functions/) — добавить новые поля
- [ ] Обновить unit-тесты ChatViewModelTest для проверки нового контекста
- [ ] Обновить privacy disclaimer в Settings sheet или Pricing help sheet

## Как возобновить (для cold-start агента)

В feature/aichat есть 3-Layer AI Chat. Layer 3 (Deep Thinking / FreeForm) шлёт запрос в Cloud Function `chat_completion` (functions/main.py) с `messages` + `checklists_summary`. Сейчас checklists_summary содержит только {name, totalItems, doneItems} per checklist — НЕ item-text и НЕ timestamps. Пользователь хочет, чтобы AI мог отвечать на вопросы типа «что последнее добавлял». Главный архитектурный вопрос — расширять ли privacy boundary. См. варианты выше.

Read for context: `docs/solutions/features/ai-chat-phase-c-full-2026-05-17.md` (privacy-by-design rationale), `feature/aichat/impl/.../presentation/ChatViewModel.kt:314` (buildChecklistsSummary), `functions/main.py` (chat_completion endpoint).
