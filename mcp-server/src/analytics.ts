/**
 * Amplitude instrumentation for the Gisti MCP server (Amplitude project 786722).
 *
 * WHY Amplitude and not Cloudflare Analytics Engine: the headline question is "do MCP users
 * retain better or worse than app-only users", and that can ONLY be answered by joining onto the
 * same user profile the app already writes. Analytics Engine cannot join to Amplitude, so it
 * cannot answer it at any price. Ingestion is the plain HTTP V2 endpoint via `fetch` — no SDK
 * (none of them target the Workers runtime, and one POST needs no dependency).
 *
 * ── IDENTITY (the whole thing hinges on this) ──────────────────────────────────────────────
 * The app sets Amplitude's `user_id` to the Firestore `users/{doc_id}` id it caches in DataStore
 * — the DEVICE/CREDIT doc id (a lowercase UUID v4), NOT google_uid, NOT the email, no prefix:
 *   composeApp/src/androidMain/.../Analytics.kt:60      `amplitude?.setUserId(userId)`
 *   feature/splash/.../SplashViewModel.kt:75,83,142     the only 3 call sites; the value is
 *                                                       `userDataRepository.getUserData().userId`
 *   feature/user/.../UserDataRepositoryImpl.kt:284      USER_ID_KEY ← `register_user`'s userId
 *   firebase-functions/main.py:936                      `str(uuid.uuid4())` → the users/{doc} id
 * `resolveUserContext().creditUserId` (firestore.ts) resolves to that SAME doc — it is defined as
 * "what the app sends to the Cloud Functions as user_id", which is read from the same DataStore
 * key. So we send it verbatim. The Cloud Functions already emit `push_sent` on exactly this id
 * (firebase-functions/push_promotions.py:207), so all three writers agree.
 *
 * A caller whose Google email has no linked Gisti user has no such id. Rather than drop them
 * (they are a real population — "connected the MCP, never signed into the app"), they are counted
 * under a pseudonymous `device_id` = `mcp_<sha256(email)[0:16]>`: stable, non-reversible, and no
 * email ever reaches Amplitude. `mcp_session_started.linked` splits the two populations.
 *
 * ── PRIVACY (hard requirement — the repo is public and the data is the user's) ─────────────
 * Checklist names, item text, notes, and AI prompts MUST NEVER reach Amplitude. This is enforced
 * structurally, not by discipline: `McpEvent` is a closed discriminated union whose only payloads
 * are a tool name (a literal from the tool registration), reasons drawn from fixed vocabularies
 * (`ToolFailureReason` / `ToolSoftFailReason` / `AuthFailureReason`), durations (numbers), and the
 * MCP client's self-reported name/version — the ONLY non-enumerable strings, and they are forced
 * through `sanitizeClientSlug` / `sanitizeClientVersion` into a bounded `[a-z0-9-]` token before
 * they can be attached. There is no free-text field, so a raw error message — which routinely
 * quotes a checklist name or a Firestore document path — cannot be uploaded without a compile
 * error. `analytics.test.ts` pins the whole payload key allowlist.
 *
 * ── NON-CRITICAL PATH ──────────────────────────────────────────────────────────────────────
 * Analytics must never slow down or fail a tool call. Every send is fire-and-forget and every
 * error is swallowed into a log. Note `ctx.waitUntil()` is deliberately NOT used: these handlers
 * run inside the GistiMCP Durable Object, and per Cloudflare's docs "waitUntil has no effect in
 * Durable Objects" — a DO stays active on its own while there is pending I/O, which an in-flight
 * fetch is. The floating promise is the correct pattern here; waitUntil would be cargo cult.
 */

import { CfError } from "./cf";

/** Amplitude HTTP V2 ingestion endpoint (US project 786722). */
const AMPLITUDE_HTTP_ENDPOINT = "https://api2.amplitude.com/2/httpapi";

/** Amplitude rejects an id shorter than this unless `options.min_id_length` is sent. */
const MIN_ID_LENGTH = 5;

/** The env this layer needs. Optional: an unset key degrades to a no-op, never an error. */
export interface AnalyticsEnv {
  /**
   * Amplitude project 786722 API key — the SAME value the Cloud Functions read as
   * `AMPLITUDE_SERVER_API_KEY` (Secret Manager `amplitude-server-key`). Set with
   * `wrangler secret put AMPLITUDE_SERVER_API_KEY`; never committed (public repo).
   */
  AMPLITUDE_SERVER_API_KEY?: string;
}

