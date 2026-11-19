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
 */

import { DurableObject } from "cloudflare:workers";
import { CfError } from "../../mcp-server/src/cf";
import { newCloudId } from "../../mcp-server/src/encode";
import { findChecklistWithOwner, writeChecklist } from "../../mcp-server/src/firestore";
import { leafItemCount } from "../../mcp-server/src/model";
import { buildAiChecklist, createEmptyChecklist, toggleItem } from "../../mcp-server/src/mutate";
import { sendTgEvent, tgIdentity, type AiFailReason, type TgEvent } from "./analytics";
import {
  callInternal,
  collectChecklists,
  generateChecklistViaCf,
  getAiConfig,
  getCreditInfo,
  getLink,
  identityBody,
  InternalApiError,
  linkIds,
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
  freeOption,
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
  MAX_FILE_MB,
  sendMessage,
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

const DRAFTS_KEY = "drafts";
const DRAFT_SEQ_KEY = "draftSeq";
const KEYS_KEY = "keys";
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

  /**
   * One Amplitude event, AWAITED inside the alarm (a floating send can be lost — MCP precedent).
   * Never throws; called after the user-visible reply so it never delays it.
   */
  private async track(telegramUserId: number, creditUserId: string | null, event: TgEvent): Promise<void> {
    await sendTgEvent(this.env, await tgIdentity(telegramUserId, creditUserId), event);
  }

  // ── dispatch ──────────────────────────────────────────────────────────────────

  private async process(update: TgUpdate, attempt: number): Promise<void> {
    if (update.callback_query) return this.onCallback(update.callback_query);
    if (update.message) return this.onMessage(update.message, attempt);
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

  private async onMessage(msg: TgMessage, attempt: number): Promise<void> {
    const user = msg.from;
    if (!user) return; // private-chat messages always carry `from`
    const lang = pickLang(user.language_code);
    const s = t(lang);
    const c = classifyMessage(msg);
    switch (c.type) {
      case "command":
        return this.onCommand(c.command, c.arg, identityOf(user, msg.chat.id), lang, attempt);
      case "reject":
        await sendMessage(this.env, msg.chat.id, rejectText(s, c.reason));
        // No link lookup just for analytics: the pseudonymous device id still attributes it.
        await this.track(user.id, null, { type: "tg_ai_failed", reason: "unsupported" });
        return;
      case "content":
        return this.addToDraft(msg.chat.id, user, lang, c.parts, c.mediaGroupId);
      case "payment":
        return this.onSuccessfulPayment(c.payment, identityOf(user, msg.chat.id), lang, attempt);
      case "service":
        return;
    }
  }

  private async onCommand(command: string, arg: string, who: TelegramIdentity, lang: Lang, attempt: number): Promise<void> {
    const s = t(lang);
    switch (resolveCommand(command)) {
      case "start":
        return this.cmdStart(arg, who, lang, attempt);
      case "lists":
        return this.showLists(who, lang, 0, undefined);
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
      await sendMessage(this.env, who.chatId, s.notConnected);
      return;
    }
    await callInternal(this.env, "unlink", { telegram_user_id: who.telegramUserId });
    await sendMessage(this.env, who.chatId, s.unlinked);
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

  private async onCallback(cq: TgCallbackQuery): Promise<void> {
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
      await this.onCallbackData(cb, who, messageId, lang, answer);
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
  ): Promise<void> {
    switch (cb.t) {
      case "noop":
        return;
      case "draft":
        return this.onDraftAction(cb.draftId, cb.action, who, messageId, lang, answer);
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
}
