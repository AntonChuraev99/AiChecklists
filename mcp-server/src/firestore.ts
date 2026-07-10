/**
 * Firestore + Google auth access layer for the Gisti MCP server, built for the
 * Cloudflare Workers runtime (NO firebase-admin / Node — see design doc).
 *
 * Auth: a service-account self-signed JWT (RS256 via Web Crypto SubtleCrypto) is
 * exchanged at oauth2.googleapis.com/token for a short-lived access token, cached
 * in-module for its lifetime. Everything else is Firestore REST.
 *
 * Identity: resolved by `google_email` (stored on the user doc by link_google_account),
 * so the service account needs only `roles/datastore.user` — no Identity Toolkit access.
 *
 * Phase 0 = read path only (resolve user, list/get checklists). Write (patch) lands
 * in Phase 2. Design: docs/active/gisti-mcp-server-design-2026-07-10.md.
 */

import type { ChecklistSyncData, FillSyncData } from "./model";

const PROJECT_ID = "aichecklists-40230";
const FIRESTORE_BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/datastore";

/** Shape of the service-account JSON key (only the fields we use). */
interface ServiceAccount {
  client_email: string;
  private_key: string; // PEM, "-----BEGIN PRIVATE KEY----- ..."
}

/** Minimal env contract this layer needs. */
export interface FirestoreEnv {
  /** The service-account JSON key, as a single JSON string (wrangler secret). */
  FIREBASE_SERVICE_ACCOUNT: string;
}

// ── base64 / base64url helpers (Workers has atob/btoa) ──────────────────────────

function base64UrlFromBytes(bytes: Uint8Array): string {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]!);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlFromString(s: string): string {
  return base64UrlFromBytes(new TextEncoder().encode(s));
}

/** Decode a base64 (standard) string to bytes. ArrayBuffer-backed for Web Crypto BufferSource. */
function bytesFromBase64(b64: string): Uint8Array<ArrayBuffer> {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

/** Strip a PEM wrapper and base64-decode the body to raw DER (PKCS8) bytes. */
function pkcs8DerFromPem(pem: string): Uint8Array<ArrayBuffer> {
  const body = pem
    .replace(/-----BEGIN [^-]+-----/, "")
    .replace(/-----END [^-]+-----/, "")
    .replace(/\s+/g, "");
  return bytesFromBase64(body);
}

// ── Access-token minting (RS256 JWT → token exchange), cached ───────────────────

let cachedToken: { value: string; expiresAt: number } | null = null;

async function importSigningKey(sa: ServiceAccount): Promise<CryptoKey> {
  // pkcs8DerFromPem returns Uint8Array<ArrayBuffer> — a valid BufferSource for importKey.
  const der = pkcs8DerFromPem(sa.private_key);
  return crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

/** Mint (or return cached) a Google access token for the datastore scope. */
export async function getAccessToken(env: FirestoreEnv): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt - 60 > now) return cachedToken.value;

  const sa = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT) as ServiceAccount;

  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: sa.client_email,
    scope: SCOPE,
    aud: TOKEN_ENDPOINT,
    iat: now,
    exp: now + 3600,
  };
  const signingInput = `${base64UrlFromString(JSON.stringify(header))}.${base64UrlFromString(
    JSON.stringify(claims),
  )}`;

  const key = await importSigningKey(sa);
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput),
  );
  const jwt = `${signingInput}.${base64UrlFromBytes(new Uint8Array(sig))}`;

  const resp = await fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!resp.ok) {
    throw new Error(`token exchange failed: ${resp.status} ${await resp.text()}`);
  }
  const data = (await resp.json()) as { access_token: string; expires_in: number };
  cachedToken = { value: data.access_token, expiresAt: now + data.expires_in };
  return data.access_token;
}

// ── Firestore REST value unwrapping ─────────────────────────────────────────────

type FsValue = Record<string, unknown>;

/** Convert a Firestore REST typed value to a plain JS value. */
function unwrapValue(v: FsValue | undefined): unknown {
  if (!v) return null;
  if ("nullValue" in v) return null;
  if ("stringValue" in v) return v.stringValue;
  if ("booleanValue" in v) return v.booleanValue;
  if ("integerValue" in v) return Number(v.integerValue); // REST returns int as string
  if ("doubleValue" in v) return v.doubleValue;
  if ("timestampValue" in v) return v.timestampValue;
  if ("mapValue" in v) return unwrapFields((v.mapValue as { fields?: Record<string, FsValue> }).fields);
  if ("arrayValue" in v) {
    const values = (v.arrayValue as { values?: FsValue[] }).values ?? [];
    return values.map((e) => unwrapValue(e));
  }
  return null;
}

/** Convert a Firestore `fields` map to a plain object. */
function unwrapFields(fields: Record<string, FsValue> | undefined): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  if (!fields) return out;
  for (const [k, v] of Object.entries(fields)) out[k] = unwrapValue(v);
  return out;
}

