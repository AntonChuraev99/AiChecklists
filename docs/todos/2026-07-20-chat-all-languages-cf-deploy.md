# Deferred: deploy Cloud Functions for Chat All-Languages support

**Status:** resolved (2026-07-20)
**Дата:** 2026-07-20
**Source:** `docs/completed/chat-all-languages-support-2026-07-20.md` (Deferred Work)
**Impact:** High — feature merged to master, now **LIVE in prod**

## ✅ Resolved 2026-07-20

Деплой выполнен под `churaevanton@gmail.com` (gcloud config `gisti`), обе функции ACTIVE:
- **chat_agent** → revision `chat-agent-00018-zoc`, оба секрета сохранены (`GEMINI_API_KEY` + `MODEL_OVERRIDE_TEST_SECRET`, через `--update-secrets`).
- **chat_completion** → ACTIVE (`GEMINI_API_KEY`).
- Создан `firebase-functions/.gcloudignore` — иначе gcloud fallback на `.gitignore` исключил бы gitignored `prompts_private.py` → stub-промпты.

**Smoke-test (throwaway user, оба эндпоинта) — все зелёные:**
| endpoint | режим | сообщение | ответ |
|---|---|---|---|
| chat_completion | `es` | англ. | испанский ✓ |
| chat_completion | `hi` | англ. | хинди (деванагари) ✓ |
| chat_completion | Auto | франц. | французский ✓ |
| chat_agent (LIVE) | `es` | англ. | испанский ✓ |
| chat_agent (LIVE) | Auto | нем. | немецкий ✓ |

prompts_private.py задеплоился корректно (реальный промпт Gisti на всех языках, не stub). Фича работает end-to-end.

---
## (исходный scope — выполнен)

Код влит в master (merge `9254be4b`), клиент+сервер-тесты зелёные. Фича **не работала в проде**, пока не задеплоены Cloud Functions с обработкой `response_language`.

### 1. Deploy Cloud Functions
- Функции: `chat_agent` (LIVE path) + `chat_completion` (dead code, но пропатчена для консистентности).
- ⚠️ **Blocker:** `firebase-functions/prompts_private.py` ДОЛЖЕН существовать локально при `deploy` (gitignored IP-промпты). Иначе задеплоятся stub-промпты → AI ломается.
- Команда: `gcloud functions deploy chat_agent --region us-central1 --set-secrets="GEMINI_API_KEY=gemini-api-key:latest"` (повторить для `chat_completion`).
  - ⚠️ `--set-secrets` перезаписывает весь список секретов — указать ВСЕ нужные (`GEMINI_API_KEY` + `MODEL_OVERRIDE_TEST_SECRET`).
- **Порядок:** сервер ПЕРВЫМ (читает старых + новых клиентов), затем клиент. Старый клиент → новый сервер = Auto (backward-compat); новый клиент → старый сервер = поле молча игнорируется.

### 2. Smoke test после деплоя
Skill `/test-firebase-function`:
- Сообщение на не-RU/EN языке (`es`, `hi`) БЕЗ `response_language` (Auto) → ответ на языке сообщения.
- То же с явным тегом → ответ на заданном языке.
- Cloud Run логи: подтвердить, что дописан `LANGUAGE`-блок.

### 3. Manual QA (опц.)
`/web-dev-run` :9090 → чат: локализованный empty-state greeting + suggestion cards, выбрать response language, отправить сообщение, проверить язык ответа.

## Как возобновить

resume: «задеплой chat all-languages» / «deploy response_language функции» / «активируй мультиязычный чат»
