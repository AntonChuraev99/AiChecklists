"""Unit tests for generate_checklist's retry on an unparseable Gemini response.

WHY THIS EXISTS: production logged three HTTP 500s inside eleven seconds for one user, all with
`JSONDecodeError: Expecting value: line 1 column 1 (char 0)` — Gemini returned an empty body and
generate_checklist refunded and failed on the first one, with no second attempt. An empty or
non-JSON completion is a transient generation blip, so one retry of the same call recovers it.

What is locked here:
  * an empty first response followed by a valid one yields 200 and bills normally (no refund);
  * two unparseable responses in a row still fail with the legacy 500 text, refund EXACTLY once
    and stop after ONE retry (no unbounded loop on the model).

Run from firebase-functions/:
    python -m pytest tests/test_generate_checklist_parse_retry.py -q

No network, no Firebase credentials: Firestore and Gemini are stubbed (mirrors
test_credit_error_reason.py), and the prompt comes from the tracked stub when the gitignored
prompts_private.py is absent.
"""

import json
import os
import sys
from unittest.mock import MagicMock

import pytest
from flask import Flask

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

app = Flask(__name__)

COST = 20
BALANCE_AFTER_RESERVE = 80

VALID_CHECKLIST_JSON = json.dumps({
    "checklist_name": "Trip to Rome",
    "items": [
        {"text": "Book flights", "checked": False},
        {"text": "Pack passport", "checked": False},
    ],
    "summary": "Two things to do before the trip",
    "confidence": 0.9,
})


@pytest.fixture
def main_module(monkeypatch):
    """Import main.py with Firebase and Gemini stubbed out (mirrors test_credit_error_reason.py)."""
    monkeypatch.setattr("firebase_admin._apps", {"[DEFAULT]": True})
    monkeypatch.setattr("firebase_admin.initialize_app", MagicMock())
    monkeypatch.setattr("firebase_admin.firestore.client", lambda: MagicMock())
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key")
    monkeypatch.setenv("REVENUECAT_API_KEY", "sk_fake_key")
    import main
    return main


def _gemini_response(text):
    response = MagicMock()
    response.text = text
    return response


def _stub_collaborators(monkeypatch, main_module, gemini_texts):
    """Stub everything generate_checklist touches except the parse/retry branch under test.

    Returns (call_gemini mock, refund_credits mock, increment_usage mock).
    """
    call_gemini = MagicMock(side_effect=[_gemini_response(t) for t in gemini_texts])
    refund_credits = MagicMock(return_value=True)
    increment_usage = MagicMock(return_value={})

    monkeypatch.setattr(main_module, "call_gemini", call_gemini)
    monkeypatch.setattr(main_module, "refund_credits", refund_credits)
    monkeypatch.setattr(main_module, "increment_usage", increment_usage)
    monkeypatch.setattr(main_module, "get_user_premium_status", lambda user_id: False)
    monkeypatch.setattr(main_module, "get_remote_config_value", lambda key, default: default)
    monkeypatch.setattr(main_module, "check_usage_limit", lambda user_id, is_premium=False: (True, ""))
    monkeypatch.setattr(main_module, "get_credits_config", lambda: {"action_cost": COST})
    monkeypatch.setattr(
        main_module, "reserve_credits_with_action",
        lambda user_id, cost, request_id=None: ("reserve", BALANCE_AFTER_RESERVE),
    )
    monkeypatch.setattr(
        main_module, "resolve_experiment_model",
        lambda user_id, flow, default_model, data: ("gemini-3.1-flash-lite", "control"),
    )
    return call_gemini, refund_credits, increment_usage


def _post_generate(main_module):
    payload = {"user_id": "user-12345678", "prompt": "Trip to Rome", "request_id": "req-1"}
    with app.test_request_context(method="POST", json=payload) as ctx:
        response = main_module.generate_checklist(ctx.request)
        return response.status_code, json.loads(response.get_data(as_text=True))


def test_empty_first_response_is_retried_once_and_succeeds(monkeypatch, main_module):
    call_gemini, refund_credits, increment_usage = _stub_collaborators(
        monkeypatch, main_module, ["", VALID_CHECKLIST_JSON]
    )

    status, body = _post_generate(main_module)

    assert status == 200, "empty Gemini response must be retried, got %s: %s" % (status, body)
    assert body["success"] is True
    assert body["checklist_name"] == "Trip to Rome"
    assert body["items"] == [
        {"text": "Book flights", "checked": False},
        {"text": "Pack passport", "checked": False},
    ]
    assert body["ai_credits"] == BALANCE_AFTER_RESERVE
    assert call_gemini.call_count == 2
    # The retry must be the SAME request: same prompt, same input, same model.
    assert call_gemini.call_args_list[0] == call_gemini.call_args_list[1]
    refund_credits.assert_not_called()
    # One logical generation = one usage tick, however many Gemini attempts it took.
    assert increment_usage.call_count == 1


def test_two_unparseable_responses_fail_and_refund_exactly_once(monkeypatch, main_module):
    call_gemini, refund_credits, increment_usage = _stub_collaborators(
        monkeypatch, main_module, ["", "", VALID_CHECKLIST_JSON]
    )

    status, body = _post_generate(main_module)

    assert status == 500
    assert body == {"success": False, "error": "Failed to parse AI response"}
    # ONE retry only: the third (valid) response must never be requested.
    assert call_gemini.call_count == 2
    refund_credits.assert_called_once_with("user-12345678", COST, "gemini_parse_error", "req-1")
    increment_usage.assert_not_called()
