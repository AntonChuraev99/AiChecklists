# gisti-telegram — Telegram bot @gisti_app_bot

A Cloudflare Worker that lets a Gisti user work with their checklists from Telegram, on the **same
Google account** as the app: same checklists, same AI-credit wallet. Phase 1 (design:
`docs/active/telegram-bot-phase1-2026-11-19.md`):

- **Link** — `/start <token>` from the app's deep link, or «Sign in with Google» from the bot.
- **Any message → checklist** — text becomes a checklist line by line for free, or with AI
  (text, photo, voice/audio, `.txt`/`.md`/`.csv` file, link; files up to 14 MB) for
  `ai_action_cost` credits.
- **Browse and tick** — `/lists`, open a checklist, tap items to check them off.
- **Credits** — `/credits`; `/help`; `/unlink`.
- **Phase 3 — Subscription** — `/premium` (menu «Subscription»): a 30-day Telegram Stars
  subscription (`telegram_premium_price_stars`, default 100 ⭐). One Premium for Telegram, the Android
  app and the web. `/paysupport` has the support contact and terms. `/app` (menu «Mobile app») opens
  the Google Play listing.

Not built yet: AI chat (phase 2), reminders (phase 4).

### Payments (Phase 3)

- `/premium` → `premium_status` (Python). Not linked → link prompt. Store premium → «already active,
  works here too». Telegram premium → until-date and renewal state. Otherwise → perks (the daily AI
  credit cap is read from `remote_config/current`) plus a `createInvoiceLink` button: `XTR`, empty
  `provider_token`, `subscription_period: 2592000`, payload `premium_month_v1`.
- `pre_checkout_query` is answered **in the webhook request, outside the per-chat queue** (Telegram
  needs the answer within 10 s). `ok` only when the payload matches, the currency is `XTR`, the
  amount equals the current `telegram_premium_price_stars` (fresh read) and the Telegram account is
  linked. If the decision cannot be made within 7 s → `ok: false` with «try again in a minute».
- `successful_payment` (a message) → `apply_payment` (idempotent by charge id, so queue retries are
  safe) → «Premium is active until …». If it still fails after 3 attempts → the error is logged and
  the user gets «payment received, premium will appear shortly, /paysupport».
- `Update.subscription` (Bot API 10.2 `BotSubscriptionUpdated` {user, invoice_payload, state:
  active|canceled|failed}) → `subscription_update`.
- Refund: `TELEGRAM_BOT_TOKEN=… node scripts/refund.mjs <telegram_user_id> <charge_id>` runs
  `refundStarPayment`, then `revoke_payment`. Re-running is safe; `--dry-run` prints the calls.

## How it works

```
Telegram ─POST /telegram/webhook (X-Telegram-Bot-Api-Secret-Token)─▶ Worker
   ├─ wrong secret → 401 · non-private chat → ignored (added to a group → leaveChat)
   └─ private chat → Durable Object TelegramChat (idFromName(chat_id)).enqueue(update) → 200
                       └─ alarm(): ordered processing, update_id dedupe, ≤3 attempts per update,
                          ~2.5 s draft debounce (albums merged), short-key map for buttons
```

- Data layer: imports `../mcp-server/src/{firestore,model,encode,mutate,cf}.ts` (dependency-free,
  unmodified) — checklists are written byte-identically to the app, exactly like the MCP tools.
- Link state, credits and money are written only by Python (`telegram_bot_internal`,
  `generate_checklist`); the worker only reads `telegram_links/{telegram_user_id}`.
- No extra secrets: the webhook `secret_token` and the `X-Gisti-Internal-Key` header are derived
  from the bot token (`src/keys.ts`, vector pinned in `keys.test.ts` and on the Python side).

## Secrets

