"""Analyze reliability: PDF input, input guards, Gemini overload fallback, empty results. OFFLINE.

WHY THIS EXISTS — four server defects behind "Analyze must work perfectly" (2026-11-20):
  1. PDF was never analyzed: `pdf_base64` was an unknown input_type, and an unknown type fell
     through to the branch that pastes input_data into the prompt as text (megabytes of base64).
     Oversized binaries were not refused before credits were reserved.
  2. A Gemini `503 UNAVAILABLE ... high demand` became our 500 within ~1-2 s — no retry, no
     second model — and the user's manual retry 40 s later succeeded.
  3. A silent voice note came back with zero items: the client built an empty checklist and the
     user paid for it.
  4. Every new outcome keeps the credit invariants: input refused before reserve, one refund per
     outcome, refund carries request_id, no usage tick for a non-result.

Run from firebase-functions/:
    python -m pytest tests/test_analyze_reliability.py -q

Same harness as test_generate_checklist_parse_retry.py: Firestore and Gemini are stubbed, except
the SDK-wiring test, which drives the REAL google-genai client against an in-process httpx
transport (no network) to prove the retry/timeout options reach the wire.
"""

import base64
import json
import os
import re
import sys
import time
from unittest.mock import MagicMock

import pytest
from flask import Flask
from google.genai import errors as genai_errors

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

app = Flask(__name__)

USER_ID = "user-12345678"
REQUEST_ID = "req-1"
COST = 20
BALANCE_AFTER_RESERVE = 80
DEFAULT_MODEL = "gemini-3.5-flash-lite"
ROLLBACK_MODEL = "gemini-3.1-flash-lite"

PDF_BYTES = b"%PDF-1.4 fake pdf body"
PDF_B64 = base64.b64encode(PDF_BYTES).decode("ascii")
IMAGE_B64 = base64.b64encode(b"\xff\xd8\xff fake jpeg").decode("ascii")
AUDIO_B64 = base64.b64encode(b"OggS fake opus").decode("ascii")

GENERATE_JSON = json.dumps({
    "checklist_name": "Trip",
    "items": [{"text": "Book flights", "checked": False}],
    "summary": "One thing to do",
    "confidence": 0.9,
})
FILL_JSON = json.dumps({
    "filled_items": [{"index": 1, "text": "Passport", "checked": True}],
    "summary": "Passport is packed",
    "confidence": 0.9,
})
NOTHING_FOUND_MESSAGE = "Couldn't find anything to turn into a checklist. Try a clearer photo or more text."


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


def _reply(text):
    response = MagicMock()
    response.text = text
    return response


def _overload(code):
    status = "UNAVAILABLE" if code == 503 else "RESOURCE_EXHAUSTED"
    cls = genai_errors.ServerError if code >= 500 else genai_errors.ClientError
    return cls(code, {"error": {
        "code": code,
        "message": "This model is currently experiencing high demand. Please try again later.",
        "status": status,
    }})


PREPAY_DEPLETED = genai_errors.ClientError(402, {"error": {
    "code": 402,
    "message": "Your prepayment credits are depleted.",
    "status": "RESOURCE_EXHAUSTED",
}})
BAD_INPUT = genai_errors.ClientError(400, {"error": {
    "code": 400,
    "message": "Request contains an invalid argument.",
    "status": "INVALID_ARGUMENT",
}})


class Harness:
    """Every collaborator of the two handlers except the branch under test."""

    def __init__(self, monkeypatch, main_module, gemini, model=DEFAULT_MODEL):
        self.main = main_module
        self.call_gemini = MagicMock(side_effect=gemini)
        self.reserve = MagicMock(return_value=("reserve", BALANCE_AFTER_RESERVE))
        self.refund = MagicMock(return_value=True)
        self.increment_usage = MagicMock(return_value={})
        monkeypatch.setattr(main_module, "call_gemini", self.call_gemini)
        monkeypatch.setattr(main_module, "reserve_credits_with_action", self.reserve)
        monkeypatch.setattr(main_module, "refund_credits", self.refund)
        monkeypatch.setattr(main_module, "increment_usage", self.increment_usage)
        monkeypatch.setattr(main_module, "get_user_premium_status", lambda user_id: False)
        monkeypatch.setattr(main_module, "get_remote_config_value", lambda key, default: default)
        monkeypatch.setattr(main_module, "check_usage_limit", lambda user_id, is_premium=False: (True, ""))
        monkeypatch.setattr(main_module, "get_credits_config", lambda: {"action_cost": COST})
        monkeypatch.setattr(
            main_module, "resolve_experiment_model",
            lambda user_id, flow, default_model, data: (model, "control"),
        )

    def post(self, handler_name, **fields):
        if handler_name == "generate_checklist":
            payload = {"user_id": USER_ID, "prompt": "Make a checklist", "request_id": REQUEST_ID}
        else:
            payload = {
                "user_id": USER_ID,
                "request_id": REQUEST_ID,
                "checklist": {"id": 1, "name": "Trip", "items": [{"text": "Passport", "checked": False}]},
            }
        payload.update(fields)
        with app.test_request_context(method="POST", json=payload) as ctx:
            response = getattr(self.main, handler_name)(ctx.request)
            return response.status_code, json.loads(response.get_data(as_text=True))


