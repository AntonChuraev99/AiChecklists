---
title: "Клиент: на 402 с reason=no_user показывать вход, а не пэйвол"
date: 2026-08-18
status: open
project: Checklists
complexity: Small
impact: Medium
blocking_reason: "needs-server-deploy — серверная половина написана в ветке worktree-analytics-process-guard, но поле `reason` появится в проде только после деплоя Cloud Functions. До деплоя клиентскую ветку не на чем проверить."
resume_trigger: "После деплоя Cloud Functions из главного checkout (ветка healthcheck-фиксов 2026-08-18 влита в master); ИЛИ пользователь говорит «доделай no_user на клиенте / почему незарегистрированному показывают пэйвол»"
keywords: [aichat, paywall, 402, no_user, credits, sign-in, cloud-functions]
---

# Клиент: 402 с `reason=no_user` → вход, а не пэйвол

⚠️ **Состояние на момент записи:** серверная половина НЕ задеплоена. В проде на 2026-08-18 крутится
1.20.0 (vc82), которая этих правок не несёт — они лежат в ветке и ждут merge. Ни один из критериев
ниже проверить сейчас нельзя.

## Что уже сделано (серверная половина, ветка `worktree-analytics-process-guard`)

`reserve_chat_credit` и `reserve_chat_completion_credits` возвращают `(action, value)` в том же
словаре вердиктов, что и `reserve_credits_with_action`: `("reserve", balance)` | `("insufficient", None)` |
`("no_user", None)`. Все call-site отвечают через `credit_error_response(action, message)`, который
кладёт в тело машиночитаемое поле:

```json
{"success": false, "error": "<legacy-текст флоу>", "reason": "no_user" | "insufficient_credits"}
```

Покрыты и чат (`classify_chat_intent`, `transcribe_audio`, `chat_completion`, `chat_agent`), и
analyze/generate — у последних другой legacy-текст (`"Not enough credits. Need N. …"`), поэтому он
передаётся параметром и остаётся дословным.

HTTP-код **402 и строка `error` намеренно не менялись** — основная масса базы на 1.17.x/1.18.x
branch'ится ровно на них, чтобы поднять пэйвол. Поле `reason` аддитивно: старые клиенты его игнорируют.
Тесты: `firebase-functions/tests/test_credit_error_reason.py` (9 кейсов, доказаны мутационной матрицей —
три ортогональные мутации уронили ровно предсказанные 4 теста).

## Что осталось сделать на клиенте

Сейчас **любой** 402 идёт через `emitInsufficientCredits(layer)` в `ChatViewModel` — а его контракт
(правило `.claude/rules/ai-chat.md`) обязывает сказать «кошелёк пуст» и увести на пэйвол через
`NavigateToPaywall(source = SOURCE_INSUFFICIENT_CREDITS)`. Для `reason = "no_user"` это неверный исход:
у человека не кончились кредиты — у него вообще нет аккаунта, и пэйвол ему бесполезен.

Нужно:

1. Прочитать `reason` из тела 402 в HTTP-слое чата (там же, где сейчас распознаётся 402).
2. Развести два исхода:
   - `insufficient_credits` (и **любое неизвестное или отсутствующее значение**) → текущее поведение,
     `emitInsufficientCredits`. Умолчание именно такое: `no_user` — ветка, гасящая пэйвол, и ошибиться
     в её сторону дороже.
   - `no_user` → предложить вход в Google-аккаунт вместо пэйвола.
3. ⚠️ **Правило «каждый 402 через `emitInsufficientCredits`» придётся уточнить, а не обойти.** Оно
   существует, чтобы call-site не заводили собственные диалекты «нет кредитов». Новый исход — не диалект,
   а второй вердикт; заводить его надо тем же способом: одним общим helper'ом, а не веткой на call-site.
   Грепнуть `InsufficientCredits` и проверить ВСЕ сиблинги — этот баг ровно так и прятался раньше.
4. Строка приглашения ко входу — только через `core/designsystem` `strings.xml`, апострофы литерально
   (`can't`, не `can\'t`). Маппинг текстов ошибок чата живёт в **двух** местах и обе поверхности надо
   тронуть: `ChatRoute.kt` (карта рядом с `chat_error_service`) и `App.kt` (своя копия той же карты).
   Номера строк намеренно не указаны — они уже разъезжались; ищи по ключу `chat_error_service`.

## Как понять, что сработало

- В логах Cloud Functions строка `credit reservation refused: reason=no_user` отделена от
  `reason=insufficient_credits` (до фикса они были неразличимы вовсе).
- Счётчик 402 перестаёт быть непригодным как сигнал монетизации: доля `no_user` считается отдельно.
  Прецедент, из-за которого это всплыло — оба 402 в окне healthcheck 2026-08-18 пришли с анонимных
  веб-сессий, то есть ровно из популяции, где вероятен `no_user`.
- На сборке с клиентской половиной анонимная веб-сессия в чате получает приглашение войти, а не пэйвол.
