/**
 * Server-side I/O of the bot: Firestore reads (link, credits, AI config), the Python
 * `telegram_bot_internal` Cloud Function (link mutations), and `generate_checklist` (AI).
 *
 * Auth to Firestore reuses the MCP's service-account token (`getAccessToken` from
 * mcp-server/src/firestore.ts); the doc reader itself is local because that module does not export
 * its generic GET / value unwrapping. Credits and link state are NEVER written here — only Python
 * mutates them (contract: "Money, credits and link state are mutated only by Python").
 */

import { getAccessToken, getUserChecklists, type FirestoreEnv } from "../../mcp-server/src/firestore";
import { CfError, type GenerateChecklistResult, type GeneratedNode } from "../../mcp-server/src/cf";
import type { ChecklistSyncData } from "../../mcp-server/src/model";
import { derivedKey, INTERNAL_KEY_LABEL } from "./keys";

const FIRESTORE_BASE =
  "https://firestore.googleapis.com/v1/projects/aichecklists-40230/databases/(default)/documents";

/** main.py DEFAULT_AI_ACTION_COST — used when remote_config/current has no `ai_action_cost`. */
export const DEFAULT_AI_ACTION_COST = 30;
/** main.py DEFAULT_MAX_INPUT_LENGTH — `ai_analysis_max_input_length` fallback. */
export const DEFAULT_MAX_INPUT_LENGTH = 10_000;
const AI_CONFIG_TTL_MS = 5 * 60 * 1000;
/** Upper bound for one generate_checklist call (Gemini + a retry inside the CF). */
const GENERATE_TIMEOUT_MS = 150_000;
const INTERNAL_TIMEOUT_MS = 20_000;

export interface BackendEnv extends FirestoreEnv {
  TELEGRAM_BOT_TOKEN: string;
  /** Cloud Functions base URL (wrangler `vars`), e.g. https://us-central1-aichecklists-40230.cloudfunctions.net */
  CF_BASE_URL: string;
}

// ── Firestore REST (read-only) ──────────────────────────────────────────────────

/** One typed Firestore REST value (`{integerValue: "5"}`, `{doubleValue: 1.5}`, …). */
export type FsValue = Record<string, unknown>;

function unwrapValue(v: FsValue | undefined): unknown {
  if (!v) return null;
  if ("nullValue" in v) return null;
  if ("stringValue" in v) return v.stringValue;
  if ("booleanValue" in v) return v.booleanValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return v.doubleValue;
  if ("timestampValue" in v) return v.timestampValue;
  if ("mapValue" in v) return unwrapFields((v.mapValue as { fields?: Record<string, FsValue> }).fields);
  if ("arrayValue" in v) return ((v.arrayValue as { values?: FsValue[] }).values ?? []).map((e) => unwrapValue(e));
  return null;
}

function unwrapFields(fields: Record<string, FsValue> | undefined): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(fields ?? {})) out[k] = unwrapValue(v);
  return out;
}

/** GET one document's typed Firestore fields; null on 404, throws on any other failure (so it is retried). */
async function getDocumentFields(env: FirestoreEnv, path: string): Promise<Record<string, FsValue> | null> {
  const token = await getAccessToken(env);
  const resp = await fetch(`${FIRESTORE_BASE}/${path}`, { headers: { Authorization: `Bearer ${token}` } });
  if (resp.status === 404) return null;
  if (!resp.ok) throw new Error(`firestore GET ${path} failed: ${resp.status} ${(await resp.text()).slice(0, 300)}`);
  const doc = (await resp.json()) as { fields?: Record<string, FsValue> };
  return doc.fields ?? {};
}

/** GET one document's fields; null on 404, throws on any other failure (so it is retried). */
export async function getDocument(env: FirestoreEnv, path: string): Promise<Record<string, unknown> | null> {
  const fields = await getDocumentFields(env, path);
  return fields === null ? null : unwrapFields(fields);
}

/** `telegram_links/{telegram_user_id}` — the identity the bot acts as. */
export interface TelegramLink {
  /** The data doc id `users/{google_uid}` — where checklists live and creates are written. */
  googleUid: string;
  /** The credit doc id — what AI calls charge (`user_id` for the CFs). */
  creditUserId: string;
}

export function parseLink(o: Record<string, unknown> | null): TelegramLink | null {
  if (!o) return null;
  const googleUid = typeof o["google_uid"] === "string" ? (o["google_uid"] as string) : "";
  if (!googleUid) return null;
  let creditUserId = typeof o["credit_user_id"] === "string" ? (o["credit_user_id"] as string) : "";
  if (!creditUserId) {
    console.warn("[gisti-tg] telegram_links doc without credit_user_id — falling back to google_uid");
    creditUserId = googleUid;
  }
  return { googleUid, creditUserId };
}

export async function getLink(env: FirestoreEnv, telegramUserId: number): Promise<TelegramLink | null> {
  return parseLink(await getDocument(env, `telegram_links/${telegramUserId}`));
}

