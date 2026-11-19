import { describe, expect, it, vi } from "vitest";
import { deriveKey, WEBHOOK_SECRET_LABEL } from "./keys";
import type { TgUpdate } from "./telegram";
import { handleWebhook, routeUpdate, WEBHOOK_PATH, type WebhookDeps } from "./webhook";

const TOKEN = "123456:TEST-TOKEN";

function deps(overrides: Partial<WebhookDeps> = {}) {
  const pending: Promise<unknown>[] = [];
  const d = {
    botToken: TOKEN,
    enqueue: vi.fn(async () => "queued" as const),
    leaveChat: vi.fn(async () => true),
    waitUntil: vi.fn((p: Promise<unknown>) => {
      pending.push(p);
    }),
    preCheckout: vi.fn(async () => {}),
    ...overrides,
  };
  return { d, pending };
}

async function request(update: unknown, secret?: string): Promise<Request> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (secret !== undefined) headers["X-Telegram-Bot-Api-Secret-Token"] = secret;
  return new Request(`https://bot.example${WEBHOOK_PATH}`, { method: "POST", headers, body: JSON.stringify(update) });
}

const privateText: TgUpdate = {
  update_id: 100,
  message: { message_id: 1, chat: { id: 42, type: "private" }, from: { id: 42 }, text: "milk" },
};

describe("handleWebhook — secret", () => {
  it("rejects a wrong secret with 401 and does not enqueue", async () => {
    const { d } = deps();
    const res = await handleWebhook(await request(privateText, "nope"), d);
    expect(res.status).toBe(401);
    expect(d.enqueue).not.toHaveBeenCalled();
  });

  it("rejects a missing secret with 401", async () => {
    const { d } = deps();
    const res = await handleWebhook(await request(privateText), d);
    expect(res.status).toBe(401);
    expect(d.enqueue).not.toHaveBeenCalled();
  });

  it("accepts the derived secret and enqueues a private-chat update into that chat", async () => {
    const { d } = deps();
    const secret = await deriveKey(TOKEN, WEBHOOK_SECRET_LABEL);
    const res = await handleWebhook(await request(privateText, secret), d);
    expect(res.status).toBe(200);
    expect(d.enqueue).toHaveBeenCalledWith(42, privateText);
  });

  it("returns 500 (Telegram redelivers) when enqueue fails", async () => {
    const { d } = deps({ enqueue: vi.fn(async () => Promise.reject(new Error("do down"))) });
    const secret = await deriveKey(TOKEN, WEBHOOK_SECRET_LABEL);
    const res = await handleWebhook(await request(privateText, secret), d);
    expect(res.status).toBe(500);
  });
});

describe("handleWebhook — non-private chats", () => {
  it("ignores a group message: 200, nothing enqueued, no leave", async () => {
    const { d } = deps();
    const secret = await deriveKey(TOKEN, WEBHOOK_SECRET_LABEL);
    const group: TgUpdate = {
      update_id: 101,
      message: { message_id: 2, chat: { id: -100, type: "supergroup" }, from: { id: 7 }, text: "hi" },
    };
    const res = await handleWebhook(await request(group, secret), d);
    expect(res.status).toBe(200);
    expect(d.enqueue).not.toHaveBeenCalled();
    expect(d.leaveChat).not.toHaveBeenCalled();
  });

  it("ignores a callback from a group message", async () => {
    const { d } = deps();
    const secret = await deriveKey(TOKEN, WEBHOOK_SECRET_LABEL);
    const cb: TgUpdate = {
      update_id: 102,
      callback_query: {
        id: "q",
        from: { id: 7 },
        data: "n",
        message: { message_id: 3, chat: { id: -100, type: "group" } },
      },
    };
    const res = await handleWebhook(await request(cb, secret), d);
    expect(res.status).toBe(200);
    expect(d.enqueue).not.toHaveBeenCalled();
  });

  it("leaves a group the bot was added to, off the response path", async () => {
    const { d, pending } = deps();
    const secret = await deriveKey(TOKEN, WEBHOOK_SECRET_LABEL);
    const added: TgUpdate = {
      update_id: 103,
      my_chat_member: {
        chat: { id: -200, type: "group" },
        from: { id: 7 },
        old_chat_member: { status: "left" },
        new_chat_member: { status: "member" },
      },
    };
    const res = await handleWebhook(await request(added, secret), d);
    await Promise.all(pending);
    expect(res.status).toBe(200);
    expect(d.leaveChat).toHaveBeenCalledWith(-200);
    expect(d.waitUntil).toHaveBeenCalledTimes(1);
    expect(d.enqueue).not.toHaveBeenCalled();
  });
});

