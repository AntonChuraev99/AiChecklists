"""Unit tests for the 402 credit-refusal contract (main.credit_error_response).

WHY THIS EXISTS: until 2026-08-18 a refused credit reservation returned the same body
whether the caller was out of credits or had no user document at all. Two things broke as a
result — a never-registered caller was shown the paywall instead of a sign-in prompt, and
the 402 counter could not be read as a monetization signal because it summed both cases.

What is locked here is BOTH halves of the fix:
  * the new `reason` field actually distinguishes the two verdicts, and
  * the wire contract old clients depend on did NOT change. Most of the installed base is
    still on 1.17.x/1.18.x and branches on status 402 plus the exact string "insufficient
    credits"; a test that only checked `reason` would let a future edit silently break them.

Run from firebase-functions/:
    python -m pytest tests/test_credit_error_reason.py -q
"""

import json
import os
import sys
from unittest.mock import MagicMock

import pytest
from flask import Flask

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

app = Flask(__name__)


@pytest.fixture
def main_module(monkeypatch):
    """Import main.py with Firebase and Gemini stubbed out (mirrors test_main.py)."""
    monkeypatch.setattr("firebase_admin._apps", {"[DEFAULT]": True})
    monkeypatch.setattr("firebase_admin.initialize_app", MagicMock())
    monkeypatch.setattr("firebase_admin.firestore.client", lambda: MagicMock())
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key")
    monkeypatch.setenv("REVENUECAT_API_KEY", "sk_fake_key")
    import main
    return main


def _body(response):
    return json.loads(response.get_data(as_text=True))


# --------------------------------------------------------------------------- reason


def test_no_user_is_reported_as_no_user(main_module):
    with app.test_request_context():
        body = _body(main_module.credit_error_response("no_user"))
    assert body["reason"] == "no_user"


def test_insufficient_is_reported_as_insufficient_credits(main_module):
    with app.test_request_context():
        body = _body(main_module.credit_error_response("insufficient"))
    assert body["reason"] == "insufficient_credits"


def test_unknown_verdict_falls_back_to_insufficient_credits(main_module):
    """An unrecognised verdict must not read as `no_user`.

    `no_user` is the branch that suppresses the paywall, so an unknown value defaulting
    there would silently hide the paywall from paying-capable users. Defaulting the other
    way is the safe direction.
    """
    with app.test_request_context():
        body = _body(main_module.credit_error_response("something_new"))
    assert body["reason"] == "insufficient_credits"


def test_the_two_verdicts_do_not_collapse(main_module):
    """The whole point of the change: the two failures must be distinguishable."""
    with app.test_request_context():
        no_user = _body(main_module.credit_error_response("no_user"))
        broke = _body(main_module.credit_error_response("insufficient"))
    assert no_user["reason"] != broke["reason"]


# --------------------------------------------------------------------------- wire compat


def test_status_code_is_still_402_for_both(main_module):
    with app.test_request_context():
        assert main_module.credit_error_response("no_user").status_code == 402
        assert main_module.credit_error_response("insufficient").status_code == 402


def test_legacy_error_string_is_unchanged(main_module):
    """Old clients (1.17.x/1.18.x) match this exact text to raise the paywall."""
    with app.test_request_context():
        for verdict in ("no_user", "insufficient"):
            body = _body(main_module.credit_error_response(verdict))
            assert body["error"] == "insufficient credits"
            assert body["success"] is False


# --------------------------------------------------------------------------- reservation


def _snapshot(exists, credits=0):
    snap = MagicMock()
    snap.exists = exists
    snap.get.return_value = credits
    return snap


def _run_reserve(main_module, snapshot):
    """Drive reserve_chat_credit's transactional body with a stubbed Firestore snapshot."""
    captured = {}

    def fake_transactional(fn):
        captured["fn"] = fn
        return fn

    user_ref = MagicMock()
    user_ref.get.return_value = snapshot
    main_module.db.collection.return_value.document.return_value = user_ref
    main_module.firestore.transactional = fake_transactional
    main_module.db.transaction.return_value = MagicMock()

    main_module.reserve_chat_credit("uid-1")
    return captured["fn"](MagicMock())


def test_missing_user_document_yields_no_user(main_module):
    assert _run_reserve(main_module, _snapshot(exists=False)) == ("no_user", None)


def test_existing_user_without_credits_yields_insufficient(main_module):
    assert _run_reserve(main_module, _snapshot(exists=True, credits=0)) == ("insufficient", None)


def test_funded_user_yields_reserve_and_new_balance(main_module):
    action, balance = _run_reserve(main_module, _snapshot(exists=True, credits=10))
    assert action == "reserve"
    assert balance == 10 - main_module.CHAT_INTENT_COST
