"""Unit tests for chat_agent_logic.py — the CURRENT-TURN boundary that chat_agent's charge
and round-cap both hang off. Dependency-free (no firebase_admin), like test_credit_idempotency.

tests/test_chat_agent_turn_boundary.py proves the HANDLER charges correctly; this file pins the
pure helper's contract directly. current_turn_start_index is tested on its own because its exact
value is invisible to scan_current_turn (a `user` entry is neither a tool turn nor a tool-call
round, so including or excluding it changes no count) — yet it is the index the planned
transcript-truncation policy must cut from. An off-by-one there silently drops the user's own
message from the turn it belongs to.

Run from firebase-functions/:
    python -m pytest tests/test_chat_agent_logic.py -q
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from chat_agent_logic import current_turn_start_index, scan_current_turn

USER = {"role": "user", "text": "add milk"}
MODEL_PROSE = {"role": "model", "text": "Added milk."}
MODEL_CALLS = {"role": "model", "tool_calls": [{"id": "c1", "name": "add_item", "args": {}}]}
TOOL = {"role": "tool", "tool_results": [{"id": "c1", "name": "add_item", "result": {}}]}


# ---------------------------------------------------------------------------
# current_turn_start_index — one past the LAST user entry
# ---------------------------------------------------------------------------

def test_start_index_is_one_past_the_last_user_entry():
    # The user entry at index 3 opens the current turn; the turn's ping-pong starts at 4.
    assert current_turn_start_index([USER, MODEL_CALLS, TOOL, USER]) == 4


def test_start_index_ignores_earlier_user_entries():
    assert current_turn_start_index([USER, MODEL_PROSE, USER, MODEL_CALLS, TOOL]) == 3


def test_start_index_of_single_user_entry():
    assert current_turn_start_index([USER]) == 1


def test_start_index_without_user_entry_scans_everything():
    # Degenerate shape: no user message at all -> whole array, i.e. pre-Stage-3 semantics.
    assert current_turn_start_index([MODEL_CALLS, TOOL]) == 0


def test_start_index_of_empty_transcript():
    assert current_turn_start_index([]) == 0


def test_start_index_tolerates_non_dict_entries():
    # main.py validates entries before this runs, but the helper must not raise on junk.
    assert current_turn_start_index(["junk", None, USER, MODEL_CALLS]) == 3


def test_start_index_normalises_role_case_and_padding():
    assert current_turn_start_index([MODEL_CALLS, {"role": " USER ", "text": "hi"}]) == 2


# ---------------------------------------------------------------------------
# scan_current_turn — (is_first_round, agent_round_count)
# ---------------------------------------------------------------------------

def test_new_turn_after_past_tool_rounds_is_first_round():
    # THE Stage-3 case: tool entries exist but belong to a PAST turn -> this turn must charge.
    assert scan_current_turn([USER, MODEL_CALLS, TOOL, MODEL_PROSE, USER]) == (True, 0)


def test_follow_up_round_of_current_turn_is_not_first():
    assert scan_current_turn([USER, MODEL_CALLS, TOOL]) == (False, 1)


def test_legacy_prod_first_round_shape_is_first_round():
    # Today's client: history seeded as prose, newest user message last.
    assert scan_current_turn([USER, MODEL_PROSE, USER]) == (True, 0)


def test_model_prose_is_not_an_agentic_round():
    # Conversation context must never count toward the per-turn round cap.
    assert scan_current_turn([USER, MODEL_PROSE, MODEL_PROSE, MODEL_PROSE]) == (True, 0)


def test_rounds_counted_only_within_current_turn():
    # 5 rounds spent on PAST turns + a fresh user message -> current turn has spent none.
    past = [USER] + [MODEL_CALLS, TOOL] * 5
    assert scan_current_turn(past + [USER]) == (True, 0)


def test_rounds_counted_within_current_turn():
    assert scan_current_turn([USER] + [MODEL_CALLS, TOOL] * 5) == (False, 5)


def test_no_user_entry_preserves_whole_array_semantics():
    # Old formula read "tool anywhere" => not first round. Must not become a fresh charge.
    assert scan_current_turn([MODEL_CALLS, TOOL]) == (False, 1)


def test_empty_transcript_is_first_round_with_no_rounds():
    assert scan_current_turn([]) == (True, 0)


def test_model_entry_with_empty_tool_calls_is_not_a_round():
    assert scan_current_turn([USER, {"role": "model", "tool_calls": []}]) == (True, 0)


def test_multiple_tool_calls_in_one_model_turn_is_one_round():
    # A round is a model TURN, not a tool call: parallel calls in one turn cost one round.
    multi = {"role": "model", "tool_calls": [
        {"id": "c1", "name": "add_item", "args": {}},
        {"id": "c2", "name": "add_item", "args": {}},
    ]}
    assert scan_current_turn([USER, multi, TOOL]) == (False, 1)


def test_equivalence_with_legacy_formula_on_all_legacy_shapes():
    """The back-compat proof, executable.

    On any transcript today's client can produce, tool entries only ever appear AFTER the last
    user message (the client rebuilds the seed from message TEXT, so past tool calls cannot be
    replayed). On that whole family, the new per-turn reading and the old whole-array reading
    must agree — otherwise the deploy changes what a store client pays.
    """
    def legacy(transcript):
        has_tool = any(e.get("role") == "tool" for e in transcript)
        rounds = sum(1 for e in transcript if e.get("role") == "model" and e.get("tool_calls"))
        return (not has_tool, rounds)

    # Seeded history (user/model prose only) + newest user message + N rounds of this turn.
    for history_pairs in range(0, 4):
        for rounds in range(0, 6):
            legacy_shape = [USER, MODEL_PROSE] * history_pairs
            legacy_shape += [USER]
            legacy_shape += [MODEL_CALLS, TOOL] * rounds
            assert scan_current_turn(legacy_shape) == legacy(legacy_shape), (
                f"per-turn reading diverges from prod behaviour on "
                f"history_pairs={history_pairs} rounds={rounds}: {legacy_shape}"
            )
