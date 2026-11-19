# Telegram bot — phase 1 (link, checklist from a message, view, shared credits)

**Статус:** Done
**Дата старта:** 2026-11-19
**Start SHA:** ec041164
**Project:** Checklists
**Тип:** feature
**Затронутые модули:** telegram-bot (new worker), firebase-functions, composeApp (commonMain), core/navigation, core/designsystem (strings), firestore.rules

## Цель (продуктовая)

The bot @gisti_app_bot and the app share one Google account: the same checklists, the same AI-credit
wallet and (phase 3) the same premium. Phase 1 delivers: account linking in both directions, any
message → checklist (free from lines, or AI from text/photo/voice/link), browsing checklists and
ticking items in Telegram, credit balance. Owner decisions (2026-11-19): Google sign-in only (no
Telegram-only accounts); phases = core → AI chat → Stars subscription → Telegram reminders; reminder
channel will be a choice push / Telegram / both; the bot lives in a **separate** Cloudflare Worker.

Later phases (not in this MR): 2 — AI chat (`chat_agent` + server-side tool executor), 3 — Stars
subscription (`telegram_premium_until`, `verify_premium` + RC-revoke must OR it, otherwise
`refill_premium_credits` revokes it nightly), 4 — reminders (server scheduler, IANA tz, channel field).

## Технический план

### Architecture

```
Telegram ──webhook (secret header)──▶ Worker gisti-telegram  (telegram-bot/)
                                        ├─ Durable Object per chat: fast 200, update_id dedupe,
                                        │  ordered processing, draft debounce, view state
                                        ├─ imports ../mcp-server/src/{firestore,encode,model,mutate,cf}.ts
                                        │  (dependency-free modules; MCP itself is NOT modified)
                                        ├─ reads telegram_links/{telegram_user_id} via Firestore REST
                                        └─ POST Python CF telegram_bot_internal (link mutations)
                                           POST existing CF generate_checklist (credits, request_id)
App / web (Firebase ID token) ──▶ Python CF telegram_account (create_token | claim | status | unlink)
```

Money, credits and link state are mutated only by Python (Admin SDK transactions). The worker only
reads link state and writes checklists through the MCP data layer (client-identical format).

### Firestore schema (server-only collections — clients have NO access)

