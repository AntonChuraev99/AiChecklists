"""Pure, unit-testable helpers for the promotional push sender (``send_promotions_batch``).

Kept import-clean (only the stdlib — no ``firebase_admin``) like ``cors.py`` /
``generated_items.py``, so the audience filter, copy A/B selection, FCM data-payload
builder, Amplitude event builder and send-error classifier can be tested WITHOUT
initializing the Admin SDK / Firestore (main.py builds a Firestore client at import).

The HTTP entrypoint (``send_promotions_batch``) and every Firebase Admin call
(Firestore query, ``messaging.send_each_for_multicast``, the Amplitude HTTP POST,
the RC server-template arm assignment) live in ``main.py``.
"""
from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any

# ---------------------------------------------------------------------------
# Promotional layer contract (report §5 / §6, Phase-3 brief).
# ---------------------------------------------------------------------------
# push_type values this PROMOTIONAL sender is allowed to emit. Every one maps to
# channel="promo" + audience_class="promotional". FUNCTIONAL pushes (reminders,
# streak/save, overdue) are NEVER sent from here — a paying user still wants those.
PROMO_PUSH_TYPES = {"reengagement", "winback", "digest", "tip", "release", "upsell"}

# Copy A/B — the one active experiment for the promo layer. The arm comes from the RC
# server-template param ``push_ab_arm`` (control|a|b, assigned by a percent condition on
# randomization_id, exactly like the AI-model experiment). The copy text comes from RC
# param ``push_copy_variants_json`` (console-editable, no redeploy) with the in-code table
# below as the fail-safe fallback.
PUSH_AB_EXPERIMENT = "copy"
PUSH_ARM_ALLOWLIST = {"control", "a", "b"}

# Fallback copy used when RC ``push_copy_variants_json`` is absent / unparseable.
# Shape: {push_type: {arm: {"title": ..., "body": ...}}}. "control" MUST exist for
# every push_type (it is the ultimate fallback when an arm has no override).
DEFAULT_PUSH_COPY: dict[str, dict[str, dict[str, str]]] = {
    "reengagement": {
        "control": {"title": "Your checklists are waiting",
                    "body": "Got a new plan to knock out today?"},
        "a": {"title": "Pick up where you left off",
              "body": "Your lists are ready when you are."},
        "b": {"title": "One small win today?",
              "body": "Open a checklist and check off a thing."},
    },
    "winback": {
        "control": {"title": "It's been a while",
                    "body": "Here's a quick template to get started again."},
        "a": {"title": "Still got plans to tackle?",
              "body": "Jump back in — setting up a list takes a minute."},
        "b": {"title": "Your checklists missed you",
              "body": "Start fresh with an AI-made list in seconds."},
    },
    "digest": {
        "control": {"title": "Your week in checklists",
                    "body": "See what you finished and what's still open."},
    },
    "tip": {
        "control": {"title": "Tip: try AI Chat",
                    "body": "Describe any goal and get a full checklist."},
    },
    "release": {
        "control": {"title": "What's new in Gisti",
                    "body": "Fresh updates are ready — take a look."},
    },
    "upsell": {
        "control": {"title": "You're getting a lot done",
                    "body": "Premium removes the checklist limit."},
    },
}


def to_aware_datetime(value: Any) -> datetime | None:
    """Best-effort convert a Firestore timestamp / ISO string → aware UTC datetime.

    Handles: ``DatetimeWithNanoseconds`` and plain ``datetime`` (Firestore Timestamp is a
    ``datetime`` subclass), ISO-8601 strings (with trailing ``Z``), and epoch numbers.
    Returns ``None`` for anything unrecognised (caller treats that as "no prior send").
    """
    if value is None:
        return None
    if isinstance(value, datetime):
        return value if value.tzinfo else value.replace(tzinfo=timezone.utc)
    if isinstance(value, (int, float)):
        try:
            return datetime.fromtimestamp(float(value), tz=timezone.utc)
        except (ValueError, OverflowError, OSError):
            return None
    if isinstance(value, str):
        try:
            return datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            return None
    return None


def resolve_channel(push_type: str) -> str:
    """Promo layer → always the low-intrusion "promo" channel (report §5.2)."""
    return "promo"


def select_copy(push_type: str, arm: str, variants_json_str: str) -> tuple[str, str]:
    """Return ``(title, body)`` for [push_type]/[arm].

    Precedence: RC ``variants_json[push_type][arm]`` → RC ``[push_type]["control"]``
    → in-code ``DEFAULT_PUSH_COPY[push_type][arm]`` → ``[push_type]["control"]``
    → a generic last resort. Malformed RC JSON never raises — it silently falls back.
    """
    # 1) RC-driven variants (console-editable, no redeploy).
    if variants_json_str:
        try:
            variants = json.loads(variants_json_str)
            block = variants.get(push_type) or {}
            entry = block.get(arm) or block.get("control")
            if entry and entry.get("title") and entry.get("body"):
                return str(entry["title"]), str(entry["body"])
        except (ValueError, AttributeError, TypeError):
            pass  # malformed RC JSON must never break a send

    # 2) In-code fallback table.
    block = DEFAULT_PUSH_COPY.get(push_type) or {}
    entry = block.get(arm) or block.get("control")
    if entry:
        return entry["title"], entry["body"]

    # 3) Last-resort generic copy.
    return "Your checklists are waiting", "Open Gisti to pick up where you left off."


