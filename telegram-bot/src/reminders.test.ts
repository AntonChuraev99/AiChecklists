import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { OwnedChecklist } from "../../mcp-server/src/firestore";
import { parseFillItems, type ChecklistSyncData } from "../../mcp-server/src/model";
import { createEmptyChecklist, toggleItem } from "../../mcp-server/src/mutate";
import type { TgEvent } from "./analytics";
import {
  InternalApiError,
  parseReminderSettings,
  type ReminderChannel,
  type ReminderSettings,
  type TelegramIdentity,
  type TelegramLink,
} from "./backend";
import { byteLength, CALLBACK_MAX_BYTES, emptyRegistry, type KeyRegistryState } from "./callback";
import { LANGS, t } from "./i18n";
import type { KvStore } from "./queue";
import {
  CLAIM_LIMIT,
  deliverReminders,
  itemState,
  loadReminderMeta,
  metaOf,
  offerChannelIfPush,
  onReminderTap,
  pageOfItem,
  parseClaimResponse,
  REM_TTL_MS,
  remKey,
  renderChannelOffer,
  renderReminder,
  renderReminderSettings,
  runReminderCron,
  saveReminderMeta,
  showReminderSettings,
  SNOOZE_MINUTES,
  type DeliveryDeps,
  type DeliveryOutcome,
  type DueReminder,
  type ReminderMeta,
  type TapDeps,
} from "./reminders";
import { TelegramError, type InlineKeyboard } from "./telegram";

/** In-memory stand-in for Durable Object storage (sorted list(), like the real one). */
class MemoryStore implements KvStore {
  readonly data = new Map<string, unknown>();
  async get<T>(key: string): Promise<T | undefined> {
    return structuredClone(this.data.get(key)) as T | undefined;
  }
  async put<T>(key: string, value: T): Promise<void> {
    this.data.set(key, structuredClone(value));
  }
  async delete(key: string): Promise<boolean> {
    return this.data.delete(key);
  }
  async list<T>(options: { prefix: string; limit?: number }): Promise<Map<string, T>> {
    const keys = [...this.data.keys()].filter((k) => k.startsWith(options.prefix)).sort();
    const out = new Map<string, T>();
    for (const k of keys.slice(0, options.limit ?? keys.length)) out.set(k, structuredClone(this.data.get(k)) as T);
    return out;
  }
}

const RID = "0123456789abcdef01234567";
const RID2 = "fedcba9876543210fedcba98";
const NOW = Date.UTC(2026, 10, 20, 12, 0);
const LINK: TelegramLink = { googleUid: "g1", creditUserId: "credit-1" };
const who: TelegramIdentity = { telegramUserId: 111, chatId: 111, username: null, firstName: "A", languageCode: "en" };

/** «Shopping»: Milk / Bread / Eggs; the server's item_id is the default fill item's own id. */
function shopping(): { c: ChecklistSyncData; milk: string; bread: string } {
  const c = createEmptyChecklist("Shopping", ["Milk", "Bread", "Eggs"], NOW - 60_000);
  const items = parseFillItems(c.fills[0]!.itemsJson);
  return { c, milk: items[0]!.id, bread: items[1]!.id };
}

function due(over: Partial<DueReminder> = {}): DueReminder {
  return {
    rid: RID,
    chatId: 111,
    telegramUserId: 111,
    languageCode: "en",
    kind: "oneshot",
    cloudId: "cl-1",
    ownerUid: "g1",
    itemId: "fill-item-1",
    checklistName: "Shopping",
    itemText: "Milk",
    fireAtMs: NOW,
    googleUid: "g1",
    timezone: "Europe/Moscow",
    ...over,
  };
}

function rawDue(over: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    rid: RID,
    chat_id: 111,
    telegram_user_id: 111,
    language_code: "ru",
    kind: "repeat",
    checklist_cloud_id: "cl-1",
    checklist_owner_uid: "g1",
    item_id: "fill-item-1",
    checklist_name: "Shopping",
    item_text: "Milk",
    fire_at_ms: NOW,
    google_uid: "g1",
    timezone: "Europe/Moscow",
    ...over,
  };
}

