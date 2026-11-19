import { describe, expect, it } from "vitest";
import { deriveKey, INTERNAL_KEY_LABEL, timingSafeEqual, WEBHOOK_SECRET_LABEL } from "./keys";

// Shared vector with the Python side (contract "Derived keys") — DO NOT change one side alone.
const TOKEN = "123456:TEST-TOKEN";

describe("deriveKey", () => {
  it("matches the pinned internal-key vector", async () => {
    expect(await deriveKey(TOKEN, INTERNAL_KEY_LABEL)).toBe("7iBbDTERoGTkAbeNNyKajDJfOFWoSO4V4bhvjPRMEjg");
  });

  it("matches the pinned webhook-secret vector", async () => {
    expect(await deriveKey(TOKEN, WEBHOOK_SECRET_LABEL)).toBe("0F_V7JjyEQfts-FqUHjJd6ceTdTdOC2jWK22GPZeawQ");
  });

  it("is base64url without padding and fits Telegram's secret_token charset", async () => {
    const k = await deriveKey(TOKEN, WEBHOOK_SECRET_LABEL);
    expect(k).toMatch(/^[A-Za-z0-9_-]{43}$/);
  });

  it("changes with the token", async () => {
    expect(await deriveKey("654321:OTHER", INTERNAL_KEY_LABEL)).not.toBe(
      await deriveKey(TOKEN, INTERNAL_KEY_LABEL),
    );
  });
});

describe("timingSafeEqual", () => {
  it("accepts equal strings and rejects different or differently-sized ones", () => {
    expect(timingSafeEqual("abc", "abc")).toBe(true);
    expect(timingSafeEqual("abc", "abd")).toBe(false);
    expect(timingSafeEqual("abc", "abcd")).toBe(false);
    expect(timingSafeEqual("", "a")).toBe(false);
  });
});
