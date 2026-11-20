/**
 * Server-side executor of the `chat_agent` tools — pure functions over the user's checklists, no I/O.
 *
 * Semantics mirror the app's dispatcher (composeApp/.../aichat/ToolCallDispatcherImpl.kt) and its
 * argument mapper (feature/aichat/.../agent/AgentToolCallMapper.kt); result shapes mirror
 * AgentToolResultSerializer.kt (minus the app's local Room `checklist_id`, which means nothing here).
 *
 * TWO STEPS, so a queue retry never runs a write twice:
 *   1. `planTool` resolves a call against the current lists into a Plan: the result for the model
 *      plus primitive `Op`s that name their targets by EXACT id (never by fuzzy text again).
 *      The caller persists the plan in Durable Object storage BEFORE it writes anything.
 *   2. `applyOps` applies those ops to the lists in memory. Every op is idempotent — an add whose
 *      deterministic node id is already present is skipped, a remove of an absent id is skipped, a
 *      re-check of a checked row changes nothing — so re-applying a persisted plan after a crash
 *      between the Firestore write and the "applied" mark is harmless.
 *
 * Lookup (dispatcher `resolveChecklistAndFill`, lines 982-1036): case-insensitive substring over
 * the checklist names; the system Inbox never takes part (`getUserChecklists` drops it). 0 matches →
 * not_found, ≥2 → ambiguous (≤6 candidates). NO hint → the most recently UPDATED checklist — the app
 * takes the first by `position` (the list the user has open or put on top); Telegram has no open
 * list, so the bot uses the one the user touched last, and sends `checklists_summary` in the same
 * order so the server's "omit for the active (first) checklist" (main.py:3281) names the same list.
 * Item = the first default-fill row whose text contains `item_text`, case-insensitive.
 *
 * Writes go through the mcp-server mutate functions (`addItem`, `deleteItem`, `toggleItem`,
 * `renameChecklist`, `createEmptyChecklist`), which already keep template + default fill in step.
 * The bot-only helpers below (reminder, row removal, re-id after an add) change an existing fill
 * row ONLY by object spread of the parsed item, so every flag the encoder carries survives
 * (`reminderFullScreen`, `alarmEnabled`, unknown keys).
 */

import { encodeFillItems, encodeTemplateItems, nextUpdatedAt } from "../../mcp-server/src/encode";
import type { OwnedChecklist } from "../../mcp-server/src/firestore";
import {
  parseFillItems,
  parseTemplateItems,
  type ChecklistFillItem,
  type ChecklistSyncData,
} from "../../mcp-server/src/model";
import { addItem, createEmptyChecklist, deleteItem, renameChecklist, toggleItem } from "../../mcp-server/src/mutate";
import { formatLocal, parseWhenIso } from "./timezone";
import { viewFill } from "./views";

export type { OwnedChecklist };

/** One function call as `chat_agent` returns it; echoed back verbatim (thought_signature included). */
export interface AgentToolCall {
  id: string;
  name: string;
  args: Record<string, unknown>;
  thought_signature?: string;
}

export interface AgentToolResult {
  id: string;
  name: string;
  result: Record<string, unknown>;
}

/** The 11 tools of `CHAT_AGENT_TOOL_NAMES` (main.py:3254-3261). `present_options` never reaches here. */
export const AGENT_TOOL_NAMES = [
  "add_item",
  "add_items",
  "create_checklist",
  "complete_item",
  "delete_item",
  "clear_completed_items",
  "set_item_reminder",
  "rename_checklist",
  "move_item",
  "find_items",
  "read_checklist",
] as const;
export type AgentToolName = (typeof AGENT_TOOL_NAMES)[number];

export function isAgentToolName(name: string): name is AgentToolName {
  return (AGENT_TOOL_NAMES as readonly string[]).includes(name);
}

/** Owner decision 2026-11-20: only these ask «Yes / Cancel»; every other write runs at once. */
export const CONFIRM_TOOLS: ReadonlySet<string> = new Set(["delete_item", "clear_completed_items", "move_item"]);

export function needsConfirmation(calls: AgentToolCall[]): boolean {
  return calls.some((c) => CONFIRM_TOOLS.has(c.name));
}

/** Ambiguity candidates cap — ToolCallDispatcherImpl.MAX_AMBIGUOUS_CANDIDATES. */
export const MAX_AMBIGUOUS_CANDIDATES = 6;
/** find_items cap — ToolCallDispatcherImpl.MAX_FIND_RESULTS. */
export const MAX_FIND_RESULTS = 10;