describe("handleWebhook — payments", () => {
  const query = {
    id: "pcq-1",
    from: { id: 42, language_code: "ru" },
    currency: "XTR",
    total_amount: 100,
    invoice_payload: "premium_month_v1",
  };

  it("answers a pre_checkout_query in the webhook path, before returning, never via the queue", async () => {
    const order: string[] = [];
    const { d } = deps({
      preCheckout: vi.fn(async () => {
        order.push("answered");
      }),
    });
    const secret = await deriveKey(TOKEN, WEBHOOK_SECRET_LABEL);
    const res = await handleWebhook(await request({ update_id: 200, pre_checkout_query: query }, secret), d);
    order.push("returned");
    expect(res.status).toBe(200);
    expect(d.preCheckout).toHaveBeenCalledWith(query);
    expect(order).toEqual(["answered", "returned"]);
    expect(d.enqueue).not.toHaveBeenCalled();
  });

  it("still returns 200 when answering the pre-checkout failed (no Telegram redelivery loop)", async () => {
    const { d } = deps({ preCheckout: vi.fn(async () => Promise.reject(new Error("telegram 502"))) });
    const secret = await deriveKey(TOKEN, WEBHOOK_SECRET_LABEL);
    const res = await handleWebhook(await request({ update_id: 201, pre_checkout_query: query }, secret), d);
    expect(res.status).toBe(200);
  });

  it("routes a BotSubscriptionUpdated (Update.subscription) to the user's private-chat queue", async () => {
    const { d } = deps();
    const secret = await deriveKey(TOKEN, WEBHOOK_SECRET_LABEL);
    const upd: TgUpdate = {
      update_id: 202,
      subscription: { user: { id: 777 }, invoice_payload: "premium_month_v1", state: "canceled" },
    };
    const res = await handleWebhook(await request(upd, secret), d);
    expect(res.status).toBe(200);
    expect(d.enqueue).toHaveBeenCalledWith(777, upd);
  });

  it("routes a successful_payment message like any private message", () => {
    expect(
      routeUpdate({
        update_id: 1,
        message: {
          message_id: 9,
          chat: { id: 42, type: "private" },
          from: { id: 42 },
          successful_payment: {
            currency: "XTR",
            total_amount: 100,
            invoice_payload: "premium_month_v1",
            telegram_payment_charge_id: "ch_1",
          },
        },
      }),
    ).toEqual({ kind: "forward", chatId: 42 });
  });
});

describe("routeUpdate", () => {
  it("does not leave when the bot is removed from a group", () => {
    expect(
      routeUpdate({
        update_id: 1,
        my_chat_member: { chat: { id: -1, type: "group" }, from: { id: 1 }, new_chat_member: { status: "left" } },
      }).kind,
    ).toBe("ignore");
  });

  it("ignores private my_chat_member (user blocked/unblocked the bot)", () => {
    expect(
      routeUpdate({
        update_id: 1,
        my_chat_member: { chat: { id: 5, type: "private" }, from: { id: 5 }, new_chat_member: { status: "kicked" } },
      }).kind,
    ).toBe("ignore");
  });

  it("forwards a private callback query to its chat", () => {
    expect(
      routeUpdate({
        update_id: 1,
        callback_query: { id: "x", from: { id: 5 }, message: { message_id: 1, chat: { id: 5, type: "private" } } },
      }),
    ).toEqual({ kind: "forward", chatId: 5 });
  });
});
