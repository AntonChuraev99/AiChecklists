/**
 * Domain mutations for the Gisti MCP write tools — pure functions over ChecklistSyncData that
 * mirror what the Kotlin app does (ChecklistRepositoryImpl), then hand a fully read-modify-written
 * doc to firestore.writeChecklist. Kept separate from mcp.ts so they are unit-testable without
 * any network (mutate.test.ts).
 *
 * Invariants enforced here (from project memory — each was a real sync bug):
 *  - Mutations target the DEFAULT fill (`isDefault`); item state (checked/note) lives on the fill.
 *  - `templateItemId` is the identity the tools speak; every fill item carries it (folder-unlinked-row bug).
 *  - Dirty-parent: any fill change bumps the CHECKLIST `updatedAt` too (touchForSync), not just the fill.
 *  - Monotonic `updatedAt` (nextUpdatedAt), one shared `now` across the two rows on reorder (atomic rule).
 *  - The default fill mirrors EVERY template item, folders included (matches addChecklist).
 */

import type { GeneratedNode } from "./cf";
import {
  encodeFillItems,
  encodeTemplateItems,
  newCloudId,
  newNodeId,
  nextUpdatedAt,
} from "./encode";
import {
  parseFillItems,
  parseTemplateItems,
  type ChecklistFillItem,
  type ChecklistItem,
  type ChecklistSyncData,
  type FillSyncData,
} from "./model";

// ── constructors (Kotlin defaults) ──────────────────────────────────────────────

function newTemplateItem(text: string, parentId: string | null, type: "ITEM" | "FOLDER"): ChecklistItem {
  return { text, checked: false, id: newNodeId(), weekday: null, priority: 0, type, parentId };
}

function newFillItem(text: string, templateItemId: string | null, checked = false): ChecklistFillItem {
  return {
    text,
    checked,
    note: null,
    id: newNodeId(),
    weekday: null,
    priority: 0,
    reminderAt: null,
    repeatRule: null,
    repeatTimeOfDayMinutes: null,
    repeatNextAt: null,
    repeatOccurrenceCount: 0,
    attachments: [],
    templateItemId,
  };
}

/** The default fill mirroring a template item list 1:1 (folders included, all unchecked). */
function buildDefaultFill(templateItems: ChecklistItem[], now: number): FillSyncData {
  const items = templateItems.map((t) => newFillItem(t.text, t.id, false));
  return {
    cloudId: newCloudId(),
    name: "",
    itemsJson: encodeFillItems(items),
    coverImagePath: null,
    createdAt: now,
    isDefault: true,
    updatedAt: now,
    isDeleted: false,
  };
}

/** A fresh checklist envelope with Kotlin defaults. */
function emptyChecklist(name: string, now: number): ChecklistSyncData {
  return {
    cloudId: newCloudId(),
    name,
    itemsJson: "[]",
    reminderAt: null,
    repeatRule: null,
    repeatTimeOfDayMinutes: null,
    repeatNextAt: null,
    repeatOccurrenceCount: 0,
    separateCompleted: false,
    position: 0,
    autoDeleteCompleted: false,
    viewMode: "Standard",
    foldersEnabled: false,
    updatedAt: now,
    isDeleted: false,
    fills: [],
  };
}

/** Assemble a checklist from a flat template list + its mirrored default fill. */
function fromTemplateItems(name: string, templateItems: ChecklistItem[], now: number): ChecklistSyncData {
  const c = emptyChecklist(name, now);
  c.itemsJson = encodeTemplateItems(templateItems);
  c.foldersEnabled = templateItems.some((t) => t.type === "FOLDER");
  c.fills = [buildDefaultFill(templateItems, now)];
  return c;
}

// ── shared mutation helpers ──────────────────────────────────────────────────────

function clone(c: ChecklistSyncData): ChecklistSyncData {
  return structuredClone(c);
}

/** The default fill (isDefault), or the first fill, creating one if the checklist has none. */
function ensureDefaultFill(c: ChecklistSyncData, now: number): FillSyncData {
  let fill = c.fills.find((f) => f.isDefault && !f.isDeleted) ?? c.fills.find((f) => !f.isDeleted);
  if (!fill) {
    fill = buildDefaultFill(parseTemplateItems(c.itemsJson), now);
    c.fills.push(fill);
  }
  return fill;
}

/** Bump the checklist's updatedAt (dirty-parent) monotonically. */
function touchChecklist(c: ChecklistSyncData, now: number): void {
  c.updatedAt = nextUpdatedAt(c.updatedAt, now);
}

/** Bump a fill's updatedAt monotonically. */
function touchFill(f: FillSyncData, now: number): void {
  f.updatedAt = nextUpdatedAt(f.updatedAt, now);
}