function callbackData(k: InlineKeyboard): string[][] {
  return k.inline_keyboard.map((row) => row.map((b) => ("callback_data" in b ? b.callback_data : `url:${b.url}`)));
}

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  vi.spyOn(console, "warn").mockImplementation(() => {});
  vi.spyOn(console, "log").mockImplementation(() => {});
});
afterEach(() => vi.restoreAllMocks());

// ── claim response ──────────────────────────────────────────────────────────────

describe("parseClaimResponse", () => {
  it("maps the server's fields and counts entries it cannot deliver", () => {
    const { reminders, malformed } = parseClaimResponse({
      success: true,
      reminders: [
        rawDue(),
        rawDue({ rid: "nothex", item_id: null }),
        rawDue({ chat_id: null }),
        rawDue({ checklist_cloud_id: "" }),
        rawDue({ rid: RID2, item_id: null, item_text: null, kind: "weird", timezone: "Not/AZone" }),
      ],
    });
    expect(malformed).toBe(3);
    expect(reminders).toEqual([
      {
        rid: RID,
        chatId: 111,
        telegramUserId: 111,
        languageCode: "ru",
        kind: "repeat",
        cloudId: "cl-1",
        ownerUid: "g1",
        itemId: "fill-item-1",
        checklistName: "Shopping",
        itemText: "Milk",
        fireAtMs: NOW,
        googleUid: "g1",
        timezone: "Europe/Moscow",
      },
      expect.objectContaining({ rid: RID2, itemId: null, itemText: null, kind: "oneshot", timezone: null }),
    ]);
  });

  it("treats a body without a reminders array as nothing claimed", () => {
    expect(parseClaimResponse({ success: true })).toEqual({ reminders: [], malformed: 0 });
  });

  it("claims within the server's limits (telegram_reminders.py:51-52)", () => {
    expect(CLAIM_LIMIT).toBeGreaterThanOrEqual(1);
    expect(CLAIM_LIMIT).toBeLessThanOrEqual(200);
  });
});

// ── rendering ───────────────────────────────────────────────────────────────────

describe("renderReminder", () => {
  it("item reminder: «⏰ item» + list line, Done / In 1 hour / Open list", () => {
    const v = renderReminder(due(), t("en"), null);
    expect(v.text).toBe("⏰ Milk\nShopping");
    expect(callbackData(v.keyboard)).toEqual([[`rd:${RID}`, `rs:${RID}`], [`ro:${RID}`]]);
    expect(v.keyboard.inline_keyboard.flat().map((b) => b.text)).toEqual(["✅ Done", "⏰ In 1 hour", "📋 Open list"]);
  });

  it("checklist-level reminder: the list name, the counts when known, no Done", () => {
    const withCounts = renderReminder(due({ itemId: null, itemText: null, languageCode: "ru" }), t("ru"), { left: 2, total: 3 });
    expect(withCounts.text).toBe("⏰ Shopping\nОсталось 2 из 3");
    expect(callbackData(withCounts.keyboard)).toEqual([[`ro:${RID}`, `rs:${RID}`]]);
    expect(renderReminder(due({ itemId: null, itemText: null }), t("en"), null).text).toBe("⏰ Shopping");
    expect(renderReminder(due({ itemId: null }), t("hi"), { left: 1, total: 4 }).text).toBe("⏰ Shopping\n4 में से 1 बाकी");
  });

  it("a snoozed reminder reads like the original; every language, every button fits 64 bytes", () => {
    for (const lang of LANGS) {
      const s = t(lang);
      expect(renderReminder(due({ kind: "snooze" }), s, null)).toEqual(renderReminder(due(), s, null));
      for (const r of [due(), due({ itemId: null })]) {
        for (const b of renderReminder(r, s, { left: 1, total: 2 }).keyboard.inline_keyboard.flat()) {
          expect(b.text.length).toBeGreaterThan(0);
          if ("callback_data" in b) expect(byteLength(b.callback_data)).toBeLessThanOrEqual(CALLBACK_MAX_BYTES);
        }
      }
    }
  });

  it("blank names and texts render as «…», never an empty line", () => {
    expect(renderReminder(due({ itemText: "  ", checklistName: "" }), t("en"), null).text).toBe("⏰ …\n…");
  });
});

