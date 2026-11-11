---
status: deferred
blocking_reason: решение владельца — пока пополнен prepay-баланс Gemini API в AI Studio; переход на Vertex AI ждёт его решения и следующего пополнения
resume_trigger: User says «переводим Gemini на Vertex / уходим с prepay» OR в логах Cloud Run снова `402 RESOURCE_EXHAUSTED ... prepayment credits are depleted`
keywords: [gemini, prepay, 402, RESOURCE_EXHAUSTED, vertex ai, billing, auto-reload, alert, ai studio]
---

# Gemini API на prepay — убрать риск молчаливого простоя AI

## Что случилось

С 2026-11-04 05:47 UTC до пополнения 2026-11-11 весь серверный AI отвечал клиенту 500 («Agent step failed»):
`chat_agent`, `classify_chat_intent` и остальные функции на `GEMINI_API_KEY` получали от Gemini
`402 RESOURCE_EXHAUSTED — Your prepayment credits are depleted` (источник: логи Cloud Run `chat-agent`, 2026-11-11).

Причина — не код. С 2026-03-23 Gemini API в AI Studio работает в двух режимах оплаты (`aistudio.google.com/docs/billing`):
- **Prepay** — кредиты покупаются заранее; привязанный Cloud Billing **не** подхватывает расход.
  Баланс 0 → *«all API keys in all projects linked to that billing account will stop working
  simultaneously. Requests then fail with an HTTP 402»*. Новые аккаунты попадают сюда по умолчанию.
- **Postpay** — расход идёт на Cloud Billing, оплата в конце месяца.

Проект `aichecklists-40230` на prepay. Алерта нет — простой длился неделю.

После пополнения 2026-11-11 smoke-тест (throwaway-пользователь): `classify_chat_intent` EN и RU → 200,
`chat_agent` RU → 200, модель `gemini-3.5-flash-lite`.

## Варианты

1. **Auto-reload в AI Studio** (минимум усилий, без кода). Billing → Auto reload: порог баланса,
   сумма пополнения, *Monthly auto-charge limit*. Ведёт себя почти как postpay.
2. **Postpay в AI Studio.** Документация противоречит себе: FAQ — переход не поддерживается;
   раздел Prepay — ручной апгрейд «после выполнения критериев» (критерии не раскрыты). Смотреть на
   странице Billing аккаунта.
3. **Vertex AI** — тот же Gemini через Google Cloud, оплата обычным Cloud Billing (postpay), без
   API-ключа. Цена `gemini-3.5-flash-lite` по агрегаторам совпадает с Gemini API ($0.30 / $2.50 за 1M
   input / output) (не проверено по официальному прайсу — сверить на
   `cloud.google.com/vertex-ai/generative-ai/pricing` перед переходом).

## Объём перехода на Vertex AI (вариант 3)

- Клиент создаётся в одном месте — `firebase-functions/main.py:65-66`
  (`genai.Client(api_key=GEMINI_API_KEY)`) → `genai.Client(vertexai=True, project=..., location=...)`.
  Вызовы `generate_content` не меняются.
- Включить `aiplatform.googleapis.com`; выдать runtime SA функций `roles/aiplatform.user`.
- Регион: проверить доступность 3.5 Flash-Lite (и 3.1 Flash-Lite — RC-откат) в регионе, при
  необходимости `global`.
- Сверить на Vertex `thinking_level` (3.5 Flash-Lite даёт 400 на `thinking_budget`) и каждую модель
  из `MODEL_OVERRIDE_ALLOWLIST`.
- Голосовой звонок (`feat/voice-call-agent`), если идёт через Live API, — свои имена моделей и endpoint.
- Smoke всех AI-функций (`/test-firebase-function`), секрет `gemini-api-key` оставить как путь отката.

## Независимо от варианта

- **Алерт.** Риск переезжает, а не исчезает: на postpay AI ляжет, если отвалится сам Cloud Billing
  (простой 26 ч в августе 2026). Нужны budget alert в Cloud Billing и алерт Cloud Monitoring на
  всплеск 5xx у `chat-agent` / `classify-chat-intent`.
- **Честная ошибка клиенту.** Сервер сейчас превращает 402 от Gemini в общий 500 — снаружи
  неотличимо от бага деплоя. Отдельный код/текст «AI временно недоступен».