def is_eligible_for_promo(
    user_data: dict, now: datetime, promo_cooldown_hours: float
) -> tuple[bool, str]:
    """Client-side audience filter for the PROMOTIONAL cohort.

    Returns ``(eligible, skip_reason)``. Excludes, in order:
      * ``no_token``          — no usable ``fcmToken``
      * ``premium``           — paying user (NEVER promo: compliance + measurement)
      * ``holdout``           — retention control group (``pushHoldout == true``)
      * ``frequency_capped``  — a promo already went out inside the cooldown window

    NOTE — consent model is "opt-OUT via the Tips & Offers notification channel", chosen
    2026-07-03 over an explicit promoOptIn soft-ask. There is deliberately NO opt-in gate
    here: a user who disables the "Tips & Offers" channel has the push dropped by the OS
    before display, so the server targets all token-holders (minus premium/holdout/cap) and
    the channel is the control surface.
    """
    token = user_data.get("fcmToken")
    if not token or not isinstance(token, str):
        return False, "no_token"
    # Premium suppression — paying users are NEVER in the promo cohort.
    if user_data.get("is_premium") is True:
        return False, "premium"
    # Holdout control group — never receives promo (assigned Phase-2 client-side).
    if user_data.get("pushHoldout") is True:
        return False, "holdout"
    # No opt-in gate here (consent = Tips & Offers channel opt-out; see docstring). The OS
    # drops the push if the user disabled that channel, so the server targets all token-holders.
    # Frequency cap — respect the industry ~1 promo/day ceiling (report §7).
    last_dt = to_aware_datetime(user_data.get("lastPromoSentAt"))
    if last_dt is not None:
        if (now - last_dt).total_seconds() < promo_cooldown_hours * 3600:
            return False, "frequency_capped"
    return True, ""


def build_data_payload(
    push_type: str,
    campaign_id: str,
    arm: str,
    title: str,
    body: str,
    checklist_id: str | None = None,
) -> dict[str, str]:
    """FCM ``data`` map. ALL values MUST be strings (FCM requirement). Contract-fixed keys.

    The client (Phase 2) reads these exact keys — do not rename them.
    """
    data = {
        "title": title,
        "body": body,
        "channel": resolve_channel(push_type),   # "promo"
        "push_type": push_type,
        "audience_class": "promotional",
        "campaign_id": campaign_id,
        "push_ab_experiment": PUSH_AB_EXPERIMENT,  # "copy"
        "push_ab_arm": arm,                        # control | a | b
    }
    if checklist_id:
        data["checklist_id"] = str(checklist_id)
    return data


def build_amplitude_event(
    uid: str,
    push_type: str,
    campaign_id: str,
    arm: str,
    now_ms: int,
    insert_id: str,
) -> dict[str, Any]:
    """One Amplitude HTTP V2 event for a delivered ``push_sent`` (the CTR denominator).

    ``insert_id`` = ``"{campaign_id}:{uid}"`` so re-runs of the same campaign de-dupe.
    ``is_premium``/``push_holdout`` are ``False`` by construction (both filtered out above).
    """
    return {
        "user_id": uid,
        "event_type": "push_sent",
        "time": now_ms,
        "insert_id": insert_id,
        "event_properties": {
            "push_type": push_type,
            "channel": "promo",
            "audience_class": "promotional",
            "campaign_id": campaign_id,
            "is_premium": False,
            "push_holdout": False,
            "push_ab_experiment": PUSH_AB_EXPERIMENT,
            "push_ab_arm": arm,
        },
        # Mirror the holdout flag as a user property (report §6.1 — every retention chart
        # can then split exposed vs holdout). These are all exposed → False.
        "user_properties": {"push_holdout": False},
    }


def classify_send_error(exc: Any) -> str:
    """Classify a failed FCM send WITHOUT importing ``firebase_admin`` (string-based → testable).

    Returns ``"unrecoverable"`` (the token is dead → delete it) or ``"transient"``
    (quota/unavailable/internal → keep the token, it may work next run).

    ``UnregisteredError`` / ``SenderIdMismatchError`` / ``ThirdPartyAuthError`` are the
    firebase-admin token-death types. ``InvalidArgumentError`` is ambiguous, so we also
    sniff the message for the known dead-token phrases.
    """
    name = type(exc).__name__
    if name in ("UnregisteredError", "SenderIdMismatchError", "ThirdPartyAuthError"):
        return "unrecoverable"
    msg = str(exc).lower()
    dead_markers = (
        "registration-token-not-registered",
        "invalid registration",
        "requested entity was not found",
        "not a valid fcm registration token",
        "invalid-registration-token",
    )
    if any(marker in msg for marker in dead_markers):
        return "unrecoverable"
    return "transient"


def chunked(seq, size):
    """Yield successive [size]-length chunks of [seq] (FCM ≤500 tokens; Amplitude ≤100 events)."""
    for i in range(0, len(seq), size):
        yield seq[i:i + size]
