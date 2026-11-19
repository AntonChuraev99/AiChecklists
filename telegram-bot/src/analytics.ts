/**
 * Amplitude events for the Telegram channel (project 786722, HTTP V2 API — same endpoint and the
 * same identity convention as mcp-server/src/analytics.ts; its event union is closed to `mcp_*`, so
 * this is a sibling, not an import).
 *
 * IDENTITY
 *  - `user_id` = the credit doc id `users/{credit_user_id}` (a UUID) — byte-identical to what the app
 *    passes to Amplitude's setUserId and what the MCP sends as `creditUserId`. Present once the
 *    Telegram account is linked; this is what joins the bot to the app user's profile.
 *  - `device_id` = `tg_<sha256(telegram_user_id)[0:16]>` — stable, pseudonymous, always sent, so the
 *    events of a not-yet-linked user are still counted and later merge with the profile.
 *
 * PRIVACY: `TgEvent` is a closed union of fixed vocabularies — no field can carry message text,
 * checklist names or error messages. `analytics.test.ts` pins the payload key allowlist.
 *
 * DELIVERY: callers `await` the send inside the Durable Object alarm (the MCP lost events when a
 * send was left floating). A send never throws and never blocks longer than SEND_TIMEOUT_MS; an
 * unset key is a no-op with one warning per isolate.
 */

/** Amplitude HTTP V2 ingestion endpoint (US project 786722) — same as the MCP. */
const AMPLITUDE_HTTP_ENDPOINT = "https://api2.amplitude.com/2/httpapi";
/** Amplitude rejects ids shorter than this. */
const MIN_ID_LENGTH = 5;
const SEND_TIMEOUT_MS = 5000;

export interface AnalyticsEnv {
  /** Same value the Cloud Functions read as AMPLITUDE_SERVER_API_KEY. Optional. */
  AMPLITUDE_SERVER_API_KEY?: string;
}

export type ChecklistMode = "free" | "ai";
export type ChecklistInput = "text" | "photo" | "voice" | "audio" | "document" | "url";
export type AiFailReason = "no_credits" | "cf_error" | "unsupported";
export type SubscriptionStateValue = "active" | "canceled" | "failed";

/** Every event the bot emits. Adding a field means adding it here first. */
export type TgEvent =
  | { type: "tg_link_completed" }
  | { type: "tg_checklist_created"; mode: ChecklistMode; input: ChecklistInput }
  | { type: "tg_lists_opened" }
  | { type: "tg_item_toggled" }
  | { type: "tg_ai_failed"; reason: AiFailReason }
  // Phase 3 — Stars subscription + mobile app
  | { type: "tg_premium_opened" }
  | { type: "tg_invoice_created" }
  /** `amount` = Stars charged (XTR integer), not money. */
  | { type: "tg_payment_succeeded"; amount: number; isRecurring: boolean }
  | { type: "tg_subscription_updated"; state: SubscriptionStateValue }
  | { type: "tg_app_link_opened" };

export interface TgAnalyticsIdentity {
  /** `users/{credit_user_id}` when linked, else null. */
  userId: string | null;
  /** `tg_<sha256(telegram_user_id)[0:16]>`. */
  deviceId: string;
}

export interface AmplitudeEvent {
  user_id?: string;
  device_id: string;
  event_type: TgEvent["type"];
  time: number;
  platform: "Telegram";
  event_properties: Record<string, string | number | boolean>;
}

function hex(bytes: Uint8Array): string {
  let out = "";
  for (let i = 0; i < bytes.length; i++) out += bytes[i]!.toString(16).padStart(2, "0");
  return out;
}

/** Stable pseudonymous device id for a Telegram user id. */
export async function tgDeviceId(telegramUserId: number): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`telegram:${telegramUserId}`));
  return `tg_${hex(new Uint8Array(digest)).slice(0, 16)}`;
}

export async function tgIdentity(telegramUserId: number, creditUserId: string | null): Promise<TgAnalyticsIdentity> {
  return { userId: creditUserId, deviceId: await tgDeviceId(telegramUserId) };
}

/** Exhaustive: a new event variant cannot compile until it decides what goes on the wire. */
function eventProperties(e: TgEvent): Record<string, string | number | boolean> {
  switch (e.type) {
    case "tg_link_completed":
    case "tg_lists_opened":
    case "tg_item_toggled":
    case "tg_premium_opened":
    case "tg_invoice_created":
    case "tg_app_link_opened":
      return {};
    case "tg_checklist_created":
      return { mode: e.mode, input: e.input };
    case "tg_ai_failed":
      return { reason: e.reason };
    case "tg_payment_succeeded":
      return { amount: e.amount, is_recurring: e.isRecurring };
    case "tg_subscription_updated":
      return { state: e.state };
  }
}

/** Pure payload builder (unit-tested). */
export function buildAmplitudeEvent(identity: TgAnalyticsIdentity, event: TgEvent, now: number): AmplitudeEvent {
  const userId = identity.userId && identity.userId.length >= MIN_ID_LENGTH ? identity.userId : undefined;
  return {
    ...(userId ? { user_id: userId } : {}),
    device_id: identity.deviceId,
    event_type: event.type,
    time: now,
    platform: "Telegram",
    event_properties: eventProperties(event),
  };
}

let warnedMissingKey = false;

/** POST one event. Resolves on every outcome — analytics must never fail or stall a user action. */
export async function sendTgEvent(env: AnalyticsEnv, identity: TgAnalyticsIdentity, event: TgEvent): Promise<void> {
  const apiKey = env.AMPLITUDE_SERVER_API_KEY;
  if (!apiKey) {
    if (!warnedMissingKey) {
      warnedMissingKey = true;
      console.warn("[gisti-tg] AMPLITUDE_SERVER_API_KEY unset — tg_* analytics disabled");
    }
    return;
  }
  try {
    const resp = await fetch(AMPLITUDE_HTTP_ENDPOINT, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ api_key: apiKey, events: [buildAmplitudeEvent(identity, event, Date.now())] }),
      signal: AbortSignal.timeout(SEND_TIMEOUT_MS),
    });
    if (!resp.ok) console.warn(`[gisti-tg] amplitude upload ${event.type} non-200: ${resp.status}`);
  } catch (e) {
    console.warn(`[gisti-tg] amplitude upload ${event.type} failed: ${e instanceof Error ? e.name : "unknown"}`);
  }
}
