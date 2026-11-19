#!/usr/bin/env node
/**
 * One-shot Bot API setup for @gisti_app_bot — run after every deploy that changes the webhook URL,
 * the command menu or the descriptions, and after rotating the token in BotFather.
 *
 *   TELEGRAM_BOT_TOKEN=... WEBHOOK_URL=https://gisti-telegram.<subdomain>.workers.dev/telegram/webhook \
 *     node scripts/setup.mjs [--dry-run]
 *
 * Does: setWebhook (secret_token = derived key, allowed_updates message/callback_query/my_chat_member/
 * pre_checkout_query/subscription, drop_pending_updates: false — updates Telegram queued while the
 * webhook was failing, `successful_payment` among them, are delivered, never discarded; the chat DO
 * dedupes by update_id, so a redelivery is safe), setMyCommands (default + en/ru/hi),
 * setMyDescription + setMyShortDescription
 * (default + en/ru/hi). Texts come from src/i18n.ts (imported via Node's built-in type stripping,
 * Node ≥ 22.18 / 23.6). The token and the derived secret are never printed.
 * `--dry-run` prints the planned calls without contacting Telegram.
 */

import { createHmac } from "node:crypto";
import { BOT_COMMANDS, BOT_DESCRIPTIONS, LANGS } from "../src/i18n.ts";

const WEBHOOK_SECRET_LABEL = "gisti-telegram-webhook-v1"; // must match src/keys.ts
// `pre_checkout_query` — Stars checkout gate; `subscription` — Bot API 10.2 BotSubscriptionUpdated
// (https://core.telegram.org/bots/api#update). successful_payment arrives inside `message`.
const ALLOWED_UPDATES = ["message", "callback_query", "my_chat_member", "pre_checkout_query", "subscription"];

const dryRun = process.argv.includes("--dry-run");
const token = process.env.TELEGRAM_BOT_TOKEN ?? "";
const webhookUrl = process.env.WEBHOOK_URL ?? "";

function fail(msg) {
  console.error(`setup: ${msg}`);
  process.exit(1);
}

if (!/^\d+:[A-Za-z0-9_-]+$/.test(token)) fail("TELEGRAM_BOT_TOKEN is missing or malformed.");
let parsed;
try {
  parsed = new URL(webhookUrl);
} catch {
  fail("WEBHOOK_URL is missing or not a URL.");
}
if (parsed.protocol !== "https:" || parsed.pathname !== "/telegram/webhook") {
  fail("WEBHOOK_URL must be https://…/telegram/webhook");
}

/** base64url(HMAC_SHA256(key = token, msg = label)), no padding — same as src/keys.ts deriveKey. */
function derive(label) {
  return createHmac("sha256", token).update(label).digest("base64url");
}

async function call(method, params) {
  const label = params.language_code ? `${method} [${params.language_code}]` : `${method} [default]`;
  if (dryRun) {
    const shown = { ...params };
    if (shown.secret_token) shown.secret_token = `<derived, ${shown.secret_token.length} chars>`;
    console.log(`DRY ${label} ${JSON.stringify(shown).slice(0, 300)}`);
    return;
  }
  let data;
  try {
    const resp = await fetch(`https://api.telegram.org/bot${token}/${method}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(params),
    });
    data = await resp.json();
  } catch (e) {
    fail(`${label}: network error — ${e instanceof Error ? e.message : e}`);
  }
  if (!data?.ok) fail(`${label}: ${data?.error_code ?? "?"} ${data?.description ?? "unknown error"}`);
  console.log(`ok  ${label}`);
}

await call("setWebhook", {
  url: webhookUrl,
  secret_token: derive(WEBHOOK_SECRET_LABEL),
  allowed_updates: ALLOWED_UPDATES,
  // false: a paid `successful_payment` waiting in Telegram's queue must not be thrown away by a re-run.
  drop_pending_updates: false,
});

await call("setMyCommands", { commands: BOT_COMMANDS.en });
for (const lang of LANGS) await call("setMyCommands", { commands: BOT_COMMANDS[lang], language_code: lang });

await call("setMyDescription", { description: BOT_DESCRIPTIONS.en.description });
await call("setMyShortDescription", { short_description: BOT_DESCRIPTIONS.en.short });
for (const lang of LANGS) {
  await call("setMyDescription", { description: BOT_DESCRIPTIONS[lang].description, language_code: lang });
  await call("setMyShortDescription", { short_description: BOT_DESCRIPTIONS[lang].short, language_code: lang });
}

console.log(dryRun ? "Dry run complete — nothing was sent." : "Bot setup complete.");
