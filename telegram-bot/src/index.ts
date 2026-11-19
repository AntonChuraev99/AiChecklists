/**
 * gisti-telegram — Worker entry for the @gisti_app_bot webhook.
 * Design: docs/active/telegram-bot-phase1-2026-11-19.md ("Worker"). README.md for deploy + secrets.
 */

import type { Env } from "./env";
import { handlePreCheckout } from "./payments";
import { leaveChat } from "./telegram";
import { handleWebhook, WEBHOOK_PATH } from "./webhook";

export { TelegramChat } from "./chat";

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === WEBHOOK_PATH) {
      return handleWebhook(request, {
        botToken: env.TELEGRAM_BOT_TOKEN,
        enqueue: (chatId, update) => env.TELEGRAM_CHAT.get(env.TELEGRAM_CHAT.idFromName(String(chatId))).enqueue(update),
        leaveChat: (chatId) => leaveChat(env, chatId),
        waitUntil: (p) => ctx.waitUntil(p),
        preCheckout: (query) => handlePreCheckout(env, query),
      });
    }
    if (url.pathname === "/" && request.method === "GET") return new Response("gisti-telegram: ok");
    return new Response("Not found", { status: 404 });
  },
} satisfies ExportedHandler<Env>;
