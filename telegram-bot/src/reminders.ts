/**
 * Phase 4 — reminders as Telegram messages. Rendering, the cron fan-out, delivery and the button
 * handlers, with every I/O injected (chat.ts / index.ts wire them to the Durable Object, Firestore,
 * `telegram_bot_internal` and the Bot API), so all of it is unit-testable without workerd.
 *
 * AT MOST ONCE. `claim_due_reminders` advances each returned entry in a transaction BEFORE the worker
 * sees it (telegram_reminders.py `_claim_one`). From there on nothing is retried that could send a
 * reminder twice: the cron never repeats the claim call (the next tick claims whatever is still due),
 * the fan-out calls each chat's Durable Object once, and the DO makes ONE sendMessage attempt per
 * reminder. A failed attempt is logged and the reminder is lost — a timeout may already have
 * delivered it. A 403 (the user blocked the bot) is logged once and the rest of that chat's batch is
 * skipped.
 *
 * BUTTONS carry only the reminder id (`rd:` / `rs:` / `ro:` + 24 hex). What it points at is stored in
 * the chat's DO under `rem:<rid>` for 7 days (pruned on write), so a tap needs no extra lookup.
 *   Done      → toggleItem(…, true) on the default fill (the MCP data layer) → write to the doc the
 *               checklist was found in → «✅ <item>». Already ticked → same edit, no write.
 *   In 1 hour → `snooze_reminder` → «I'll remind you again at HH:MM» in the server's zone.
 *   Open list → the /lists items view as a NEW message (the reminder stays).
 *   `rc:t|b|p` (+ `:c` from /reminders) → masked PATCH of `users/{google_uid}.reminder_channel`; the
 *               users-doc trigger rebuilds the reminder index on the server.
 */

import type { OwnedChecklist } from "../../mcp-server/src/firestore";
import { parseFillItems, type ChecklistSyncData } from "../../mcp-server/src/model";
import { toggleItem } from "../../mcp-server/src/mutate";
import type { ReminderKind, TgEvent } from "./analytics";
import {
  InternalApiError,
  linkIds,
  type ReminderChannel,
  type ReminderSettings,
  type TelegramIdentity,
  type TelegramLink,
} from "./backend";
import { encodeCallback, RID_RE, type Callback, type ChannelSource, type KeyRegistryState } from "./callback";
import { pickLang, t, type Lang, type Strings } from "./i18n";
import { errText, type KvStore } from "./queue";
import { TelegramError, type InlineButton, type InlineKeyboard } from "./telegram";
import { formatHm, isValidZone, offsetLabel, offsetMinutesAt } from "./timezone";
import { checklistRows, ITEMS_PAGE_SIZE, progress, renderItemsPage, viewFill, type View } from "./views";

/** «In 1 hour». */
export const SNOOZE_MINUTES = 60;
/** Reminders claimed per cron tick (server default 50, max 200 — telegram_reminders.py:51-52). */
export const CLAIM_LIMIT = 50;
/**
 * The claim call's timeout. Generous on purpose: an abort AFTER the server claimed loses those
 * reminders (they are never re-claimed), and the cron has minutes of wall time, not seconds.
 */
export const CLAIM_TIMEOUT_MS = 55_000;
/** Chats delivered in parallel per tick (a chat's own reminders go out one by one). */
export const DELIVERY_CONCURRENCY = 4;
export const REM_PREFIX = "rem:";
/** How long a reminder's buttons keep working. */
export const REM_TTL_MS = 7 * 24 * 60 * 60 * 1000;

const KINDS: readonly ReminderKind[] = ["oneshot", "repeat", "snooze"];

// ── claim response ──────────────────────────────────────────────────────────────

/** One claimed reminder (main.py `telegram_bot_internal` claim_due_reminders, camel-cased). */
export interface DueReminder {
  rid: string;
  chatId: number;
  telegramUserId: number;
  languageCode: string | null;
  kind: ReminderKind;
  cloudId: string;
  ownerUid: string | null;
  /** null = a checklist-level reminder. */
  itemId: string | null;
  checklistName: string;
  itemText: string | null;
  fireAtMs: number | null;
  googleUid: string | null;
  timezone: string | null;
}

