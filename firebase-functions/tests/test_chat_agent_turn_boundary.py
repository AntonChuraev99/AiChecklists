"""chat_agent turn-boundary + idempotent-reserve tests — OFFLINE (no deploy, no Gemini, no Firebase).

Unlike tests/test_chat_agent.py (which hits the DEPLOYED function and spends real credits),
this suite imports main.py with a FAKE in-memory Firestore and a FAKE Gemini client, so it can
assert on the REAL wallet delta of the REAL handler. Every credit assertion below reads the
fake `users/{uid}.ai_credits` doc that main.py's own transaction code wrote.

What it locks (Stage 3 server foundation, 2026-07-16):

  (1) TURN BOUNDARY — "first round" means "no tool turn AFTER the last user message", not
      "no tool turn anywhere in the array". Once the client persists the transcript, a session
      that ever ran a tool would otherwise be free forever (silent revenue loss).
  (2) BACK-COMPAT — today's store client (Android vc67 / web) sends a transcript whose tool
      entries can only ever appear after the last user message, so old and new formulas agree.
      These tests are green BOTH before and after the fix and must stay that way.
  (3) ROUND CAP — CHAT_AGENT_MAX_ROUNDS counts rounds of ONE turn, not of the whole session.
  (4) IDEMPOTENCY — an optional request_id makes the reserve replay-safe; no request_id keeps
      exactly today's path.
  (5) REFUND — refund only what THIS invocation actually deducted; a replay refunds nothing.

Run from firebase-functions/:
    python -m pytest tests/test_chat_agent_turn_boundary.py -q
"""

import os
import sys
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

import pytest
from flask import Flask

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

app = Flask(__name__)

START_BALANCE = 100
CHAT_AGENT_COST = 3  # mirrors main.CHAT_AGENT_COST / CHAT_COMPLETION_COST


# ---------------------------------------------------------------------------
# Fake Firestore — enough surface for reserve/refund + the reservation dedup doc.
# Keyed "collection/doc_id" so the credit_reservations doc id is asserted for real.
# ---------------------------------------------------------------------------

class _FakeSnapshot:
    def __init__(self, data):
        self._data = data
        self.exists = data is not None

    def get(self, field):
        return (self._data or {}).get(field)

    def to_dict(self):
        return dict(self._data) if self._data is not None else None


class _FakeDocRef:
    def __init__(self, store, key):
        self._store = store
        self.key = key

    def get(self, transaction=None):
        return _FakeSnapshot(self._store.get(self.key))

    def set(self, data):
        self._store[self.key] = dict(data)

    def update(self, data):
        self._store.setdefault(self.key, {}).update(data)

    def delete(self):
        self._store.pop(self.key, None)


class _FakeCollection:
    def __init__(self, store, name):
        self._store = store
        self._name = name

    def document(self, doc_id):
        return _FakeDocRef(self._store, f"{self._name}/{doc_id}")

    def add(self, data):  # credits_refund_log
        self._store.setdefault(f"{self._name}/__log__", []).append(dict(data))
        return None


class _FakeTransaction:
    """firestore.transactional is patched to identity, so this just applies writes."""

    def update(self, ref, data):
        ref.update(data)

    def set(self, ref, data):
        ref.set(data)


class FakeDb:
    def __init__(self):
        self.store = {}

    def collection(self, name):
        return _FakeCollection(self.store, name)

    def transaction(self):
        return _FakeTransaction()


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def main(monkeypatch):
    monkeypatch.setattr("firebase_admin._apps", {"[DEFAULT]": True})
    monkeypatch.setattr("firebase_admin.initialize_app", MagicMock())
    monkeypatch.setattr("firebase_admin.firestore.client", lambda: MagicMock())
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key")
    monkeypatch.setenv("REVENUECAT_API_KEY", "sk_fake_key")

    import importlib
    import main as main_mod
    importlib.reload(main_mod)

    fake_db = FakeDb()
    monkeypatch.setattr(main_mod, "db", fake_db)
    # Real transaction bodies, applied inline (same trick as test_main.py).
    monkeypatch.setattr("firebase_admin.firestore.transactional", lambda f: f)
    # Keep the A/B model resolver off Remote Config / Firestore.
    monkeypatch.setattr(
        main_mod, "resolve_experiment_model",
        lambda user_id, flow, default, data: (default, "control"),
    )
    monkeypatch.setattr(main_mod, "increment_usage", lambda *a, **k: None)
    return main_mod


