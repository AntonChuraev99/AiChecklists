/**
 * Write-path encoders for the Gisti MCP server — the byte-for-byte mirror of the Kotlin
 * sync serializer. This is the single most drift-prone artifact (the invariant logic now
 * lives in THREE places: Kotlin `toMap`, Kotlin `toChecklistSyncData`, and here). The
 * Kotlin↔TS byte-equality contract test (encode.test.ts) is the gate that guards it.
 *
 * Contract source (verified 2026-07-10 against file:line — see design doc):
 *   Two encoding layers with DIFFERENT rules:
 *   1. itemsJson + checklist-level repeatRule = kotlinx.serialization `Json { ignoreUnknownKeys=true }`
 *      → `encodeDefaults=false` → fields equal to their Kotlin default are OMITTED; keys in
 *      primary-constructor order; enums by constant name (ITEM/FOLDER uppercase, no @SerialName).
 *   2. The Firestore document map (`toMap`) = a hand-written map → EVERY key is ALWAYS present;
 *      null → nullValue (no omit at the document layer). Timestamps are raw int64 millis
 *      (integerValue), never Firestore Timestamp, never doubleValue.
 *
 * Round-trip fidelity: `repeatRule` (item-level, nested object) and `attachments` are carried
 * VERBATIM from the read model, so a decode→encode of an app-written item reproduces its bytes.
 */

import type {
  ChecklistFillItem,
  ChecklistItem,
  ChecklistSyncData,
  FillSyncData,
} from "./model";

// ── Layer 1: itemsJson (kotlinx encodeDefaults=false, constructor order) ─────────

/**
 * Encode ONE template item. Key order (Checklist.kt:47-55): text, checked, id, weekday,
 * priority, type, parentId. Omit when equal to Kotlin default: checked=false, weekday=null,
 * priority=0, type=ITEM, parentId=null. `id` is always emitted (no stable default). `text`
 * required.
 */
function encodeTemplateItem(it: ChecklistItem): Record<string, unknown> {
  const o: Record<string, unknown> = {};
  o["text"] = it.text;
  if (it.checked) o["checked"] = true;
  o["id"] = it.id;
  if (it.weekday !== null) o["weekday"] = it.weekday;
  if (it.priority !== 0) o["priority"] = it.priority;
  if (it.type === "FOLDER") o["type"] = "FOLDER";
  if (it.parentId !== null) o["parentId"] = it.parentId;
  return o;
}

/** Serialize a template item list to the exact `itemsJson` bytes the app writes. */
export function encodeTemplateItems(items: ChecklistItem[]): string {
  return JSON.stringify(items.map(encodeTemplateItem));
}

/**
 * Encode ONE fill item. Key order (Checklist.kt:152-169): text, checked, note, id, weekday,
 * priority, reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
 * attachments, templateItemId. ⚠ `checked` has NO Kotlin default → ALWAYS emitted (even false).
 * Everything else omits its default. `repeatRule` (nested object) and `attachments` pass through
 * verbatim.
 */
function encodeFillItem(it: ChecklistFillItem): Record<string, unknown> {
  const o: Record<string, unknown> = {};
  o["text"] = it.text;
  o["checked"] = it.checked; // always — required field, no default
  if (it.note !== null) o["note"] = it.note;
  o["id"] = it.id;
  if (it.weekday !== null) o["weekday"] = it.weekday;
  if (it.priority !== 0) o["priority"] = it.priority;
  if (it.reminderAt !== null) o["reminderAt"] = it.reminderAt;
  if (it.repeatRule !== null && it.repeatRule !== undefined) o["repeatRule"] = it.repeatRule;
  if (it.repeatTimeOfDayMinutes !== null) o["repeatTimeOfDayMinutes"] = it.repeatTimeOfDayMinutes;
  if (it.repeatNextAt !== null) o["repeatNextAt"] = it.repeatNextAt;
  if (it.repeatOccurrenceCount !== 0) o["repeatOccurrenceCount"] = it.repeatOccurrenceCount;
  if (Array.isArray(it.attachments) && it.attachments.length > 0) o["attachments"] = it.attachments;
  if (it.templateItemId !== null) o["templateItemId"] = it.templateItemId;
  return o;
}

/** Serialize a fill item list to the exact `itemsJson` bytes the app writes. */
export function encodeFillItems(items: ChecklistFillItem[]): string {
  return JSON.stringify(items.map(encodeFillItem));
}

// ── Layer 2: Firestore document map (all keys always present) ────────────────────