function str(v: unknown): string | null {
  return typeof v === "string" && v.length > 0 ? v : null;
}

function isId(v: unknown): v is number {
  return typeof v === "number" && Number.isSafeInteger(v) && v !== 0;
}

/** One entry of the claim response, or null when it cannot be delivered (no rid / chat / checklist). */
export function parseDueReminder(raw: unknown): DueReminder | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  const rid = o["rid"];
  const cloudId = str(o["checklist_cloud_id"]);
  if (typeof rid !== "string" || !RID_RE.test(rid) || !isId(o["chat_id"]) || !isId(o["telegram_user_id"]) || !cloudId) {
    return null;
  }
  let kind = o["kind"] as ReminderKind;
  if (!KINDS.includes(kind)) {
    console.warn(`[gisti-tg] reminder ${rid}: unknown kind "${String(o["kind"]).slice(0, 20)}" — shown as a one-shot`);
    kind = "oneshot";
  }
  return {
    rid,
    chatId: o["chat_id"] as number,
    telegramUserId: o["telegram_user_id"] as number,
    languageCode: str(o["language_code"]),
    kind,
    cloudId,
    ownerUid: str(o["checklist_owner_uid"]),
    itemId: str(o["item_id"]),
    checklistName: typeof o["checklist_name"] === "string" ? o["checklist_name"] : "",
    itemText: typeof o["item_text"] === "string" ? o["item_text"] : null,
    fireAtMs: typeof o["fire_at_ms"] === "number" ? o["fire_at_ms"] : null,
    googleUid: str(o["google_uid"]),
    timezone: isValidZone(o["timezone"]) ? o["timezone"] : null,
  };
}

export function parseClaimResponse(data: Record<string, unknown>): { reminders: DueReminder[]; malformed: number } {
  const raw = Array.isArray(data["reminders"]) ? data["reminders"] : [];
  const reminders: DueReminder[] = [];
  let malformed = 0;
  for (const r of raw) {
    const parsed = parseDueReminder(r);
    if (parsed) reminders.push(parsed);
    else malformed++;
  }
  return { reminders, malformed };
}

// ── stored button state (`rem:<rid>`) ───────────────────────────────────────────

/** What a reminder message's buttons act on — written when it is delivered. */
export interface ReminderMeta {
  cloudId: string;
  itemId: string | null;
  ownerUid: string | null;
  name: string;
  text: string | null;
  timezone: string | null;
  /** When it was stored (epoch ms) — the 7-day TTL runs from here. */
  at: number;
}

export function remKey(rid: string): string {
  return `${REM_PREFIX}${rid}`;
}

export function metaOf(r: DueReminder, now: number): ReminderMeta {
  return {
    cloudId: r.cloudId,
    itemId: r.itemId,
    ownerUid: r.ownerUid,
    name: r.checklistName,
    text: r.itemText,
    timezone: r.timezone,
    at: now,
  };
}

/** Store a reminder's button state, dropping entries older than the TTL first. */
export async function saveReminderMeta(store: KvStore, rid: string, meta: ReminderMeta, now: number): Promise<void> {
  for (const [key, m] of await store.list<ReminderMeta>({ prefix: REM_PREFIX })) {
    if (now - m.at >= REM_TTL_MS) await store.delete(key);
  }
  await store.put(remKey(rid), meta);
}

/** The button state of `rid`, or null when unknown or past the TTL. */
export async function loadReminderMeta(store: KvStore, rid: string, now: number): Promise<ReminderMeta | null> {
  const m = await store.get<ReminderMeta>(remKey(rid));
  return m && now - m.at < REM_TTL_MS ? m : null;
}

// ── rendering ───────────────────────────────────────────────────────────────────

export interface Counts {
  left: number;
  total: number;
}

/** Unchecked / all items of the list's view fill; null for a list without items. */
export function checklistCounts(c: ChecklistSyncData): Counts | null {
  const { done, total } = progress(checklistRows(c));
  return total > 0 ? { left: total - done, total } : null;
}

