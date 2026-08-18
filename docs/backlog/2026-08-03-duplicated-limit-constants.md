---
title: "Техдолг: одна и та же величина лимита живёт в 3-4 местах и расходится"
date: 2026-08-03
type: backlog
status: ready
severity: medium
effort: S
why_not_now: один экземпляр этого класса уже стрельнул и заведён отдельным багом; остальные пока совпадают по значению, поэтому чинятся планово, а не срочно
modules: [composeApp/commonMain/aichat, feature/home, feature/paywall, core/remoteconfig, firebase-functions]
keywords: [FREE_CHECKLIST_LIMIT, FREE_ATTACHMENT_LIMIT_PER_ITEM, max_attachments_per_item_free, RemoteConfig, GetUserLimitsUseCase, init.js, single-source-of-truth]
project: gisti-ai-checklists
---

# Лимиты продублированы хардкодами вместо чтения Remote Config

Найдено 2026-08-03 при сверке доков с кодом. Класс проблемы: **одна величина имеет несколько
источников**, синхронизируемых вручную через комментарий вида «mirrors X». Компилятор такие
комментарии не проверяет, поэтому при изменении оригинала копия молча остаётся старой.

## Уже стрельнуло (заведено отдельно)

`FREE_CHECKLIST_LIMIT = 4` в `ToolCallDispatcherImpl.kt:77` против RC-дефолта **5** →
AI-чат отказывает в 5-м чек-листе, который разрешает UI.
→ [`docs/todos/2026-08-03-aichat-free-checklist-limit-hardcoded-4.md`](../todos/2026-08-03-aichat-free-checklist-limit-hardcoded-4.md)

## Ещё не стрельнуло, но механизм тот же

| Величина | Источник истины | Копии | Статус |
|---|---|---|---|
| Вложений на пункт | RC `max_attachments_per_item_free` = 3 (`RemoteConfigKeys.kt:86`) | `FREE_ATTACHMENT_LIMIT_PER_ITEM = 3` (`ChecklistDetailViewModel.kt:3588`) · `FREE_ATTACH_LIMIT_PER_ITEM = 3` (`ToolCallDispatcherImpl.kt:80`) | значения совпадают — **пока** |
| Чек-листов Free | RC `max_checklists_free` = 5 (`:82`) | комментарий `MAX_CHECKLISTS_FREE=4` (`PaywallScreen.kt:572`) · `init.js` веба = 4 | комментарий и веб-слой уже разошлись |
| Дневной лимит Premium | клиент `ai_daily_limit_premium` = 300 (`:76`) | сервер `DEFAULT_DAILY_LIMIT_PREMIUM = 100` (`main.py:292`) · `DEFAULT_PREMIUM_DAILY_CREDITS_CAP = 300` (`main.py:297`) | 🔴 **три значения**, требует решения владельца — какое верно |

Отдельно: RC-ключи `max_weekly_checklists_free`, `max_attachments_per_item_free` и
`first_checklist_variant` **отсутствуют** в `FirebaseRemoteConfigProvider.getDefaultsMap()`
(`:46-64`, там 16 ключей из 20) — они резолвятся только через `defaultValue` в точке вызова, то
есть SDK-дефолта не имеют.

## Что сделать

1. Убрать хардкоды, читать лимиты через `GetUserLimitsUseCase`
   (`feature/paywall/.../GetUserLimitsUseCase.kt:22-65`) — тем же путём, что UI.
2. Довести `getDefaultsMap()` до полного набора ключей.
3. Свести `init.js` (4-й слой конфига на вебе) к общим дефолтам — сейчас он держит свой
   `max_checklists_free=4` и `onboarding="interactive"`, и при провале `fetchAndActivate` тихо
   схлопывает A/B на вебе.
4. Разрешить развилку premium-daily 100 vs 300 — **вопрос продуктовый**, не технический.

## Как поймать следующий такой

Комментарий «mirrors X» / «duplicates X» / «keep in sync with X» рядом с константой — маркер
этого класса. Дешёвая проверка: `grep -rn "mirrors\|keep in sync" --include=*.kt` и сверка каждой
находки с её оригиналом.
