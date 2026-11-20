import { describe, expect, it } from "vitest";
import { encodeFillItems } from "../../mcp-server/src/encode";
import { parseFillItems, parseTemplateItems, type ChecklistSyncData } from "../../mcp-server/src/model";
import { createEmptyChecklist } from "../../mcp-server/src/mutate";
import {
  applyOps,
  needsConfirmation,
  planTool,
  seededUuid,
  toolStatus,
  type AgentToolCall,
  type OwnedChecklist,
  type Plan,
} from "./agentTools";
import { viewFill } from "./views";

const GOOGLE = "google-uid";
const DEVICE = "device-doc";
const CTX = { timezone: "Etc/GMT-3", seed: "tg-chat-42-1:0:0" };

/** A checklist whose fill rows carry the flags a re-encode must never drop. */
function list(name: string, items: string[], updatedAt: number, owner = GOOGLE): OwnedChecklist {
  const c = createEmptyChecklist(name, items, updatedAt);
  c.updatedAt = updatedAt;
  const fill = viewFill(c)!;
  fill.itemsJson = encodeFillItems(
    parseFillItems(fill.itemsJson).map((it) => ({
      ...it,
      reminderFullScreen: true,
      alarmEnabled: false,
      extra: { futureField: "keep-me" },
    })),
  );
  return { checklist: c, ownerId: owner };
}

function call(name: string, args: Record<string, unknown>): AgentToolCall {
  return { id: "c1", name, args };
}

function rows(c: ChecklistSyncData) {
  return parseFillItems(viewFill(c)!.itemsJson);
}

function rawRows(c: ChecklistSyncData): Record<string, unknown>[] {
  return JSON.parse(viewFill(c)!.itemsJson) as Record<string, unknown>[];
}

/** Every row except the touched ones still carries all three flags, byte for byte. */
function expectFlagsKept(c: ChecklistSyncData, exceptTexts: string[] = []) {
  for (const raw of rawRows(c)) {
    if (exceptTexts.includes(raw["text"] as string)) continue;
    expect(raw["reminderFullScreen"], `${raw["text"]}`).toBe(true);
    expect(raw["alarmEnabled"], `${raw["text"]}`).toBe(false);
    expect(raw["futureField"], `${raw["text"]}`).toBe("keep-me");
  }
}

function run(lists: OwnedChecklist[], c: AgentToolCall, ctx = CTX): { plan: Plan; changed: OwnedChecklist[] } {
  const plan = planTool(c, lists, ctx);
  const changed = applyOps(lists, plan.ops, GOOGLE, 5_000);
  return { plan, changed };
}

describe("checklist lookup", () => {
  it("matches the hint as a case-insensitive substring", () => {
    const lists = [list("Groceries", ["milk"], 1), list("Work", ["report"], 2)];
    expect(run(lists, call("add_item", { checklist_hint: "grocer", item_text: "eggs" })).plan.result).toEqual({
      status: "success",
      details: ["eggs", "Groceries"],
    });
  });

  it("0 matches → not_found with the hint, ≥2 → ambiguous with ≤6 candidates", () => {
    const many = Array.from({ length: 8 }, (_, i) => list(`Trip ${i}`, ["x"], i));
    expect(planTool(call("add_item", { checklist_hint: "nope", item_text: "x" }), many, CTX).result).toEqual({
      status: "not_found",
      details: ["nope"],
    });
    const amb = planTool(call("add_item", { checklist_hint: "trip", item_text: "x" }), many, CTX).result;
    expect(amb["status"]).toBe("ambiguous");
    expect(amb["candidates"]).toHaveLength(6);
  });

  it("no hint → the most recently updated checklist (deterministic default)", () => {
    const lists = [list("Old", ["a"], 10), list("Fresh", ["b"], 99), list("Mid", ["c"], 50)];
    expect(run(lists, call("add_item", { item_text: "x" })).changed[0]!.checklist.name).toBe("Fresh");
    expect(planTool(call("add_item", { item_text: "x" }), [], CTX).result).toEqual({ status: "not_found" });
  });
});