class _FakeGeminiResponse:
    def __init__(self, text="Done."):
        self.text = text
        self.candidates = [SimpleNamespace(content=SimpleNamespace(parts=[]))]


def _set_balance(main, uid, credits):
    main.db.store[f"users/{uid}"] = {"ai_credits": credits}


def _balance(main, uid):
    return main.db.store[f"users/{uid}"]["ai_credits"]


def _run(main, transcript, uid="u1", request_id=None, gemini_raises=False):
    """Invoke the REAL chat_agent handler with a fake Gemini client."""
    payload = {
        "user_id": uid,
        "locale": "en",
        "timezone_offset_minutes": 0,
        "checklists_summary": [],
        "transcript": transcript,
    }
    if request_id is not None:
        payload["request_id"] = request_id

    req = MagicMock()
    req.method = "POST"
    req.get_json.return_value = payload

    gemini = MagicMock()
    if gemini_raises:
        gemini.models.generate_content.side_effect = RuntimeError("gemini boom")
    else:
        gemini.models.generate_content.return_value = _FakeGeminiResponse()

    with patch.object(main, "gemini_client", gemini):
        with app.test_request_context():
            resp = main.chat_agent(req)
    return resp, gemini


# ---------------------------------------------------------------------------
# Transcript builders
# ---------------------------------------------------------------------------

def _tool_round(i):
    """One agentic round: a model turn requesting a tool + the client's tool result."""
    return [
        {"role": "model", "tool_calls": [
            {"id": f"c{i}", "name": "find_items", "args": {"query": "x"}}]},
        {"role": "tool", "tool_results": [
            {"id": f"c{i}", "name": "find_items", "result": {"status": "success", "found": 0}}]},
    ]


# ===========================================================================
# (1) RED — the silent-free-turn bug that persisted transcripts will trigger
# ===========================================================================

def test_new_turn_after_past_tool_round_is_charged(main):
    """A NEW user message in a session that already ran a tool must cost CHAT_AGENT_COST.

    This is the persisted-transcript shape: tool entries exist, but they belong to a PAST
    turn — they sit BEFORE the last role=="user". Today's `has_tool_turn = any tool anywhere`
    reads them as "we are mid-turn" and skips the reserve entirely => every turn of the
    session is free forever. Fails on current code by design.
    """
    _set_balance(main, "u1", START_BALANCE)
    transcript = [
        {"role": "user", "text": "add milk to groceries"},
        *_tool_round(1),
        {"role": "model", "text": "Added milk."},
        {"role": "user", "text": "what is left to buy?"},  # <- NEW turn starts here
    ]
    resp, _ = _run(main, transcript)

    assert resp.status_code == 200, resp.get_json()
    assert _balance(main, "u1") == START_BALANCE - CHAT_AGENT_COST, (
        "new turn after a past tool round was NOT charged: balance "
        f"{_balance(main, 'u1')} (expected {START_BALANCE - CHAT_AGENT_COST})"
    )
    assert resp.get_json()["credits_remaining"] == START_BALANCE - CHAT_AGENT_COST


# ===========================================================================
# (2) BACK-COMPAT — today's store client must behave IDENTICALLY (green before AND after)
# ===========================================================================

def test_legacy_first_round_is_charged(main):
    """Today's prod shape: history seeded as user/model prose, newest user message last."""
    _set_balance(main, "u1", START_BALANCE)
    transcript = [
        {"role": "user", "text": "hi"},
        {"role": "model", "text": "Hello!"},
        {"role": "user", "text": "add milk to groceries"},
    ]
    resp, _ = _run(main, transcript)

    assert resp.status_code == 200, resp.get_json()
    assert _balance(main, "u1") == START_BALANCE - CHAT_AGENT_COST


def test_legacy_follow_up_round_is_free(main):
    """Round 2 of the SAME turn (tool result appended after the user message) reserves 0."""
    _set_balance(main, "u1", START_BALANCE)
    transcript = [
        {"role": "user", "text": "add milk to groceries"},
        *_tool_round(1),
    ]
    resp, _ = _run(main, transcript)

    assert resp.status_code == 200, resp.get_json()
    assert _balance(main, "u1") == START_BALANCE, "follow-up round must not deduct"


def test_legacy_first_round_insufficient_credits_is_402(main):
    """The 402 path is unchanged for the old contract."""
    _set_balance(main, "u1", CHAT_AGENT_COST - 1)
    resp, gemini = _run(main, [{"role": "user", "text": "add milk"}])

    assert resp.status_code == 402, resp.get_json()
    assert gemini.models.generate_content.call_count == 0, "402 must not reach Gemini"


