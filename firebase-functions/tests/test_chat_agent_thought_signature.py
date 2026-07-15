"""chat_agent — thought_signature round-trip through the stateless transcript.

Bug (prod, found by /healthcheck 2026-07-15): the `variant_b` arm of the ai_model_arm
A/B test routes 50% of users to gemini-3.1-flash-lite. Gemini 3.x emits a
`thought_signature` on every function_call part and REQUIRES it back when that call is
replayed; 2.5 (control) never emits one. `chat_agent` is stateless — the client stores
the transcript and resends it — but the signature was dropped on serialization and never
rebuilt, so round 2 of every tool-using dialog died:

    400 INVALID_ARGUMENT — Function call is missing a thought_signature in functionCall
    parts... function call `default_api:read_checklist`, position 2.

Round 1 charged the flat per-turn credit; the 400 landed on round 2 where
`reserved_this_round` is False -> no refund. The user paid for an error.

Root cause: `thinking_config=ThinkingConfig(thinking_budget=0)` was believed to suppress
signatures ("Verified on the stable model" — main.py). True on 2.5, false on 3.x. The
pre-enable check for the arm only ever exercised round 1, so it passed.

These are pure unit tests over the two production functions that form the contract:
    _serialize_function_calls   (model parts  -> client transcript)
    _reconstruct_agent_contents (client transcript -> Gemini contents)
No network, no credits. The live end-to-end check against Gemini lives in
`repro_thought_signature.py` (scratchpad, needs GEMINI_API_KEY) — it confirmed the 400
before the fix and PASS after, on both arms and for a stale client.

Run:  python -m pytest firebase-functions/tests/test_chat_agent_thought_signature.py -x
"""

import base64
import sys
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

# main.py initializes firebase_admin + a Firestore client at import time; neither is
# involved in the signature round-trip, and ADC is not available in unit-test envs.
with mock.patch("firebase_admin.initialize_app"), \
     mock.patch("firebase_admin.firestore.client"):
    import main

from google.genai import types  # noqa: E402

REAL_SIGNATURE = b"\x0a\x2fake-thought-signature-blob\xff\x00"


def _model_part(name="read_checklist", args=None, signature=None):
    """A model-emitted function_call part, as the SDK hands it back."""
    return types.Part(
        function_call=types.FunctionCall(id="c1", name=name, args=args or {"name": "Trip"}),
        thought_signature=signature,
    )


def _function_call_parts(contents):
    return [p for c in contents for p in c.parts if getattr(p, "function_call", None)]


# ─── serialization: the signature must survive the trip out to the client ───

def test_serialize_emits_base64_signature_when_model_returns_one():
    calls = main._serialize_function_calls([_model_part(signature=REAL_SIGNATURE)])

    assert len(calls) == 1
    assert "thought_signature" in calls[0], (
        "REGRESSION: signature dropped on serialization — this is the exact prod bug. "
        "It lives on the PART, not on the FunctionCall."
    )
    assert base64.b64decode(calls[0]["thought_signature"]) == REAL_SIGNATURE


def test_serialize_omits_key_entirely_when_model_returns_no_signature():
    """The 2.5 control arm emits none — the key must be absent, not null/empty."""
    calls = main._serialize_function_calls([_model_part(signature=None)])

    assert calls and "thought_signature" not in calls[0]


def test_serialize_still_skips_present_options():
    """Signature support must not weaken the server-terminal present_options guard."""
    parts = [_model_part(name="present_options", signature=REAL_SIGNATURE)]

    assert main._serialize_function_calls(parts) == []


# ─── reconstruction: every replayed function_call part must carry a signature ───

def test_reconstruct_restores_the_real_signature():
    transcript = [
        {"role": "user", "text": "what's missing?"},
        {"role": "model", "tool_calls": [{
            "id": "c1", "name": "read_checklist", "args": {"name": "Trip"},
            "thought_signature": base64.b64encode(REAL_SIGNATURE).decode(),
        }]},
    ]

    part = _function_call_parts(main._reconstruct_agent_contents(transcript))[0]

    assert part.thought_signature == REAL_SIGNATURE


def test_reconstruct_substitutes_placeholder_for_a_stale_client():
    """A build predating this field sends no signature — Gemini 3.x would 400 on that.

    The documented placeholder is what keeps those installs working with no app release,
    so this is the assertion that guards the prod fix.
    """
    transcript = [
        {"role": "user", "text": "what's missing?"},
        {"role": "model", "tool_calls": [
            {"id": "c1", "name": "read_checklist", "args": {"name": "Trip"}},
        ]},
    ]

    part = _function_call_parts(main._reconstruct_agent_contents(transcript))[0]

    assert part.thought_signature == main._LEGACY_THOUGHT_SIGNATURE
    assert part.thought_signature, "a replayed call must never go out unsigned"


def test_reconstruct_falls_back_to_placeholder_on_undecodable_signature():
    """Garbage in the transcript must degrade the turn, never 500 it."""
    transcript = [
        {"role": "user", "text": "hi"},
        {"role": "model", "tool_calls": [{
            "id": "c1", "name": "read_checklist", "args": {},
            "thought_signature": "!!! not base64 !!!",
        }]},
    ]

    part = _function_call_parts(main._reconstruct_agent_contents(transcript))[0]

    assert part.thought_signature == main._LEGACY_THOUGHT_SIGNATURE


def test_placeholder_is_the_google_documented_value():
    """Only Google's documented strings pass validation — an invented one 400s the same."""
    assert main._LEGACY_THOUGHT_SIGNATURE == b"skip_thought_signature_validator"


# ─── the full contract, end to end through both production functions ───

def test_round_trip_serialize_then_reconstruct_preserves_signature():
    """Round 1 output -> client transcript -> round 2 input. The exact path that broke."""
    tool_calls = main._serialize_function_calls([_model_part(signature=REAL_SIGNATURE)])

    transcript = [
        {"role": "user", "text": "what's missing?"},
        {"role": "model", "tool_calls": tool_calls},
        {"role": "tool", "tool_results": [
            {"id": "c1", "name": "read_checklist", "result": {"status": "success"}},
        ]},
    ]
    part = _function_call_parts(main._reconstruct_agent_contents(transcript))[0]

    assert part.thought_signature == REAL_SIGNATURE
    assert part.function_call.name == "read_checklist"
    assert part.function_call.args == {"name": "Trip"}


def test_tool_results_are_not_given_a_signature():
    """Only model-emitted function_call parts need one; function_response parts must not."""
    transcript = [
        {"role": "tool", "tool_results": [
            {"id": "c1", "name": "read_checklist", "result": {"status": "success"}},
        ]},
    ]

    contents = main._reconstruct_agent_contents(transcript)
    responses = [p for c in contents for p in c.parts if getattr(p, "function_response", None)]

    assert len(responses) == 1
    assert not responses[0].thought_signature