function orEllipsis(s: string | null): string {
  const v = (s ?? "").trim();
  return v.length > 0 ? v : "…";
}

/** What the reminder message says, shared by the delivered message and its edits. */
interface ReminderSubject {
  name: string;
  text: string | null;
  itemId: string | null;
}

function subjectOf(r: DueReminder): ReminderSubject {
  return { name: r.checklistName, text: r.itemText, itemId: r.itemId };
}

/** «⏰ <item>» + «<list>» for an item, «⏰ <list>» (+ «N of M left») for the whole list. Plain text. */
export function reminderBody(m: ReminderSubject, s: Strings, counts: Counts | null): string {
  if (m.itemId !== null) return `⏰ ${orEllipsis(m.text)}\n${orEllipsis(m.name)}`;
  const lines = [`⏰ ${orEllipsis(m.name)}`];
  if (counts) lines.push(s.remLeft(counts.left, counts.total));
  return lines.join("\n");
}

/** The delivered message: Done / In 1 hour / Open list for an item, Open list / In 1 hour for a list. */
export function renderReminder(r: DueReminder, s: Strings, counts: Counts | null): View {
  const done: InlineButton = { text: s.btnRemDone, callback_data: encodeCallback({ t: "remDone", rid: r.rid }) };
  const snooze: InlineButton = { text: s.btnRemSnooze, callback_data: encodeCallback({ t: "remSnooze", rid: r.rid }) };
  const open: InlineButton = { text: s.btnRemOpen, callback_data: encodeCallback({ t: "remOpen", rid: r.rid }) };
  const rows = r.itemId !== null ? [[done, snooze], [open]] : [[open, snooze]];
  return { text: reminderBody(subjectOf(r), s, counts), keyboard: { inline_keyboard: rows } };
}

/** The offer after linking — same texts and `rc:*` data as Python's (telegram_reminders.py). */
export function renderChannelOffer(s: Strings): View {
  const row = (text: string, channel: ReminderChannel): InlineButton[] => [
    { text, callback_data: encodeCallback({ t: "channel", channel, source: "offer" }) },
  ];
  return {
    text: s.remOffer,
    keyboard: {
      inline_keyboard: [row(s.btnOfferTelegram, "telegram"), row(s.btnOfferBoth, "both"), row(s.btnOfferNo, "push")],
    },
  };
}

/** "UTC+3 · Europe/Moscow"; an `Etc/…` zone (what `/timezone` stores) shows the offset only. */
export function zoneLabel(tz: string, now: number): string {
  const offset = offsetLabel(offsetMinutesAt(tz, now));
  return tz.startsWith("Etc/") ? offset : `${offset} · ${tz}`;
}

/** `/reminders`: current channel + zone, then the three choices with the current one marked. */
export function renderReminderSettings(settings: ReminderSettings, s: Strings, now: number): View {
  const parts = [s.remSettings(s.channelName[settings.channel], settings.timezone ? zoneLabel(settings.timezone, now) : null)];
  if (settings.channel !== "push" && !settings.timezone) parts.push(s.remNeedsTimezone);
  parts.push(s.remWhere);
  const order: ReminderChannel[] = ["telegram", "both", "push"];
  return {
    text: parts.join("\n\n"),
    keyboard: {
      inline_keyboard: order.map((ch) => [
        {
          text: `${ch === settings.channel ? "✅ " : ""}${s.btnChannel[ch]}`,
          callback_data: encodeCallback({ t: "channel", channel: ch, source: "command" }),
        },
      ]),
    },
  };
}

/** After a channel tap. Telegram chosen while the zone is unknown → the `/timezone` hint (nothing is scheduled without it). */
export function channelConfirmation(channel: ReminderChannel, timezone: string | null, s: Strings): string {
  const text = s.channelSet[channel];
  return channel !== "push" && timezone === null ? `${text}\n\n${s.remNeedsTimezone}` : text;
}

// ── item state (the same fill choice as mutate.ts toggleItem) ───────────────────

