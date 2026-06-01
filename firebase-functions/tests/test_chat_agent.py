"""
chat_agent (Phase 2 agentic bridge) tests — hits the DEPLOYED Cloud Function.

chat_agent is the stateless "next-step oracle": the client POSTs the structured
transcript so far, the server rebuilds Gemini `contents`, calls generate_content
with the tool catalog, and returns either tool_calls (the client executes them)
or a final message. This locks the four behaviours the bridge must guarantee:

  (i)   an explicit "add" command emits an add_item(s) tool_call (not prose);
  (ii)  charge-once-per-turn: a follow-up round (transcript already has a `tool`
        turn) does NOT deduct credits again;
  (iii) a pure Q&A turn returns type:"final";
  (iv)  the server round-cap returns a graceful final without erroring.

RED until chat_agent is deployed (404/Not Found) and the function calling works;
GREEN after `gcloud functions deploy chat_agent ...`.

Run:  python -m pytest firebase-functions/tests/test_chat_agent.py -x
  or: python firebase-functions/tests/test_chat_agent.py

Each test that reaches Gemini spends ~3 credits on a throwaway user. LLM output
is non-deterministic — assertions check structure (type / tool name / credit
delta), never exact wording.
"""

import json
import urllib.request
import urllib.error

BASE = "https://us-central1-aichecklists-40230.cloudfunctions.net"

ADD_TOOL_NAMES = {"add_item", "add_items", "create_checklist"}


def _post(path, payload, timeout=90):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")


def _new_user():
    status, body = _post("/register_user", {
        "device_id": "chat-agent-test",
        "app_version": "1.16.0",
        "platform": "test",
    })
    assert status == 200, f"register_user failed: {status} {body}"
    return json.loads(body)["user_id"]


def _step(user_id, transcript, locale="ru", checklists=None):
    status, body = _post("/chat_agent", {
        "user_id": user_id,
        "locale": locale,
        "timezone_offset_minutes": 180,
        "checklists_summary": checklists or [],
        "transcript": transcript,
    })
    assert status == 200, f"chat_agent {status}: {body}"
    data = json.loads(body)
    assert data.get("success") is True, f"success != true: {body}"
    assert data.get("type") in ("tool_calls", "final"), f"bad type: {body}"
    return data


def test_add_command_emits_tool_call():
    """'добавь молоко и хлеб' must emit an add_item(s)/create_checklist call, not prose."""
    uid = _new_user()
    result = _step(uid, [
        {"role": "user", "text": "добавь молоко и хлеб в покупки"},
    ], checklists=[{"name": "Покупки", "totalItems": 0, "doneItems": 0}])
    assert result["type"] == "tool_calls", f"expected tool_calls, got: {result}"
    names = {c.get("name") for c in result.get("tool_calls", [])}
    assert names & ADD_TOOL_NAMES, f"expected an add/create tool, got {names}"


def test_charge_once_per_turn():
    """First round deducts CHAT_AGENT_COST; a follow-up round with a tool turn deducts 0."""
    uid = _new_user()

    # Round 1 — no tool turn yet → charged. Record the post-charge balance.
    r1 = _step(uid, [
        {"role": "user", "text": "добавь молоко в покупки"},
    ], checklists=[{"name": "Покупки", "totalItems": 0, "doneItems": 0}])
    credits_after_round_1 = r1["credits_remaining"]

    # Round 2 — transcript now contains a `tool` turn → server must reserve 0.
    r2 = _step(uid, [
        {"role": "user", "text": "добавь молоко в покупки"},
        {"role": "model", "tool_calls": [
            {"id": "c1", "name": "add_item",
             "args": {"checklist_hint": "покупки", "item_text": "молоко"}}]},
        {"role": "tool", "tool_results": [
            {"id": "c1", "name": "add_item",
             "result": {"status": "success", "added": 1, "checklist": "Покупки"}}]},
    ], checklists=[{"name": "Покупки", "totalItems": 1, "doneItems": 0}])

    assert r2["credits_remaining"] == credits_after_round_1, (
        f"follow-up round deducted credits: {credits_after_round_1} -> "
        f"{r2['credits_remaining']}"
    )


def test_pure_qa_returns_final():
    """A turn that needs no action returns type:'final'."""
    uid = _new_user()
    result = _step(uid, [
        {"role": "user", "text": "спасибо!"},
    ])
    assert result["type"] == "final", f"expected final, got: {result}"
    assert (result.get("content") or "").strip(), "final content must be non-empty"


def test_round_cap_returns_final():
    """Five model turns hit the server cap → graceful final, no error, no Gemini call."""
    uid = _new_user()
    transcript = [{"role": "user", "text": "помоги разобраться со списками"}]
    for i in range(5):  # 5 model turns == CHAT_AGENT_MAX_ROUNDS
        transcript.append({"role": "model", "tool_calls": [
            {"id": f"c{i}", "name": "find_items", "args": {"query": "x"}}]})
        transcript.append({"role": "tool", "tool_results": [
            {"id": f"c{i}", "name": "find_items",
             "result": {"status": "success", "found": 0}}]})
    result = _step(uid, transcript)
    assert result["type"] == "final", f"round-cap must return final, got: {result}"


if __name__ == "__main__":
    test_add_command_emits_tool_call()
    print("PASS: add command emits tool_call")
    test_charge_once_per_turn()
    print("PASS: charge once per turn")
    test_pure_qa_returns_final()
    print("PASS: pure Q&A returns final")
    test_round_cap_returns_final()
    print("PASS: round cap returns final")
    print("ALL chat_agent tests passed")
