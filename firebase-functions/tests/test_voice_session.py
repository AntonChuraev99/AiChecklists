"""Voice call — voice_session minutes + chat_agent voice mode. OFFLINE (no deploy, no Gemini,
no Firebase).

Same harness as tests/test_chat_agent_turn_boundary.py: main.py is imported with a FAKE
in-memory Firestore (here with sub-collections: users/{uid}/voice_sessions/{id},
users/{uid}/voice/usage) and a FAKE Gemini client, and `_voice_now` is driven by a test clock,
so every assertion reads what the REAL handlers wrote.

What it locks:
  (1) minutes are charged by the SERVER clock only, min(gap, 20 s) per beat; client fields
      claiming elapsed time are ignored;
  (2) free lifetime / premium daily (UTC day) / per-call ceiling end the call;
  (3) a session without a heartbeat for 45 s is closed lazily, the gap uncharged;
  (4) end is idempotent — a repeated end charges nothing;
  (5) chat_agent with a valid voice session reserves NO credit; an invalid one is an explicit
      error (no silent charge, no silent free turn); no `voice` keeps the text path;
  (6) voice turns are rate-limited per session (20/min, >= 1.5 s apart).

Run from firebase-functions/:
    python -m pytest tests/test_voice_session.py -q
"""

import os
import sys
from datetime import datetime, timezone
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

import pytest
from flask import Flask

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import voice_logic  # noqa: E402

app = Flask(__name__)

START_BALANCE = 100
CHAT_AGENT_COST = 3  # mirrors main.CHAT_AGENT_COST
# 2026-11-04 12:00:00 UTC — far from midnight, so a test spanning minutes stays on one UTC day.
T0 = datetime(2026, 11, 4, 12, 0, 0, tzinfo=timezone.utc).timestamp()


# ---------------------------------------------------------------------------
# Fake Firestore with sub-collections. Keys are full paths: "users/u1/voice/usage".
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

    def collection(self, name):
        return _FakeCollection(self._store, f"{self.key}/{name}")


class _FakeCollection:
    def __init__(self, store, path):
        self._store = store
        self._path = path

    def document(self, doc_id):
        return _FakeDocRef(self._store, f"{self._path}/{doc_id}")

    def add(self, data):
        self._store.setdefault(f"{self._path}/__log__", []).append(dict(data))
        return None


class _FakeTransaction:
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


class Clock:
    def __init__(self, t):
        self.t = t

    def __call__(self):
        return self.t

    def advance(self, sec):
        self.t += sec


# ---------------------------------------------------------------------------
# Fixtures + helpers
# ---------------------------------------------------------------------------

@pytest.fixture
def clock():
    return Clock(T0)


@pytest.fixture
def main(monkeypatch, clock):
    monkeypatch.setattr("firebase_admin._apps", {"[DEFAULT]": True})
    monkeypatch.setattr("firebase_admin.initialize_app", MagicMock())
    monkeypatch.setattr("firebase_admin.firestore.client", lambda: MagicMock())
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key")
    monkeypatch.setenv("REVENUECAT_API_KEY", "sk_fake_key")

    import importlib
    import main as main_mod
    importlib.reload(main_mod)

    monkeypatch.setattr(main_mod, "db", FakeDb())
    monkeypatch.setattr("firebase_admin.firestore.transactional", lambda f: f)
    monkeypatch.setattr(
        main_mod, "resolve_experiment_model",
        lambda user_id, flow, default, data: (default, "control"),
    )
    monkeypatch.setattr(main_mod, "increment_usage", lambda *a, **k: None)
    monkeypatch.setattr(main_mod, "_voice_now", clock)
    return main_mod


def _user(main, uid="u1", premium=False, credits=START_BALANCE):
    main.db.store[f"users/{uid}"] = {"ai_credits": credits, "is_premium": premium}


def _rc(main, **values):
    main.db.store["remote_config/current"] = dict(values)


def _usage(main, uid="u1"):
    return main.db.store.get(f"users/{uid}/voice/usage", {})


def _session(main, sid, uid="u1"):
    return main.db.store.get(f"users/{uid}/voice_sessions/{sid}")


def _req(payload):
    req = MagicMock()
    req.method = "POST"
    req.get_json.return_value = payload
    return req


def _voice(main, action, uid="u1", **fields):
    with app.test_request_context():
        resp = main.voice_session(_req({"user_id": uid, "action": action, **fields}))
    return resp, resp.get_json()


def _start(main, uid="u1", **fields):
    resp, body = _voice(main, "start", uid=uid, **fields)
    assert resp.status_code == 200, body
    return body["session_id"]