function findFillItem(c: ChecklistSyncData, itemId: string) {
  const fill = viewFill(c);
  if (!fill) return null;
  const items = parseFillItems(fill.itemsJson);
  // mutate.ts findFillIndex: by templateItemId first, then the fill item's own id (the server's item_id).
  return items.find((i) => i.templateItemId === itemId) ?? items.find((i) => i.id === itemId) ?? null;
}

export function itemState(c: ChecklistSyncData, itemId: string): "checked" | "unchecked" | "missing" {
  const it = findFillItem(c, itemId);
  return !it ? "missing" : it.checked ? "checked" : "unchecked";
}

/** The items page that shows `itemId` (0 when it is not on any). */
export function pageOfItem(c: ChecklistSyncData, itemId: string | null): number {
  if (itemId === null) return 0;
  const it = findFillItem(c, itemId);
  const rowId = it ? (it.templateItemId ?? it.id) : itemId;
  const idx = checklistRows(c).findIndex((r) => r.kind === "item" && r.itemId === rowId);
  return idx < 0 ? 0 : Math.floor(idx / ITEMS_PAGE_SIZE);
}

// ── cron fan-out (index.ts scheduled) ───────────────────────────────────────────

export type DeliveryOutcome = "sent" | "blocked" | "failed";

export interface CronDeps {
  /** `claim_due_reminders` → the raw response body. */
  claim(): Promise<Record<string, unknown>>;
  /** Hand one chat's reminders to its Durable Object (`idFromName(String(chat_id))`). */
  deliver(chatId: number, batch: DueReminder[]): Promise<DeliveryOutcome[]>;
}

export interface CronSummary {
  claimed: number;
  sent: number;
  blocked: number;
  failed: number;
  malformed: number;
}

async function forEachLimited<T>(items: T[], limit: number, fn: (item: T) => Promise<void>): Promise<void> {
  let next = 0;
  const worker = async (): Promise<void> => {
    while (next < items.length) await fn(items[next++]!);
  };
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
}

/**
 * One cron tick: claim, group by chat, deliver each chat's batch through its DO. A failing claim or a
 * failing DO is logged and never retried (the reminders are already claimed); one chat's failure
 * never stops the others. Never throws.
 */
export async function runReminderCron(deps: CronDeps): Promise<CronSummary> {
  const summary: CronSummary = { claimed: 0, sent: 0, blocked: 0, failed: 0, malformed: 0 };
  let data: Record<string, unknown>;
  try {
    data = await deps.claim();
  } catch (e) {
    console.error(`[gisti-tg] claim_due_reminders failed — the next tick claims what is still due: ${errText(e)}`);
    return summary;
  }
  const { reminders, malformed } = parseClaimResponse(data);
  summary.claimed = reminders.length;
  summary.malformed = malformed;
  if (malformed > 0) console.error(`[gisti-tg] claim_due_reminders returned ${malformed} malformed reminder(s) — claimed, not delivered`);

  const byChat = new Map<number, DueReminder[]>();
  for (const r of reminders) byChat.set(r.chatId, [...(byChat.get(r.chatId) ?? []), r]);
  await forEachLimited([...byChat.entries()], DELIVERY_CONCURRENCY, async ([chatId, batch]) => {
    try {
      for (const outcome of await deps.deliver(chatId, batch)) summary[outcome] += 1;
    } catch (e) {
      summary.failed += batch.length;
      console.error(
        `[gisti-tg] reminder delivery failed for ${batch.map((r) => r.rid).join(",")} — not re-sent: ${errText(e)}`,
      );
    }
  });
  if (reminders.length + malformed > 0) {
    console.log(
      `[gisti-tg] reminders: claimed ${summary.claimed}, sent ${summary.sent}, blocked ${summary.blocked}, failed ${summary.failed}, malformed ${summary.malformed}`,
    );
  }
  return summary;
}

// ── delivery (inside the chat's Durable Object) ─────────────────────────────────

