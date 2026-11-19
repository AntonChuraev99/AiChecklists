/**
 * The per-chat update queue that lives in the Durable Object's storage.
 *
 *  - `enqueue` (webhook path, must be fast): dedupe by `update_id` against a bounded "seen" list,
 *    then persist the update under a zero-padded key so `list()` returns them in update order.
 *  - `drain` (alarm path): process the head, one at a time, in order. The attempt counter is
 *    persisted BEFORE the work, so even an isolate crash mid-update counts as an attempt.
 *    A retryable failure keeps the entry with a backoff (`notBefore`) and stops the drain to keep
 *    ordering; a non-retryable one, or the MAX_ATTEMPTS-th, drops the entry and reports it —
 *    one poisoned update can never wedge the chat.
 *
 * Storage is behind `KvStore` so the whole thing is unit-testable without workerd.
 */

import { CfError } from "../../mcp-server/src/cf";
import { InternalApiError } from "./backend";
import { FileTooLargeError, TelegramError, type TgUpdate } from "./telegram";

export interface KvStore {
  get<T>(key: string): Promise<T | undefined>;
  put<T>(key: string, value: T): Promise<void>;
  delete(key: string): Promise<boolean>;
  list<T>(options: { prefix: string; limit?: number }): Promise<Map<string, T>>;
}

export const QUEUE_PREFIX = "q:";
export const SEEN_KEY = "seen";
/** How many recent update_ids are remembered for dedupe (Telegram retries within minutes). */
export const SEEN_MAX = 500;
export const MAX_ATTEMPTS = 3;

export interface QueueEntry {
  update: TgUpdate;
  attempts: number;
  notBefore: number;
}

export function queueKey(updateId: number): string {
  return `${QUEUE_PREFIX}${String(updateId).padStart(15, "0")}`;
}

/** Backoff before attempt n+1 after n failed attempts: 2 s, 8 s, … */
export function backoffMs(attempts: number): number {
  return 2000 * 4 ** Math.max(0, attempts - 1);
}

/**
 * Transient failures are worth another attempt; a definite "no" from an API is not.
 * Telegram 4xx (bad request, blocked by the user), internal-API 4xx (token gone, no account),
 * an oversized file — retrying cannot change the answer.
 */
export function isRetryable(e: unknown): boolean {
  if (e instanceof FileTooLargeError) return false;
  if (e instanceof TelegramError) return e.code === 0 || e.code === 429 || e.code >= 500;
  if (e instanceof InternalApiError) return e.status === 0 || e.status === 429 || e.status >= 500;
  if (e instanceof CfError) return e.status === 0 || e.status >= 500;
  return true; // Firestore / network / unknown — assume transient
}

export type Processor = (update: TgUpdate, attempt: number) => Promise<void>;
export type FailureHandler = (update: TgUpdate, error: unknown, attempts: number) => Promise<void>;

export class UpdateQueue {
  constructor(private readonly store: KvStore) {}

  /** Persist an update unless it was seen before. Safe against concurrent calls (DO input gates). */
  async enqueue(update: TgUpdate, now: number): Promise<"queued" | "duplicate"> {
    const id = update.update_id;
    const seen = (await this.store.get<number[]>(SEEN_KEY)) ?? [];
    if (seen.includes(id)) return "duplicate";
    seen.push(id);
    if (seen.length > SEEN_MAX) seen.splice(0, seen.length - SEEN_MAX);
    await this.store.put(SEEN_KEY, seen);
    await this.store.put<QueueEntry>(queueKey(id), { update, attempts: 0, notBefore: now });
    return "queued";
  }

  async head(): Promise<[string, QueueEntry] | null> {
    const m = await this.store.list<QueueEntry>({ prefix: QUEUE_PREFIX, limit: 1 });
    const first = m.entries().next();
    return first.done ? null : first.value;
  }

  /**
   * Process queued updates in order until the queue is empty (→ null) or the head must wait for a
   * retry (→ the time it becomes due). Updates enqueued while draining are picked up too.
   */
  async drain(now: () => number, process: Processor, onFinalFailure: FailureHandler): Promise<number | null> {
    for (;;) {
      const head = await this.head();
      if (!head) return null;
      const [key, entry] = head;
      if (entry.notBefore > now()) return entry.notBefore;

      if (entry.attempts >= MAX_ATTEMPTS) {
        // Crashed MAX_ATTEMPTS times without reaching the catch below (isolate killed mid-update).
        await this.store.delete(key);
        await this.safeFailure(onFinalFailure, entry.update, new Error("max attempts exceeded"), entry.attempts);
        continue;
      }

      entry.attempts += 1;
      await this.store.put(key, entry);
      try {
        await process(entry.update, entry.attempts);
        await this.store.delete(key);
      } catch (e) {
        if (isRetryable(e) && entry.attempts < MAX_ATTEMPTS) {
          entry.notBefore = now() + backoffMs(entry.attempts);
          await this.store.put(key, entry);
          console.warn(
            `[gisti-tg] update ${entry.update.update_id} failed (attempt ${entry.attempts}), retrying: ${errText(e)}`,
          );
          return entry.notBefore;
        }
        await this.store.delete(key);
        await this.safeFailure(onFinalFailure, entry.update, e, entry.attempts);
      }
    }
  }

  private async safeFailure(handler: FailureHandler, update: TgUpdate, e: unknown, attempts: number): Promise<void> {
    try {
      await handler(update, e, attempts);
    } catch (inner) {
      console.error(`[gisti-tg] failure handler for update ${update.update_id} threw: ${errText(inner)}`);
    }
  }
}

export function errText(e: unknown): string {
  return e instanceof Error ? `${e.name}: ${e.message}` : String(e);
}
