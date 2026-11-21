# Telegram bot — phase 2 (AI chat) + fill-item flags fix

**Статус:** Partially Done
**Дата старта:** 2026-11-20
**Start SHA:** 83f437ce
**Project:** Checklists
**Тип:** feature
**Затронутые модули:** telegram-bot, mcp-server (data layer)

## Цель (продуктовая)

The AI assistant — the app's flagship — inside @gisti_app_bot, on the same account, checklists and
credit wallet. Phase 1/3 design: `docs/archive/telegram-bot-phase1-2026-11-19.md`. Owner decisions
(2026-11-20):

- **Entry:** a text draft gets a third button «💬 Ask AI (N credits)»; `/chat [text]` opens the chat
  directly. While the chat is open, text messages go straight to the agent; it closes on «End chat»
  or after 30 min without messages. The free «Checklist from lines» path stays.
- **Confirmation:** agent writes run at once; only `delete_item`, `clear_completed_items` and
  `move_item` ask «Yes / Cancel».
- Prerequisite fix found while scoping: the fill-item encoder of `mcp-server` drops
  `reminderFullScreen` and `alarmEnabled`, so every fill write from the bot or MCP rewrites them on
  every item of that fill.

## Технический план

### Fill-item flags fix (mcp-server data layer)

`mcp-server/src/encode.ts` `encodeFillItem` emits a whitelist of keys; the app's
`ChecklistFillItem` (`feature/checklist/.../domain/model/Checklist.kt`) also has
`reminderFullScreen` (default false) and `alarmEnabled` (default true). Fix: model + encode both
fields in the app's key order, and carry any other key the parser does not know through unchanged,
so the next new app field is not wiped either. Same audit for template items and the checklist doc.

### Chat loop (worker, no Python change)

`chat_agent` runs ONE Gemini step per HTTP call and returns `tool_calls`; the caller executes them
and calls back (the app's loop: `ChatViewModel`; the closest model is the voice gateway, which calls
`chat_agent` without the L2 classifier). The bot runs that loop in its Durable Object:

- Transcript in DO storage, entries `user` / `model` text / `model` tool_calls (with
  `thought_signature` echoed verbatim) / `tool` results; cut like the app's `AgentTranscriptWindow`.
- One `request_id` per turn → the 3-credit charge happens once, on round 1, idempotently
  (`credit_reservations`); rounds 2-5 are free. Server cap: 5 tool rounds.
- `checklists_summary` built from Firestore like `AgentChecklistsSummary` (8 lists × 6 recent items).
- `present_options` → inline buttons; a tap is the next user turn (charged, as in the app).
- 402 `reason: no_user` → sign-in prompt; `insufficient_credits` → out-of-credits + `/premium`.

### Server-side tool executor (worker)

The 11 dispatcher tools over the MCP data layer (`mcp-server/src/{firestore,mutate,model}.ts`),
result shapes as in the app's `AgentToolResultSerializer`; checklist lookup by case-insensitive
substring with not_found / one / ambiguous outcomes. A batch containing a destructive call is shown
as one «Yes / Cancel» card and the loop pauses in DO storage; Cancel → `{"status":"declined"}` per
call; a new message while a card is pending declines it first, so every `tool_calls` entry is
always followed by its results.

### Known gaps kept out of scope

- Free tier «5 checklists» is not enforced by the bot (true since phase 1 and for MCP).
- `chat_agent` trusts `user_id` from the body (pre-existing, `validate_request` in `main.py`).

## Лог итераций

### Итерация 1 — 2026-11-20 — general-purpose (mcp-server): флаги пунктов заполнения → red 5 тестов, затем mcp-server 82/82, telegram-bot 110/110
Корень: `encodeFillItem` / `decodeFillItem` знали фиксированный список ключей, а любая запись заполнения перекодирует все его пункты. Фикс: `reminderFullScreen` и `alarmEnabled` в порядке Kotlin (`FILL_ITEM_KEYS`), неизвестные ключи едут в `extra` и дописываются после известных — у пунктов и заполнения, и шаблона. Уровень документа не терял ничего: `writeChecklist` шлёт PATCH с `updateMask` = `CHECKLIST_DOC_FIELD_PATHS`.

### Итерация 2 — 2026-11-20 — general-purpose (worker): AI-чат → tsc ok, telegram-bot 168/168, mcp-server 82/82, `wrangler deploy --dry-run` ok (живого прогона против `chat_agent` не было)
Новые `src/agent.ts`, `src/agentTools.ts`, `src/timezone.ts`. `request_id = tg-chat-<chatId>-<turnSeq>`; повтор апдейта возобновляет ход, а не начинает новый; запись идёт шагами «план с точными id → Firestore → отметка», добавления получают детерминированные id. Сознательные отличия от приложения: список по умолчанию — последний изменённый (в приложении первый по `position`), `checklists_summary` в том же порядке; `response_language` — код языка Telegram; «Отмена» отклоняет всю пачку, включая чтения.

### Итерация 3 — 2026-11-20 — task-gate ревью Standards + Spec → general-purpose (worker): 6 подтверждённых находок → telegram-bot 171/171, mcp-server 82/82, dry-run ok
Spec: 402 оставлял чат открытым без «End chat» — Free-пользователь с пустым кошельком больше не мог сделать бесплатный чек-лист из строк (теперь 402 закрывает чат); карточка подтверждения строилась из аргументов модели, а цель искалась после «Да» (теперь пачка планируется до карточки, «Да» применяет сохранённые id); таймер простоя сбрасывался только ходами агента; `response_language` слал сырой язык Telegram. <!-- docs-leak-scan: reviewed — 402 is an HTTP status, not a metric --> Standards: `planClear` держал свою копию поиска узла шаблона, уже разошедшуюся с `templateFor`. `/unlink` теперь закрывает чат. Деплой остановлен: wrangler залогинен в рабочий аккаунт Cloudflare, а не в аккаунт из `wrangler.jsonc`.

## Выводы

- **Сделано:** AI-ассистент в боте — `chat_agent` гоняется воркером (без классификатора, как голосовой звонок), 11 инструментов исполняются в воркере поверх слоя данных MCP, подтверждение только для удаления / очистки / переноса, `/chat`, `/timezone`. Попутно закрыт живой баг слоя данных: запись заполнения из бота или MCP сбрасывала `alarmEnabled` / `reminderFullScreen` у всех пунктов — `docs/solutions/mcp-fill-encoder-drops-unknown-keys-2026-11-20.md`.
- **Выяснили:** цикл инструментов `chat_agent` живёт на клиенте — серверу без устройства нужен свой исполнитель; карточку подтверждения надо строить из уже найденных целей, иначе пользователь подтверждает не то, что удалится; отказ по кредитам обязан закрывать режим чата, иначе он съедает бесплатный путь.
- **Осталось:** деплой `gisti-mcp` + `gisti-telegram` и `setup.mjs` после входа в нужный аккаунт Cloudflare, живая проверка `/chat` владельцем; Free-лимит в 5 чек-листов бот не проверяет — `docs/backlog/2026-11-20-telegram-mcp-free-checklist-limit.md`; цена хода продублирована в воркере — строка в `docs/backlog/2026-08-03-duplicated-limit-constants.md`.
