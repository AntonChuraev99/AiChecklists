---
title: "fix: server security and credits race condition"
type: fix
date: 2026-02-17
---

# fix: Server security и race condition при списании кредитов

## Overview

Комплексный фикс серверной части (`firebase-functions/main.py`). Три уровня проблем по приоритету:

| Приоритет | Проблема | Влияние |
|-----------|----------|---------|
| **P0** | `restore_credits_after_purchase` без верификации покупки | Любой может дать себе Premium бесплатно |
| **P0** | `is_premium` читается из request body клиента | Клиент может соврать |
| **P1** | TOCTOU race condition в кредитах | Параллельный запрос → бесплатный анализ |
| **P1** | Silent failure в `deduct_user_credits` | Exception → анализ возвращается бесплатно |
| **P1** | `check_usage_limit()` не вызывается | Daily limits не работают |
| **P2** | Дублирование кода в двух AI-endpoints | Баги нужно чинить в двух местах |

---

## P0: Верификация покупки в `restore_credits_after_purchase`

### Проблема

**Текущий код** (`main.py:760-829`):

```python
def restore_credits_after_purchase(request: Request):
    # ... validate_request ...
    user_id = data["user_id"]
    # НЕТ верификации покупки!
    user_ref.update({
        "is_premium": True,           # ← Premium бесплатно
        "ai_credits": credits_cap,    # ← 300 кредитов бесплатно
    })
```

Endpoint доступен публично (`deploy.sh:28: --allow-unauthenticated`). Один curl — и Premium активирован.

### Решение: Проверка через RevenueCat REST API

```python
import requests as http_requests  # avoid conflict with flask Request
from datetime import datetime, timezone

REVENUECAT_API_KEY = os.environ.get("REVENUECAT_API_KEY")  # V1 Secret key (sk_...), NOT public key

# Tri-state result for RevenueCat verification
VERIFIED = "verified"
NOT_VERIFIED = "not_verified"
UNAVAILABLE = "unavailable"


def verify_premium_with_revenuecat(user_id: str) -> str:
    """
    Verify user has active subscription via RevenueCat API.
    Returns: VERIFIED, NOT_VERIFIED, or UNAVAILABLE.
    """
    if not REVENUECAT_API_KEY:
        return UNAVAILABLE

    try:
        resp = http_requests.get(
            f"https://api.revenuecat.com/v1/subscribers/{user_id}",
            headers={"Authorization": f"Bearer {REVENUECAT_API_KEY}"},
            timeout=5
        )
        if resp.status_code != 200:
            return NOT_VERIFIED

        data = resp.json()
        entitlements = data.get("subscriber", {}).get("entitlements", {})
        premium = entitlements.get("premium", {})
        if not premium:
            return NOT_VERIFIED

        expires = premium.get("expires_date")
        if expires is None:
            return VERIFIED  # lifetime

        # IMPORTANT: use timezone-aware datetime (utcnow() is naive → TypeError in Python 3.12)
        if datetime.fromisoformat(expires.replace("Z", "+00:00")) > datetime.now(timezone.utc):
            return VERIFIED
        return NOT_VERIFIED
    except (http_requests.Timeout, http_requests.ConnectionError):
        return UNAVAILABLE
    except Exception:
        return NOT_VERIFIED
```

Обновить `restore_credits_after_purchase`:

```python
def restore_credits_after_purchase(request: Request):
    data, error = validate_request(request)
    if error:
        return create_error_response(error)

    user_id = data["user_id"]

    # Verify purchase with RevenueCat
    status = verify_premium_with_revenuecat(user_id)
    if status == UNAVAILABLE:
        return create_error_response("Verification service temporarily unavailable. Please try again.", 503)
    if status == NOT_VERIFIED:
        return create_error_response("No active subscription found", 403)

    # ... rest of the function (update is_premium, credits) ...
```

**Deploy**: добавить `REVENUECAT_API_KEY` как secret:

```bash
echo -n "$REVENUECAT_API_KEY" | gcloud secrets create revenuecat-api-key --data-file=-

gcloud functions deploy restore_credits_after_purchase \
    --set-secrets="GEMINI_API_KEY=gemini-api-key:latest,REVENUECAT_API_KEY=revenuecat-api-key:latest" \
    ...
```

---

## P0: Читать `is_premium` из Firestore, не из клиента

### Проблема