class _FakeGeminiResponse:
    def __init__(self, text="Added milk to Groceries."):
        self.text = text
        self.candidates = [SimpleNamespace(content=SimpleNamespace(parts=[]))]


def _agent(main, transcript=None, uid="u1", voice=None, **extra):
    payload = {
        "user_id": uid,
        "locale": "en",
        "timezone_offset_minutes": 0,
        "checklists_summary": [],
        "transcript": transcript or [{"role": "user", "text": "add milk to groceries"}],
        **extra,
    }
    if voice is not None:
        payload["voice"] = voice
    gemini = MagicMock()
    gemini.models.generate_content.return_value = _FakeGeminiResponse()
    with patch.object(main, "gemini_client", gemini):
        with app.test_request_context():
            resp = main.chat_agent(_req(payload))
    return resp, gemini


def _sent_config(gemini):
    return gemini.models.generate_content.call_args.kwargs["config"]


def _tool_names(config):
    return {
        fd.name
        for tool in (config.tools or [])
        for fd in (tool.function_declarations or [])
    }


# ===========================================================================
# Pure logic (voice_logic.py)
# ===========================================================================

def test_beat_charge_is_the_server_gap_capped_at_20s():
    s = {"last_beat_at": T0, "charged_seconds": 0}
    assert voice_logic.beat_charge(s, T0 + 15.9, 300, 600) == 15  # floored
    assert voice_logic.beat_charge(s, T0 + 35, 300, 600) == 20    # capped
    assert voice_logic.beat_charge(s, T0 + 46, 300, 600) == 0     # idle -> nothing
    assert voice_logic.beat_charge(s, T0 - 5, 300, 600) == 0      # clock skew never refunds


def test_beat_charge_is_cut_to_wallet_and_call_ceiling():
    assert voice_logic.beat_charge({"last_beat_at": T0, "charged_seconds": 0}, T0 + 15, 4, 600) == 4
    assert voice_logic.beat_charge({"last_beat_at": T0, "charged_seconds": 595}, T0 + 15, 300, 600) == 5


def test_quota_free_is_lifetime_premium_is_utc_day():
    limits = voice_logic.normalise_limits({})
    assert voice_logic.quota_remaining(False, {"lifetime_seconds": 120}, T0, limits) == 180
    today = voice_logic.utc_day_key(T0)
    assert voice_logic.quota_remaining(
        True, {"lifetime_seconds": 5000, "day_key": today, "day_seconds": 200}, T0, limits) == 1000
    # Yesterday's premium seconds do not count today.
    assert voice_logic.quota_remaining(
        True, {"day_key": "2026-11-03", "day_seconds": 1200}, T0, limits) == 1200


def test_apply_charge_rolls_the_day_over():
    usage = voice_logic.apply_charge({"lifetime_seconds": 50, "day_key": "2026-11-03",
                                      "day_seconds": 50}, 10, T0)
    assert usage == {"lifetime_seconds": 60, "day_key": "2026-11-04", "day_seconds": 10}


def test_normalise_limits_falls_back_on_garbage():
    limits = voice_logic.normalise_limits({
        "voice_free_lifetime_seconds": "abc",
        "voice_premium_daily_seconds": -5,
        "voice_max_call_seconds": True,
    })
    assert limits == {"free_lifetime": 300, "premium_daily": 1200, "max_call": 600}
    assert voice_logic.normalise_limits({"voice_max_call_seconds": "120"})["max_call"] == 120


def test_turn_rate_decision():
    ok, times, _ = voice_logic.turn_rate_decision([], T0)
    assert ok and times == [T0]
    ok, _, retry = voice_logic.turn_rate_decision([T0], T0 + 1.0)
    assert not ok and retry == pytest.approx(0.5)
    window = [T0 + 2 * i for i in range(20)]
    ok, _, _ = voice_logic.turn_rate_decision(window, T0 + 40)
    assert not ok, "21st turn inside 60 s must be refused"
    ok, times, _ = voice_logic.turn_rate_decision(window, T0 + 61)
    assert ok and len(times) == 20, "turns older than 60 s drop out of the window"


# ===========================================================================
# voice_session — start
# ===========================================================================

def test_start_creates_session_with_defaults(main):
    _user(main)
    resp, body = _voice(main, "start")
    assert resp.status_code == 200, body
    assert body["remaining_seconds"] == 300
    assert body["call_remaining_seconds"] == 300
    assert body["max_call_seconds"] == 600
    assert body["heartbeat_interval_seconds"] == 15
    assert body["is_premium"] is False
    s = _session(main, body["session_id"])
    assert s["status"] == "open" and s["last_beat_at"] == T0 and s["charged_seconds"] == 0