- `telegram_link_tokens/{sha256_hex(token)}` — one-time, TTL 10 min:
  `direction` (`"app_to_bot"` | `"bot_to_app"`), `google_uid`, `credit_user_id` (app_to_bot),
  `telegram_user_id` (int), `chat_id` (int), `telegram_username`, `telegram_first_name`,
  `language_code` (bot_to_app), `created_at`, `expires_at`, `used_at` (null until consumed).
  The raw token is never stored. Token = 32 random bytes, base64url without padding (43 chars,
  fits Telegram's `start` parameter: ≤64 chars of `[A-Za-z0-9_-]`).
- `telegram_links/{telegram_user_id}` — `telegram_user_id`, `chat_id`, `telegram_username`,
  `telegram_first_name`, `language_code`, `google_uid`, `credit_user_id`, `linked_at`.
- Credit doc `users/{credit_user_id}` gets `telegram_user_id`, `telegram_username`,
  `telegram_linked_at` (server-written; deleted on unlink).
- Linking is 1:1, enforced in one transaction: binding a Telegram account that is linked elsewhere,
  or a Google account that already has another Telegram, replaces the old link and clears the old
  fields.

Identity (two-doc model): `google_uid` = Firebase uid = id of the data doc `users/{google_uid}`
(checklists live there); `credit_user_id` = the device-registration UUID doc that carries
`google_uid` as a field plus `ai_credits` / `is_premium`. Server resolves the credit doc by
`users where google_uid == <firebase uid>`; prefer the body `user_id` if it is among the matches,
else the first; no match → 409 `no_account`.

### Python Cloud Functions (2 new services)

**`telegram_account`** — app/web facing. `Authorization: Bearer <Firebase ID token>`, verified with
the existing `verify_firebase_token`. CORS like the other web-called CFs. Body `{action, ...}`:
- `create_token` (`user_id?`) → `{token, deep_link: "https://t.me/gisti_app_bot?start=<token>", expires_in_sec: 600}`
- `claim` (`token` of direction bot_to_app) → `{linked: true, telegram_username}`; 410 expired/used, 404 unknown
- `status` → `{linked, telegram_username?, linked_at_ms?}`
- `unlink` → `{linked: false}`; best-effort Bot API `sendMessage` to the old chat (warning log on failure)

**`telegram_bot_internal`** — worker facing. Header `X-Gisti-Internal-Key` (derived key, below),
compared with `hmac.compare_digest`; anything else → 401. Body `{action, ...}`:
- `consume_app_token` (`token, telegram_user_id, chat_id, username, first_name, language_code`) → `{google_uid, credit_user_id}`; 410/404
- `create_login_token` (same Telegram fields) → `{token, url: "https://app.gisti-ai.com/?tg_link=<token>"}`
- `unlink` (`telegram_user_id`) → `{ok: true}`

Deploy: `--update-secrets TELEGRAM_BOT_TOKEN=telegram-bot-token:latest` (secret created 2026-11-19).

### Derived keys — no extra secrets

Both sides hold `TELEGRAM_BOT_TOKEN` and derive (base64url, no padding):
- internal key = `HMAC_SHA256(key=bot_token, msg="gisti-telegram-internal-v1")`
- webhook secret_token = `HMAC_SHA256(key=bot_token, msg="gisti-telegram-webhook-v1")`

Rotating the token in BotFather rotates both. A Python test and a TS test pin the SAME vector for
token `123456:TEST-TOKEN` so the two implementations cannot drift:
internal = `7iBbDTERoGTkAbeNNyKajDJfOFWoSO4V4bhvjPRMEjg`,
webhook = `0F_V7JjyEQfts-FqUHjJd6ceTdTdOC2jWK22GPZeawQ` (источник: python hmac one-liner, 2026-11-19).

### Worker `telegram-bot/` (name `gisti-telegram`, same CF account as `mcp-server`)

- `POST /telegram/webhook`: verify `X-Telegram-Bot-Api-Secret-Token`; non-private chat → if the bot
  was added to a group, `leaveChat`; ignore → 200. Private chat → forward to the chat's Durable
  Object (enqueue keyed by `update_id`; duplicate → ignored) → 200 immediately. Processing runs in
  the DO `alarm()` (no Telegram retry on slow AI calls).
- Content messages are debounced (`DEBOUNCE_MS = 2500` in `telegram-bot/src/draft.ts`, also groups albums by `media_group_id`) into one draft →
  one prompt with buttons: text → «Checklist from lines» (free, no AI) / «Checklist with AI (N
  credits)»; photo / voice / audio / document / link → AI only. Files via `getFile` (≤20 MB — Bot API limit, источник: core.telegram.org/bots/api#getfile; else a
  polite refusal). AI → existing `generate_checklist` with `user_id = credit_user_id` and
  `request_id = tg-<chat_id>-<draft_id>` (idempotent on retries), then `writeChecklist` to
  `users/{google_uid}` like MCP `create_checklist_ai`. Cost shown from `remote_config/current`
  `ai_action_cost` (cached). 402 → "out of credits" message.
- Commands: `/start [token]`, `/lists` (paginated, open → items of the default fill as toggle
  buttons, paginated; callback_data ≤64 bytes via short keys mapped in DO storage), `/credits`,
  `/help`, `/unlink`. Not linked → explanation + «Sign in with Google» button (`create_login_token`
  URL) + "or in the app: menu → Telegram".
- i18n EN / RU / HI from `language_code` (fallback EN).
- `scripts/setup.mjs`: `setWebhook` (allowed_updates `message, callback_query, my_chat_member`),
  `setMyCommands`, descriptions — per language.

### App (commonMain → Android + current wasm web)

- New screen «Telegram» (drawer, next to MCP): status, Connect → `create_token` → open deep link,
  Disconnect (confirm). Not signed in with Google → explain + sign-in. Status refresh on resume.
- `?tg_link=<token>` on web start (and Android App Link if it matches) → after Google sign-in →
  `claim` → feedback snackbar.
- ⛔ Google Play anti-steering: the app never mentions Stars, premium-in-the-bot or payment on this
  screen. Copy is about checklists, chat and notifications only.
- Strings EN/RU/HI in `core/designsystem` `strings.xml`.

### Release gates (Android)

- Play Data Safety: declare "User IDs" (Telegram id) — app functionality — BEFORE the release that
  ships the screen (precedent: 1.18.7 rejected for declaration drift).
- Privacy policy (`hosting/public/privacy-policy.html`): Telegram data + messages sent to AI — section
  written in this branch; tracked with the other gates in `docs/todos/2026-11-19-telegram-android-release-gates.md`.
- BotFather `/setjoingroups` → Disable (owner action; code also ignores non-private chats).

### Phase 3 — Stars subscription + «Mobile app» menu (added 2026-11-19, same branch)

Owner decisions: price **100 ⭐ / 30 days** (Telegram allows only `subscription_period = 2592000`),
no trial (Stars subscriptions have none), bot copy says the subscription is shared by all platforms
(Telegram, Android app, web). Anti-steering applies to the Android app only — the bot may name the
price and Google Play freely; the app still never mentions Stars.

- Remote config `remote_config/current.telegram_premium_price_stars` (default **100** in code).
- Credit doc `users/{credit_user_id}`: `telegram_premium_until` (timestamp), `telegram_subscription`
  `{charge_id, state: active|canceled|failed, updated_at}`; existing `is_premium`,
  `premium_activated_at`, `ai_credits` set like the RevenueCat grant path.
- `telegram_payments/{telegram_payment_charge_id}` — idempotency + audit: `telegram_user_id`,
  `credit_user_id|null`, `google_uid|null`, `total_amount`, `currency`, `invoice_payload`,
  `is_recurring`, `is_first_recurring`, `subscription_expiration_date`, `status`
  (`applied|unassigned|refunded`), `created_at`. Server-only (rules deny).
- **Premium = RevenueCat OR `telegram_premium_until > now`.** `verify_premium` must honour it (else
  the daily `refill_premium_credits` flips `is_premium` to false on Stars buyers), and the RC revoke
  branch must not clear `is_premium` while Telegram premium is active. Expiry without renewal is
  picked up by the daily refill job (`NOT_VERIFIED` → `is_premium=false`).
- `telegram_bot_internal` new actions: `premium_status` (`telegram_user_id` → `linked, is_premium,
  source: telegram|store|null, telegram_premium_until_ms, subscription_state, price_stars`);
  `apply_payment` (idempotent by charge id, `currency == "XTR"`, unlinked → `unassigned` + error log);
  `subscription_update` (`state`); `revoke_payment` (`telegram_payment_charge_id`, after a refund).
- Bot: `/premium` (menu «Subscription»): not linked → link prompt; premium from the store → "already
  active, works here too"; Telegram premium active → until-date + renewal state; else benefits +
  "one subscription for every platform" + invoice button (`createInvoiceLink`, `XTR`, 100,
  30 days). `pre_checkout_query` answered **outside the per-chat queue** within 10 s (Bot API deadline, источник: core.telegram.org/bots/api#answerprecheckoutquery) (linked + price
  matches config). `successful_payment` → `apply_payment` → confirmation. `BotSubscriptionUpdated`
  → `subscription_update`. `/paysupport` (required by Telegram) + terms link
  `https://gisti-app.web.app/terms`. `scripts/refund.mjs <charge_id>` → `refundStarPayment` +
  `revoke_payment`.
- Bot: `/app` (menu «Mobile app») → button to
  `https://play.google.com/store/apps/details?id=com.antonchuraev.aichecklists&referrer=utm_source%3Dtelegram%26utm_medium%3Dbot`.

### Known pre-existing risk (not fixed here)

AI CFs trust `user_id` from the body (`validate_request`, `main.py:366`); the bot calls them the
same way MCP does — it does not widen the hole. Separate task.

## Лог итераций

### Итерация 1 — 2026-11-19 — design-expert: DESIGN_SPEC экрана Telegram → `docs/designs/telegram-screen-2026-11-19/spec.md`
7 состояний (Loading/SignedOut/NotLinked/Pending/Linked/Error + снэкбары), пункт меню после MCP, 37 ключей `telegram_*` EN/RU/HI, без логотипов. Грабли для реализации: на вебе `window.open` после `await` блокируется как popup → токен выпускать заранее при показе NotLinked; `safeOpenUri` возвращает `Unit` — нужен вариант с результатом.
### Итерация 2 — 2026-11-19 — general-purpose (Python): `telegram_link.py` + хендлеры `telegram_account` / `telegram_bot_internal` + 37 тестов, deploy.sh, functions.yaml, firestore.rules → зелёные
Конверт ответа `{"success": bool, "error"?: code}`; токен не того направления → 404 `unknown_token` (не тратится); `telegram_user_id`/`chat_id` только числа; `status`/`unlink` работают по `google_uid` без credit doc; резолв credit doc написан заново (`get_authenticated_user_id` берёт только `limit(1)`); ошибка `requests` содержит URL с токеном бота → в лог только тип/статус. ⚠ Базовый прогон всего `tests/` вызвал боевые CF (6 файлов — источник: отчёт агента итерации 2; `test_chat_agent*`, `test_chat_completion_honesty`, `test_generate_checklist_size` регистрируют одноразовые аккаунты в проде). 5 падений в `test_main.py` — существовавшие до задачи.
### Итерация 3 — 2026-11-19 — general-purpose (worker): `telegram-bot/` (DO-очередь, дебаунс черновика, /lists, i18n, setup.mjs) → tsc ok, vitest 56/56, dry-run deploy ok
`generate_checklist` принимает `text|url|image_base64|audio_base64`; аудио всегда уходило в Gemini как `audio/mp4` (`main.py:1617` без `audio_mime_type`) — голосовые Telegram OGG → доработка CF (передача `mime_type` через `normalize_audio_mime`). PDF не поддержан CF → отказ. Аналитика MCP — закрытый union `mcp_*` → свой `analytics.ts`. Потолок файла снижен до 14 MB (источник: `MAX_FILE_BYTES = 14 * 1024 * 1024` в `telegram-bot/src/telegram.ts`), с запасом под inline-лимит запроса Gemini ~20 MB с учётом base64 (приблизительно — не проверено в этой сессии). Повтор AI идемпотентен: фиксированный `cloudId` черновика + `request_id`.
### Итерация 4 — 2026-11-19 — general-purpose ×2: `generate_checklist` передаёт `mime_type` аудио через `normalize_audio_mime` (59 passed) · воркер: свой `analytics.ts` (`tg_*`, `device_id = tg_<sha256(tg_id)[0:16]>`, `user_id = credit_user_id`), потолок файла `MAX_FILE_BYTES` → vitest 63/63
### Итерация 5 — 2026-11-19 — main: деплой бэкенда (по согласию владельца) → живой
- Secret Manager `telegram-bot-token` + роль `roles/secretmanager.secretAccessor` (источник: gcloud IAM) для `27698629989-compute@developer.gserviceaccount.com` (без биндинга деплой с `--update-secrets` не стартует).
- CF `telegram_bot_internal`, `telegram_account` (новые), `generate_checklist` → ревизия `generate-checklist-00044-fol`. Смоук: оба отвечают 401 без авторизации; `create_login_token` с производным ключом → 200 (ключ на Python и в воркере совпал); `generate_checklist` → 200 с реальным чек-листом, кредиты 100→99 (classify) →79 (generate) (источник: curl-смоук с одноразовым аккаунтом `tg-smoke-20261119-a`). Первые два вызова дали 500 — в логах `genai.errors.ServerError: 503 UNAVAILABLE … high demand` (перегрузка Gemini, не регрессия: `model_id` резолвится как раньше, кредиты вернулись).
- Воркер `gisti-telegram` → `https://gisti-telegram.gisti.workers.dev` (аккаунт gmail `2c9dfaad…`, `wrangler whoami` первым), секреты bulk, `setup.mjs` 13/13 ok, вебхук стоит. Без секрета вебхук → 401. Маршрута `/health` нет.
### Итерация 6 — 2026-11-19 — feature-expert: слой фичи экрана Telegram (`composeApp/.../telegram/`, HTTP-клиент в `feature/user`, nav, drawer, `?tg_link` web+Android, 39 строк ×3 языка — источник: отчёт feature-expert) → 39 тестов зелёные в изоляции, сборки ok
HTTP-клиент лёг в `feature/user` рядом с `UserApiService` (у composeApp нет ktor). Android-манифест не нужен — App Link уже на весь хост `app.gisti-ai.com`. «Вошёл» = Firebase user **и** `isGoogleLinked` (иначе 409 `no_account` сразу после входа). Полный `:composeApp:testAndroidHostTest` — 1 из 427 падает плавающе: `UncaughtExceptionsBeforeTest` с `CoroutinesInternalError` (источник: вывод прогона `:composeApp:testAndroidHostTest`, класс kotlinx.coroutines) из `DataStoreImpl.handleUpdate` — утечка записи DataStore из чужого теста на `UnconfinedTestDispatcher`; без новых файлов прогон зелёный → NEEDS_DELEGATION @test-expert.
### Итерация 7 — 2026-11-19 — test-expert: корень утечки — `CsatViewModelTest.createViewModel()` создавал `PreferenceDataStoreFactory.createWithPath` без `scope` (дефолт `Dispatchers.IO + SupervisorJob()`), запись доезжала после `resetMain()` → `scope = backgroundScope` → полный `:composeApp:testAndroidHostTest --rerun` 428/0 два раза подряд
### Итерация 8 — 2026-11-19 — compose-expert: вёрстка `TelegramScreen` по спеке + общие `integrations/IntegrationRows.kt` (вынесены из `McpScreen.kt`, MCP-кадр побайтно не изменился) + Roborazzi-матрица 60 кадров → wasm/Android сборки ok
Отступ текста в рядах выгод считается от `lineHeight` (фиксированный `SpacingSm` центрировал строку только при fontScale 1.0). Новый ключ `telegram_linked_date` — в проекте нет локализованного формата даты с годом.
### Итерация 9 — 2026-11-19 — main + feature-expert: живая проверка → баг `?tg_link` на вебе → фикс
Android-устройство (Pixel 9): владелец прошёл привязку, `/lists`, отметку, чек-лист из строк, кредиты — «всё работает»; logcat без FATAL. Веб :9090: `?tg_link` вырезан из URL, но экран Telegram не открылся (остался Inbox). Гипотеза «`combine` не эмитит у анонима» опровергнута — экран из «Обзора» рендерит SignedOut. Корень (feature-expert): push `AppNavRoute.Telegram` на `[Splash]` до конца сплэша; сплэш делает `replaceStack` (`AppNavigatorImpl.kt:92`, `:198-201`) и стирает маршрут. Фикс: `handleTelegramLinkClaim(awaitAppShell)` + в `App.kt` `snapshotFlow { backStack.any { isV2TabRoute(it) } }.first { it }`; red-тесты `coldStart_signedOut_opensScreenOnlyAfterShellIsUp` / `coldStart_signedIn_claimsAtOnce_butOpensScreenOnlyAfterShellIsUp` (`TelegramLinkClaimHandlerTest.kt`) падали до фикса, после — 8/8. Сосед: `handleGalleryDeepLink` с тем же push на `[Splash]` — предложено владельцу, не тронуто.
Перепроверка на :9090 после фикса (холодный старт, аноним, 1024px): `/?tg_link=…` → экран Telegram в SignedOut поверх Inbox, URL очищен, «Назад» → Inbox (источник: Playwright-снимки сессии, в репозиторий не входят). Ошибки консоли — `wasmExports` deprecation и `__firestoreOnDocSnapshot … insufficient permissions` у анонима — экран Telegram слушателей Firestore не вешает (статус через CF).
### Итерация 10 — 2026-11-19 — general-purpose (Python): Фаза 3 сервер → 67 passed, `test_main.py` те же 5 существовавших падений
`verify_premium` = старое тело (`verify_store_premium`) ИЛИ `telegram_premium_until > now`; RC-revoke не снимает `is_premium` при активном Telegram-премиуме; `apply_payment` идемпотентен одной транзакцией (`create()` платежа + grant как у RC + `premium_events_log source=telegram_stars`); поздний платёж с более ранней датой не укорачивает период; `revoke_payment` обрывает премиум только для последнего списания. `telegram_bot_internal` получает ещё `REVENUECAT_API_KEY`. ⚠ Деплой обязан включать `refill_premium_credits`, `on_rc_event_created`, `restore_credits_after_purchase` (общий `verify_premium`) + `firestore.rules`, иначе ночной refill снимет премиум Stars-покупателям. Непривязанные платежи (`unassigned`) не применяются автоматически при поздней привязке.
### Итерация 11 — 2026-11-19 — general-purpose (worker) + main: Фаза 3 бот → tsc ok, vitest 88/88
`/premium`, `/app` (Google Play с `utm_source=telegram`), `/paysupport` (`PaywallConfig.SUPPORT_EMAIL` + terms), `scripts/refund.mjs <telegram_user_id> <charge_id>` (`refundStarPayment` требует `user_id`). Поле подписки сверено с Bot API: `Update.subscription` → `BotSubscriptionUpdated {user, invoice_payload, state}` (источник: https://core.telegram.org/bots/api#botsubscriptionupdated). `pre_checkout_query` отвечается в запросе вебхука до 200, мимо очереди DO; цену воркер читает сам из `remote_config/current` (холодный старт CF может не уложиться в 10 с). Склейка main: воркер читал `premium_until_ms`, Python отдаёт `telegram_premium_until_ms` (`chat.ts:405`) — без правки подтверждение оплаты шло без даты.
### Итерация 12 — 2026-11-19 — main: деплой Фазы 3 (по согласию владельца) → живой
`firestore.rules` (released), CF `telegram_bot_internal`, `refill_premium_credits`, `restore_credits_after_purchase`, `on_rc_event_created` (nam5, `--cpu=1`) — все ACTIVE; новая ревизия доказана вызовом `premium_status` → 200 `price_stars: 100` (источник: curl-смоук). Воркер `bb9b43d9-7eeb-4d29-807d-b53aabe1720c`, `setup.mjs` 13/13, `allowed_updates` = message, callback_query, pre_checkout_query, my_chat_member, subscription (источник: `getWebhookInfo`). <!-- docs-leak-scan: reviewed — function name and the configured price, no purchase metric -->
### Итерация 13 — 2026-11-19 — task-gate full: ревью Standards + Spec → 6 подтверждённых находок → general-purpose ×2 (worker, Python) + main
Владелец проверил Фазу 3 в боте («да работает»). Ревью нашло, триаж по коду подтвердил:
- P1 — `apply_payment` не прошёл за 3 попытки очереди (~10 с) → платёж только в `console.error`, премиум не придёт никогда. Фикс: журнал `pay:<charge_id>` в хранилище DO (`telegram-bot/src/ledger.ts`), повторы 1 мин → 12 ч до 30 дней; туда же `successful_payment` из `onFinalFailure`.
- P2 — `setup.mjs` с `drop_pending_updates: true` выбрасывал недоставленные `successful_payment` → `false`.
- P3 — продление после отвязки становилось `unassigned`, бот обещал «скоро». Фикс: честный текст `paymentUnlinked` + приглашение войти; Python при привязке (`_consume_and_link`, оба направления) применяет живые `unassigned` в той же транзакции; поля выдачи — один хелпер `_premium_grant_update` вместо второй копии.
- S1 — цена: Python брал только целое, воркер — любое число → при double в конфиге pre-checkout отклонял все оплаты. Воркер берёт цену только из `integerValue`.
- S2 — экран Telegram отсутствовал в `FEATURE_CATALOG_RU/EN` (`firebase-functions/prompts_private.py`, gitignored — не проверено guard'ом, файла нет в git) → пункт 26. <!-- docs-leak-scan: reviewed — constant NAMES only, no prompt body -->

- P4 — политика конфиденциальности молчала о данных Telegram → раздел «Telegram Bot (optional)» + Telegram в сторонних сервисах.
Деплой фиксов (по согласию владельца): воркер `b8d99246-86c0-41ef-b2f0-efbb2d5b5e16`; ревизии Cloud Run `telegram-bot-internal-00003-new`, `telegram-account-00002-zex`, `chat-completion-00023-yiw`, `chat-agent-00032-ful` — созданы после коммита, Ready=True (источник: `gcloud run revisions list`); Firebase Hosting `gisti-app` — живая политика содержит «Telegram Bot (optional)» (источник: curl); перед деплоем три живые страницы совпадали с `master` побайтно. Вебхук: `pending_update_count` 0, ошибок нет (источник: `getWebhookInfo`).
Проверки: воркер tsc ok, vitest 110/110; Python 90 passed; `test_main.py` — те же 5 падений, что на `ec041164` (источник: pytest по `git archive` базы).

## Выводы

- **Сделано:** Фаза 1 (привязка в обе стороны, чек-лист из текста, строк, фото и голоса, `/lists` с отметкой пунктов, общие кредиты, экран Telegram на Android и вебе) и Фаза 3 (подписка за Stars, общий премиум, меню «Подписка» и «Мобильное приложение»). Обе живые и проверены владельцем; решение о втором канале оплаты — `docs/decisions/2026-11-19-telegram-stars-second-billing-channel.md`.
- **Выяснили:** сплэш `replaceStack` стирает маршрут, положенный до конца сплэша, — диплинки ждут оболочку приложения; поле `updateTime` из `gcloud functions describe` (источник: вывод gcloud) не доказывает деплой, доказывает только новое поведение (`premium_status` → 200); деньги после ответа вебхука 200 нельзя держать только в повторах очереди — нужен долговечный журнал.
- **Осталось:** гейты Android-релиза — `docs/todos/2026-11-19-telegram-android-release-gates.md`; Фаза 2 (AI-чат в боте) и Фаза 4 (напоминания в Telegram) не начаты; поля выдачи премиума всё ещё продублированы между `telegram_link.py` и `main._handle_rc_event_payload`; тот же гонщик сплэша в `handleGalleryDeepLink` не тронут.