describe("add_item / add_items / create_checklist", () => {
  it("adds to template + default fill with deterministic ids, and a re-applied plan is a no-op", () => {
    const lists = [list("Groceries", ["milk"], 1)];
    const { plan, changed } = run(lists, call("add_item", { item_text: "eggs" }));
    expect(plan.result).toEqual({ status: "success", details: ["eggs"] });
    const c = changed[0]!.checklist;
    const t = parseTemplateItems(c.itemsJson).find((i) => i.text === "eggs")!;
    const f = rows(c).find((i) => i.text === "eggs")!;
    expect(f.templateItemId).toBe(t.id);
    expect(t.id).toBe(seededUuid(`${CTX.seed}:t:0`));
    expectFlagsKept(c, ["eggs"]);

    // Retry after a crash between the Firestore write and the "applied" mark: nothing doubles.
    expect(applyOps(lists, plan.ops, GOOGLE, 6_000)).toEqual([]);
    expect(rows(lists[0]!.checklist).filter((r) => r.text === "eggs")).toHaveLength(1);
  });

  it("add_items reports the count; an empty list is unknown_tool like the app's mapper", () => {
    const lists = [list("Groceries", ["milk"], 1)];
    expect(run(lists, call("add_items", { item_texts: ["a", " ", "b"] })).plan.result).toEqual({
      status: "success",
      details: ["2", "Groceries"],
    });
    expect(rows(lists[0]!.checklist).map((r) => r.text)).toEqual(["milk", "a", "b"]);
    expect(planTool(call("add_items", { item_texts: [" "] }), lists, CTX).result).toEqual({
      status: "unknown_tool",
      detail: "add_items",
    });
  });

  it("create_checklist writes to the data doc under a deterministic cloudId, once", () => {
    const lists = [list("Work", ["x"], 1, DEVICE)];
    const { plan, changed } = run(lists, call("create_checklist", { name: "Climbing", initial_items: ["shoes", "chalk"] }));
    expect(plan.result).toEqual({ status: "success", details: ["Climbing", "2"] });
    expect(changed[0]!.ownerId).toBe(GOOGLE);
    expect(changed[0]!.checklist.cloudId).toBe(seededUuid(`${CTX.seed}:list`));
    expect(applyOps(lists, plan.ops, GOOGLE, 7_000)).toEqual([]);
    expect(lists).toHaveLength(2);
    expect(planTool(call("create_checklist", { name: "Solo", initial_items: ["one"] }), lists, CTX).result).toEqual({
      status: "success",
      details: ["Solo"],
    });
  });
});

describe("complete_item / delete_item", () => {
  it("completes the first row containing the text and keeps every flag of every row", () => {
    const lists = [list("Groceries", ["whole milk", "milk chocolate"], 1)];
    const { plan, changed } = run(lists, call("complete_item", { item_text: "MILK" }));
    expect(plan.result).toEqual({ status: "success", details: ["whole milk", "Groceries"] });
    const c = changed[0]!.checklist;
    expect(rows(c).map((r) => r.checked)).toEqual([true, false]);
    expectFlagsKept(c);
  });

  it("an already-done row is a success without a write; a missing row is not_found", () => {
    const lists = [list("Groceries", ["milk"], 1)];
    run(lists, call("complete_item", { item_text: "milk" }));
    const again = run(lists, call("complete_item", { item_text: "milk" }));
    expect(again.plan).toEqual({ result: { status: "success", details: ["milk"] }, ops: [] });
    expect(planTool(call("complete_item", { item_text: "bread" }), lists, CTX).result).toEqual({
      status: "not_found",
      details: ["bread", "Groceries"],
    });
  });

  it("delete_item removes the template node and the fill row; a replay does not take a second match", () => {
    const lists = [list("Groceries", ["milk", "milk chocolate", "bread"], 1)];
    const { plan, changed } = run(lists, call("delete_item", { item_text: "milk" }));
    expect(plan.result).toEqual({ status: "success", details: ["milk", "Groceries"] });
    const c = changed[0]!.checklist;
    expect(rows(c).map((r) => r.text)).toEqual(["milk chocolate", "bread"]);
    expect(parseTemplateItems(c.itemsJson).map((t) => t.text)).toEqual(["milk chocolate", "bread"]);
    expectFlagsKept(c);
    expect(applyOps(lists, plan.ops, GOOGLE, 8_000)).toEqual([]);
    expect(rows(lists[0]!.checklist).map((r) => r.text)).toEqual(["milk chocolate", "bread"]);
  });
});