def _ok_reply(handler_name):
    return _reply(GENERATE_JSON if handler_name == "generate_checklist" else FILL_JSON)


HANDLERS = ["generate_checklist", "analyze_and_fill_checklist"]


# ---------------------------------------------------------------------------------------------
# Defect 1 — PDF is analyzed as a document, unknown / oversized inputs are refused before reserve
# ---------------------------------------------------------------------------------------------

def test_call_gemini_sends_pdf_as_an_application_pdf_part(monkeypatch, main_module):
    client = MagicMock()
    monkeypatch.setattr(main_module, "gemini_client", client)

    main_module.call_gemini("Summarize", "pdf_base64", PDF_B64, model_id=DEFAULT_MODEL)

    contents = client.models.generate_content.call_args.kwargs["contents"]
    assert isinstance(contents, list), "PDF must be sent as a binary Part, not pasted into the prompt"
    assert contents[0] == "Summarize"
    part = contents[1]
    assert part.inline_data.mime_type == "application/pdf"
    assert part.inline_data.data == PDF_BYTES


@pytest.mark.parametrize("handler_name", HANDLERS)
def test_pdf_reaches_gemini_as_bytes_not_as_prompt_text(monkeypatch, main_module, handler_name):
    h = Harness(monkeypatch, main_module, [_ok_reply(handler_name)])

    status, body = h.post(handler_name, input_type="pdf_base64", input_data=PDF_B64)

    assert status == 200, body
    prompt, input_type, input_data = h.call_gemini.call_args.args
    assert input_type == "pdf_base64"
    assert input_data == PDF_B64
    assert PDF_B64 not in prompt, "base64 must never be pasted into the prompt"


@pytest.mark.parametrize("handler_name", HANDLERS)
def test_unknown_input_type_is_refused_before_reserve(monkeypatch, main_module, handler_name):
    h = Harness(monkeypatch, main_module, [_ok_reply(handler_name)])

    status, body = h.post(handler_name, input_type="video_base64", input_data=PDF_B64)

    assert status == 400, body
    assert body["error_code"] == "unsupported_input_type"
    h.reserve.assert_not_called()
    h.call_gemini.assert_not_called()


@pytest.mark.parametrize("handler_name", HANDLERS)
@pytest.mark.parametrize("input_type, input_data", [
    ("image_base64", IMAGE_B64),
    ("pdf_base64", PDF_B64),
    ("audio_base64", AUDIO_B64),
])
def test_oversized_binary_is_refused_before_reserve(monkeypatch, main_module, handler_name, input_type, input_data):
    # Shrink the cap instead of allocating 20 MB; the handler must read the module constant.
    monkeypatch.setattr(main_module, "MAX_BINARY_INPUT_BYTES", 8, raising=False)
    h = Harness(monkeypatch, main_module, [_ok_reply(handler_name)])

    status, body = h.post(handler_name, input_type=input_type, input_data=input_data)

    assert status == 413, body
    assert body["error_code"] == "input_too_large"
    h.reserve.assert_not_called()
    h.call_gemini.assert_not_called()


@pytest.mark.parametrize("handler_name", HANDLERS)
def test_undecodable_base64_is_refused_before_reserve(monkeypatch, main_module, handler_name):
    h = Harness(monkeypatch, main_module, [_ok_reply(handler_name)])

    status, body = h.post(handler_name, input_type="pdf_base64", input_data="abc")  # bad padding

    assert status == 400, body
    assert body["error_code"] == "invalid_input"
    h.reserve.assert_not_called()