| Name | Required | Value |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | yes | BotFather token of @gisti_app_bot |
| `FIREBASE_SERVICE_ACCOUNT` | yes | service-account JSON key as one string — same format as `gisti-mcp` |
| `AMPLITUDE_SERVER_API_KEY` | no | Amplitude project key — the same value the Cloud Functions and `gisti-mcp` use. Unset → the bot works unchanged, only the `tg_*` events are skipped (one warning per isolate) |

`CF_BASE_URL` is a plain var in `wrangler.jsonc`.

## Develop / test

```bash
npm install
npm run typecheck   # tsc --noEmit (includes the imported mcp-server modules)
npm test            # vitest
```

## Deploy

```bash
# 1. FIRST: the login must own the account_id from wrangler.jsonc.
#    A different account → STOP, re-login or use a CLOUDFLARE_API_TOKEN scoped to that account.
npx wrangler@4 whoami

# 2. secrets (once; again after a token rotation)
npx wrangler@4 secret put TELEGRAM_BOT_TOKEN
npx wrangler@4 secret put FIREBASE_SERVICE_ACCOUNT
npx wrangler@4 secret put AMPLITUDE_SERVER_API_KEY   # optional, enables tg_* analytics

# 3. deploy
npx wrangler@4 deploy

# 4. register the webhook, command menu and descriptions (EN / RU / HI). Re-run it after Phase 3:
#    allowed_updates now include pre_checkout_query and subscription, and the menu gained
#    premium / app / paysupport.
TELEGRAM_BOT_TOKEN=... WEBHOOK_URL=https://gisti-telegram.<subdomain>.workers.dev/telegram/webhook \
  node scripts/setup.mjs            # add --dry-run to print the calls without sending
```

Order with the backend: deploy the Python `telegram_bot_internal` (with `TELEGRAM_BOT_TOKEN` from
Secret Manager) before step 4, otherwise linking answers «something went wrong». In BotFather set
`/setjoingroups` → Disable (the worker also leaves any group it is added to).

## Analytics (`src/analytics.ts`)

Amplitude HTTP V2 (same endpoint and identity rules as `mcp-server/src/analytics.ts`), sent with
`await` inside the Durable Object alarm, after the user-visible reply.

| Event | Properties |
|---|---|
| `tg_link_completed` | — |
| `tg_checklist_created` | `mode` free / ai · `input` text / photo / voice / audio / document / url |
| `tg_lists_opened` | — (the `/lists` command; page flips are not counted) |
| `tg_item_toggled` | — |
| `tg_ai_failed` | `reason` no_credits / cf_error / unsupported (rejected file type or size, too long) |
| `tg_premium_opened` | — (`/premium` for a linked user) |
| `tg_invoice_created` | — (the invoice button was shown) |
| `tg_payment_succeeded` | `amount` (Stars), `is_recurring` |
| `tg_subscription_updated` | `state` active / canceled / failed |
| `tg_app_link_opened` | — (`/app`; a tap on the Play button itself is not observable) |

`user_id` = the credit doc id `users/{credit_user_id}` (the id the app passes to `setUserId`), set
once the account is linked; `device_id` = `tg_<sha256(telegram_user_id)[0:16]>` on every event;
`platform` = `Telegram`. No message text, checklist name or error message is ever sent — the event
type is a closed union of fixed vocabularies (`analytics.test.ts` pins the key allowlist).

## Known limits

- Files above **14 MB** are refused with a message: the file reaches Gemini inline as base64 (×4/3)
  inside a request capped at ~20 MB.
- `generate_checklist` takes ONE input: an album or a draft with several files uses the first one
  (the prompt says so).
- Images go to the CF as `image/jpeg`: Telegram photos and JPEG files only; other image files get
  «send it as a photo». PDFs are refused (the CF has no PDF input).
- Audio: the CF currently labels every audio as `audio/mp4`; Telegram voice notes are OGG/Opus. The
  worker already sends `mime_type`, which the CF ignores today — verify voice with a live smoke test.
- A link is passed to the model as the URL string (the CF does not fetch the page), same as the app.
- AI output language: `ru` for Russian users, English otherwise (the CF supports only these two).
