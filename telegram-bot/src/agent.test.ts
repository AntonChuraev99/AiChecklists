import { describe, expect, it } from "vitest";
import { createEmptyChecklist, toggleItem } from "../../mcp-server/src/mutate";
import { parseTemplateItems } from "../../mcp-server/src/model";
import {
  appendRound,
  beginTurn,
  buildAgentRequest,
  buildChecklistsSummary,
  CHAT_IDLE_MS,
  chatRequestId,
  closeChat,
  closeTurn,
  emptyChatState,
  finishTurn,
  isTranscriptValid,
  MAX_TOOL_ROUNDS,
  noteUserActivity,
  plainText,
  planBatch,
  pruneTranscript,
  renderConfirmCard,
  renderOptions,
  renderReply,
  routeText,
  selectWindow,
  splitText,
  STORED_MAX_TURNS,
  TELEGRAM_TEXT_MAX,
  toolCallsAction,
  type Entry,
} from "./agent";
import { applyOps, type AgentToolCall, type OwnedChecklist } from "./agentTools";
import { parseAgentResponse } from "./backend";
import { parseFillItems } from "../../mcp-server/src/model";
import { viewFill } from "./views";
import { byteLength, CALLBACK_MAX_BYTES, decodeCallback } from "./callback";
import { t } from "./i18n";

function calls(...names: string[]): AgentToolCall[] {
  return names.map((name, i) => ({ id: `call_${i}`, name, args: { item_text: "milk" }, thought_signature: `sig${i}==` }));
}

/** One finished turn: user, `rounds` tool rounds, model answer. */
function turn(text: string, rounds = 0): Entry[] {
  const out: Entry[] = [{ role: "user", text }];
  for (let r = 0; r < rounds; r++) {
    const c = calls("find_items");
    out.push({ role: "model", tool_calls: c }, { role: "tool", tool_results: [{ id: c[0]!.id, name: "find_items", result: { status: "success" } }] });
  }
  out.push({ role: "model", text: `answer to ${text}` });
  return out;
}

describe("transcript window (AgentTranscriptWindow mirror)", () => {
  it("keeps the newest 6 turns, oldest first", () => {
    const all = Array.from({ length: 9 }, (_, i) => turn(`m${i}`)).flat();
    const w = selectWindow(all);
    expect(w.filter((e) => e.role === "user").map((e) => (e as { text: string }).text)).toEqual(["m3", "m4", "m5", "m6", "m7", "m8"]);
  });

  it("cuts by whole turns under the entry cap — a calls/results pair is never split", () => {
    const all = [...turn("a", 5), ...turn("b", 5), ...turn("c", 5)]; // 12 entries per turn
    const w = selectWindow(all);
    expect(w.length).toBeLessThanOrEqual(30);
    expect(w[0]).toEqual({ role: "user", text: "b" });
    expect(isTranscriptValid(w)).toBe(true);
  });

  it("drops older turns over the char cap but always keeps the last turn", () => {
    const big = "x".repeat(5000);
    const w = selectWindow([...turn(big), ...turn(big)]);
    expect(w.filter((e) => e.role === "user")).toHaveLength(1);
    const huge = "y".repeat(9000);
    expect(selectWindow(turn(huge))[0]).toEqual({ role: "user", text: huge });
  });

  it("sanitize drops orphan calls and orphan results", () => {
    const c = calls("add_item");
    const broken: Entry[] = [
      { role: "user", text: "u" },
      { role: "model", tool_calls: c },
      { role: "user", text: "v" },
      { role: "tool", tool_results: [{ id: "x", name: "add_item", result: {} }] },
    ];
    expect(selectWindow(broken)).toEqual([
      { role: "user", text: "u" },
      { role: "user", text: "v" },
    ]);
  });

  it("storage keeps at most 40 turns", () => {
    const all = Array.from({ length: STORED_MAX_TURNS + 5 }, (_, i) => turn(`m${i}`)).flat();
    const kept = pruneTranscript(all);
    expect(kept.filter((e) => e.role === "user")).toHaveLength(STORED_MAX_TURNS);
    expect(kept[0]).toEqual({ role: "user", text: "m5" });
  });
});