/**
 * Why a tool call THREW, as a CLOSED vocabulary — `cf_402` (no credits), `cf_429` (daily limit),
 * `cf_503` (AI off), `cf_network`, `exception` (anything unhandled). Deliberately NOT a free
 * string: a raw `Error.message` can carry user content, and this type is what forbids it.
 */
export type ToolFailureReason = "exception" | "cf_network" | `cf_${number}`;

/**
 * Why a tool call did NOT do what was asked even though it returned normally — the "soft refusals"
 * that used to be indistinguishable from success, because `mcp_tool_called` fires before the
 * handler and `mcp_tool_failed` only fires on a throw. Every one of these is a `textResult(...)`
 * early return in mcp.ts; `softResult()` there is the only way to produce one.
 *
 * `checklist_not_found` / `item_not_found` / `fill_not_found` are the "the agent invented an id"
 * signal — the single most likely reason an MCP session goes nowhere while looking healthy.
 * Also a CLOSED vocabulary: the message shown to the user quotes checklist names, the reason never does.
 */
export type ToolSoftFailReason =
  | "rate_limited"
  | "not_linked"
  | "checklist_not_found"
  | "fill_not_found"
  | "item_not_found"
  | "empty_query"
  | "empty_name"
  | "no_items";

/**
 * Why an OAuth leg did not produce a connected client — one value per rejecting branch in
 * google-handler.ts, so "nobody came" and "people came and bounced" stop looking identical.
 * `google_token_${status}` keeps the upstream HTTP status the way `cf_${status}` does (a 400 from
 * Google's token endpoint is a config bug, a 5xx is Google being down — different stories).
 */
export type AuthFailureReason =
  | "invalid_request"
  | "missing_client_id"
  | "missing_code_or_state"
  | "invalid_state"
  | "state_missing_client_id"
  | "no_id_token"
  | "id_token_unreadable"
  | "no_email"
  | "grant_failed"
  | `google_token_${number}`;

/** The complete set of events this server emits. Adding a field means adding it here first. */
export type McpEvent =
  // ── OAuth funnel (google-handler.ts) — no user identity exists yet, see AUTH_FLOW_DEVICE_ID ──
  | { type: "mcp_auth_started" }
  | { type: "mcp_auth_completed" }
  | { type: "mcp_auth_failed"; reason: AuthFailureReason }
  // ── MCP session + tools (mcp.ts) ─────────────────────────────────────────────────────────────
  | {
      type: "mcp_session_started";
      linked: boolean;
      /** false = the Firestore identity lookup failed, so `linked` is unknown, not "no account". */
      identityResolved: boolean;
      /** Sanitised MCP client slug, e.g. `claude-code`; `unknown` when the client sent none. */
      clientName: string;
      /** Sanitised client version, e.g. `1.2.3`; `unknown` when the client sent none. */
      clientVersion: string;
    }
  | { type: "mcp_tool_called"; toolName: string }
  | {
      type: "mcp_tool_completed";
      toolName: string;
      durationMs: number;
      /** null = the tool did what was asked; otherwise the soft refusal it returned instead. */
      softFail: ToolSoftFailReason | null;
    }
  | { type: "mcp_tool_failed"; toolName: string; reason: ToolFailureReason; durationMs: number };

/** Who the event belongs to. `userId` joins to the app's profile; `deviceId` is the fallback. */
export interface AnalyticsIdentity {
  /**
   * The `users/{doc_id}` id — byte-identical to what the app passes to `setUserId`. Null when the
   * caller's Google email has no linked Gisti user (then only `deviceId` identifies them).
   */
  userId: string | null;
  /** Pseudonymous, stable, non-reversible: `mcp_<sha256(email)[0:16]>`. Never the email itself. */
  deviceId: string;
  /**
   * Whether the Firestore identity lookup actually ran. False = degraded (lookup threw), so
   * `userId: null` means "unknown", NOT "unlinked". Uploaded as `identity_resolved` on
   * `mcp_session_started` so the unlinked-population metric can exclude the unknowns instead of
   * silently counting them as unlinked.
   */
  resolved: boolean;
}

/**
 * The synthetic device every pre-identity OAuth event is attributed to.
 *
 * At `/authorize` — and at every `/callback` branch that rejects before the id_token is read —
 * there is no email, hence no `deviceId`, and Amplitude requires one. A per-attempt random id
 * would work but would mint a brand-new "user" per auth attempt and inflate DAU; one shared
 * constant keeps the inflation at exactly +1 device. Consequence to remember when querying: read
 * the auth funnel as EVENT TOTALS (`mcp_auth_started` vs `mcp_auth_completed` vs
 * `mcp_auth_failed`), never as unique users. `mcp_auth_completed` is the exception — by then the
 * email is known, so it carries the real `mcp_<sha256(email)>` device and joins to the person.
 */
