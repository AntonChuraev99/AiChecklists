/**
 * gisti-telegram — Worker entry for the @gisti_app_bot webhook and the reminder cron.
 * Design: docs/archive/telegram-bot-phase1-2026-11-19.md ("Worker"). README.md for deploy + secrets.
 */

import { callInternal } from "./backend";
import type { Env } from "./env";
import { handlePreCheckout } from "./payments";
import { CLAIM_LIMIT, CLAIM_TIMEOUT_MS, runReminderCron } from "./reminders";
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

  /**
   * Phase 4 — every minute (wrangler.jsonc `triggers.crons`): claim the due reminders and hand each
   * chat's batch to that chat's Durable Object. `runReminderCron` never rejects; the claim happens on
   * the server BEFORE anything is sent, so nothing here is ever retried (reminders.ts "AT MOST ONCE").
   */
  async scheduled(_controller: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(
      runReminderCron({
        claim: () => callInternal(env, "claim_due_reminders", { limit: CLAIM_LIMIT }, { timeoutMs: CLAIM_TIMEOUT_MS }),
        deliver: (chatId, batch) => env.TELEGRAM_CHAT.get(env.TELEGRAM_CHAT.idFromName(String(chatId))).deliverReminders(batch),
      }),
    );
  },
} satisfies ExportedHandler<Env>;
