/**
 * Unit coverage for the Amplitude instrumentation (analytics.ts).
 *
 * Two things are worth pinning here, and they are the two that silently break:
 *  1. PRIVACY — the payload may carry a tool name, fixed-vocabulary reasons, durations, and a
 *     SANITISED client slug, and nothing else. A regression that starts forwarding `e.message`
 *     would ship checklist names to a third party from a public repo. `EVERY_EVENT` +
 *     `PROPERTY_ALLOWLIST` below are the gate: a new event variant that is not listed fails the
 *     exhaustiveness check, and a new property that is not on the allowlist fails the key test.
 *  2. THE JOIN KEY — `user_id` must be the app's `users/{doc_id}` verbatim. Any prefixing/casing
 *     "cleanup" silently detaches every MCP event from the user's profile, which is the entire
 *     point of sending them to Amplitude rather than Analytics Engine.
 */
import { describe, expect, it } from "vitest";
import {
  authFlowIdentity,
  AUTH_FLOW_DEVICE_ID,
  buildAmplitudeEvent,
  failureReason,
  googleTokenFailureReason,
  markSoftFail,
  pseudonymousDeviceId,
  sanitizeClientSlug,
  sanitizeClientVersion,
  sendMcpEvent,
  softFailReasonOf,
  UNKNOWN_CLIENT,
  type AnalyticsIdentity,
  type McpEvent,
} from "./analytics";
import { CfError } from "./cf";

const NOW = 1_760_000_000_000;
const LINKED: AnalyticsIdentity = {
  userId: "3f2a1b9c-4d5e-4f6a-8b7c-1d2e3f4a5b6c",
  deviceId: "mcp_abc123def456",
  resolved: true,
};
const UNLINKED: AnalyticsIdentity = { userId: null, deviceId: "mcp_abc123def456", resolved: true };

const SESSION_STARTED: McpEvent = {
  type: "mcp_session_started",
  linked: true,
  identityResolved: true,
  clientName: "claude-code",
  clientVersion: "1.2.3",
};

/**
 * One instance of EVERY event the server can emit. `Record<McpEvent["type"], McpEvent>` is the
 * exhaustiveness gate: adding a variant to `McpEvent` without adding it here stops compiling, so a
 * new event cannot ship without passing the privacy tests below.
 */
const EVERY_EVENT: Record<McpEvent["type"], McpEvent> = {
  mcp_auth_started: { type: "mcp_auth_started" },
  mcp_auth_completed: { type: "mcp_auth_completed" },
  mcp_auth_failed: { type: "mcp_auth_failed", reason: "invalid_state" },
  mcp_session_started: SESSION_STARTED,
  mcp_tool_called: { type: "mcp_tool_called", toolName: "list_checklists" },
  mcp_tool_completed: {
    type: "mcp_tool_completed",
    toolName: "get_checklist",
    durationMs: 412,
    softFail: "checklist_not_found",
  },
  mcp_tool_failed: { type: "mcp_tool_failed", toolName: "fill_checklist_ai", reason: "cf_402", durationMs: 900 },
};

/** The COMPLETE set of event_property keys allowed on the wire, across every event. */
const PROPERTY_ALLOWLIST = [
  "client_name",
  "client_version",
  "duration_ms",
  "identity_resolved",
  "linked",
  "outcome",
  "reason",
  "tool_name",
] as const;

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

describe("googleTokenFailureReason (OAuth vocabulary)", () => {
  it("keeps Google's status — 400 is our config, 5xx is Google", () => {
    expect(googleTokenFailureReason(400)).toBe("google_token_400");
    expect(googleTokenFailureReason(503)).toBe("google_token_503");
  });

  it("never emits a non-integer status into the event name", () => {
    expect(googleTokenFailureReason(Number.NaN)).toBe("google_token_0");
    expect(googleTokenFailureReason(404.7)).toBe("google_token_404");
  });
});

describe("buildAmplitudeEvent — join key", () => {
  it("sends users/{doc_id} verbatim as user_id (the app's setUserId value)", () => {
    const e = buildAmplitudeEvent(LINKED, { type: "mcp_tool_called", toolName: "list_checklists" }, NOW);
    expect(e.user_id).toBe("3f2a1b9c-4d5e-4f6a-8b7c-1d2e3f4a5b6c"); // no prefix, no casing change
    expect(e.time).toBe(NOW);
  });

  it("omits user_id for an unlinked caller but still counts them via device_id", () => {
    const e = buildAmplitudeEvent(UNLINKED, { ...SESSION_STARTED, linked: false }, NOW);
    expect(e.user_id).toBeUndefined();
    expect(e.device_id).toBe("mcp_abc123def456");
  });

  it("drops a user_id Amplitude would 400 on (<5 chars) rather than lose the whole event", () => {
    const e = buildAmplitudeEvent(
      { userId: "abc", deviceId: "mcp_abc123def456", resolved: true },
      { type: "mcp_tool_called", toolName: "add_item" },
      NOW,
    );
    expect(e.user_id).toBeUndefined();
    expect(e.device_id).toBe("mcp_abc123def456");
  });

  it("attributes pre-identity OAuth events to one shared device so they can't inflate DAU", () => {
    const e = buildAmplitudeEvent(authFlowIdentity(), { type: "mcp_auth_started" }, NOW);
    expect(e.user_id).toBeUndefined();
    expect(e.device_id).toBe(AUTH_FLOW_DEVICE_ID);
    expect(AUTH_FLOW_DEVICE_ID.length).toBeGreaterThanOrEqual(5); // Amplitude 400s a shorter id
  });
});

