---
title: 5 тестов Cloud Functions падают на HEAD — ждут (response, status), получают Response
opened: 2026-08-03
type: tech-debt
area: firebase-functions / tests
severity: low
---

# Что

В `firebase-functions/test_main.py` пять тестов падают **на чистом `master`**, независимо от каких-либо правок:

- `TestRestoreCreditsRevenueCat::test_rejects_without_valid_subscription`
- `TestRestoreCreditsRevenueCat::test_returns_503_when_revenuecat_unavailable`
- `TestIsPremiumFromFirestore::test_is_premium_from_firestore_not_request`
- `TestReserveCredits::test_gemini_failure_refunds_reserved_credits`
- `TestUsageLimits::test_usage_limit_enforced_for_free_user`

Симптом одинаковый:

```
TypeError: cannot unpack non-iterable Response object
    response, status = main.analyze_and_fill_checklist(req)
```

Тесты распаковывают возврат хендлера в кортеж `(response, status)`, а хендлеры отдают готовый Flask `Response`. То есть разъехались тест и сигнатура — вероятно, хендлеры перевели на `create_error_response(...)`, а тесты не обновили.

## Как проверено

Прогон на чистом `HEAD` через `git stash` в ходе задачи 2026-08-03: те же пять падений и до, и после фикса `da410d8a` — то есть фикс их не вносил. Остальные 112 тестов зелёные.

## Почему это важно, но не срочно

Падающий набор обесценивает гейт: если `pytest` и так красный, никто не заметит **новое** падение. Именно на этот набор опирается `.github/workflows/functions-guard.yml`, который задуман как дешёвый гейт на каждый PR.

Не срочно, потому что покрываемая логика (RevenueCat-верификация, атомарный резерв кредитов, лимиты) в проде работает и проверялась вживую — сломан контракт **тестов**, а не поведение.

## Что сделать

Привести пять тестов к фактической сигнатуре хендлеров: читать `response.status_code` и `response.get_json()` вместо распаковки кортежа. Проверить заодно, нет ли других тестов с той же формой вызова, которые сейчас проходят случайно.

## Resume

Пользователь говорит «почини питон-тесты», «красный pytest в functions», «functions-guard падает» — либо при следующей правке `firebase-functions/main.py`, когда зелёный набор понадобится как гейт.