// ── Phase 1: AI-write mappers ────────────────────────────────────────────────────

/** Walk the generate_checklist nested tree → a flat List<ChecklistItem> with UUID ids + parentId. */
export function treeToTemplateItems(tree: GeneratedNode[]): ChecklistItem[] {
  const out: ChecklistItem[] = [];
  const walk = (nodes: GeneratedNode[], parentId: string | null): void => {
    for (const n of nodes) {
      const isFolder =
        typeof n === "object" &&
        n !== null &&
        (n as { type?: unknown }).type === "folder" &&
        Array.isArray((n as { children?: unknown }).children);
      if (isFolder) {
        const folder = newTemplateItem(n.text, parentId, "FOLDER");
        out.push(folder);
        walk((n as { children: GeneratedNode[] }).children, folder.id);
      } else {
        out.push(newTemplateItem(n.text, parentId, "ITEM"));
      }
    }
  };
  walk(tree, null);
  return out;
}

/** Build a brand-new checklist from an AI-generated tree (Phase 1 create). */
export function buildAiChecklist(name: string, tree: GeneratedNode[], now: number): ChecklistSyncData {
  return fromTemplateItems(name, treeToTemplateItems(tree), now);
}

/** One flat {text,checked} per default-fill item, in order — the payload sent to analyze CF. */
export function flattenFillForAi(c: ChecklistSyncData, now: number): Array<{ text: string; checked: boolean }> {
  const fill = ensureDefaultFill(clone(c), now); // clone: read-only view, don't mutate the input
  return parseFillItems(fill.itemsJson).map((i) => ({ text: i.text, checked: i.checked }));
}

/**
 * Apply analyze_and_fill_checklist results onto the default fill: `filled_items[i].index` maps to
 * the i-th fill item we sent, setting checked + note while preserving ids + templateItemId.
 * Dirty-parent bump. Returns the read-modify-written checklist.
 */
export function applyFilledItems(
  c: ChecklistSyncData,
  filled: Array<{ index: number; checked: boolean; note: string | null }>,
  now: number,
): ChecklistSyncData {
  const next = clone(c);
  const fill = ensureDefaultFill(next, now);
  const items = parseFillItems(fill.itemsJson);
  for (const f of filled) {
    const item = items[f.index];
    if (!item) continue;
    item.checked = f.checked;
    item.note = f.note ?? null;
  }
  fill.itemsJson = encodeFillItems(items);
  touchFill(fill, now);
  touchChecklist(next, now);
  return next;
}

// ── Phase 2: CRUD ops ────────────────────────────────────────────────────────────

/** Locate a fill item by the template id it mirrors, falling back to its own fill-item id. */
function findFillIndex(items: ChecklistFillItem[], itemId: string): number {
  const byLink = items.findIndex((i) => i.templateItemId === itemId);
  if (byLink !== -1) return byLink;
  return items.findIndex((i) => i.id === itemId);
}

/** Toggle a checklist item's checked state (on the default fill). Null if the item is absent. */
export function toggleItem(c: ChecklistSyncData, itemId: string, checked: boolean, now: number): ChecklistSyncData | null {
  const next = clone(c);
  const fill = ensureDefaultFill(next, now);
  const items = parseFillItems(fill.itemsJson);
  const idx = findFillIndex(items, itemId);
  if (idx === -1) return null;
  items[idx]!.checked = checked;
  fill.itemsJson = encodeFillItems(items);
  touchFill(fill, now);
  touchChecklist(next, now);
  return next;
}

/** Set/clear the note on a checklist item (default fill). Null if the item is absent. */
export function editNote(c: ChecklistSyncData, itemId: string, note: string | null, now: number): ChecklistSyncData | null {
  const next = clone(c);
  const fill = ensureDefaultFill(next, now);
  const items = parseFillItems(fill.itemsJson);
  const idx = findFillIndex(items, itemId);
  if (idx === -1) return null;
  items[idx]!.note = note && note.length > 0 ? note : null;
  fill.itemsJson = encodeFillItems(items);
  touchFill(fill, now);
  touchChecklist(next, now);
  return next;
}

