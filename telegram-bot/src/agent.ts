/**
 * The AI chat (Phase 2) — pure parts: transcript, send window, request body, chat state and its
 * reducers, and the chat's message rendering. No I/O; the loop that calls `chat_agent` lives in
 * chat.ts, the tool executor in agentTools.ts.
 *
 * `chat_agent` (firebase-functions/main.py, docstring 3767-3813) runs ONE Gemini step per HTTP call
 * and is stateless: the bot keeps the transcript in the chat's Durable Object and sends a window of
 * it each round. A TURN = one user message + the tool rounds answering it; one `request_id` per
 * turn, reused on every round, so the 3-credit charge happens once, on round 1
 * (`scan_current_turn`, chat_agent_logic.py:36-58; idempotent reserve, main.py:742-798).
 *
 * TRANSCRIPT INVARIANT: a `model.tool_calls` entry is stored only together with the `tool` entry
 * answering every call — a batch waiting for «Yes / Cancel» lives in `pending`, a batch being
 * executed in `turn.inflight`, and both reach the transcript only as a complete pair.
 */

import type { TgEvent } from "./analytics";
import {
  agentOrder,
  applyOps,
  declinedResults,
  isAgentToolName,
  needsConfirmation,
  planTool,
  toolStatus,
  type AgentToolCall,
  type AgentToolResult,
  type OwnedChecklist,
  type Plan,
} from "./agentTools";
import { encodeCallback } from "./callback";
import type { Lang, Strings } from "./i18n";
import type { InlineButton, InlineKeyboard } from "./telegram";
import { formatLocal, parseWhenIso } from "./timezone";
import { viewFill } from "./views";
import { parseFillItems, parseTemplateItems } from "../../mcp-server/src/model";

// ── transcript ──────────────────────────────────────────────────────────────────

export type Entry =
  | { role: "user"; text: string }
  | { role: "model"; text: string }
  | { role: "model"; tool_calls: AgentToolCall[] }
  | { role: "tool"; tool_results: AgentToolResult[] };

function isCalls(e: Entry): e is { role: "model"; tool_calls: AgentToolCall[] } {
  return e.role === "model" && "tool_calls" in e;
}

/** Window caps — AgentTranscriptWindow.kt:45-60 (6 turns / 30 entries / 8000 chars). */
export const WINDOW_MAX_TURNS = 6;
export const WINDOW_MAX_ENTRIES = 30;
export const WINDOW_MAX_CHARS = 8000;
/** Stored history (the app keeps 40 turns on disk); the char cap keeps the DO value small. */
export const STORED_MAX_TURNS = 40;
export const STORED_MAX_CHARS = 60_000;

/**
 * Drop tool entries that are not an adjacent calls → results pair (AgentTranscriptWindow.sanitize).
 * Gemini answers a lone function_call or function_response with 400; losing one round of memory is
 * better than losing the send.
 */
export function sanitizeTranscript(entries: Entry[]): Entry[] {
  const out: Entry[] = [];
  for (let i = 0; i < entries.length; i++) {
    const e = entries[i]!;
    if (isCalls(e)) {
      const next = entries[i + 1];
      if (next?.role === "tool") {
        out.push(e, next);
        i++;
      }
      continue;
    }
    if (e.role === "tool") continue;
    out.push(e);
  }
  return out;
}

/** Split at every user entry; anything before the first user entry is its own group. */
function groupIntoTurns(entries: Entry[]): Entry[][] {
  const turns: Entry[][] = [];
  for (const e of entries) {
    if (e.role === "user" || turns.length === 0) turns.push([]);
    turns[turns.length - 1]!.push(e);
  }
  return turns;
}

function charsOf(e: Entry): number {
  if (isCalls(e)) {
    return e.tool_calls.reduce((n, c) => n + c.name.length + JSON.stringify(c.args ?? {}).length + (c.thought_signature?.length ?? 0), 0);
  }
  if (e.role === "tool") return e.tool_results.reduce((n, r) => n + r.name.length + JSON.stringify(r.result).length, 0);
  return e.text.length;
}