describe("channel views", () => {
  it("the offer carries exactly Python's callback data rc:t / rc:b / rc:p and its texts", () => {
    const v = renderChannelOffer(t("en"));
    expect(v.text).toBe("Telegram is linked to Gisti. Want your reminders here?");
    expect(callbackData(v.keyboard)).toEqual([["rc:t"], ["rc:b"], ["rc:p"]]);
    expect(v.keyboard.inline_keyboard.flat().map((b) => b.text)).toEqual([
      "Send reminders here instead of the phone",
      "Both",
      "No",
    ]);
    expect(renderChannelOffer(t("ru")).keyboard.inline_keyboard.flat().map((b) => b.text)).toEqual([
      "Присылать сюда вместо телефона",
      "И сюда, и на телефон",
      "Нет",
    ]);
  });

  it("/reminders marks the current channel and shows the zone", () => {
    const v = renderReminderSettings({ channel: "both", timezone: "Europe/Moscow" }, t("en"), NOW);
    expect(v.text).toContain("here in Telegram and on the phone");
    expect(v.text).toContain("UTC+3 · Europe/Moscow");
    expect(v.text).not.toContain("/timezone");
    expect(callbackData(v.keyboard)).toEqual([["rc:t:c"], ["rc:b:c"], ["rc:p:c"]]);
    expect(v.keyboard.inline_keyboard.flat().map((b) => b.text)).toEqual(["Telegram only", "✅ Telegram and phone", "Phone only"]);
  });

  it("/reminders: an Etc zone shows the offset only; Telegram without a zone warns", () => {
    expect(renderReminderSettings({ channel: "push", timezone: "Etc/GMT-3" }, t("en"), NOW).text).toContain("Time zone: UTC+3\n");
    const noZone = renderReminderSettings({ channel: "telegram", timezone: null }, t("en"), NOW);
    expect(noZone.text).toContain("not set — /timezone");
    expect(noZone.text).toContain(t("en").remNeedsTimezone);
    expect(renderReminderSettings({ channel: "push", timezone: null }, t("en"), NOW).text).not.toContain(t("en").remNeedsTimezone);
  });
});

describe("parseReminderSettings", () => {
  it("absent or unknown channel is push (Python normalize_channel); an invalid zone is unknown", () => {
    expect(parseReminderSettings(null)).toEqual({ channel: "push", timezone: null });
    expect(parseReminderSettings({ reminder_channel: "sms", timezone: "Asia/Kolkata" })).toEqual({
      channel: "push",
      timezone: "Asia/Kolkata",
    });
    expect(parseReminderSettings({ reminder_channel: "telegram", timezone: "Mars/Base" })).toEqual({
      channel: "telegram",
      timezone: null,
    });
    expect(parseReminderSettings({ reminder_channel: "both" }).channel).toBe("both");
  });
});

describe("itemState / pageOfItem", () => {
  it("finds the item by the server's fill-item id, like mutate.ts toggleItem", () => {
    const { c, milk } = shopping();
    expect(itemState(c, milk)).toBe("unchecked");
    expect(itemState(toggleItem(c, milk, true, NOW)!, milk)).toBe("checked");
    expect(itemState(c, "gone")).toBe("missing");
  });

  it("opens the page that shows the item", () => {
    const c = createEmptyChecklist("Long", Array.from({ length: 25 }, (_, i) => `Item ${i}`), NOW);
    const items = parseFillItems(c.fills[0]!.itemsJson);
    expect(pageOfItem(c, items[0]!.id)).toBe(0);
    expect(pageOfItem(c, items[12]!.id)).toBe(1);
    expect(pageOfItem(c, items[24]!.id)).toBe(2);
    expect(pageOfItem(c, null)).toBe(0);
    expect(pageOfItem(c, "gone")).toBe(0);
  });
});

// ── stored button state ─────────────────────────────────────────────────────────

describe("reminder meta", () => {
  it("expires after 7 days and is pruned on the next write", async () => {
    const store = new MemoryStore();
    const old = NOW - REM_TTL_MS - 1;
    await saveReminderMeta(store, RID2, metaOf(due({ rid: RID2 }), old), old);
    expect(await loadReminderMeta(store, RID2, NOW)).toBeNull();
    await saveReminderMeta(store, RID, metaOf(due(), NOW), NOW);
    expect(store.data.has(remKey(RID2))).toBe(false);
    expect(await loadReminderMeta(store, RID, NOW + REM_TTL_MS - 1)).toMatchObject({ cloudId: "cl-1", itemId: "fill-item-1" });
  });
});