describe("buildAmplitudeEvent — privacy contract", () => {
  it("carries ONLY the declared properties per event — never checklist content", () => {
    expect(buildAmplitudeEvent(LINKED, SESSION_STARTED, NOW).event_properties).toEqual({
      linked: true,
      identity_resolved: true,
      client_name: "claude-code",
      client_version: "1.2.3",
    });
    expect(
      buildAmplitudeEvent(LINKED, { type: "mcp_tool_called", toolName: "create_checklist_ai" }, NOW).event_properties,
    ).toEqual({ tool_name: "create_checklist_ai" });
    expect(
      buildAmplitudeEvent(
        LINKED,
        { type: "mcp_tool_failed", toolName: "fill_checklist_ai", reason: "cf_402", durationMs: 1234 },
        NOW,
      ).event_properties,
    ).toEqual({ tool_name: "fill_checklist_ai", reason: "cf_402", duration_ms: 1234 });
  });

  it("emits a payload whose every key is on the allowlist (nothing rides along)", () => {
    const e = buildAmplitudeEvent(
      LINKED,
      { type: "mcp_tool_failed", toolName: "toggle_item", reason: "exception", durationMs: 7 },
      NOW,
    );
    expect(Object.keys(e).sort()).toEqual(["device_id", "event_properties", "event_type", "time", "user_id"]);
    expect(e.event_type).toBe("mcp_tool_failed");
  });

  it("keeps EVERY event's property keys inside the allowlist (the gate for new events)", () => {
    for (const event of Object.values(EVERY_EVENT)) {
      const props = buildAmplitudeEvent(LINKED, event, NOW).event_properties;
      for (const key of Object.keys(props)) {
        expect(PROPERTY_ALLOWLIST, `${event.type}.${key} is not on the allowlist`).toContain(key);
      }
      // Numbers and booleans cannot smuggle text; every STRING value must be short and slug-like.
      for (const value of Object.values(props)) {
        if (typeof value === "string") expect(value.length).toBeLessThanOrEqual(32);
      }
    }
  });

  it("has no free-text field left for user content to enter through", () => {
    // Every event, serialised — nothing here may look like a checklist name / note / prompt.
    const wire = Object.values(EVERY_EVENT)
      .map((e) => JSON.stringify(buildAmplitudeEvent(LINKED, e, NOW)))
      .join(" ");
    expect(wire).not.toMatch(/\s(shopping|milk|packing)/i);
    expect(wire).not.toContain("@"); // no email, anywhere
  });
});

describe("mcp_tool_completed — the success/soft-refusal signal", () => {
  it("marks a normal return `ok` and attaches no reason", () => {
    const props = buildAmplitudeEvent(
      LINKED,
      { type: "mcp_tool_completed", toolName: "list_checklists", durationMs: 55, softFail: null },
      NOW,
    ).event_properties;
    expect(props).toEqual({ tool_name: "list_checklists", duration_ms: 55, outcome: "ok" });
    expect(props["reason"]).toBeUndefined();
  });

  it("marks a soft refusal with its closed-vocabulary reason — the 'invented id' signal", () => {
    expect(
      buildAmplitudeEvent(
        LINKED,
        { type: "mcp_tool_completed", toolName: "get_checklist", durationMs: 61, softFail: "checklist_not_found" },
        NOW,
      ).event_properties,
    ).toEqual({ tool_name: "get_checklist", duration_ms: 61, outcome: "soft_fail", reason: "checklist_not_found" });
  });

  it("carries duration as a number so latency is queryable, not a formatted string", () => {
    const props = buildAmplitudeEvent(
      LINKED,
      { type: "mcp_tool_completed", toolName: "create_checklist_ai", durationMs: 4200, softFail: null },
      NOW,
    ).event_properties;
    expect(typeof props["duration_ms"]).toBe("number");
  });
});

