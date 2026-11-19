/**
 * Rendering of the /lists views — pure functions from checklist data to message text + keyboard.
 *
 *  - Lists: non-deleted, non-Inbox checklists (union over the user's docs), by `position`, 8 per page.
 *  - Items: the template tree with checked state from the default fill (the fill toggle_item writes),
 *    10 rows per page; folders are header rows (tap = no-op), leaves are toggle buttons.
 */

import { encodeCallback, keyFor, type KeyRegistryState } from "./callback";
import { aiPlan, draftCounts, freeOption, hasMultipleFiles, MAX_PARTS, type Draft } from "./draft";
import { formatDate, type Lang, type Strings } from "./i18n";
import { PLAY_STORE_URL } from "./links";
import type { PremiumStatus } from "./payments";
import type { InlineButton, InlineKeyboard } from "./telegram";
import {
  parseFillItems,
  parseTemplateItems,
  type ChecklistItem,
  type ChecklistSyncData,
  type FillSyncData,
} from "../../mcp-server/src/model";

export const LISTS_PAGE_SIZE = 8;
export const ITEMS_PAGE_SIZE = 10;
const BUTTON_TEXT_MAX = 60;

export interface View {
  text: string;
  keyboard: InlineKeyboard;
}

function clip(s: string, max = BUTTON_TEXT_MAX): string {
  const one = s.replace(/\s+/g, " ").trim();
  return one.length > max ? `${one.slice(0, max - 1)}…` : one || "…";
}

export function pageCount(total: number, size: number): number {
  return Math.max(1, Math.ceil(total / size));
}

export function clampPage(page: number, pages: number): number {
  return Math.min(Math.max(0, Math.floor(page)), pages - 1);
}

/**
 * The fill state shown and toggled: the live default fill, else the first live fill — the same
 * choice mutate.ts makes (ensureDefaultFill), so what you see is what a tap changes.
 */
export function viewFill(c: ChecklistSyncData): FillSyncData | null {
  return c.fills.find((f) => f.isDefault && !f.isDeleted) ?? c.fills.find((f) => !f.isDeleted) ?? null;
}

export type Row =
  | { kind: "folder"; text: string; depth: number }
  | { kind: "item"; text: string; depth: number; itemId: string; checked: boolean };

/** Flatten a checklist into display rows (depth-first over the template tree, like MCP get_checklist). */
export function checklistRows(c: ChecklistSyncData): Row[] {
  const template = parseTemplateItems(c.itemsJson);
  const fill = viewFill(c);
  const fillItems = fill ? parseFillItems(fill.itemsJson) : [];

  if (template.length === 0) {
    // Legacy: no template tree — the flat fill is all there is.
    return fillItems.map((it) => ({
      kind: "item" as const,
      text: it.text,
      depth: 0,
      itemId: it.templateItemId ?? it.id,
      checked: it.checked,
    }));
  }

  const checked = new Map<string, boolean>();
  for (const fi of fillItems) if (fi.templateItemId) checked.set(fi.templateItemId, fi.checked);

  const folderIds = new Set(template.filter((i) => i.type === "FOLDER").map((i) => i.id));
  const isRoot = (i: ChecklistItem): boolean => !i.parentId || !folderIds.has(i.parentId);
  const rows: Row[] = [];
  const seen = new Set<string>();
  const walk = (items: ChecklistItem[], depth: number): void => {
    for (const it of items) {
      if (seen.has(it.id)) continue; // defensive: a parentId cycle must not loop forever
      seen.add(it.id);
      if (it.type === "FOLDER") {
        rows.push({ kind: "folder", text: it.text, depth });
        walk(
          template.filter((ch) => !isRoot(ch) && ch.parentId === it.id),
          depth + 1,
        );
      } else {
        rows.push({ kind: "item", text: it.text, depth, itemId: it.id, checked: checked.get(it.id) ?? false });
      }
    }
  };
  walk(template.filter(isRoot), 0);
  return rows;
}

export function progress(rows: Row[]): { done: number; total: number } {
  let done = 0;
  let total = 0;
  for (const r of rows) {
    if (r.kind !== "item") continue;
    total++;
    if (r.checked) done++;
  }
  return { done, total };
}

/** Lists order: `position`, then name — the order the app shows. */
export function sortChecklists(list: ChecklistSyncData[]): ChecklistSyncData[] {
  return [...list].sort((a, b) => a.position - b.position || a.name.localeCompare(b.name));
}

/**
 * The one prompt a draft turns into: what was received, then the buttons it qualifies for. `notice`
 * (an error from a failed AI attempt) goes on top while the buttons stay, so the user can retry or
 * pick the free option.
 */
export function renderDraftPrompt(d: Draft, aiCost: number, s: Strings, notice?: string): View {
  const free = freeOption(d, s.defaultChecklistName);
  const lines: string[] = [];
  if (notice) lines.push(notice, "");
  lines.push(s.draftSummary(draftCounts(d.parts)));
  if (free) lines.push(s.draftFreeHint(free.items.length));
  if (hasMultipleFiles(d)) lines.push(s.onlyFirstFile);
  if (d.overflow) lines.push(s.tooManyParts(MAX_PARTS));
  lines.push("", s.draftQuestion);

  const rows: InlineButton[][] = [];
  if (free) rows.push([{ text: s.btnFree, callback_data: encodeCallback({ t: "draft", draftId: d.id, action: "free" }) }]);
  if (aiPlan(d)) {
    rows.push([{ text: s.btnAi(aiCost), callback_data: encodeCallback({ t: "draft", draftId: d.id, action: "ai" }) }]);
  }
  rows.push([{ text: s.btnCancel, callback_data: encodeCallback({ t: "draft", draftId: d.id, action: "cancel" }) }]);
  return { text: lines.join("\n"), keyboard: { inline_keyboard: rows } };
}

