/**
 * Telegram Stars subscription (Phase 3) — pure decisions and payload builders, plus the
 * pre-checkout handler that runs in the WEBHOOK path (not the per-chat queue): Telegram cancels the
 * payment unless `answerPreCheckoutQuery` arrives within 10 s.
 *
 * Money and premium state are mutated only by Python (`telegram_bot_internal`: premium_status,
 * apply_payment, subscription_update, revoke_payment). The worker builds the invoice, gates the
 * checkout and forwards payment facts.
 *
 * Bot API facts used (https://core.telegram.org/bots/api, checked 2026-11-19):
 *  - createInvoiceLink: Stars → `currency: "XTR"`, `provider_token: ""`, exactly one price,
 *    `subscription_period` must be 2592000 (30 days).
 *  - answerPreCheckoutQuery(pre_checkout_query_id, ok, error_message required when ok=false).
 *  - Update.subscription: BotSubscriptionUpdated {user, invoice_payload, state: active|canceled|failed}
 *    (Bot API 10.2).
 */

import { getAiConfig, getLink, type BackendEnv } from "./backend";
import { formatDate, pickLang, t, type Lang, type Strings } from "./i18n";
import { tgCall, type TgPreCheckoutQuery, type TgSuccessfulPayment } from "./telegram";

/** Opaque invoice payload of the 30-day Premium subscription (versioned, ≤128 bytes). */
export const PREMIUM_PAYLOAD = "premium_month_v1";
export const STARS_CURRENCY = "XTR";
/** The only period Telegram accepts for Stars subscriptions. */
export const SUBSCRIPTION_PERIOD_SEC = 2592000;
/** Answer the pre-checkout with a refusal rather than miss Telegram's 10 s window. */
const PRE_CHECKOUT_BUDGET_MS = 7000;

export const SUBSCRIPTION_STATES = ["active", "canceled", "failed"] as const;
export type SubscriptionState = (typeof SUBSCRIPTION_STATES)[number];

export function isSubscriptionState(s: unknown): s is SubscriptionState {
  return typeof s === "string" && (SUBSCRIPTION_STATES as readonly string[]).includes(s);
}

// ── premium_status ──────────────────────────────────────────────────────────────

export interface PremiumStatus {
  linked: boolean;
  isPremium: boolean;
  source: "telegram" | "store" | null;
  telegramPremiumUntilMs: number | null;
  subscriptionState: SubscriptionState | null;
  priceStars: number;
}

/** Tolerant parse of the `premium_status` response (missing/invalid price → `defaultPrice`). */
export function parsePremiumStatus(r: Record<string, unknown>, defaultPrice: number): PremiumStatus {
  const source = r["source"] === "telegram" || r["source"] === "store" ? r["source"] : null;
  const until = r["telegram_premium_until_ms"];
  const price = r["price_stars"];
  return {
    linked: r["linked"] === true,
    isPremium: r["is_premium"] === true,
    source,
    telegramPremiumUntilMs: typeof until === "number" && Number.isFinite(until) ? until : null,
    subscriptionState: isSubscriptionState(r["subscription_state"]) ? r["subscription_state"] : null,
    priceStars: typeof price === "number" && Number.isInteger(price) && price > 0 ? price : defaultPrice,
  };
}

// ── invoice ─────────────────────────────────────────────────────────────────────

/** `createInvoiceLink` parameters for the 30-day Stars subscription. */
export function invoiceParams(s: Strings, priceStars: number): Record<string, unknown> {
  return {
    title: s.invoiceTitle,
    description: s.invoiceDescription,
    payload: PREMIUM_PAYLOAD,
    provider_token: "",
    currency: STARS_CURRENCY,
    prices: [{ label: s.invoiceLabel, amount: priceStars }],
    subscription_period: SUBSCRIPTION_PERIOD_SEC,
  };
}

// ── pre-checkout ────────────────────────────────────────────────────────────────

export type PreCheckoutReason = "unknown_payload" | "wrong_currency" | "price_changed" | "not_linked";
export type PreCheckoutDecision = { ok: true } | { ok: false; reason: PreCheckoutReason };

/** Accept only our payload, in Stars, at today's price, from a linked account. Pure. */
export function decidePreCheckout(
  q: Pick<TgPreCheckoutQuery, "currency" | "total_amount" | "invoice_payload">,
  linked: boolean,
  priceStars: number,
): PreCheckoutDecision {
  if (q.invoice_payload !== PREMIUM_PAYLOAD) return { ok: false, reason: "unknown_payload" };
  if (q.currency !== STARS_CURRENCY) return { ok: false, reason: "wrong_currency" };
  if (q.total_amount !== priceStars) return { ok: false, reason: "price_changed" };
  if (!linked) return { ok: false, reason: "not_linked" };
  return { ok: true };
}