export interface DeliveryDeps {
  store: KvStore;
  now(): number;
  getLink(telegramUserId: number): Promise<TelegramLink | null>;
  findChecklist(ids: string[], cloudId: string): Promise<OwnedChecklist | null>;
  send(chatId: number, text: string, keyboard: InlineKeyboard): Promise<unknown>;
  /** Never throws (analytics). */
  track(r: DueReminder, creditUserId: string | null, event: TgEvent): Promise<void>;
}

/** «N of M left» from a fresh read; null (logged) when the read fails or finds nothing. */
async function freshCounts(deps: DeliveryDeps, r: DueReminder): Promise<{ counts: Counts | null; creditUserId: string | null }> {
  try {
    const link = await deps.getLink(r.telegramUserId);
    if (!link) {
      console.warn(`[gisti-tg] reminder ${r.rid}: no link at delivery — sent without counts`);
      return { counts: null, creditUserId: null };
    }
    const owned = await deps.findChecklist(linkIds(link), r.cloudId);
    if (!owned) console.warn(`[gisti-tg] reminder ${r.rid}: checklist not found at delivery — sent without counts`);
    return { counts: owned ? checklistCounts(owned.checklist) : null, creditUserId: link.creditUserId };
  } catch (e) {
    console.warn(`[gisti-tg] reminder ${r.rid}: checklist read failed — sent without counts: ${errText(e)}`);
    return { counts: null, creditUserId: null };
  }
}

async function deliverOne(deps: DeliveryDeps, r: DueReminder): Promise<DeliveryOutcome> {
  const now = deps.now();
  try {
    await saveReminderMeta(deps.store, r.rid, metaOf(r, now), now);
  } catch (e) {
    // Still send: the reminder matters more than its buttons (a tap then answers «outdated»).
    console.error(`[gisti-tg] reminder ${r.rid}: button state not stored: ${errText(e)}`);
  }
  const { counts, creditUserId } = r.itemId === null ? await freshCounts(deps, r) : { counts: null, creditUserId: null };
  const view = renderReminder(r, t(pickLang(r.languageCode)), counts);
  try {
    await deps.send(r.chatId, view.text, view.keyboard);
  } catch (e) {
    if (e instanceof TelegramError && e.code === 403) {
      console.error(`[gisti-tg] reminder ${r.rid} not delivered: the user blocked the bot`);
      return "blocked";
    }
    console.error(`[gisti-tg] reminder ${r.rid} not delivered (claimed — never re-sent): ${errText(e)}`);
    return "failed";
  }
  await deps.track(r, creditUserId, { type: "tg_reminder_sent", kind: r.kind });
  return "sent";
}

/**
 * Deliver one chat's claimed reminders in order, ONE send attempt each. One failure never stops the
 * next; after a 403 (blocked) the rest are skipped without another call or log line.
 */
export async function deliverReminders(deps: DeliveryDeps, batch: DueReminder[]): Promise<DeliveryOutcome[]> {
  const out: DeliveryOutcome[] = [];
  let blocked = false;
  for (const r of batch) {
    if (blocked) {
      out.push("blocked");
      continue;
    }
    let outcome: DeliveryOutcome;
    try {
      outcome = await deliverOne(deps, r);
    } catch (e) {
      console.error(`[gisti-tg] reminder ${r.rid} delivery threw — not re-sent: ${errText(e)}`);
      outcome = "failed";
    }
    if (outcome === "blocked") blocked = true;
    out.push(outcome);
  }
  return out;
}

// ── the channel offer after `/start <token>` (app → bot) ───────────────────────

export interface OfferDeps {
  getSettings(googleUid: string): Promise<ReminderSettings>;
  send(chatId: number, text: string, keyboard: InlineKeyboard): Promise<unknown>;
}

/** Offer «reminders here?» only while the channel is push (absent counts as push). True when sent. */
export async function offerChannelIfPush(deps: OfferDeps, googleUid: string, chatId: number, lang: Lang): Promise<boolean> {
  const settings = await deps.getSettings(googleUid);
  if (settings.channel !== "push") return false;
  const view = renderChannelOffer(t(lang));
  await deps.send(chatId, view.text, view.keyboard);
  return true;
}

