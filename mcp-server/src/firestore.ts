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
import { CHECKLIST_DOC_FIELD_PATHS, toChecklistDoc, wrapFields } from "./encode";

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

/** One `users` doc matching the caller's email: its own id + the google_uid FIELD it carries. */
interface UserMatch {
  /** The matched doc's id — the device-registration doc (holds credits + identity fields). */
  docId: string;
  /** Its `google_uid` field value — the id of the checklist-bearing doc, or null if unset. */
  googleUid: string | null;
}

/** Run the `users where google_email == email` query and extract (docId, google_uid) per hit. */
async function queryUsersByEmail(env: FirestoreEnv, email: string): Promise<UserMatch[]> {
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

  const matches: UserMatch[] = [];
  for (const row of result) {
    if (!row.document?.name) continue;
    const docId = row.document.name.split("/").pop();
    if (!docId) continue;
    matches.push({ docId, googleUid: row.document.fields?.google_uid?.stringValue ?? null });
  }
  return matches;
}

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
  const matches = await queryUsersByEmail(env, email);
  const ids = new Set<string>();
  for (const m of matches) {
    ids.add(m.docId);
    if (m.googleUid) ids.add(m.googleUid);
  }
  return [...ids];
}

/**
 * Identity split needed by the WRITE phases (CF credit calls vs Firestore writes land on
 * DIFFERENT docs — the #1 trap of this data model):
 *  - `creditUserId` = the device-registration doc (holds credits + is what the APP sends to
 *    the Cloud Functions as `user_id`). AI credit reservation must charge THIS doc.
 *  - `writeUserId`  = the google_uid data doc (holds `checklists`, cross-device source of
 *    truth). ALL checklist mutations/creates target THIS doc. Falls back to the device doc
 *    only if the person has no linked google_uid (degenerate; keeps writes self-consistent).
 *  - `allIds` = union for read fan-out.
 * Returns null when the email is not linked to any Gisti user (caller → "sign in first").
 */
export interface UserContext {
  creditUserId: string;
  writeUserId: string;
  allIds: string[];
}

export async function resolveUserContext(env: FirestoreEnv, email: string): Promise<UserContext | null> {
  const matches = await queryUsersByEmail(env, email);
  if (matches.length === 0) return null;
  // The doc matched by google_email IS the device/credit doc; prefer the first with a
  // google_uid so writes land on the checklist-bearing doc even if a stray device doc sorts first.
  const primary = matches.find((m) => m.googleUid) ?? matches[0]!;
  const ids = new Set<string>();
  for (const m of matches) {
    ids.add(m.docId);
    if (m.googleUid) ids.add(m.googleUid);
  }
  return {
    creditUserId: primary.docId,
    writeUserId: primary.googleUid ?? primary.docId,
    allIds: [...ids],
  };
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
    isInbox: bool("isInbox"),
    fills,
  };
}

/**
 * List all non-deleted, non-Inbox checklists for a user (follows pagination).
 *
 * The system Inbox is filtered out here, which covers every DISCOVERY path: `collectChecklists`
 * in mcp.ts fans this out for `list_checklists`, `search_checklists` and name resolution, so no
 * tool can ever surface or auto-resolve the Inbox by name. It is an internal quick-capture
 * surface, hidden from every picker in the app itself.
 *
 * ⚠ This is NOT the only gate, and it is not the one the write path uses: tools that take a raw
 * `checklistId` go through [findChecklistWithOwner], which carries its own `!isInbox` check for
 * exactly that reason. Both filters are load-bearing — do not drop one on the assumption that the
 * other covers it.
 */
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
      if (!checklist.isDeleted && !checklist.isInbox) out.push(checklist);
    }
    pageToken = page.nextPageToken;
  } while (pageToken);
  return out;
}

/**
 * Fetch a single checklist by cloudId, or null if missing.
 *
 * Deliberately RAW: no `isDeleted` and no `isInbox` filtering. The verification scripts read back
 * soft-deleted and freshly-written docs through this, so a filter here would break them. Every
 * caller reachable from an MCP tool goes through [findChecklistWithOwner], which applies both
 * filters — if you add a new tool call site, use that, not this.
 */
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

// ── Write API (Phase 1/2) ─────────────────────────────────────────────────────────

/** A located checklist plus the user_id doc it actually lives under (mutations write back here). */
export interface OwnedChecklist {
  checklist: ChecklistSyncData;
  /** The `users/{ownerId}` doc holding this checklist — write the mutation back to THIS id. */
  ownerId: string;
}

/**
 * Find a checklist by cloudId across candidate user_ids AND report which doc owns it. Mutations
 * must read-modify-write the SAME doc they were found in (never migrate data between the
 * device-doc and the google_uid-doc), so callers pass ownerId back to `writeChecklist`.
 * Returns the first non-deleted, non-Inbox hit, or null.
 *
 * `!isInbox` is the WRITE-path gate for the system Inbox. Every mutating tool (rename, add_item,
 * toggle_item, delete_checklist, …) resolves its `checklistId` through here, and `get_checklist`
 * reads through here too — so a raw cloudId can neither read nor mutate the Inbox even though the
 * ids are not discoverable through [getUserChecklists]. Filtering in the LIST query alone would
 * have left the whole write path open (defence in depth, not a duplicate check). Callers already
 * degrade to a `No checklist found with id …` message on null, so this stays a visible failure
 * rather than a silent no-op.
 */
export async function findChecklistWithOwner(
  env: FirestoreEnv,
  ids: string[],
  cloudId: string,
): Promise<OwnedChecklist | null> {
  for (const ownerId of ids) {
    const checklist = await getChecklist(env, ownerId, cloudId);
    if (checklist && !checklist.isDeleted && !checklist.isInbox) return { checklist, ownerId };
  }
  return null;
}

/**
 * Create or overwrite a checklist doc at `users/{userId}/checklists/{cloudId}`.
 *
 * PATCH with an `updateMask` of exactly the fields we manage: every managed field is written
 * (create-or-replace them), while any field the app adds in the future that we don't model is
 * left untouched — matching the client's `set(merge = true)`. The caller supplies the fully
 * read-modify-written `ChecklistSyncData` (all managed fields populated) and the owning userId
 * (the google_uid data doc for creates; the located ownerId for mutations).
 */
export async function writeChecklist(
  env: FirestoreEnv,
  userId: string,
  checklist: ChecklistSyncData,
): Promise<void> {
  const doc = toChecklistDoc(checklist);
  const mask = CHECKLIST_DOC_FIELD_PATHS.map((p) => `updateMask.fieldPaths=${encodeURIComponent(p)}`).join(
    "&",
  );
  await firestoreFetch(env, `/users/${userId}/checklists/${checklist.cloudId}?${mask}`, {
    method: "PATCH",
    body: JSON.stringify({ fields: wrapFields(doc) }),
  });
}

/**
 * HARD-delete a checklist document (physically removes it). ⚠ NOT an MCP tool — the app uses a
 * soft `isDeleted` tombstone for cross-device delete propagation, so exposing a hard delete would
 * break sync. Provided only for the verification script's cleanup (leave prod pristine after a
 * test write).
 */
export async function deleteChecklistDoc(env: FirestoreEnv, userId: string, cloudId: string): Promise<void> {
  await firestoreFetch(env, `/users/${userId}/checklists/${cloudId}`, { method: "DELETE" });
}
