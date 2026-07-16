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
 * are a tool name (a literal from the tool registration) and a `ToolFailureReason` drawn from a
 * fixed vocabulary. There is no free-text field, so a raw error message — which routinely quotes a
 * checklist name or a Firestore document path — cannot be attached without a compile error.
 * `analytics.test.ts` pins this.
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
 * Why a tool call failed, as a CLOSED vocabulary — `cf_402` (no credits), `cf_429` (daily limit),
 * `cf_503` (AI off), `cf_network`, `exception` (anything unhandled). Deliberately NOT a free
 * string: a raw `Error.message` can carry user content, and this type is what forbids it.
 */
export type ToolFailureReason = "exception" | "cf_network" | `cf_${number}`;

/** The complete set of events this server emits. Adding a field means adding it here first. */
export type McpEvent =
  | { type: "mcp_session_started"; linked: boolean }
  | { type: "mcp_tool_called"; toolName: string }
  | { type: "mcp_tool_failed"; toolName: string; reason: ToolFailureReason };

/** Who the event belongs to. `userId` joins to the app's profile; `deviceId` is the fallback. */
export interface AnalyticsIdentity {
  /**
   * The `users/{doc_id}` id — byte-identical to what the app passes to `setUserId`. Null when the
   * caller's Google email has no linked Gisti user (then only `deviceId` identifies them).
   */
  userId: string | null;
  /** Pseudonymous, stable, non-reversible: `mcp_<sha256(email)[0:16]>`. Never the email itself. */
  deviceId: string;
}

/** One event in the Amplitude HTTP V2 `events[]` array (only the fields we populate). */
export interface AmplitudeEvent {
  user_id?: string;
  device_id?: string;
  event_type: McpEvent["type"];
  time: number;
  event_properties: Record<string, string | boolean>;
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

/**
 * Build the Amplitude payload for one event. Pure + exported so `analytics.test.ts` can assert the
 * privacy contract (that nothing but the whitelisted properties can appear on the wire).
 */
export function buildAmplitudeEvent(
  identity: AnalyticsIdentity,
  event: McpEvent,
  now: number,
): AmplitudeEvent {
  const properties: Record<string, string | boolean> =
    event.type === "mcp_session_started"
      ? { linked: event.linked }
      : event.type === "mcp_tool_called"
        ? { tool_name: event.toolName }
        : { tool_name: event.toolName, reason: event.reason };

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
