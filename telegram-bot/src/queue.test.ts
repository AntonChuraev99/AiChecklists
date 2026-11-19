import { describe, expect, it, vi } from "vitest";
import { CfError } from "../../mcp-server/src/cf";
import { InternalApiError } from "./backend";
import { isRetryable, MAX_ATTEMPTS, queueKey, UpdateQueue, type KvStore } from "./queue";
import { FileTooLargeError, TelegramError, type TgUpdate } from "./telegram";

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

function update(id: number, text = "x"): TgUpdate {
  return { update_id: id, message: { message_id: id, chat: { id: 1, type: "private" }, from: { id: 1 }, text } };
}

describe("UpdateQueue.enqueue — dedupe by update_id", () => {
  it("processes a duplicate update_id only once", async () => {
    const q = new UpdateQueue(new MemoryStore());
    expect(await q.enqueue(update(10), 0)).toBe("queued");
    expect(await q.enqueue(update(10), 0)).toBe("duplicate");

    const process = vi.fn(async () => {});
    await q.drain(() => 0, process, vi.fn(async () => {}));
    expect(process).toHaveBeenCalledTimes(1);
  });

  it("still rejects a redelivery after the original was processed and removed", async () => {
    const q = new UpdateQueue(new MemoryStore());
    await q.enqueue(update(11), 0);
    const process = vi.fn(async () => {});
    await q.drain(() => 0, process, vi.fn(async () => {}));
    expect(await q.enqueue(update(11), 0)).toBe("duplicate");
    await q.drain(() => 0, process, vi.fn(async () => {}));
    expect(process).toHaveBeenCalledTimes(1);
  });
});

describe("UpdateQueue.drain", () => {
  it("processes in update_id order regardless of arrival order", async () => {
    const q = new UpdateQueue(new MemoryStore());
    await q.enqueue(update(1000), 0);
    await q.enqueue(update(9), 0);
    await q.enqueue(update(50), 0);
    const seen: number[] = [];
    await q.drain(() => 0, async (u) => void seen.push(u.update_id), vi.fn(async () => {}));
    expect(seen).toEqual([9, 50, 1000]);
  });

  it("retries a transient failure with backoff, then drops it after MAX_ATTEMPTS and moves on", async () => {
    const store = new MemoryStore();
    const q = new UpdateQueue(store);
    await q.enqueue(update(1), 0);
    await q.enqueue(update(2), 0);

    let clock = 0;
    const done: number[] = [];
    const process = vi.fn(async (u: TgUpdate) => {
      if (u.update_id === 1) throw new Error("firestore 503");
      done.push(u.update_id);
    });
    const onFail = vi.fn(async () => {});

    // attempt 1 fails → waits; update 2 must NOT jump the queue
    const next1 = await q.drain(() => clock, process, onFail);
    expect(next1).toBeGreaterThan(0);
    expect(done).toEqual([]);

    clock = next1!;
    const next2 = await q.drain(() => clock, process, onFail);
    expect(next2).toBeGreaterThan(clock);

    clock = next2!;
    const next3 = await q.drain(() => clock, process, onFail);
    expect(next3).toBeNull();

    expect(process.mock.calls.filter(([u]) => u.update_id === 1)).toHaveLength(MAX_ATTEMPTS);
    expect(onFail).toHaveBeenCalledTimes(1);
    expect(done).toEqual([2]);
    expect(store.data.has(queueKey(1))).toBe(false);
  });

  it("drops a non-retryable failure immediately and reports it", async () => {
    const q = new UpdateQueue(new MemoryStore());
    await q.enqueue(update(5), 0);
    const onFail = vi.fn(async () => {});
    const process = vi.fn(async () => {
      throw new TelegramError("sendMessage", 400, "Bad Request: chat not found");
    });
    expect(await q.drain(() => 0, process, onFail)).toBeNull();
    expect(process).toHaveBeenCalledTimes(1);
    expect(onFail).toHaveBeenCalledTimes(1);
  });

  it("drops an entry that already burned MAX_ATTEMPTS (crashed isolate) without running it again", async () => {
    const store = new MemoryStore();
    const q = new UpdateQueue(store);
    await store.put(queueKey(7), { update: update(7), attempts: MAX_ATTEMPTS, notBefore: 0 });
    const process = vi.fn(async () => {});
    const onFail = vi.fn(async () => {});
    await q.drain(() => 0, process, onFail);
    expect(process).not.toHaveBeenCalled();
    expect(onFail).toHaveBeenCalledTimes(1);
  });

  it("keeps draining when the failure handler itself throws", async () => {
    const q = new UpdateQueue(new MemoryStore());
    await q.enqueue(update(1), 0);
    await q.enqueue(update(2), 0);
    const done: number[] = [];
    await q.drain(
      () => 0,
      async (u) => {
        if (u.update_id === 1) throw new FileTooLargeError();
        done.push(u.update_id);
      },
      async () => {
        throw new Error("telegram down");
      },
    );
    expect(done).toEqual([2]);
  });
});

describe("isRetryable", () => {
  it("retries transient failures only", () => {
    expect(isRetryable(new Error("firestore 503"))).toBe(true);
    expect(isRetryable(new TelegramError("sendMessage", 429, "Too Many Requests"))).toBe(true);
    expect(isRetryable(new TelegramError("sendMessage", 502, "Bad Gateway"))).toBe(true);
    expect(isRetryable(new TelegramError("sendMessage", 403, "Forbidden: bot was blocked by the user"))).toBe(false);
    expect(isRetryable(new InternalApiError(503, "bot_not_configured", "unlink"))).toBe(true);
    expect(isRetryable(new InternalApiError(401, "unauthorized", "unlink"))).toBe(false);
    expect(isRetryable(new CfError(402, "Not enough credits", "generate_checklist"))).toBe(false);
    expect(isRetryable(new FileTooLargeError())).toBe(false);
  });
});