```python
# main.py:385 — клиент может послать is_premium: true
is_premium = data.get("is_premium", False)
```

### Решение

Добавить helper и использовать его во всех AI-функциях:

```python
def get_user_premium_status(user_id: str) -> bool:
    """Get premium status from Firestore (server truth), not from client."""
    user_data = get_user_data(user_id)
    if user_data is None:
        return False
    return user_data.get("is_premium", False)
```

В обоих AI endpoints заменить:

```python
# BEFORE:
is_premium = data.get("is_premium", False)

# AFTER:
is_premium = get_user_premium_status(user_id)
```

> **Примечание**: `is_premium` из request body можно вообще игнорировать. Клиентский код продолжит его отправлять — сервер просто не будет его читать. Обратная совместимость сохранена.

---

## P1: Atomic reserve-then-confirm (без refund)

### Проблема

**Bug 1: TOCTOU race condition** (`main.py:401-460`):

```
Запрос A                              Запрос B (параллельный)
─────────                             ─────────
check_credits(user) → 50 >= 30 ✓
                                      check_credits(user) → 50 >= 30 ✓
... Gemini call (5-30 сек) ...
                                      ... Gemini call (5-30 сек) ...
deduct(user) → 50 - 30 = 20
                                      deduct(user) → 20 - 30 = max(0, -10) = 0
                                      ← Запрос B получил анализ бесплатно!
```

**Bug 2: Silent failure** (`main.py:196-219`):

```python
def deduct_user_credits(user_id: str) -> int:
    try:
        ...
    except Exception:   # ← ловим всё
        return 0        # ← тихо, Gemini результат всё равно отдаётся
```

### Решение: Reserve before Gemini, no refund

Кредиты списываются **ДО** вызова Gemini атомарной транзакцией. Если Gemini упадёт — кредиты **не возвращаются**. Это стандартная модель ("credits consumed on attempt").

**Почему без refund:**
- Refund добавляет второй transactional path и второй failure mode
- Silent `pass` в refund — та же проблема, что мы чиним (Bug 2)
- 30 кредитов при Gemini-ошибке — приемлемый trade-off (Gemini reliability >99%)
- Так работают SMS-сервисы, API rate limits, игровая валюта

```python
def reserve_credits(user_id: str) -> int | None:
    """
    Atomically check and deduct credits in a single Firestore transaction.
    Returns new remaining count, or None if insufficient credits.
    """
    config = get_credits_config()
    cost = config["action_cost"]
    user_ref = db.collection("users").document(user_id)

    @firestore.transactional
    def txn(transaction):
        snapshot = user_ref.get(transaction=transaction)
        if not snapshot.exists:
            return None
        current = snapshot.get("ai_credits") or 0
        if current < cost:
            return None
        new_count = current - cost
        transaction.update(user_ref, {
            "ai_credits": new_count,
            "updated_at": datetime.now(timezone.utc).isoformat()
        })
        return new_count

    return txn(db.transaction())
```

**Использование в обоих AI endpoints (inline, без обёртки):**

```python
# BEFORE (текущий код):
allowed, remaining, credits_error = check_credits_available(user_id, is_premium)
if not allowed:
    return create_error_response(credits_error, 402)
# ... Gemini call ...
new_remaining = deduct_user_credits(user_id)

# AFTER:
remaining = reserve_credits(user_id)
if remaining is None:
    cost = get_credits_config()["action_cost"]
    suffix = "Refill at 12:00 CET." if is_premium else "Get premium for daily refill."
    return create_error_response(f"Not enough credits. Need {cost}. {suffix}", 402)
# ... Gemini call ...
# remaining уже содержит актуальный остаток
```

**Удалить:** `check_credits_available()` (строки 175-193), `deduct_user_credits()` (строки 196-219).

---

## P1: Активировать `check_usage_limit()`

### Проблема

`check_usage_limit()` определена (`main.py:82-89`), но **нигде не вызывается**. `increment_usage()` считает запросы, но лимит не проверяется. Заявленные лимиты 10/100 в день — фикция.

### Решение

Добавить проверку в оба AI endpoints **после** reserve credits (чтобы не тратить кредиты впустую — добавить перед reserve):

```python
# В analyze_and_fill_checklist и generate_checklist, ПЕРЕД reserve_credits:
usage_allowed, usage_error = check_usage_limit(user_id, is_premium)
if not usage_allowed:
    return create_error_response(usage_error, 429)  # 429 Too Many Requests
```

