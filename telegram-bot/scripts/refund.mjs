#!/usr/bin/env node
/**
 * Refund one Telegram Stars payment and revoke the premium it granted.
 *
 *   TELEGRAM_BOT_TOKEN=... node scripts/refund.mjs <telegram_user_id> <telegram_payment_charge_id> [--dry-run]
 *
 * 1. Bot API `refundStarPayment(user_id, telegram_payment_charge_id)` — the Stars go back to the user.
 *    An already-refunded charge is not an error here: step 2 still runs, so a half-done earlier run
 *    can be finished by re-running the script.
 * 2. `telegram_bot_internal` `revoke_payment(telegram_payment_charge_id)` — Python marks the payment
 *    `refunded` and takes the Telegram premium back (contract Phase 3).
 *
 * `CF_BASE_URL` overrides the Cloud Functions base (default: prod). The bot token and the derived
 * internal key are never printed.
 */

import { createHmac } from "node:crypto";

const INTERNAL_KEY_LABEL = "gisti-telegram-internal-v1"; // must match src/keys.ts
const CF_BASE_URL = process.env.CF_BASE_URL ?? "https://us-central1-aichecklists-40230.cloudfunctions.net";

const args = process.argv.slice(2).filter((a) => !a.startsWith("--"));
const dryRun = process.argv.includes("--dry-run");
const token = process.env.TELEGRAM_BOT_TOKEN ?? "";

function fail(msg) {
  console.error(`refund: ${msg}`);
  process.exit(1);
}

const [userIdArg, chargeId] = args;
const userId = Number(userIdArg);
if (!Number.isSafeInteger(userId) || userId <= 0) fail("usage: refund.mjs <telegram_user_id> <telegram_payment_charge_id>");
if (!chargeId || chargeId.length > 256) fail("telegram_payment_charge_id is missing.");
if (!/^\d+:[A-Za-z0-9_-]+$/.test(token)) fail("TELEGRAM_BOT_TOKEN is missing or malformed.");

const internalKey = createHmac("sha256", token).update(INTERNAL_KEY_LABEL).digest("base64url");

async function postJson(url, body, headers = {}) {
  const resp = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  let data = {};
  try {
    data = await resp.json();
  } catch {
    // non-JSON
  }
  return { status: resp.status, data };
}

if (dryRun) {
  console.log(`DRY refundStarPayment {"user_id":${userId},"telegram_payment_charge_id":"${chargeId}"}`);
  console.log(`DRY telegram_bot_internal {"action":"revoke_payment","telegram_payment_charge_id":"${chargeId}"}`);
  console.log("Dry run complete — nothing was sent.");
  process.exit(0);
}

// 1. Refund the Stars.
let refund;
try {
  refund = await postJson(`https://api.telegram.org/bot${token}/refundStarPayment`, {
    user_id: userId,
    telegram_payment_charge_id: chargeId,
  });
} catch (e) {
  fail(`refundStarPayment: network error — ${e instanceof Error ? e.message : e}`);
}
const alreadyRefunded = /ALREADY_REFUNDED/i.test(String(refund.data?.description ?? ""));
if (refund.data?.ok === true) console.log("ok  refundStarPayment");
else if (alreadyRefunded) console.log("ok  refundStarPayment (already refunded earlier)");
else fail(`refundStarPayment: ${refund.data?.error_code ?? refund.status} ${refund.data?.description ?? "unknown error"}`);

// 2. Revoke the premium on the server.
let revoke;
try {
  revoke = await postJson(
    `${CF_BASE_URL}/telegram_bot_internal`,
    { action: "revoke_payment", telegram_payment_charge_id: chargeId },
    { "X-Gisti-Internal-Key": internalKey },
  );
} catch (e) {
  fail(`revoke_payment: network error — ${e instanceof Error ? e.message : e}. Stars ARE refunded; re-run to revoke.`);
}
if (revoke.data?.success !== true) {
  fail(`revoke_payment: ${revoke.status} ${revoke.data?.error ?? "unknown error"}. Stars ARE refunded; re-run to revoke.`);
}
console.log("ok  revoke_payment");
console.log("Refund complete.");
