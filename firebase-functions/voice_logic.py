"""Pure voice-call accounting logic (unit-testable without firebase_admin).

Mirrors the cors.py / credits_logic.py / chat_agent_logic.py pattern: every money-critical
decision of a voice call lives here as a side-effect-free function, while main.py keeps the
HTTP validation and the Firestore transactions around it.

Minutes are counted by the SERVER clock only (ported from AiTeacher voiceSession.ts /
sessionStore.ts). A heartbeat or end charges min(now - last_beat_at, BEAT_CHARGE_MAX_SEC),
capped by what is left of the quota and of the per-call ceiling. The client never reports
elapsed time — a client that lied (or sent nothing) cannot stretch or shrink its minutes.
A session without a heartbeat for SESSION_IDLE_SEC is over and charges nothing more; it is
closed lazily on the next request that touches it (no scheduler).

Storage (main.py):
    users/{uid}/voice_sessions/{session_id}  — one call: status, timestamps, charged seconds
    users/{uid}/voice/usage                  — lifetime_seconds, day_key (UTC), day_seconds

Plans:
    free     — voice_free_lifetime_seconds for the life of the user (all voice seconds ever)
    premium  — voice_premium_daily_seconds per UTC day
Both plans also cap one call at voice_max_call_seconds.
"""

import math
import re
from datetime import datetime, timezone

# A heartbeat / end charges at most this much since the previous beat.
BEAT_CHARGE_MAX_SEC = 20
# Without a heartbeat for this long the call is over (closed lazily).
SESSION_IDLE_SEC = 45
# What the client is told to use; BEAT_CHARGE_MAX_SEC leaves 5 s of jitter on top of it.
HEARTBEAT_INTERVAL_SEC = 15

DEFAULT_FREE_LIFETIME_SECONDS = 300
DEFAULT_PREMIUM_DAILY_SECONDS = 1200
DEFAULT_MAX_CALL_SECONDS = 600

# chat_agent voice turns (first rounds) per session.
TURN_RATE_WINDOW_SEC = 60
TURN_RATE_MAX_PER_WINDOW = 20
TURN_MIN_GAP_SEC = 1.5

STATUS_OPEN = "open"
STATUS_ENDED = "ended"
STATUS_EXPIRED = "expired"

# End reasons written to the session doc and returned to the client.
REASON_QUOTA = "quota_exhausted"
REASON_MAX_CALL = "max_call_duration"
REASON_IDLE = "idle"
REASON_CLIENT_END = "client_end"
REASON_DISABLED = "disabled"


_SESSION_ID_RE = re.compile(r"[A-Za-z0-9_-]{8,64}")


def is_valid_session_id(value) -> bool:
    """A session id is used as a Firestore doc id: 8-64 chars of [A-Za-z0-9_-] (a UUID fits)."""
    return isinstance(value, str) and _SESSION_ID_RE.fullmatch(value) is not None


def utc_day_key(now: float) -> str:
    """UTC calendar day of [now] (epoch seconds) as YYYY-MM-DD — the premium quota day."""
    return datetime.fromtimestamp(now, tz=timezone.utc).strftime("%Y-%m-%d")


def _non_negative_int(value, default: int) -> int:
    """Coerce a Remote Config value; anything unusable falls back to [default]."""
    if isinstance(value, bool):
        return default
    try:
        n = int(value)
    except (TypeError, ValueError):
        return default
    return n if n >= 0 else default


def normalise_limits(raw: dict | None) -> dict:
    """Remote Config values -> {free_lifetime, premium_daily, max_call} (ints, >= 0)."""
    raw = raw or {}
    return {
        "free_lifetime": _non_negative_int(
            raw.get("voice_free_lifetime_seconds"), DEFAULT_FREE_LIFETIME_SECONDS),
        "premium_daily": _non_negative_int(
            raw.get("voice_premium_daily_seconds"), DEFAULT_PREMIUM_DAILY_SECONDS),
        "max_call": _non_negative_int(
            raw.get("voice_max_call_seconds"), DEFAULT_MAX_CALL_SECONDS),
    }


