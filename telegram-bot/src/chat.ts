/**
 * `TelegramChat` — one Durable Object per private chat (`idFromName(chat_id)`).
 *
 * `enqueue()` (RPC from the webhook) only touches storage and returns at once. Everything that
 * talks to Firestore, the Cloud Functions or the Bot API runs in `alarm()`:
 *   1. drain the update queue in order (queue.ts: dedupe, bounded attempts, backoff);
 *   2. if the collecting draft's debounce deadline passed, send its prompt;
 *   3. retry the payments parked in the ledger that are due (ledger.ts: long backoff, 30 days);
 *   4. re-arm the alarm for the earliest of: next queued retry, draft deadline, parked payment.
 * A DO runs one alarm at a time, so updates of a chat are processed strictly sequentially.
 *
 * Identity (contract "Firestore schema"): reads fan out over [google_uid, credit_user_id]; creates
 * write to `users/{google_uid}`; mutations write back to the doc the checklist was found in; AI
 * calls charge `credit_user_id`. Same rules as the MCP tools, same data-layer calls.
 *
 * Phase 2 — AI chat (agent.ts, agentTools.ts). Chat mode opens from «💬 Ask AI» under a text draft
 * or `/chat`; while it is open, plain TEXT goes straight to `chat_agent` (photos, voice, files and
 * links keep the draft flow, commands always work); it closes on «End chat» or lazily after 30 min
 * without a user message. One turn = one `request_id` = `tg-chat-<chat_id>-<turn_seq>`. State is one
 * storage key ("chat"): the transcript, the active turn and, persisted per call BEFORE any write,
 * the plan of every tool call — so a queue retry of the update that drives a turn resumes it where
 * it stopped (`turn.updateId`) and never runs a write twice; the closed turn's reply waits in an
 * outbox, so a failed send is re-sent instead of re-asking (and re-charging) the agent.
 */

import { DurableObject } from "cloudflare:workers";
import { CfError } from "../../mcp-server/src/cf";
import { newCloudId } from "../../mcp-server/src/encode";
import { findChecklistWithOwner, writeChecklist, type OwnedChecklist } from "../../mcp-server/src/firestore";
import { leafItemCount } from "../../mcp-server/src/model";
import { buildAiChecklist, createEmptyChecklist, toggleItem } from "../../mcp-server/src/mutate";
import {
  appendRound,
  beginTurn,
  buildAgentRequest,
  buildChecklistsSummary,
  callSeed,
  closeChat,
  closeTurn,
  confirmKeyboard,
  emptyChatState,
  endChatKeyboard,
  finishTurn,
  isChatOpen,
  noteUserActivity,
  plainText,
  planBatch,
  pruneTranscript,
  renderConfirmCard,
  renderOptions,
  renderReply,
  toolCallsAction,
  toolEvents,
  tooLongForChat,
  type ChatState,
  type ClosedTurn,
  type OutMsg,
  type TurnEnd,
} from "./agent";
import { applyOps, declinedResults, planTool, type AgentToolResult } from "./agentTools";
import {
  sendTgEvent,
  sendTgEvents,
  tgIdentity,
  type AiFailReason,
  type ChatTurnResult,
  type TgAnalyticsIdentity,
  type TgEvent,
} from "./analytics";
import {
  callChatAgent,
  callInternal,
  CHAT_AGENT_COST,
  collectChecklists,
  collectOwnedChecklists,
  generateChecklistViaCf,
  getAiConfig,
  getCreditInfo,
  getLink,
  getUserTimezone,
  identityBody,
  InternalApiError,
  linkIds,
  setUserTimezone,
  type TelegramIdentity,
  type TelegramLink,
} from "./backend";
import {
  decodeCallback,
  emptyRegistry,
  resolveKey,
  type Callback,
  type DraftAction,
  type KeyRegistryState,
} from "./callback";
import {
  addParts,
  aiInputLength,
  aiPlan,
  aiRequestId,
  ALBUM_MERGE_MS,
  classifyMessage,
  draftText,
  freeOption,
  isTextOnly,
  newDraft,
  nextDraftId,
  type Draft,
  type DraftPart,
  type RejectReason,
} from "./draft";
import { resolveCommand } from "./commands";
import type { Env } from "./env";
import { pickLang, t, type Lang, type Strings } from "./i18n";
import { MAX_ITEMS } from "./lines";
import { SUPPORT_EMAIL, TERMS_URL } from "./links";
import { applyOrPark, PaymentLedger, paymentToApply, type ParkedPayment, type PaymentToApply } from "./ledger";
import {
  invoiceParams,
  isSubscriptionState,
  parsePremiumStatus,
  paymentReplyText,
  type PaymentOutcome,
} from "./payments";
import { backoffMs, errText, isRetryable, MAX_ATTEMPTS, UpdateQueue, type KvStore } from "./queue";
import {
  answerCallback,
  base64FromBytes,
  downloadFile,
  editOrSend,
  FileTooLargeError,
  isNotModified,
  MAX_FILE_MB,
  sendMessage,
  sendTyping,
  TelegramError,
  tgCall,
  type InlineKeyboard,
  type TgBotSubscriptionUpdated,
  type TgCallbackQuery,
  type TgMessage,
  type TgSuccessfulPayment,
  type TgUpdate,
  type TgUser,
} from "./telegram";
import {
  premiumScreen,
  renderAppView,
  renderDraftPrompt,
  renderItemsPage,
  renderListsPage,
  renderPremiumView,
} from "./views";
import { formatHm, offsetLabel, offsetMinutesAt, renderTimezonePicker, TZ_CHOICES, zoneForOffset } from "./timezone";

const DRAFTS_KEY = "drafts";
const DRAFT_SEQ_KEY = "draftSeq";
const KEYS_KEY = "keys";
/** The AI chat state (agent.ts ChatState) — one key. */
const CHAT_KEY = "chat";
/** Drafts older than this are forgotten (their buttons then answer "expired"). */
const DRAFT_TTL_MS = 24 * 60 * 60 * 1000;
const MAX_STORED_DRAFTS = 10;
/** Shape of the one-time link token (contract: 32 random bytes, base64url, 43 chars). */
const TOKEN_RE = /^[A-Za-z0-9_-]{16,64}$/;

type Drafts = Record<string, Draft>;
type Answer = (text?: string) => Promise<void>;

/** An input the CF would reject for length — refused before spending a call. */
class TooLongError extends Error {
  constructor() {
    super("input exceeds ai_analysis_max_input_length");
    this.name = "TooLongError";
  }
}

function rejectText(s: Strings, reason: RejectReason): string {
  switch (reason) {
    case "too_big":
      return s.fileTooBig(MAX_FILE_MB);
    case "pdf":
      return s.pdfUnsupported;
    case "image_as_file":
      return s.imageAsPhoto;
    case "unsupported":
      return s.unsupported;
  }
}

/** User-facing text for an AI failure we answer in place (buttons stay); null → let the queue retry. */
function aiFailureNotice(e: unknown, s: Strings): string | null {
  if (e instanceof TooLongError) return s.tooLongForAi;
  if (e instanceof FileTooLargeError) return s.fileTooBig(MAX_FILE_MB);
  if (e instanceof CfError) {
    if (e.status === 402) return s.outOfCredits;
    if (e.status === 429) return s.dailyLimit;
    if (e.status === 503) return s.aiDisabled;
    if (e.status === 400 && /maximum length/i.test(e.serverMessage)) return s.tooLongForAi;
    return s.aiFailed; // the CF refunded the reservation; the same request_id is safe to re-tap
  }
  return null;
}

/** The `tg_ai_failed.reason` for a failure answered by aiFailureNotice. */
function aiFailReason(e: unknown): AiFailReason {
  if (e instanceof CfError) {
    if (e.status === 402) return "no_credits";
    if (e.status === 400 && /maximum length/i.test(e.serverMessage)) return "unsupported";
    return "cf_error";
  }
  return "unsupported"; // too long / too big — the input itself cannot go to the AI
}