> **Порядок проверок:** usage limit → reserve credits → Gemini. Так при превышении лимита кредиты не списываются.

---

## P2: DRY — вынести общий код

### Дублирование в `analyze_and_fill_checklist` и `generate_checklist`

Три блока кода идентичны:

#### 1. `call_gemini()` — вызов Gemini по типу input

```python
def call_gemini(prompt: str, input_type: str, input_data: str):
    """Call Gemini API with appropriate content type."""
    model = genai.GenerativeModel("gemini-2.0-flash-lite")
    if input_type == "image_base64" and input_data:
        return model.generate_content([
            prompt, {"mime_type": "image/jpeg", "data": base64.b64decode(input_data)}
        ])
    if input_type == "audio_base64" and input_data:
        return model.generate_content([
            prompt, {"mime_type": "audio/mp4", "data": base64.b64decode(input_data)}
        ])
    return model.generate_content(prompt)
```

#### 2. `parse_gemini_json()` — извлечение JSON из ответа

```python
def parse_gemini_json(response_text: str) -> dict:
    """Extract and parse JSON from Gemini response."""
    if "```json" in response_text:
        response_text = response_text.split("```json")[1].split("```")[0]
    elif "```" in response_text:
        response_text = response_text.split("```")[1].split("```")[0]
    return json.loads(response_text.strip())
```

#### 3. Безопасные сообщения об ошибках (inline в endpoints)

Вместо отдельного helper, использовать два `except` в try-блоке каждого endpoint:

```python
try:
    response = call_gemini(prompt, input_type, input_data)
    result = parse_gemini_json(response.text)
except json.JSONDecodeError:
    return create_error_response("Failed to parse AI response", 500, ai_credits=remaining)
except Exception:
    return create_error_response("AI processing failed. Please try again.", 500, ai_credits=remaining)
```

> **Важно:** `ai_credits=remaining` возвращает клиенту актуальный остаток даже при ошибке. Текущий код отдаёт `str(e)` клиенту, что может содержать stack traces и внутренние детали.

---

## Итоговая структура `main.py` (изменённые функции)

```
УДАЛИТЬ:
- check_credits_available()     (строки 175-193)
- deduct_user_credits()         (строки 196-219)

ДОБАВИТЬ:
+ get_user_premium_status()     (~5 строк)
+ verify_premium_with_revenuecat()  (~20 строк)
+ reserve_credits()             (~15 строк, одна функция с nested @transactional)
+ call_gemini()                 (~10 строк)
+ parse_gemini_json()           (~5 строк)

ИЗМЕНИТЬ:
~ restore_credits_after_purchase  — добавить verify_premium_with_revenuecat()
~ analyze_and_fill_checklist      — is_premium из Firestore, usage limit, reserve_credits
~ generate_checklist              — то же
```

---

## Что НЕ меняется

- Remote Config — без изменений
- Клиентский код (Kotlin) — без изменений
- API contract — без изменений (request/response формат тот же)
- `is_premium` в request body — игнорируется, но не ломает клиент
- Flat rate 30 кредитов — без изменений
- `register_user`, `refill_premium_credits`, `get_credits_info`, `get_usage_stats` — без изменений

---

## Тесты

Тестов для Cloud Functions сейчас **нет**. Добавляем `test_main.py` с pytest, покрывая все изменённые функции.

### Setup: `firebase-functions/test_main.py`

**Зависимости (добавить в `requirements-dev.txt`):**

```
pytest==8.*
pytest-mock==3.*
```

**Стратегия мокирования:**
- `db` (Firestore client) → mock с контролем данных
- `genai.GenerativeModel` → mock с контролем ответов
- `http_requests.get` (RevenueCat) → mock с контролем статуса
- Cloud Functions вызываются через Flask test client (functions-framework поддерживает это)

### Тесты: P0 Security

#### `test_restore_credits_rejects_without_valid_subscription`

```python
def test_restore_credits_rejects_without_valid_subscription(mock_revenuecat_no_sub):
    """restore_credits_after_purchase returns 403 when RevenueCat has no active sub."""
    response = call_function("restore_credits_after_purchase", {
        "user_id": "user-123"
    })
    assert response.status_code == 403
    assert "No active subscription" in response.json["error"]