/**
 * The newest whole turns that fit the caps, oldest first (AgentTranscriptWindow.select). The last
 * turn — the one being answered — is always kept, even alone over a cap. Cutting by turn keeps
 * every calls/results pair intact by construction; `chat_agent` answers 400 (never trims) above 60
 * entries, so the window is what keeps a long chat alive.
 */
export function selectWindow(
  entries: Entry[],
  maxTurns = WINDOW_MAX_TURNS,
  maxEntries = WINDOW_MAX_ENTRIES,
  maxChars = WINDOW_MAX_CHARS,
): Entry[] {
  const clean = sanitizeTranscript(entries);
  if (clean.length === 0) return [];
  let window = groupIntoTurns(clean).slice(-maxTurns);
  const size = (w: Entry[][]): number => w.reduce((n, t) => n + t.length, 0);
  const chars = (w: Entry[][]): number => w.reduce((n, t) => n + t.reduce((m, e) => m + charsOf(e), 0), 0);
  while (window.length > 1 && (size(window) > maxEntries || chars(window) > maxChars)) window = window.slice(1);
  return window.flat();
}

/** What is kept in storage: the last STORED_MAX_TURNS turns, fewer if they outgrow STORED_MAX_CHARS. */
export function pruneTranscript(entries: Entry[]): Entry[] {
  let turns = groupIntoTurns(entries).slice(-STORED_MAX_TURNS);
  while (turns.length > 1 && JSON.stringify(turns).length > STORED_MAX_CHARS) turns = turns.slice(1);
  return turns.flat();
}

/** True when every tool_calls entry is directly followed by results for exactly its call ids. */
export function isTranscriptValid(entries: Entry[]): boolean {
  for (let i = 0; i < entries.length; i++) {
    const e = entries[i]!;
    if (e.role === "tool") {
      const prev = entries[i - 1];
      if (!prev || !isCalls(prev)) return false;
      continue;
    }
    if (!isCalls(e)) continue;
    const next = entries[i + 1];
    if (!next || next.role !== "tool") return false;
    const ids = e.tool_calls.map((c) => c.id).join("\u0001");
    if (next.tool_results.map((r) => r.id).join("\u0001") !== ids) return false;
  }
  return true;
}

// ── checklists_summary (AgentChecklistsSummary.kt:26-80) ────────────────────────

export const SUMMARY_MAX_LISTS = 8;
export const SUMMARY_RECENT_PER_LIST = 6;
export const SUMMARY_RECENT_TOTAL = 30;

export interface SummaryEntry {
  name: string;
  totalItems: number;
  doneItems: number;
  recentItems: Array<{ text: string; checked: boolean; position: number }>;
}

/**
 * 8 lists in the bot's order (most recently updated first — the same order the executor's default
 * uses), leaf items only (folders skipped), the last 6 leaves of each as `recentItems` within a
 * global budget of 30. Done state comes from the default fill (the template carries none).
 */
export function buildChecklistsSummary(lists: OwnedChecklist[]): SummaryEntry[] {
  let budget = SUMMARY_RECENT_TOTAL;
  return agentOrder(lists)
    .slice(0, SUMMARY_MAX_LISTS)
    .map(({ checklist: c }) => {
      const fill = viewFill(c);
      const fillItems = fill ? parseFillItems(fill.itemsJson) : [];
      const template = parseTemplateItems(c.itemsJson);
      let leaves: Array<{ text: string; checked: boolean; position: number }>;
      if (template.length > 0) {
        const checked = new Map<string, boolean>();
        for (const f of fillItems) if (f.templateItemId) checked.set(f.templateItemId, f.checked);
        leaves = template
          .map((t, position) => ({ t, position }))
          .filter(({ t }) => t.type === "ITEM")
          .map(({ t, position }) => ({ text: t.text, checked: checked.get(t.id) ?? false, position }));
      } else {
        leaves = fillItems.map((f, position) => ({ text: f.text, checked: f.checked, position }));
      }
      const cap = Math.max(0, Math.min(SUMMARY_RECENT_PER_LIST, budget));
      const recentItems = cap === 0 ? [] : leaves.slice(-cap);
      budget -= recentItems.length;
      return {
        name: c.name,
        totalItems: leaves.length,
        doneItems: leaves.filter((l) => l.checked).length,
        recentItems,
      };
    });
}

