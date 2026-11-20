/**
 * Inline-button `callback_data` (Telegram limit: 1–64 BYTES) and the short-key registry behind it.
 *
 * Checklist and item ids are UUIDs (36 chars each) — two of them would already blow the limit — so
 * buttons carry a short base36 key, and the key → {cloudId, itemId} mapping lives in the chat's
 * Durable Object storage. The registry reuses the key of an already-mapped target (a re-render does
 * not mint new keys) and evicts the oldest keys FIFO; a tap on an evicted key gets "view outdated".
 */

export const CALLBACK_MAX_BYTES = 64;

/** `chat` = «💬 Ask AI»: open the AI chat with the draft text as the first message (Phase 2). */
export type DraftAction = "free" | "ai" | "cancel" | "chat";

export type Callback =
  | { t: "draft"; draftId: string; action: DraftAction }
  | { t: "lists"; page: number }
  | { t: "open"; key: string; page: number }
  | { t: "toggle"; key: string; checked: boolean; page: number }
  | { t: "noop" }
  // Phase 2 — AI chat. `turn` = the chat turn the button belongs to; a tap on an older turn's
  // button is "outdated". Option labels live in DO storage — the button carries only an index.
  | { t: "confirm"; turn: number; yes: boolean }
  | { t: "option"; turn: number; index: number }
  | { t: "endChat" }
  /** `/timezone` pick: minutes east of UTC (one of TZ_CHOICES). */
  | { t: "tz"; offset: number };

const ACTION_CODE: Record<DraftAction, string> = { free: "f", ai: "a", cancel: "x", chat: "c" };
const CODE_ACTION: Record<string, DraftAction> = { f: "free", a: "ai", x: "cancel", c: "chat" };

export function encodeCallback(cb: Callback): string {
  switch (cb.t) {
    case "draft":
      return `d:${cb.draftId}:${ACTION_CODE[cb.action]}`;
    case "lists":
      return `l:${cb.page}`;
    case "open":
      return `o:${cb.key}:${cb.page}`;
    case "toggle":
      return `t:${cb.key}:${cb.checked ? 1 : 0}:${cb.page}`;
    case "noop":
      return "n";
    case "confirm":
      return `a:${cb.turn}:${cb.yes ? "y" : "n"}`;
    case "option":
      return `p:${cb.turn}:${cb.index}`;
    case "endChat":
      return "e";
    case "tz":
      return `z:${cb.offset}`;
  }
}

const ID = "[0-9a-z]{1,16}";
const PAGE = "(\\d{1,4})";
const TURN = "(\\d{1,12})";
const RE_DRAFT = new RegExp(`^d:(${ID}):([faxc])$`);
const RE_LISTS = new RegExp(`^l:${PAGE}$`);
const RE_OPEN = new RegExp(`^o:(${ID}):${PAGE}$`);
const RE_TOGGLE = new RegExp(`^t:(${ID}):([01]):${PAGE}$`);
const RE_CONFIRM = new RegExp(`^a:${TURN}:([yn])$`);
const RE_OPTION = new RegExp(`^p:${TURN}:(\\d)$`);
const RE_TZ = /^z:(-?\d{1,3})$/;

/** Parse callback_data; anything malformed or foreign → null (caller answers "view outdated"). */
export function decodeCallback(data: string | undefined): Callback | null {
  if (!data) return null;
  if (data === "n") return { t: "noop" };
  let m = RE_DRAFT.exec(data);
  if (m) return { t: "draft", draftId: m[1]!, action: CODE_ACTION[m[2]!]! };
  m = RE_LISTS.exec(data);
  if (m) return { t: "lists", page: Number(m[1]) };
  m = RE_OPEN.exec(data);
  if (m) return { t: "open", key: m[1]!, page: Number(m[2]) };
  m = RE_TOGGLE.exec(data);
  if (m) return { t: "toggle", key: m[1]!, checked: m[2] === "1", page: Number(m[3]) };
  if (data === "e") return { t: "endChat" };
  m = RE_CONFIRM.exec(data);
  if (m) return { t: "confirm", turn: Number(m[1]), yes: m[2] === "y" };
  m = RE_OPTION.exec(data);
  if (m) return { t: "option", turn: Number(m[1]), index: Number(m[2]) };
  m = RE_TZ.exec(data);
  if (m) return { t: "tz", offset: Number(m[1]) };
  return null;
}

export function byteLength(s: string): number {
  return new TextEncoder().encode(s).length;
}

// ── Key registry ────────────────────────────────────────────────────────────────

/** What a short key points at: a checklist, or one item of it. */
export interface KeyTarget {
  c: string;
  i?: string;
}

export interface KeyRegistryState {
  seq: number;
  map: Record<string, KeyTarget>;
  /** target → key, so re-rendering the same view reuses keys. */
  rev: Record<string, string>;
  /** Insertion order for FIFO eviction. */
  order: string[];
}

/** Keys kept per chat. Sized so the serialized registry stays well under a DO storage value limit. */
export const MAX_KEYS = 400;

export function emptyRegistry(): KeyRegistryState {
  return { seq: 0, map: {}, rev: {}, order: [] };
}

function revKey(t: KeyTarget): string {
  return `${t.c}\u0001${t.i ?? ""}`;
}

/** The key for `target`, minting (and evicting the oldest) if needed. Mutates `state`. */
export function keyFor(state: KeyRegistryState, target: KeyTarget): string {
  const rk = revKey(target);
  const existing = state.rev[rk];
  if (existing && state.map[existing]) return existing;

  state.seq += 1;
  const key = state.seq.toString(36);
  state.map[key] = target.i === undefined ? { c: target.c } : { c: target.c, i: target.i };
  state.rev[rk] = key;
  state.order.push(key);

  while (state.order.length > MAX_KEYS) {
    const old = state.order.shift()!;
    const t = state.map[old];
    if (t) delete state.rev[revKey(t)];
    delete state.map[old];
  }
  return key;
}

export function resolveKey(state: KeyRegistryState, key: string): KeyTarget | undefined {
  return state.map[key];
}