def test_no_user_entry_keeps_whole_transcript_semantics(main):
    """Degenerate transcript with no user entry: fall back to scanning everything.

    Guards the boundary helper against an IndexError / accidental 'first round' on a shape
    the old formula handled as "has tool turn => not first round".
    """
    _set_balance(main, "u1", START_BALANCE)
    resp, _ = _run(main, [*_tool_round(1)])

    assert resp.status_code == 200, resp.get_json()
    assert _balance(main, "u1") == START_BALANCE


# ===========================================================================
# (3) ROUND CAP — cap counts the CURRENT turn only
# ===========================================================================

def test_past_tool_rounds_do_not_trip_the_round_cap(main):
    """5 tool rounds accumulated across PAST turns + a new user message => not capped.

    Today agent_round_count counts every tool_calls turn in the array, so a persisted session
    would return the cap message before Gemini is ever called — for every new message.
    """
    _set_balance(main, "u1", START_BALANCE)
    transcript = [{"role": "user", "text": "help me"}]
    for i in range(5):  # == CHAT_AGENT_MAX_ROUNDS, all in PAST turns
        transcript.extend(_tool_round(i))
        transcript.append({"role": "model", "text": f"Step {i} done."})
    transcript.append({"role": "user", "text": "now what about tomorrow?"})  # NEW turn

    resp, gemini = _run(main, transcript)

    assert resp.status_code == 200, resp.get_json()
    assert gemini.models.generate_content.call_count == 1, (
        "past-turn rounds tripped the per-turn cap: Gemini was never called"
    )
    assert resp.get_json()["content"] == "Done."


def test_current_turn_round_cap_still_returns_final(main):
    """5 tool rounds INSIDE the current turn => graceful cap final, no Gemini, no charge."""
    _set_balance(main, "u1", START_BALANCE)
    transcript = [{"role": "user", "text": "help me"}]
    for i in range(5):
        transcript.extend(_tool_round(i))

    resp, gemini = _run(main, transcript)

    assert resp.status_code == 200, resp.get_json()
    assert resp.get_json()["type"] == "final"
    assert gemini.models.generate_content.call_count == 0, "cap must short-circuit Gemini"
    assert _balance(main, "u1") == START_BALANCE


# ===========================================================================
# (4) IDEMPOTENCY — request_id makes the first-round reserve replay-safe
# ===========================================================================

def test_same_request_id_charges_once(main):
    """Two calls with the same request_id (client retried a dropped response) charge ONCE."""
    _set_balance(main, "u1", START_BALANCE)
    transcript = [{"role": "user", "text": "add milk to groceries"}]

    r1, _ = _run(main, transcript, request_id="req-abc")
    r2, _ = _run(main, transcript, request_id="req-abc")

    assert r1.status_code == 200 and r2.status_code == 200
    assert _balance(main, "u1") == START_BALANCE - CHAT_AGENT_COST, (
        f"retry double-charged: balance {_balance(main, 'u1')}"
    )
    assert r1.get_json()["credits_remaining"] == START_BALANCE - CHAT_AGENT_COST
    assert r2.get_json()["credits_remaining"] == START_BALANCE - CHAT_AGENT_COST, (
        "replay must report the balance recorded at first reserve"
    )


def test_different_request_ids_charge_separately(main):
    """Distinct turns (distinct request_ids) each pay — dedup must not swallow real turns."""
    _set_balance(main, "u1", START_BALANCE)
    transcript = [{"role": "user", "text": "add milk to groceries"}]

    _run(main, transcript, request_id="req-1")
    _run(main, transcript, request_id="req-2")

    assert _balance(main, "u1") == START_BALANCE - 2 * CHAT_AGENT_COST


def test_reservation_doc_is_namespaced_by_user(main):
    """Reuses the existing credit_reservations scheme: doc id == '{user_id}__{request_id}'."""
    _set_balance(main, "u1", START_BALANCE)
    _run(main, [{"role": "user", "text": "add milk"}], request_id="req-abc")

    assert "credit_reservations/u1__req-abc" in main.db.store
    doc = main.db.store["credit_reservations/u1__req-abc"]
    assert doc["cost"] == CHAT_AGENT_COST, "chat must reserve 3, never action_cost (30)"
    assert doc["remaining_after"] == START_BALANCE - CHAT_AGENT_COST