/** Add a new leaf item to the template + mirror it into the default fill. Returns {checklist,itemId}. */
export function addItem(
  c: ChecklistSyncData,
  text: string,
  parentId: string | null,
  now: number,
): { checklist: ChecklistSyncData; itemId: string } {
  const next = clone(c);
  const template = parseTemplateItems(next.itemsJson);
  // Only honor parentId if it points at an existing FOLDER; otherwise add at root.
  const parent = parentId ? template.find((t) => t.id === parentId && t.type === "FOLDER") : null;
  const item = newTemplateItem(text, parent ? parent.id : null, "ITEM");
  template.push(item);
  next.itemsJson = encodeTemplateItems(template);

  const fill = ensureDefaultFill(next, now);
  const fillItems = parseFillItems(fill.itemsJson);
  fillItems.push(newFillItem(text, item.id, false));
  fill.itemsJson = encodeFillItems(fillItems);
  touchFill(fill, now);
  touchChecklist(next, now);
  return { checklist: next, itemId: item.id };
}

/** Rename an item — updates the template item and its mirrored fill item. Null if absent. */
export function renameItem(c: ChecklistSyncData, itemId: string, text: string, now: number): ChecklistSyncData | null {
  const next = clone(c);
  const template = parseTemplateItems(next.itemsJson);
  const t = template.find((i) => i.id === itemId);
  if (!t) return null;
  t.text = text;
  next.itemsJson = encodeTemplateItems(template);

  const fill = ensureDefaultFill(next, now);
  const fillItems = parseFillItems(fill.itemsJson);
  const fi = fillItems.find((i) => i.templateItemId === itemId);
  if (fi) fi.text = text;
  fill.itemsJson = encodeFillItems(fillItems);
  touchFill(fill, now);
  touchChecklist(next, now);
  return next;
}

/**
 * Delete an item: removes the template item + its mirrored fill item. If it was a FOLDER, its
 * direct children are promoted to the deleted node's parent (no dangling parentId). Null if absent.
 */
export function deleteItem(c: ChecklistSyncData, itemId: string, now: number): ChecklistSyncData | null {
  const next = clone(c);
  const template = parseTemplateItems(next.itemsJson);
  const target = template.find((i) => i.id === itemId);
  if (!target) return null;
  for (const child of template) if (child.parentId === itemId) child.parentId = target.parentId;
  const remaining = template.filter((i) => i.id !== itemId);
  next.itemsJson = encodeTemplateItems(remaining);

  const fill = ensureDefaultFill(next, now);
  const fillItems = parseFillItems(fill.itemsJson).filter((i) => i.templateItemId !== itemId && i.id !== itemId);
  fill.itemsJson = encodeFillItems(fillItems);
  touchFill(fill, now);
  touchChecklist(next, now);
  return next;
}

/**
 * Reorder items to the given id order (template + mirrored fill, one shared `now` — atomic rule).
 * Ids absent from `orderedIds` keep their relative order at the end (safety). Null if none of the
 * ids match (nothing to reorder).
 */
export function reorderItems(c: ChecklistSyncData, orderedIds: string[], now: number): ChecklistSyncData | null {
  const next = clone(c);
  const template = parseTemplateItems(next.itemsJson);
  const rank = new Map<string, number>();
  orderedIds.forEach((id, i) => rank.set(id, i));
  const hasAny = template.some((t) => rank.has(t.id));
  if (!hasAny) return null;
  const stableRank = (id: string, fallback: number): number => (rank.has(id) ? rank.get(id)! : orderedIds.length + fallback);
  const reTemplate = template
    .map((t, i) => ({ t, k: stableRank(t.id, i) }))
    .sort((a, b) => a.k - b.k)
    .map((e) => e.t);
  next.itemsJson = encodeTemplateItems(reTemplate);

  const fill = ensureDefaultFill(next, now);
  const fillItems = parseFillItems(fill.itemsJson);
  const reFill = fillItems
    .map((f, i) => ({ f, k: stableRank(f.templateItemId ?? f.id, i) }))
    .sort((a, b) => a.k - b.k)
    .map((e) => e.f);
  fill.itemsJson = encodeFillItems(reFill);
  touchFill(fill, now);
  touchChecklist(next, now);
  return next;
}

/** Rename the checklist (envelope only; no fill change). */
export function renameChecklist(c: ChecklistSyncData, name: string, now: number): ChecklistSyncData {
  const next = clone(c);
  next.name = name;
  touchChecklist(next, now);
  return next;
}

/** Soft-delete the checklist (isDeleted=true), matching the app's tombstone sync. */
export function softDeleteChecklist(c: ChecklistSyncData, now: number): ChecklistSyncData {
  const next = clone(c);
  next.isDeleted = true;
  touchChecklist(next, now);
  return next;
}

/** Create a non-AI checklist from a list of item texts (all root-level leaves). */
export function createEmptyChecklist(name: string, itemTexts: string[], now: number): ChecklistSyncData {
  const template = itemTexts
    .map((t) => t.trim())
    .filter((t) => t.length > 0)
    .map((t) => newTemplateItem(t, null, "ITEM"));
  return fromTemplateItems(name, template, now);
}