export function preCheckoutError(s: Strings, reason: PreCheckoutReason | "unavailable"): string {
  switch (reason) {
    case "not_linked":
      return s.preCheckoutNotLinked;
    case "price_changed":
      return s.preCheckoutPriceChanged;
    case "unknown_payload":
    case "wrong_currency":
      return s.preCheckoutInvalid;
    case "unavailable":
      return s.preCheckoutUnavailable;
  }
}

/** `answerPreCheckoutQuery` parameters for a decision. */
export function preCheckoutAnswer(queryId: string, d: PreCheckoutDecision | { ok: false; reason: "unavailable" }, s: Strings) {
  return d.ok
    ? { pre_checkout_query_id: queryId, ok: true }
    : { pre_checkout_query_id: queryId, ok: false, error_message: preCheckoutError(s, d.reason) };
}

function withTimeout<T>(p: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`timed out after ${ms} ms`)), ms);
    p.then(
      (v) => {
        clearTimeout(timer);
        resolve(v);
      },
      (e: unknown) => {
        clearTimeout(timer);
        reject(e);
      },
    );
  });
}

/**
 * Webhook-path handler: decide from Firestore (link doc + fresh price) and answer at once. Any
 * failure to decide in time answers `ok: false` ("try again in a minute") — never silence, which
 * Telegram would turn into a failed payment after 10 s anyway.
 */
export async function handlePreCheckout(env: BackendEnv, q: TgPreCheckoutQuery): Promise<void> {
  const s = t(pickLang(q.from.language_code));
  let answer: ReturnType<typeof preCheckoutAnswer>;
  try {
    const [link, cfg] = await withTimeout(
      Promise.all([getLink(env, q.from.id), getAiConfig(env, { fresh: true })]),
      PRE_CHECKOUT_BUDGET_MS,
    );
    const decision = decidePreCheckout(q, link !== null, cfg.priceStars);
    if (!decision.ok) console.warn(`[gisti-tg] pre-checkout ${q.id} refused: ${decision.reason}`);
    answer = preCheckoutAnswer(q.id, decision, s);
  } catch (e) {
    console.error(`[gisti-tg] pre-checkout ${q.id} could not be decided: ${e instanceof Error ? e.message : e}`);
    answer = preCheckoutAnswer(q.id, { ok: false, reason: "unavailable" }, s);
  }
  await tgCall(env, "answerPreCheckoutQuery", answer);
}

// ── successful_payment ──────────────────────────────────────────────────────────

/** `apply_payment` body for a `successful_payment` (ids/amounts as JSON numbers). Pure. */
export function applyPaymentBody(telegramUserId: number, p: TgSuccessfulPayment): Record<string, unknown> {
  return {
    telegram_user_id: telegramUserId,
    telegram_payment_charge_id: p.telegram_payment_charge_id,
    total_amount: p.total_amount,
    currency: p.currency,
    invoice_payload: p.invoice_payload,
    is_recurring: p.is_recurring === true,
    is_first_recurring: p.is_first_recurring === true,
    subscription_expiration_date: typeof p.subscription_expiration_date === "number" ? p.subscription_expiration_date : null,
  };
}

/** A renewal (not the first charge of the subscription). */
export function isRenewal(p: TgSuccessfulPayment): boolean {
  return p.is_recurring === true && p.is_first_recurring !== true;
}

/** What `apply_payment` decided, as far as the message to the payer is concerned. */
export type PaymentOutcome =
  /** Premium granted (or extended) until `untilMs`. */
  | { kind: "applied"; untilMs: number }
  /** `status: "unassigned"` — no current link to a Gisti account, nothing granted; linking applies it. */
  | { kind: "unlinked" }
  /** Not confirmed (no until-date in the answer, refused, or parked for a later retry) — "shortly". */
  | { kind: "pending" };

/**
 * `apply_payment` answer → outcome. Python replays the stored outcome for a known charge id, so a
 * retried call lands here with the same `status` / `telegram_premium_until_ms` as the first one.
 */
export function parseApplyPayment(r: Record<string, unknown>, chargeId: string): PaymentOutcome {
  if (r["status"] === "unassigned") return { kind: "unlinked" };
  // Python telegram_link.apply_payment returns `telegram_premium_until_ms` (same name as premium_status).
  const u = r["telegram_premium_until_ms"];
  if (typeof u === "number" && Number.isFinite(u)) return { kind: "applied", untilMs: u };
  console.error(
    `[gisti-tg] apply_payment for charge ${chargeId} returned no telegram_premium_until_ms (applied=${String(r["applied"])}, status=${String(r["status"])})`,
  );
  return { kind: "pending" };
}

/** The message for an outcome: confirmation / renewal with the date, the unlinked notice, or "pending". */
export function paymentReplyText(o: PaymentOutcome, renewal: boolean, lang: Lang): string {
  const s = t(lang);
  switch (o.kind) {
    case "applied": {
      const date = formatDate(o.untilMs, lang);
      return renewal ? s.paymentRenewed(date) : s.paymentConfirmed(date);
    }
    case "unlinked":
      return s.paymentUnlinked;
    case "pending":
      return s.paymentPending;
  }
}