def test_both_reserve_paths_charge_the_same_cost(main):
    """The legacy (no request_id) path reserves CHAT_COMPLETION_COST, the idempotent path
    reserves CHAT_AGENT_COST. They are the same number today; if anyone drifts them, a turn's
    price would depend on whether the client happened to send a request_id."""
    assert main.CHAT_AGENT_COST == main.CHAT_COMPLETION_COST
    assert main.CHAT_AGENT_COST == CHAT_AGENT_COST, "test constant drifted from main.py"


def test_request_id_absent_uses_legacy_path_untouched(main):
    """No request_id => no dedup doc is written at all (today's exact behaviour)."""
    _set_balance(main, "u1", START_BALANCE)
    _run(main, [{"role": "user", "text": "add milk"}])

    assert not [k for k in main.db.store if k.startswith("credit_reservations/")]
    assert _balance(main, "u1") == START_BALANCE - CHAT_AGENT_COST


def test_replay_of_insufficient_balance_does_not_402(main):
    """A replay wins even if the wallet has since been spent down below cost."""
    _set_balance(main, "u1", CHAT_AGENT_COST)
    transcript = [{"role": "user", "text": "add milk"}]

    r1, _ = _run(main, transcript, request_id="req-abc")
    assert r1.status_code == 200
    assert _balance(main, "u1") == 0

    r2, _ = _run(main, transcript, request_id="req-abc")
    assert r2.status_code == 200, "replay of an already-paid turn must not 402"
    assert r2.get_json()["credits_remaining"] == 0


def test_follow_up_round_with_request_id_is_still_free(main):
    """request_id must not make a follow-up round start charging."""
    _set_balance(main, "u1", START_BALANCE)
    resp, _ = _run(
        main,
        [{"role": "user", "text": "add milk"}, *_tool_round(1)],
        request_id="req-round2",
    )

    assert resp.status_code == 200
    assert _balance(main, "u1") == START_BALANCE
    assert not [k for k in main.db.store if k.startswith("credit_reservations/")], (
        "a round that reserved nothing must not write a dedup doc"
    )


# ===========================================================================
# (5) REFUND — correct on both paths, never double
# ===========================================================================

def test_gemini_failure_refunds_legacy_path(main):
    _set_balance(main, "u1", START_BALANCE)
    resp, _ = _run(main, [{"role": "user", "text": "add milk"}], gemini_raises=True)

    assert resp.status_code == 500
    assert _balance(main, "u1") == START_BALANCE, "legacy refund did not restore the balance"


def test_gemini_failure_refunds_and_clears_reservation(main):
    """Fresh reserve + Gemini failure => refund AND drop the dedup doc, so the client's
    retry re-reserves cleanly instead of replaying a rolled-back reservation (a free turn)."""
    _set_balance(main, "u1", START_BALANCE)
    resp, _ = _run(main, [{"role": "user", "text": "add milk"}],
                   request_id="req-abc", gemini_raises=True)

    assert resp.status_code == 500
    assert _balance(main, "u1") == START_BALANCE
    assert "credit_reservations/u1__req-abc" not in main.db.store, (
        "refunded reservation must be rolled back, else the retry rides a free replay"
    )

    # The retry pays exactly once.
    r2, _ = _run(main, [{"role": "user", "text": "add milk"}], request_id="req-abc")
    assert r2.status_code == 200
    assert _balance(main, "u1") == START_BALANCE - CHAT_AGENT_COST


def test_replay_failure_does_not_refund_twice(main):
    """THE double-refund guard: a replay deducted nothing, so a Gemini failure on the replay
    must NOT hand back credits charged by the earlier invocation (that would mint credits)."""
    _set_balance(main, "u1", START_BALANCE)
    transcript = [{"role": "user", "text": "add milk"}]

    r1, _ = _run(main, transcript, request_id="req-abc")  # pays 3
    assert _balance(main, "u1") == START_BALANCE - CHAT_AGENT_COST

    r2, _ = _run(main, transcript, request_id="req-abc", gemini_raises=True)
    assert r2.status_code == 500
    assert _balance(main, "u1") == START_BALANCE - CHAT_AGENT_COST, (
        f"replay refunded credits it never reserved: balance {_balance(main, 'u1')}"
    )
    assert "credit_reservations/u1__req-abc" in main.db.store, (
        "a replay must not roll back the original reservation"
    )


def test_follow_up_round_failure_refunds_nothing(main):
    """Unchanged: a round that reserved 0 refunds 0."""
    _set_balance(main, "u1", START_BALANCE)
    resp, _ = _run(main, [{"role": "user", "text": "add milk"}, *_tool_round(1)],
                   gemini_raises=True)

    assert resp.status_code == 500
    assert _balance(main, "u1") == START_BALANCE