describe("request body", () => {
  it("has the chat_agent shape; request_id is stable across the rounds of a turn", () => {
    const state = emptyChatState();
    const tr = beginTurn(state, "add milk", 42, 1001, 0);
    const base = {
      creditUserId: "credit-1",
      requestId: tr.requestId,
      lang: "ru" as const,
      offsetMinutes: 180,
      summary: [],
    };
    const round1 = buildAgentRequest({ ...base, transcript: state.transcript });
    appendRound(state, calls("add_item"), [{ id: "call_0", name: "add_item", result: { status: "success" } }]);
    const round2 = buildAgentRequest({ ...base, transcript: state.transcript });
    expect(round1["request_id"]).toBe("tg-chat-42-1");
    expect(round2["request_id"]).toBe(round1["request_id"]);
    expect(round1).toMatchObject({
      user_id: "credit-1",
      locale: "ru",
      response_language: "ru",
      timezone_offset_minutes: 180,
      supports_options: true,
      checklists_summary: [],
    });
    expect((round2["transcript"] as Entry[]).map((e) => e.role)).toEqual(["user", "model", "tool"]);
    // The thought signature goes back byte-for-byte.
    expect(((round2["transcript"] as Entry[])[1] as { tool_calls: AgentToolCall[] }).tool_calls[0]!.thought_signature).toBe("sig0==");
  });

  it("omits timezone_offset_minutes when the zone is unknown; response_language = the bot UI language", () => {
    const body = buildAgentRequest({
      creditUserId: "c",
      requestId: chatRequestId(7, 3),
      lang: "hi",
      offsetMinutes: null,
      summary: [],
      transcript: [{ role: "user", text: "hi" }],
    });
    expect("timezone_offset_minutes" in body).toBe(false);
    expect(body["response_language"]).toBe("hi"); // pickLang → en|ru|hi, never the raw Telegram code
    expect(body["locale"]).toBe("en");
    expect(body["request_id"]).toBe("tg-chat-7-3");
  });

  it("checklists_summary: most recently updated first, leaves only, done state from the fill, last 6 items", () => {
    const old = createEmptyChecklist("Old", ["a"], 1);
    old.updatedAt = 1;
    let fresh = createEmptyChecklist("Fresh", Array.from({ length: 9 }, (_, i) => `t${i}`), 5);
    fresh = toggleItem(fresh, parseTemplateItems(fresh.itemsJson)[8]!.id, true, 10)!;
    const summary = buildChecklistsSummary([
      { checklist: old, ownerId: "g" },
      { checklist: fresh, ownerId: "g" },
    ]);
    expect(summary.map((s) => s.name)).toEqual(["Fresh", "Old"]);
    expect(summary[0]).toMatchObject({ totalItems: 9, doneItems: 1 });
    expect(summary[0]!.recentItems.map((r) => r.text)).toEqual(["t3", "t4", "t5", "t6", "t7", "t8"]);
    expect(summary[0]!.recentItems[5]).toEqual({ text: "t8", checked: true, position: 8 });
  });
});

