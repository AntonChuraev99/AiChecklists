"""Pure credit-reservation decision logic (unit-testable without firebase_admin).

Mirrors the cors.py / generated_items.py pattern: the money-critical branch of
reserve_credits lives here as a side-effect-free function so it can be tested fast
and in isolation, while main.py owns the Firestore transaction plumbing around it.
"""


def reservation_decision(
    user_exists: bool,
    current_balance: int,
    cost: int,
    prior_remaining: int | None,
) -> tuple[str, int | None]:
    """Decide what reserve_credits' transaction should do.

    Args:
        user_exists: whether the user doc exists.
        current_balance: the user's current ai_credits.
        cost: action_cost to reserve.
        prior_remaining: balance recorded by a PREVIOUS reservation for the same
            (user_id, request_id), or None when no prior reservation exists.

    Returns (action, value):
        ("no_user", None)       -> user doc missing.
        ("replay", prior)       -> idempotent replay: do NOT deduct again; return the
                                   balance recorded at first reserve. Guards against the
                                   double-charge when the client retries a transport drop
                                   that happened AFTER a server success.
        ("insufficient", None)  -> not enough credits.
        ("reserve", new_count)  -> fresh reservation; deduct `cost`.
    """
    if not user_exists:
        return ("no_user", None)
    # Replay wins even if the balance is now < cost: the charge already happened once.
    if prior_remaining is not None:
        return ("replay", prior_remaining)
    if current_balance < cost:
        return ("insufficient", None)
    return ("reserve", current_balance - cost)
