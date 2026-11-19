"""generate_checklist passes the client's audio MIME type to Gemini. OFFLINE.

WHY THIS EXISTS: the Telegram bot sends voice notes (OGG/Opus) to generate_checklist with a
`mime_type` field, but the handler called call_gemini without `audio_mime_type`, so Gemini was
always told the bytes were audio/mp4. What is locked here:
  * normalize_audio_mime maps the containers the bot sends to a Gemini-whitelisted value;
  * audio input forwards the normalized `mime_type`; no field (the app) keeps audio/mp4;
  * non-audio input calls call_gemini exactly as before — no audio_mime_type at all.

Run from firebase-functions/:
    python -m pytest tests/test_generate_checklist_audio_mime.py -q

Same harness as test_generate_checklist_parse_retry.py: Firestore and Gemini are stubbed.
"""

import base64
import json
import os
import sys
from unittest.mock import MagicMock

import pytest
from flask import Flask

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

app = Flask(__name__)

VALID_CHECKLIST_JSON = json.dumps({
    "checklist_name": "Groceries",
    "items": [{"text": "Milk", "checked": False}],
    "summary": "From a voice note",
    "confidence": 0.9,
})
AUDIO_B64 = base64.b64encode(b"OggS fake opus bytes").decode("ascii")


@pytest.fixture
def main_module(monkeypatch):
    monkeypatch.setattr("firebase_admin._apps", {"[DEFAULT]": True})
    monkeypatch.setattr("firebase_admin.initialize_app", MagicMock())
    monkeypatch.setattr("firebase_admin.firestore.client", lambda: MagicMock())
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key")
    monkeypatch.setenv("REVENUECAT_API_KEY", "sk_fake_key")
    import main
    return main


@pytest.fixture
def call_gemini(monkeypatch, main_module):
    response = MagicMock()
    response.text = VALID_CHECKLIST_JSON
    mock = MagicMock(return_value=response)
    monkeypatch.setattr(main_module, "call_gemini", mock)
    monkeypatch.setattr(main_module, "refund_credits", MagicMock(return_value=True))
    monkeypatch.setattr(main_module, "increment_usage", MagicMock(return_value={}))
    monkeypatch.setattr(main_module, "get_user_premium_status", lambda user_id: False)
    monkeypatch.setattr(main_module, "get_remote_config_value", lambda key, default: default)
    monkeypatch.setattr(main_module, "check_usage_limit", lambda user_id, is_premium=False: (True, ""))
    monkeypatch.setattr(main_module, "get_credits_config", lambda: {"action_cost": 20})
    monkeypatch.setattr(
        main_module, "reserve_credits_with_action",
        lambda user_id, cost, request_id=None: ("reserve", 80),
    )
    monkeypatch.setattr(
        main_module, "resolve_experiment_model",
        lambda user_id, flow, default_model, data: ("gemini-3.5-flash-lite", "control"),
    )
    return mock


def _generate(main_module, **fields):
    payload = {"user_id": "user-12345678", "prompt": "Make a checklist", "request_id": "req-1",
               **fields}
    with app.test_request_context(method="POST", json=payload) as ctx:
        response = main_module.generate_checklist(ctx.request)
    assert response.status_code == 200, response.get_data(as_text=True)


@pytest.mark.parametrize("client_mime, expected", [
    ("audio/ogg", "audio/ogg"),
    ("audio/ogg; codecs=opus", "audio/ogg"),
    ("AUDIO/OGG;codecs=opus", "audio/ogg"),
    ("audio/mpeg", "audio/mpeg"),
    ("audio/mp3", "audio/mpeg"),
    ("audio/m4a", "audio/mp4"),
    ("audio/webm;codecs=opus", "audio/webm"),
    ("", "audio/mp4"),
    ("video/quicktime", "audio/mp4"),
])
def test_normalize_audio_mime_returns_a_gemini_whitelisted_value(main_module, client_mime, expected):
    normalized = main_module.normalize_audio_mime(client_mime)
    assert normalized == expected
    assert normalized in main_module._GEMINI_AUDIO_MIME_WHITELIST


def test_voice_note_mime_reaches_gemini(main_module, call_gemini):
    _generate(main_module, input_type="audio_base64", input_data=AUDIO_B64,
              mime_type="audio/ogg; codecs=opus")
    assert call_gemini.call_args.kwargs["audio_mime_type"] == "audio/ogg"
    assert call_gemini.call_args.kwargs["model_id"] == "gemini-3.5-flash-lite"


def test_audio_without_mime_type_keeps_the_mp4_default(main_module, call_gemini):
    # The app never sends mime_type — it must get exactly what it got before.
    _generate(main_module, input_type="audio_base64", input_data=AUDIO_B64)
    assert call_gemini.call_args.kwargs["audio_mime_type"] == "audio/mp4"


def test_non_string_mime_type_falls_back_instead_of_crashing(main_module, call_gemini):
    _generate(main_module, input_type="audio_base64", input_data=AUDIO_B64, mime_type=42)
    assert call_gemini.call_args.kwargs["audio_mime_type"] == "audio/mp4"


def test_non_audio_input_ignores_mime_type(main_module, call_gemini):
    _generate(main_module, input_type="text", input_data="milk, bread", mime_type="audio/ogg")
    assert "audio_mime_type" not in call_gemini.call_args.kwargs
    assert call_gemini.call_args.kwargs == {"model_id": "gemini-3.5-flash-lite"}