describe("chat mode routing", () => {
  it("text → agent while open, → draft when closed, → expired after 30 min idle", () => {
    const s = emptyChatState();
    expect(routeText(s, 1_000)).toBe("draft");
    beginTurn(s, "hi", 1, 1, 1_000);
    closeTurn(s);
    s.transcript.push({ role: "model", text: "hello" });
    expect(routeText(s, 1_000 + CHAT_IDLE_MS)).toBe("agent");
    expect(routeText(s, 1_000 + CHAT_IDLE_MS + 1)).toBe("expired");
    closeChat(s);
    expect(routeText(s, 2_000)).toBe("draft");
  });

  it("any user activity while open refreshes the idle clock; after expiry it reports expired", () => {
    const s = emptyChatState();
    beginTurn(s, "hi", 1, 1, 0);
    finishTurn(s, "final", "hello");
    const photoAt = 20 * 60 * 1000;
    expect(noteUserActivity(s, photoAt)).toBe("refreshed"); // a photo / command / button 20 min later
    expect(routeText(s, photoAt + 25 * 60 * 1000)).toBe("agent"); // 45 min after the turn, 25 after the photo
    expect(noteUserActivity(s, photoAt + CHAT_IDLE_MS + 1)).toBe("expired");
    closeChat(s);
    expect(noteUserActivity(s, photoAt + CHAT_IDLE_MS + 2)).toBe("none");
  });

  it("402 (no credits / no user) closes chat mode, so the next text drafts; other refusals keep it open", () => {
    for (const end of ["no_credits", "no_user"] as const) {
      const s = emptyChatState();
      beginTurn(s, "help me", 1, 1, 0);
      expect(finishTurn(s, end, null)).toEqual({ updateId: 1, rounds: 0 });
      expect(s.open).toBe(false);
      expect(s.transcript).toEqual([]); // the unanswered message is not replayed later
      expect(routeText(s, 1)).toBe("draft");
    }
    const s = emptyChatState();
    beginTurn(s, "too long?", 1, 1, 0);
    finishTurn(s, "rejected", null);
    expect(routeText(s, 1)).toBe("agent");
  });

  it("a batch with a destructive call pauses, others run, round 5 stops locally", () => {
    expect(toolCallsAction(calls("add_item", "complete_item"), 0)).toBe("execute");
    expect(toolCallsAction(calls("add_item", "delete_item"), 0)).toBe("pause");
    expect(toolCallsAction(calls("move_item"), 1)).toBe("pause");
    expect(toolCallsAction(calls("clear_completed_items"), 2)).toBe("pause");
    expect(toolCallsAction(calls("add_item"), MAX_TOOL_ROUNDS)).toBe("limit");
  });
});

describe("closing a turn keeps the transcript valid", () => {
  it("a new message while a card is pending declines every call of the batch first", () => {
    const s = emptyChatState();
    beginTurn(s, "delete milk and add bread", 1, 10, 0);
    const batch = calls("delete_item", "add_item");
    s.pending = { turnSeq: s.turnSeq, calls: batch, records: {}, cardText: "card", cardMessageId: 77 };

    const closed = closeTurn(s)!;
    expect(closed.declined).toEqual(batch);
    expect(closed.cardMessageId).toBe(77);
    beginTurn(s, "never mind, what's on my list?", 1, 11, 1);

    expect(isTranscriptValid(s.transcript)).toBe(true);
    expect(s.transcript.map((e) => e.role)).toEqual(["user", "model", "tool", "user"]);
    const results = (s.transcript[2] as { tool_results: Array<{ id: string; result: unknown }> }).tool_results;
    expect(results).toEqual([
      { id: "call_0", name: "delete_item", result: { status: "declined" } },
      { id: "call_1", name: "add_item", result: { status: "declined" } },
    ]);
    expect(s.pending).toBeUndefined();
    expect(s.turn?.requestId).toBe("tg-chat-1-2");
  });

  it("an interrupted batch keeps the applied results; a turn that produced nothing drops its message", () => {
    const s = emptyChatState();
    beginTurn(s, "two things", 1, 10, 0);
    const batch = calls("add_item", "add_item");
    s.turn!.inflight = { calls: batch, records: { "0": { plan: { result: { status: "success" }, ops: [] }, applied: true } } };
    closeTurn(s);
    expect(isTranscriptValid(s.transcript)).toBe(true);
    const results = (s.transcript[2] as { tool_results: Array<{ result: unknown }> }).tool_results.map((r) => r.result);
    expect(results).toEqual([{ status: "success" }, { status: "error", detail: "interrupted" }]);

    const empty = emptyChatState();
    beginTurn(empty, "lost", 1, 20, 0);
    closeTurn(empty);
    expect(empty.transcript).toEqual([]);
  });
});