/**
 * Build the plain document object for one fill (embedded in the checklist doc's `fills[]`).
 * Key order = FillSyncData.toMap (AndroidFirestoreSyncDataSource.kt:40-49). All keys present.
 */
function toFillDoc(f: FillSyncData): Record<string, unknown> {
  return {
    cloudId: f.cloudId,
    name: f.name,
    itemsJson: f.itemsJson,
    coverImagePath: f.coverImagePath, // null → nullValue
    createdAt: f.createdAt,
    isDefault: f.isDefault,
    updatedAt: f.updatedAt,
    isDeleted: f.isDeleted,
  };
}

/**
 * Build the plain Firestore document object for a checklist. Key order = ChecklistSyncData.toMap
 * (AndroidFirestoreSyncDataSource.kt:51-68). EVERY key present (null → nullValue). `itemsJson`
 * here is the already-encoded string on the model — the caller re-encodes it (encodeTemplateItems)
 * whenever it mutates the item tree.
 */
export function toChecklistDoc(c: ChecklistSyncData): Record<string, unknown> {
  return {
    cloudId: c.cloudId,
    name: c.name,
    itemsJson: c.itemsJson,
    reminderAt: c.reminderAt,
    repeatRule: c.repeatRule,
    repeatTimeOfDayMinutes: c.repeatTimeOfDayMinutes,
    repeatNextAt: c.repeatNextAt,
    repeatOccurrenceCount: c.repeatOccurrenceCount,
    separateCompleted: c.separateCompleted,
    position: c.position,
    autoDeleteCompleted: c.autoDeleteCompleted,
    viewMode: c.viewMode,
    foldersEnabled: c.foldersEnabled,
    updatedAt: c.updatedAt,
    isDeleted: c.isDeleted,
    fills: c.fills.map(toFillDoc),
  };
}

/** Ordered top-level field paths of the checklist doc — used as the Firestore PATCH updateMask. */
export const CHECKLIST_DOC_FIELD_PATHS: readonly string[] = [
  "cloudId",
  "name",
  "itemsJson",
  "reminderAt",
  "repeatRule",
  "repeatTimeOfDayMinutes",
  "repeatNextAt",
  "repeatOccurrenceCount",
  "separateCompleted",
  "position",
  "autoDeleteCompleted",
  "viewMode",
  "foldersEnabled",
  "updatedAt",
  "isDeleted",
  "fills",
];

// ── Layer 2b: plain doc → Firestore REST typed values ────────────────────────────

/** A Firestore REST typed value ({stringValue}, {integerValue}, {mapValue}, ...). */
export type FsTypedValue = Record<string, unknown>;

/**
 * Wrap a plain JS value into the Firestore REST value format. The Kotlin writer emits only
 * strings / int64 / booleans / arrays / maps / nulls — never doubles — so integers map to
 * integerValue (string-encoded per the REST spec). A non-integer number falls back to
 * doubleValue defensively, but the app never produces one.
 */
export function wrapValue(v: unknown): FsTypedValue {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === "string") return { stringValue: v };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") {
    return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  }
  if (Array.isArray(v)) return { arrayValue: { values: v.map(wrapValue) } };
  if (typeof v === "object") return { mapValue: { fields: wrapFields(v as Record<string, unknown>) } };
  return { nullValue: null };
}

/** Wrap a plain object into a Firestore REST `fields` map. */
export function wrapFields(obj: Record<string, unknown>): Record<string, FsTypedValue> {
  const out: Record<string, FsTypedValue> = {};
  for (const [k, val] of Object.entries(obj)) out[k] = wrapValue(val);
  return out;
}

// ── id + mutation helpers ────────────────────────────────────────────────────────

/**
 * A fresh node id. The device uses `"${millis}_${rnd}"`, which collides ~12% over 50 bulk
 * nodes (design doc) — so MCP-created nodes use a UUID instead. Decode tolerates both.
 */
export function newNodeId(): string {
  return crypto.randomUUID();
}

/** A fresh cloudId (checklist / fill document id) — the app uses `Uuid.random().toString()`. */
export function newCloudId(): string {
  return crypto.randomUUID();
}

/**
 * The next monotonic `updatedAt` for a doc being rewritten: at least 1ms past its current value
 * so a same-millisecond write still advances Last-Write-Wins (sync-reorder-nonmonotonic-race).
 */
export function nextUpdatedAt(currentUpdatedAt: number, now: number): number {
  return Math.max(now, currentUpdatedAt + 1);
}