def test_start_premium_gets_daily_quota(main):
    _user(main, premium=True)
    resp, body = _voice(main, "start")
    assert body["remaining_seconds"] == 1200 and body["is_premium"] is True
    assert body["call_remaining_seconds"] == 600


def test_start_reads_limits_from_remote_config(main):
    _user(main)
    _rc(main, voice_free_lifetime_seconds=120, voice_max_call_seconds=90)
    _, body = _voice(main, "start")
    assert body["remaining_seconds"] == 120 and body["max_call_seconds"] == 90
    assert body["call_remaining_seconds"] == 90


def test_start_with_exhausted_free_quota_is_402(main):
    _user(main)
    main.db.store["users/u1/voice/usage"] = {"lifetime_seconds": 300}
    resp, body = _voice(main, "start")
    assert resp.status_code == 402
    assert body == {"success": False, "error": "voice_quota_exhausted",
                    "is_premium": False, "remaining_seconds": 0}
    assert not [k for k in main.db.store if "/voice_sessions/" in k], "no session on refusal"


def test_start_unknown_user_is_404(main):
    resp, body = _voice(main, "start", uid="ghost")
    assert resp.status_code == 404 and body["error"] == "user_not_found"


def test_start_kill_switch_is_503(main):
    _user(main)
    _rc(main, voice_call_enabled=False)
    resp, body = _voice(main, "start")
    assert resp.status_code == 503 and body["error"] == "voice_disabled"


def test_start_is_idempotent_by_client_session_id(main, clock):
    _user(main)
    sid = "11111111-2222-4333-8444-555555555555"
    assert _start(main, client_session_id=sid) == sid
    clock.advance(5)
    assert _start(main, client_session_id=sid) == sid
    assert _session(main, sid)["started_at"] == T0, "retry must not reset the call"


def test_start_with_closed_client_session_id_is_409(main):
    _user(main)
    sid = "11111111-2222-4333-8444-555555555555"
    _start(main, client_session_id=sid)
    _voice(main, "end", session_id=sid)
    resp, body = _voice(main, "start", client_session_id=sid)
    assert resp.status_code == 409 and body["error"] == "voice_session_closed"


# ===========================================================================
# voice_session — heartbeat charges by the server clock
# ===========================================================================

def test_heartbeat_charges_server_gap_and_ignores_client_time(main, clock):
    _user(main)
    sid = _start(main)
    clock.advance(15)
    resp, body = _voice(main, "heartbeat", session_id=sid, elapsed_seconds=0, client_now=0)
    assert resp.status_code == 200, body
    assert body["ended"] is False
    assert body["charged_seconds"] == 15 and body["remaining_seconds"] == 285
    assert _usage(main)["lifetime_seconds"] == 15


def test_heartbeat_gap_is_capped_at_20s(main, clock):
    _user(main)
    sid = _start(main)
    clock.advance(40)  # late beat, still under the 45 s idle limit
    _, body = _voice(main, "heartbeat", session_id=sid, elapsed_seconds=9999)
    assert body["charged_seconds"] == 20
    assert _usage(main)["lifetime_seconds"] == 20


def test_free_lifetime_quota_ends_the_call(main, clock):
    _user(main)
    main.db.store["users/u1/voice/usage"] = {"lifetime_seconds": 290}
    sid = _start(main)
    clock.advance(15)
    _, body = _voice(main, "heartbeat", session_id=sid)
    assert body["ended"] is True and body["reason"] == "quota_exhausted"
    assert body["remaining_seconds"] == 0
    assert _usage(main)["lifetime_seconds"] == 300, "never charged past the quota"
    assert _session(main, sid)["status"] == "ended"


def test_premium_daily_quota_ends_the_call(main, clock):
    _user(main, premium=True)
    main.db.store["users/u1/voice/usage"] = {
        "lifetime_seconds": 5000, "day_key": "2026-11-04", "day_seconds": 1190}
    sid = _start(main)
    clock.advance(15)
    _, body = _voice(main, "heartbeat", session_id=sid)
    assert body["ended"] is True and body["reason"] == "quota_exhausted"
    assert _usage(main)["day_seconds"] == 1200


def test_max_call_duration_ends_the_call(main, clock):
    _user(main, premium=True)
    _rc(main, voice_max_call_seconds=30)
    sid = _start(main)
    clock.advance(15)
    _, b1 = _voice(main, "heartbeat", session_id=sid)
    assert b1["ended"] is False and b1["call_remaining_seconds"] == 15
    clock.advance(15)
    _, b2 = _voice(main, "heartbeat", session_id=sid)
    assert b2["ended"] is True and b2["reason"] == "max_call_duration"
    assert b2["charged_seconds"] == 30
    assert b2["remaining_seconds"] == 1170, "the wallet keeps what the call did not use"