def test_binary_cap_fits_the_cloud_functions_request_limit(main_module):
    cap = main_module.MAX_BINARY_INPUT_BYTES
    assert cap <= 20 * 1024 * 1024
    # base64 inflates by 4/3; the encoded body must stay under Cloud Functions gen2's 32 MB cap
    # with room left for the JSON envelope and the prompt.
    encoded = 4 * -(-cap // 3)
    assert encoded < 31 * 1024 * 1024


# ---------------------------------------------------------------------------------------------
# Defect 2 — overload survives the SDK retries -> ONE call on the rollback model
# ---------------------------------------------------------------------------------------------

@pytest.mark.parametrize("handler_name", HANDLERS)
@pytest.mark.parametrize("code", [503, 429])
def test_overload_falls_back_once_to_the_rollback_model(monkeypatch, main_module, handler_name, code):
    h = Harness(monkeypatch, main_module, [_overload(code), _ok_reply(handler_name)])

    status, body = h.post(handler_name, input_type="text", input_data="passport, tickets")

    assert status == 200, body
    assert h.call_gemini.call_count == 2
    assert h.call_gemini.call_args_list[0].kwargs["model_id"] == DEFAULT_MODEL
    fallback = h.call_gemini.call_args_list[1].kwargs
    assert fallback["model_id"] == ROLLBACK_MODEL
    # ONE call: no SDK retries on the fallback, and its timeout fits the time that is left.
    assert fallback["http_options"].retry_options.attempts == 1
    assert 0 < fallback["http_options"].timeout <= main_module.ANALYZE_ATTEMPT_TIMEOUT_MS
    # The response names the model that actually answered.
    assert body["model_id"] == ROLLBACK_MODEL
    assert body["ai_credits"] == BALANCE_AFTER_RESERVE
    h.refund.assert_not_called()
    assert h.increment_usage.call_count == 1


@pytest.mark.parametrize("handler_name", HANDLERS)
def test_prepay_402_does_not_fall_back_and_refunds_once(monkeypatch, main_module, handler_name):
    h = Harness(monkeypatch, main_module, [PREPAY_DEPLETED, _ok_reply(handler_name)])

    status, body = h.post(handler_name, input_type="text", input_data="passport")

    assert status == 500
    assert body == {"success": False, "error": "AI processing failed. Please try again."}
    assert h.call_gemini.call_count == 1
    h.refund.assert_called_once_with(USER_ID, COST, "gemini_error", REQUEST_ID)
    h.increment_usage.assert_not_called()


@pytest.mark.parametrize("handler_name", HANDLERS)
def test_input_error_400_does_not_fall_back(monkeypatch, main_module, handler_name):
    h = Harness(monkeypatch, main_module, [BAD_INPUT, _ok_reply(handler_name)])

    status, _ = h.post(handler_name, input_type="text", input_data="passport")

    assert status == 500
    assert h.call_gemini.call_count == 1
    h.refund.assert_called_once_with(USER_ID, COST, "gemini_error", REQUEST_ID)


@pytest.mark.parametrize("handler_name", HANDLERS)
def test_no_fallback_when_the_model_already_is_the_rollback_model(monkeypatch, main_module, handler_name):
    h = Harness(monkeypatch, main_module, [_overload(503), _ok_reply(handler_name)], model=ROLLBACK_MODEL)

    status, _ = h.post(handler_name, input_type="text", input_data="passport")

    assert status == 500
    assert h.call_gemini.call_count == 1
    h.refund.assert_called_once_with(USER_ID, COST, "gemini_error", REQUEST_ID)


@pytest.mark.parametrize("handler_name", HANDLERS)
def test_failed_fallback_refunds_exactly_once(monkeypatch, main_module, handler_name):
    h = Harness(monkeypatch, main_module, [_overload(503), _overload(503), _ok_reply(handler_name)])

    status, body = h.post(handler_name, input_type="text", input_data="passport")

    assert status == 500
    assert body == {"success": False, "error": "AI processing failed. Please try again."}
    assert h.call_gemini.call_count == 2
    h.refund.assert_called_once_with(USER_ID, COST, "gemini_error", REQUEST_ID)
    h.increment_usage.assert_not_called()


@pytest.mark.parametrize("handler_name", HANDLERS)
def test_no_fallback_once_the_time_budget_is_spent(monkeypatch, main_module, handler_name):
    # A fallback started this late would run past the function timeout, where the platform kills
    # the instance and the reserved credits are never refunded.
    monkeypatch.setattr(main_module, "ANALYZE_DEADLINE_SECONDS", 0)
    h = Harness(monkeypatch, main_module, [_overload(503), _ok_reply(handler_name)])

    status, _ = h.post(handler_name, input_type="text", input_data="passport")

    assert status == 500
    assert h.call_gemini.call_count == 1
    h.refund.assert_called_once_with(USER_ID, COST, "gemini_error", REQUEST_ID)


def test_call_gemini_retries_overload_through_the_real_sdk(monkeypatch, main_module):
    """The retry/timeout options must reach the wire on the installed google-genai version."""
    import httpx
    from google import genai
    from google.genai import types

    overloaded = {"error": {"code": 503, "message": "high demand", "status": "UNAVAILABLE"}}
    ok = {"candidates": [{"content": {"role": "model", "parts": [{"text": "ok"}]}, "finishReason": "STOP"}]}
    replies = [httpx.Response(503, json=overloaded), httpx.Response(503, json=overloaded), httpx.Response(200, json=ok)]
    seen = []

    def transport(request):
        seen.append(request)
        return replies[len(seen) - 1]

    client = genai.Client(
        api_key="fake-key",
        http_options=types.HttpOptions(httpx_client=httpx.Client(transport=httpx.MockTransport(transport))),
    )
    monkeypatch.setattr(main_module, "gemini_client", client)
    monkeypatch.setattr(time, "sleep", lambda seconds: None)  # tenacity backoff

    # A photo: documents/audio use one long attempt (test_documents_get_one_long_attempt_not_three_short_ones).
    response = main_module.call_gemini("Summarize", "image_base64", PDF_B64, model_id=DEFAULT_MODEL)

    assert response.text == "ok"
    assert len(seen) == 3, "two 503s must be retried inside the SDK"
    assert b"image/jpeg" in seen[0].content
    expected_timeout_s = str(-(-main_module.ANALYZE_ATTEMPT_TIMEOUT_MS // 1000))
    assert seen[0].headers["X-Server-Timeout"] == expected_timeout_s


def _deploy_timeout_seconds(function_name):
    deploy_sh = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "deploy.sh")
    with open(deploy_sh, encoding="utf-8") as f:
        text = f.read()
    block = re.search(r"gcloud functions deploy %s \\\n(.*?)(?:\n\s*\n|\Z)" % function_name, text, re.S)
    assert block, "deploy block for %s not found in deploy.sh" % function_name
    return int(re.search(r"--timeout=(\d+)s", block.group(1)).group(1))


def test_retry_budget_fits_inside_the_function_timeout(main_module):
    options = main_module.ANALYZE_GEMINI_HTTP_OPTIONS
    retry = options.retry_options
    waits = sum(
        min(retry.initial_delay * retry.exp_base ** i + retry.jitter, retry.max_delay)
        for i in range(retry.attempts - 1)
    )
    worst_case_s = retry.attempts * options.timeout / 1000 + waits
    assert worst_case_s <= main_module.ANALYZE_DEADLINE_SECONDS
    for name in HANDLERS:
        assert main_module.ANALYZE_DEADLINE_SECONDS < _deploy_timeout_seconds(name)


@pytest.mark.parametrize("input_type", ["pdf_base64", "audio_base64"])
def test_documents_get_one_long_attempt_not_three_short_ones(monkeypatch, main_module, input_type):
    # Prod smoke 2026-11-20: a one-page PDF timed out three 15 s attempts in a row.
    client = MagicMock()
    monkeypatch.setattr(main_module, "gemini_client", client)

    main_module.call_gemini("p", input_type, PDF_B64, model_id=DEFAULT_MODEL)

    options = client.models.generate_content.call_args.kwargs["config"].http_options
    assert options.timeout > main_module.ANALYZE_ATTEMPT_TIMEOUT_MS
    assert options.retry_options.attempts * options.timeout / 1000 <= main_module.ANALYZE_DEADLINE_SECONDS


def test_text_keeps_the_short_retrying_attempts(monkeypatch, main_module):
    client = MagicMock()
    monkeypatch.setattr(main_module, "gemini_client", client)

    main_module.call_gemini("p", "text", "", model_id=DEFAULT_MODEL)

    options = client.models.generate_content.call_args.kwargs["config"].http_options
    assert options is main_module.ANALYZE_GEMINI_HTTP_OPTIONS


# ---------------------------------------------------------------------------------------------
# Defect 3 — an empty result is refunded and reported, never billed
# ---------------------------------------------------------------------------------------------

@pytest.mark.parametrize("handler_name, empty_json", [
    ("generate_checklist", json.dumps({"checklist_name": "Voice note", "items": [], "summary": ""})),
    ("generate_checklist", json.dumps({
        "checklist_name": "Voice note",
        "items": [{"text": "Folder", "type": "folder", "children": []}],
    })),
    ("generate_checklist", json.dumps([])),
    ("analyze_and_fill_checklist", json.dumps({"filled_items": [], "summary": ""})),
])
def test_empty_result_is_refunded_and_reported_as_nothing_found(monkeypatch, main_module, handler_name, empty_json):
    h = Harness(monkeypatch, main_module, [_reply(empty_json)])

    status, body = h.post(handler_name, input_type="audio_base64", input_data=AUDIO_B64)

    assert status == 422, body
    assert body == {"success": False, "error": NOTHING_FOUND_MESSAGE, "error_code": "nothing_found"}
    h.refund.assert_called_once_with(USER_ID, COST, "nothing_found", REQUEST_ID)
    h.increment_usage.assert_not_called()
