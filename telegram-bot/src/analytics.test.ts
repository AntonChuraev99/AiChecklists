import { afterEach, describe, expect, it, vi } from "vitest";
import { buildAmplitudeEvent, sendTgEvent, sendTgEvents, tgDeviceId, tgIdentity, type TgEvent } from "./analytics";

const CREDIT_ID = "3f2b8c1e-1d2a-4c3b-9e8f-0a1b2c3d4e5f";

/** One of every variant — a new event type fails to compile here until it is added. */
const ALL: Record<TgEvent["type"], TgEvent> = {
  tg_link_completed: { type: "tg_link_completed" },
  tg_checklist_created: { type: "tg_checklist_created", mode: "ai", input: "voice" },
  tg_lists_opened: { type: "tg_lists_opened" },
  tg_item_toggled: { type: "tg_item_toggled" },
  tg_ai_failed: { type: "tg_ai_failed", reason: "no_credits" },
  tg_premium_opened: { type: "tg_premium_opened" },
  tg_invoice_created: { type: "tg_invoice_created" },
  tg_payment_succeeded: { type: "tg_payment_succeeded", amount: 100, isRecurring: true },
  tg_subscription_updated: { type: "tg_subscription_updated", state: "canceled" },
  tg_app_link_opened: { type: "tg_app_link_opened" },
  tg_chat_opened: { type: "tg_chat_opened", source: "button" },
  tg_chat_turn: { type: "tg_chat_turn", result: "final", rounds: 2 },
  tg_chat_tool: { type: "tg_chat_tool", tool: "delete_item", status: "declined" },
  tg_reminder_sent: { type: "tg_reminder_sent", kind: "repeat" },
  tg_reminder_action: { type: "tg_reminder_action", action: "snooze" },
  tg_reminder_channel_set: { type: "tg_reminder_channel_set", channel: "both", source: "offer" },
};

const ALLOWED_PROPS: Record<TgEvent["type"], string[]> = {
  tg_link_completed: [],
  tg_checklist_created: ["input", "mode"],
  tg_lists_opened: [],
  tg_item_toggled: [],
  tg_ai_failed: ["reason"],
  tg_premium_opened: [],
  tg_invoice_created: [],
  tg_payment_succeeded: ["amount", "is_recurring"],
  tg_subscription_updated: ["state"],
  tg_app_link_opened: [],
  tg_chat_opened: ["source"],
  tg_chat_turn: ["result", "rounds"],
  tg_chat_tool: ["status", "tool"],
  tg_reminder_sent: ["kind"],
  tg_reminder_action: ["action"],
  tg_reminder_channel_set: ["channel", "source"],
};

afterEach(() => vi.unstubAllGlobals());

describe("buildAmplitudeEvent", () => {
  it("sends the credit doc id as user_id, the pseudonymous id as device_id, and only allowlisted keys", async () => {
    const identity = await tgIdentity(123456789, CREDIT_ID);
    for (const event of Object.values(ALL)) {
      const e = buildAmplitudeEvent(identity, event, 1_700_000_000_000);
      expect(Object.keys(e).sort()).toEqual(["device_id", "event_properties", "event_type", "platform", "time", "user_id"]);
      expect(e.user_id).toBe(CREDIT_ID);
      expect(e.device_id).toBe(identity.deviceId);
      expect(e.event_type).toBe(event.type);
      expect(e.platform).toBe("Telegram");
      expect(Object.keys(e.event_properties).sort()).toEqual(ALLOWED_PROPS[event.type]);
    }
  });

  it("carries mode/input and reason verbatim", async () => {
    const identity = await tgIdentity(1, CREDIT_ID);
    expect(buildAmplitudeEvent(identity, ALL.tg_checklist_created, 0).event_properties).toEqual({ mode: "ai", input: "voice" });
    expect(buildAmplitudeEvent(identity, ALL.tg_ai_failed, 0).event_properties).toEqual({ reason: "no_credits" });
    expect(buildAmplitudeEvent(identity, ALL.tg_payment_succeeded, 0).event_properties).toEqual({
      amount: 100,
      is_recurring: true,
    });
    expect(buildAmplitudeEvent(identity, ALL.tg_subscription_updated, 0).event_properties).toEqual({ state: "canceled" });
  });

  it("omits user_id when the Telegram user is not linked (or the id is too short for Amplitude)", async () => {
    const unlinked = buildAmplitudeEvent(await tgIdentity(1, null), ALL.tg_ai_failed, 0);
    expect("user_id" in unlinked).toBe(false);
    const short = buildAmplitudeEvent({ userId: "abc", deviceId: "tg_x" }, ALL.tg_lists_opened, 0);
    expect("user_id" in short).toBe(false);
  });
});

