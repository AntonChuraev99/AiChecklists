/**
 * Unit coverage for the Amplitude instrumentation (analytics.ts).
 *
 * Two things are worth pinning here, and they are the two that silently break:
 *  1. PRIVACY — the payload may carry a tool name and a fixed-vocabulary reason, and nothing else.
 *     A regression that starts forwarding `e.message` would ship checklist names to a third party
 *     from a public repo. The "no user content" test is the gate.
 *  2. THE JOIN KEY — `user_id` must be the app's `users/{doc_id}` verbatim. Any prefixing/casing
 *     "cleanup" silently detaches every MCP event from the user's profile, which is the entire
 *     point of sending them to Amplitude rather than Analytics Engine.
 */
import { describe, expect, it } from "vitest";
import {
  buildAmplitudeEvent,
  failureReason,
  pseudonymousDeviceId,
  sendMcpEvent,
  type AnalyticsIdentity,
} from "./analytics";
import { CfError } from "./cf";

const NOW = 1_760_000_000_000;
const LINKED: AnalyticsIdentity = { userId: "3f2a1b9c-4d5e-4f6a-8b7c-1d2e3f4a5b6c", deviceId: "mcp_abc123def456" };
const UNLINKED: AnalyticsIdentity = { userId: null, deviceId: "mcp_abc123def456" };

describe("failureReason (closed vocabulary)", () => {
  it("keeps the CF status — 402/429/503 are different stories, not one 'error'", () => {
    expect(failureReason(new CfError(402, "Not enough credits. Need 1.", "generate_checklist"))).toBe("cf_402");
    expect(failureReason(new CfError(429, "daily limit", "generate_checklist"))).toBe("cf_429");
    expect(failureReason(new CfError(503, "off", "generate_checklist"))).toBe("cf_503");
  });

  it("maps a CF network failure (status 0) to cf_network", () => {
    expect(failureReason(new CfError(0, "network error: fetch failed", "generate_checklist"))).toBe("cf_network");
  });

  it("collapses anything unhandled to 'exception' WITHOUT reading its message", () => {
    expect(failureReason(new Error("firestore GET /users/abc/checklists/Milk shopping failed"))).toBe("exception");
    expect(failureReason("a bare string throw")).toBe("exception");
    expect(failureReason(undefined)).toBe("exception");
  });
});

describe("buildAmplitudeEvent — join key", () => {
  it("sends users/{doc_id} verbatim as user_id (the app's setUserId value)", () => {
    const e = buildAmplitudeEvent(LINKED, { type: "mcp_tool_called", toolName: "list_checklists" }, NOW);
    expect(e.user_id).toBe("3f2a1b9c-4d5e-4f6a-8b7c-1d2e3f4a5b6c"); // no prefix, no casing change
    expect(e.time).toBe(NOW);
  });

  it("omits user_id for an unlinked caller but still counts them via device_id", () => {
    const e = buildAmplitudeEvent(UNLINKED, { type: "mcp_session_started", linked: false }, NOW);
    expect(e.user_id).toBeUndefined();
    expect(e.device_id).toBe("mcp_abc123def456");
  });

  it("drops a user_id Amplitude would 400 on (<5 chars) rather than lose the whole event", () => {
    const e = buildAmplitudeEvent({ userId: "abc", deviceId: "mcp_abc123def456" }, { type: "mcp_tool_called", toolName: "add_item" }, NOW);
    expect(e.user_id).toBeUndefined();
    expect(e.device_id).toBe("mcp_abc123def456");
  });
});