// ── button handlers ─────────────────────────────────────────────────────────────

export type Answer = (text?: string) => Promise<void>;

export interface TapDeps {
  store: KvStore;
  now(): number;
  getLink(telegramUserId: number): Promise<TelegramLink | null>;
  findChecklist(ids: string[], cloudId: string): Promise<OwnedChecklist | null>;
  writeChecklist(ownerId: string, c: ChecklistSyncData): Promise<void>;
  /** `snooze_reminder` → the raw response body; throws InternalApiError (404 `unknown_reminder`). */
  snooze(rid: string, minutes: number, telegramUserId: number): Promise<Record<string, unknown>>;
  getSettings(googleUid: string): Promise<ReminderSettings>;
  setChannel(googleUid: string, channel: ReminderChannel): Promise<void>;
  send(chatId: number, text: string, keyboard?: InlineKeyboard): Promise<unknown>;
  /** Edit the tapped message (a new message when it can no longer be edited — editOrSend). */
  edit(chatId: number, messageId: number | undefined, text: string, keyboard?: InlineKeyboard): Promise<void>;
  loginPrompt(who: TelegramIdentity, lang: Lang): Promise<void>;
  loadKeys(): Promise<KeyRegistryState>;
  saveKeys(keys: KeyRegistryState): Promise<void>;
  /** Never throws (analytics). */
  track(who: TelegramIdentity, creditUserId: string | null, event: TgEvent): Promise<void>;
}

export type ReminderTap = Extract<Callback, { t: "remDone" | "remSnooze" | "remOpen" | "channel" }>;

/** Every tap gets a visible answer: a callback toast, an edit, or a message. */
export async function onReminderTap(
  deps: TapDeps,
  cb: ReminderTap,
  who: TelegramIdentity,
  messageId: number | undefined,
  lang: Lang,
  answer: Answer,
): Promise<void> {
  switch (cb.t) {
    case "remDone":
      return onDone(deps, cb.rid, who, messageId, lang, answer);
    case "remSnooze":
      return onSnooze(deps, cb.rid, who, messageId, lang, answer);
    case "remOpen":
      return onOpen(deps, cb.rid, who, lang, answer);
    case "channel":
      return onChannel(deps, cb.channel, cb.source, who, messageId, lang, answer);
  }
}

/** The reminder's own text plus a closing note, buttons removed. */
function closedText(meta: ReminderMeta, s: Strings, note: string): string {
  return `${reminderBody(meta, s, null)}\n\n${note}`;
}

async function onDone(
  deps: TapDeps,
  rid: string,
  who: TelegramIdentity,
  messageId: number | undefined,
  lang: Lang,
  answer: Answer,
): Promise<void> {
  const s = t(lang);
  const meta = await loadReminderMeta(deps.store, rid, deps.now());
  if (!meta || meta.itemId === null) {
    await answer(s.remOutdated);
    return;
  }
  const link = await deps.getLink(who.telegramUserId);
  if (!link) {
    await answer();
    return deps.loginPrompt(who, lang);
  }
  const owned = await deps.findChecklist(linkIds(link), meta.cloudId);
  if (!owned) {
    await answer(s.checklistGone);
    await deps.edit(who.chatId, messageId, closedText(meta, s, s.checklistGone));
    return;
  }
  const state = itemState(owned.checklist, meta.itemId);
  // Already ticked → the same «done» edit with no write. toggleItem finds items exactly as itemState
  // does, so a null from it is the same "item gone".
  const next = state === "unchecked" ? toggleItem(owned.checklist, meta.itemId, true, deps.now()) : null;
  if (state === "missing" || (state === "unchecked" && !next)) {
    await answer(s.remItemGone);
    await deps.edit(who.chatId, messageId, closedText(meta, s, s.remItemGone));
    return;
  }
  if (next) await deps.writeChecklist(owned.ownerId, next);
  await answer();
  await deps.edit(who.chatId, messageId, `✅ ${orEllipsis(meta.text)}\n${orEllipsis(meta.name)}`);
  await deps.track(who, link.creditUserId, { type: "tg_reminder_action", action: "done" });
}