describe("clear_completed_items", () => {
  it("without a hint and with ≥2 lists asks which one (ambiguous), never guesses", () => {
    const lists = [list("A", ["x"], 1), list("B", ["y"], 2)];
    expect(planTool(call("clear_completed_items", {}), lists, CTX).result).toEqual({
      status: "ambiguous",
      candidates: ["B", "A"],
    });
  });

  it("removes only the checked rows (template too) and keeps the flags of the rest", () => {
    const lists = [list("Groceries", ["milk", "bread", "eggs"], 1)];
    run(lists, call("complete_item", { item_text: "milk" }));
    run(lists, call("complete_item", { item_text: "eggs" }));
    const { plan, changed } = run(lists, call("clear_completed_items", { checklist_hint: "groc" }));
    expect(plan.result).toEqual({ status: "success", details: ["2", "Groceries"] });
    const c = changed[0]!.checklist;
    expect(rows(c).map((r) => r.text)).toEqual(["bread"]);
    expect(parseTemplateItems(c.itemsJson).map((t) => t.text)).toEqual(["bread"]);
    expectFlagsKept(c);
    expect(run(lists, call("clear_completed_items", {})).plan).toEqual({
      result: { status: "success", details: ["Groceries"] },
      ops: [],
    });
  });
});

describe("set_item_reminder", () => {
  it("writes reminderAt from the user's local time by spread — every other field survives", () => {
    const lists = [list("Groceries", ["milk", "bread"], 1)];
    const { plan, changed } = run(lists, call("set_item_reminder", { item_text: "milk", when_iso: "2026-06-01T09:00:00" }));
    expect(plan.result).toEqual({ status: "success", details: ["milk", "2026-06-01 09:00"] });
    const c = changed[0]!.checklist;
    const raw = rawRows(c)[0]!;
    expect(raw["reminderAt"]).toBe(Date.UTC(2026, 5, 1, 6, 0, 0)); // 09:00 at UTC+3
    expect(raw["reminderFullScreen"]).toBe(true);
    expect(raw["alarmEnabled"]).toBe(false);
    expect(raw["futureField"]).toBe("keep-me");
    expectFlagsKept(c);
  });

  it("unknown zone → error timezone_unknown (+ flag for the /timezone hint); bad ISO → unknown_tool", () => {
    const lists = [list("Groceries", ["milk"], 1)];
    const p = planTool(call("set_item_reminder", { item_text: "milk", when_iso: "2026-06-01T09:00:00" }), lists, {
      ...CTX,
      timezone: null,
    });
    expect(p).toEqual({ result: { status: "error", detail: "timezone_unknown" }, ops: [], timezoneUnknown: true });
    expect(toolStatus(p.result)).toBe("error");
    expect(planTool(call("set_item_reminder", { item_text: "milk", when_iso: "soon" }), lists, CTX).result).toEqual({
      status: "unknown_tool",
      detail: "set_item_reminder",
    });
  });
});

describe("rename_checklist", () => {
  it("renames by hint; both args are required", () => {
    const lists = [list("Groceries", ["milk"], 1)];
    const { plan, changed } = run(lists, call("rename_checklist", { checklist_hint: "groc", new_name: "Food" }));
    expect(plan.result).toEqual({ status: "success", details: ["Groceries", "Food"] });
    expect(changed[0]!.checklist.name).toBe("Food");
    expectFlagsKept(changed[0]!.checklist);
    expect(planTool(call("rename_checklist", { new_name: "X" }), lists, CTX).result["status"]).toBe("unknown_tool");
  });
});