export type ToolStatus = "success" | "not_found" | "ambiguous" | "declined" | "error";

export const DECLINED_RESULT: Readonly<Record<string, unknown>> = Object.freeze({ status: "declined" });

/** One `{"status":"declined"}` per call, in call order — the answer to a cancelled batch. */
export function declinedResults(calls: AgentToolCall[]): AgentToolResult[] {
  return calls.map((c) => ({ id: c.id, name: c.name, result: { ...DECLINED_RESULT } }));
}

/** The analytics bucket of a result (unknown_tool / timezone errors → "error"). */
export function toolStatus(result: Record<string, unknown>): ToolStatus {
  const s = result["status"];
  return s === "success" || s === "not_found" || s === "ambiguous" || s === "declined" ? s : "error";
}

// ── ops ─────────────────────────────────────────────────────────────────────────

/** Fields a moved row carries over (the dispatcher's move copies text, checked, note, priority). */
export interface Carry {
  checked: boolean;
  note: string | null;
  priority: number;
}

export type Op =
  /** New checklist under the data doc; skipped when `cloudId` already exists. */
  | { kind: "create"; cloudId: string; name: string; texts: string[] }
  /** New root items; item i gets the deterministic ids `nodeId(seed, i)` — skipped when present. */
  | { kind: "add"; cloudId: string; texts: string[]; seed: string; carry?: Carry }
  /** Mark done. `key` = templateItemId (or the fill row id for a legacy unlinked row). */
  | { kind: "check"; cloudId: string; key: string }
  /** Remove a row: the template node (mutate.deleteItem, also drops its linked fill rows) + the fill row. */
  | { kind: "remove"; cloudId: string; fillItemId: string; templateItemId: string | null }
  | { kind: "reminder"; cloudId: string; fillItemId: string; at: number }
  | { kind: "rename"; cloudId: string; name: string };

/**
 * What a planned call will actually do, with the RESOLVED names (the row text and list it matched,
 * not the model's fuzzy arguments) — the confirmation card is rendered from this. Absent when the
 * call changes nothing (not_found, ambiguous, already done, unknown tool).
 */
export type PlanPreview =
  | { kind: "add"; texts: string[]; list: string }
  | { kind: "create"; name: string; items: number }
  | { kind: "complete"; item: string; list: string }
  | { kind: "delete"; item: string; list: string }
  | { kind: "clear"; count: number; list: string }
  | { kind: "reminder"; item: string; list: string; at: number }
  | { kind: "rename"; from: string; to: string }
  | { kind: "move"; item: string; from: string; to: string }
  | { kind: "find"; query: string }
  | { kind: "read"; list: string };

export interface Plan {
  result: Record<string, unknown>;
  ops: Op[];
  preview?: PlanPreview;
  /** set_item_reminder refused because the user's zone is unknown → the reply offers /timezone. */
  timezoneUnknown?: boolean;
}

export interface PlanContext {
  /** The user's IANA zone, or null when unknown. */
  timezone: string | null;
  /** Unique per call (`<request_id>:<round>:<index>`) — seeds the deterministic ids of created nodes. */
  seed: string;
}

// ── deterministic ids ───────────────────────────────────────────────────────────

/** cyrb128 — a fast 128-bit non-cryptographic hash (synchronous, unlike WebCrypto). */
function cyrb128(str: string): [number, number, number, number] {
  let h1 = 1779033703;
  let h2 = 3144134277;
  let h3 = 1013904242;
  let h4 = 2773480762;
  for (let i = 0; i < str.length; i++) {
    const k = str.charCodeAt(i);
    h1 = h2 ^ Math.imul(h1 ^ k, 597399067);
    h2 = h3 ^ Math.imul(h2 ^ k, 2869860233);
    h3 = h4 ^ Math.imul(h3 ^ k, 951274213);
    h4 = h1 ^ Math.imul(h4 ^ k, 2716044179);
  }
  h1 = Math.imul(h3 ^ (h1 >>> 18), 597399067);
  h2 = Math.imul(h4 ^ (h2 >>> 22), 2869860233);
  h3 = Math.imul(h1 ^ (h3 >>> 17), 951274213);
  h4 = Math.imul(h2 ^ (h4 >>> 19), 2716044179);
  h1 ^= h2 ^ h3 ^ h4;
  h2 ^= h1;
  h3 ^= h1;
  h4 ^= h1;
  return [h1 >>> 0, h2 >>> 0, h3 >>> 0, h4 >>> 0];
}