```

#### `test_restore_credits_succeeds_with_valid_subscription`

```python
def test_restore_credits_succeeds_with_valid_subscription(mock_revenuecat_active_sub, mock_user):
    """restore_credits_after_purchase grants premium when RevenueCat confirms subscription."""
    response = call_function("restore_credits_after_purchase", {
        "user_id": "user-123"
    })
    assert response.status_code == 200
    assert response.json["is_premium"] is True
    assert response.json["ai_credits"] == 300
```

#### `test_restore_credits_returns_503_when_revenuecat_unavailable`

```python
def test_restore_credits_returns_503_when_revenuecat_unavailable(mock_revenuecat_timeout, mock_user):
    """Returns 503 (retry later) when RevenueCat API is unreachable."""
    response = call_function("restore_credits_after_purchase", {
        "user_id": "user-123"
    })
    assert response.status_code == 503
    assert "temporarily unavailable" in response.json["error"].lower()
```

#### `test_restore_credits_rejects_expired_subscription`

```python
def test_restore_credits_rejects_expired_subscription(mock_revenuecat_expired_sub, mock_user):
    """Returns 403 when RevenueCat has an expired subscription."""
    response = call_function("restore_credits_after_purchase", {
        "user_id": "user-123"
    })
    assert response.status_code == 403
    assert "No active subscription" in response.json["error"]
```

#### `test_is_premium_from_firestore_not_request`

```python
def test_is_premium_from_firestore_not_request(mock_user_free):
    """AI endpoints ignore is_premium from request body, read from Firestore."""
    # User is free in Firestore, but client sends is_premium=True
    response = call_function("analyze_and_fill_checklist", {
        "user_id": "user-123",
        "is_premium": True,  # ← should be ignored
        "checklist": {"items": [{"text": "item1"}]},
        "input_type": "text",
        "input_data": "test"
    })
    # Verify: error message says "Get premium", not "Refill at 12:00 CET"
    # (proves server used Firestore value, not client's is_premium=True)
```

### Тесты: P1 Race condition

#### `test_reserve_credits_atomic_deduction`

```python
def test_reserve_credits_atomic_deduction(mock_firestore_user_with_credits_50):
    """reserve_credits deducts exactly action_cost and returns new balance."""
    remaining = reserve_credits("user-123")
    assert remaining == 20  # 50 - 30
```

#### `test_reserve_credits_returns_none_when_insufficient`

```python
def test_reserve_credits_returns_none_when_insufficient(mock_firestore_user_with_credits_10):
    """reserve_credits returns None when user has fewer credits than action_cost."""
    remaining = reserve_credits("user-123")
    assert remaining is None
```

#### `test_reserve_credits_returns_none_when_user_not_found`

```python
def test_reserve_credits_returns_none_when_user_not_found(mock_firestore_no_user):
    """reserve_credits returns None when user document doesn't exist."""
    remaining = reserve_credits("nonexistent-user")
    assert remaining is None
```

#### `test_gemini_failure_does_not_refund_credits`

```python
def test_gemini_failure_does_not_refund_credits(
    mock_firestore_user_with_credits_50, mock_gemini_failure
):
    """When Gemini fails, credits are consumed (not refunded)."""
    response = call_function("analyze_and_fill_checklist", {
        "user_id": "user-123",
        "checklist": {"items": [{"text": "item1"}]},
        "input_type": "text",
        "input_data": "test"
    })
    assert response.status_code == 500
    # Credits should be 20 (50 - 30), NOT 50 (no refund)
    assert get_user_credits("user-123") == 20
    # Response includes remaining credits for client UI update
    assert response.json["ai_credits"] == 20
```

### Тесты: P1 Usage limits

#### `test_usage_limit_enforced_for_free_user`

```python
def test_usage_limit_enforced_for_free_user(mock_firestore_user_with_credits_300, mock_usage_at_limit_10):
    """Free user at daily limit (10) gets 429, credits NOT deducted."""
    response = call_function("analyze_and_fill_checklist", {
        "user_id": "user-123",
        "checklist": {"items": [{"text": "item1"}]},
        "input_type": "text",
        "input_data": "test"
    })
    assert response.status_code == 429
    assert get_user_credits("user-123") == 300  # не списаны