// ── delivery ────────────────────────────────────────────────────────────────────

function deliveryDeps(over: Partial<DeliveryDeps> = {}) {
  const store = new MemoryStore();
  const sends: Array<{ chatId: number; text: string; keyboard: InlineKeyboard }> = [];
  const events: TgEvent[] = [];
  const deps: DeliveryDeps = {
    store,
    now: () => NOW,
    getLink: async () => LINK,
    findChecklist: async () => null,
    send: async (chatId, text, keyboard) => {
      sends.push({ chatId, text, keyboard });
      return {};
    },
    track: async (_r, _credit, e) => {
      events.push(e);
    },
    ...over,
  };
  return { deps, store, sends, events };
}

describe("deliverReminders", () => {
  it("stores the button state, sends once and counts tg_reminder_sent with the kind", async () => {
    const { deps, store, sends, events } = deliveryDeps();
    expect(await deliverReminders(deps, [due({ kind: "repeat" })])).toEqual(["sent"]);
    expect(sends).toHaveLength(1);
    expect(sends[0]!.text).toBe("⏰ Milk\nShopping");
    expect(store.data.get(remKey(RID))).toMatchObject({ cloudId: "cl-1", itemId: "fill-item-1", name: "Shopping", at: NOW });
    expect(events).toEqual([{ type: "tg_reminder_sent", kind: "repeat" }]);
  });

  it("checklist-level: counts from a fresh read; a failed read still sends, without counts", async () => {
    const { c, milk } = shopping();
    const owned: OwnedChecklist = { checklist: toggleItem(c, milk, true, NOW)!, ownerId: "g1" };
    const ok = deliveryDeps({ findChecklist: async (ids) => (ids.includes("g1") ? owned : null) });
    await deliverReminders(ok.deps, [due({ itemId: null, itemText: null })]);
    expect(ok.sends[0]!.text).toBe("⏰ Shopping\n2 of 3 left");

    const down = deliveryDeps({
      findChecklist: async () => {
        throw new Error("firestore down");
      },
    });
    expect(await deliverReminders(down.deps, [due({ itemId: null, itemText: null })])).toEqual(["sent"]);
    expect(down.sends[0]!.text).toBe("⏰ Shopping");
    expect(console.warn).toHaveBeenCalled();
  });

  it("one failed send does not stop the next, and nothing is sent twice", async () => {
    const send = vi
      .fn<DeliveryDeps["send"]>()
      .mockRejectedValueOnce(new TelegramError("sendMessage", 500, "Internal"))
      .mockResolvedValue({});
    const { deps, events } = deliveryDeps({ send });
    expect(await deliverReminders(deps, [due(), due({ rid: RID2 })])).toEqual(["failed", "sent"]);
    expect(send).toHaveBeenCalledTimes(2);
    expect(events).toHaveLength(1);
  });

  it("403 (blocked) → logged once, the rest of the chat's batch is not attempted", async () => {
    const send = vi.fn<DeliveryDeps["send"]>().mockRejectedValue(new TelegramError("sendMessage", 403, "Forbidden: bot was blocked by the user"));
    const { deps } = deliveryDeps({ send });
    const third = "aaaaaaaaaaaaaaaaaaaaaaaa";
    expect(await deliverReminders(deps, [due(), due({ rid: RID2 }), due({ rid: third })])).toEqual(["blocked", "blocked", "blocked"]);
    expect(send).toHaveBeenCalledTimes(1);
    expect(console.error).toHaveBeenCalledTimes(1);
  });

  it("a storage failure still delivers the reminder", async () => {
    const { deps, sends } = deliveryDeps();
    deps.store = {
      ...deps.store,
      list: async () => {
        throw new Error("storage down");
      },
      get: async () => undefined,
      put: async () => undefined,
      delete: async () => true,
    };
    expect(await deliverReminders(deps, [due()])).toEqual(["sent"]);
    expect(sends).toHaveLength(1);
  });
});

// ── cron fan-out ────────────────────────────────────────────────────────────────