describe("mcp_auth_* — the OAuth funnel", () => {
  it("sends no properties at all on start/complete (there is nothing safe to say yet)", () => {
    expect(buildAmplitudeEvent(authFlowIdentity(), { type: "mcp_auth_started" }, NOW).event_properties).toEqual({});
    expect(buildAmplitudeEvent(UNLINKED, { type: "mcp_auth_completed" }, NOW).event_properties).toEqual({});
  });

  it("reports each rejecting branch by its own closed-vocabulary reason", () => {
    const reasons = [
      "invalid_request",
      "missing_client_id",
      "missing_code_or_state",
      "invalid_state",
      "state_missing_client_id",
      "no_id_token",
      "id_token_unreadable",
      "no_email",
      "grant_failed",
      googleTokenFailureReason(400),
    ] as const;
    for (const reason of reasons) {
      expect(
        buildAmplitudeEvent(authFlowIdentity(), { type: "mcp_auth_failed", reason }, NOW).event_properties,
      ).toEqual({ reason });
    }
  });
});

describe("mcp_session_started — identity_resolved", () => {
  it("separates 'no Gisti account' from 'we could not tell' so linked=false stays honest", () => {
    const unlinked = buildAmplitudeEvent(UNLINKED, { ...SESSION_STARTED, linked: false }, NOW).event_properties;
    expect(unlinked).toMatchObject({ linked: false, identity_resolved: true });

    const degraded = buildAmplitudeEvent(
      { userId: null, deviceId: "mcp_abc123def456", resolved: false },
      { ...SESSION_STARTED, linked: false, identityResolved: false },
      NOW,
    ).event_properties;
    expect(degraded).toMatchObject({ linked: false, identity_resolved: false });
  });
});

describe("client identification (the only non-enumerable strings on the wire)", () => {
  it("slugifies a real client name", () => {
    expect(sanitizeClientSlug("Claude Code")).toBe("claude-code");
    expect(sanitizeClientSlug("claude-ai")).toBe("claude-ai");
    expect(sanitizeClientSlug("Cursor")).toBe("cursor");
  });

  it("degrades to 'unknown' rather than dropping the property or throwing", () => {
    expect(sanitizeClientSlug(undefined)).toBe(UNKNOWN_CLIENT);
    expect(sanitizeClientSlug("")).toBe(UNKNOWN_CLIENT);
    expect(sanitizeClientSlug("!!!")).toBe(UNKNOWN_CLIENT);
    expect(sanitizeClientSlug(42)).toBe(UNKNOWN_CLIENT);
    expect(sanitizeClientVersion(undefined)).toBe(UNKNOWN_CLIENT);
    expect(sanitizeClientVersion("")).toBe(UNKNOWN_CLIENT);
  });

  it("bounds a hostile name so a client cannot smuggle content through clientInfo", () => {
    const hostile = sanitizeClientSlug('Milk shopping list for Anna: buy 2% milk, bread — user@example.com');
    expect(hostile.length).toBeLessThanOrEqual(24);
    expect(hostile).not.toContain("@");
    expect(hostile).toMatch(/^[a-z0-9-]+$/);
    expect(hostile.endsWith("-")).toBe(false);
  });

  it("keeps a version parseable but bounded", () => {
    expect(sanitizeClientVersion("1.2.3")).toBe("1.2.3");
    expect(sanitizeClientVersion("2.0.0-beta.1+build")).toBe("2.0.0-beta.1+bui");
    expect(sanitizeClientVersion("v1.0 (nightly build)").length).toBeLessThanOrEqual(16);
  });
});

describe("soft-fail marker (how tracked() learns an early return happened)", () => {
  it("round-trips the reason", () => {
    const result = markSoftFail({ content: [{ type: "text", text: "No checklist found with id abc." }] }, "checklist_not_found");
    expect(softFailReasonOf(result)).toBe("checklist_not_found");
  });

  it("returns null for an untagged result and for non-objects", () => {
    expect(softFailReasonOf({ content: [] })).toBeNull();
    expect(softFailReasonOf(null)).toBeNull();
    expect(softFailReasonOf("nope")).toBeNull();
    expect(softFailReasonOf(undefined)).toBeNull();
  });

  it("is INVISIBLE to the MCP client — the tool payload must not change shape", () => {
    const plain = { content: [{ type: "text", text: "hi" }] };
    const tagged = markSoftFail({ content: [{ type: "text", text: "hi" }] }, "not_linked");
    expect(JSON.stringify(tagged)).toBe(JSON.stringify(plain));
    expect(Object.keys(tagged)).toEqual(["content"]);
    expect({ ...tagged }).toEqual(plain);
  });

  it("tags in place so the handler keeps returning the very object it built", () => {
    const original = { content: [{ type: "text", text: "hi" }] };
    expect(markSoftFail(original, "rate_limited")).toBe(original);
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