// ── request ─────────────────────────────────────────────────────────────────────

/** One per turn, reused by every round of it: `tg-chat-<chat_id>-<turn_seq>`. */
export function chatRequestId(chatId: number, turnSeq: number): string {
  return `tg-chat-${chatId}-${turnSeq}`;
}

export interface AgentRequestInput {
  creditUserId: string;
  requestId: string;
  /** The bot's UI language — also the agent's `response_language`, so one chat never mixes two languages. */
  lang: Lang;
  /** null = zone unknown → the field is omitted (the server then assumes 0). */
  offsetMinutes: number | null;
  summary: SummaryEntry[];
  transcript: Entry[];
}

/** The `chat_agent` body (validation: main.py:3847-3895). The transcript is windowed here. */
export function buildAgentRequest(i: AgentRequestInput): Record<string, unknown> {
  return {
    user_id: i.creditUserId,
    request_id: i.requestId,
    locale: i.lang === "ru" ? "ru" : "en",
    response_language: i.lang,
    ...(i.offsetMinutes !== null ? { timezone_offset_minutes: i.offsetMinutes } : {}),
    checklists_summary: i.summary,
    supports_options: true,
    transcript: selectWindow(i.transcript),
  };
}

// ── chat state (one DO storage key) ─────────────────────────────────────────────

/** Chat mode closes after this long without a user message (checked lazily on the next one). */
export const CHAT_IDLE_MS = 30 * 60 * 1000;
/** Tool rounds per turn — the server cap CHAT_AGENT_MAX_ROUNDS (main.py:3222); ≤6 HTTP calls a turn. */
export const MAX_TOOL_ROUNDS = 5;
/** A first message longer than this is refused locally (server: 12000 chars of user text, main.py:3224). */
export const MAX_CHAT_INPUT_CHARS = 10_000;

/** True when `text` is refused locally as too long for one chat turn (see MAX_CHAT_INPUT_CHARS). */
export function tooLongForChat(text: string): boolean {
  return text.length > MAX_CHAT_INPUT_CHARS;
}

/** A planned call and whether its writes are done — persisted per call so a retry never runs it twice. */
export interface CallRecord {
  plan: Plan;
  applied: boolean;
}

export interface InflightBatch {
  calls: AgentToolCall[];
  /** By call index. */
  records: Record<string, CallRecord>;
}

export interface ActiveTurn {
  seq: number;
  requestId: string;
  /** The update driving the turn right now; a queue retry of THAT update resumes the turn. */
  updateId: number;
  /** Tool rounds completed in this turn. */
  round: number;
  inflight?: InflightBatch;
  timezoneUnknown?: boolean;
}

/**
 * A batch with a destructive call, waiting for «Yes / Cancel». Every call is already PLANNED (exact
 * ids) — the card shows those plans and «Yes» applies them as stored, never re-resolving by text.
 */
export interface PendingBatch {
  turnSeq: number;
  calls: AgentToolCall[];
  records: Record<string, CallRecord>;
  cardText: string;
  cardMessageId: number | null;
}

export interface OutMsg {
  text: string;
  keyboard?: InlineKeyboard;
}

/** Replies of a closed turn not yet delivered — a retry of the same update only re-sends them. */
export interface Outbox {
  updateId: number;
  messages: OutMsg[];
}

export interface ChatOptions {
  turnSeq: number;
  prompt: string;
  labels: string[];
}

export interface ChatState {
  open: boolean;
  lastUserAt: number;
  turnSeq: number;
  transcript: Entry[];
  turn?: ActiveTurn;
  pending?: PendingBatch;
  options?: ChatOptions;
  outbox?: Outbox;
}