describe("runReminderCron", () => {
  it("groups by chat, delivers each chat once, and one DO failure does not stop the rest", async () => {
    const deliver = vi.fn(async (chatId: number, batch: DueReminder[]): Promise<DeliveryOutcome[]> => {
      if (chatId === 222) throw new Error("DO reset");
      return batch.map(() => "sent");
    });
    const summary = await runReminderCron({
      claim: async () => ({
        success: true,
        reminders: [
          rawDue({ rid: "a".repeat(24), chat_id: 111 }),
          rawDue({ rid: "b".repeat(24), chat_id: 222, telegram_user_id: 222 }),
          rawDue({ rid: "c".repeat(24), chat_id: 111 }),
          rawDue({ rid: "d".repeat(24), chat_id: 333, telegram_user_id: 333 }),
          { rid: "bad" },
        ],
      }),
      deliver,
    });
    expect(deliver).toHaveBeenCalledTimes(3);
    const batches = new Map(deliver.mock.calls.map(([chatId, batch]) => [chatId, batch.map((r) => r.rid)]));
    expect(batches.get(111)).toEqual(["a".repeat(24), "c".repeat(24)]);
    expect(batches.get(333)).toEqual(["d".repeat(24)]);
    expect(summary).toEqual({ claimed: 4, sent: 3, blocked: 0, failed: 1, malformed: 1 });
  });

  it("a failed claim is logged, never retried, and never throws", async () => {
    const claim = vi.fn(async () => {
      throw new InternalApiError(503, "unavailable", "claim_due_reminders");
    });
    const deliver = vi.fn();
    await expect(runReminderCron({ claim, deliver })).resolves.toEqual({ claimed: 0, sent: 0, blocked: 0, failed: 0, malformed: 0 });
    expect(claim).toHaveBeenCalledTimes(1);
    expect(deliver).not.toHaveBeenCalled();
    expect(console.error).toHaveBeenCalled();
  });
});

// ── the offer after /start ──────────────────────────────────────────────────────

describe("offerChannelIfPush", () => {
  async function run(settings: ReminderSettings) {
    const send = vi.fn(async () => ({}));
    const sent = await offerChannelIfPush({ getSettings: async () => settings, send }, "g1", 111, "en");
    return { sent, send };
  }

  it("offers while the channel is push — absent counts as push", async () => {
    const { sent, send } = await run(parseReminderSettings({}));
    expect(sent).toBe(true);
    expect(send).toHaveBeenCalledTimes(1);
    const [chatId, text, keyboard] = send.mock.calls[0] as unknown as [number, string, InlineKeyboard];
    expect(chatId).toBe(111);
    expect(text).toBe(t("en").remOffer);
    expect(callbackData(keyboard)).toEqual([["rc:t"], ["rc:b"], ["rc:p"]]);
    expect((await run({ channel: "push", timezone: "Europe/Moscow" })).sent).toBe(true);
  });

  it("does not offer when Telegram is already chosen", async () => {
    for (const channel of ["telegram", "both"] as ReminderChannel[]) {
      const { sent, send } = await run({ channel, timezone: null });
      expect(sent).toBe(false);
      expect(send).not.toHaveBeenCalled();
    }
  });
});

// ── button handlers ─────────────────────────────────────────────────────────────

interface Recorded {
  answers: Array<string | undefined>;
  writes: Array<{ ownerId: string; c: ChecklistSyncData }>;
  edits: Array<{ messageId: number | undefined; text: string; keyboard?: InlineKeyboard }>;
  sends: Array<{ text: string; keyboard?: InlineKeyboard }>;
  events: TgEvent[];
  channels: Array<{ googleUid: string; channel: ReminderChannel }>;
  snoozes: Array<{ rid: string; minutes: number; telegramUserId: number }>;
  loginPrompts: number;
  settingsReads: number;
}