describe("parseAgentResponse", () => {
  it("maps 402 reasons, 400, and the three success types", () => {
    expect(parseAgentResponse(402, { reason: "no_user" })).toEqual({ kind: "no_user" });
    expect(parseAgentResponse(402, { reason: "insufficient_credits" })).toEqual({ kind: "no_credits" });
    expect(parseAgentResponse(400, { error: "transcript too long" })).toEqual({ kind: "rejected", error: "transcript too long" });
    expect(parseAgentResponse(200, { success: true, type: "final", content: " Done " })).toEqual({ kind: "final", content: "Done" });
    const tc = parseAgentResponse(200, {
      success: true,
      type: "tool_calls",
      tool_calls: [{ id: "a", name: "add_item", args: { item_text: "x" }, thought_signature: "QUJD" }, { name: "find_items" }],
    });
    expect(tc).toEqual({
      kind: "tool_calls",
      calls: [
        { id: "a", name: "add_item", args: { item_text: "x" }, thought_signature: "QUJD" },
        { id: "call_1", name: "find_items", args: {} },
      ],
    });
  });

  it("cleans options and degrades to a final below 2; malformed or 5xx → null (retry)", () => {
    expect(parseAgentResponse(200, { success: true, type: "options", prompt: "Which?", options: ["A", " a ", "", "B"] })).toEqual({
      kind: "options",
      prompt: "Which?",
      options: ["A", "B"],
    });
    expect(parseAgentResponse(200, { success: true, type: "options", prompt: "Which?", options: ["A"] })).toEqual({
      kind: "final",
      content: "Which?",
    });
    expect(parseAgentResponse(200, { success: true, type: "tool_calls", tool_calls: [] })).toBeNull();
    expect(parseAgentResponse(200, { success: false })).toBeNull();
    expect(parseAgentResponse(500, { error: "Agent step failed" })).toBeNull();
  });
});

describe("chat rendering", () => {
  it("splits long answers at the Telegram limit and puts «End chat» under the last chunk", () => {
    const long = Array.from({ length: 300 }, (_, i) => `Line number ${i} of a long answer.`).join("\n");
    const msgs = renderReply(long, t("en"), [t("en").tzUnknownHint]);
    expect(msgs.length).toBeGreaterThan(1);
    for (const m of msgs) expect(m.text.length).toBeLessThanOrEqual(TELEGRAM_TEXT_MAX);
    expect(msgs.slice(0, -1).every((m) => m.keyboard === undefined)).toBe(true);
    expect(decodeCallback((msgs.at(-1)!.keyboard!.inline_keyboard[0]![0] as { callback_data: string }).callback_data)).toEqual({ t: "endChat" });
    expect(msgs.at(-1)!.text.endsWith(t("en").tzUnknownHint)).toBe(true);
  });

  it("splitText never loses text", () => {
    const text = "word ".repeat(3000).trim();
    expect(splitText(text, 100).join(" ")).toBe(text);
  });

  it("strips Markdown marks the plain-text message would show", () => {
    expect(plainText("## Plan\n**Milk** and __eggs__\n* one\n- two")).toBe("Plan\nMilk and eggs\n• one\n• two");
  });

  it("options carry an index (≤64 bytes), not the label", () => {
    const m = renderOptions("Which list?", ["Groceries for the whole week and more".repeat(3), "Work"], 123456789, t("ru"));
    const rows = m.keyboard!.inline_keyboard;
    expect(rows).toHaveLength(3);
    const first = rows[0]![0] as { text: string; callback_data: string };
    expect(first.text.length).toBeLessThanOrEqual(60);
    expect(byteLength(first.callback_data)).toBeLessThanOrEqual(CALLBACK_MAX_BYTES);
    expect(decodeCallback(first.callback_data)).toEqual({ t: "option", turn: 123456789, index: 0 });
  });

});

