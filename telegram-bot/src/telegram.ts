/**
 * Minimal Telegram Bot API surface: the update shapes this bot reads and a tiny JSON client.
 *
 * Only the fields the bot actually uses are typed. The token never appears in an error message or a
 * log line — errors carry the method name and Telegram's `description` only.
 */

// ── Update shapes (subset) ──────────────────────────────────────────────────────

export interface TgUser {
  id: number;
  is_bot?: boolean;
  first_name?: string;
  username?: string;
  language_code?: string;
}

export interface TgChat {
  id: number;
  type: "private" | "group" | "supergroup" | "channel" | string;
}

export interface TgEntity {
  type: string;
  offset: number;
  length: number;
  url?: string;
}

export interface TgPhotoSize {
  file_id: string;
  file_unique_id?: string;
  width?: number;
  height?: number;
  file_size?: number;
}

export interface TgFileRef {
  file_id: string;
  file_unique_id?: string;
  mime_type?: string;
  file_size?: number;
  file_name?: string;
}

export interface TgMessage {
  message_id: number;
  from?: TgUser;
  chat: TgChat;
  date?: number;
  text?: string;
  caption?: string;
  entities?: TgEntity[];
  caption_entities?: TgEntity[];
  media_group_id?: string;
  photo?: TgPhotoSize[];
  voice?: TgFileRef;
  audio?: TgFileRef;
  document?: TgFileRef;
  video?: TgFileRef;
  video_note?: TgFileRef;
  animation?: TgFileRef;
  sticker?: TgFileRef;
  successful_payment?: TgSuccessfulPayment;
  refunded_payment?: { currency: string; total_amount: number; invoice_payload: string; telegram_payment_charge_id: string };
}

/** Bot API `SuccessfulPayment` (service message in the buyer's private chat). */
export interface TgSuccessfulPayment {
  currency: string;
  total_amount: number;
  invoice_payload: string;
  /** Unix seconds; recurring (subscription) payments only. */
  subscription_expiration_date?: number;
  is_recurring?: boolean;
  is_first_recurring?: boolean;
  telegram_payment_charge_id: string;
  provider_payment_charge_id?: string;
}

/** Bot API `PreCheckoutQuery` — must be answered within 10 s. */
export interface TgPreCheckoutQuery {
  id: string;
  from: TgUser;
  currency: string;
  total_amount: number;
  invoice_payload: string;
}

/**
 * Bot API 10.2 `BotSubscriptionUpdated` (`Update.subscription`): a user's payment subscription
 * toward the bot changed. `state`: "canceled" | "active" (re-enabled) | "failed" (renewal failed).
 * Source: https://core.telegram.org/bots/api#botsubscriptionupdated
 */
export interface TgBotSubscriptionUpdated {
  user: TgUser;
  invoice_payload: string;
  state: string;
}

export interface TgCallbackQuery {
  id: string;
  from: TgUser;
  message?: TgMessage;
  data?: string;
}

export interface TgChatMember {
  status: string;
  user?: TgUser;
}

export interface TgChatMemberUpdated {
  chat: TgChat;
  from: TgUser;
  old_chat_member?: TgChatMember;
  new_chat_member?: TgChatMember;
}

export interface TgUpdate {
  update_id: number;
  message?: TgMessage;
  callback_query?: TgCallbackQuery;
  my_chat_member?: TgChatMemberUpdated;
  pre_checkout_query?: TgPreCheckoutQuery;
  subscription?: TgBotSubscriptionUpdated;
}

// ── Keyboard ────────────────────────────────────────────────────────────────────

export type InlineButton = { text: string; callback_data: string } | { text: string; url: string };

export interface InlineKeyboard {
  inline_keyboard: InlineButton[][];
}

// ── Client ──────────────────────────────────────────────────────────────────────

/**
 * Largest file the bot accepts. Telegram's getFile allows 20 MB, but the file then travels to
 * Gemini inline as base64 (×4/3) inside a request capped at ~20 MB total — so 14 MB raw is the
 * ceiling that still fits with the prompt.
 */
export const MAX_FILE_BYTES = 14 * 1024 * 1024;
export const MAX_FILE_MB = MAX_FILE_BYTES / (1024 * 1024);

/** A Bot API call that returned `ok: false` (or a non-JSON / network failure, code 0). */
export class TelegramError extends Error {
  constructor(
    readonly method: string,
    readonly code: number,
    readonly description: string,
  ) {
    super(`telegram ${method} failed (${code}): ${description}`);
    this.name = "TelegramError";
  }
}

export interface TelegramEnv {
  TELEGRAM_BOT_TOKEN: string;
}

