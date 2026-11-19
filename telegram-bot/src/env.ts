import type { TelegramChat } from "./chat";

/**
 * Worker bindings. Secrets (never committed — `wrangler secret put`):
 *  - TELEGRAM_BOT_TOKEN       — @gisti_app_bot token from BotFather; the webhook secret and the
 *                               internal CF key are DERIVED from it (keys.ts), no extra secrets.
 *  - FIREBASE_SERVICE_ACCOUNT — the service-account JSON key as one string (same format as the MCP).
 *  - AMPLITUDE_SERVER_API_KEY — optional; unset → tg_* analytics is a no-op (one warning).
 * Vars (wrangler.jsonc): CF_BASE_URL.
 */
export interface Env {
  TELEGRAM_BOT_TOKEN: string;
  FIREBASE_SERVICE_ACCOUNT: string;
  AMPLITUDE_SERVER_API_KEY?: string;
  CF_BASE_URL: string;
  TELEGRAM_CHAT: DurableObjectNamespace<TelegramChat>;
}
