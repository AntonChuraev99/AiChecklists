/**
 * Keys derived from the bot token — no extra secrets (contract: docs/active/telegram-bot-phase1-2026-11-19.md,
 * "Derived keys"). Both the worker and the Python Cloud Functions hold TELEGRAM_BOT_TOKEN and compute
 * base64url(HMAC_SHA256(key = bot_token, msg = label)) without padding. Rotating the token in
 * BotFather rotates every derived key at once.
 *
 * The test vector for token `123456:TEST-TOKEN` is pinned on BOTH sides (keys.test.ts here, a
 * Python test there) so the two implementations cannot drift.
 */

/** Label for the `X-Gisti-Internal-Key` header the worker sends to `telegram_bot_internal`. */
export const INTERNAL_KEY_LABEL = "gisti-telegram-internal-v1";

/** Label for the webhook `secret_token` (`X-Telegram-Bot-Api-Secret-Token` header). */
export const WEBHOOK_SECRET_LABEL = "gisti-telegram-webhook-v1";

function base64UrlFromBytes(bytes: Uint8Array): string {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]!);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** base64url(HMAC-SHA256(key = botToken, msg = label)), no padding. */
export async function deriveKey(botToken: string, label: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(botToken),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(label));
  return base64UrlFromBytes(new Uint8Array(sig));
}

/**
 * Constant-time string comparison: the loop always walks the longer input, so the time taken does
 * not reveal how many leading characters matched. A length mismatch still returns false.
 */
export function timingSafeEqual(a: string, b: string): boolean {
  const enc = new TextEncoder();
  const x = enc.encode(a);
  const y = enc.encode(b);
  const len = Math.max(x.length, y.length);
  let diff = x.length ^ y.length;
  for (let i = 0; i < len; i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

/** Per-isolate memo: the derived keys only change when the token does. */
const memo = new Map<string, Promise<string>>();

/** Memoised `deriveKey` — one HMAC per (token, label) per isolate instead of one per request. */
export function derivedKey(botToken: string, label: string): Promise<string> {
  const k = `${label}\u0000${botToken}`;
  let p = memo.get(k);
  if (!p) {
    p = deriveKey(botToken, label);
    memo.set(k, p);
  }
  return p;
}