export function emptyChatState(): ChatState {
  return { open: false, lastUserAt: 0, turnSeq: 0, transcript: [] };
}

export function isChatOpen(state: ChatState, now: number): boolean {
  return state.open && now - state.lastUserAt <= CHAT_IDLE_MS;
}

/**
 * Where a plain text message goes: the agent while chat mode is open, otherwise the draft flow.
 * `expired` = open but idle past CHAT_IDLE_MS → the caller closes the chat, then drafts the text.
 */
export function routeText(state: ChatState, now: number): "agent" | "draft" | "expired" {
  if (!state.open) return "draft";
  return now - state.lastUserAt <= CHAT_IDLE_MS ? "agent" : "expired";
}

/** What closing a turn left behind (for the card edit and analytics). */
export interface ClosedTurn {
  seq: number;
  rounds: number;
  declined: AgentToolCall[];
  cardMessageId: number | null;
  cardText: string | null;
}

/**
 * Close the active turn, keeping the transcript valid:
 *  - a pending card → its calls + one `{"status":"declined"}` per call;
 *  - a batch interrupted mid-execution → its calls + the results that were applied, the rest
 *    `{"status":"error","detail":"interrupted"}`;
 *  - a turn that produced nothing (no round, no batch) → its unanswered user message is dropped,
 *    so a resend does not reach the model twice.
 * Null when there was nothing to close.
 */
export function closeTurn(state: ChatState): ClosedTurn | null {
  const turn = state.turn;
  const pending = state.pending;
  if (!turn && !pending) return null;
  const closed: ClosedTurn = {
    seq: turn?.seq ?? pending!.turnSeq,
    rounds: turn?.round ?? 0,
    declined: [],
    cardMessageId: pending?.cardMessageId ?? null,
    cardText: pending?.cardText ?? null,
  };
  if (pending) {
    state.transcript.push(
      { role: "model", tool_calls: pending.calls },
      { role: "tool", tool_results: declinedResults(pending.calls) },
    );
    closed.declined = pending.calls;
    closed.rounds += 1;
  } else if (turn?.inflight) {
    const inf = turn.inflight;
    state.transcript.push(
      { role: "model", tool_calls: inf.calls },
      {
        role: "tool",
        tool_results: inf.calls.map((c, i) => {
          const rec = inf.records[String(i)];
          return {
            id: c.id,
            name: c.name,
            result: rec?.applied ? rec.plan.result : { status: "error", detail: "interrupted" },
          };
        }),
      },
    );
    closed.rounds += 1;
  } else if (turn && turn.round === 0) {
    const last = state.transcript[state.transcript.length - 1];
    if (last?.role === "user") state.transcript.pop();
  }
  state.turn = undefined;
  state.pending = undefined;
  return closed;
}

/** Start a turn with the user's text (call `closeTurn` first). Opens chat mode. */
export function beginTurn(state: ChatState, text: string, chatId: number, updateId: number, now: number): ActiveTurn {
  state.open = true;
  state.lastUserAt = now;
  state.options = undefined;
  state.outbox = undefined;
  state.turnSeq += 1;
  state.transcript.push({ role: "user", text });
  const turn: ActiveTurn = { seq: state.turnSeq, requestId: chatRequestId(chatId, state.turnSeq), updateId, round: 0 };
  state.turn = turn;
  return turn;
}

/** Close chat mode (End chat / idle). Returns the turn it had to close, if any. */
export function closeChat(state: ChatState): ClosedTurn | null {
  const closed = closeTurn(state);
  state.open = false;
  state.options = undefined;
  return closed;
}

/** Record a finished round: the calls and their results, as one pair. */
export function appendRound(state: ChatState, calls: AgentToolCall[], results: AgentToolResult[]): void {
  state.transcript.push({ role: "model", tool_calls: calls }, { role: "tool", tool_results: results });
  if (state.turn) {
    state.turn.round += 1;
    state.turn.inflight = undefined;
  }
}