function tapDeps(opts: {
  owned?: OwnedChecklist | null;
  link?: TelegramLink | null;
  settings?: ReminderSettings;
  snooze?: TapDeps["snooze"];
  meta?: ReminderMeta | null;
}) {
  const store = new MemoryStore();
  const rec: Recorded = {
    answers: [],
    writes: [],
    edits: [],
    sends: [],
    events: [],
    channels: [],
    snoozes: [],
    loginPrompts: 0,
    settingsReads: 0,
  };
  let keys: KeyRegistryState = emptyRegistry();
  const deps: TapDeps = {
    store,
    now: () => NOW,
    getLink: async () => (opts.link === undefined ? LINK : opts.link),
    findChecklist: async () => opts.owned ?? null,
    writeChecklist: async (ownerId, c) => {
      rec.writes.push({ ownerId, c });
    },
    snooze:
      opts.snooze ??
      (async (rid, minutes, telegramUserId) => {
        rec.snoozes.push({ rid, minutes, telegramUserId });
        return { success: true, rid: RID2, fire_at_ms: Date.UTC(2026, 10, 20, 13, 0), timezone: "Europe/Moscow" };
      }),
    getSettings: async () => {
      rec.settingsReads++;
      return opts.settings ?? { channel: "push", timezone: "Europe/Moscow" };
    },
    setChannel: async (googleUid, channel) => {
      rec.channels.push({ googleUid, channel });
    },
    send: async (_chatId, text, keyboard) => {
      rec.sends.push({ text, keyboard });
      return {};
    },
    edit: async (_chatId, messageId, text, keyboard) => {
      rec.edits.push({ messageId, text, keyboard });
    },
    loginPrompt: async () => {
      rec.loginPrompts++;
    },
    loadKeys: async () => structuredClone(keys),
    saveKeys: async (k) => {
      keys = k;
    },
    track: async (_who, _credit, e) => {
      rec.events.push(e);
    },
  };
  const answer = async (text?: string) => {
    rec.answers.push(text);
  };
  return { deps, store, rec, answer };
}

async function seedMeta(store: MemoryStore, over: Partial<DueReminder> = {}, at = NOW): Promise<void> {
  const r = due(over);
  await saveReminderMeta(store, r.rid, metaOf(r, at), at);
}

describe("Done", () => {
  it("ticks the item in the doc it was found in and edits the message to «✅ item»", async () => {
    const { c, milk } = shopping();
    const { deps, store, rec, answer } = tapDeps({ owned: { checklist: c, ownerId: "credit-1" } });
    await seedMeta(store, { itemId: milk });
    await onReminderTap(deps, { t: "remDone", rid: RID }, who, 77, "en", answer);
    expect(rec.writes).toHaveLength(1);
    expect(rec.writes[0]!.ownerId).toBe("credit-1");
    expect(itemState(rec.writes[0]!.c, milk)).toBe("checked");
    expect(rec.edits).toEqual([{ messageId: 77, text: "✅ Milk\nShopping", keyboard: undefined }]);
    expect(rec.answers).toEqual([undefined]);
    expect(rec.events).toEqual([{ type: "tg_reminder_action", action: "done" }]);
  });

  it("an already ticked item → the same edit, no write", async () => {
    const { c, milk } = shopping();
    const { deps, store, rec, answer } = tapDeps({ owned: { checklist: toggleItem(c, milk, true, NOW)!, ownerId: "g1" } });
    await seedMeta(store, { itemId: milk });
    await onReminderTap(deps, { t: "remDone", rid: RID }, who, 77, "en", answer);
    expect(rec.writes).toHaveLength(0);
    expect(rec.edits.map((e) => e.text)).toEqual(["✅ Milk\nShopping"]);
  });

  it("an item that is gone → «item no longer exists», no write", async () => {
    const { c } = shopping();
    const { deps, store, rec, answer } = tapDeps({ owned: { checklist: c, ownerId: "g1" } });
    await seedMeta(store, { itemId: "deleted-item" });
    await onReminderTap(deps, { t: "remDone", rid: RID }, who, 77, "ru", answer);
    expect(rec.writes).toHaveLength(0);
    expect(rec.answers).toEqual([t("ru").remItemGone]);
    expect(rec.edits[0]!.text).toBe(`⏰ Milk\nShopping\n\n${t("ru").remItemGone}`);
    expect(rec.events).toEqual([]);
  });

  it("an unknown or expired rid → «this reminder is outdated», nothing read or written", async () => {
    const { deps, store, rec, answer } = tapDeps({});
    const getLink = vi.spyOn(deps, "getLink");
    await onReminderTap(deps, { t: "remDone", rid: RID }, who, 77, "en", answer);
    await seedMeta(store, { rid: RID2 }, NOW - REM_TTL_MS);
    await onReminderTap(deps, { t: "remDone", rid: RID2 }, who, 77, "en", answer);
    expect(rec.answers).toEqual([t("en").remOutdated, t("en").remOutdated]);
    expect(getLink).not.toHaveBeenCalled();
    expect(rec.writes).toHaveLength(0);
    expect(rec.edits).toHaveLength(0);
  });

  it("not linked → the sign-in prompt, no write", async () => {
    const { deps, store, rec, answer } = tapDeps({ link: null });
    await seedMeta(store);
    await onReminderTap(deps, { t: "remDone", rid: RID }, who, 77, "en", answer);
    expect(rec.loginPrompts).toBe(1);
    expect(rec.writes).toHaveLength(0);
  });
});