/** The user doc ids to fan reads out over — the MCP `allIds` union (data doc + credit doc). */
export function linkIds(link: TelegramLink): string[] {
  return [...new Set([link.googleUid, link.creditUserId])];
}

/**
 * Union of checklists across the user's docs, deduped by cloudId (freshest `updatedAt` wins) — the
 * same fan-out as MCP `collectChecklists` (mcp.ts, not importable here: it pulls in the MCP SDK).
 * `getUserChecklists` already drops `isDeleted` and the system Inbox.
 */
export async function collectChecklists(env: FirestoreEnv, ids: string[]): Promise<ChecklistSyncData[]> {
  const byId = new Map<string, ChecklistSyncData>();
  for (const uid of ids) {
    for (const c of await getUserChecklists(env, uid)) {
      const prev = byId.get(c.cloudId);
      if (!prev || c.updatedAt > prev.updatedAt) byId.set(c.cloudId, c);
    }
  }
  return [...byId.values()];
}

export interface CreditInfo {
  credits: number;
  premium: boolean;
}

export async function getCreditInfo(env: FirestoreEnv, creditUserId: string): Promise<CreditInfo> {
  const o = await getDocument(env, `users/${creditUserId}`);
  if (!o) {
    console.warn("[gisti-tg] credit doc missing for a linked user — showing 0 credits");
    return { credits: 0, premium: false };
  }
  return {
    credits: typeof o["ai_credits"] === "number" ? (o["ai_credits"] as number) : 0,
    premium: o["is_premium"] === true,
  };
}

/** main.py DEFAULT_PREMIUM_DAILY_CREDITS_CAP — `premium_daily_credits_cap` fallback. */
export const DEFAULT_PREMIUM_DAILY_CAP = 300;
/** Contract default for `telegram_premium_price_stars` (Phase 3: 100 ⭐ / 30 days). */
export const DEFAULT_PRICE_STARS = 100;

export interface AiConfig {
  actionCost: number;
  maxInputLength: number;
  /** Premium daily AI-credit refill ceiling — shown in the /premium perks, never hardcoded. */
  premiumDailyCap: number;
  /** Telegram Stars price of the 30-day subscription. */
  priceStars: number;
}

function positive(v: unknown, d: number): number {
  return typeof v === "number" && Number.isFinite(v) && v > 0 ? v : d;
}

/** Positive integer or the default — the same rule as `parsePremiumStatus` and Python `price_stars_or_default`. */
function positiveInt(v: unknown, d: number): number {
  return typeof v === "number" && Number.isInteger(v) && v > 0 ? v : d;
}

const PRICE_STARS_KEY = "telegram_premium_price_stars";

/** Plain-object → config with defaults (pure; exported for tests). */
export function parseRemoteConfig(o: Record<string, unknown> | null): AiConfig {
  return {
    actionCost: positive(o?.["ai_action_cost"], DEFAULT_AI_ACTION_COST),
    maxInputLength: positive(o?.["ai_analysis_max_input_length"], DEFAULT_MAX_INPUT_LENGTH),
    premiumDailyCap: positive(o?.["premium_daily_credits_cap"], DEFAULT_PREMIUM_DAILY_CAP),
    priceStars: positiveInt(o?.[PRICE_STARS_KEY], DEFAULT_PRICE_STARS),
  };
}

/**
 * Typed Firestore fields of `remote_config/current` → config. The Stars price must be stored as a
 * Firestore INTEGER: Python reads it with `isinstance(value, int)`, so a double — even a whole one
 * like 150.0, which unwraps to the same JS number as 150 — is the default there, and must be here
 * too, or pre-checkout would compare the invoice (Python's price) against a different number and
 * refuse every payment. Other fields parse exactly as `parseRemoteConfig`. Pure; exported for tests.
 */
export function parseRemoteConfigFields(fields: Record<string, FsValue> | null): AiConfig {
  if (fields === null) return parseRemoteConfig(null);
  const o = unwrapFields(fields);
  const price = fields[PRICE_STARS_KEY];
  if (!price || !("integerValue" in price)) delete o[PRICE_STARS_KEY];
  return parseRemoteConfig(o);
}

let aiConfigCache: { value: AiConfig; expiresAt: number } | null = null;

/**
 * `remote_config/current` (the Firestore doc the CFs read), cached ~5 min per isolate.
 * `fresh: true` bypasses the cache — the pre-checkout price check must see what Python just put on
 * the invoice, and a read failure there THROWS (a payment decision must not run on a default).
 */
export async function getAiConfig(env: FirestoreEnv, opts: { fresh?: boolean } = {}): Promise<AiConfig> {
  const now = Date.now();
  if (!opts.fresh && aiConfigCache && aiConfigCache.expiresAt > now) return aiConfigCache.value;
  let value: AiConfig = parseRemoteConfig(null);
  try {
    value = parseRemoteConfigFields(await getDocumentFields(env, "remote_config/current"));
  } catch (e) {
    if (opts.fresh) throw e;
    // The displayed price is informational (the CF charges the real one) — degrade, but say so.
    console.warn(`[gisti-tg] remote_config/current read failed, using defaults: ${e instanceof Error ? e.message : e}`);
    aiConfigCache = { value, expiresAt: now + 30_000 };
    return value;
  }
  aiConfigCache = { value, expiresAt: now + AI_CONFIG_TTL_MS };
  return value;
}