/** POST a Bot API method with a JSON body and return `result`. */
export async function tgCall<T = unknown>(
  env: TelegramEnv,
  method: string,
  params: Record<string, unknown>,
): Promise<T> {
  let resp: Response;
  try {
    resp = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/${method}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(params),
    });
  } catch (e) {
    throw new TelegramError(method, 0, `network error: ${e instanceof Error ? e.message : "unknown"}`);
  }
  let data: { ok?: boolean; result?: T; description?: string; error_code?: number } = {};
  try {
    data = (await resp.json()) as typeof data;
  } catch {
    // non-JSON body — fall through to the status-based error
  }
  if (!resp.ok || data.ok !== true) {
    throw new TelegramError(method, data.error_code ?? resp.status, data.description ?? resp.statusText);
  }
  return data.result as T;
}

export function sendMessage(
  env: TelegramEnv,
  chatId: number,
  text: string,
  replyMarkup?: InlineKeyboard,
): Promise<TgMessage> {
  return tgCall<TgMessage>(env, "sendMessage", {
    chat_id: chatId,
    text,
    link_preview_options: { is_disabled: true },
    ...(replyMarkup ? { reply_markup: replyMarkup } : {}),
  });
}

/** True when Telegram refused an edit only because nothing changed — not an error for us. */
export function isNotModified(e: unknown): boolean {
  return e instanceof TelegramError && e.code === 400 && /not modified/i.test(e.description);
}

/**
 * Edit a bot message in place. "message is not modified" is swallowed (a double tap re-renders the
 * same view). A message that can no longer be edited (deleted, too old) falls back to a fresh
 * message, so the user always sees the result.
 */
export async function editOrSend(
  env: TelegramEnv,
  chatId: number,
  messageId: number | undefined,
  text: string,
  replyMarkup?: InlineKeyboard,
): Promise<void> {
  if (messageId !== undefined) {
    try {
      await tgCall(env, "editMessageText", {
        chat_id: chatId,
        message_id: messageId,
        text,
        link_preview_options: { is_disabled: true },
        reply_markup: replyMarkup ?? { inline_keyboard: [] },
      });
      return;
    } catch (e) {
      if (isNotModified(e)) return;
      if (!(e instanceof TelegramError) || e.code !== 400) throw e;
      console.warn(`[gisti-tg] edit failed, sending a new message instead: ${e.description}`);
    }
  }
  await sendMessage(env, chatId, text, replyMarkup);
}

export async function answerCallback(env: TelegramEnv, callbackQueryId: string, text?: string): Promise<void> {
  try {
    await tgCall(env, "answerCallbackQuery", {
      callback_query_id: callbackQueryId,
      ...(text ? { text } : {}),
    });
  } catch (e) {
    // A stale query ("query is too old") must not abort the action the user asked for.
    console.warn(`[gisti-tg] answerCallbackQuery failed: ${e instanceof Error ? e.message : e}`);
  }
}

export function leaveChat(env: TelegramEnv, chatId: number): Promise<unknown> {
  return tgCall(env, "leaveChat", { chat_id: chatId });
}

/** The file is above MAX_FILE_BYTES (or getFile said it is too big). */
export class FileTooLargeError extends Error {
  constructor() {
    super("file exceeds the size limit");
    this.name = "FileTooLargeError";
  }
}

/** getFile → download the bytes. Throws FileTooLargeError above MAX_FILE_BYTES. */
export async function downloadFile(env: TelegramEnv, fileId: string): Promise<Uint8Array> {
  let file: { file_path?: string; file_size?: number };
  try {
    file = await tgCall<{ file_path?: string; file_size?: number }>(env, "getFile", { file_id: fileId });
  } catch (e) {
    if (e instanceof TelegramError && /too big/i.test(e.description)) throw new FileTooLargeError();
    throw e;
  }
  if ((file.file_size ?? 0) > MAX_FILE_BYTES) throw new FileTooLargeError();
  if (!file.file_path) throw new TelegramError("getFile", 0, "no file_path in result");

  let resp: Response;
  try {
    resp = await fetch(`https://api.telegram.org/file/bot${env.TELEGRAM_BOT_TOKEN}/${file.file_path}`);
  } catch (e) {
    throw new TelegramError("downloadFile", 0, `network error: ${e instanceof Error ? e.message : "unknown"}`);
  }
  if (!resp.ok) throw new TelegramError("downloadFile", resp.status, resp.statusText);
  const bytes = new Uint8Array(await resp.arrayBuffer());
  if (bytes.length > MAX_FILE_BYTES) throw new FileTooLargeError();
  return bytes;
}

/** Standard base64 of a byte array, encoded in chunks so no 20 MB intermediate string is built. */
export function base64FromBytes(bytes: Uint8Array): string {
  const CHUNK = 0x8000 * 3; // multiple of 3 → chunks concatenate without inner padding
  const parts: string[] = [];
  for (let i = 0; i < bytes.length; i += CHUNK) {
    const slice = bytes.subarray(i, i + CHUNK);
    let bin = "";
    for (let j = 0; j < slice.length; j++) bin += String.fromCharCode(slice[j]!);
    parts.push(btoa(bin));
  }
  return parts.join("");
}