/** What to do with a `tool_calls` answer at `round`. */
export function toolCallsAction(calls: AgentToolCall[], round: number): "limit" | "pause" | "execute" {
  if (round >= MAX_TOOL_ROUNDS) return "limit";
  return needsConfirmation(calls) ? "pause" : "execute";
}

/** The id seed of call `index` of round `round` — the same for a batch planned now or at a card. */
export function callSeed(turn: ActiveTurn, index: number): string {
  return `${turn.requestId}:${turn.round}:${index}`;
}

/**
 * Plan every call of a batch BEFORE anything is written (the confirmation card), each against the
 * lists as the previous calls will leave them — simulated on a copy, so «create, then add to it»
 * resolves as it will run. Writes nothing. The records are stored with the card; «Yes» applies them
 * as they are (exact ids), so what the card showed is what changes.
 */
export function planBatch(
  calls: AgentToolCall[],
  lists: OwnedChecklist[],
  turn: ActiveTurn,
  timezone: string | null,
  now: number,
): Record<string, CallRecord> {
  const sim = structuredClone(lists);
  const records: Record<string, CallRecord> = {};
  calls.forEach((call, i) => {
    const plan = planTool(call, sim, { timezone, seed: callSeed(turn, i) });
    applyOps(sim, plan.ops, "simulation", now);
    records[String(i)] = { plan, applied: plan.ops.length === 0 };
  });
  return records;
}

/** How a turn ended — decides what happens to the transcript and to chat mode. */
export type TurnEnd = "final" | "options" | "no_credits" | "no_user" | "rejected" | "limit";

/**
 * Close the active turn. `final` / `options` record the model's text; the rest close the turn
 * (`closeTurn`). `no_credits` and `no_user` also CLOSE chat mode: every next text would hit the same
 * wall, so it goes back to the draft flow (where «Checklist from lines» is free). Returns the update
 * that drove the turn (the outbox key) and its rounds, or null when no turn was active.
 */
export function finishTurn(state: ChatState, end: TurnEnd, modelText: string | null): { updateId: number; rounds: number } | null {
  const turn = state.turn;
  if (!turn) return null;
  if (modelText !== null && (end === "final" || end === "options")) {
    state.transcript.push({ role: "model", text: modelText });
    state.turn = undefined;
    state.pending = undefined;
  } else {
    closeTurn(state);
  }
  if (end === "no_credits" || end === "no_user") {
    state.open = false;
    state.options = undefined;
  }
  return { updateId: turn.updateId, rounds: turn.round };
}

/**
 * Any user message or button while chat mode is open keeps it alive (the 30-min idle clock runs
 * from the LAST user action, not the last agent turn). `expired` → the caller closes the chat;
 * `refreshed` → persist; `none` → chat mode is closed, nothing to do.
 */
export function noteUserActivity(state: ChatState, now: number): "none" | "refreshed" | "expired" {
  const route = routeText(state, now);
  if (route === "draft") return "none";
  if (route === "expired") return "expired";
  state.lastUserAt = now;
  return "refreshed";
}

// ── rendering ───────────────────────────────────────────────────────────────────

export const TELEGRAM_TEXT_MAX = 4096;
const BUTTON_TEXT_MAX = 60;
const CARD_LINE_MAX = 200;
const CARD_TEXT_MAX = 3800;

function clip(s: string, max: number): string {
  const one = s.replace(/\s+/g, " ").trim();
  return one.length > max ? `${one.slice(0, max - 1)}…` : one;
}

/**
 * The model writes Markdown; the bot sends plain text (no parse_mode). Strip the marks that would
 * show as noise — `**bold**`, `# headings`, `* bullets` — and leave everything else as written.
 */
