/**
 * Durable retry ledger for Stars payments the update queue could not apply.
 *
 * A `successful_payment` reaches the bot exactly once — the webhook already answered 200, Telegram
 * never redelivers it. The queue retries `apply_payment` for ~10 s (MAX_ATTEMPTS, 2 s / 8 s backoff);
 * if the Cloud Function is still down after that, dropping the update would lose a paid payment. So
 * the last queue attempt PARKS it here, under `pay:<charge_id>`, and the DO alarm retries it with a
 * long backoff (1 min, 5 min, 30 min, 2 h, 6 h, then every 12 h) for up to 30 days:
 *  - success → the payer gets the real confirmation (or the "not linked" notice), entry deleted;
 *  - a definite "no" (4xx) → logged with the charge id, entry dropped;
 *  - still failing 30 days after the first failure → logged as given up, entry dropped.
 * `apply_payment` is idempotent by charge id (Python replays the stored outcome), so re-sending the
 * same body after an unseen success is safe. Once it answered, the outcome is stored on the entry and
 * only the message is retried — the Cloud Function is not called again for it.
 *
 * Keys never collide with the queue (`q:`) or the dedupe list (`seen`), and `UpdateQueue.head()`
 * lists only `q:`, so the queue drain never sees a ledger entry. Storage is behind `KvStore`.
 */

import type { TelegramIdentity } from "./backend";
import type { Lang } from "./i18n";
import { applyPaymentBody, isRenewal, parseApplyPayment, type PaymentOutcome } from "./payments";
import { errText, isRetryable, MAX_ATTEMPTS, type KvStore } from "./queue";
import type { TgSuccessfulPayment } from "./telegram";

export const LEDGER_PREFIX = "pay:";
const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
/** Wait before ledger retry n+1 after n failed ones; the last value repeats. */
export const LEDGER_BACKOFF_MS: readonly number[] = [
  1 * MINUTE_MS,
  5 * MINUTE_MS,
  30 * MINUTE_MS,
  2 * HOUR_MS,
  6 * HOUR_MS,
  12 * HOUR_MS,
];
/** A payment still failing this long after its first failure is given up (logged, dropped). */
export const LEDGER_GIVE_UP_MS = 30 * 24 * HOUR_MS;

/** Everything needed to apply a payment later and to message the payer about it. */
export interface PaymentToApply {
  chargeId: string;
  who: TelegramIdentity;
  lang: Lang;
  renewal: boolean;
  /** The exact `apply_payment` request body — replayed verbatim. */
  body: Record<string, unknown>;
}

export interface ParkedPayment extends PaymentToApply {
  /** Failed ledger retries so far (0 when parked). */
  attempts: number;
  notBefore: number;
  firstFailedAt: number;
  lastError: string;
  /** Set once `apply_payment` answered — from then on only the message is owed. */
  outcome: PaymentOutcome | null;
}

export type ApplyFn = (body: Record<string, unknown>) => Promise<Record<string, unknown>>;
/** Tell the payer the outcome. A throw keeps the entry (retryable) or drops it (definite "no"). */
export type NotifyFn = (p: ParkedPayment, outcome: PaymentOutcome) => Promise<void>;

export function ledgerKey(chargeId: string): string {
  return `${LEDGER_PREFIX}${chargeId}`;
}

export function ledgerBackoffMs(failedRetries: number): number {
  const i = Math.min(Math.max(0, failedRetries), LEDGER_BACKOFF_MS.length - 1);
  return LEDGER_BACKOFF_MS[i] ?? 12 * HOUR_MS;
}

export function paymentToApply(p: TgSuccessfulPayment, who: TelegramIdentity, lang: Lang): PaymentToApply {
  return {
    chargeId: p.telegram_payment_charge_id,
    who,
    lang,
    renewal: isRenewal(p),
    body: applyPaymentBody(who.telegramUserId, p),
  };
}

export class PaymentLedger {
  constructor(private readonly store: KvStore) {}