// ── telegram_bot_internal ───────────────────────────────────────────────────────

/** A non-success answer from telegram_bot_internal: HTTP status + the `error` code. */
export class InternalApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly action: string,
  ) {
    super(`telegram_bot_internal ${action} failed (${status}): ${code}`);
    this.name = "InternalApiError";
  }
}

/** The Telegram identity fields every link action carries. IDs are JSON numbers (Python rejects strings). */
export interface TelegramIdentity {
  telegramUserId: number;
  chatId: number;
  username: string | null;
  firstName: string | null;
  languageCode: string | null;
}

export function identityBody(id: TelegramIdentity): Record<string, unknown> {
  return {
    telegram_user_id: id.telegramUserId,
    chat_id: id.chatId,
    ...(id.username ? { username: id.username } : {}),
    ...(id.firstName ? { first_name: id.firstName } : {}),
    ...(id.languageCode ? { language_code: id.languageCode } : {}),
  };
}

/**
 * POST `{action, ...body}` with the derived `X-Gisti-Internal-Key`. Envelope: `{success: true, ...}`
 * on 200, `{success: false, error: "<code>"}` otherwise → InternalApiError(status, code).
 */
export type InternalAction =
  | "consume_app_token"
  | "create_login_token"
  | "unlink"
  | "premium_status"
  | "apply_payment"
  | "subscription_update"
  | "revoke_payment";

export async function callInternal(
  env: BackendEnv,
  action: InternalAction,
  body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const key = await derivedKey(env.TELEGRAM_BOT_TOKEN, INTERNAL_KEY_LABEL);
  let resp: Response;
  try {
    resp = await fetch(`${env.CF_BASE_URL}/telegram_bot_internal`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Gisti-Internal-Key": key },
      body: JSON.stringify({ action, ...body }),
      signal: AbortSignal.timeout(INTERNAL_TIMEOUT_MS),
    });
  } catch (e) {
    throw new InternalApiError(0, `network: ${e instanceof Error ? e.message : "unknown"}`, action);
  }
  let data: Record<string, unknown> = {};
  try {
    data = (await resp.json()) as Record<string, unknown>;
  } catch {
    // non-JSON → status-based error below
  }
  if (!resp.ok || data["success"] !== true) {
    const code = typeof data["error"] === "string" ? (data["error"] as string) : resp.statusText || "error";
    throw new InternalApiError(resp.status, code, action);
  }
  return data;
}

// ── generate_checklist ──────────────────────────────────────────────────────────

/**
 * POST generate_checklist — the cf.ts contract (same body, same `{success,...}` unwrap, CfError on
 * failure) plus `mime_type` for audio. The CF does not read `mime_type` yet (it labels every audio
 * as audio/mp4); sending it now means no worker change once it does. A local copy rather than
 * cf.ts `generateChecklist` only because that function has no slot for the extra field.
 */
export async function generateChecklistViaCf(
  env: BackendEnv,
  args: {
    userId: string;
    prompt: string;
    requestId: string;
    inputType: "text" | "url" | "image_base64" | "audio_base64";
    inputData: string;
    locale: string;
    mimeType?: string;
  },
): Promise<GenerateChecklistResult & { creditsKnown: boolean }> {
  const fn = "generate_checklist";
  const body = JSON.stringify({
    user_id: args.userId,
    prompt: args.prompt,
    input_type: args.inputType,
    input_data: args.inputData,
    locale: args.locale,
    request_id: args.requestId,
    ...(args.mimeType ? { mime_type: args.mimeType } : {}),
  });
  let resp: Response;
  try {
    resp = await fetch(`${env.CF_BASE_URL}/${fn}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
      signal: AbortSignal.timeout(GENERATE_TIMEOUT_MS),
    });
  } catch (e) {
    throw new CfError(0, `network error: ${e instanceof Error ? e.message : "unknown"}`, fn);
  }
  let data: Record<string, unknown> = {};
  try {
    data = (await resp.json()) as Record<string, unknown>;
  } catch {
    // non-JSON → status-based error below
  }
  if (!resp.ok || data["success"] !== true) {
    const msg = typeof data["error"] === "string" ? (data["error"] as string) : resp.statusText;
    throw new CfError(resp.status, msg, fn);
  }
  return {
    checklistName: typeof data["checklist_name"] === "string" ? (data["checklist_name"] as string) : "New Checklist",
    items: Array.isArray(data["items"]) ? (data["items"] as GeneratedNode[]) : [],
    summary: typeof data["summary"] === "string" ? (data["summary"] as string) : "",
    confidence: typeof data["confidence"] === "number" ? (data["confidence"] as number) : 0.8,
    aiCredits: typeof data["ai_credits"] === "number" ? (data["ai_credits"] as number) : 0,
    creditsKnown: typeof data["ai_credits"] === "number",
  };
}