export const AUTH_FLOW_DEVICE_ID = "mcp_auth_flow";

/** Identity for a pre-identity OAuth event (see `AUTH_FLOW_DEVICE_ID`). */
export function authFlowIdentity(): AnalyticsIdentity {
  return { userId: null, deviceId: AUTH_FLOW_DEVICE_ID, resolved: false };
}

/**
 * Identity for a caller whose email is known but whose Gisti user has NOT been looked up (the tail
 * of the OAuth callback — a Firestore round-trip there would buy nothing: the same `deviceId` is
 * what the tool events carry, so Amplitude attaches this event to the person on its own).
 */
export async function emailOnlyIdentity(email: string): Promise<AnalyticsIdentity> {
  return { userId: null, deviceId: await pseudonymousDeviceId(email), resolved: false };
}

/** One event in the Amplitude HTTP V2 `events[]` array (only the fields we populate). */
export interface AmplitudeEvent {
  user_id?: string;
  device_id?: string;
  event_type: McpEvent["type"];
  time: number;
  event_properties: Record<string, string | number | boolean>;
}

function hex(bytes: Uint8Array): string {
  let out = "";
  for (let i = 0; i < bytes.length; i++) out += bytes[i]!.toString(16).padStart(2, "0");
  return out;
}

/**
 * A stable pseudonymous id for a Google email. SHA-256 truncated to 64 bits of hex: enough to
 * count distinct unlinked connections, useless for recovering the address.
 */
export async function pseudonymousDeviceId(email: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(email.trim().toLowerCase()));
  return `mcp_${hex(new Uint8Array(digest)).slice(0, 16)}`;
}

/**
 * Map an unknown thrown value onto the closed failure vocabulary. A `CfError` keeps its HTTP
 * status (that's the diagnostic signal — 402 vs 503 are very different stories); everything else
 * collapses to `exception`. The error's MESSAGE is never read: it is logged, never uploaded.
 */
export function failureReason(e: unknown): ToolFailureReason {
  if (e instanceof CfError) return e.status === 0 ? "cf_network" : `cf_${e.status}`;
  return "exception";
}

/** Google's token endpoint said no — keep the status, drop the body (it can echo request params). */
export function googleTokenFailureReason(status: number): AuthFailureReason {
  return `google_token_${Number.isFinite(status) ? Math.trunc(status) : 0}`;
}

// ── MCP client identification (the ONLY non-enumerable strings we upload) ─────────────────────

/** Hard cap on the client slug — short enough that it cannot smuggle a sentence of user content. */
const CLIENT_SLUG_MAX = 24;
/** Hard cap on the client version string. */
const CLIENT_VERSION_MAX = 16;
/** What both sanitisers return when the client sent nothing usable. */
export const UNKNOWN_CLIENT = "unknown";

/**
 * Reduce the MCP client's self-reported `clientInfo.name` to a bounded `[a-z0-9-]` slug
 * ("Claude Code" → `claude-code`).
 *
 * This is a sanitiser, not a formatter: it is the enforcement point that keeps the one
 * non-enumerable string on the wire from becoming a free-text field. An allowlist of known clients
 * was the alternative, but it would report every NEW client as `other` — which is the same
 * unmeasurability this instrumentation exists to remove. A ≤24-char charset-restricted token is
 * the compromise, and the value originates in the client binary, never in user content.
 */
export function sanitizeClientSlug(raw: unknown): string {
  if (typeof raw !== "string") return UNKNOWN_CLIENT;
  const slug = raw
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+/, "")
    .slice(0, CLIENT_SLUG_MAX)
    .replace(/-+$/, "");
  return slug || UNKNOWN_CLIENT;
}

/** Same idea for `clientInfo.version`: digits, letters and separators only, ≤16 chars. */
export function sanitizeClientVersion(raw: unknown): string {
  if (typeof raw !== "string") return UNKNOWN_CLIENT;
  const version = raw.toLowerCase().replace(/[^a-z0-9.+-]+/g, "").slice(0, CLIENT_VERSION_MAX);
  return version || UNKNOWN_CLIENT;
}

// ── Soft-failure marker (see ToolSoftFailReason) ──────────────────────────────────────────────

/**
 * Symbol key, so the marker is invisible to `JSON.stringify` and to object spread: the MCP client
 * receives exactly the payload it received before, and the wrapper in mcp.ts can still read why a
 * handler returned early. A `isError` flag on the result would have been visible to the client and
 * changed tool behaviour; this task is observability only.
 */
