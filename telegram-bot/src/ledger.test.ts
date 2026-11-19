import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { InternalApiError, type TelegramIdentity } from "./backend";
import {
  applyOrPark,
  LEDGER_BACKOFF_MS,
  LEDGER_GIVE_UP_MS,
  ledgerBackoffMs,
  ledgerKey,
  PaymentLedger,
  paymentToApply,
  type NotifyFn,
  type ParkedPayment,
  type PaymentToApply,
} from "./ledger";
import { PREMIUM_PAYLOAD } from "./payments";
import { MAX_ATTEMPTS, queueKey, UpdateQueue, type KvStore } from "./queue";
import { TelegramError, type TgSuccessfulPayment, type TgUpdate } from "./telegram";

/** In-memory stand-in for Durable Object storage (sorted list(), like the real one). */
class MemoryStore implements KvStore {
  readonly data = new Map<string, unknown>();
  async get<T>(key: string): Promise<T | undefined> {
    return structuredClone(this.data.get(key)) as T | undefined;
  }
  async put<T>(key: string, value: T): Promise<void> {
    this.data.set(key, structuredClone(value));
  }
  async delete(key: string): Promise<boolean> {
    return this.data.delete(key);
  }
  async list<T>(options: { prefix: string; limit?: number }): Promise<Map<string, T>> {
    const keys = [...this.data.keys()].filter((k) => k.startsWith(options.prefix)).sort();
    const out = new Map<string, T>();
    for (const k of keys.slice(0, options.limit ?? keys.length)) out.set(k, structuredClone(this.data.get(k)) as T);
    return out;
  }
}

const MIN = 60_000;
const HOUR = 60 * MIN;
const who: TelegramIdentity = { telegramUserId: 42, chatId: 42, username: "u", firstName: "U", languageCode: "ru" };
const successfulPayment = (charge = "stxCHARGE1", over: Partial<TgSuccessfulPayment> = {}): TgSuccessfulPayment => ({
  currency: "XTR",
  total_amount: 100,
  invoice_payload: PREMIUM_PAYLOAD,
  is_recurring: true,
  is_first_recurring: true,
  subscription_expiration_date: 1_790_000_000,
  telegram_payment_charge_id: charge,
  ...over,
});
const payment = (charge = "stxCHARGE1"): PaymentToApply => paymentToApply(successfulPayment(charge), who, "ru");
const down = () => new InternalApiError(503, "unavailable", "apply_payment");
const applied = (untilMs = Date.UTC(2026, 11, 19)) => ({
  success: true,
  applied: true,
  status: "applied",
  telegram_premium_until_ms: untilMs,
  duplicate: false,
});

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  vi.spyOn(console, "warn").mockImplementation(() => {});
});
afterEach(() => vi.restoreAllMocks());

async function stored(store: MemoryStore, charge = "stxCHARGE1"): Promise<ParkedPayment | undefined> {
  return store.get<ParkedPayment>(ledgerKey(charge));
}

describe("PaymentLedger.park", () => {
  it("persists everything needed to retry and to message the payer, due in 1 min", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    expect(await ledger.park(payment(), down(), 1000)).toBe("parked");
    const e = await stored(store);
    expect(e).toMatchObject({
      chargeId: "stxCHARGE1",
      who,
      lang: "ru",
      renewal: false,
      attempts: 0,
      notBefore: 1000 + MIN,
      firstFailedAt: 1000,
      outcome: null,
    });
    expect(e?.body).toMatchObject({ telegram_user_id: 42, telegram_payment_charge_id: "stxCHARGE1", total_amount: 100 });
    expect(e?.lastError).toContain("503");
    expect(ledgerKey("stxCHARGE1")).toBe("pay:stxCHARGE1");
  });

  it("does not reset an already parked charge", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    await ledger.park(payment(), down(), 1000);
    expect(await ledger.park(payment(), down(), 99_999)).toBe("exists");
    expect((await stored(store))?.firstFailedAt).toBe(1000);
  });

  it("nextDue is the earliest notBefore, null when empty", async () => {
    const ledger = new PaymentLedger(new MemoryStore());
    expect(await ledger.nextDue()).toBeNull();
    await ledger.park(payment("b"), down(), 5000);
    await ledger.park(payment("a"), down(), 9000);
    expect(await ledger.nextDue()).toBe(5000 + MIN);
  });
});