function identityOf(user: TgUser, chatId: number): TelegramIdentity {
  return {
    telegramUserId: user.id,
    chatId,
    username: user.username ?? null,
    firstName: user.first_name ?? null,
    languageCode: user.language_code ?? null,
  };
}

function draftIdentity(d: Draft): TelegramIdentity {
  return {
    telegramUserId: d.user.id,
    chatId: d.chatId,
    username: d.user.username,
    firstName: d.user.firstName,
    languageCode: d.user.languageCode,
  };
}

/** A keyboard with no rows is sent as no keyboard at all. */
function keyboardOrNone(k: InlineKeyboard): InlineKeyboard | undefined {
  return k.inline_keyboard.length > 0 ? k : undefined;
}

/** The `tg_chat_turn.result` of a turn end. */
function turnResult(end: TurnEnd): ChatTurnResult {
  return end === "final" || end === "options" || end === "no_credits" ? end : "error";
}

function collectingDraft(drafts: Drafts): Draft | undefined {
  return Object.values(drafts).find((d) => d.status === "collecting");
}

export class TelegramChat extends DurableObject<Env> {
  private readonly store: KvStore;
  private readonly queue: UpdateQueue;
  private readonly ledger: PaymentLedger;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    const storage = ctx.storage;
    this.store = {
      get: <T>(key: string) => storage.get<T>(key),
      put: <T>(key: string, value: T) => storage.put<T>(key, value),
      delete: (key: string) => storage.delete(key),
      list: <T>(options: { prefix: string; limit?: number }) => storage.list<T>(options),
    };
    this.queue = new UpdateQueue(this.store);
    this.ledger = new PaymentLedger(this.store);
  }

  // ── RPC + alarm plumbing ──────────────────────────────────────────────────────

  /** Called by the webhook. Storage only — the webhook can answer Telegram within milliseconds. */
  async enqueue(update: TgUpdate): Promise<"queued" | "duplicate"> {
    const result = await this.queue.enqueue(update, Date.now());
    if (result === "queued") await this.scheduleAt(Date.now());
    return result;
  }

  async alarm(): Promise<void> {
    try {
      await this.queue.drain(
        () => Date.now(),
        (update, attempt) => this.process(update, attempt),
        (update, error, attempts) => this.onFinalFailure(update, error, attempts),
      );
      await this.flushDueDraft();
    } catch (e) {
      console.error(`[gisti-tg] alarm processing failed: ${errText(e)}`);
    }
    // Separate try: a broken queue must not starve parked payments, and vice versa.
    try {
      await this.ledger.retryDue(
        () => Date.now(),
        (body) => callInternal(this.env, "apply_payment", body),
        (p, outcome) => this.notifyParkedPayment(p, outcome),
      );
    } catch (e) {
      console.error(`[gisti-tg] payment ledger retry failed: ${errText(e)}`);
    }
    // A throw here (storage down) makes the platform retry the alarm — the queue never stalls silently.
    await this.scheduleNext();
  }

  /** Arm the alarm at `at` unless an earlier one is already set. */
  private async scheduleAt(at: number): Promise<void> {
    const current = await this.ctx.storage.getAlarm();
    if (current === null || current > at) await this.ctx.storage.setAlarm(at);
  }

  private async scheduleNext(): Promise<void> {
    const candidates: number[] = [];
    const head = await this.queue.head();
    if (head) candidates.push(Math.max(head[1].notBefore, Date.now()));
    const collecting = collectingDraft(await this.loadDrafts());
    if (collecting) candidates.push(collecting.dueAt);
    const payment = await this.ledger.nextDue();
    if (payment !== null) candidates.push(Math.max(payment, Date.now()));
    if (candidates.length > 0) await this.scheduleAt(Math.min(...candidates));
  }

  // ── storage helpers ───────────────────────────────────────────────────────────

  private async loadDrafts(): Promise<Drafts> {
    return (await this.store.get<Drafts>(DRAFTS_KEY)) ?? {};
  }

  private async saveDrafts(drafts: Drafts): Promise<void> {
    const now = Date.now();
    const keep = Object.values(drafts)
      .filter((d) => d.status === "collecting" || now - d.updatedAt < DRAFT_TTL_MS)
      .sort((a, b) => b.createdAt - a.createdAt)
      .slice(0, MAX_STORED_DRAFTS);
    const next: Drafts = {};
    for (const d of keep) next[d.id] = d;
    await this.store.put(DRAFTS_KEY, next);
  }

  private async loadKeys(): Promise<KeyRegistryState> {
    return (await this.store.get<KeyRegistryState>(KEYS_KEY)) ?? emptyRegistry();
  }

  private async saveKeys(keys: KeyRegistryState): Promise<void> {
    await this.store.put(KEYS_KEY, keys);
  }

  private async loadChat(): Promise<ChatState> {
    return (await this.store.get<ChatState>(CHAT_KEY)) ?? emptyChatState();
  }

  /** Persist the chat state; the stored transcript is capped by whole turns (the current one stays). */
  private async saveChat(state: ChatState): Promise<void> {
    state.transcript = pruneTranscript(state.transcript);
    await this.store.put(CHAT_KEY, state);
  }

  /**
   * One Amplitude event, AWAITED inside the alarm (a floating send can be lost — MCP precedent).
   * Never throws; called after the user-visible reply so it never delays it.
   */
  private async track(telegramUserId: number, creditUserId: string | null, event: TgEvent): Promise<void> {
    await sendTgEvent(this.env, await tgIdentity(telegramUserId, creditUserId), event);
  }

  // ── dispatch ──────────────────────────────────────────────────────────────────

  private async process(update: TgUpdate, attempt: number): Promise<void> {
    // A retried update that drives a chat turn resumes that turn instead of starting a new one.
    if (attempt > 1 && (await this.resumeChat(update))) return;
    if (update.callback_query) return this.onCallback(update.callback_query, update.update_id);
    if (update.message) return this.onMessage(update.message, attempt, update.update_id);
    if (update.subscription) return this.onSubscriptionUpdated(update.subscription);
  }

  /** The last word after the queue gave up on an update: log it and tell the user — never silent. */
  private async onFinalFailure(update: TgUpdate, error: unknown, attempts: number): Promise<void> {
    console.error(`[gisti-tg] update ${update.update_id} dropped after ${attempts} attempt(s): ${errText(error)}`);
    if (error instanceof TelegramError && error.code === 403) return; // the user blocked the bot
    const cq = update.callback_query;
    const chatId = update.message?.chat.id ?? cq?.message?.chat.id;
    if (chatId === undefined) return;
    const lang = pickLang((update.message?.from ?? cq?.from)?.language_code);
    const s = t(lang);

    // A payment the queue gave up on (isolate crashed MAX_ATTEMPTS times, or the reply failed after
    // apply) goes to the ledger — apply_payment is idempotent, so a payment already applied is only
    // re-confirmed later. Never dropped.
    const paid = update.message?.successful_payment;
    const payer = update.message?.from;
    if (paid && payer) {
      await this.ledger.park(paymentToApply(paid, identityOf(payer, chatId), lang), error, Date.now());
      await sendMessage(this.env, chatId, s.paymentPending);
      return;
    }

    // A chat turn this update was driving: close it with a valid transcript, then say so.
    const chat = await this.loadChat();
    if (chat.turn?.updateId === update.update_id) {
      const closed = closeTurn(chat);
      chat.outbox = undefined;
      await this.saveChat(chat);
      // Chat mode stays open (a retry is the user's next message), so the way out stays on screen.
      await sendMessage(this.env, chatId, s.chatFailed, endChatKeyboard(s));
      const user = update.message?.from ?? cq?.from;
      if (user) await this.track(user.id, null, { type: "tg_chat_turn", result: "error", rounds: closed?.rounds ?? 0 });
      return;
    }
    if (chat.outbox?.updateId === update.update_id) {
      // The turn's reply could not be delivered — drop it; the generic error below still says so.
      chat.outbox = undefined;
      await this.saveChat(chat);
    }

    // A failed AI create must not leave the prompt stuck on "Creating…" with no buttons.
    const cb = cq ? decodeCallback(cq.data) : null;
    if (cb?.t === "draft") {
      const drafts = await this.loadDrafts();
      const d = drafts[cb.draftId];
      if (d && d.status === "creating") {
        d.status = "prompted";
        await this.saveDrafts(drafts);
        const cfg = await getAiConfig(this.env);
        const view = renderDraftPrompt(d, cfg.actionCost, s, s.genericError);
        await editOrSend(this.env, chatId, cq?.message?.message_id ?? d.promptMessageId ?? undefined, view.text, view.keyboard);
        return;
      }
    }
    await sendMessage(this.env, chatId, s.genericError);
  }

  // ── messages ──────────────────────────────────────────────────────────────────

  private async onMessage(msg: TgMessage, attempt: number, updateId: number): Promise<void> {
    const user = msg.from;
    if (!user) return; // private-chat messages always carry `from`
    const lang = pickLang(user.language_code);
    const s = t(lang);
    const c = classifyMessage(msg);
    const who = identityOf(user, msg.chat.id);
    // Any user message keeps an open chat alive (photo, voice, file, command, text); one idle past
    // 30 min is closed here first, so a text after that takes the draft flow below.
    const chat = await this.loadChat();
    const activity = noteUserActivity(chat, Date.now());
    if (activity === "expired") await this.expireChat(chat, who, lang);
    else if (activity === "refreshed") await this.saveChat(chat);
    switch (c.type) {
      case "command":
        return this.onCommand(c.command, c.arg, who, lang, attempt, updateId);
      case "reject":
        await sendMessage(this.env, msg.chat.id, rejectText(s, c.reason));
        // No link lookup just for analytics: the pseudonymous device id still attributes it.
        await this.track(user.id, null, { type: "tg_ai_failed", reason: "unsupported" });
        return;
      case "content": {
        // Chat mode: a plain text message goes to the agent. A link, photo, voice or file — and any
        // text once the chat is closed or idle past 30 min — takes the draft flow as before.
        const only = c.parts.length === 1 ? c.parts[0] : undefined;
        if (typeof msg.text === "string" && only?.kind === "text" && activity === "refreshed") {
          return this.onChatText(only.text, who, lang, updateId);
        }
        return this.addToDraft(msg.chat.id, user, lang, c.parts, c.mediaGroupId);
      }
      case "payment":
        return this.onSuccessfulPayment(c.payment, who, lang, attempt);
      case "service":
        return;
    }
  }

  private async onCommand(
    command: string,
    arg: string,
    who: TelegramIdentity,
    lang: Lang,
    attempt: number,
    updateId: number,
  ): Promise<void> {
    const s = t(lang);
    switch (resolveCommand(command)) {
      case "start":
        return this.cmdStart(arg, who, lang, attempt);
      case "lists":
        return this.showLists(who, lang, 0, undefined);
      case "chat":
        return this.cmdChat(arg, who, lang, updateId);
      case "timezone":
        return this.cmdTimezone(who, lang);
      case "credits":
        return this.cmdCredits(who, lang);
      case "premium":
        return this.cmdPremium(who, lang);
      case "app":
        return this.cmdApp(who, lang);
      case "paysupport":
        await sendMessage(this.env, who.chatId, s.paySupport(SUPPORT_EMAIL, TERMS_URL));
        return;
      case "unlink":
        return this.cmdUnlink(who, lang);
      case "help":
        await sendMessage(this.env, who.chatId, s.help);
        return;
    }
  }

  // ── Phase 3: subscription, payments, mobile app ───────────────────────────────

  /**
   * `/premium`: not linked → link prompt; store premium → "already active, works here too";
   * Telegram premium → until-date + renewal state; otherwise perks + "one subscription for every
   * platform" + a `createInvoiceLink` button (XTR, price from premium_status, 30 days).
   */
  private async cmdPremium(who: TelegramIdentity, lang: Lang): Promise<void> {
    const s = t(lang);
    const link = await getLink(this.env, who.telegramUserId);
    if (!link) return this.sendLoginPrompt(who, lang, false);
    const cfg = await getAiConfig(this.env);
    const status = parsePremiumStatus(
      await callInternal(this.env, "premium_status", { telegram_user_id: who.telegramUserId }),
      cfg.priceStars,
    );
    if (!status.linked) return this.sendLoginPrompt(who, lang, false);

    let invoiceUrl: string | null = null;
    if (premiumScreen(status).kind === "offer") {
      invoiceUrl = await tgCall<string>(this.env, "createInvoiceLink", invoiceParams(s, status.priceStars));
    }
    const view = renderPremiumView(status, cfg.premiumDailyCap, s, lang, invoiceUrl);
    await sendMessage(this.env, who.chatId, view.text, keyboardOrNone(view.keyboard));
    await this.track(who.telegramUserId, link.creditUserId, { type: "tg_premium_opened" });
    if (invoiceUrl) await this.track(who.telegramUserId, link.creditUserId, { type: "tg_invoice_created" });
  }

  private async cmdApp(who: TelegramIdentity, lang: Lang): Promise<void> {
    const view = renderAppView(t(lang));
    await sendMessage(this.env, who.chatId, view.text, view.keyboard);
    await this.track(who.telegramUserId, null, { type: "tg_app_link_opened" });
  }

  /**
   * `successful_payment` → `apply_payment` (idempotent by charge id, so queue retries are safe) →
   * confirmation with the until-date, or the "not linked" notice for `status: "unassigned"`. If the
   * Cloud Function is still down on the last queue attempt, the payment is parked in the durable
   * ledger (ledger.ts) and the user is told it arrived and Premium will follow — never silence, never
   * a dropped payment.
   */
  private async onSuccessfulPayment(
    p: TgSuccessfulPayment,
    who: TelegramIdentity,
    lang: Lang,
    attempt: number,
  ): Promise<void> {
    const payment = paymentToApply(p, who, lang);
    const outcome = await applyOrPark(
      this.ledger,
      payment,
      (body) => callInternal(this.env, "apply_payment", body),
      attempt,
      Date.now(),
    );
    await this.sendPaymentReply(payment, outcome);
    await this.track(who.telegramUserId, null, {
      type: "tg_payment_succeeded",
      amount: p.total_amount,
      isRecurring: p.is_recurring === true,
    });
  }

  /**
   * The payer's message for an outcome. For an unlinked payment the sign-in prompt follows — a
   * best-effort extra: its failure must not fail (and so repeat) the payment message itself.
   */
  private async sendPaymentReply(p: PaymentToApply, outcome: PaymentOutcome): Promise<void> {
    await sendMessage(this.env, p.who.chatId, paymentReplyText(outcome, p.renewal, p.lang));
    if (outcome.kind !== "unlinked") return;
    try {
      await this.sendLoginPrompt(p.who, p.lang, false);
    } catch (e) {
      console.warn(`[gisti-tg] login prompt after unlinked charge ${p.chargeId} failed: ${errText(e)}`);
    }
  }

  /** Ledger callback: a parked payment was finally applied. "Pending" was already said when it was parked. */
  private async notifyParkedPayment(p: ParkedPayment, outcome: PaymentOutcome): Promise<void> {
    if (outcome.kind === "pending") return; // parseApplyPayment logged why there is no date
    await this.sendPaymentReply(p, outcome);
  }

  /** Bot API 10.2 `Update.subscription` → `subscription_update`. No chat message (Telegram notifies itself). */
  private async onSubscriptionUpdated(sub: TgBotSubscriptionUpdated): Promise<void> {
    if (!isSubscriptionState(sub.state)) {
      console.warn(`[gisti-tg] subscription update with unknown state "${String(sub.state).slice(0, 20)}" — skipped`);
      return;
    }
    await callInternal(this.env, "subscription_update", { telegram_user_id: sub.user.id, state: sub.state });
    await this.track(sub.user.id, null, { type: "tg_subscription_updated", state: sub.state });
  }

  private async cmdStart(arg: string, who: TelegramIdentity, lang: Lang, attempt: number): Promise<void> {
    const s = t(lang);
    if (!arg) {
      if (await getLink(this.env, who.telegramUserId)) {
        await sendMessage(this.env, who.chatId, `${s.welcome}\n\n${s.help}`);
        return;
      }
      await sendMessage(this.env, who.chatId, s.welcome);
      await this.sendLoginPrompt(who, lang, false);
      return;
    }
    if (!TOKEN_RE.test(arg)) {
      await sendMessage(this.env, who.chatId, s.tokenExpired);
      return;
    }
    let creditUserId: string | null = null;
    try {
      const r = await callInternal(this.env, "consume_app_token", { token: arg, ...identityBody(who) });
      creditUserId = typeof r["credit_user_id"] === "string" ? (r["credit_user_id"] as string) : null;
    } catch (e) {
      if (!(e instanceof InternalApiError)) throw e;
      if (e.status === 409) {
        await sendMessage(this.env, who.chatId, s.noAccount);
        return;
      }
      if (e.status === 404 || e.status === 410) {
        // A retried update whose first attempt already consumed the token now sees token_used —
        // the link exists, so it is a success, not an expired link.
        const existing = attempt > 1 && e.status === 410 ? await getLink(this.env, who.telegramUserId) : null;
        if (!existing) {
          await sendMessage(this.env, who.chatId, s.tokenExpired);
          return;
        }
        creditUserId = existing.creditUserId;
      } else {
        throw e;
      }
    }
    await sendMessage(this.env, who.chatId, `${s.linked}\n\n${s.help}`);
    await this.track(who.telegramUserId, creditUserId, { type: "tg_link_completed" });
  }

  private async cmdCredits(who: TelegramIdentity, lang: Lang): Promise<void> {
    const link = await getLink(this.env, who.telegramUserId);
    if (!link) return this.sendLoginPrompt(who, lang, false);
    const [info, cfg] = await Promise.all([getCreditInfo(this.env, link.creditUserId), getAiConfig(this.env)]);
    await sendMessage(this.env, who.chatId, t(lang).credits(info.credits, info.premium, cfg.actionCost));
  }

  private async cmdUnlink(who: TelegramIdentity, lang: Lang): Promise<void> {
    const s = t(lang);
    if (!(await getLink(this.env, who.telegramUserId))) {
      await this.closeChatQuietly(who, lang);
      await sendMessage(this.env, who.chatId, s.notConnected);
      return;
    }
    await callInternal(this.env, "unlink", { telegram_user_id: who.telegramUserId });
    await this.closeChatQuietly(who, lang); // an unlinked chat would answer every text with a sign-in prompt
    await sendMessage(this.env, who.chatId, s.unlinked);
  }

  /** Close chat mode without a message of its own (a pending card is declined and marked). */
  private async closeChatQuietly(who: TelegramIdentity, lang: Lang): Promise<void> {
    const s = t(lang);
    const state = await this.loadChat();
    if (!state.open && !state.turn && !state.pending) return;
    const closed = closeChat(state);
    await this.saveChat(state);
    await this.afterClose(closed, who, s, null, s.confirmDeclined);
  }

  /**
   * Not linked: explain, offer «Sign in with Google» (a one-time `create_login_token` URL) and the
   * in-app path. If the token call fails the message still goes out, without the button — logged.
   */
  private async sendLoginPrompt(who: TelegramIdentity, lang: Lang, resend: boolean): Promise<void> {
    const s = t(lang);
    let url: string | null = null;
    try {
      const r = await callInternal(this.env, "create_login_token", identityBody(who));
      const u = r["url"];
      url = typeof u === "string" && u.startsWith("https://") ? u : null;
      if (!url) console.error("[gisti-tg] create_login_token returned no https url");
    } catch (e) {
      console.error(`[gisti-tg] create_login_token failed: ${errText(e)}`);
    }
    const text = [s.notLinked, s.orInApp, resend ? s.sendAgainAfterLink : ""].filter((x) => x).join("\n\n");
    await sendMessage(this.env, who.chatId, text, url ? { inline_keyboard: [[{ text: s.signInButton, url }]] } : undefined);
  }

  // ── drafts ────────────────────────────────────────────────────────────────────

  private async addToDraft(
    chatId: number,
    user: TgUser,
    lang: Lang,
    parts: DraftPart[],
    mediaGroupId: string | null,
  ): Promise<void> {
    const now = Date.now();
    const drafts = await this.loadDrafts();
    let d = collectingDraft(drafts);

    // An album item that arrives after its draft was already prompted joins that draft.
    if (!d && mediaGroupId) {
      const prompted = Object.values(drafts).find(
        (x) => x.status === "prompted" && x.mediaGroupIds.includes(mediaGroupId) && now - x.updatedAt < ALBUM_MERGE_MS,
      );
      if (prompted) {
        addParts(prompted, parts, mediaGroupId, now);
        await this.saveDrafts(drafts);
        const cfg = await getAiConfig(this.env);
        const view = renderDraftPrompt(prompted, cfg.actionCost, t(prompted.lang));
        await editOrSend(this.env, chatId, prompted.promptMessageId ?? undefined, view.text, view.keyboard);
        return;
      }
    }

    if (!d) {
      const seq = (await this.store.get<number>(DRAFT_SEQ_KEY)) ?? 0;
      const { id, numeric } = nextDraftId(seq, now);
      await this.store.put(DRAFT_SEQ_KEY, numeric);
      d = newDraft({
        id,
        chatId,
        user: {
          id: user.id,
          username: user.username ?? null,
          firstName: user.first_name ?? null,
          languageCode: user.language_code ?? null,
        },
        lang,
        cloudId: newCloudId(),
        now,
      });
      drafts[id] = d;
    }
    d.lang = lang;
    addParts(d, parts, mediaGroupId, now);
    await this.saveDrafts(drafts);
  }

  /** Send the prompt of a collecting draft whose debounce deadline has passed. */
  private async flushDueDraft(): Promise<void> {
    const drafts = await this.loadDrafts();
    const d = collectingDraft(drafts);
    if (!d || d.dueAt > Date.now()) return;
    try {
      await this.promptDraft(drafts, d);
    } catch (e) {
      d.flushAttempts += 1;
      if (isRetryable(e) && d.flushAttempts < MAX_ATTEMPTS) {
        d.dueAt = Date.now() + backoffMs(d.flushAttempts);
        await this.saveDrafts(drafts);
        console.warn(`[gisti-tg] draft prompt failed (attempt ${d.flushAttempts}), retrying: ${errText(e)}`);
        return;
      }
      console.error(`[gisti-tg] draft prompt dropped after ${d.flushAttempts} attempt(s): ${errText(e)}`);
      delete drafts[d.id];
      await this.saveDrafts(drafts);
      if (e instanceof TelegramError && e.code === 403) return;
      try {
        await sendMessage(this.env, d.chatId, t(d.lang).genericError);
      } catch (inner) {
        console.error(`[gisti-tg] could not report the dropped draft: ${errText(inner)}`);
      }
    }
  }

  private async promptDraft(drafts: Drafts, d: Draft): Promise<void> {
    const link = await getLink(this.env, d.user.id);
    if (!link) {
      // Send first, forget after: a failed send keeps the draft for the retry.
      await this.sendLoginPrompt(draftIdentity(d), d.lang, true);
      delete drafts[d.id];
      await this.saveDrafts(drafts);
      return;
    }
    const cfg = await getAiConfig(this.env);
    const view = renderDraftPrompt(d, cfg.actionCost, t(d.lang));
    const sent = await sendMessage(this.env, d.chatId, view.text, view.keyboard);
    d.status = "prompted";
    d.promptMessageId = sent.message_id;
    d.updatedAt = Date.now();
    await this.saveDrafts(drafts);
  }

  // ── callbacks ─────────────────────────────────────────────────────────────────

  private async onCallback(cq: TgCallbackQuery, updateId: number): Promise<void> {
    let answered = false;
    const answer: Answer = async (text) => {
      if (answered) return;
      answered = true;
      await answerCallback(this.env, cq.id, text);
    };
    const lang = pickLang(cq.from.language_code);
    const s = t(lang);
    try {
      const chatId = cq.message?.chat.id;
      const messageId = cq.message?.message_id;
      if (chatId === undefined) return;
      const cb = decodeCallback(cq.data);
      if (!cb) {
        await answer(s.viewOutdated);
        return;
      }
      const who = identityOf(cq.from, chatId);
      await this.onCallbackData(cb, who, messageId, lang, answer, updateId);
    } finally {
      await answer(); // always clear the button spinner, even when the work threw
    }
  }

  private async onCallbackData(
    cb: Callback,
    who: TelegramIdentity,
    messageId: number | undefined,
    lang: Lang,
    answer: Answer,
    updateId: number,
  ): Promise<void> {
    // Any other button also keeps an open chat alive; the chat's own buttons (card, options, End
    // chat) check expiry themselves and answer «chat ended» rather than act on a closed chat.
    if (cb.t !== "confirm" && cb.t !== "option" && cb.t !== "endChat") {
      const chat = await this.loadChat();
      const activity = noteUserActivity(chat, Date.now());
      if (activity === "expired") await this.expireChat(chat, who, lang);
      else if (activity === "refreshed") await this.saveChat(chat);
    }
    switch (cb.t) {
      case "noop":
        return;
      case "draft":
        return this.onDraftAction(cb.draftId, cb.action, who, messageId, lang, answer, updateId);
      case "confirm":
        return this.onConfirm(cb.turn, cb.yes, who, messageId, lang, answer, updateId);
      case "option":
        return this.onOption(cb.turn, cb.index, who, messageId, lang, answer, updateId);
      case "endChat":
        return this.onEndChat(who, lang, answer);
      case "tz":
        return this.onTimezonePick(cb.offset, who, messageId, lang, answer);
      case "lists":
        await answer();
        return this.showLists(who, lang, cb.page, messageId);
      case "open":
        return this.openChecklist(cb.key, cb.page, who, messageId, lang, answer);
      case "toggle":
        return this.toggle(cb.key, cb.checked, cb.page, who, messageId, lang, answer);
    }
  }

  private async onDraftAction(
    draftId: string,
    action: DraftAction,
    who: TelegramIdentity,
    messageId: number | undefined,
    lang: Lang,
    answer: Answer,
    updateId: number,
  ): Promise<void> {
    const s = t(lang);
    const drafts = await this.loadDrafts();
    const d = drafts[draftId];
    if (!d || d.status === "collecting") {
      await answer(s.draftExpired);
      await editOrSend(this.env, who.chatId, messageId, s.draftExpired);
      return;
    }
    if (d.status === "done") return; // double tap after success — nothing to do
    const target = messageId ?? d.promptMessageId ?? undefined;

    if (action === "cancel") {
      delete drafts[draftId];
      await this.saveDrafts(drafts);
      await answer();
      await editOrSend(this.env, who.chatId, target, s.cancelled);
      return;
    }

    const link = await getLink(this.env, who.telegramUserId);
    if (!link) {
      await answer();
      await this.sendLoginPrompt(who, lang, true);
      return;
    }
    if (action === "free") return this.createFromLines(drafts, d, link, who.chatId, target, s, answer);
    if (action === "chat") return this.askAiFromDraft(drafts, d, link, who, target, lang, answer, updateId);
    return this.createWithAi(drafts, d, link, who.chatId, target, lang, answer);
  }

  /** Free path: lines → items → `create_checklist_empty` sequence (createEmptyChecklist + writeChecklist). */
  private async createFromLines(
    drafts: Drafts,
    d: Draft,
    link: TelegramLink,
    chatId: number,
    messageId: number | undefined,
    s: Strings,
    answer: Answer,
  ): Promise<void> {
    const parsed = freeOption(d, s.defaultChecklistName);
    if (!parsed) {
      await answer(s.draftExpired);
      return;
    }
    await answer();
    const checklist = createEmptyChecklist(parsed.title, parsed.items, Date.now());
    checklist.cloudId = d.cloudId; // fixed per draft → a retried write updates, never duplicates
    await writeChecklist(this.env, link.googleUid, checklist);
    d.status = "done";
    d.updatedAt = Date.now();
    await this.saveDrafts(drafts);

    const lines = [s.created(checklist.name, leafItemCount(checklist))];
    if (parsed.truncated) lines.push(s.truncatedLines(MAX_ITEMS));
    lines.push("", s.seeLists);
    await editOrSend(this.env, chatId, messageId, lines.join("\n"));
    await this.track(d.user.id, link.creditUserId, { type: "tg_checklist_created", mode: "free", input: "text" });
  }

  /** AI path: the `create_checklist_ai` sequence (generate_checklist → buildAiChecklist → writeChecklist). */
  private async createWithAi(
    drafts: Drafts,
    d: Draft,
    link: TelegramLink,
    chatId: number,
    messageId: number | undefined,
    lang: Lang,
    answer: Answer,
  ): Promise<void> {
    const s = t(lang);
    const plan = aiPlan(d);
    if (!plan) {
      await answer(s.draftExpired);
      return;
    }
    await answer(s.creating);
    d.status = "creating";
    d.updatedAt = Date.now();
    await this.saveDrafts(drafts);
    await editOrSend(this.env, chatId, messageId, s.creating);

    const cfg = await getAiConfig(this.env);
    let result: Awaited<ReturnType<typeof generateChecklistViaCf>>;
    try {
      let inputData = plan.inputData;
      if (plan.file) {
        const bytes = await downloadFile(this.env, plan.file.fileId);
        inputData = plan.file.kind === "textfile" ? new TextDecoder("utf-8").decode(bytes) : base64FromBytes(bytes);
      }
      if (aiInputLength(plan.inputType, plan.prompt, inputData) > cfg.maxInputLength) throw new TooLongError();
      result = await generateChecklistViaCf(this.env, {
        userId: link.creditUserId,
        prompt: plan.prompt,
        requestId: aiRequestId(chatId, d.id),
        inputType: plan.inputType,
        inputData,
        locale: lang === "ru" ? "ru" : "en",
        ...(plan.file?.kind === "audio" ? { mimeType: plan.file.mime } : {}),
      });
    } catch (e) {
      const notice = aiFailureNotice(e, s);
      if (notice === null) throw e; // transient → queue retry; the final failure restores the prompt
      console.warn(`[gisti-tg] AI create refused for draft ${d.id}: ${errText(e)}`);
      d.status = "prompted";
      d.updatedAt = Date.now();
      await this.saveDrafts(drafts);
      const view = renderDraftPrompt(d, cfg.actionCost, s, notice);
      await editOrSend(this.env, chatId, messageId, view.text, view.keyboard);
      await this.track(d.user.id, link.creditUserId, { type: "tg_ai_failed", reason: aiFailReason(e) });
      return;
    }

    const checklist = buildAiChecklist(result.checklistName, result.items, Date.now());
    checklist.cloudId = d.cloudId;
    await writeChecklist(this.env, link.googleUid, checklist);
    d.status = "done";
    d.updatedAt = Date.now();
    await this.saveDrafts(drafts);

    const lines = [s.created(checklist.name, leafItemCount(checklist))];
    if (result.creditsKnown) lines.push(s.creditsLeft(result.aiCredits));
    lines.push("", s.seeLists);
    await editOrSend(this.env, chatId, messageId, lines.join("\n"));
    await this.track(d.user.id, link.creditUserId, { type: "tg_checklist_created", mode: "ai", input: plan.input });
  }

  // ── lists ─────────────────────────────────────────────────────────────────────

  private async showLists(who: TelegramIdentity, lang: Lang, page: number, messageId: number | undefined): Promise<void> {
    const s = t(lang);
    const link = await getLink(this.env, who.telegramUserId);
    if (!link) return this.sendLoginPrompt(who, lang, false);
    const checklists = await collectChecklists(this.env, linkIds(link));
    if (checklists.length === 0) {
      await editOrSend(this.env, who.chatId, messageId, s.noLists);
      return;
    }
    const keys = await this.loadKeys();
    const view = renderListsPage(checklists, page, s, keys);
    await this.saveKeys(keys);
    await editOrSend(this.env, who.chatId, messageId, view.text, view.keyboard);
    // Counted on the /lists command only (messageId undefined), not on page flips or "back".
    if (messageId === undefined) await this.track(who.telegramUserId, link.creditUserId, { type: "tg_lists_opened" });
  }

  private async openChecklist(
    key: string,
    page: number,
    who: TelegramIdentity,
    messageId: number | undefined,
    lang: Lang,
    answer: Answer,
  ): Promise<void> {
    const s = t(lang);
    const keys = await this.loadKeys();
    const target = resolveKey(keys, key);
    if (!target) {
      await answer(s.viewOutdated);
      return;
    }
    const link = await getLink(this.env, who.telegramUserId);
    if (!link) {
      await answer();
      return this.sendLoginPrompt(who, lang, false);
    }
    const owned = await findChecklistWithOwner(this.env, linkIds(link), target.c);
    if (!owned) {
      await answer(s.checklistGone);
      return;
    }
    await answer();
    const view = renderItemsPage(owned.checklist, page, s, keys);
    await this.saveKeys(keys);
    await editOrSend(this.env, who.chatId, messageId, view.text, view.keyboard);
  }

  /** Toggle = MCP `toggle_item`: findChecklistWithOwner → toggleItem → writeChecklist(ownerId). */
  private async toggle(
    key: string,
    checked: boolean,
    page: number,
    who: TelegramIdentity,
    messageId: number | undefined,
    lang: Lang,
    answer: Answer,
  ): Promise<void> {
    const s = t(lang);
    const keys = await this.loadKeys();
    const target = resolveKey(keys, key);
    if (!target?.i) {
      await answer(s.viewOutdated);
      return;
    }
    const link = await getLink(this.env, who.telegramUserId);
    if (!link) {
      await answer();
      return this.sendLoginPrompt(who, lang, false);
    }
    const owned = await findChecklistWithOwner(this.env, linkIds(link), target.c);
    if (!owned) {
      await answer(s.checklistGone);
      return;
    }
    // The button carries the TARGET state, so a retried or doubled tap is idempotent.
    const next = toggleItem(owned.checklist, target.i, checked, Date.now());
    if (!next) {
      await answer(s.viewOutdated);
      const view = renderItemsPage(owned.checklist, page, s, keys);
      await this.saveKeys(keys);
      await editOrSend(this.env, who.chatId, messageId, view.text, view.keyboard);
      return;
    }
    await writeChecklist(this.env, owned.ownerId, next);
    await answer();
    const view = renderItemsPage(next, page, s, keys);
    await this.saveKeys(keys);
    await editOrSend(this.env, who.chatId, messageId, view.text, view.keyboard);
    await this.track(who.telegramUserId, link.creditUserId, { type: "tg_item_toggled" });
  }

  // ── AI chat (Phase 2) — entry points ──────────────────────────────────────────

  /** `/chat` opens chat mode (intro + «End chat»); `/chat <text>` opens it and sends the text as the first turn. */
  private async cmdChat(arg: string, who: TelegramIdentity, lang: Lang, updateId: number): Promise<void> {
    const s = t(lang);
    const link = await getLink(this.env, who.telegramUserId);
    if (!link) return this.sendLoginPrompt(who, lang, false);
    const state = await this.loadChat();
    const now = Date.now();
    const wasOpen = isChatOpen(state, now);
    const expired = !wasOpen && state.open ? closeChat(state) : null; // idle past 30 min: retire that session first
    if (tooLongForChat(arg)) {
      await this.saveChat(state);
      await sendMessage(this.env, who.chatId, s.chatTooLong);
      await this.afterClose(expired, who, s, link.creditUserId, s.confirmDeclined);
      return;
    }
    if (!arg) {
      state.open = true;
      state.lastUserAt = now;
      await this.saveChat(state);
      await sendMessage(this.env, who.chatId, s.chatIntro(CHAT_AGENT_COST), endChatKeyboard(s));
      await this.afterClose(expired, who, s, link.creditUserId, s.confirmDeclined);
      if (!wasOpen) await this.track(who.telegramUserId, link.creditUserId, { type: "tg_chat_opened", source: "command" });
      return;
    }
    if (!wasOpen) await this.sendBestEffort(who.chatId, s.chatIntro(CHAT_AGENT_COST), endChatKeyboard(s));
    await this.openTurn(state, arg, who, s, link, updateId);
    await this.afterClose(expired, who, s, link.creditUserId, s.confirmDeclined);
    if (!wasOpen) await this.track(who.telegramUserId, link.creditUserId, { type: "tg_chat_opened", source: "command" });
    await this.driveTurn(state, who, lang, link);
  }

  /** «💬 Ask AI» under a text draft: open chat mode with the draft text as the first turn. */
  private async askAiFromDraft(
    drafts: Drafts,
    d: Draft,
    link: TelegramLink,
    who: TelegramIdentity,
    messageId: number | undefined,
    lang: Lang,
    answer: Answer,
    updateId: number,
  ): Promise<void> {
    const s = t(lang);
    const text = isTextOnly(d) ? draftText(d) : "";
    if (!text) {
      await answer(s.draftExpired);
      return;
    }
    if (tooLongForChat(text)) {
      await answer();
      const cfg = await getAiConfig(this.env);
      const view = renderDraftPrompt(d, cfg.actionCost, s, s.chatTooLong); // buttons stay: lines / AI still work
      await editOrSend(this.env, who.chatId, messageId, view.text, view.keyboard);
      return;
    }
    await answer();
    const state = await this.loadChat();
    const wasOpen = isChatOpen(state, Date.now());
    // The turn is saved FIRST: its updateId is what lets a retry of this tap resume it, whereas a
    // draft marked done with no turn behind it would make the retry a silent no-op.
    await this.openTurn(state, text, who, s, link, updateId);
    d.status = "done";
    d.updatedAt = Date.now();
    await this.saveDrafts(drafts);
    await this.editBestEffort(who.chatId, messageId, s.chatIntro(CHAT_AGENT_COST), endChatKeyboard(s));
    if (!wasOpen) await this.track(who.telegramUserId, link.creditUserId, { type: "tg_chat_opened", source: "button" });
    await this.driveTurn(state, who, lang, link);
  }

  /** A plain text message while chat mode is open. */
  private async onChatText(text: string, who: TelegramIdentity, lang: Lang, updateId: number): Promise<void> {
    const s = t(lang);
    const link = await getLink(this.env, who.telegramUserId);
    if (!link) {
      // Unlinked meanwhile (e.g. from the app): close chat mode so the next texts draft again.
      await this.closeChatQuietly(who, lang);
      return this.sendLoginPrompt(who, lang, true);
    }
    if (tooLongForChat(text)) {
      await sendMessage(this.env, who.chatId, s.chatTooLong, endChatKeyboard(s));
      return;
    }
    const state = await this.loadChat();
    await this.openTurn(state, text, who, s, link, updateId);
    await this.driveTurn(state, who, lang, link);
  }

  /** Chat mode idle past 30 min, found on the next text: close it (declining a pending card); the text then drafts. */
  private async expireChat(state: ChatState, who: TelegramIdentity, lang: Lang): Promise<void> {
    const s = t(lang);
    const closed = closeChat(state);
    await this.saveChat(state);
    await this.afterClose(closed, who, s, null, s.confirmDeclined);
  }

  /** «Yes» / «Cancel» on the confirmation card: run the batch, or answer every call with `declined`. */
  private async onConfirm(
    turnSeq: number,
    yes: boolean,
    who: TelegramIdentity,
    messageId: number | undefined,
    lang: Lang,
    answer: Answer,
    updateId: number,
  ): Promise<void> {
    const s = t(lang);
    const state = await this.loadChat();
    const p = state.pending;
    const turn = state.turn;
    if (!p || !turn || p.turnSeq !== turnSeq || turn.seq !== turnSeq) {
      await answer(s.chatOutdated);
      return;
    }
    const link = await this.chatButtonGate(state, who, lang, answer);
    if (!link) return;
    state.lastUserAt = Date.now();
    state.pending = undefined;
    turn.updateId = updateId; // this tap now drives the turn — a retry of it resumes here
    const declined = declinedResults(p.calls);
    if (yes) {
      // The plans the card showed — applied as stored (exact ids), never re-resolved by text.
      turn.inflight = { calls: p.calls, records: p.records };
    } else {
      appendRound(state, p.calls, declined);
    }
    await this.saveChat(state);
    await this.editBestEffort(who.chatId, messageId ?? p.cardMessageId, `${p.cardText}\n\n${yes ? s.confirmApproved : s.confirmDeclined}`);
    if (!yes) await sendTgEvents(this.env, await tgIdentity(who.telegramUserId, link.creditUserId), toolEvents(p.calls, declined));
    await this.driveTurn(state, who, lang, link); // the agent sees the results (or the refusals) and answers
  }

  /** A `present_options` button: its label becomes the next user turn (charged, as in the app). */
  private async onOption(
    turnSeq: number,
    index: number,
    who: TelegramIdentity,
    messageId: number | undefined,
    lang: Lang,
    answer: Answer,
    updateId: number,
  ): Promise<void> {
    const s = t(lang);
    const state = await this.loadChat();
    const options = state.options;
    const label = options && options.turnSeq === turnSeq ? options.labels[index] : undefined;
    if (!options || label === undefined) {
      await answer(s.chatOutdated);
      return;
    }
    const link = await this.chatButtonGate(state, who, lang, answer);
    if (!link) return;
    await this.editBestEffort(who.chatId, messageId, `${plainText(options.prompt)}\n\n› ${label}`);
    await this.openTurn(state, label, who, s, link, updateId);
    await this.driveTurn(state, who, lang, link);
  }

  /**
   * The shared gate of the card / option buttons, run after their own "is this button current"
   * check: a chat idle past 30 min is closed (answered «chat ended»), an unlinked user gets the
   * sign-in prompt. The callback is answered either way. Returns the link to act with, or null.
   */
  private async chatButtonGate(state: ChatState, who: TelegramIdentity, lang: Lang, answer: Answer): Promise<TelegramLink | null> {
    const s = t(lang);
    if (!isChatOpen(state, Date.now())) {
      const closed = closeChat(state);
      await this.saveChat(state);
      await answer(s.chatExpired);
      await this.afterClose(closed, who, s, null, s.confirmDeclined);
      return null;
    }
    const link = await getLink(this.env, who.telegramUserId);
    await answer();
    if (!link) {
      await this.sendLoginPrompt(who, lang, false);
      return null;
    }
    return link;
  }

  /** «End chat»: close chat mode (a pending card is declined); texts become drafts again. */
  private async onEndChat(who: TelegramIdentity, lang: Lang, answer: Answer): Promise<void> {
    const s = t(lang);
    const state = await this.loadChat();
    const closed = closeChat(state);
    await this.saveChat(state);
    await answer();
    await sendMessage(this.env, who.chatId, s.chatEnded);
    await this.afterClose(closed, who, s, null, s.confirmDeclined);
  }

  /** `/timezone`: «what time is it for you now?» with the current local time of each offered offset. */
  private async cmdTimezone(who: TelegramIdentity, lang: Lang): Promise<void> {
    const link = await getLink(this.env, who.telegramUserId);
    if (!link) return this.sendLoginPrompt(who, lang, false);
    const view = renderTimezonePicker(Date.now(), await getUserTimezone(this.env, link.googleUid), t(lang));
    await sendMessage(this.env, who.chatId, view.text, view.keyboard);
  }

  /** A `/timezone` pick → `users/{google_uid}` {timezone, timezone_source:"telegram"} (masked PATCH). */
  private async onTimezonePick(
    offset: number,
    who: TelegramIdentity,
    messageId: number | undefined,
    lang: Lang,
    answer: Answer,
  ): Promise<void> {
    const s = t(lang);
    const zone = TZ_CHOICES.includes(offset) ? zoneForOffset(offset) : null;
    if (!zone) {
      await answer(s.viewOutdated);
      return;
    }
    const link = await getLink(this.env, who.telegramUserId);
    if (!link) {
      await answer();
      return this.sendLoginPrompt(who, lang, false);
    }
    await setUserTimezone(this.env, link.googleUid, zone);
    await answer();
    await editOrSend(this.env, who.chatId, messageId, s.timezoneSaved(offsetLabel(offset), formatHm(Date.now(), zone)));
  }

  // ── AI chat — the turn loop ───────────────────────────────────────────────────

  /**
   * A queue retry of an update that drives a chat turn (attempt > 1): re-send the closed turn's
   * undelivered reply, or resume the turn from its persisted progress. False → not a chat retry.
   */
  private async resumeChat(update: TgUpdate): Promise<boolean> {
    const user = update.message?.from ?? update.callback_query?.from;
    const chatId = update.message?.chat.id ?? update.callback_query?.message?.chat.id;
    if (!user || chatId === undefined) return false;
    const state = await this.loadChat();
    if (state.outbox?.updateId === update.update_id) {
      await this.flushOutbox(state, chatId);
      return true;
    }
    if (state.turn?.updateId !== update.update_id) return false;
    if (update.callback_query) await answerCallback(this.env, update.callback_query.id);
    const lang = pickLang(user.language_code);
    const who = identityOf(user, chatId);
    const link = await getLink(this.env, user.id);
    if (!link) {
      closeTurn(state);
      await this.saveChat(state);
      await this.sendLoginPrompt(who, lang, true);
      return true;
    }
    await this.driveTurn(state, who, lang, link);
    return true;
  }

  /** Close whatever turn is open (a pending card is declined), start a new one with `text`, persist. */
  private async openTurn(
    state: ChatState,
    text: string,
    who: TelegramIdentity,
    s: Strings,
    link: TelegramLink,
    updateId: number,
  ): Promise<void> {
    const closed = closeTurn(state);
    beginTurn(state, text, who.chatId, updateId, Date.now());
    await this.saveChat(state);
    await this.afterClose(closed, who, s, link.creditUserId, s.confirmSuperseded);
  }

  /**
   * Run the active turn until it ends or pauses: call `chat_agent` with the windowed transcript,
   * execute tool rounds that need no confirmation, pause on a destructive batch (card), and finish
   * on a final / options / refusal. A transient failure throws — the queue retries the update and
   * `resumeChat` comes back here with the progress persisted so far.
   */
  private async driveTurn(state: ChatState, who: TelegramIdentity, lang: Lang, link: TelegramLink): Promise<void> {
    const s = t(lang);
    const turn = state.turn;
    if (!turn) return;
    if (state.pending) {
      if (state.pending.cardMessageId === null) await this.sendCard(state, who.chatId, s);
      return;
    }
    const [lists, timezone, identity] = await Promise.all([
      collectOwnedChecklists(this.env, linkIds(link)),
      getUserTimezone(this.env, link.googleUid),
      tgIdentity(who.telegramUserId, link.creditUserId),
    ]);
    if (turn.inflight) await this.executeBatch(state, lists, link, timezone, identity);

    for (;;) {
      await sendTyping(this.env, who.chatId);
      const step = await callChatAgent(
        this.env,
        buildAgentRequest({
          creditUserId: link.creditUserId,
          requestId: turn.requestId,
          lang,
          offsetMinutes: timezone ? offsetMinutesAt(timezone, Date.now()) : null,
          summary: buildChecklistsSummary(lists),
          transcript: state.transcript,
        }),
      );

      if (step.kind === "tool_calls") {
        const action = toolCallsAction(step.calls, turn.round);
        if (action === "execute") {
          turn.inflight = { calls: step.calls, records: {} };
          await this.saveChat(state);
          await this.executeBatch(state, lists, link, timezone, identity);
          continue;
        }
        if (action === "pause") {
          // Plan the whole batch now and show THOSE plans: the card names the row and list that
          // will really change, and «Yes» applies exactly these records.
          const records = planBatch(step.calls, lists, turn, timezone, Date.now());
          const card = renderConfirmCard(step.calls, records, turn.seq, s, timezone);
          state.pending = { turnSeq: turn.seq, calls: step.calls, records, cardText: card.text, cardMessageId: null };
          await this.saveChat(state);
          await this.sendCard(state, who.chatId, s);
          return;
        }
        // The server answers its own round cap with a final (main.py:3904-3917); this is the local guard.
        console.warn(`[gisti-tg] chat turn ${turn.requestId}: tool calls after ${turn.round} rounds — stopped locally`);
        return this.endTurn(state, who, identity, "limit", [{ text: s.chatRoundLimit, keyboard: endChatKeyboard(s) }], null);
      }

      const tzHint = turn.timezoneUnknown ? [s.tzUnknownHint] : [];
      switch (step.kind) {
        case "final":
          return this.endTurn(state, who, identity, "final", renderReply(step.content, s, tzHint), step.content);
        case "options": {
          state.options = { turnSeq: turn.seq, prompt: step.prompt, labels: step.options };
          const shown = [step.prompt, ...tzHint].join("\n\n");
          return this.endTurn(state, who, identity, "options", [renderOptions(shown, step.options, turn.seq, s)], step.prompt);
        }
        case "no_credits":
          // finishTurn closes chat mode: the message says so, and the next text is a draft again.
          return this.endTurn(state, who, identity, "no_credits", [{ text: s.chatNoCredits }], null);
        case "rejected":
          console.warn(`[gisti-tg] chat_agent refused turn ${turn.requestId} (400): ${step.error.slice(0, 200)}`);
          return this.endTurn(state, who, identity, "rejected", [{ text: s.chatRejected, keyboard: endChatKeyboard(s) }], null);
        case "no_user": {
          const ended = finishTurn(state, "no_user", null); // also closes chat mode
          await this.saveChat(state);
          await this.sendLoginPrompt(who, lang, true);
          await sendTgEvents(this.env, identity, [{ type: "tg_chat_turn", result: "error", rounds: ended?.rounds ?? 0 }]);
          return;
        }
      }
    }
  }

  /**
   * Execute the in-flight batch call by call. Per call: plan (resolve to exact ids) → persist the
   * plan → apply + write to Firestore → persist "applied". A retry skips applied calls and re-applies
   * a persisted plan idempotently (agentTools.ts), so no write ever runs twice. The calls and their
   * results reach the transcript only together, once every call has a result.
   */
  private async executeBatch(
    state: ChatState,
    lists: OwnedChecklist[],
    link: TelegramLink,
    timezone: string | null,
    identity: TgAnalyticsIdentity,
  ): Promise<void> {
    const turn = state.turn;
    const inflight = turn?.inflight;
    if (!turn || !inflight) return;
    const results: AgentToolResult[] = [];
    for (let i = 0; i < inflight.calls.length; i++) {
      const call = inflight.calls[i]!;
      const key = String(i);
      let rec = inflight.records[key];
      if (!rec) {
        const plan = planTool(call, lists, { timezone, seed: callSeed(turn, i) });
        rec = { plan, applied: plan.ops.length === 0 };
        inflight.records[key] = rec;
        await this.saveChat(state);
      }
      if (!rec.applied) {
        for (const owned of applyOps(lists, rec.plan.ops, link.googleUid, Date.now())) {
          await writeChecklist(this.env, owned.ownerId, owned.checklist);
        }
        rec.applied = true;
        await this.saveChat(state);
      }
      if (rec.plan.timezoneUnknown) turn.timezoneUnknown = true;
      results.push({ id: call.id, name: call.name, result: rec.plan.result });
    }
    appendRound(state, inflight.calls, results);
    await this.saveChat(state);
    await sendTgEvents(this.env, identity, toolEvents(inflight.calls, results));
  }

  /**
   * End the turn (`finishTurn`: the model's text for final / options, else a closed turn; chat mode
   * closes on no_credits), park the replies in the outbox, persist, then deliver. A failed send is
   * retried from the outbox by `resumeChat` — the agent is never asked (or paid) twice for one message.
   * Every reply that leaves chat mode open carries «End chat».
   */
  private async endTurn(
    state: ChatState,
    who: TelegramIdentity,
    identity: TgAnalyticsIdentity,
    end: TurnEnd,
    messages: OutMsg[],
    modelText: string | null,
  ): Promise<void> {
    const ended = finishTurn(state, end, modelText);
    if (!ended) return;
    state.outbox = { updateId: ended.updateId, messages };
    await this.saveChat(state);
    await this.flushOutbox(state, who.chatId);
    await sendTgEvents(this.env, identity, [{ type: "tg_chat_turn", result: turnResult(end), rounds: ended.rounds }]);
  }

  /** Deliver the outbox in order, persisting progress between messages. */
  private async flushOutbox(state: ChatState, chatId: number): Promise<void> {
    const box = state.outbox;
    if (!box) return;
    while (box.messages.length > 0) {
      const m = box.messages[0]!;
      await sendMessage(this.env, chatId, m.text, m.keyboard);
      box.messages.shift();
      if (box.messages.length > 0) await this.saveChat(state);
    }
    state.outbox = undefined;
    await this.saveChat(state);
  }

  /** Send the pending card and remember its message id (to mark it «Cancelled» if it is superseded). */
  private async sendCard(state: ChatState, chatId: number, s: Strings): Promise<void> {
    const p = state.pending;
    if (!p) return;
    const sent = await sendMessage(this.env, chatId, p.cardText, confirmKeyboard(p.turnSeq, s));
    p.cardMessageId = sent.message_id;
    await this.saveChat(state);
  }

  /** After a turn was closed by something else: mark its card, count the declined calls. Best-effort. */
  private async afterClose(
    closed: ClosedTurn | null,
    who: TelegramIdentity,
    s: Strings,
    creditUserId: string | null,
    note: string,
  ): Promise<void> {
    if (!closed) return;
    if (closed.cardMessageId !== null && closed.cardText) {
      await this.editBestEffort(who.chatId, closed.cardMessageId, `${closed.cardText}\n\n${note}`);
    }
    const events: TgEvent[] = [
      ...toolEvents(closed.declined, declinedResults(closed.declined)),
      { type: "tg_chat_turn", result: closed.declined.length > 0 ? "declined" : "error", rounds: closed.rounds },
    ];
    await sendTgEvents(this.env, await tgIdentity(who.telegramUserId, creditUserId), events);
  }

  /** Edit a bot message (buttons replaced by `keyboard`, else dropped); failure is only logged — never a fresh message. */
  private async editBestEffort(
    chatId: number,
    messageId: number | null | undefined,
    text: string,
    keyboard?: InlineKeyboard,
  ): Promise<void> {
    if (messageId === null || messageId === undefined) return;
    try {
      await tgCall(this.env, "editMessageText", {
        chat_id: chatId,
        message_id: messageId,
        text,
        link_preview_options: { is_disabled: true },
        reply_markup: keyboard ?? { inline_keyboard: [] },
      });
    } catch (e) {
      if (!isNotModified(e)) console.warn(`[gisti-tg] best-effort edit failed: ${errText(e)}`);
    }
  }

  private async sendBestEffort(chatId: number, text: string, keyboard?: InlineKeyboard): Promise<void> {
    try {
      await sendMessage(this.env, chatId, text, keyboard);
    } catch (e) {
      console.warn(`[gisti-tg] best-effort message failed: ${errText(e)}`);
    }
  }
}
