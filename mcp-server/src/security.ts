/**
 * Public-launch hardening primitives for the Gisti MCP server:
 *  - opaque OAuth `state` backed by KV (replaces the unsigned base64 state — CSRF/tamper guard),
 *  - a best-effort per-user rate limiter (defense-in-depth over the CF's hard credit/daily guard),
 *  - a deterministic `request_id` so a transport/tool retry dedups the idempotent CF credit
 *    reservation instead of double-charging.
 *
 * All KV access goes through the minimal `KvLike` shape so these are unit-testable with a fake KV.
 */

/** The subset of KVNamespace we use (so tests can pass a fake in-memory store). */
export interface KvLike {
  get(key: string): Promise<string | null>;
  put(key: string, value: string, opts?: { expirationTtl?: number }): Promise<void>;
  delete(key: string): Promise<void>;
}

// ── OAuth state (opaque, KV-backed, one-time) ────────────────────────────────────

const STATE_PREFIX = "state:";
const STATE_TTL_SECONDS = 600; // an auth round-trip is seconds; 10 min is generous.

/** Store the OAuth request under a random nonce and return the nonce to carry as `state`. */
export async function storeAuthState(kv: KvLike, oauthReqInfo: unknown): Promise<string> {
  const nonce = crypto.randomUUID();
  await kv.put(`${STATE_PREFIX}${nonce}`, JSON.stringify(oauthReqInfo), {
    expirationTtl: STATE_TTL_SECONDS,
  });
  return nonce;
}

/**
 * Consume the OAuth request for a nonce (single use — deleted on read). Returns null if the
 * nonce is unknown/expired (forged or replayed state) so the caller can reject the callback.
 */
export async function consumeAuthState(kv: KvLike, nonce: string): Promise<unknown | null> {
  if (!nonce) return null;
  const raw = await kv.get(`${STATE_PREFIX}${nonce}`);
  if (raw === null) return null;
  await kv.delete(`${STATE_PREFIX}${nonce}`);
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

// ── Per-user rate limiting (best-effort, KV fixed-window) ─────────────────────────

export interface RateLimitResult {
  allowed: boolean;
  remaining: number;
  /** Seconds until the current window resets. */
  resetSeconds: number;
}

/** Named tiers with their (limit, windowSeconds). CRUD/reads per-minute; AI per-hour. */
export const RATE_TIERS = {
  read: { limit: 180, windowSeconds: 60 },
  write: { limit: 60, windowSeconds: 60 },
  ai: { limit: 30, windowSeconds: 3600 },
} as const;

export type RateTier = keyof typeof RATE_TIERS;

/**
 * Fixed-window counter in KV. Best-effort only: KV is eventually consistent and the read-then-write
 * isn't atomic, so a burst can slip a few over — that's acceptable because the REAL money/abuse
 * guard is the Cloud Function's transactional credit + daily-usage limit. This tier just slows
 * hammering of the worker/Firestore. Min KV TTL is 60s, so every window is ≥ 60s.
 */
export async function rateLimit(
  kv: KvLike,
  key: string,
  limit: number,
  windowSeconds: number,
  now: number,
): Promise<RateLimitResult> {
  const k = `rl:${key}`;
  const windowMs = windowSeconds * 1000;
  let windowStart = now;
  let count = 0;

  const raw = await kv.get(k);
  if (raw) {
    try {
      const p = JSON.parse(raw) as { s?: unknown; c?: unknown };
      if (typeof p.s === "number" && typeof p.c === "number" && now - p.s < windowMs) {
        windowStart = p.s;
        count = p.c;
      }
    } catch {
      /* corrupt entry → treat as a fresh window */
    }
  }

  const resetSeconds = Math.max(1, Math.ceil((windowStart + windowMs - now) / 1000));
  if (count >= limit) return { allowed: false, remaining: 0, resetSeconds };

  const newCount = count + 1;
  await kv.put(k, JSON.stringify({ s: windowStart, c: newCount }), {
    expirationTtl: Math.max(60, resetSeconds),
  });
  return { allowed: true, remaining: limit - newCount, resetSeconds };
}

// ── Deterministic request_id (idempotent credit reservation on retry) ────────────

/**
 * A stable request_id for an AI action: SHA-256 over the action's identity + a coarse time bucket,
 * so that a transport/tool retry of the SAME action within the bucket produces the SAME id → the
 * CF's idempotent `reserve_credits` returns the recorded balance and does NOT charge twice. A
 * genuinely distinct action (different args) or a retry in the next bucket gets a fresh id.
 */
export async function stableRequestId(parts: string[], bucketMs: number, now: number): Promise<string> {
  const bucket = Math.floor(now / bucketMs);
  const input = [...parts, String(bucket)].join("|");
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input));
  const bytes = new Uint8Array(digest);
  let hex = "";
  for (let i = 0; i < bytes.length; i++) hex += bytes[i]!.toString(16).padStart(2, "0");
  return hex.slice(0, 32);
}

/** The time bucket for request_id dedup — a retry within this window is treated as the same action. */
export const REQUEST_ID_BUCKET_MS = 60_000;