describe("buildAmplitudeEvent — privacy contract", () => {
  it("carries ONLY tool_name / reason / linked — never checklist content", () => {
    expect(buildAmplitudeEvent(LINKED, { type: "mcp_session_started", linked: true }, NOW).event_properties).toEqual({
      linked: true,
    });
    expect(
      buildAmplitudeEvent(LINKED, { type: "mcp_tool_called", toolName: "create_checklist_ai" }, NOW).event_properties,
    ).toEqual({ tool_name: "create_checklist_ai" });
    expect(
      buildAmplitudeEvent(LINKED, { type: "mcp_tool_failed", toolName: "fill_checklist_ai", reason: "cf_402" }, NOW)
        .event_properties,
    ).toEqual({ tool_name: "fill_checklist_ai", reason: "cf_402" });
  });

  it("emits a payload whose every key is on the allowlist (nothing rides along)", () => {
    const e = buildAmplitudeEvent(LINKED, { type: "mcp_tool_failed", toolName: "toggle_item", reason: "exception" }, NOW);
    expect(Object.keys(e).sort()).toEqual(["device_id", "event_properties", "event_type", "time", "user_id"]);
    expect(e.event_type).toBe("mcp_tool_failed");
  });
});

describe("pseudonymousDeviceId", () => {
  it("is stable, prefixed, and does not contain the email", async () => {
    const a = await pseudonymousDeviceId("someone@example.com");
    const b = await pseudonymousDeviceId("someone@example.com");
    expect(a).toBe(b);
    expect(a).toMatch(/^mcp_[0-9a-f]{16}$/);
    expect(a).not.toContain("someone");
    expect(a).not.toContain("example");
  });

  it("normalises case/whitespace so one person is one id", async () => {
    expect(await pseudonymousDeviceId("  Someone@Example.com ")).toBe(await pseudonymousDeviceId("someone@example.com"));
  });

  it("separates different people", async () => {
    expect(await pseudonymousDeviceId("a@example.com")).not.toBe(await pseudonymousDeviceId("b@example.com"));
  });
});

describe("sendMcpEvent (non-critical path)", () => {
  it("no-ops without an api key instead of throwing (server runs before the secret exists)", async () => {
    const calls: unknown[] = [];
    const original = globalThis.fetch;
    globalThis.fetch = (async (...args: unknown[]) => {
      calls.push(args);
      return new Response("{}", { status: 200 });
    }) as typeof fetch;
    try {
      await expect(sendMcpEvent({}, LINKED, { type: "mcp_tool_called", toolName: "get_checklist" }, NOW)).resolves.toBeUndefined();
      expect(calls).toHaveLength(0);
    } finally {
      globalThis.fetch = original;
    }
  });

  it("posts api_key + a single-event batch to the HTTP V2 endpoint", async () => {
    let url = "";
    let body: { api_key?: string; events?: unknown[] } = {};
    const original = globalThis.fetch;
    globalThis.fetch = (async (u: string, init: RequestInit) => {
      url = u;
      body = JSON.parse(init.body as string);
      return new Response("{}", { status: 200 });
    }) as unknown as typeof fetch;
    try {
      await sendMcpEvent(
        { AMPLITUDE_SERVER_API_KEY: "test-key" },
        LINKED,
        { type: "mcp_tool_called", toolName: "list_fills" },
        NOW,
      );
      expect(url).toBe("https://api2.amplitude.com/2/httpapi");
      expect(body.api_key).toBe("test-key");
      expect(body.events).toHaveLength(1);
    } finally {
      globalThis.fetch = original;
    }
  });

  it("swallows a transport failure — a tool call must never fail because of analytics", async () => {
    const original = globalThis.fetch;
    globalThis.fetch = (async () => {
      throw new Error("network down");
    }) as typeof fetch;
    try {
      await expect(
        sendMcpEvent({ AMPLITUDE_SERVER_API_KEY: "k" }, LINKED, { type: "mcp_tool_called", toolName: "add_item" }, NOW),
      ).resolves.toBeUndefined();
    } finally {
      globalThis.fetch = original;
    }
  });

  it("swallows a non-200 from Amplitude", async () => {
    const original = globalThis.fetch;
    globalThis.fetch = (async () => new Response("bad", { status: 400 })) as typeof fetch;
    try {
      await expect(
        sendMcpEvent({ AMPLITUDE_SERVER_API_KEY: "k" }, LINKED, { type: "mcp_tool_called", toolName: "add_item" }, NOW),
      ).resolves.toBeUndefined();
    } finally {
      globalThis.fetch = original;
    }
  });
});