  /** Park a payment for later retries. An already parked charge keeps its schedule and history. */
  async park(p: PaymentToApply, error: unknown, now: number): Promise<"parked" | "exists"> {
    const key = ledgerKey(p.chargeId);
    if ((await this.store.get<ParkedPayment>(key)) !== undefined) return "exists";
    const entry: ParkedPayment = {
      ...p,
      attempts: 0,
      notBefore: now + ledgerBackoffMs(0),
      firstFailedAt: now,
      lastError: errText(error),
      outcome: null,
    };
    await this.store.put<ParkedPayment>(key, entry);
    console.error(`[gisti-tg] apply_payment for charge ${p.chargeId} parked in the retry ledger: ${entry.lastError}`);
    return "parked";
  }

  async entries(): Promise<Map<string, ParkedPayment>> {
    return this.store.list<ParkedPayment>({ prefix: LEDGER_PREFIX });
  }

  /** The earliest `notBefore` among parked payments, or null when the ledger is empty. */
  async nextDue(): Promise<number | null> {
    let min: number | null = null;
    for (const p of (await this.entries()).values()) if (min === null || p.notBefore < min) min = p.notBefore;
    return min;
  }

  /** Retry every parked payment that is due. One entry's failure never blocks the others. */
  async retryDue(now: () => number, apply: ApplyFn, notify: NotifyFn): Promise<void> {
    for (const [key, p] of await this.entries()) {
      if (p.notBefore > now()) continue;
      await this.retryOne(key, p, now, apply, notify);
    }
  }

  private async retryOne(key: string, p: ParkedPayment, now: () => number, apply: ApplyFn, notify: NotifyFn): Promise<void> {
    try {
      if (p.outcome === null) {
        p.outcome = parseApplyPayment(await apply(p.body), p.chargeId);
        await this.store.put(key, p);
      }
      await notify(p, p.outcome);
      await this.store.delete(key);
      return;
    } catch (e) {
      const what = p.outcome === null ? "apply_payment" : "the payer notification";
      if (!isRetryable(e)) {
        console.error(`[gisti-tg] ${what} for parked charge ${p.chargeId} refused, dropped from the ledger: ${errText(e)}`);
        await this.store.delete(key);
        return;
      }
      const t = now();
      p.attempts += 1;
      p.lastError = errText(e);
      if (t - p.firstFailedAt >= LEDGER_GIVE_UP_MS) {
        console.error(
          `[gisti-tg] giving up on charge ${p.chargeId}: ${what} still failing after ${p.attempts} ledger retries over 30 days: ${p.lastError}`,
        );
        await this.store.delete(key);
        return;
      }
      p.notBefore = t + ledgerBackoffMs(p.attempts);
      await this.store.put(key, p);
      console.warn(`[gisti-tg] ${what} for parked charge ${p.chargeId} failed (ledger retry ${p.attempts}): ${p.lastError}`);
    }
  }
}

/**
 * Queue-path apply of a `successful_payment`. While the queue still has attempts left, a retryable
 * failure is rethrown (the queue retries in seconds); on the last attempt it is parked in the ledger
 * instead of being dropped. A definite "no" (4xx) is logged. Both → "pending" (the payer is told the
 * payment arrived and where to write if Premium does not follow).
 */
export async function applyOrPark(
  ledger: PaymentLedger,
  p: PaymentToApply,
  apply: ApplyFn,
  attempt: number,
  now: number,
): Promise<PaymentOutcome> {
  try {
    return parseApplyPayment(await apply(p.body), p.chargeId);
  } catch (e) {
    if (!isRetryable(e)) {
      console.error(`[gisti-tg] apply_payment refused for charge ${p.chargeId}: ${errText(e)}`);
      return { kind: "pending" };
    }
    if (attempt < MAX_ATTEMPTS) throw e;
    await ledger.park(p, e, now);
    return { kind: "pending" };
  }
}
