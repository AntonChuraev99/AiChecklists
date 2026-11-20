import { describe, expect, it } from "vitest";
import {
  byteLength,
  CALLBACK_MAX_BYTES,
  decodeCallback,
  emptyRegistry,
  encodeCallback,
  keyFor,
  MAX_KEYS,
  resolveKey,
  type Callback,
} from "./callback";
import { nextDraftId } from "./draft";
import { renderItemsPage, renderListsPage } from "./views";
import { t } from "./i18n";
import { buildAiChecklist } from "../../mcp-server/src/mutate";

const UUID = "123e4567-e89b-12d3-a456-426614174000";

describe("callback_data", () => {
  it("round-trips every callback kind", () => {
    const all: Callback[] = [
      { t: "draft", draftId: "mi4x9z2a", action: "free" },
      { t: "draft", draftId: "mi4x9z2a", action: "ai" },
      { t: "draft", draftId: "mi4x9z2a", action: "cancel" },
      { t: "lists", page: 3 },
      { t: "open", key: "1z", page: 0 },
      { t: "toggle", key: "abc", checked: true, page: 12 },
      { t: "toggle", key: "abc", checked: false, page: 0 },
      { t: "noop" },
    ];
    for (const cb of all) expect(decodeCallback(encodeCallback(cb))).toEqual(cb);
  });

  it("stays within 64 bytes in the worst case (huge key, max page, far-future draft id)", () => {
    const hugeKey = Number.MAX_SAFE_INTEGER.toString(36);
    const farDraft = nextDraftId(0, 8.64e15).id; // max JS Date
    const worst: Callback[] = [
      { t: "toggle", key: hugeKey, checked: true, page: 9999 },
      { t: "open", key: hugeKey, page: 9999 },
      { t: "lists", page: 9999 },
      { t: "draft", draftId: farDraft, action: "cancel" },
    ];
    for (const cb of worst) {
      const data = encodeCallback(cb);
      expect(byteLength(data)).toBeLessThanOrEqual(CALLBACK_MAX_BYTES);
      expect(decodeCallback(data)).toEqual(cb);
    }
  });

  it("rejects foreign or malformed data", () => {
    for (const bad of [undefined, "", "x", "t:abc:2:0", "d:abc:z", `o:${UUID}:0`, "l:-1", "t:ABC:1:0"]) {
      expect(decodeCallback(bad)).toBeNull();
    }
  });

  it("every button a real view renders fits the limit (UUID ids, long text)", () => {
    const tree = Array.from({ length: 25 }, (_, i) => ({ text: `Item number ${i} `.repeat(10) }));
    const c = buildAiChecklist("A".repeat(300), [{ text: "Folder", type: "folder" as const, children: tree }], 1);
    const keys = emptyRegistry();
    const views = [
      renderListsPage(Array.from({ length: 20 }, () => c), 1, t("en"), keys),
      renderItemsPage(c, 1, t("hi"), keys),
    ];
    for (const v of views) {
      for (const row of v.keyboard.inline_keyboard) {
        for (const b of row) {
          if ("callback_data" in b) expect(byteLength(b.callback_data)).toBeLessThanOrEqual(CALLBACK_MAX_BYTES);
        }
      }
    }
  });
});

describe("callback_data — Phase 2 (AI chat, time zone)", () => {
  it("round-trips the chat and time-zone kinds within 64 bytes", () => {
    const all: Callback[] = [
      { t: "draft", draftId: "mi4x9z2a", action: "chat" },
      { t: "confirm", turn: 1, yes: true },
      { t: "confirm", turn: 999_999_999_999, yes: false },
      { t: "option", turn: 999_999_999_999, index: 5 },
      { t: "option", turn: 3, index: 0 },
      { t: "endChat" },
      { t: "tz", offset: 330 },
      { t: "tz", offset: -480 },
      { t: "tz", offset: 0 },
    ];
    for (const cb of all) {
      const data = encodeCallback(cb);
      expect(byteLength(data)).toBeLessThanOrEqual(CALLBACK_MAX_BYTES);
      expect(decodeCallback(data)).toEqual(cb);
    }
  });

  it("rejects malformed chat data", () => {
    for (const bad of ["a:1:x", "a::y", "p:1:10", "p:x:1", "z:abc", "z:12345", "d:abc:q", "ee"]) {
      expect(decodeCallback(bad)).toBeNull();
    }
  });
});

describe("key registry", () => {
  it("maps a short key to the checklist / item ids and reuses it for the same target", () => {
    const reg = emptyRegistry();
    const k1 = keyFor(reg, { c: UUID, i: "item-1" });
    const k2 = keyFor(reg, { c: UUID, i: "item-1" });
    const k3 = keyFor(reg, { c: UUID });
    expect(k1).toBe(k2);
    expect(k3).not.toBe(k1);
    expect(resolveKey(reg, k1)).toEqual({ c: UUID, i: "item-1" });
    expect(resolveKey(reg, k3)).toEqual({ c: UUID });
  });

  it("evicts the oldest keys beyond MAX_KEYS and never reissues an evicted key", () => {
    const reg = emptyRegistry();
    const first = keyFor(reg, { c: "c0" });
    for (let i = 1; i <= MAX_KEYS; i++) keyFor(reg, { c: `c${i}` });
    expect(Object.keys(reg.map)).toHaveLength(MAX_KEYS);
    expect(resolveKey(reg, first)).toBeUndefined();
    const again = keyFor(reg, { c: "c0" });
    expect(again).not.toBe(first);
    expect(Object.keys(reg.rev)).toHaveLength(MAX_KEYS);
  });
});