describe("PaymentLedger.retryDue", () => {
  it("leaves a not-yet-due entry alone and retries it once due", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    await ledger.park(payment(), down(), 0);
    const apply = vi.fn(async () => applied());
    const notify = vi.fn(async () => {});

    await ledger.retryDue(() => MIN - 1, apply, notify);
    expect(apply).not.toHaveBeenCalled();

    await ledger.retryDue(() => MIN, apply, notify);
    expect(apply).toHaveBeenCalledTimes(1);
    expect(apply).toHaveBeenCalledWith(payment().body);
  });

  it("on success notifies with the outcome and deletes the entry", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    await ledger.park(payment(), down(), 0);
    const notify = vi.fn<NotifyFn>(async () => {});
    await ledger.retryDue(() => MIN, async () => applied(123), notify);
    expect(notify).toHaveBeenCalledTimes(1);
    expect(notify.mock.calls[0]?.[1]).toEqual({ kind: "applied", untilMs: 123 });
    expect(await stored(store)).toBeUndefined();
    expect(await ledger.nextDue()).toBeNull();
  });

  it("an unassigned answer (no Gisti link) is reported as unlinked, then deleted", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    await ledger.park(payment(), down(), 0);
    const notify = vi.fn<NotifyFn>(async () => {});
    await ledger.retryDue(
      () => MIN,
      async () => ({ success: true, applied: false, status: "unassigned", telegram_premium_until_ms: null, duplicate: false }),
      notify,
    );
    expect(notify.mock.calls[0]?.[1]).toEqual({ kind: "unlinked" });
    expect(await stored(store)).toBeUndefined();
  });

  it("backs off 1 min, 5 min, 30 min, 2 h, 6 h, then every 12 h", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    let clock = 0;
    await ledger.park(payment(), down(), clock);
    const gaps: number[] = [(await stored(store))!.notBefore - clock];
    const apply = vi.fn(async () => Promise.reject(down()));
    for (let i = 0; i < 7; i++) {
      clock = (await stored(store))!.notBefore;
      await ledger.retryDue(() => clock, apply, vi.fn(async () => {}));
      gaps.push((await stored(store))!.notBefore - clock);
    }
    expect(gaps).toEqual([MIN, 5 * MIN, 30 * MIN, 2 * HOUR, 6 * HOUR, 12 * HOUR, 12 * HOUR, 12 * HOUR]);
    expect(apply).toHaveBeenCalledTimes(7);
    expect((await stored(store))?.attempts).toBe(7);
    expect(ledgerBackoffMs(0)).toBe(LEDGER_BACKOFF_MS[0]);
    expect(ledgerBackoffMs(100)).toBe(12 * HOUR);
  });

  it("drops an entry on a non-retryable (4xx) answer, logging the charge id", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    await ledger.park(payment(), down(), 0);
    const notify = vi.fn(async () => {});
    await ledger.retryDue(
      () => MIN,
      async () => Promise.reject(new InternalApiError(400, "invalid_payment", "apply_payment")),
      notify,
    );
    expect(notify).not.toHaveBeenCalled();
    expect(await stored(store)).toBeUndefined();
    expect(vi.mocked(console.error).mock.calls.some(([m]) => String(m).includes("stxCHARGE1"))).toBe(true);
  });

  it("keeps retrying until 30 days after the first failure, then gives up and drops it", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    await ledger.park(payment(), down(), 0);
    const apply = vi.fn(async () => Promise.reject(down()));

    await ledger.retryDue(() => LEDGER_GIVE_UP_MS - 1, apply, vi.fn(async () => {}));
    expect(await stored(store)).toBeDefined();

    const e = (await stored(store))!;
    await store.put(ledgerKey("stxCHARGE1"), { ...e, notBefore: LEDGER_GIVE_UP_MS });
    await ledger.retryDue(() => LEDGER_GIVE_UP_MS, apply, vi.fn(async () => {}));
    expect(await stored(store)).toBeUndefined();
    expect(apply).toHaveBeenCalledTimes(2);
    expect(
      vi.mocked(console.error).mock.calls.some(([m]) => String(m).includes("giving up") && String(m).includes("stxCHARGE1")),
    ).toBe(true);
  });

  it("after apply succeeded, a failed message is retried WITHOUT calling apply_payment again", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    await ledger.park(payment(), down(), 0);
    const apply = vi.fn(async () => applied(555));
    const notify = vi
      .fn<NotifyFn>()
      .mockRejectedValueOnce(new TelegramError("sendMessage", 502, "Bad Gateway"))
      .mockResolvedValueOnce(undefined);

    await ledger.retryDue(() => MIN, apply, notify);
    const kept = await stored(store);
    expect(kept?.outcome).toEqual({ kind: "applied", untilMs: 555 });
    expect(kept?.notBefore).toBe(MIN + 5 * MIN);

    await ledger.retryDue(() => kept!.notBefore, apply, notify);
    expect(apply).toHaveBeenCalledTimes(1);
    expect(notify).toHaveBeenCalledTimes(2);
    expect(await stored(store)).toBeUndefined();
  });

  it("one failing entry does not block another due one", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    await ledger.park(payment("a"), down(), 0);
    await ledger.park(payment("b"), down(), 0);
    const notify = vi.fn(async () => {});
    await ledger.retryDue(
      () => MIN,
      async (body) => (body["telegram_payment_charge_id"] === "a" ? Promise.reject(down()) : applied()),
      notify,
    );
    expect(await stored(store, "a")).toBeDefined();
    expect(await stored(store, "b")).toBeUndefined();
    expect(notify).toHaveBeenCalledTimes(1);
  });
});