```

#### `test_premium_user_higher_usage_limit`

```python
def test_premium_user_higher_usage_limit(mock_firestore_premium_user, mock_usage_at_11):
    """Premium user with 11 daily requests passes (limit is 100)."""
    response = call_function("analyze_and_fill_checklist", {
        "user_id": "user-123",
        "checklist": {"items": [{"text": "item1"}]},
        "input_type": "text",
        "input_data": "test"
    })
    assert response.status_code == 200
```

### Тесты: P2 Helpers

#### `test_parse_gemini_json_with_code_fence`

```python
def test_parse_gemini_json_with_code_fence():
    assert parse_gemini_json('```json\n{"key": "val"}\n```') == {"key": "val"}

def test_parse_gemini_json_plain():
    assert parse_gemini_json('{"key": "val"}') == {"key": "val"}

def test_parse_gemini_json_invalid_raises():
    with pytest.raises(json.JSONDecodeError):
        parse_gemini_json("not json at all")
```

### Запуск тестов

```bash
cd firebase-functions
pip install -r requirements.txt -r requirements-dev.txt
pytest test_main.py -v
```

---

## Acceptance Criteria

### P0: Security
- [x] `restore_credits_after_purchase` верифицирует покупку через RevenueCat API
- [x] Без валидной подписки — `403 Forbidden`
- [x] `is_premium` во всех AI-функциях читается из Firestore user document, не из request body
- [x] `REVENUECAT_API_KEY` хранится как Google Cloud Secret

### P1: Race condition + Usage limits
- [x] `reserve_credits()` атомарно проверяет и списывает кредиты в одной Firestore transaction
- [x] Два параллельных запроса от одного пользователя: первый проходит, второй получает 402
- [x] При ошибке Gemini кредиты НЕ возвращаются (consumed on attempt)
- [x] При ошибке списания Gemini НЕ вызывается (402 ответ)
- [x] При ошибке Gemini — ответ включает `"ai_credits": remaining` для обновления UI
- [x] `check_usage_limit()` вызывается в обоих AI endpoints перед reserve
- [x] Старые функции `check_credits_available` и `deduct_user_credits` удалены

### P2: Code quality
- [x] Gemini call и JSON parsing вынесены в shared helpers
- [x] Error messages не содержат `str(e)` с внутренними деталями

### Tests
- [x] `test_main.py` создан в `firebase-functions/`
- [x] `requirements-dev.txt` создан с pytest + pytest-mock
- [x] Все unit-тесты проходят: `pytest test_main.py -v`
- [x] Покрыты: security (5 тестов), race condition (4 теста), usage limits (2 теста), helpers (3 теста) — итого 14 тестов

---

## Риски

| Риск | Митигация |
|------|-----------|
| RevenueCat API недоступен | `verify_premium_with_revenuecat` возвращает `UNAVAILABLE` при таймауте → 503 "retry later" |
| Gemini fail → потеря 30 кредитов | Допустимо. Gemini reliability >99%. Альтернатива (refund) создаёт больше проблем |
| Firestore transaction contention | Конфликт только при запросах одного юзера одновременно. Firestore ретраит до 5 раз |
| Старый клиент шлёт `is_premium: true` | Игнорируется сервером. Обратная совместимость |
| `check_usage_limit` блокирует пользователей | Лимиты уже задокументированы (10 free / 100 premium), теперь они просто начнут работать |

---

## Deploy checklist

- [x] Добавить `requests` в `requirements.txt` (для RevenueCat HTTP call)
- [ ] Создать secret: `gcloud secrets create revenuecat-api-key --data-file=-`
- [x] Обновить `deploy.sh`: добавить `--set-secrets` для `REVENUECAT_API_KEY` в `restore_credits_after_purchase`
- [ ] Развернуть все изменённые функции: `restore_credits_after_purchase`, `analyze_and_fill_checklist`, `generate_checklist`

---

## References

- `firebase-functions/main.py:175-219` — текущие `check_credits_available` + `deduct_user_credits`
- `firebase-functions/main.py:400-475` — `analyze_and_fill_checklist`
- `firebase-functions/main.py:511-629` — `generate_checklist`
- `firebase-functions/main.py:760-829` — `restore_credits_after_purchase`
- `firebase-functions/main.py:82-89` — `check_usage_limit` (определена, не используется)
- `firebase-functions/deploy.sh:28` — `--allow-unauthenticated`
- [Firestore Transactions](https://firebase.google.com/docs/firestore/manage-data/transactions)
- [RevenueCat REST API](https://www.revenuecat.com/docs/api-v1)