/**
 * A UUID-shaped id derived from `seed` (same seed → same id). Node ids are free-form strings to the
 * app (device "millis_rand", MCP UUID); this keeps the UUID shape the MCP-created nodes use.
 */
export function seededUuid(seed: string): string {
  const hex = cyrb128(seed)
    .map((n) => n.toString(16).padStart(8, "0"))
    .join("")
    .split("");
  hex[12] = "4";
  hex[16] = "89ab"[parseInt(hex[16]!, 16) & 3]!;
  const h = hex.join("");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20, 32)}`;
}

export function nodeIds(seed: string, i: number): { templateId: string; fillId: string } {
  return { templateId: seededUuid(`${seed}:t:${i}`), fillId: seededUuid(`${seed}:f:${i}`) };
}

// ── results (AgentToolResultSerializer shapes) ──────────────────────────────────

function success(details: string[] = []): Record<string, unknown> {
  return details.length > 0 ? { status: "success", details } : { status: "success" };
}

function notFound(details: string[], reason?: string): Record<string, unknown> {
  return {
    status: "not_found",
    ...(reason ? { reason } : {}),
    ...(details.length > 0 ? { details } : {}),
  };
}

function ambiguous(names: string[]): Record<string, unknown> {
  return { status: "ambiguous", candidates: names.slice(0, MAX_AMBIGUOUS_CANDIDATES) };
}

/** The app's mapper returns null for an unknown name or a missing/blank required arg → this. */
export function unknownToolResult(name: string): Record<string, unknown> {
  return { status: "unknown_tool", detail: name };
}

/** A plan that writes nothing: a read, a refusal (not_found / ambiguous / unknown_tool) or a no-op success. */
function noWritePlan(result: Record<string, unknown>, extra: Partial<Plan> = {}): Plan {
  return { result, ops: [], ...extra };
}

// ── arguments (AgentToolCallMapper rules: blank = absent) ───────────────────────

function str(args: Record<string, unknown>, key: string): string | null {
  const v = args[key];
  return typeof v === "string" && v.trim().length > 0 ? v : null;
}

function strList(args: Record<string, unknown>, key: string): string[] | null {
  const v = args[key];
  if (!Array.isArray(v)) return null;
  return v.filter((x): x is string => typeof x === "string" && x.trim().length > 0);
}

// ── lookup ──────────────────────────────────────────────────────────────────────

/** The bot's list order: most recently updated first (name breaks ties) — default target + summary. */
export function agentOrder(lists: OwnedChecklist[]): OwnedChecklist[] {
  return [...lists].sort(
    (a, b) => b.checklist.updatedAt - a.checklist.updatedAt || a.checklist.name.localeCompare(b.checklist.name),
  );
}

function includesCi(haystack: string, needle: string): boolean {
  return haystack.toLowerCase().includes(needle.toLowerCase());
}

type Resolved = { list: OwnedChecklist } | { fail: Record<string, unknown> };

function resolveList(lists: OwnedChecklist[], hint: string | null): Resolved {
  const ordered = agentOrder(lists);
  if (hint !== null) {
    const matches = ordered.filter((l) => includesCi(l.checklist.name, hint));
    if (matches.length === 0) return { fail: notFound([hint]) };
    if (matches.length > 1) return { fail: ambiguous(matches.map((m) => m.checklist.name)) };
    return { list: matches[0]! };
  }
  const first = ordered[0];
  return first ? { list: first } : { fail: notFound([]) };
}

function fillRows(c: ChecklistSyncData): ChecklistFillItem[] {
  const fill = viewFill(c);
  return fill ? parseFillItems(fill.itemsJson) : [];
}

function findRow(c: ChecklistSyncData, itemText: string): ChecklistFillItem | null {
  return fillRows(c).find((r) => includesCi(r.text, itemText)) ?? null;
}

/**
 * The template node a row's removal must take with it: its `templateItemId` link when that node
 * exists, else (a legacy unlinked row, or a dangling link) the first node whose text contains
 * `matchText` — the text match the dispatcher always uses (delete_item line 296, move line 862).
 */
function templateFor(c: ChecklistSyncData, row: ChecklistFillItem, matchText: string): string | null {
  const template = parseTemplateItems(c.itemsJson);
  if (row.templateItemId && template.some((t) => t.id === row.templateItemId)) return row.templateItemId;
  return template.find((t) => includesCi(t.text, matchText))?.id ?? null;
}

// ── plan ────────────────────────────────────────────────────────────────────────

/** Resolve one call into its result + ops. Never throws for user data; unknown input → unknown_tool. */
export function planTool(call: AgentToolCall, lists: OwnedChecklist[], ctx: PlanContext): Plan {
  const args = call.args && typeof call.args === "object" ? call.args : {};
  switch (call.name) {
    case "add_item":
      return planAddItem(args, lists, ctx);
    case "add_items":
      return planAddItems(args, lists, ctx);
    case "create_checklist":
      return planCreate(args, ctx);
    case "complete_item":
      return planComplete(args, lists);
    case "delete_item":
      return planDelete(args, lists);
    case "clear_completed_items":
      return planClear(args, lists);
    case "set_item_reminder":
      return planReminder(args, lists, ctx);
    case "rename_checklist":
      return planRename(args, lists);
    case "move_item":
      return planMove(args, lists, ctx);
    case "find_items":
      return planFind(args, lists);
    case "read_checklist":
      return planRead(args, lists);
    default:
      return noWritePlan(unknownToolResult(call.name));
  }
}

/** add_item (dispatcher 235-281): details [text, list] with a hint, [text] without. */
function planAddItem(args: Record<string, unknown>, lists: OwnedChecklist[], ctx: PlanContext): Plan {
  const text = str(args, "item_text");
  if (!text) return noWritePlan(unknownToolResult("add_item"));
  const hint = str(args, "checklist_hint");
  const r = resolveList(lists, hint);
  if ("fail" in r) return noWritePlan(r.fail);
  const c = r.list.checklist;
  return {
    result: success(hint ? [text, c.name] : [text]),
    ops: [{ kind: "add", cloudId: c.cloudId, texts: [text], seed: ctx.seed }],
    preview: { kind: "add", texts: [text], list: c.name },
  };
}

/** add_items (659-684): details [count, list]. */
function planAddItems(args: Record<string, unknown>, lists: OwnedChecklist[], ctx: PlanContext): Plan {
  const texts = strList(args, "item_texts");
  if (!texts || texts.length === 0) return noWritePlan(unknownToolResult("add_items"));
  const r = resolveList(lists, str(args, "checklist_hint"));
  if ("fail" in r) return noWritePlan(r.fail);
  const c = r.list.checklist;
  return {
    result: success([String(texts.length), c.name]),
    ops: [{ kind: "add", cloudId: c.cloudId, texts, seed: ctx.seed }],
    preview: { kind: "add", texts, list: c.name },
  };
}

/**
 * create_checklist (359-387): details [name] for 0/1 items, [name, count] for more. The app's
 * free-tier ceiling (`freeChecklistCeilingReached` → requires_premium) is NOT enforced here — the
 * same known gap as the bot's other create paths and the MCP.
 */
function planCreate(args: Record<string, unknown>, ctx: PlanContext): Plan {
  const name = str(args, "name");
  if (!name) return noWritePlan(unknownToolResult("create_checklist"));
  const texts = strList(args, "initial_items") ?? [];
  return {
    result: success(texts.length > 1 ? [name, String(texts.length)] : [name]),
    ops: [{ kind: "create", cloudId: seededUuid(`${ctx.seed}:list`), name, texts }],
    preview: { kind: "create", name, items: texts.length },
  };
}

/** complete_item (307-336): already done → success [text] and no write. */
function planComplete(args: Record<string, unknown>, lists: OwnedChecklist[]): Plan {
  const text = str(args, "item_text");
  if (!text) return noWritePlan(unknownToolResult("complete_item"));
  const r = resolveList(lists, str(args, "checklist_hint"));
  if ("fail" in r) return noWritePlan(r.fail);
  const c = r.list.checklist;
  const row = findRow(c, text);
  if (!row) return noWritePlan(notFound([text, c.name]));
  if (row.checked) return noWritePlan(success([row.text]));
  return {
    result: success([row.text, c.name]),
    ops: [{ kind: "check", cloudId: c.cloudId, key: row.templateItemId ?? row.id }],
    preview: { kind: "complete", item: row.text, list: c.name },
  };
}

/** delete_item (285-303): details [row text, list]. */
function planDelete(args: Record<string, unknown>, lists: OwnedChecklist[]): Plan {
  const text = str(args, "item_text");
  if (!text) return noWritePlan(unknownToolResult("delete_item"));
  const r = resolveList(lists, str(args, "checklist_hint"));
  if ("fail" in r) return noWritePlan(r.fail);
  const c = r.list.checklist;
  const row = findRow(c, text);
  if (!row) return noWritePlan(notFound([text, c.name]));
  return {
    result: success([row.text, c.name]),
    ops: [{ kind: "remove", cloudId: c.cloudId, fillItemId: row.id, templateItemId: templateFor(c, row, text) }],
    preview: { kind: "delete", item: row.text, list: c.name },
  };
}

/**
 * clear_completed_items (745-792 + ChecklistRepositoryImpl.deleteCompletedItems): no hint and ≥2
 * lists → ambiguous (a bulk delete never guesses). 0 checked → success [list]; else [count, list].
 */
function planClear(args: Record<string, unknown>, lists: OwnedChecklist[]): Plan {
  const hint = str(args, "checklist_hint");
  let target: OwnedChecklist;
  if (hint !== null) {
    const r = resolveList(lists, hint);
    if ("fail" in r) return noWritePlan(r.fail);
    target = r.list;
  } else {
    const ordered = agentOrder(lists);
    if (ordered.length === 0) return noWritePlan(notFound([]));
    if (ordered.length > 1) return noWritePlan(ambiguous(ordered.map((l) => l.checklist.name)));
    target = ordered[0]!;
  }
  const c = target.checklist;
  const done = fillRows(c).filter((row) => row.checked);
  if (done.length === 0) return noWritePlan(success([c.name]));
  const ops: Op[] = done.map((row) => ({
    kind: "remove",
    cloudId: c.cloudId,
    fillItemId: row.id,
    templateItemId: templateFor(c, row, row.text),
  }));
  return {
    result: success([String(done.length), c.name]),
    ops,
    preview: { kind: "clear", count: done.length, list: c.name },
  };
}

/**
 * set_item_reminder (391-410): writes the default-fill row's `reminderAt` only. `when_iso` without
 * an offset is the user's local time; no stored zone → `{"status":"error","detail":"timezone_unknown"}`.
 */
function planReminder(args: Record<string, unknown>, lists: OwnedChecklist[], ctx: PlanContext): Plan {
  const text = str(args, "item_text");
  const whenIso = str(args, "when_iso");
  if (!text || !whenIso) return noWritePlan(unknownToolResult("set_item_reminder"));
  const when = parseWhenIso(whenIso, ctx.timezone);
  if (when.kind === "invalid") return noWritePlan(unknownToolResult("set_item_reminder"));
  if (when.kind === "tz_unknown" || ctx.timezone === null) {
    return noWritePlan({ status: "error", detail: "timezone_unknown" }, { timezoneUnknown: true });
  }
  const r = resolveList(lists, str(args, "checklist_hint"));
  if ("fail" in r) return noWritePlan(r.fail);
  const c = r.list.checklist;
  const row = findRow(c, text);
  if (!row) return noWritePlan(notFound([text, c.name]));
  return {
    result: success([row.text, formatLocal(when.at, ctx.timezone)]),
    ops: [{ kind: "reminder", cloudId: c.cloudId, fillItemId: row.id, at: when.at }],
    preview: { kind: "reminder", item: row.text, list: c.name, at: when.at },
  };
}

/** rename_checklist (707-727): both args required; details [old name, new name]. */
function planRename(args: Record<string, unknown>, lists: OwnedChecklist[]): Plan {
  const hint = str(args, "checklist_hint");
  const newName = str(args, "new_name");
  if (!hint || !newName) return noWritePlan(unknownToolResult("rename_checklist"));
  const r = resolveList(lists, hint);
  if ("fail" in r) return noWritePlan(r.fail);
  const c = r.list.checklist;
  return {
    result: success([c.name, newName]),
    ops: [{ kind: "rename", cloudId: c.cloudId, name: newName }],
    preview: { kind: "rename", from: c.name, to: newName },
  };
}

/**
 * move_item (813-880): source (hint or default) → destination (hint) → the row must exist → same
 * list is a success with no write → a row with a reminder, repeat or attachment is refused as
 * `{"status":"not_found","reason":"move_blocked"}` → add to the destination FIRST, then remove from
 * the source (a failed second half leaves a duplicate, never a lost item).
 */
function planMove(args: Record<string, unknown>, lists: OwnedChecklist[], ctx: PlanContext): Plan {
  const text = str(args, "item_text");
  const toHint = str(args, "to_checklist_hint");
  if (!text || !toHint) return noWritePlan(unknownToolResult("move_item"));
  const src = resolveList(lists, str(args, "from_checklist_hint"));
  if ("fail" in src) return noWritePlan(src.fail);
  const dst = resolveList(lists, toHint);
  if ("fail" in dst) return noWritePlan(dst.fail);
  const source = src.list.checklist;
  const target = dst.list.checklist;
  const row = findRow(source, text);
  if (!row) return noWritePlan(notFound([text, source.name]));
  if (target.cloudId === source.cloudId) return noWritePlan(success([row.text, target.name]));
  if (row.reminderAt !== null || row.repeatRule !== null || row.attachments.length > 0) {
    return noWritePlan(notFound([row.text], "move_blocked"));
  }
  return {
    result: success([row.text, target.name]),
    ops: [
      {
        kind: "add",
        cloudId: target.cloudId,
        texts: [row.text],
        seed: ctx.seed,
        carry: { checked: row.checked, note: row.note, priority: row.priority },
      },
      { kind: "remove", cloudId: source.cloudId, fillItemId: row.id, templateItemId: templateFor(source, row, text) },
    ],
    preview: { kind: "move", item: row.text, from: source.name, to: target.name },
  };
}

/** find_items (451-476): ≤10 hits over every node; details [count, "«item» in list; …" (+N more)]. */
function planFind(args: Record<string, unknown>, lists: OwnedChecklist[]): Plan {
  const query = str(args, "query");
  if (!query) return noWritePlan(unknownToolResult("find_items"));
  const hits: Array<[string, string]> = [];
  for (const { checklist: c } of agentOrder(lists)) {
    const template = parseTemplateItems(c.itemsJson);
    const texts = template.length > 0 ? template.map((t) => t.text) : fillRows(c).map((r) => r.text);
    for (const text of texts) {
      if (!includesCi(text, query)) continue;
      hits.push([c.name, text]);
      if (hits.length >= MAX_FIND_RESULTS) break;
    }
    if (hits.length >= MAX_FIND_RESULTS) break;
  }
  if (hits.length === 0) return noWritePlan(notFound([query]));
  const summary = hits
    .slice(0, 5)
    .map(([list, item]) => `«${item}» in ${list}`)
    .join("; ");
  const suffix = hits.length > 5 ? ` (+${hits.length - 5} more)` : "";
  return noWritePlan(success([String(hits.length), `${summary}${suffix}`]), { preview: { kind: "find", query } });
}

/** read_checklist (688-703): `{"status":"success","checklist":name,"items":[{text,done}]}`. */
function planRead(args: Record<string, unknown>, lists: OwnedChecklist[]): Plan {
  const name = str(args, "name");
  if (!name) return noWritePlan(unknownToolResult("read_checklist"));
  const r = resolveList(lists, name);
  if ("fail" in r) return noWritePlan(r.fail);
  const c = r.list.checklist;
  if (!viewFill(c)) return noWritePlan(notFound([c.name])); // the dispatcher's fill_load_failed
  return noWritePlan(
    {
      status: "success",
      checklist: c.name,
      items: fillRows(c).map((row) => ({ text: row.text, done: row.checked })),
    },
    { preview: { kind: "read", list: c.name } },
  );
}

// ── apply ───────────────────────────────────────────────────────────────────────

function hasTemplateNode(c: ChecklistSyncData, id: string): boolean {
  return parseTemplateItems(c.itemsJson).some((t) => t.id === id);
}

/** mutate.addItem, then re-key the new node to its deterministic ids (+ the moved row's state). */
function addOne(c: ChecklistSyncData, text: string, ids: { templateId: string; fillId: string }, carry: Carry | undefined, now: number): ChecklistSyncData {
  const { checklist, itemId } = addItem(c, text, null, now);
  checklist.itemsJson = encodeTemplateItems(
    parseTemplateItems(checklist.itemsJson).map((t) =>
      t.id === itemId ? { ...t, id: ids.templateId, ...(carry ? { checked: carry.checked } : {}) } : t,
    ),
  );
  const fill = viewFill(checklist);
  if (fill) {
    fill.itemsJson = encodeFillItems(
      parseFillItems(fill.itemsJson).map((f) =>
        f.templateItemId === itemId
          ? {
              ...f,
              id: ids.fillId,
              templateItemId: ids.templateId,
              ...(carry ? { checked: carry.checked, note: carry.note, priority: carry.priority } : {}),
            }
          : f,
      ),
    );
  }
  return checklist;
}

/** Drop the fill row `fillItemId` from the default fill (filter only — no other row is touched). */
function dropFillRow(c: ChecklistSyncData, fillItemId: string, now: number): ChecklistSyncData | null {
  const next = structuredClone(c);
  const fill = viewFill(next);
  if (!fill) return null;
  const items = parseFillItems(fill.itemsJson);
  if (!items.some((i) => i.id === fillItemId)) return null;
  fill.itemsJson = encodeFillItems(items.filter((i) => i.id !== fillItemId));
  fill.updatedAt = nextUpdatedAt(fill.updatedAt, now);
  next.updatedAt = nextUpdatedAt(next.updatedAt, now);
  return next;
}

/** Set `reminderAt` on one fill row by spread; null when the row is gone or already has that time. */
export function setRowReminder(c: ChecklistSyncData, fillItemId: string, at: number, now: number): ChecklistSyncData | null {
  const next = structuredClone(c);
  const fill = viewFill(next);
  if (!fill) return null;
  const items = parseFillItems(fill.itemsJson);
  const idx = items.findIndex((i) => i.id === fillItemId);
  if (idx === -1 || items[idx]!.reminderAt === at) return null;
  items[idx] = { ...items[idx]!, reminderAt: at };
  fill.itemsJson = encodeFillItems(items);
  fill.updatedAt = nextUpdatedAt(fill.updatedAt, now);
  next.updatedAt = nextUpdatedAt(next.updatedAt, now);
  return next;
}

function applyOne(op: Op, c: ChecklistSyncData, now: number): ChecklistSyncData | null {
  switch (op.kind) {
    case "create":
      return null; // handled by applyOps (it adds a list instead of changing one)
    case "add": {
      let next: ChecklistSyncData | null = null;
      for (let i = 0; i < op.texts.length; i++) {
        const ids = nodeIds(op.seed, i);
        const base: ChecklistSyncData = next ?? c;
        if (hasTemplateNode(base, ids.templateId)) continue; // already applied by an earlier attempt
        next = addOne(base, op.texts[i]!, ids, op.carry, now);
      }
      return next;
    }
    case "check":
      if (fillRows(c).some((r) => (r.templateItemId === op.key || r.id === op.key) && r.checked)) return null;
      return toggleItem(c, op.key, true, now);
    case "remove": {
      let next: ChecklistSyncData | null = null;
      if (op.templateItemId && hasTemplateNode(c, op.templateItemId)) next = deleteItem(c, op.templateItemId, now);
      return dropFillRow(next ?? c, op.fillItemId, now) ?? next;
    }
    case "reminder":
      return setRowReminder(c, op.fillItemId, op.at, now);
    case "rename":
      return c.name === op.name ? null : renameChecklist(c, op.name, now);
  }
}

/**
 * Apply ops to `lists` IN PLACE (so the next call of the batch sees them) and return the lists that
 * changed, in the order they first changed — the order to write them (a move writes the
 * destination before the source). A target that vanished since the plan is skipped.
 */
export function applyOps(lists: OwnedChecklist[], ops: Op[], writeUid: string, now: number): OwnedChecklist[] {
  const changed: string[] = [];
  const mark = (cloudId: string): void => {
    if (!changed.includes(cloudId)) changed.push(cloudId);
  };
  for (const op of ops) {
    if (op.kind === "create") {
      if (lists.some((l) => l.checklist.cloudId === op.cloudId)) continue;
      const created = createEmptyChecklist(op.name, op.texts, now);
      created.cloudId = op.cloudId;
      lists.push({ checklist: created, ownerId: writeUid });
      mark(op.cloudId);
      continue;
    }
    const idx = lists.findIndex((l) => l.checklist.cloudId === op.cloudId);
    if (idx === -1) continue;
    const next = applyOne(op, lists[idx]!.checklist, now);
    if (!next) continue;
    lists[idx] = { ...lists[idx]!, checklist: next };
    mark(op.cloudId);
  }
  return changed.map((id) => lists.find((l) => l.checklist.cloudId === id)!);
}