def quota_remaining(is_premium: bool, usage: dict | None, now: float, limits: dict) -> int:
    """Seconds left in the user's wallet right now (before any per-call ceiling)."""
    usage = usage or {}
    if is_premium:
        used_today = (
            int(usage.get("day_seconds") or 0)
            if usage.get("day_key") == utc_day_key(now) else 0
        )
        return max(0, limits["premium_daily"] - used_today)
    return max(0, limits["free_lifetime"] - int(usage.get("lifetime_seconds") or 0))


def apply_charge(usage: dict | None, sec: int, now: float) -> dict:
    """New usage aggregate after charging [sec] seconds (day_seconds rolls over at UTC midnight)."""
    usage = dict(usage or {})
    today = utc_day_key(now)
    day_seconds = int(usage.get("day_seconds") or 0) if usage.get("day_key") == today else 0
    usage["lifetime_seconds"] = int(usage.get("lifetime_seconds") or 0) + sec
    usage["day_key"] = today
    usage["day_seconds"] = day_seconds + sec
    return usage


def is_idle(session: dict, now: float) -> bool:
    """No heartbeat for longer than SESSION_IDLE_SEC."""
    return now - float(session.get("last_beat_at") or 0) > SESSION_IDLE_SEC


def is_live(session: dict | None, now: float) -> bool:
    """Open and beaten within SESSION_IDLE_SEC."""
    return bool(session) and session.get("status") == STATUS_OPEN and not is_idle(session, now)


def beat_charge(session: dict, now: float, remaining: int, max_call: int) -> int:
    """Seconds a heartbeat / end may charge, by the server clock only.

    The gap since the last beat, floored to whole seconds and capped at BEAT_CHARGE_MAX_SEC,
    then cut to what is left of the per-call ceiling and of the wallet. An idle session (gap
    over SESSION_IDLE_SEC) charges nothing: the call is considered over since its last beat.
    """
    gap = now - float(session.get("last_beat_at") or 0)
    if gap > SESSION_IDLE_SEC:
        return 0
    gap_sec = int(math.floor(max(0.0, gap)))
    call_left = max_call - int(session.get("charged_seconds") or 0)
    return max(0, min(gap_sec, BEAT_CHARGE_MAX_SEC, call_left, remaining))


def call_remaining(session: dict, remaining: int, max_call: int) -> int:
    """Seconds this call may still run: the smaller of the wallet and the per-call ceiling."""
    return max(0, min(remaining, max_call - int(session.get("charged_seconds") or 0)))


def end_reason_after_charge(charged_seconds: int, remaining: int, max_call: int) -> str | None:
    """Why the call must end after a charge, or None when it may go on."""
    if remaining <= 0:
        return REASON_QUOTA
    if charged_seconds >= max_call:
        return REASON_MAX_CALL
    return None


def voice_turn_verdict(session: dict | None, now: float, remaining: int, max_call: int) -> str | None:
    """Why a chat_agent voice turn may NOT ride on [session], or None when it may.

    not_found -> no such session for this user; closed -> ended/expired; expired -> open but
    without a heartbeat for SESSION_IDLE_SEC; exhausted -> wallet or per-call ceiling spent.
    """
    if not session:
        return "not_found"
    if session.get("status") != STATUS_OPEN:
        return "closed"
    if is_idle(session, now):
        return "expired"
    if call_remaining(session, remaining, max_call) <= 0:
        return "exhausted"
    return None


def turn_rate_decision(turn_times: list | None, now: float) -> tuple[bool, list, float]:
    """Rate-limit voice turns of one session.

    Returns (allowed, new_turn_times, retry_after_seconds). Keeps only the timestamps of the
    last TURN_RATE_WINDOW_SEC; refuses a turn closer than TURN_MIN_GAP_SEC to the previous
    one, or beyond TURN_RATE_MAX_PER_WINDOW in the window. A refused turn is NOT recorded.
    """
    recent = sorted(
        float(t) for t in (turn_times or [])
        if isinstance(t, (int, float)) and now - float(t) < TURN_RATE_WINDOW_SEC
    )
    if recent and now - recent[-1] < TURN_MIN_GAP_SEC:
        return False, recent, round(TURN_MIN_GAP_SEC - (now - recent[-1]), 3)
    if len(recent) >= TURN_RATE_MAX_PER_WINDOW:
        return False, recent, round(TURN_RATE_WINDOW_SEC - (now - recent[0]), 3)
    return True, recent + [now], 0.0