async function firestoreFetch(
  env: FirestoreEnv,
  path: string,
  init?: RequestInit,
): Promise<unknown> {
  const token = await getAccessToken(env);
  const resp = await fetch(`${FIRESTORE_BASE}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });
  if (!resp.ok) {
    throw new Error(`firestore ${init?.method ?? "GET"} ${path} failed: ${resp.status} ${await resp.text()}`);
  }
  return resp.json();
}

// ── Public read API ─────────────────────────────────────────────────────────────

/**
 * Resolve ALL candidate user_ids for the Google email proven by OAuth.
 *
 * Gisti keeps TWO docs per person (identity divergence, cf. project memory
 * `credits-shared-self-healing`): a device-registration doc whose ID is a UUID and which
 * carries the `google_email`/`google_uid` FIELDS, and a google-keyed doc whose ID IS the
 * Firebase `google_uid` and which actually holds the checklists (its identity fields are
 * empty). Resolving by the `google_email` field alone lands on the (often empty) device doc.
 *
 * So we return the UNION: every doc matching `google_email == email`, PLUS each of their
 * `google_uid` field values (the id of the checklist-bearing doc). Callers union checklists
 * across all candidates. Empty array → no linked user (caller → "sign in first").
 */
export async function resolveUserIds(env: FirestoreEnv, email: string): Promise<string[]> {
  const body = {
    structuredQuery: {
      from: [{ collectionId: "users" }],
      where: {
        fieldFilter: { field: { fieldPath: "google_email" }, op: "EQUAL", value: { stringValue: email } },
      },
      limit: 5,
    },
  };
  const result = (await firestoreFetch(env, ":runQuery", {
    method: "POST",
    body: JSON.stringify(body),
  })) as Array<{ document?: { name: string; fields?: { google_uid?: { stringValue?: string } } } }>;

  const ids = new Set<string>();
  for (const row of result) {
    if (!row.document?.name) continue;
    const docId = row.document.name.split("/").pop(); // device-registration doc
    if (docId) ids.add(docId);
    const guid = row.document.fields?.google_uid?.stringValue; // google-keyed data doc id
    if (guid) ids.add(guid);
  }
  return [...ids];
}

/** Back-compat single-id resolve (diagnostic scripts). Prefer resolveUserIds. */
export async function resolveUserIdByEmail(env: FirestoreEnv, email: string): Promise<string | null> {
  const [first] = await resolveUserIds(env, email);
  return first ?? null;
}

/** Map a raw Firestore checklist doc (fields already unwrapped) to ChecklistSyncData. */
function toChecklistSyncData(cloudId: string, o: Record<string, unknown>): ChecklistSyncData {
  const str = (k: string, d = ""): string => (typeof o[k] === "string" ? (o[k] as string) : d);
  const num = (k: string): number | null => (typeof o[k] === "number" ? (o[k] as number) : null);
  const numD = (k: string, d = 0): number => (typeof o[k] === "number" ? (o[k] as number) : d);
  const bool = (k: string): boolean => o[k] === true;

  const fills: FillSyncData[] = Array.isArray(o["fills"])
    ? (o["fills"] as Record<string, unknown>[]).map((f) => ({
        cloudId: typeof f["cloudId"] === "string" ? (f["cloudId"] as string) : "",
        name: typeof f["name"] === "string" ? (f["name"] as string) : "",
        itemsJson: typeof f["itemsJson"] === "string" ? (f["itemsJson"] as string) : "[]",
        coverImagePath: typeof f["coverImagePath"] === "string" ? (f["coverImagePath"] as string) : null,
        createdAt: typeof f["createdAt"] === "number" ? (f["createdAt"] as number) : 0,
        isDefault: f["isDefault"] === true,
        updatedAt: typeof f["updatedAt"] === "number" ? (f["updatedAt"] as number) : 0,
        isDeleted: f["isDeleted"] === true,
      }))
    : [];

  return {
    cloudId,
    name: str("name"),
    itemsJson: str("itemsJson", "[]"),
    reminderAt: num("reminderAt"),
    repeatRule: str("repeatRule") || null,
    repeatTimeOfDayMinutes: num("repeatTimeOfDayMinutes"),
    repeatNextAt: num("repeatNextAt"),
    repeatOccurrenceCount: numD("repeatOccurrenceCount"),
    separateCompleted: bool("separateCompleted"),
    position: numD("position"),
    autoDeleteCompleted: bool("autoDeleteCompleted"),
    viewMode: str("viewMode", "Standard"),
    foldersEnabled: bool("foldersEnabled"),
    updatedAt: numD("updatedAt"),
    isDeleted: bool("isDeleted"),
    fills,
  };
}

/** List all non-deleted checklists for a user (follows pagination). */
export async function getUserChecklists(env: FirestoreEnv, userId: string): Promise<ChecklistSyncData[]> {
  const out: ChecklistSyncData[] = [];
  let pageToken: string | undefined;
  do {
    const qs = new URLSearchParams({ pageSize: "100" });
    if (pageToken) qs.set("pageToken", pageToken);
    const page = (await firestoreFetch(env, `/users/${userId}/checklists?${qs}`)) as {
      documents?: Array<{ name: string; fields?: Record<string, FsValue> }>;
      nextPageToken?: string;
    };
    for (const doc of page.documents ?? []) {
      const cloudId = doc.name.split("/").pop() ?? "";
      const checklist = toChecklistSyncData(cloudId, unwrapFields(doc.fields));
      if (!checklist.isDeleted) out.push(checklist);
    }
    pageToken = page.nextPageToken;
  } while (pageToken);
  return out;
}

/** Fetch a single checklist by cloudId, or null if missing. */
export async function getChecklist(
  env: FirestoreEnv,
  userId: string,
  cloudId: string,
): Promise<ChecklistSyncData | null> {
  try {
    const doc = (await firestoreFetch(env, `/users/${userId}/checklists/${cloudId}`)) as {
      name: string;
      fields?: Record<string, FsValue>;
    };
    return toChecklistSyncData(cloudId, unwrapFields(doc.fields));
  } catch {
    return null; // 404 → not found
  }
}
