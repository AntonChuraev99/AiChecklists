"""
chat_agent — reasoning-question tool-routing tests (hits the DEPLOYED Cloud Function).

Bug (Amplitude debug 787810, 2026-06-02): the checklist-detail reasoning chips
("What's missing from this checklist?", "Give me a short summary of my progress.",
"Suggest more items for this checklist.") were answered with "Nothing matches «…»".
Root cause on the server side: for these WHOLE-LIST questions the agent picked the
find_items tool and passed the user's entire question as the search `query` — a
substring search that finds nothing -> the client renders chat_dispatch_find_no_match
("Nothing matches"). The correct tool is read_checklist(name=<current list>), which
returns the list contents so the agent can reason about what's missing / progress.

This test locks the fix in CHAT_AGENT_SYSTEM_TEMPLATE + context_block:

  (i)  for a whole-list reasoning question with a Current checklist in context, the
       agent's first tool call must NOT be find_items carrying the question text
       (the exact mis-fire that produced "Nothing matches"); read_checklist is the
       expected choice;
  (ii) after a read_checklist result is fed back, the final answer must be a real
       reasoning answer, never a "nothing matches" deflection.

RED until chat_agent is redeployed with the updated prompt; GREEN after
`gcloud functions deploy chat_agent ...`.

Run:  python -m pytest firebase-functions/tests/test_chat_agent_read_checklist.py -x
  or: python firebase-functions/tests/test_chat_agent_read_checklist.py

Each test that reaches Gemini spends ~3 credits on a throwaway user. LLM output is
non-deterministic — assertions check tool ROUTING (which tool, what query shape) and
the absence of the "nothing matches" failure string, never exact wording.
"""

import json
import urllib.request
import urllib.error

BASE = "https://us-central1-aichecklists-40230.cloudfunctions.net"

CONTEXT_LIST = "Trip to Japan"
CONTEXT_ITEMS = ["Passport", "Plane tickets", "Hotel booking"]

# Whole-list reasoning questions sent by the checklist-detail chips.
WHATS_MISSING_Q = "What's missing from this checklist?"
SUMMARY_Q = "Give me a short summary of my progress."
ADD_ITEMS_Q = "Suggest more items for this checklist."

# Substrings that signal the bug leaked through to the final answer.
NOTHING_MATCHES_MARKERS = ("nothing matches", "no items match", "couldn't find", "did not find")


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
        "device_id": "chat-agent-read-test",
        "app_version": "1.16.0",
        "platform": "test",
    })
    assert status == 200, f"register_user failed: {status} {body}"
    return json.loads(body)["user_id"]


def _step(user_id, transcript, *, context_name=CONTEXT_LIST, checklists=None, locale="en"):
    payload = {
        "user_id": user_id,
        "locale": locale,
        "timezone_offset_minutes": 0,
        "checklists_summary": checklists or [
            {"name": CONTEXT_LIST, "totalItems": len(CONTEXT_ITEMS), "doneItems": 1},
        ],
        "transcript": transcript,
    }
    if context_name:
        payload["context_checklist"] = {"name": context_name}
    status, body = _post("/chat_agent", payload)
    assert status == 200, f"chat_agent {status}: {body}"
    data = json.loads(body)
    assert data.get("success") is True, f"success != true: {body}"
    assert data.get("type") in ("tool_calls", "final"), f"bad type: {body}"
    return data


def _assert_not_find_items_with_question(result, question):
    """Round 1 must not mis-route the whole-list question to find_items(query=question)."""
    if result["type"] != "tool_calls":
        # Agent answered without a tool — acceptable (it can reason from the summary),
        # as long as it did not deflect with "nothing matches" (checked separately).
        return
    calls = result.get("tool_calls", [])
    for c in calls:
        if c.get("name") == "find_items":
            query = (c.get("args") or {}).get("query", "") or ""
            # The exact bug: the user's whole sentence shipped as a substring query.
            assert query.strip().lower() != question.strip().lower(), (
                f"REGRESSION: find_items called with the full question as query "
                f"({query!r}) — this is the 'Nothing matches' mis-route. "
                f"Expected read_checklist for a whole-list question."
            )
            # Even a long free-text query (>3 words) is the same class of mistake.
            assert len(query.split()) <= 3, (
                f"find_items query looks like a sentence, not an item keyword: {query!r}"
            )
    # Positive signal: a read tool was chosen, and for whole-list questions it should be
    # read_checklist. (Not a hard failure if the model answered from the summary instead.)
    names = {c.get("name") for c in calls}
    if names & {"find_items", "read_checklist"}:
        assert "read_checklist" in names, (
            f"whole-list question used {names} — expected read_checklist, not find_items"
        )


def _read_result_followup(question):
    """Transcript where the agent called read_checklist and the client returned items."""
    return [
        {"role": "user", "text": question},
        {"role": "model", "tool_calls": [
            {"id": "r1", "name": "read_checklist", "args": {"name": CONTEXT_LIST}}]},
        {"role": "tool", "tool_results": [
            {"id": "r1", "name": "read_checklist", "result": {
                "status": "success",
                "checklist": CONTEXT_LIST,
                "items": [{"text": t, "checked": (t == "Passport")} for t in CONTEXT_ITEMS],
            }}]},
    ]


def test_whats_missing_routes_to_read_checklist_not_find_items():
    uid = _new_user()
    result = _step(uid, [{"role": "user", "text": WHATS_MISSING_Q}])
    _assert_not_find_items_with_question(result, WHATS_MISSING_Q)


def test_summary_routes_to_read_checklist_not_find_items():
    uid = _new_user()
    result = _step(uid, [{"role": "user", "text": SUMMARY_Q}])
    _assert_not_find_items_with_question(result, SUMMARY_Q)


def test_add_items_routes_to_read_checklist_not_find_items():
    uid = _new_user()
    result = _step(uid, [{"role": "user", "text": ADD_ITEMS_Q}])
    _assert_not_find_items_with_question(result, ADD_ITEMS_Q)


def test_whats_missing_final_answer_is_not_nothing_matches():
    """After a read_checklist result, the final answer must reason — never 'nothing matches'."""
    uid = _new_user()
    result = _step(uid, _read_result_followup(WHATS_MISSING_Q))
    # The agent may do one more read round, but it must not deflect. Accept either a final
    # answer or a further read tool; reject any 'nothing matches' style content.
    content = (result.get("content") or "").lower()
    for marker in NOTHING_MATCHES_MARKERS:
        assert marker not in content, (
            f"final answer contains '{marker}' after read_checklist returned items: {content!r}"
        )
    # find_items must not appear at all on this whole-list reasoning follow-up.
    if result["type"] == "tool_calls":
        names = {c.get("name") for c in result.get("tool_calls", [])}
        assert "find_items" not in names, (
            f"follow-up used find_items for a whole-list question: {names}"
        )


if __name__ == "__main__":
    test_whats_missing_routes_to_read_checklist_not_find_items()
    print("PASS: what's-missing routes to read_checklist")
    test_summary_routes_to_read_checklist_not_find_items()
    print("PASS: summary routes to read_checklist")
    test_add_items_routes_to_read_checklist_not_find_items()
    print("PASS: add-items routes to read_checklist")
    test_whats_missing_final_answer_is_not_nothing_matches()
    print("PASS: final answer is not 'nothing matches'")
    print("ALL chat_agent read_checklist tests passed")