def test_idle_session_is_closed_lazily_without_charging_the_gap(main, clock):
    _user(main)
    sid = _start(main)
    clock.advance(15)
    _voice(main, "heartbeat", session_id=sid)
    clock.advance(46)
    _, body = _voice(main, "heartbeat", session_id=sid)
    assert body["ended"] is True and body["reason"] == "idle"
    assert _usage(main)["lifetime_seconds"] == 15, "the idle gap must not be charged"
    s = _session(main, sid)
    assert s["status"] == "expired" and s["ended_at"] == T0 + 15


def test_kill_switch_ends_a_running_call_on_next_heartbeat(main, clock):
    _user(main)
    sid = _start(main)
    _rc(main, voice_call_enabled=False)
    clock.advance(15)
    _, body = _voice(main, "heartbeat", session_id=sid)
    assert body["ended"] is True and body["reason"] == "disabled"
    assert _usage(main)["lifetime_seconds"] == 15, "the time actually talked is still charged"


def test_heartbeat_unknown_session_is_404(main):
    _user(main)
    resp, body = _voice(main, "heartbeat", session_id="does-not-exist-123")
    assert resp.status_code == 404 and body["error"] == "voice_session_not_found"


def test_heartbeat_requires_session_id(main):
    _user(main)
    resp, _ = _voice(main, "heartbeat")
    assert resp.status_code == 400


def test_unknown_action_is_400(main):
    _user(main)
    resp, _ = _voice(main, "quota")
    assert resp.status_code == 400


# ===========================================================================
# voice_session — end
# ===========================================================================

def test_end_charges_last_stretch_and_is_idempotent(main, clock):
    _user(main)
    sid = _start(main)
    clock.advance(10)
    _, b1 = _voice(main, "end", session_id=sid)
    assert b1["ended"] is True and b1["reason"] == "client_end" and b1["charged_seconds"] == 10
    clock.advance(10)
    resp, b2 = _voice(main, "end", session_id=sid)
    assert resp.status_code == 200
    assert b2["ended"] is True and b2.get("already_closed") is True
    assert b2["charged_seconds"] == 10
    assert _usage(main)["lifetime_seconds"] == 10, "a repeated end charged again"


def test_end_after_idle_charges_nothing(main, clock):
    _user(main)
    sid = _start(main)
    clock.advance(60)
    _, body = _voice(main, "end", session_id=sid)
    assert body["ended"] is True and body["reason"] == "idle"
    assert _usage(main) == {}


# ===========================================================================
# chat_agent — voice mode
# ===========================================================================

def test_voice_turn_with_valid_session_reserves_no_credit(main, clock):
    _user(main)
    sid = _start(main)
    clock.advance(2)
    resp, gemini = _agent(main, voice={"session_id": sid}, supports_options=True)
    assert resp.status_code == 200, resp.get_json()
    assert main.db.store["users/u1"]["ai_credits"] == START_BALANCE
    assert resp.get_json()["credits_remaining"] == START_BALANCE
    config = _sent_config(gemini)
    assert "VOICE CALL" in config.system_instruction
    assert "present_options" not in _tool_names(config), "voice mode must not offer chips"
    assert _session(main, sid)["turns"] == 1


def test_voice_turn_unknown_session_is_an_error_not_a_charge(main):
    _user(main)
    resp, gemini = _agent(main, voice={"session_id": "no-such-session-1"})
    assert resp.status_code == 409
    assert resp.get_json() == {"success": False, "error": "voice_session_invalid",
                               "reason": "not_found"}
    assert gemini.models.generate_content.call_count == 0
    assert main.db.store["users/u1"]["ai_credits"] == START_BALANCE


def test_voice_turn_on_another_users_session_is_refused(main):
    _user(main, uid="u1")
    _user(main, uid="u2")
    sid = _start(main, uid="u1")
    resp, gemini = _agent(main, uid="u2", voice={"session_id": sid})
    assert resp.status_code == 409 and resp.get_json()["reason"] == "not_found"
    assert main.db.store["users/u2"]["ai_credits"] == START_BALANCE
    assert gemini.models.generate_content.call_count == 0


def test_voice_turn_on_closed_session_is_refused(main, clock):
    _user(main)
    sid = _start(main)
    _voice(main, "end", session_id=sid)
    clock.advance(2)
    resp, _ = _agent(main, voice={"session_id": sid})
    assert resp.status_code == 409 and resp.get_json()["reason"] == "closed"