const SOFT_FAIL_REASON: unique symbol = Symbol("gisti.mcp.softFailReason");

/** Tag a tool result as a soft refusal. Returns the SAME object, so it stays a valid ToolResult. */
export function markSoftFail<T extends object>(result: T, reason: ToolSoftFailReason): T {
  Object.defineProperty(result, SOFT_FAIL_REASON, { value: reason, enumerable: false });
  return result;
}

/** Read back the tag, or null when the handler returned a real result. */
export function softFailReasonOf(value: unknown): ToolSoftFailReason | null {
  if (typeof value !== "object" || value === null) return null;
  const reason = (value as Record<symbol, unknown>)[SOFT_FAIL_REASON];
  return typeof reason === "string" ? (reason as ToolSoftFailReason) : null;
}

/**
 * The event_properties for one event — exhaustive `switch`, so a new variant of `McpEvent` cannot
 * be added without deciding here what (if anything) it puts on the wire.
 */
function eventProperties(event: McpEvent): Record<string, string | number | boolean> {
  switch (event.type) {
    case "mcp_auth_started":
    case "mcp_auth_completed":
      return {};
    case "mcp_auth_failed":
      return { reason: event.reason };
    case "mcp_session_started":
      return {
        linked: event.linked,
        identity_resolved: event.identityResolved,
        client_name: event.clientName,
        client_version: event.clientVersion,
      };
    case "mcp_tool_called":
      return { tool_name: event.toolName };
    case "mcp_tool_completed":
      // `outcome` is what makes a soft refusal countable; `reason` only rides along when there is
      // one, so an `ok` call cannot be mistaken for a failure with a missing reason.
      return {
        tool_name: event.toolName,
        duration_ms: event.durationMs,
        outcome: event.softFail ? "soft_fail" : "ok",
        ...(event.softFail ? { reason: event.softFail } : {}),
      };
    case "mcp_tool_failed":
      return { tool_name: event.toolName, reason: event.reason, duration_ms: event.durationMs };
  }
}

/**
 * Build the Amplitude payload for one event. Pure + exported so `analytics.test.ts` can assert the
 * privacy contract (that nothing but the whitelisted properties can appear on the wire).
 */
export function buildAmplitudeEvent(
  identity: AnalyticsIdentity,
  event: McpEvent,
  now: number,
): AmplitudeEvent {
  const properties = eventProperties(event);

  // Amplitude 400s the whole request on a too-short id, so only send one that can be accepted.
  const userId = identity.userId && identity.userId.length >= MIN_ID_LENGTH ? identity.userId : undefined;
  return {
    ...(userId ? { user_id: userId } : {}),
    device_id: identity.deviceId,
    event_type: event.type,
    time: now,
    event_properties: properties,
  };
}

/** Log the missing-key warning once per isolate instead of on every single tool call. */
let warnedMissingKey = false;

/**
 * POST one event to Amplitude. Resolves (never rejects) on any failure — the caller is on the
 * fire-and-forget path and a dropped event must cost the user nothing. Mirrors the Cloud
 * Functions' `_emit_amplitude_events` (firebase-functions/main.py:1646): a missing key logs and
 * no-ops so the server keeps working before the secret is configured.
 */
export async function sendMcpEvent(
  env: AnalyticsEnv,
  identity: AnalyticsIdentity,
  event: McpEvent,
  now: number,
): Promise<void> {
  const apiKey = env.AMPLITUDE_SERVER_API_KEY;
  if (!apiKey) {
    if (!warnedMissingKey) {
      warnedMissingKey = true;
      console.warn(
        "[gisti-mcp] AMPLITUDE_SERVER_API_KEY unset — MCP analytics disabled (no mcp_* events). " +
          "Set it with `wrangler secret put AMPLITUDE_SERVER_API_KEY` to measure MCP usage.",
      );
    }
    return;
  }
  try {
    const resp = await fetch(AMPLITUDE_HTTP_ENDPOINT, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ api_key: apiKey, events: [buildAmplitudeEvent(identity, event, now)] }),
    });
    if (!resp.ok) {
      // 400 = malformed/short id, 413 = too big, 429 = throttled, 5xx = Amplitude. All non-fatal.
      console.warn(`[gisti-mcp] amplitude upload non-200: ${resp.status} ${(await resp.text()).slice(0, 200)}`);
    }
  } catch (e) {
    console.warn(`[gisti-mcp] amplitude upload failed: ${e instanceof Error ? e.message : "unknown"}`);
  }
}
