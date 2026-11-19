/**
 * `POST /telegram/webhook` — the fast path. Verify the secret header, route the update, answer 200.
 *
 *  - Wrong / missing `X-Telegram-Bot-Api-Secret-Token` → 401 (constant-time compare against the
 *    key derived from the bot token).
 *  - Non-private chats are ignored; when the bot is ADDED to a group/channel it leaves (`leaveChat`,
 *    off the response path). BotFather /setjoingroups=Disable is the first line, this is the second.
 *  - Private chats → the chat's Durable Object queues the update (dedupe by update_id) → 200 at once.
 *    All real work happens later in the DO alarm, so a slow AI call never makes Telegram retry.
 *  - `pre_checkout_query` is answered HERE, before the 200, never through the queue: Telegram fails
 *    the payment unless the answer arrives within 10 s, and a queue can be busy with an AI call.
 *  - `subscription` (Bot API 10.2 BotSubscriptionUpdated) has no chat; it goes to the user's private
 *    chat DO (private chat id == user id).
 *
 * Dependencies are injected so this is testable without workerd.
 */

import { derivedKey, timingSafeEqual, WEBHOOK_SECRET_LABEL } from "./keys";
import type { TgPreCheckoutQuery, TgUpdate } from "./telegram";

export const WEBHOOK_PATH = "/telegram/webhook";
const SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

export interface WebhookDeps {
  botToken: string | undefined;
  /** Queue the update in the private chat's Durable Object. */
  enqueue(chatId: number, update: TgUpdate): Promise<"queued" | "duplicate">;
  leaveChat(chatId: number): Promise<unknown>;
  waitUntil(p: Promise<unknown>): void;
  /** Decide and answer a pre-checkout query (awaited before the webhook returns). */
  preCheckout(query: TgPreCheckoutQuery): Promise<void>;
}

export type Route =
  | { kind: "forward"; chatId: number }
  | { kind: "leave"; chatId: number }
  | { kind: "precheckout"; query: TgPreCheckoutQuery }
  | { kind: "ignore"; reason: string };

const JOINED_STATUSES = new Set(["member", "administrator", "creator", "restricted"]);

/** Where an update goes. Pure. */
export function routeUpdate(update: TgUpdate): Route {
  if (typeof update.update_id !== "number") return { kind: "ignore", reason: "no update_id" };

  if (update.pre_checkout_query) return { kind: "precheckout", query: update.pre_checkout_query };
  if (update.subscription) {
    const userId = update.subscription.user?.id;
    return typeof userId === "number"
      ? { kind: "forward", chatId: userId }
      : { kind: "ignore", reason: "subscription without user" };
  }

  if (update.my_chat_member) {
    const m = update.my_chat_member;
    if (m.chat.type !== "private" && JOINED_STATUSES.has(m.new_chat_member?.status ?? "")) {
      return { kind: "leave", chatId: m.chat.id };
    }
    return { kind: "ignore", reason: "my_chat_member" };
  }

  const chat = update.message?.chat ?? update.callback_query?.message?.chat;
  if (!chat) return { kind: "ignore", reason: "no chat" };
  if (chat.type !== "private") return { kind: "ignore", reason: `chat type ${chat.type}` };
  return { kind: "forward", chatId: chat.id };
}

function ok(): Response {
  return new Response("ok", { status: 200 });
}

export async function handleWebhook(request: Request, deps: WebhookDeps): Promise<Response> {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  if (!deps.botToken) {
    console.error("[gisti-tg] TELEGRAM_BOT_TOKEN is not set — rejecting webhook");
    return new Response("Not configured", { status: 500 });
  }

  const expected = await derivedKey(deps.botToken, WEBHOOK_SECRET_LABEL);
  const got = request.headers.get(SECRET_HEADER) ?? "";
  if (!timingSafeEqual(got, expected)) return new Response("Unauthorized", { status: 401 });

  let update: TgUpdate;
  try {
    update = (await request.json()) as TgUpdate;
  } catch {
    console.warn("[gisti-tg] webhook body is not JSON — dropped");
    return ok(); // a retry would carry the same body; nothing to gain from a non-2xx
  }

  const route = routeUpdate(update);
  if (route.kind === "precheckout") {
    try {
      await deps.preCheckout(route.query);
    } catch (e) {
      // The handler answers ok:false itself on any decision failure; reaching here means the
      // answerPreCheckoutQuery call failed — Telegram will time the payment out. Nothing to retry.
      console.error(`[gisti-tg] pre-checkout ${route.query.id} not answered: ${e instanceof Error ? e.message : e}`);
    }
    return ok();
  }
  if (route.kind === "leave") {
    console.log(`[gisti-tg] added to non-private chat ${route.chatId} — leaving`);
    deps.waitUntil(
      deps.leaveChat(route.chatId).catch((e) =>
        console.error(`[gisti-tg] leaveChat ${route.chatId} failed: ${e instanceof Error ? e.message : e}`),
      ),
    );
    return ok();
  }
  if (route.kind === "ignore") return ok();

  try {
    await deps.enqueue(route.chatId, update);
  } catch (e) {
    // Not persisted → let Telegram redeliver (dedupe makes a redelivery safe).
    console.error(`[gisti-tg] enqueue failed for update ${update.update_id}: ${e instanceof Error ? e.message : e}`);
    return new Response("Retry later", { status: 500 });
  }
  return ok();
}