describe("In 1 hour", () => {
  it("snoozes for 60 minutes as this Telegram user and shows the local time", async () => {
    const { deps, store, rec, answer } = tapDeps({});
    await seedMeta(store);
    await onReminderTap(deps, { t: "remSnooze", rid: RID }, who, 77, "en", answer);
    expect(rec.snoozes).toEqual([{ rid: RID, minutes: SNOOZE_MINUTES, telegramUserId: 111 }]);
    expect(SNOOZE_MINUTES).toBe(60);
    expect(rec.edits[0]!.text).toBe("⏰ Milk\nShopping\n\n⏰ I'll remind you again at 16:00.");
    expect(rec.events).toEqual([{ type: "tg_reminder_action", action: "snooze" }]);
  });

  it("an unknown zone omits the clock", async () => {
    const { deps, store, rec, answer } = tapDeps({
      snooze: async () => ({ success: true, rid: RID2, fire_at_ms: NOW + 3_600_000, timezone: null }),
    });
    await seedMeta(store);
    await onReminderTap(deps, { t: "remSnooze", rid: RID }, who, 77, "ru", answer);
    expect(rec.edits[0]!.text).toContain("⏰ Напомню ещё раз через час.");
  });

  it("404 unknown_reminder → «outdated», buttons removed; other failures go to the queue retry", async () => {
    const gone = tapDeps({
      snooze: async () => {
        throw new InternalApiError(404, "unknown_reminder", "snooze_reminder");
      },
    });
    await seedMeta(gone.store);
    await onReminderTap(gone.deps, { t: "remSnooze", rid: RID }, who, 77, "en", gone.answer);
    expect(gone.rec.answers).toEqual([t("en").remOutdated]);
    expect(gone.rec.edits).toEqual([{ messageId: 77, text: `⏰ Milk\nShopping\n\n${t("en").remOutdated}`, keyboard: undefined }]);
    expect(gone.rec.events).toEqual([]);

    const down = tapDeps({
      snooze: async () => {
        throw new InternalApiError(503, "unavailable", "snooze_reminder");
      },
    });
    await seedMeta(down.store);
    await expect(onReminderTap(down.deps, { t: "remSnooze", rid: RID }, who, 77, "en", down.answer)).rejects.toBeInstanceOf(
      InternalApiError,
    );
  });
});

describe("In 1 hour — 409 channel_off", () => {
  const off = async () => {
    throw new InternalApiError(409, "channel_off", "snooze_reminder");
  };

  it("linked → a visible «reminders are off» answer, no «I'll remind you» edit", async () => {
    for (const lang of LANGS) {
      const { deps, store, rec, answer } = tapDeps({ snooze: off });
      await seedMeta(store);
      await onReminderTap(deps, { t: "remSnooze", rid: RID }, who, 77, lang, answer);
      expect(rec.answers).toEqual([t(lang).remChannelOff]);
      expect(t(lang).remChannelOff).toContain("/reminders");
      expect(rec.edits).toHaveLength(0);
      expect(rec.events).toEqual([]);
    }
  });

  it("unlinked → the sign-in prompt instead", async () => {
    const { deps, store, rec, answer } = tapDeps({ snooze: off, link: null });
    await seedMeta(store);
    await onReminderTap(deps, { t: "remSnooze", rid: RID }, who, 77, "en", answer);
    expect(rec.loginPrompts).toBe(1);
    expect(rec.answers).toEqual([undefined]);
    expect(rec.edits).toHaveLength(0);
  });
});

