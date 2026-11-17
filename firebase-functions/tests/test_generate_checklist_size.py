"""
generate_checklist size tests — hits the DEPLOYED Cloud Function.

User report: a checklist created from a short idea ("Pack for a beach trip" /
"Собраться на море") came back with 43 items. A list made from an idea alone
must stay focused (target 8-12 items, more only when the user explicitly asks for
a detailed / complete list). A list made FROM source content that already holds
items (pasted text, photo, PDF, link) must keep every one of them.

The requests mirror the real client (AnalyzeRepositoryImpl + FirebaseAiServiceImpl):
RawText -> prompt "Create a checklist based on: <text>", input_type "text",
input_data "<text>". The client sends no `locale`, so none is sent here either:
the RU case relies on the server detecting the language of the data.

Run:  python -m pytest firebase-functions/tests/test_generate_checklist_size.py -s
  or: python firebase-functions/tests/test_generate_checklist_size.py

Each generation spends 20 credits of a throwaway user (100 lifetime per user), so
one user covers all three cases. LLM output is non-deterministic — the idea bound
(5..14) carries slack around the 8-12 target; the source guard allows 2 merges.
"""

import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = "https://us-central1-aichecklists-40230.cloudfunctions.net"

IDEA_MIN_ITEMS = 5
IDEA_MAX_ITEMS = 14
SOURCE_ITEMS = [
    "Book the moving truck for Saturday",
    "Order 30 cardboard boxes",
    "Buy packing tape and markers",
    "Cancel the gym membership",
    "Transfer the internet contract",
    "Update the address at the bank",
    "Forward mail at the post office",
    "Defrost and clean the fridge",
    "Return the library books",
    "Pick up dry cleaning",
    "Photograph the meter readings",
    "Book the cleaning service for the old flat",
    "Collect the spare keys from the neighbour",
    "Disassemble the bed frame",
    "Pack the winter clothes",
    "Label the kitchen boxes",
    "Notify the landlord in writing",
    "Register at the new address",
    "Change the address for the car insurance",
    "Water the plants before leaving",
]
SOURCE_MIN_ITEMS = len(SOURCE_ITEMS) - 2  # 18 — allow the model to merge at most two lines

_user = {"id": None, "generations": 0}
CREDITS_PER_USER = 100
COST_PER_GENERATION = 20


def _post(path, payload, timeout=120):
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


def _user_id():
    """One throwaway user per run; a fresh one once its lifetime credits are spent.

    register_user is idempotent on device_id (a repeated id returns the SAME user and its
    already-spent wallet), so every registration needs a unique device id.
    """
    if _user["id"] is None or (_user["generations"] + 1) * COST_PER_GENERATION > CREDITS_PER_USER:
        status, body = _post("/register_user", {
            "device_id": f"generate-size-test-{uuid.uuid4().hex}",
            "app_version": "1.23.1",
            "platform": "test",
        })
        assert status == 200, f"register_user failed: {status} {body}"
        _user["id"] = json.loads(body)["user_id"]
        _user["generations"] = 0
    _user["generations"] += 1
    return _user["id"]


def _generate_from_text(text):
    status, body = _post("/generate_checklist", {
        "user_id": _user_id(),
        "is_premium": False,
        "prompt": f"Create a checklist based on: {text}",
        "input_type": "text",
        "input_data": text,
    })
    assert status == 200, f"generate_checklist {status}: {body}"
    data = json.loads(body)
    assert data.get("success") is True, f"success != true: {body}"
    return data


def _count(nodes):
    """(leaves, folders) across the whole nested tree — a leaf is a checkable item."""
    leaves, folders = 0, 0
    for n in nodes or []:
        if n.get("type") == "folder":
            folders += 1
            sub_leaves, sub_folders = _count(n.get("children"))
            leaves += sub_leaves
            folders += sub_folders
        else:
            leaves += 1
    return leaves, folders


def _report(label, data):
    leaves, folders = _count(data.get("items"))
    print(f"[{label}] leaves={leaves} folders={folders} model={data.get('model_id')} "
          f"name={data.get('checklist_name')!r}")
    return leaves


def _assert_idea_size(label, text):
    leaves = _report(label, _generate_from_text(text))
    assert IDEA_MIN_ITEMS <= leaves <= IDEA_MAX_ITEMS, (
        f"[{label}] idea-only list has {leaves} items, expected "
        f"{IDEA_MIN_ITEMS}..{IDEA_MAX_ITEMS} (target 8-12)"
    )


def test_idea_only_en_is_focused():
    _assert_idea_size("idea-en", "Pack for a beach trip")


def test_idea_only_ru_is_focused():
    _assert_idea_size("idea-ru", "Собраться на море")


def test_source_list_keeps_every_item():
    text = "My to-do list for the move:\n" + "\n".join(
        f"{i}. {item}" for i, item in enumerate(SOURCE_ITEMS, start=1)
    )
    leaves = _report("source-20", _generate_from_text(text))
    assert leaves >= SOURCE_MIN_ITEMS, (
        f"[source-20] source text had {len(SOURCE_ITEMS)} items, the list kept only {leaves} "
        f"(expected >= {SOURCE_MIN_ITEMS})"
    )


if __name__ == "__main__":
    fns = [test_idea_only_en_is_focused, test_idea_only_ru_is_focused, test_source_list_keeps_every_item]
    failed = 0
    for fn in fns:
        try:
            fn()
            print("PASS " + fn.__name__)
        except AssertionError as e:
            failed += 1
            print("FAIL %s: %s" % (fn.__name__, e))
    print("\n%d/%d passed" % (len(fns) - failed, len(fns)))
    sys.exit(1 if failed else 0)