describe("confirmation card — built from the plans, applied as planned", () => {
  /** «Groceries» has «milk»; «Work» (most recently updated) has «milk chocolate bar». */
  function lists(): OwnedChecklist[] {
    const groceries = createEmptyChecklist("Groceries", ["milk", "bread"], 1);
    groceries.updatedAt = 1;
    const work = createEmptyChecklist("Work", ["milk chocolate bar", "report"], 2);
    work.updatedAt = 2;
    return [
      { checklist: groceries, ownerId: "g" },
      { checklist: work, ownerId: "g" },
    ];
  }
  const rowTexts = (l: OwnedChecklist) => parseFillItems(viewFill(l.checklist)!.itemsJson).map((r) => r.text);

  it("names the row and list that will really change, and says when a call changes nothing", () => {
    const state = emptyChatState();
    const turn = beginTurn(state, "delete milk", 42, 1, 0);
    const batch: AgentToolCall[] = [
      { id: "1", name: "delete_item", args: { item_text: "milk" } }, // no hint → most recently updated = Work
      { id: "2", name: "add_item", args: { item_text: "eggs", checklist_hint: "groc" } },
      { id: "3", name: "set_item_reminder", args: { item_text: "bread", checklist_hint: "work", when_iso: "2026-06-01T09:00:00" } },
      { id: "4", name: "delete_item", args: { item_text: "caviar", checklist_hint: "Groceries" } },
      { id: "5", name: "move_item", args: { item_text: "report", to_checklist_hint: "o" } }, // «Groceries» and «Work» both contain "o"
    ];
    const records = planBatch(batch, lists(), turn, "Etc/GMT-3", 10);
    const card = renderConfirmCard(batch, records, turn.seq, t("en"), "Etc/GMT-3");
    expect(card.text).toBe(
      "Do this?\n\n" +
        "1. 🗑 Delete «milk chocolate bar» from «Work»\n" +
        "2. Add «eggs» to «Groceries»\n" +
        "3. Remind about «bread» at 2026-06-01 09:00 — not found — nothing will change\n" +
        "4. 🗑 Delete «caviar» from «Groceries» — not found — nothing will change\n" +
        "5. ↪ Move «report» to «o» — several lists match («Groceries», «Work») — nothing will change",
    );
    const [yes, no] = card.keyboard.inline_keyboard[0]! as Array<{ callback_data: string }>;
    expect(decodeCallback(yes!.callback_data)).toEqual({ t: "confirm", turn: turn.seq, yes: true });
    expect(decodeCallback(no!.callback_data)).toEqual({ t: "confirm", turn: turn.seq, yes: false });
  });

  it("«Yes» applies exactly the planned ids — even if the default list changed in between", () => {
    const state = emptyChatState();
    const turn = beginTurn(state, "delete milk", 42, 1, 0);
    const batch: AgentToolCall[] = [{ id: "1", name: "delete_item", args: { item_text: "milk" } }];
    const atCard = lists();
    const records = planBatch(batch, atCard, turn, null, 10);

    // Between card and tap the user touches Groceries: it is now the most recently updated list.
    const fresh = structuredClone(atCard);
    fresh[0]!.checklist.updatedAt = 99;
    const changed = applyOps(fresh, records["0"]!.plan.ops, "g", 100);

    expect(changed.map((c) => c.checklist.name)).toEqual(["Work"]);
    expect(rowTexts(fresh[1]!)).toEqual(["report"]);
    expect(rowTexts(fresh[0]!)).toEqual(["milk", "bread"]); // re-resolving would have hit Groceries
  });

  it("plans later calls against the effects of earlier ones (create, then add to it) without writing", () => {
    const state = emptyChatState();
    const turn = beginTurn(state, "x", 42, 1, 0);
    const input = lists();
    const records = planBatch(
      [
        { id: "1", name: "create_checklist", args: { name: "Climbing" } },
        { id: "2", name: "add_item", args: { item_text: "chalk", checklist_hint: "climb" } },
        { id: "3", name: "delete_item", args: { item_text: "bread" } },
      ],
      input,
      turn,
      null,
      10,
    );
    expect(records["1"]!.plan.preview).toEqual({ kind: "add", texts: ["chalk"], list: "Climbing" });
    expect(input).toHaveLength(2); // the caller's lists are untouched
    expect(rowTexts(input[0]!)).toEqual(["milk", "bread"]);
  });
});