def test_voice_turn_on_stale_session_is_refused_and_closed(main, clock):
    _user(main)
    sid = _start(main)
    clock.advance(46)
    resp, _ = _agent(main, voice={"session_id": sid})
    assert resp.status_code == 409 and resp.get_json()["reason"] == "expired"
    assert _session(main, sid)["status"] == "expired"


def test_voice_turn_on_exhausted_wallet_is_refused(main, clock):
    _user(main)
    sid = _start(main)
    main.db.store["users/u1/voice/usage"] = {"lifetime_seconds": 300}  # spent by another call
    clock.advance(2)
    resp, _ = _agent(main, voice={"session_id": sid})
    assert resp.status_code == 409 and resp.get_json()["reason"] == "exhausted"
    assert main.db.store["users/u1"]["ai_credits"] == START_BALANCE


def test_voice_turn_with_kill_switch_off_is_refused(main, clock):
    _user(main)
    sid = _start(main)
    _rc(main, voice_call_enabled=False)
    clock.advance(2)
    resp, _ = _agent(main, voice={"session_id": sid})
    assert resp.status_code == 409 and resp.get_json()["reason"] == "disabled"


@pytest.mark.parametrize("voice", [{"session_id": ""}, {"session_id": 42}, "abc", {}])
def test_malformed_voice_field_is_400(main, voice):
    _user(main)
    resp, gemini = _agent(main, voice=voice)
    assert resp.status_code == 400
    assert resp.get_json()["error"] == "voice_session_invalid"
    assert resp.get_json()["reason"] == "malformed"
    assert gemini.models.generate_content.call_count == 0
    assert main.db.store["users/u1"]["ai_credits"] == START_BALANCE


def test_without_voice_the_text_path_is_unchanged(main):
    _user(main)
    resp, gemini = _agent(main, supports_options=True)
    assert resp.status_code == 200
    assert main.db.store["users/u1"]["ai_credits"] == START_BALANCE - CHAT_AGENT_COST
    config = _sent_config(gemini)
    assert "VOICE CALL" not in config.system_instruction
    assert "present_options" in _tool_names(config)


def test_voice_follow_up_round_is_free_and_skips_the_turn_claim(main, clock):
    _user(main)
    sid = _start(main)
    clock.advance(2)
    transcript = [
        {"role": "user", "text": "add milk"},
        {"role": "model", "tool_calls": [{"id": "c1", "name": "add_item",
                                          "args": {"text": "milk"}}]},
        {"role": "tool", "tool_results": [{"id": "c1", "name": "add_item",
                                           "result": {"status": "success"}}]},
    ]
    resp, gemini = _agent(main, transcript=transcript, voice={"session_id": sid})
    assert resp.status_code == 200, resp.get_json()
    assert main.db.store["users/u1"]["ai_credits"] == START_BALANCE
    assert "VOICE CALL" in _sent_config(gemini).system_instruction
    assert _session(main, sid)["turns"] == 0, "a follow-up round is not a new turn"


# ===========================================================================
# chat_agent — voice rate limit
# ===========================================================================

def test_voice_turns_closer_than_1_5s_are_rate_limited(main, clock):
    _user(main)
    sid = _start(main)
    clock.advance(2)
    r1, _ = _agent(main, voice={"session_id": sid})
    assert r1.status_code == 200
    clock.advance(1.0)
    r2, gemini = _agent(main, voice={"session_id": sid})
    assert r2.status_code == 429
    body = r2.get_json()
    assert body["error"] == "voice_rate_limited"
    assert body["retry_after_seconds"] == pytest.approx(0.5)
    assert gemini.models.generate_content.call_count == 0
    assert main.db.store["users/u1"]["ai_credits"] == START_BALANCE


def test_voice_turns_beyond_20_per_minute_are_rate_limited(main, clock):
    _user(main, premium=True)
    sid = _start(main)
    for i in range(20):
        clock.advance(2)
        if i % 7 == 6:  # keep the session fresh, like the client's 15 s heartbeat
            _voice(main, "heartbeat", session_id=sid)
        resp, _ = _agent(main, voice={"session_id": sid})
        assert resp.status_code == 200, (i, resp.get_json())
    clock.advance(2)
    resp, _ = _agent(main, voice={"session_id": sid})
    assert resp.status_code == 429 and resp.get_json()["error"] == "voice_rate_limited"
    assert main.db.store["users/u1"]["ai_credits"] == START_BALANCE