async function onSnooze(
  deps: TapDeps,
  rid: string,
  who: TelegramIdentity,
  messageId: number | undefined,
  lang: Lang,
  answer: Answer,
): Promise<void> {
  const s = t(lang);
  const meta = await loadReminderMeta(deps.store, rid, deps.now());
  if (!meta) {
    await answer(s.remOutdated);
    return;
  }
  let res: Record<string, unknown>;
  try {
    res = await deps.snooze(rid, SNOOZE_MINUTES, who.telegramUserId);
  } catch (e) {
    // 404 unknown_reminder: the entry is gone (unlinked, channel back to the phone) or not this user's.
    if (e instanceof InternalApiError && e.status === 404) {
      await answer(s.remOutdated);
      await deps.edit(who.chatId, messageId, closedText(meta, s, s.remOutdated));
      return;
    }
    // 409 channel_off: nothing was written — the channel is back on the phone, or the account is
    // unlinked / relinked elsewhere. No «I'll remind you» edit.
    if (e instanceof InternalApiError && e.status === 409 && e.code === "channel_off") {
      if (!(await deps.getLink(who.telegramUserId))) {
        await answer();
        return deps.loginPrompt(who, lang);
      }
      await answer(s.remChannelOff);
      return;
    }
    throw e; // 5xx / network → the queue retries; snoozing again only moves the same entry
  }
  const fireAt = res["fire_at_ms"];
  const zone = res["timezone"];
  const time = typeof fireAt === "number" && isValidZone(zone) ? formatHm(fireAt, zone) : null;
  await answer();
  await deps.edit(who.chatId, messageId, closedText(meta, s, s.remSnoozed(time)));
  await deps.track(who, null, { type: "tg_reminder_action", action: "snooze" });
}

async function onOpen(deps: TapDeps, rid: string, who: TelegramIdentity, lang: Lang, answer: Answer): Promise<void> {
  const s = t(lang);
  const meta = await loadReminderMeta(deps.store, rid, deps.now());
  if (!meta) {
    await answer(s.remOutdated);
    return;
  }
  const link = await deps.getLink(who.telegramUserId);
  if (!link) {
    await answer();
    return deps.loginPrompt(who, lang);
  }
  const owned = await deps.findChecklist(linkIds(link), meta.cloudId);
  if (!owned) {
    await answer(s.checklistGone);
    return;
  }
  const keys = await deps.loadKeys();
  const view = renderItemsPage(owned.checklist, pageOfItem(owned.checklist, meta.itemId), s, keys);
  await deps.saveKeys(keys);
  await answer();
  await deps.send(who.chatId, view.text, view.keyboard); // a NEW message: the reminder stays
  await deps.track(who, link.creditUserId, { type: "tg_reminder_action", action: "open" });
}

async function onChannel(
  deps: TapDeps,
  channel: ReminderChannel,
  source: ChannelSource,
  who: TelegramIdentity,
  messageId: number | undefined,
  lang: Lang,
  answer: Answer,
): Promise<void> {
  const s = t(lang);
  const link = await deps.getLink(who.telegramUserId);
  if (!link) {
    await answer();
    return deps.loginPrompt(who, lang);
  }
  const timezone = channel === "push" ? null : (await deps.getSettings(link.googleUid)).timezone;
  await deps.setChannel(link.googleUid, channel);
  await answer();
  await deps.edit(who.chatId, messageId, channelConfirmation(channel, timezone, s));
  await deps.track(who, link.creditUserId, { type: "tg_reminder_channel_set", channel, source });
}

/** `/reminders`: not linked → the sign-in prompt; else the settings view. */
export async function showReminderSettings(deps: TapDeps, who: TelegramIdentity, lang: Lang): Promise<void> {
  const link = await deps.getLink(who.telegramUserId);
  if (!link) return deps.loginPrompt(who, lang);
  const view = renderReminderSettings(await deps.getSettings(link.googleUid), t(lang), deps.now());
  await deps.send(who.chatId, view.text, view.keyboard);
}
