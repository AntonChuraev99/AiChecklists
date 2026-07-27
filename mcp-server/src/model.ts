/**
 * Domain model + `itemsJson` codec for the Gisti MCP server.
 *
 * This is a faithful TypeScript mirror of the Kotlin sync schema so the MCP can
 * read (Phase 0) and later write (Phase 2) the exact same Firestore documents the
 * app writes. Source of truth:
 *   - feature/checklist/.../domain/model/Checklist.kt          (ChecklistItem, ChecklistFillItem)
 *   - feature/checklist/.../domain/model/ChecklistNodeType.kt  (enum ITEM|FOLDER)
 *   - feature/checklist/.../data/sync/ChecklistSyncData.kt     (envelope fields)
 *   - composeApp/.../sync/AndroidFirestoreSyncDataSource.kt    (toMap / toChecklistSyncData)
 *
 * kotlinx.serialization config on the app side is `Json { ignoreUnknownKeys = true }`
 * (SyncRepositoryImpl:53) with kotlinx defaults — meaning:
 *   - `encodeDefaults = false`  → fields equal to their Kotlin default are OMITTED from JSON.
 *   - enums serialized by constant name (no @SerialName): "ITEM" / "FOLDER".
 *   - decode is tolerant: absent field → its default.
 *
 * This module works on PLAIN JS objects. Un-wrapping the Firestore REST value format
 * ({stringValue,integerValue,arrayValue,...}) is the firestore layer's job, not this one.
 *
 * Phase 0 = decoders only (read path). Encoders (write path, omit-defaults) land in
 * Phase 2 alongside the Kotlin↔TS byte-equality contract test — see the design doc:
 * docs/active/gisti-mcp-server-design-2026-07-10.md.
 */

// ── Template item tree (checklist.itemsJson = List<ChecklistItem>) ──────────────

export type ChecklistNodeType = "ITEM" | "FOLDER";

/** A node in the template folder tree. Leaf (ITEM) or container (FOLDER). */
export interface ChecklistItem {
  text: string;
  checked: boolean; // default false
  id: string; // "${millis}_${random}" on device; UUID for MCP-created nodes (collision-safe)
  weekday: number | null; // ISO 1..7, only when viewMode=Weekly
  priority: number; // 0 = normal, 1 = starred
  type: ChecklistNodeType; // default ITEM
  parentId: string | null; // parent FOLDER id; null = checklist root
}

/** State of one item inside a fill (fill.itemsJson = List<ChecklistFillItem>). */
export interface ChecklistFillItem {
  text: string;
  checked: boolean;
  note: string | null;
  id: string;
  weekday: number | null;
  priority: number;
  reminderAt: number | null;
  /**
   * Item-level ReminderRepeatRule — a NESTED JSON object in the fill's itemsJson (unlike the
   * checklist-level repeatRule which is a stringified JSON string). Preserved VERBATIM (raw
   * parsed value) so a read-modify-write re-emits its exact bytes; the MCP never edits it.
   */
  repeatRule: unknown;
  repeatTimeOfDayMinutes: number | null;
  repeatNextAt: number | null;
  repeatOccurrenceCount: number;
  attachments: unknown[]; // Attachment[] — preserved verbatim for round-trip fidelity
  /** Stable link to the template ChecklistItem.id — the reconciliation key. */
  templateItemId: string | null;
}

// ── Firestore envelope (users/{userId}/checklists/{cloudId}) ────────────────────

export interface FillSyncData {
  cloudId: string;
  name: string;
  itemsJson: string;
  coverImagePath: string | null;
  createdAt: number;
  isDefault: boolean;
  updatedAt: number;
  isDeleted: boolean;
}

export interface ChecklistSyncData {
  cloudId: string;
  name: string;
  itemsJson: string;
  reminderAt: number | null;
  repeatRule: string | null;
  repeatTimeOfDayMinutes: number | null;
  repeatNextAt: number | null;
  repeatOccurrenceCount: number;
  separateCompleted: boolean;
  position: number;
  autoDeleteCompleted: boolean;
  viewMode: string; // "Standard" | ...
  foldersEnabled: boolean;
  updatedAt: number;
  isDeleted: boolean;
  /**
   * Marks the app's auto-created system Inbox (v2 nav arm quick-capture zone). Optional because
   * documents written before the flag existed simply omit the key.
   *
   * MCP hides these rows at BOTH gates — `getUserChecklists` (discovery: list/search/name
   * resolution) and `findChecklistWithOwner` (every raw-`checklistId` read and mutation). The Inbox
   * is an internal capture surface, not a project, and exposing it would let any MCP client
   * list/rename/write to it. It is deliberately
   * absent from `toChecklistDoc` / `CHECKLIST_DOC_FIELD_PATHS` — the worker never writes the flag,
   * and a masked PATCH preserves fields outside the mask, so an app-set `isInbox` survives our
   * writes untouched. If you ever add it to the mask you MUST add it to the doc builder too, or
   * every PATCH writes null over it.
   */
  isInbox?: boolean;
  fills: FillSyncData[];
}