describe("Open list", () => {
  it("sends the items view as a NEW message and keeps the reminder", async () => {
    const { c, bread } = shopping();
    const { deps, store, rec, answer } = tapDeps({ owned: { checklist: c, ownerId: "g1" } });
    await seedMeta(store, { itemId: bread });
    await onReminderTap(deps, { t: "remOpen", rid: RID }, who, 77, "en", answer);
    expect(rec.edits).toHaveLength(0);
    expect(rec.sends).toHaveLength(1);
    expect(rec.sends[0]!.text).toContain("Shopping\n0/3 done");
    expect(rec.answers).toEqual([undefined]);
    expect(rec.events).toEqual([{ type: "tg_reminder_action", action: "open" }]);
  });

  it("a deleted checklist answers «checklist no longer exists»", async () => {
    const { deps, store, rec, answer } = tapDeps({ owned: null });
    await seedMeta(store, { itemId: null });
    await onReminderTap(deps, { t: "remOpen", rid: RID }, who, 77, "en", answer);
    expect(rec.answers).toEqual([t("en").checklistGone]);
    expect(rec.sends).toHaveLength(0);
  });
});

describe("channel choice (rc:*)", () => {
  it("writes the channel and confirms in place; source comes from the button", async () => {
    const { deps, rec, answer } = tapDeps({ settings: { channel: "push", timezone: "Europe/Moscow" } });
    await onReminderTap(deps, { t: "channel", channel: "telegram", source: "offer" }, who, 55, "en", answer);
    expect(rec.channels).toEqual([{ googleUid: "g1", channel: "telegram" }]);
    expect(rec.edits).toEqual([{ messageId: 55, text: t("en").channelSet.telegram, keyboard: undefined }]);
    expect(rec.events).toEqual([{ type: "tg_reminder_channel_set", channel: "telegram", source: "offer" }]);
  });

  it("Telegram or both without a known zone → the /timezone hint", async () => {
    const { deps, rec, answer } = tapDeps({ settings: { channel: "push", timezone: null } });
    await onReminderTap(deps, { t: "channel", channel: "both", source: "command" }, who, 55, "ru", answer);
    expect(rec.edits[0]!.text).toBe(`${t("ru").channelSet.both}\n\n${t("ru").remNeedsTimezone}`);
    expect(rec.events).toEqual([{ type: "tg_reminder_channel_set", channel: "both", source: "command" }]);
  });

  it("push needs no zone (not even read)", async () => {
    const { deps, rec, answer } = tapDeps({ settings: { channel: "telegram", timezone: null } });
    await onReminderTap(deps, { t: "channel", channel: "push", source: "offer" }, who, 55, "en", answer);
    expect(rec.settingsReads).toBe(0);
    expect(rec.channels).toEqual([{ googleUid: "g1", channel: "push" }]);
    expect(rec.edits[0]!.text).toBe(t("en").channelSet.push);
  });

  it("not linked → the sign-in prompt, nothing written", async () => {
    const { deps, rec, answer } = tapDeps({ link: null });
    await onReminderTap(deps, { t: "channel", channel: "telegram", source: "offer" }, who, 55, "en", answer);
    expect(rec.loginPrompts).toBe(1);
    expect(rec.channels).toHaveLength(0);
    expect(rec.answers).toEqual([undefined]);
  });
});

describe("/reminders", () => {
  it("linked → the current channel and the three buttons; not linked → the sign-in prompt", async () => {
    const linked = tapDeps({ settings: { channel: "telegram", timezone: "Asia/Kolkata" } });
    await showReminderSettings(linked.deps, who, "en");
    expect(linked.rec.sends).toHaveLength(1);
    expect(linked.rec.sends[0]!.text).toContain("UTC+5:30 · Asia/Kolkata");
    expect(linked.rec.sends[0]!.keyboard!.inline_keyboard.flat()[0]!.text).toBe("✅ Telegram only");

    const unlinked = tapDeps({ link: null });
    await showReminderSettings(unlinked.deps, who, "en");
    expect(unlinked.rec.loginPrompts).toBe(1);
    expect(unlinked.rec.sends).toHaveLength(0);
  });
});
