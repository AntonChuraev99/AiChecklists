/** Unit coverage for the launch-hardening primitives (security.ts) with a fake in-memory KV. */
import { describe, expect, it } from "vitest";
import {
  consumeAuthState,
  rateLimit,
  stableRequestId,
  storeAuthState,
  type KvLike,
} from "./security";

class FakeKV implements KvLike {
  store = new Map<string, string>();
  async get(k: string): Promise<string | null> {
    return this.store.has(k) ? this.store.get(k)! : null;
  }
  async put(k: string, v: string): Promise<void> {
    this.store.set(k, v);
  }
  async delete(k: string): Promise<void> {
    this.store.delete(k);
  }
}

describe("rateLimit (fixed window)", () => {
  it("allows up to the limit then denies within the window", async () => {
    const kv = new FakeKV();
    const now = 1_000_000;
    for (let i = 0; i < 3; i++) {
      const r = await rateLimit(kv, "u", 3, 60, now + i); // small drift, same window
      expect(r.allowed).toBe(true);
      expect(r.remaining).toBe(2 - i);
    }
    const denied = await rateLimit(kv, "u", 3, 60, now + 3);
    expect(denied.allowed).toBe(false);
    expect(denied.remaining).toBe(0);
    expect(denied.resetSeconds).toBeGreaterThan(0);
  });

  it("resets after the window elapses", async () => {
    const kv = new FakeKV();
    const now = 5_000_000;
    for (let i = 0; i < 2; i++) await rateLimit(kv, "u", 2, 60, now);
    expect((await rateLimit(kv, "u", 2, 60, now)).allowed).toBe(false);
    expect((await rateLimit(kv, "u", 2, 60, now + 61_000)).allowed).toBe(true);
  });

  it("keys are independent", async () => {
    const kv = new FakeKV();
    const now = 1_000;
    await rateLimit(kv, "a", 1, 60, now);
    expect((await rateLimit(kv, "a", 1, 60, now)).allowed).toBe(false);
    expect((await rateLimit(kv, "b", 1, 60, now)).allowed).toBe(true);
  });
});

describe("stableRequestId (retry dedup)", () => {
  it("is identical for the same parts within the same time bucket", async () => {
    const a = await stableRequestId(["u", "create", "x"], 60_000, 1_000);
    const b = await stableRequestId(["u", "create", "x"], 60_000, 30_000); // same bucket (0)
    expect(a).toBe(b);
    expect(a).toHaveLength(32);
  });

  it("differs across buckets and across parts", async () => {
    const base = await stableRequestId(["u", "create", "x"], 60_000, 1_000);
    const nextBucket = await stableRequestId(["u", "create", "x"], 60_000, 61_000); // bucket 1
    const otherArgs = await stableRequestId(["u", "create", "y"], 60_000, 1_000);
    expect(nextBucket).not.toBe(base);
    expect(otherArgs).not.toBe(base);
  });
});

describe("OAuth state (opaque, one-time, KV)", () => {
  it("round-trips the request once and rejects replay / forged nonces", async () => {
    const kv = new FakeKV();
    const req = { clientId: "abc", redirectUri: "https://x/cb", scope: ["read"] };
    const nonce = await storeAuthState(kv, req);
    expect(typeof nonce).toBe("string");
    expect(await consumeAuthState(kv, nonce)).toEqual(req);
    expect(await consumeAuthState(kv, nonce)).toBeNull(); // one-time — already consumed
    expect(await consumeAuthState(kv, "forged")).toBeNull();
    expect(await consumeAuthState(kv, "")).toBeNull();
  });
});