describe("chat events", () => {
  it("carry only fixed vocabularies — never message text, names or errors", async () => {
    const identity = await tgIdentity(1, CREDIT_ID);
    expect(buildAmplitudeEvent(identity, ALL.tg_chat_opened, 0).event_properties).toEqual({ source: "button" });
    expect(buildAmplitudeEvent(identity, ALL.tg_chat_turn, 0).event_properties).toEqual({ result: "final", rounds: 2 });
    expect(buildAmplitudeEvent(identity, ALL.tg_chat_tool, 0).event_properties).toEqual({ tool: "delete_item", status: "declined" });
  });

  it("sendTgEvents posts a batch in one request", async () => {
    const fetchMock = vi.fn(async () => new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await sendTgEvents({ AMPLITUDE_SERVER_API_KEY: "k" }, await tgIdentity(1, CREDIT_ID), [ALL.tg_chat_tool, ALL.tg_chat_turn]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect((JSON.parse(String(init.body)) as { events: unknown[] }).events).toHaveLength(2);
  });
});

describe("reminder events", () => {
  it("carry only fixed vocabularies — no item text, checklist name or id", async () => {
    const identity = await tgIdentity(1, CREDIT_ID);
    expect(buildAmplitudeEvent(identity, ALL.tg_reminder_sent, 0).event_properties).toEqual({ kind: "repeat" });
    expect(buildAmplitudeEvent(identity, ALL.tg_reminder_action, 0).event_properties).toEqual({ action: "snooze" });
    expect(buildAmplitudeEvent(identity, ALL.tg_reminder_channel_set, 0).event_properties).toEqual({
      channel: "both",
      source: "offer",
    });
  });
});

describe("tgDeviceId", () => {
  it("is stable, prefixed, 16 hex chars and does not contain the raw Telegram id", async () => {
    const a = await tgDeviceId(123456789);
    expect(a).toMatch(/^tg_[0-9a-f]{16}$/);
    expect(await tgDeviceId(123456789)).toBe(a);
    expect(await tgDeviceId(987654321)).not.toBe(a);
    expect(a).not.toContain("123456789");
  });
});

describe("sendTgEvent", () => {
  it("is a no-op without an API key", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    await sendTgEvent({}, await tgIdentity(1, CREDIT_ID), ALL.tg_lists_opened);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("posts one event to the HTTP V2 endpoint and never throws on failure", async () => {
    const fetchMock = vi.fn(async () => new Response("bad", { status: 400 }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(
      sendTgEvent({ AMPLITUDE_SERVER_API_KEY: "k" }, await tgIdentity(1, CREDIT_ID), ALL.tg_item_toggled),
    ).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("https://api2.amplitude.com/2/httpapi");
    const body = JSON.parse(String(init.body)) as { api_key: string; events: unknown[] };
    expect(body.api_key).toBe("k");
    expect(body.events).toHaveLength(1);

    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new Error("network down"))));
    await expect(
      sendTgEvent({ AMPLITUDE_SERVER_API_KEY: "k" }, await tgIdentity(1, CREDIT_ID), ALL.tg_item_toggled),
    ).resolves.toBeUndefined();
  });
});
