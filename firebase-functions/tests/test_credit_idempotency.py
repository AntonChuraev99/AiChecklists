"""Unit tests for reservation_decision (credits_logic.py) — the money-critical branch of
reserve_credits. Locks the idempotency guard that stops an AI-request retry from
double-charging credits (found by bug-pattern-reviewer L2, 2026-07-09).

Run from firebase-functions/:
    python -m pytest tests/test_credit_idempotency.py -q
or without pytest:
    python tests/test_credit_idempotency.py

No Firebase credentials needed — credits_logic.py is dependency-free (like generated_items.py).
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from credits_logic import reservation_decision

COST = 20


def test_fresh_reserve_deducts_cost():
    # No prior reservation, enough balance -> deduct once.
    assert reservation_decision(True, 100, COST, None) == ("reserve", 80)


def test_replay_does_not_deduct_again():
    # THE double-charge guard: same request_id already reserved (recorded remaining 80).
    # A retry must NOT deduct again — it returns the recorded balance, balance untouched.
    assert reservation_decision(True, 80, COST, 80) == ("replay", 80)


def test_replay_wins_even_when_balance_now_insufficient():
    # After the first reserve the user may have spent down to < cost. The replay of the
    # ALREADY-charged request must still succeed idempotently, never re-charge or 402.
    assert reservation_decision(True, 5, COST, 80) == ("replay", 80)


def test_replay_returns_recorded_zero_balance():
    # remaining_after can legitimately be 0 (spent the last credits). 0 is not None, so it
    # must be treated as a real replay, not fall through to a fresh reserve.
    assert reservation_decision(True, 0, COST, 0) == ("replay", 0)


def test_insufficient_credits_returns_none():
    # No prior reservation and balance < cost -> caller returns 402.
    assert reservation_decision(True, COST - 1, COST, None) == ("insufficient", None)


def test_exact_balance_reserves_to_zero():
    # Boundary: balance == cost is enough (not "insufficient"); reserves down to 0.
    assert reservation_decision(True, COST, COST, None) == ("reserve", 0)


def test_missing_user_returns_none():
    assert reservation_decision(False, 0, COST, None) == ("no_user", None)


def test_missing_user_takes_precedence_over_prior():
    # Defensive: a missing user doc short-circuits before any replay consideration.
    assert reservation_decision(False, 100, COST, 80) == ("no_user", None)


if __name__ == "__main__":
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
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