describe("move_item", () => {
  it("adds to the destination first, then removes from the source, keeping text, done state and note", () => {
    const lists = [list("Inbox list", ["call mom", "buy milk"], 2), list("Family", ["birthday"], 1)];
    run(lists, call("complete_item", { checklist_hint: "inbox", item_text: "call mom" }));
    const { plan, changed } = run(
      lists,
      call("move_item", { item_text: "call", from_checklist_hint: "inbox", to_checklist_hint: "family" }),
    );
    expect(plan.result).toEqual({ status: "success", details: ["call mom", "Family"] });
    expect(changed.map((c) => c.checklist.name)).toEqual(["Family", "Inbox list"]);
    const family = changed[0]!.checklist;
    const moved = rows(family).find((r) => r.text === "call mom")!;
    expect(moved.checked).toBe(true);
    expect(moved.templateItemId).toBe(parseTemplateItems(family.itemsJson).find((t) => t.text === "call mom")!.id);
    expectFlagsKept(family, ["call mom"]);
    expect(rows(changed[1]!.checklist).map((r) => r.text)).toEqual(["buy milk"]);
    expectFlagsKept(changed[1]!.checklist);

    // Replay after a half-applied move: the destination is not doubled.
    expect(applyOps(lists, plan.ops, GOOGLE, 9_000)).toEqual([]);
    expect(rows(lists[1]!.checklist).filter((r) => r.text === "call mom")).toHaveLength(1);
  });

  it("refuses a row with a reminder as not_found + reason move_blocked; same list is a no-op success", () => {
    const lists = [list("Inbox list", ["call mom"], 2), list("Family", ["birthday"], 1)];
    run(lists, call("set_item_reminder", { checklist_hint: "inbox", item_text: "call", when_iso: "2026-06-01T09:00:00" }));
    expect(planTool(call("move_item", { item_text: "call", from_checklist_hint: "inbox", to_checklist_hint: "fam" }), lists, CTX))
      .toEqual({ result: { status: "not_found", reason: "move_blocked", details: ["call mom"] }, ops: [] });
    expect(
      planTool(call("move_item", { item_text: "birthday", from_checklist_hint: "fam", to_checklist_hint: "family" }), lists, CTX),
    ).toEqual({ result: { status: "success", details: ["birthday", "Family"] }, ops: [] });
  });
});

describe("read-only tools", () => {
  it("find_items caps at 10 and summarises the first 5", () => {
    const lists = [list("A", Array.from({ length: 12 }, (_, i) => `task ${i}`), 1)];
    const r = planTool(call("find_items", { query: "TASK" }), lists, CTX);
    expect(r.ops).toEqual([]);
    expect(r.result["status"]).toBe("success");
    const [count, summary] = r.result["details"] as string[];
    expect(count).toBe("10");
    expect(summary).toBe("«task 0» in A; «task 1» in A; «task 2» in A; «task 3» in A; «task 4» in A (+5 more)");
    expect(planTool(call("find_items", { query: "zzz" }), lists, CTX).result).toEqual({ status: "not_found", details: ["zzz"] });
  });

  it("read_checklist returns {status, checklist, items:[{text, done}]}", () => {
    const lists = [list("Groceries", ["milk", "bread"], 1)];
    run(lists, call("complete_item", { item_text: "bread" }));
    expect(planTool(call("read_checklist", { name: "groceries" }), lists, CTX).result).toEqual({
      status: "success",
      checklist: "Groceries",
      items: [
        { text: "milk", done: false },
        { text: "bread", done: true },
      ],
    });
  });

  it("an unknown tool or a missing required arg answers unknown_tool", () => {
    expect(planTool(call("present_options", {}), [], CTX).result).toEqual({ status: "unknown_tool", detail: "present_options" });
    expect(planTool(call("delete_item", {}), [], CTX).result).toEqual({ status: "unknown_tool", detail: "delete_item" });
  });
});

describe("confirmation routing", () => {
  it("only delete_item, clear_completed_items and move_item ask for confirmation", () => {
    expect(needsConfirmation([call("add_item", {}), call("complete_item", {}), call("set_item_reminder", {})])).toBe(false);
    for (const name of ["delete_item", "clear_completed_items", "move_item"]) {
      expect(needsConfirmation([call("add_item", {}), call(name, {})])).toBe(true);
    }
  });
});

describe("seededUuid", () => {
  it("is stable, UUID-shaped and differs per seed", () => {
    const a = seededUuid("x:t:0");
    expect(a).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    expect(seededUuid("x:t:0")).toBe(a);
    expect(seededUuid("x:t:1")).not.toBe(a);
  });
});