// ── Decoders: itemsJson string → typed items (read path) ────────────────────────

/** Coerce a possibly-absent JSON value to a number, or null. */
function numOrNull(v: unknown): number | null {
  return typeof v === "number" ? v : null;
}

function decodeTemplateItem(raw: Record<string, unknown>): ChecklistItem {
  const rawType = raw["type"];
  return {
    text: typeof raw["text"] === "string" ? (raw["text"] as string) : "",
    checked: raw["checked"] === true, // absent → false (kotlinx default)
    id: typeof raw["id"] === "string" ? (raw["id"] as string) : "",
    weekday: numOrNull(raw["weekday"]),
    priority: typeof raw["priority"] === "number" ? (raw["priority"] as number) : 0,
    type: rawType === "FOLDER" ? "FOLDER" : "ITEM", // absent/unknown → ITEM
    parentId: typeof raw["parentId"] === "string" ? (raw["parentId"] as string) : null,
  };
}

function decodeFillItem(raw: Record<string, unknown>): ChecklistFillItem {
  return {
    text: typeof raw["text"] === "string" ? (raw["text"] as string) : "",
    checked: raw["checked"] === true,
    note: typeof raw["note"] === "string" ? (raw["note"] as string) : null,
    id: typeof raw["id"] === "string" ? (raw["id"] as string) : "",
    weekday: numOrNull(raw["weekday"]),
    priority: typeof raw["priority"] === "number" ? (raw["priority"] as number) : 0,
    reminderAt: numOrNull(raw["reminderAt"]),
    repeatRule: raw["repeatRule"] ?? null, // nested object — preserved verbatim
    repeatTimeOfDayMinutes: numOrNull(raw["repeatTimeOfDayMinutes"]),
    repeatNextAt: numOrNull(raw["repeatNextAt"]),
    repeatOccurrenceCount:
      typeof raw["repeatOccurrenceCount"] === "number" ? (raw["repeatOccurrenceCount"] as number) : 0,
    attachments: Array.isArray(raw["attachments"]) ? (raw["attachments"] as unknown[]) : [],
    templateItemId:
      typeof raw["templateItemId"] === "string" ? (raw["templateItemId"] as string) : null,
  };
}

/** Parse a checklist's `itemsJson` blob into the typed template tree. Tolerant of "[]"/empty. */
export function parseTemplateItems(itemsJson: string): ChecklistItem[] {
  const arr = safeParseArray(itemsJson);
  return arr.map((n) => decodeTemplateItem(n));
}

/** Parse a fill's `itemsJson` blob into typed fill items. Tolerant of "[]"/empty. */
export function parseFillItems(itemsJson: string): ChecklistFillItem[] {
  const arr = safeParseArray(itemsJson);
  return arr.map((n) => decodeFillItem(n));
}

function safeParseArray(json: string): Record<string, unknown>[] {
  if (!json || json.trim() === "") return [];
  let parsed: unknown;
  try {
    parsed = JSON.parse(json);
  } catch {
    return [];
  }
  if (!Array.isArray(parsed)) return [];
  return parsed.filter((e): e is Record<string, unknown> => typeof e === "object" && e !== null);
}

// ── View helpers (read path convenience) ────────────────────────────────────────

/** The primary fill (isDefault) — the one CRUD ops target. Falls back to first, or null. */
export function defaultFill(checklist: ChecklistSyncData): FillSyncData | null {
  return checklist.fills.find((f) => f.isDefault) ?? checklist.fills[0] ?? null;
}

/** Count of checkable leaf items (excludes FOLDER nodes) in the template. */
export function leafItemCount(checklist: ChecklistSyncData): number {
  return parseTemplateItems(checklist.itemsJson).filter((i) => i.type === "ITEM").length;
}