export function plainText(md: string): string {
  return md
    .replace(/\*\*(.+?)\*\*/g, "$1")
    .replace(/__(.+?)__/g, "$1")
    .replace(/^#{1,6}\s+/gm, "")
    .replace(/^(\s*)[*-]\s+/gm, "$1• ")
    .trim();
}

/** Split into Telegram-sized chunks, preferring paragraph, then line, then word boundaries. */
export function splitText(text: string, max = TELEGRAM_TEXT_MAX): string[] {
  const out: string[] = [];
  let rest = text;
  while (rest.length > max) {
    const window = rest.slice(0, max);
    let cut = window.lastIndexOf("\n\n");
    if (cut < max / 2) cut = window.lastIndexOf("\n");
    if (cut < max / 2) cut = window.lastIndexOf(" ");
    if (cut <= 0) cut = max;
    out.push(rest.slice(0, cut).trimEnd());
    rest = rest.slice(cut).trimStart();
  }
  if (rest.length > 0 || out.length === 0) out.push(rest);
  return out;
}

function endChatRow(s: Strings): InlineButton[] {
  return [{ text: s.btnEndChat, callback_data: encodeCallback({ t: "endChat" }) }];
}

/** A final answer: plain text (+ extra lines), split to the limit; «End chat» under the last chunk. */
export function renderReply(text: string, s: Strings, extraLines: string[] = []): OutMsg[] {
  const body = [plainText(text), ...extraLines].filter((x) => x.length > 0).join("\n\n");
  const chunks = splitText(body || "…");
  return chunks.map((chunk, i) => (i === chunks.length - 1 ? { text: chunk, keyboard: { inline_keyboard: [endChatRow(s)] } } : { text: chunk }));
}

/** `present_options`: the question + one button per option (index → label kept in state) + «End chat». */
export function renderOptions(prompt: string, labels: string[], turnSeq: number, s: Strings): OutMsg {
  const rows: InlineButton[][] = labels.map((label, index) => [
    { text: clip(label, BUTTON_TEXT_MAX) || "…", callback_data: encodeCallback({ t: "option", turn: turnSeq, index }) },
  ]);
  rows.push(endChatRow(s));
  return { text: splitText(plainText(prompt) || "…")[0]!, keyboard: { inline_keyboard: rows } };
}

function arg(call: AgentToolCall, key: string): string | null {
  const v = call.args?.[key];
  return typeof v === "string" && v.trim().length > 0 ? clip(v, 80) : null;
}

/** A card line from the call's own arguments (what the model asked for) — for calls that change nothing. */
export function describeCall(call: AgentToolCall, s: Strings, timezone: string | null): string {
  const a = s.act;
  const list = arg(call, "checklist_hint");
  switch (call.name) {
    case "add_item":
      return a.add(arg(call, "item_text") ?? "…", list);
    case "add_items": {
      const v = call.args?.["item_texts"];
      return a.addMany(Array.isArray(v) ? v.length : 0, list);
    }
    case "create_checklist": {
      const v = call.args?.["initial_items"];
      return a.create(arg(call, "name") ?? "…", Array.isArray(v) ? v.length : 0);
    }
    case "complete_item":
      return a.complete(arg(call, "item_text") ?? "…", list);
    case "delete_item":
      return a.delete(arg(call, "item_text") ?? "…", list);
    case "clear_completed_items":
      return a.clear(list, null);
    case "set_item_reminder": {
      const raw = arg(call, "when_iso") ?? "…";
      const parsed = parseWhenIso(raw, timezone);
      return a.reminder(arg(call, "item_text") ?? "…", parsed.kind === "ok" && timezone ? formatLocal(parsed.at, timezone) : raw);
    }
    case "rename_checklist":
      return a.rename(list ?? "…", arg(call, "new_name") ?? "…");
    case "move_item":
      return a.move(arg(call, "item_text") ?? "…", arg(call, "from_checklist_hint"), arg(call, "to_checklist_hint") ?? "…");
    case "find_items":
      return a.find(arg(call, "query") ?? "…");
    case "read_checklist":
      return a.read(arg(call, "name") ?? "…");
    default:
      return clip(call.name, 40);
  }
}

/**
 * One card line from a call's PLAN: a write names the resolved row and list (what will really
 * change, even when the model's text matched something else); a call that will change nothing
 * shows the model's arguments and why (not found, several lists match, already so, blocked).
 */
export function describePlan(call: AgentToolCall, plan: Plan, s: Strings, timezone: string | null): string {
  const a = s.act;
  const p = plan.preview;
  if (p) {
    switch (p.kind) {
      case "add":
        return p.texts.length === 1 ? a.add(clip(p.texts[0]!, 80), p.list) : a.addMany(p.texts.length, p.list);
      case "create":
        return a.create(clip(p.name, 80), p.items);
      case "complete":
        return a.complete(clip(p.item, 80), p.list);
      case "delete":
        return a.delete(clip(p.item, 80), p.list);
      case "clear":
        return a.clear(p.list, p.count);
      case "reminder":
        return a.reminder(clip(p.item, 80), timezone ? formatLocal(p.at, timezone) : new Date(p.at).toISOString());
      case "rename":
        return a.rename(p.from, clip(p.to, 80));
      case "move":
        return a.move(clip(p.item, 80), p.from, p.to);
      case "find":
        return a.find(clip(p.query, 80));
      case "read":
        return a.read(p.list);
    }
  }
  const base = describeCall(call, s, timezone);
  const r = plan.result;
  let why = a.cannot;
  if (r["status"] === "not_found") why = r["reason"] === "move_blocked" ? a.blocked : a.willNotFind;
  else if (r["status"] === "ambiguous") {
    const names = Array.isArray(r["candidates"]) ? (r["candidates"] as unknown[]).map(String) : [];
    why = a.willAsk(names.map((n) => `«${clip(n, 40)}»`).join(", "));
  } else if (r["status"] === "success") why = a.noChange;
  return `${base} — ${why}`;
}

/** The ONE confirmation card for a batch, rendered from its plans, then «Yes» / «Cancel». */
export function renderConfirmCard(
  calls: AgentToolCall[],
  records: Record<string, CallRecord>,
  turnSeq: number,
  s: Strings,
  timezone: string | null,
): { text: string; keyboard: InlineKeyboard } {
  const lines = [s.confirmTitle, ""];
  let length = s.confirmTitle.length;
  for (let i = 0; i < calls.length; i++) {
    const call = calls[i]!;
    const rec = records[String(i)];
    const text = rec ? describePlan(call, rec.plan, s, timezone) : describeCall(call, s, timezone);
    const line = `${i + 1}. ${clip(text, CARD_LINE_MAX)}`;
    if (length + line.length > CARD_TEXT_MAX) {
      lines.push(`… (+${calls.length - i})`);
      break;
    }
    lines.push(line);
    length += line.length + 1;
  }
  return { text: lines.join("\n"), keyboard: confirmKeyboard(turnSeq, s) };
}

/** «Yes» / «Cancel» for the card of `turnSeq` (rebuilt on resume — only the card text is stored). */
export function confirmKeyboard(turnSeq: number, s: Strings): InlineKeyboard {
  return {
    inline_keyboard: [
      [
        { text: s.btnConfirmYes, callback_data: encodeCallback({ t: "confirm", turn: turnSeq, yes: true }) },
        { text: s.btnCancel, callback_data: encodeCallback({ t: "confirm", turn: turnSeq, yes: false }) },
      ],
    ],
  };
}

export function endChatKeyboard(s: Strings): InlineKeyboard {
  return { inline_keyboard: [endChatRow(s)] };
}

/** One `tg_chat_tool` per call of a round (unknown tool names are not counted — the union is closed). */
export function toolEvents(calls: AgentToolCall[], results: AgentToolResult[]): TgEvent[] {
  const out: TgEvent[] = [];
  calls.forEach((c, i) => {
    const r = results[i];
    if (r && isAgentToolName(c.name)) out.push({ type: "tg_chat_tool", tool: c.name, status: toolStatus(r.result) });
  });
  return out;
}
