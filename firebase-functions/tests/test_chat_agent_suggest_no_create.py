"""
chat_agent — "suggest ideas for THIS checklist" must not create a NEW checklist.

Bug (Amplitude prod 786722, 1.18.x, 2026-07-23, thumbs-down x2): on the home screen the
user asked "Generate fresh ideas for items I could add to this checklist." and the agent
CREATED a brand-new checklist ("Gemini") instead of suggesting items for one of the user's
existing lists. Two failures: wrong tool (create_checklist) + lost the "this/the checklist =
an EXISTING list" meaning. See docs/todos/2026-07-23-aichat-fullchat-suggest-creates-wrong-checklist.md.

Fix (CHAT_AGENT_SYSTEM_TEMPLATE, prompts_private.py): a request about "this"/"the" checklist
("fresh ideas for items I could add", "what's missing", "what else to add") ALWAYS refers to
an existing list — NEVER create_checklist for it; if no Current checklist is in context and the
user names none, present_options with the existing list names, do not invent a new list.

  RED  against the currently-deployed (old) prompt: create_checklist can appear.
  GREEN after `gcloud functions deploy chat_agent ...`: create_checklist must NOT appear on
        round 1 for this "add to this checklist" request when the user has existing lists and
        no Current checklist is set — the agent must read/suggest/present_options instead.

LLM output is non-deterministic — the assertion checks tool ROUTING (create_checklist absent),
never exact wording, and runs the scenario a few times to beat stochasticity.

Run:  python -m pytest firebase-functions/tests/test_chat_agent_suggest_no_create.py -x
  or: python firebase-functions/tests/test_chat_agent_suggest_no_create.py
"""

import json
import urllib.request
import urllib.error

BASE = "https://us-central1-aichecklists-40230.cloudfunctions.net"

# Home screen: the user has several existing lists, none is "open" (no context_checklist).
EXISTING_LISTS = [
    {"name": "Work tasks", "totalItems": 6, "doneItems": 2},
    {"name": "Groceries", "totalItems": 4, "doneItems": 0},
    {"name": "Weekend plans", "totalItems": 3, "doneItems": 1},
]
SUGGEST_Q = "Generate fresh ideas for items I could add to this checklist."

# Substrings signalling the agent claimed it created a list in a text reply.
CREATED_MARKERS = ("i've created", "i have created", "created a new checklist",
                   "created a checklist", "new checklist called")


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
        "device_id": "chat-agent-suggest-test",
        "app_version": "1.18.3",
        "platform": "test",
    })
    assert status == 200, f"register_user failed: {status} {body}"
    return json.loads(body)["user_id"]


def _step_home_screen(user_id, question, locale="en"):
    """A turn on the HOME screen — deliberately no context_checklist."""
    payload = {
        "user_id": user_id,
        "locale": locale,
        "timezone_offset_minutes": 0,
        "checklists_summary": EXISTING_LISTS,
        "transcript": [{"role": "user", "text": question}],
        # NB: no "context_checklist" — this is the exact scenario that produced "Gemini".
    }
    status, body = _post("/chat_agent", payload)
    assert status == 200, f"chat_agent {status}: {body}"
    data = json.loads(body)
    assert data.get("success") is True, f"success != true: {body}"
    return data


def _assert_no_new_checklist_created(result):
    """The core contract: 'add to this checklist' must NOT create a new list."""
    if result.get("type") == "tool_calls":
        names = [c.get("name") for c in result.get("tool_calls", [])]
        assert "create_checklist" not in names, (
            f"BUG: agent called create_checklist for an 'add to this checklist' request "
            f"(tools={names}). Expected read_checklist / add_items / present_options against "
            f"an EXISTING list, never a new one."
        )
    content = (result.get("content") or "").lower()
    for marker in CREATED_MARKERS:
        assert marker not in content, (
            f"BUG: agent claimed it created a checklist in a text reply: {content!r}"
        )


def test_suggest_ideas_does_not_create_new_checklist():
    uid = _new_user()
    result = _step_home_screen(uid, SUGGEST_Q)
    _assert_no_new_checklist_created(result)


if __name__ == "__main__":
    RUNS = 3  # beat LLM stochasticity — the bug must not appear in ANY run post-fix.
    for i in range(1, RUNS + 1):
        uid = _new_user()
        result = _step_home_screen(uid, SUGGEST_Q)
        kind = result.get("type")
        names = [c.get("name") for c in result.get("tool_calls", [])] if kind == "tool_calls" else []
        print(f"run {i}: type={kind} tools={names} content={ (result.get('content') or '')[:80]!r}")
        _assert_no_new_checklist_created(result)
    print(f"PASS: create_checklist never appeared across {RUNS} runs")