describe("queue + ledger keys", () => {
  it("the queue drain never sees ledger entries", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    const queue = new UpdateQueue(store);
    await ledger.park(payment(), down(), 0);
    expect(await queue.head()).toBeNull();

    const u: TgUpdate = { update_id: 7, message: { message_id: 7, chat: { id: 42, type: "private" }, from: { id: 42 }, text: "x" } };
    await queue.enqueue(u, 0);
    const process = vi.fn(async () => {});
    expect(await queue.drain(() => 0, process, vi.fn(async () => {}))).toBeNull();
    expect(process).toHaveBeenCalledTimes(1);
    expect(await stored(store)).toBeDefined();
    expect(ledgerKey("x").startsWith("q:")).toBe(false);
  });
});

describe("applyOrPark (the successful_payment queue path)", () => {
  it("returns the outcome when apply_payment answers", async () => {
    const ledger = new PaymentLedger(new MemoryStore());
    expect(await applyOrPark(ledger, payment(), async () => applied(77), 1, 0)).toEqual({ kind: "applied", untilMs: 77 });
    expect(
      await applyOrPark(ledger, payment(), async () => ({ success: true, applied: false, status: "unassigned" }), 1, 0),
    ).toEqual({ kind: "unlinked" });
  });

  it("rethrows a transient failure while the queue has attempts left (nothing parked)", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    const err = down();
    await expect(applyOrPark(ledger, payment(), async () => Promise.reject(err), MAX_ATTEMPTS - 1, 0)).rejects.toBe(err);
    expect(await stored(store)).toBeUndefined();
  });

  it("parks on the last attempt instead of dropping, and reports pending", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    const out = await applyOrPark(ledger, payment(), async () => Promise.reject(down()), MAX_ATTEMPTS, 1000);
    expect(out).toEqual({ kind: "pending" });
    expect(await stored(store)).toMatchObject({ chargeId: "stxCHARGE1", notBefore: 1000 + MIN, firstFailedAt: 1000 });
  });

  it("does not park a definite 'no' (4xx) — logged, pending", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    const out = await applyOrPark(
      ledger,
      payment(),
      async () => Promise.reject(new InternalApiError(400, "invalid_payment", "apply_payment")),
      1,
      0,
    );
    expect(out).toEqual({ kind: "pending" });
    expect(await stored(store)).toBeUndefined();
  });

  it("end to end: a payment the Cloud Function refuses for every queue attempt ends up parked, not lost", async () => {
    const store = new MemoryStore();
    const ledger = new PaymentLedger(store);
    const queue = new UpdateQueue(store);
    const sp = successfulPayment("stxE2E", { is_first_recurring: false });
    const u: TgUpdate = {
      update_id: 900,
      message: { message_id: 1, chat: { id: 42, type: "private" }, from: { id: 42, language_code: "hi" }, successful_payment: sp },
    };
    await queue.enqueue(u, 0);
    let clock = 0;
    const replies: string[] = [];
    const process = async (upd: TgUpdate, attempt: number) => {
      const p = paymentToApply(upd.message!.successful_payment!, { ...who, languageCode: "hi" }, "hi");
      const o = await applyOrPark(ledger, p, async () => Promise.reject(down()), attempt, clock);
      replies.push(o.kind);
    };
    const onFail = vi.fn(async () => {});
    for (let i = 0; i < MAX_ATTEMPTS; i++) {
      const next = await queue.drain(() => clock, process, onFail);
      if (next !== null) clock = next;
    }
    expect(store.data.has(queueKey(900))).toBe(false);
    expect(onFail).not.toHaveBeenCalled();
    expect(replies).toEqual(["pending"]); // told once, on the parking attempt
    const parked = await stored(store, "stxE2E");
    expect(parked).toMatchObject({ lang: "hi", renewal: true, attempts: 0 });
    expect(await ledger.nextDue()).toBe(clock + MIN);
  });
});
