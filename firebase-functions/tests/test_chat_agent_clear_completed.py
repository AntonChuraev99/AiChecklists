"""
chat_agent "delete completed items" tests — hits the DEPLOYED Cloud Function.

Locks the fix for two device-reported bad answers (one root: the agent had no
bulk clear-completed tool):

  bug #2 — a checklist open, "удали выполненные" was mis-parsed as a literal
           single item named "выполненные" → delete_item(item_text="выполненные")
           → the client searched for that item → NotFound.
  bug #1 — no checklist open, the agent asked "Из какого чеклиста…?" as prose
           (type:"final") with no tappable options.

Fix: a clear_completed_items(checklist_hint?) tool. With a checklist in context the
agent targets it; with several lists and no context it must NOT dead-end in prose —
it either calls the tool (client resolves the ambiguous hint into its which-list
picker) or calls present_options.

RED until `gcloud functions deploy chat_agent ...` ships the new tool + prompt;
GREEN after. LLM output is non-deterministic — assertions check STRUCTURE (tool
name / type), never wording. Each Gemini turn spends ~3 credits on a throwaway user.

Run:  python -m pytest firebase-functions/tests/test_chat_agent_clear_completed.py -x
  or: python firebase-functions/tests/test_chat_agent_clear_completed.py
"""

import json
import urllib.request
import urllib.error

BASE = "https://us-central1-aichecklists-40230.cloudfunctions.net"


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
        "device_id": "clear-completed-test",
        "app_version": "1.17.0",
        "platform": "test",
    })
    assert status == 200, f"register_user failed: {status} {body}"
    return json.loads(body)["user_id"]


def _step(user_id, transcript, checklists, locale="ru",
          context_checklist=None, supports_options=False):
    payload = {
        "user_id": user_id,
        "locale": locale,
        "timezone_offset_minutes": 180,
        "checklists_summary": checklists,
        "transcript": transcript,
        "supports_options": supports_options,
    }
    if context_checklist is not None:
        payload["context_checklist"] = context_checklist
    status, body = _post("/chat_agent", payload)
    assert status == 200, f"chat_agent {status}: {body}"
    data = json.loads(body)
    assert data.get("success") is True, f"success != true: {body}"
    return data


def _tool_names(result):
    return {c.get("name") for c in result.get("tool_calls", [])}


def test_clear_completed_with_open_checklist_emits_bulk_tool():
    """bug #2: an open checklist + 'удали выполненные' → clear_completed_items,
    NEVER delete_item(item_text≈'выполненные') and NEVER prose."""
    uid = _new_user()
    result = _step(
        uid,
        [{"role": "user", "text": "удали выполненные"}],
        checklists=[{"name": "Покупки", "totalItems": 4, "doneItems": 2}],
        context_checklist={"name": "Покупки"},
    )
    assert result.get("type") == "tool_calls", f"expected tool_calls, got: {result}"
    names = _tool_names(result)
    assert "clear_completed_items" in names, f"expected clear_completed_items, got {names}"
    # It must not fall back to a literal single-item delete of the word "выполненные".
    for c in result.get("tool_calls", []):
        if c.get("name") == "delete_item":
            item = ((c.get("args") or {}).get("item_text") or "").lower()
            assert "выполнен" not in item, f"mis-parsed as literal delete: {c}"


def test_clear_completed_ambiguous_never_dead_ends_in_prose():
    """bug #1: no checklist open + several lists → must NOT ask in prose.
    Acceptable: call clear_completed_items (client resolves the ambiguous hint into
    its which-list picker) OR present_options. A type:'final' prose question fails."""
    uid = _new_user()
    result = _step(
        uid,
        [{"role": "user", "text": "удали выполненные пункты"}],
        checklists=[
            {"name": "Покупки", "totalItems": 3, "doneItems": 1},
            {"name": "Работа", "totalItems": 5, "doneItems": 2},
            {"name": "Дом", "totalItems": 2, "doneItems": 2},
        ],
        supports_options=True,
    )
    rtype = result.get("type")
    if rtype == "options":
        return  # offered the checklists as chips — good
    assert rtype == "tool_calls", (
        f"dead-ended (expected tool_calls or options, got {rtype}): {result}"
    )
    names = _tool_names(result)
    assert "clear_completed_items" in names, (
        f"ambiguous clear-completed must route through the bulk tool, got {names}"
    )


if __name__ == "__main__":
    test_clear_completed_with_open_checklist_emits_bulk_tool()
    print("PASS: open checklist → clear_completed_items (not literal delete)")
    test_clear_completed_ambiguous_never_dead_ends_in_prose()
    print("PASS: ambiguous → tool or options, never prose")
    print("ALL clear_completed tests passed")
