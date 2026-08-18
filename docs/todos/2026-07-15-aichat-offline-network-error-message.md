---
title: "AI Chat — honest offline/network error message after the Layer 1 disconnect"
date: 2026-07-15
type: todo
status: deferred
modules: [feature/aichat/impl, core/designsystem]
keywords: [ai-chat, offline, network-error, chat_completion_error, layer1, feedback, strings]
project: gisti-ai-checklists
blocking_reason: none — owner deliberately deferred (scope call, 2026-07-15)
resume_trigger: «честный офлайн-текст в чате» / «chat network error»
---

# AI Chat — честное «нет сети» вместо «AI couldn't respond»

**Отложено владельцем 2026-07-15**, осознанно, при отключении Layer 1
(ADR `docs/decisions/2026-07-15-remove-ai-chat-layer1.md`). Не баг из ниоткуда — прямое
следствие того изменения, зафиксированное сразу, чтобы не всплыло как «непонятная жалоба».

## Что происходит сейчас

До отключения L1 чат работал офлайн: локальный парсер исполнял «добавь молоко» без сети, а
`NetworkError`/`ServiceError` на Layer 2 деградировали на L1-результат
(`AiChatRepositoryImpl.kt:151-158`, до изменения).

После отключения любой запрос идёт в сеть. Без интернета путь такой:
Layer 2 `NetworkError` → эскалация в Layer 3 (FreeForm) → `agentStep` → снова сетевая ошибка →
`ChatViewModel` показывает `chat_completion_error` = **«AI couldn't respond. Please try again.»**

Отклик есть (правило «Обратная связь на каждый user action» формально соблюдено — не тишина и
не вводящее в заблуждение «не понял тебя»), но текст **винит AI вместо связи**.

## Почему это стоит починить

- **Офлайн из «работает» стал «не работает»** — это новый частый сценарий, а не редкий край.
- Текст провоцирует бесполезный retry: пользователь в метро/самолёте жмёт «попробовать снова»,
  каждый ретрай — новый сетевой вызов, а при восстановлении связи ещё и расход кредита.
- «AI couldn't respond» уводит диагностику в ложную сторону: пользователь думает, что сломан
  AI (и пишет об этом в отзыв), а сломан вообще-то Wi-Fi.

## Что сделать

1. Разделить `NetworkError` и `ServiceError` в `ChatViewModel.kt:2314` (сейчас схлопнуты в один
   `chat_completion_error`).
2. Новая строка `chat_network_error` в `core/designsystem` `strings.xml`, **EN + RU**.
   Формулировка по правилам проекта: причина + что делать («No connection. Check your internet
   and try again.»). ⚠️ Только через `stringResource`/`getString` — никаких литералов в Kotlin
   (recurring bug 2026-06-07); апострофы в strings.xml писать **литерально** (`can't`, не `can\'t`).
3. Смапить новый ключ в двух местах: `ChatRoute.kt:248` и `App.kt:826`.

Скоуп ~4 файла. Тесты: green-покрытие (это feature work, не плохой ответ — red-first не нужен,
правило `.claude/rules/ai-chat.md`).

## Ссылки

- ADR отключения L1: `docs/decisions/2026-07-15-remove-ai-chat-layer1.md`
- Umbrella-план (этап 2): `docs/active/ai-chat-rework-3-stages-2026-07-15.md`