/** `/app`: short pitch + a URL button to the Play listing (with Telegram attribution). */
export function renderAppView(s: Strings): View {
  return { text: s.appText, keyboard: { inline_keyboard: [[{ text: s.btnOpenPlay, url: PLAY_STORE_URL }]] } };
}

/** What `/premium` shows for a linked user — decided by `premium_status`. */
export type PremiumScreen = { kind: "store" } | { kind: "telegram"; untilMs: number } | { kind: "offer" };

export function premiumScreen(st: PremiumStatus): PremiumScreen {
  if (st.isPremium && st.source === "telegram" && st.telegramPremiumUntilMs !== null) {
    return { kind: "telegram", untilMs: st.telegramPremiumUntilMs };
  }
  if (st.isPremium) return { kind: "store" }; // store, or premium without a known source — nothing to sell
  return { kind: "offer" };
}

/**
 * `/premium` message. `invoiceUrl` is the `createInvoiceLink` result; only the offer needs it.
 * The daily credit cap comes from remote config — never a literal.
 */
export function renderPremiumView(
  st: PremiumStatus,
  dailyCap: number,
  s: Strings,
  lang: Lang,
  invoiceUrl: string | null,
): View {
  const screen = premiumScreen(st);
  if (screen.kind === "store") return { text: s.premiumStore, keyboard: { inline_keyboard: [] } };
  if (screen.kind === "telegram") {
    return {
      text: s.premiumTelegram(formatDate(screen.untilMs, lang), st.subscriptionState),
      keyboard: { inline_keyboard: [] },
    };
  }
  const rows: InlineButton[][] = invoiceUrl ? [[{ text: s.btnSubscribe(st.priceStars), url: invoiceUrl }]] : [];
  return { text: s.premiumOffer(st.priceStars, dailyCap), keyboard: { inline_keyboard: rows } };
}

function navRow(prev: string | null, label: string, next: string | null): InlineButton[] {
  const row: InlineButton[] = [];
  if (prev) row.push({ text: "◀", callback_data: prev });
  row.push({ text: label, callback_data: encodeCallback({ t: "noop" }) });
  if (next) row.push({ text: "▶", callback_data: next });
  return row;
}

export function renderListsPage(
  checklists: ChecklistSyncData[],
  requestedPage: number,
  s: Strings,
  keys: KeyRegistryState,
): View {
  const sorted = sortChecklists(checklists);
  const pages = pageCount(sorted.length, LISTS_PAGE_SIZE);
  const page = clampPage(requestedPage, pages);
  const slice = sorted.slice(page * LISTS_PAGE_SIZE, (page + 1) * LISTS_PAGE_SIZE);

  const rows: InlineButton[][] = slice.map((c) => {
    const { done, total } = progress(checklistRows(c));
    const key = keyFor(keys, { c: c.cloudId });
    return [{ text: clip(`${c.name || "…"} · ${done}/${total}`), callback_data: encodeCallback({ t: "open", key, page: 0 }) }];
  });
  if (pages > 1) {
    rows.push(
      navRow(
        page > 0 ? encodeCallback({ t: "lists", page: page - 1 }) : null,
        `${page + 1}/${pages}`,
        page < pages - 1 ? encodeCallback({ t: "lists", page: page + 1 }) : null,
      ),
    );
  }
  return { text: s.listsHeader(page + 1, pages), keyboard: { inline_keyboard: rows } };
}

export function renderItemsPage(
  c: ChecklistSyncData,
  requestedPage: number,
  s: Strings,
  keys: KeyRegistryState,
): View {
  const all = checklistRows(c);
  const { done, total } = progress(all);
  const pages = pageCount(all.length, ITEMS_PAGE_SIZE);
  const page = clampPage(requestedPage, pages);
  const listKey = keyFor(keys, { c: c.cloudId });
  const back: InlineButton[] = [{ text: s.btnBack, callback_data: encodeCallback({ t: "lists", page: 0 }) }];

  if (all.length === 0) {
    return { text: s.noItems(c.name || "…"), keyboard: { inline_keyboard: [back] } };
  }

  const rows: InlineButton[][] = all.slice(page * ITEMS_PAGE_SIZE, (page + 1) * ITEMS_PAGE_SIZE).map((r) => {
    const indent = "  ".repeat(Math.min(r.depth, 3));
    if (r.kind === "folder") {
      return [{ text: clip(`${indent}📁 ${r.text}`), callback_data: encodeCallback({ t: "noop" }) }];
    }
    const key = keyFor(keys, { c: c.cloudId, i: r.itemId });
    return [
      {
        text: clip(`${indent}${r.checked ? "✅" : "⬜"} ${r.text}`),
        callback_data: encodeCallback({ t: "toggle", key, checked: !r.checked, page }),
      },
    ];
  });
  if (pages > 1) {
    rows.push(
      navRow(
        page > 0 ? encodeCallback({ t: "open", key: listKey, page: page - 1 }) : null,
        `${page + 1}/${pages}`,
        page < pages - 1 ? encodeCallback({ t: "open", key: listKey, page: page + 1 }) : null,
      ),
    );
  }
  rows.push(back);
  return { text: s.itemsHeader(c.name || "…", done, total, page + 1, pages), keyboard: { inline_keyboard: rows } };
}
