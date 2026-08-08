"""
Firebase Cloud Functions for AI Checklists App.

Functions:
1. register_user - Register or retrieve user by device ID (web: 0 credits, mobile: 100)
1b. link_google_account - Link Google account to existing user (web: grants starter pack)
2. analyze_and_fill_checklist - Auto-fill existing checklist based on user data
3. generate_checklist - Create new checklist from prompt + user data
4. get_usage_stats - Get user's AI usage statistics
5. refill_premium_credits - Daily credits refill for premium users (called by Cloud Scheduler at 12:00 CET)
6. restore_credits_after_purchase - Instantly restore credits after premium purchase
7. get_credits_info - Get credits configuration and user's current credits

All AI calls go through these functions for usage control and monitoring.
Credits are deducted for all users (including premium). Premium users get daily refill to cap.
"""

import asyncio
import base64
import json
import logging
import os
import threading
import time
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

import firebase_admin
from firebase_admin import auth as firebase_auth, credentials, firestore, messaging, remote_config
from flask import Request, jsonify, make_response
from google import genai
from google.genai import types
# Firestore query filters MUST be built with FieldFilter, never positionally:
# `query.where("f", "==", v)` is deprecated and makes the SDK emit a UserWarning
# ("Detected filter using positional arguments") on EVERY call — hundreds of lines of
# log noise per month across register_user / link_google_account / refill_premium_credits /
# send_promotions_batch. `where(filter=FieldFilter(...))` is the semantically identical
# supported form.
from google.cloud.firestore_v1.base_query import FieldFilter
import functions_framework
from firebase_functions import firestore_fn  # 2nd gen Firestore trigger
import requests as http_requests  # avoid conflict with flask Request
from flask import request as flask_request  # global request context for CORS origin echo-back

import cors  # local module: CORS origin whitelist (unit-testable without firebase_admin)
from generated_items import MAX_FOLDER_DEPTH, sanitize_generated_items  # nested AI-item sanitizer (unit-testable)
from credits_logic import reservation_decision  # local module: reserve-credits branch (unit-testable)
from chat_agent_logic import scan_current_turn  # local module: chat_agent turn boundary (unit-testable)
import push_promotions  # local module: promo-push audience filter / copy A/B / payload (unit-testable)

# Module logger — replaces bare print(); logger.exception() in except blocks emits the
# traceback to Cloud Logging stderr so real 5xx causes are no longer invisible.
logger = logging.getLogger("gisti")

# Initialize Firebase Admin
if not firebase_admin._apps:
    firebase_admin.initialize_app()

db = firestore.client()

# Configure Gemini — google-genai GA SDK (replaced deprecated google-generativeai, EOL 2025-11-30).
# A single module-level Client is reused across invocations of a warm container.
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")
gemini_client = genai.Client(api_key=GEMINI_API_KEY) if GEMINI_API_KEY else None

# ----------------------------------------------------------------------------
# Test-only Gemini model override (cost-gated).
#
# Lets an offline evaluation harness (firebase-functions/tests/ai_model_eval.py)
# run the same scenarios across multiple Gemini models to compare quality, WITHOUT
# changing the production defaults. This is the ONLY supported way to swap models
# per request and it is locked down three ways because the CF is public:
#   1. MODEL_OVERRIDE_TEST_SECRET must be configured in the CF environment. When it
#      is empty/unset, the override is fully DISABLED — prod behaves exactly as before.
#   2. The request must present a matching `test_secret`.
#   3. The requested model must be in MODEL_OVERRIDE_ALLOWLIST (bounds cost — no
#      arbitrary/expensive model can be forced by a hostile caller).
# Any check failing → the endpoint's normal default model is used, silently.
MODEL_OVERRIDE_TEST_SECRET = os.environ.get("MODEL_OVERRIDE_TEST_SECRET", "")
# Every id here is verified reachable — probed against the live API 2026-07-15.
# An allow-listed model that 404s is worse than an absent one: the gate waves it through,
# and an RC arm pointing at it 500s every request for that flow. gemini-2.5-pro,
# gemini-2.0-flash and gemini-2.0-flash-lite were removed for exactly that reason
# ("no longer available to new users"). Re-probe before adding an id back.
# Bounds cost too: only these can be selected by the experiment config or the test-override
# gate, so a fat-fingered config value can never reach an arbitrary (expensive) model.
# NOTE: the gemini-2.5-* ids below reach EOL 2026-10-16 — migrate the control arm before then.
MODEL_OVERRIDE_ALLOWLIST = {
    "gemini-2.5-flash-lite",
    "gemini-2.5-flash",
    # 3.x tier — production A/B experiment arms (see assign_model_arm).
    "gemini-3.1-flash-lite",
    "gemini-3.5-flash",
}


def resolve_model(default_model: str, requested_model, provided_secret) -> str:
    """Return [requested_model] only when the test-override gate fully passes; else [default_model].

    See MODEL_OVERRIDE_TEST_SECRET docs above for the three-part gate. Prod-safe by
    construction: with no secret configured this always returns [default_model].
    """
    if not requested_model:
        return default_model
    if not MODEL_OVERRIDE_TEST_SECRET:
        return default_model  # override disabled (no secret configured in CF env)
    if not provided_secret or provided_secret != MODEL_OVERRIDE_TEST_SECRET:
        return default_model  # unauthorized caller
    if requested_model not in MODEL_OVERRIDE_ALLOWLIST:
        return default_model  # not an allow-listed test model
    return requested_model


# ----------------------------------------------------------------------------
# Production A/B model experiment — Firebase Remote Config SERVER template.
#
# Each user gets a stable arm ("control" | "variant_b") from a Remote Config "User in
# random percentage" condition evaluated server-side against `randomization_id` (= our
# user_id). The RC server template (namespace firebase-server) holds:
#   - condition `variant_50`   = percent(<= split) on seed "expmodel1"
#   - param `ai_model_arm`      -> "variant_b" under the condition, else "control"
#   - param `ai_model_<flow>`   -> variant model under the condition, else the control model
# Managed entirely from the Firebase console (Remote Config → change template type to
# "Server"): adjust the split (condition %), a model (param value), or stop (condition to
# 0%) with native versioning + rollback — no redeploy, no Firestore doc, no hand-rolled hash.
#
# `get_server_template()` downloads the full template once per warm container (async → we
# bridge it with asyncio.run) and refreshes at most every _RC_TTL_SECONDS; `evaluate()` is
# a fast LOCAL call per request. The runtime service account needs roles/cloudconfig.viewer.
#
# Prod-safe by construction: RC load failure / missing param / unknown-or-disallowed model
# -> (default_model, "control") — EXACTLY today's behaviour. In-app defaults mirror control
# and every resolved model is re-checked against MODEL_OVERRIDE_ALLOWLIST (cost bound).
# Does NOT change economics — only the Gemini model string per request varies by arm.
_RC_MODEL_DEFAULTS = {
    "ai_model_arm": "control",
    "ai_model_chat_agent": "gemini-2.5-flash",
    "ai_model_classify_chat_intent": "gemini-2.5-flash-lite",
    "ai_model_analyze": "gemini-2.5-flash-lite",
    "ai_model_generate": "gemini-2.5-flash-lite",
    "ai_model_chat_completion": "gemini-2.5-flash",
}
_RC_TTL_SECONDS = 300  # refresh the server template at most every 5 min per warm container
_rc_template = None
_rc_loaded_at = 0.0
_rc_lock = threading.Lock()


def _get_rc_server_template():
    """Return a cached, periodically-refreshed RC server template, or None if never loaded."""
    global _rc_template, _rc_loaded_at
    if _rc_template is not None and (time.time() - _rc_loaded_at) < _RC_TTL_SECONDS:
        return _rc_template
    with _rc_lock:
        # Re-check under lock — another request thread may have just refreshed.
        if _rc_template is not None and (time.time() - _rc_loaded_at) < _RC_TTL_SECONDS:
            return _rc_template
        try:
            _rc_template = asyncio.run(
                remote_config.get_server_template(default_config=_RC_MODEL_DEFAULTS)
            )
            _rc_loaded_at = time.time()
        except Exception as e:  # noqa: BLE001 — never let RC break the AI path
            print(f"[ai_model_experiment] RC server template load failed: {type(e).__name__}: {e}")
    return _rc_template


def assign_model_arm(user_id: str, flow: str, default_model: str) -> tuple[str, str]:
    """Return (model_id, arm) for [user_id] on [flow] via the RC server template.

    Prod-safe: any RC failure (load / evaluate / missing / disallowed model) falls back to
    (default_model, "control"). Assignment is deterministic per user (RC hashes
    randomization_id) and identical across flows, so the client can read the arm from any
    single AI response and set it as a sticky analytics dimension.
    """
    template = _get_rc_server_template()
    if template is None:
        return default_model, "control"
    try:
        config = template.evaluate({"randomization_id": user_id})
        arm = config.get_string("ai_model_arm") or "control"
        model = config.get_string(f"ai_model_{flow}")
    except Exception as e:  # noqa: BLE001 — RC must never break the AI path
        print(f"[ai_model_experiment] RC evaluate failed flow={flow}: {type(e).__name__}: {e}")
        return default_model, "control"
    if not model or model not in MODEL_OVERRIDE_ALLOWLIST:
        # RC missing/unknown/disallowed model — fail safe to control default. Forcing the
        # arm to "control" too keeps arm↔model consistent (never attribute variant_b to a
        # request that actually ran the control model).
        return default_model, "control"
    return model, arm


def resolve_experiment_model(user_id: str, flow: str, default_model: str, data: dict) -> tuple[str, str]:
    """Resolve (model_id, arm). Precedence: eval test-override > server A/B > default.

    The offline eval harness (ai_model_eval.py) still wins when its secret-gated
    `model_override` is present, so multi-model comparison keeps working; that arm is
    labelled "override" so experiment analytics never counts eval traffic as a real arm.
    """
    base_model, arm = assign_model_arm(user_id, flow, default_model)
    overridden = resolve_model(base_model, data.get("model_override"), data.get("test_secret"))
    if overridden != base_model:
        return overridden, "override"
    return base_model, arm


# RevenueCat verification (V1 Secret key, NOT public key)
REVENUECAT_API_KEY = os.environ.get("REVENUECAT_API_KEY")

# ---------------------------------------------------------------------------
# Promotional push (send_promotions_batch) configuration.
# ---------------------------------------------------------------------------
# Amplitude server-side ingestion for the `push_sent` event — the ONLY reliable CTR
# denominator (a client can't count a push that never arrived). SECRET: read from env
# (Secret Manager `amplitude-server-key`), never hard-coded. Empty → push_sent emit is
# skipped (send still works, just uncounted). Region defaults to US to match the browser
# SDK (init.js `serverZone: 'US'`, project 786722); EU projects override the endpoint env.
AMPLITUDE_SERVER_API_KEY = os.environ.get("AMPLITUDE_SERVER_API_KEY", "")
AMPLITUDE_HTTP_ENDPOINT = os.environ.get(
    "AMPLITUDE_HTTP_ENDPOINT", "https://api2.amplitude.com/2/httpapi"
)

# Optional admin gate for the promo sender. When set (recommended for prod), a caller
# (Cloud Scheduler body / manual trigger) MUST present a matching `admin_key`. Unset →
# open, like refill_premium_credits (relies on the function being scheduler-invoked).
PUSH_ADMIN_KEY = os.environ.get("PUSH_ADMIN_KEY", "")

# Tokens per multicast call. FCM itself accepts 500, but that number is NOT the
# constraint that matters here: `messaging.send_each_for_multicast` fans out ONE THREAD
# PER TOKEN — `send_each()` builds `ThreadPoolExecutor(max_workers=len(messages))` — and
# every thread shares a single `requests.Session` whose urllib3 pool defaults to 10
# connections. A 500-token chunk therefore races 500 threads for 10 slots, and urllib3
# discards + reopens the surplus. Observed in prod as
# `Connection pool is full, discarding connection: fcm.googleapis.com` — 21 hits in 7d
# (2026-07-31), including 8 on the then-current revision. Every send still returned 2xx,
# so the cost was sockets and latency, never delivery.
#
# Sizing the chunk to the pool makes fan-out fit exactly. The audience is small (95
# eligible on the 2026-07-22 campaign), so the extra sequential round-trips are
# irrelevant, and this stays on public API: firebase-admin never exposed `pool_maxsize`
# (upstream request firebase/firebase-admin-python#648 was never implemented, and
# reaching into `_get_messaging_service()._client.session` is private-API territory).
#
# ⚠️ These two numbers move TOGETHER. Raising the chunk without also raising the urllib3
# pool re-creates the exact contention this constant exists to remove — test_main_structure
# asserts the bound so the regression fails the suite instead of the logs.
_FCM_URLLIB3_POOL_MAXSIZE = 10  # requests/urllib3 default; firebase-admin does not override it
_FCM_TOKENS_PER_MULTICAST = _FCM_URLLIB3_POOL_MAXSIZE

# Amplitude's HTTP V2 batch limit. Named for the same reason as the constant above: a bare
# number at the call site gives a reader no way to tell WHICH service's limit it encodes, and
# the structural guard cannot tell a deliberate limit from a regression.
_AMPLITUDE_EVENTS_PER_BATCH = 100

# ---------------------------------------------------------------------------
# Proprietary AI prompts live OUTSIDE the public repo.
#   real:   prompts_private.py          (gitignored — must be present at deploy)
#   public: prompts_private_example.py  (redacted stubs)
# ---------------------------------------------------------------------------
try:
    from prompts_private import (
        FILL_CHECKLIST_PROMPT,
        GENERATE_CHECKLIST_PROMPT,
        CLASSIFY_CHAT_INTENT_PROMPT,
        TRANSCRIBE_AUDIO_PROMPT,
        FEATURE_CATALOG_RU,
        FEATURE_CATALOG_EN,
        CHAT_COMPLETION_PROMPT_TEMPLATE,
        CHAT_AGENT_SYSTEM_TEMPLATE,
    )
except ImportError:  # public repo / CI without the private module
    from prompts_private_example import (
        FILL_CHECKLIST_PROMPT,
        GENERATE_CHECKLIST_PROMPT,
        CLASSIFY_CHAT_INTENT_PROMPT,
        TRANSCRIBE_AUDIO_PROMPT,
        FEATURE_CATALOG_RU,
        FEATURE_CATALOG_EN,
        CHAT_COMPLETION_PROMPT_TEMPLATE,
        CHAT_AGENT_SYSTEM_TEMPLATE,
    )

# Tri-state result for RevenueCat verification
VERIFIED = "verified"
NOT_VERIFIED = "not_verified"
UNAVAILABLE = "unavailable"

# Usage limits (can be overridden via Remote Config)
DEFAULT_DAILY_LIMIT_FREE = 10
DEFAULT_DAILY_LIMIT_PREMIUM = 100
DEFAULT_MAX_INPUT_LENGTH = 10000

# AI Credits system (configurable via remote_config collection in Firestore)
# Default values - can be changed remotely without redeploying
DEFAULT_INITIAL_CREDITS = 100  # Credits given to new users
DEFAULT_AI_ACTION_COST = 30    # Cost per AI action (analyze/generate)
DEFAULT_PREMIUM_DAILY_CREDITS_CAP = 300  # Max credits premium users get refilled to daily


def get_user_usage(user_id: str) -> dict:
    """Get user's daily usage stats."""
    today = datetime.utcnow().strftime("%Y-%m-%d")
    doc_ref = db.collection("usage").document(f"{user_id}_{today}")
    doc = doc_ref.get()

    if doc.exists:
        return doc.to_dict()
    return {"user_id": user_id, "date": today, "count": 0, "requests": []}


def increment_usage(user_id: str, function_name: str, input_type: str) -> dict:
    """Increment user's usage counter and log request."""
    today = datetime.utcnow().strftime("%Y-%m-%d")
    doc_ref = db.collection("usage").document(f"{user_id}_{today}")

    usage = get_user_usage(user_id)
    usage["count"] += 1
    usage["requests"].append({
        "function": function_name,
        "input_type": input_type,
        "timestamp": datetime.utcnow().isoformat(),
    })
    usage["last_request"] = datetime.utcnow().isoformat()

    doc_ref.set(usage)
    return usage


def check_usage_limit(user_id: str, is_premium: bool = False) -> tuple[bool, str]:
    """Check if user has exceeded daily usage limit."""
    usage = get_user_usage(user_id)
    limit = DEFAULT_DAILY_LIMIT_PREMIUM if is_premium else DEFAULT_DAILY_LIMIT_FREE

    if usage["count"] >= limit:
        return False, f"Daily limit of {limit} requests exceeded. Resets at midnight UTC."
    return True, ""


def get_remote_config_value(key: str, default: Any) -> Any:
    """Get value from Remote Config collection in Firestore."""
    try:
        doc = db.collection("remote_config").document("current").get()
        if doc.exists:
            config = doc.to_dict()
            return config.get(key, default)
    except Exception:
        pass
    return default


def validate_request(request: Request) -> tuple[dict | None, str | None]:
    """Validate incoming request and extract data."""
    if request.method != "POST":
        return None, "Only POST method is allowed"

    try:
        data = request.get_json()
    except Exception:
        return None, "Invalid JSON body"

    if not data:
        return None, "Request body is required"

    user_id = data.get("user_id")
    if not user_id:
        return None, "user_id is required"

    return data, None


def create_error_response(message: str, status_code: int = 400):
    """Create standardized error response with CORS headers so the web client
    can read the body even on non-2xx status."""
    return add_cors_headers(make_response(
        jsonify({"success": False, "error": message}), status_code
    ))


def create_success_response(data: dict):
    """Create standardized success response with CORS headers so any
    @functions_framework.http endpoint becomes browser-callable."""
    return add_cors_headers(make_response(jsonify({"success": True, **data})))


def _resolve_cors_origin() -> str | None:
    """Echo back the request Origin when it is whitelisted (the CORS spec allows
    only a single value in Access-Control-Allow-Origin, so a static list won't do).
    Native clients (Android) send no Origin header and are unaffected by CORS."""
    try:
        origin = flask_request.headers.get("Origin", "")
    except RuntimeError:
        # Outside request context — defensive, never expected under functions_framework.
        return None
    return origin if cors.origin_allowed(origin) else None


def add_cors_headers(response):
    """Add CORS headers to a Flask response for whitelisted browser origins."""
    origin = _resolve_cors_origin()
    if origin:
        response.headers["Access-Control-Allow-Origin"] = origin
    response.headers["Access-Control-Allow-Methods"] = "POST, OPTIONS"
    response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
    response.headers["Access-Control-Max-Age"] = "3600"
    response.headers["Vary"] = "Origin"
    return response


def cors_preflight_ok():
    """Return 204 No Content for CORS preflight OPTIONS requests."""
    return add_cors_headers(make_response("", 204))


# ============================================================================
# Firebase Auth token verification
# ============================================================================

def verify_firebase_token(request: Request) -> tuple[dict | None, tuple | None]:
    """
    Extract and verify Firebase ID token from Authorization header.
    Returns (decoded_token, None) on success, (None, None) when no token
    is present (fall through to legacy auth), or (None, (message, status))
    on invalid token.
    """
    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        return None, None

    id_token = auth_header[7:]
    try:
        decoded = firebase_auth.verify_id_token(id_token)
        return decoded, None
    except (firebase_auth.RevokedIdTokenError,
            firebase_auth.ExpiredIdTokenError,
            firebase_auth.InvalidIdTokenError):
        return None, ("Invalid or expired authentication token", 401)
    except firebase_auth.UserDisabledError:
        return None, ("User account is disabled", 403)
    except Exception as e:
        # Shared helper: every caller turns this tuple into a 500 of its own. Without a traceback
        # here the failure surfaces under the calling endpoint with no cause attached, so the
        # per-endpoint logging added on 2026-07-28 still reports "500" and nothing else.
        # The expected auth failures above are handled by type and stay unlogged on purpose.
        logger.exception("verify_firebase_token: verification failed (%s)", type(e).__name__)
        return None, ("Authentication verification failed", 500)


def get_authenticated_user_id(request: Request, data: dict) -> tuple[str | None, tuple | None]:
    """
    Resolve user_id from either Firebase token (new) or user_id body field (legacy).
    Firebase token takes precedence when present.
    Returns (user_id, None) on success or (None, (message, status)) on failure.
    """
    decoded_token, error = verify_firebase_token(request)
    if error:
        return None, error

    if decoded_token:
        firebase_uid = decoded_token["uid"]
        users = (db.collection("users")
                 .where(filter=FieldFilter("google_uid", "==", firebase_uid))
                 .limit(1).get())
        for user_doc in users:
            return user_doc.id, None
        return None, ("No linked user found. Please sign in first.", 404)

    user_id = data.get("user_id")
    if not user_id:
        return None, ("Authentication required", 401)

    user_doc = db.document(f"users/{user_id}").get()
    if not user_doc.exists:
        return None, ("User not found", 404)

    return user_id, None


# ============================================================================
# RevenueCat purchase verification
# ============================================================================

def verify_premium_with_revenuecat(user_id: str) -> str:
    """
    Verify user has active subscription via RevenueCat REST API.

    Kept as a fallback for verify_premium() during the rollout of the
    RevenueCat Firebase Extension and for direct backfill / admin scripts.

    Returns: VERIFIED, NOT_VERIFIED, or UNAVAILABLE.
    """
    if not REVENUECAT_API_KEY:
        return UNAVAILABLE

    try:
        resp = http_requests.get(
            f"https://api.revenuecat.com/v1/subscribers/{user_id}",
            headers={"Authorization": f"Bearer {REVENUECAT_API_KEY}"},
            timeout=5
        )
        if resp.status_code != 200:
            return NOT_VERIFIED

        data = resp.json()
        entitlements = data.get("subscriber", {}).get("entitlements", {})
        premium = entitlements.get("premium", {})
        if not premium:
            return NOT_VERIFIED

        expires = premium.get("expires_date")
        if expires is None:
            return VERIFIED  # lifetime

        if datetime.fromisoformat(expires.replace("Z", "+00:00")) > datetime.now(timezone.utc):
            return VERIFIED
        return NOT_VERIFIED
    except (http_requests.Timeout, http_requests.ConnectionError):
        return UNAVAILABLE
    except Exception:
        return NOT_VERIFIED


def _parse_iso_timestamp(value: Any) -> datetime | None:
    """Parse a RevenueCat Extension timestamp (ISO string or Firestore Timestamp)."""
    if value is None:
        return None
    # Firestore Timestamp (has .to_datetime() or is already datetime)
    if isinstance(value, datetime):
        return value if value.tzinfo else value.replace(tzinfo=timezone.utc)
    if hasattr(value, "to_datetime"):
        try:
            parsed = value.to_datetime()
            return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)
        except Exception:
            return None
    # ISO 8601 string
    try:
        s = str(value).replace("Z", "+00:00")
        parsed = datetime.fromisoformat(s)
        return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)
    except (ValueError, TypeError):
        return None


def verify_premium_from_firestore(user_id: str) -> str:
    """
    Verify premium via rc_customers/{user_id} — populated by the RevenueCat
    Firebase Extension (firestore-revenuecat-purchases) on every subscription
    event. Extension schema (per extension v0.1.18):

        {
          "original_app_user_id": "<uuid>",
          "entitlements": {
            "<entitlement_id>": {
              "expires_date": "<ISO 8601 string>",       # nullable for lifetime
              "grace_period_expires_date": "<ISO 8601>",  # optional
              "purchase_date": "<ISO 8601>",
              "product_identifier": "<product_id>"
            }
          },
          "aliases": ["<app_user_id>", ...]
        }

    Any entitlement whose expires_date or grace_period_expires_date is in the
    future counts as premium. No entitlements = NOT_VERIFIED.

    Mirrors the REST contract: VERIFIED / NOT_VERIFIED / UNAVAILABLE.
    NOT_VERIFIED is returned both when the customer has no active entitlement
    AND when no document exists (extension not installed, or new user never
    surfaced to the extension yet). Callers should chain with the REST
    fallback via verify_premium().
    """
    try:
        doc = db.collection("rc_customers").document(user_id).get()
        if not doc.exists:
            return NOT_VERIFIED
        data = doc.to_dict() or {}
        entitlements = data.get("entitlements", {}) or {}
        now = datetime.now(timezone.utc)
        for _ent_id, ent in entitlements.items():
            if not isinstance(ent, dict):
                continue

            # Lifetime / non-expiring entitlement
            expires = ent.get("expires_date")
            if expires is None:
                return VERIFIED

            expires_dt = _parse_iso_timestamp(expires)
            if expires_dt is not None and expires_dt > now:
                return VERIFIED

            # Grace period keeps the user premium past the paid-through date
            # (typical billing retry window). Without this check a billing
            # issue would flip the user to free for 24-48h.
            grace = ent.get("grace_period_expires_date")
            if grace:
                grace_dt = _parse_iso_timestamp(grace)
                if grace_dt is not None and grace_dt > now:
                    return VERIFIED
        return NOT_VERIFIED
    except Exception:
        return UNAVAILABLE


def verify_premium(user_id: str) -> str:
    """
    Premium verification with defence-in-depth:
    1. Read from Firestore (rc_customers/{user_id}) — fast, no external call,
       race-condition-free because the Extension writes before webhook ACK.
    2. Fall back to RevenueCat REST when Firestore has no active record. This
       protects against (a) extension not yet installed, (b) transient sync
       lag, (c) historical users created before the extension went live.

    Callsites: restore_credits_after_purchase, refill_premium_credits.
    """
    firestore_result = verify_premium_from_firestore(user_id)
    if firestore_result == VERIFIED:
        return VERIFIED

    rest_result = verify_premium_with_revenuecat(user_id)
    if rest_result == VERIFIED:
        return VERIFIED
    # Prefer a definitive NOT_VERIFIED over REST UNAVAILABLE so clients see 403
    # (actionable) rather than 503 (retry storm) when both paths disagree.
    if rest_result == UNAVAILABLE and firestore_result == NOT_VERIFIED:
        return NOT_VERIFIED
    return rest_result


# ============================================================================
# AI Credits management (lifetime credits for AI usage)
# ============================================================================

def get_credits_config() -> dict:
    """
    Get AI credits configuration from remote config.
    This allows changing values without redeploying.

    To change remotely, update the Firestore document:
    remote_config/current with fields:
    - initial_ai_credits: int (credits for new users)
    - ai_action_cost: int (cost per AI action)
    - premium_daily_credits_cap: int (max credits for premium daily refill)
    """
    return {
        "initial_credits": get_remote_config_value("initial_ai_credits", DEFAULT_INITIAL_CREDITS),
        "action_cost": get_remote_config_value("ai_action_cost", DEFAULT_AI_ACTION_COST),
        "premium_daily_credits_cap": get_remote_config_value("premium_daily_credits_cap", DEFAULT_PREMIUM_DAILY_CREDITS_CAP)
    }


def get_user_data(user_id: str) -> dict | None:
    """Get user data from Firestore."""
    try:
        doc = db.collection("users").document(user_id).get()
        if doc.exists:
            return doc.to_dict()
    except Exception:
        pass
    return None


def get_user_credits(user_id: str) -> int:
    """Get user's remaining AI credits."""
    user_data = get_user_data(user_id)
    if user_data is None:
        return 0
    return user_data.get("ai_credits", 0)


def get_user_premium_status(user_id: str) -> bool:
    """Get premium status from Firestore (server truth), not from client."""
    user_data = get_user_data(user_id)
    if user_data is None:
        return False
    return user_data.get("is_premium", False)


def reserve_credits_with_action(
    user_id: str, cost: int, request_id: str | None = None
) -> tuple[str, int | None]:
    """Atomically check-and-deduct [cost] credits in a single Firestore transaction.

    Returns (action, value) — the raw verdict from reservation_decision:
        ("reserve", new_balance)  -> THIS call deducted the cost.
        ("replay", recorded)      -> same (user_id, request_id) already paid; nothing deducted.
        ("insufficient", None) / ("no_user", None) -> caller returns 402.

    Callers that may refund MUST branch on the action, not just the balance: only a
    "reserve" may be refunded. Refunding a "replay" would hand back credits deducted by a
    DIFFERENT invocation — and two racing replays would each refund, minting credits.

    When [request_id] is provided (client-generated UUID, stable across HTTP retries of
    the same logical action), the reservation is idempotent: a repeat of the same
    request_id does NOT deduct again — it returns the balance recorded at first reserve.
    This closes the double-charge window where the client retries a transport exception
    AFTER the server already reserved + returned 200 but the client never received it.
    Old clients omit request_id → falls back to the original non-deduped behaviour.

    [cost] is passed in rather than read from Remote Config because the flows differ:
    analyze/generate charge action_cost (~30), chat charges a flat CHAT_AGENT_COST (3).
    """
    user_ref = db.collection("users").document(user_id)
    # Namespace the dedup doc by user so two users' request_ids can never collide. request_id
    # is a UUID from the client (no "/"), safe as a Firestore doc id.
    res_ref = (
        db.collection("credit_reservations").document(f"{user_id}__{request_id}")
        if request_id else None
    )

    @firestore.transactional
    def txn(transaction):
        # All reads MUST precede all writes inside a Firestore transaction.
        snapshot = user_ref.get(transaction=transaction)
        res_snap = res_ref.get(transaction=transaction) if res_ref is not None else None
        current = (snapshot.get("ai_credits") or 0) if snapshot.exists else 0
        prior_remaining = res_snap.get("remaining_after") if (res_snap is not None and res_snap.exists) else None

        action, value = reservation_decision(snapshot.exists, current, cost, prior_remaining)
        if action != "reserve":
            return action, value  # no_user/insufficient -> None; replay -> recorded remaining

        now_iso = datetime.now(timezone.utc).isoformat()
        transaction.update(user_ref, {"ai_credits": value, "updated_at": now_iso})
        if res_ref is not None:
            transaction.set(res_ref, {
                "user_id": user_id,
                "request_id": request_id,
                "cost": cost,
                "remaining_after": value,
                "reserved_at": now_iso,
            })
        return action, value

    return txn(db.transaction())


def reserve_credits(user_id: str, request_id: str | None = None) -> int | None:
    """Reserve the Remote-Config action_cost (analyze / generate flows).

    Thin wrapper over reserve_credits_with_action that keeps the original
    balance-or-None contract for callers that never need the fresh-vs-replay distinction.
    """
    cost = get_credits_config()["action_cost"]
    _action, value = reserve_credits_with_action(user_id, cost, request_id)
    return value


def refund_credits(user_id: str, amount: int, reason: str, request_id: str | None = None) -> bool:
    """Refund `amount` credits previously reserved (inverse of reserve_credits).

    Called when a downstream step fails AFTER credits were already reserved
    (rejected input caught before reserve does NOT reach here). Increments the
    balance in a single Firestore transaction and logs to credits_refund_log
    for audit.

    When [request_id] is given, the matching credit_reservations doc is DELETED on a
    successful refund so reserve/refund stay symmetric on the idempotency key: a
    subsequent retry (e.g. retryOnServerErrors after a Gemini 5xx that we just refunded)
    re-reserves cleanly instead of replaying the now-rolled-back reservation. If the
    refund did NOT apply, the reservation is kept so a retry replays (user stays charged
    exactly once and still gets a result) rather than being charged a second time.

    Best-effort: any failure here is swallowed so the ORIGINAL error (the reason
    we are refunding in the first place) is what surfaces to the client.
    Returns True on success, False if the user doc is missing or the txn fails.
    """
    user_ref = db.collection("users").document(user_id)

    @firestore.transactional
    def txn(transaction):
        snapshot = user_ref.get(transaction=transaction)
        if not snapshot.exists:
            return False
        current = snapshot.get("ai_credits") or 0
        transaction.update(user_ref, {
            "ai_credits": current + amount,
            "updated_at": datetime.now(timezone.utc).isoformat(),
        })
        return True

    try:
        ok = txn(db.transaction())
        if ok:
            # Roll back the idempotency marker so a retry re-reserves (single charge) rather
            # than replaying a reservation whose credits we just returned (would be a free action).
            if request_id:
                try:
                    db.collection("credit_reservations").document(f"{user_id}__{request_id}").delete()
                except Exception:
                    logger.exception(
                        "refund_credits: reservation-doc delete failed user=%s request_id=%s",
                        user_id[:8], request_id,
                    )
            try:
                db.collection("credits_refund_log").add({
                    "user_id": user_id,
                    "reason": reason,
                    "amount": amount,
                    "refunded_at": datetime.now(timezone.utc).isoformat(),
                })
            except Exception:
                # Refund itself applied; only the audit-log write failed. Non-fatal, but log it
                # so the audit trail's gaps are visible instead of silent.
                logger.exception(
                    "refund_credits: audit-log write failed user=%s reason=%s amount=%s",
                    user_id[:8], reason, amount,
                )
        else:
            # User doc missing -> refund could NOT be applied: the user stays wrongly charged.
            logger.error(
                "refund_credits: user doc missing, refund NOT applied user=%s reason=%s amount=%s",
                user_id[:8], reason, amount,
            )
        return ok
    except Exception:
        # Firestore txn failed -> the one path where a user is wrongly charged with zero
        # telemetry. Surface it (was a silent `return False`).
        logger.exception(
            "refund_credits: txn failed, refund NOT applied user=%s reason=%s amount=%s",
            user_id[:8], reason, amount,
        )
        return False


# ============================================================================
# Shared AI helpers
# ============================================================================

def call_gemini(prompt: str, input_type: str, input_data: str, audio_mime_type: str = "audio/mp4", model_id: str = None):
    """Call Gemini API with appropriate content type.

    [audio_mime_type] is honored only when input_type == "audio_base64".
    Allowed by Gemini: audio/mp4, audio/mpeg, audio/wav, audio/webm, audio/flac, audio/ogg.
    Callers must normalize browser variants (e.g. "audio/m4a", "audio/webm;codecs=opus")
    before invoking this function.

    [model_id] defaults to gemini-2.5-flash-lite. Callers may pass a test-override value
    already resolved via [resolve_model]; never pass an unvalidated client value here.
    """
    model_id = model_id or "gemini-2.5-flash-lite"
    if input_type == "image_base64" and input_data:
        return gemini_client.models.generate_content(
            model=model_id,
            contents=[
                prompt,
                types.Part.from_bytes(data=base64.b64decode(input_data), mime_type="image/jpeg"),
            ],
        )
    if input_type == "audio_base64" and input_data:
        return gemini_client.models.generate_content(
            model=model_id,
            contents=[
                prompt,
                types.Part.from_bytes(data=base64.b64decode(input_data), mime_type=audio_mime_type),
            ],
        )
    return gemini_client.models.generate_content(model=model_id, contents=prompt)


# ----------------------------------------------------------------------------
# Audio MIME normalization for Gemini Files API.
#
# Browsers vary in what MediaRecorder produces:
#   - Chrome / Firefox / Edge → "audio/webm;codecs=opus"
#   - Safari → "audio/mp4" (with codec params)
#   - Android MediaRecorder (AAC/m4a) → "audio/m4a"
# Gemini whitelist: audio/mp4, audio/mpeg, audio/wav, audio/webm, audio/flac, audio/ogg.
# This function strips codec parameters and maps common aliases.
# ----------------------------------------------------------------------------

_GEMINI_AUDIO_MIME_WHITELIST = {
    "audio/mp4", "audio/mpeg", "audio/wav", "audio/webm", "audio/flac", "audio/ogg",
}


def normalize_audio_mime(client_mime: str) -> str:
    """Normalize a client-supplied audio MIME type for Gemini.

    Returns a whitelisted MIME or falls back to "audio/mp4" if unrecognised
    (Gemini's most permissive container — covers AAC/ALAC/etc).
    """
    if not client_mime:
        return "audio/mp4"
    # Strip codec parameters: "audio/webm;codecs=opus" → "audio/webm"
    base = client_mime.split(";", 1)[0].strip().lower()
    # Alias: m4a is an AAC-in-MP4 audio-only container
    if base == "audio/m4a":
        base = "audio/mp4"
    # Alias: x-m4a (Safari)
    if base == "audio/x-m4a":
        base = "audio/mp4"
    # Alias: mp3 → mpeg
    if base == "audio/mp3":
        base = "audio/mpeg"
    if base in _GEMINI_AUDIO_MIME_WHITELIST:
        return base
    # Unknown — let Gemini try as mp4. Logged via response failure if it rejects.
    return "audio/mp4"


def parse_gemini_json(response_text: str) -> dict:
    """Extract and parse JSON from Gemini response."""
    if "```json" in response_text:
        response_text = response_text.split("```json")[1].split("```")[0]
    elif "```" in response_text:
        response_text = response_text.split("```")[1].split("```")[0]
    return json.loads(response_text.strip())


# ============================================================================
# FUNCTION 1: Register or retrieve user by device ID
# ============================================================================

@functions_framework.http
def register_user(request: Request):
    """
    Register a new user or retrieve existing user by device ID.

    This prevents abuse by reinstalling the app - same device always gets same user_id.

    Request body:
    {
        "device_id": "string (unique device identifier)"
    }

    Response:
    {
        "success": true,
        "user_id": "string (UUID)",
        "is_new_user": boolean,
        "is_premium": boolean,
        "ai_credits": number,
        "created_at": "ISO datetime string"
    }
    """
    # CORS preflight — browsers send OPTIONS before cross-origin POST
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    if request.method != "POST":
        return add_cors_headers(make_response(
            jsonify({"success": False, "error": "Only POST method is allowed"}), 405
        ))

    try:
        data = request.get_json()
    except Exception:
        return add_cors_headers(make_response(
            jsonify({"success": False, "error": "Invalid JSON body"}), 400
        ))

    if not data:
        return add_cors_headers(make_response(
            jsonify({"success": False, "error": "Request body is required"}), 400
        ))

    device_id = data.get("device_id")
    if not device_id or not isinstance(device_id, str) or len(device_id) < 10:
        return add_cors_headers(make_response(
            jsonify({"success": False, "error": "Valid device_id is required (min 10 characters)"}), 400
        ))

    # Normalize device_id (trim, lowercase for consistency)
    device_id = device_id.strip().lower()

    try:
        # Get credits config (allows remote configuration)
        config = get_credits_config()
        platform = data.get("platform", "")

        # Web users start with 0 credits — they unlock the starter pack
        # (same 100 credits as mobile) by signing in with Google.
        # Mobile users get credits immediately at install (implicit trust
        # via Play Store account).
        if platform == "web":
            initial_credits = 0
        else:
            initial_credits = config["initial_credits"]

        # Check if user with this device_id already exists
        users_ref = db.collection("users")
        existing_users = users_ref.where(filter=FieldFilter("device_id", "==", device_id)).limit(1).get()

        for user_doc in existing_users:
            # User exists - return existing data
            user_data = user_doc.to_dict()
            return add_cors_headers(make_response(jsonify({"success": True,
                "user_id": user_doc.id,
                "is_new_user": False,
                "is_premium": user_data.get("is_premium", False),
                "ai_credits": user_data.get("ai_credits", 0),
                "created_at": user_data.get("created_at", "")
            })))

        # User doesn't exist - create new user
        new_user_id = str(uuid.uuid4())
        now = datetime.utcnow().isoformat()

        user_data = {
            "device_id": device_id,
            "is_premium": False,
            "ai_credits": initial_credits,
            "created_at": now,
            "updated_at": now,
            "app_version": data.get("app_version", ""),
            "platform": data.get("platform", ""),
        }

        # Save to Firestore with user_id as document ID
        users_ref.document(new_user_id).set(user_data)

        return add_cors_headers(make_response(jsonify({"success": True,
            "user_id": new_user_id,
            "is_new_user": True,
            "is_premium": False,
            "ai_credits": initial_credits,
            "created_at": now
        })))

    except Exception as e:
        # Same defect the 2026-07-28 pass closed in the other eight 500-branches; this handler was
        # not in that deploy set and kept both halves of it. Without the traceback the 500 leaves
        # only a bare request log and the root cause is unrecoverable after the fact — and this is
        # the second-busiest endpoint (~225 calls/week), so it was the costliest place to still miss.
        logger.exception("register_user: failed (%s)", type(e).__name__)
        # Do NOT put str(e) in the response body — it ships Firestore internals to the client.
        return create_error_response("Failed to register user", 500)


# ============================================================================
# FUNCTION 1b: Link Google account to existing device-based user
# ============================================================================

@functions_framework.http
def link_google_account(request: Request):
    """
    Link a Google account to an existing device-based user.
    Grants starter credits on web (same pack as Android install).

    Request:
      Headers: Authorization: Bearer <firebase_id_token>
      Body: { "user_id": "existing device-based user_id", "platform": "web"|"android" }

    Response:
      { "success": true, "user_id": "...", "google_email": "...",
        "is_existing_account": false, "ai_credits": 100,
        "is_premium": false, "bonus_credits_granted": 100 }
    """
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    if request.method != "POST":
        return create_error_response("Only POST method is allowed", 405)

    decoded_token, error = verify_firebase_token(request)
    if error:
        return create_error_response(error[0], error[1])
    if not decoded_token:
        return create_error_response("Firebase ID token required in Authorization header", 401)

    try:
        data = request.get_json() or {}
    except Exception:
        return create_error_response("Invalid JSON body", 400)

    user_id = data.get("user_id")
    if not user_id:
        return create_error_response("user_id is required", 400)

    platform = data.get("platform", "")
    firebase_uid = decoded_token["uid"]
    google_email = decoded_token.get("email", "")
    google_name = decoded_token.get("name", "")
    google_photo = decoded_token.get("picture", "")

    try:
        # Check if this Google account is already linked to another user
        existing = (db.collection("users")
                    .where(filter=FieldFilter("google_uid", "==", firebase_uid))
                    .limit(1).get())
        for doc in existing:
            if doc.id != user_id:
                existing_data = doc.to_dict()
                return create_success_response({
                    "user_id": doc.id,
                    "google_email": google_email,
                    "is_existing_account": True,
                    "ai_credits": existing_data.get("ai_credits", 0),
                    "is_premium": existing_data.get("is_premium", False),
                    "bonus_credits_granted": 0,
                })

        user_ref = db.document(f"users/{user_id}")
        user_doc = user_ref.get()

        if not user_doc.exists:
            return create_error_response("User not found", 404)

        user_data = user_doc.to_dict()

        # Grant starter pack credits on web (one-time, same as Android install)
        config = get_credits_config()
        bonus = 0
        already_granted = user_data.get("google_bonus_credits_granted", False)
        if platform == "web" and not already_granted:
            bonus = config["initial_credits"]

        update_data = {
            "google_uid": firebase_uid,
            "google_email": google_email,
            "google_display_name": google_name,
            "google_photo_url": google_photo,
            "google_linked_at": firestore.SERVER_TIMESTAMP,
            "updated_at": datetime.utcnow().isoformat(),
        }
        if bonus > 0:
            update_data["ai_credits"] = user_data.get("ai_credits", 0) + bonus
            update_data["google_bonus_credits_granted"] = True

        user_ref.update(update_data)

        return create_success_response({
            "user_id": user_id,
            "google_email": google_email,
            "is_existing_account": False,
            "ai_credits": user_data.get("ai_credits", 0) + bonus,
            "is_premium": user_data.get("is_premium", False),
            "bonus_credits_granted": bonus,
        })

    except Exception as e:
        # Without this the 500 is unrecoverable after the fact: Cloud Logging keeps the request
        # log (a bare "500") but no traceback, so the root cause is gone. Precedent 2026-07-27:
        # a 500 here (5.04s latency) left zero app-log lines and could not be diagnosed at all.
        logger.exception("link_google_account: failed (%s)", type(e).__name__)
        # Do NOT put str(e) in the response body — it ships Firestore/genai internals to the client.
        return create_error_response("Failed to link Google account", 500)


# ============================================================================
# FUNCTION 1c: Register an FCM push token onto an existing credit-doc
# ============================================================================

def parse_push_token_registration(data) -> tuple[dict | None, str | None]:
    """Validate & normalize a register_push_token body. Pure — no Firestore, unit-testable.

    Returns (fields, None) on success or (None, error_message) on invalid input.
    `fields` = {user_id, fcm_token, platform, push_holdout, fcm_opt_in}. The two
    boolean flags default to False when the client omits them.
    """
    if not isinstance(data, dict):
        return None, "Request body is required"

    user_id = data.get("user_id")
    if not user_id or not isinstance(user_id, str) or len(user_id) < 10:
        return None, "Valid user_id is required (min 10 characters)"

    fcm_token = data.get("fcm_token")
    if not fcm_token or not isinstance(fcm_token, str) or not fcm_token.strip():
        return None, "fcm_token is required (non-empty string)"

    platform = data.get("platform")
    if platform not in ("android", "web"):
        return None, "platform must be one of: android, web"

    push_holdout = data.get("push_holdout", False)
    fcm_opt_in = data.get("fcm_opt_in", False)
    if not isinstance(push_holdout, bool):
        return None, "push_holdout must be a boolean"
    if not isinstance(fcm_opt_in, bool):
        return None, "fcm_opt_in must be a boolean"

    return {
        # user_id is a document id — strip whitespace but do NOT lowercase (it must
        # match the exact server-issued UUID that register_user created the doc under).
        "user_id": user_id.strip(),
        "fcm_token": fcm_token.strip(),
        "platform": platform,
        "push_holdout": push_holdout,
        "fcm_opt_in": fcm_opt_in,
    }, None


@functions_framework.http
def register_push_token(request: Request):
    """
    Merge-write a device's FCM push token into its existing credit-doc
    users/{user_id}.

    This is how ANONYMOUS (not-signed-in) users get a push token onto the
    server: they only ever touch their user doc through this Cloud Functions
    layer (the client has no direct Firestore write path into the users/
    collection), so the token has to be delivered here to widen push reach.
    The field names written here (fcmToken, pushHoldout) are exactly what
    send_promotions_batch / push_promotions.is_eligible_for_promo read back.

    Request body:
    {
        "user_id": "string (server-issued UUID, >=10 chars)",
        "fcm_token": "string (non-empty FCM registration token)",
        "platform": "android" | "web",
        "push_holdout": boolean (optional, default false),
        "fcm_opt_in": boolean (optional, default false)
    }

    Response 200: { "success": true }
    Response 400: invalid body (missing/short user_id, blank token, bad platform)

    Idempotent: this is a merge-write, so repeated calls just refresh the token
    and lastActiveAt — never a duplicate doc, and never clobbering the credit
    fields (is_premium / ai_credits / google_uid / device_id are untouched).

    Doc-not-exists behaviour: set(merge=True) CREATES the doc if it is absent,
    holding only these 5 push fields. In the normal flow register_user has
    already created the credit-doc before the client ever obtains a user_id, so
    the doc exists and this is a pure field-merge. A merge-create only happens
    for a stale/unknown user_id (harmless — such a partial doc carries no credits
    and never affects billing); see the security note below for the abuse angle.

    SECURITY: deployed --allow-unauthenticated with the SAME trust model as
    register_user — the caller supplies its own user_id (just like device_id
    there), with no Firebase ID token by design (anonymous users have no Auth
    identity, which is the very reason this endpoint exists). Risk: a hostile
    client could name someone else's user_id and attach its own token, so the
    victim's promo pushes would go to the attacker's device (or overwrite the
    victim's real token, silently dropping their pushes); a spray of fake
    user_ids could also mint junk partial docs. Firebase App Check — enforced at
    the platform level for this project, exactly as for register_user (there is
    no in-code App Check anywhere in this module; it is a console/gateway
    concern) — is what makes that expensive: only genuine app builds obtain a
    valid App Check token, blocking scripted user_id spoofing.
    """
    # CORS preflight — browsers send OPTIONS before cross-origin POST
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    if request.method != "POST":
        return create_error_response("Only POST method is allowed", 405)

    try:
        data = request.get_json()
    except Exception:
        return create_error_response("Invalid JSON body", 400)

    fields, error = parse_push_token_registration(data)
    if error:
        return create_error_response(error, 400)

    try:
        db.collection("users").document(fields["user_id"]).set(
            {
                "fcmToken": fields["fcm_token"],
                "platform": fields["platform"],
                "lastActiveAt": firestore.SERVER_TIMESTAMP,
                "pushHoldout": fields["push_holdout"],
                "fcmOptIn": fields["fcm_opt_in"],
            },
            merge=True,  # never clobber is_premium / ai_credits / google_uid / device_id
        )
        return create_success_response({})
    except Exception as e:
        logger.exception("register_push_token: failed (%s)", type(e).__name__)
        return create_error_response("Failed to register push token", 500)


# ============================================================================
# FUNCTION 2: Auto-fill existing checklist
# ============================================================================




@functions_framework.http
def analyze_and_fill_checklist(request: Request):
    """
    Auto-fill an existing checklist based on user-provided data.

    Request body:
    {
        "user_id": "string",
        "is_premium": boolean (optional),
        "checklist": {
            "id": number,
            "name": "string",
            "items": [{"text": "string", "checked": boolean}]
        },
        "input_type": "text" | "url" | "image_base64" | "audio_base64",
        "input_data": "string (text content, URL, base64 image, or base64 audio)"
    }

    Response:
    {
        "success": true,
        "filled_items": [...],
        "summary": "string",
        "confidence": 0.0-1.0,
        "usage": {"count": number, "limit": number}
    }
    """
    # CORS preflight — browsers send OPTIONS before cross-origin POST
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    # Validate request
    data, error = validate_request(request)
    if error:
        return create_error_response(error)

    user_id = data["user_id"]
    is_premium = get_user_premium_status(user_id)
    checklist = data.get("checklist")
    input_type = data.get("input_type")
    input_data = data.get("input_data")
    # Idempotency key: client sends a stable UUID per logical AI action (reused across HTTP
    # retries). Absent on old clients -> None -> non-deduped fallback. See reserve_credits.
    request_id = (data.get("request_id") or "").strip() or None

    # Validate required fields
    if not checklist or not isinstance(checklist.get("items"), list):
        return create_error_response("checklist with items is required")
    if not input_type or not input_data:
        return create_error_response("input_type and input_data are required")

    # Check feature flag
    if not get_remote_config_value("feature_ai_analysis_enabled", True):
        return create_error_response("AI analysis is currently disabled", 503)

    # Check daily usage limit (before spending credits)
    usage_allowed, usage_error = check_usage_limit(user_id, is_premium)
    if not usage_allowed:
        return create_error_response(usage_error, 429)

    # Check input length (skip for binary data types) — BEFORE reserving credits,
    # so a rejected (too-long) input never deducts credits.
    if input_type not in ("image_base64", "audio_base64"):
        max_length = get_remote_config_value("ai_analysis_max_input_length", DEFAULT_MAX_INPUT_LENGTH)
        if len(input_data) > max_length:
            return create_error_response(f"Input data exceeds maximum length of {max_length} characters")

    # Reserve credits atomically (deduct before Gemini call)
    cost = get_credits_config()["action_cost"]
    remaining = reserve_credits(user_id, request_id)
    if remaining is None:
        suffix = "Refill at 12:00 CET." if is_premium else "Get premium for daily refill."
        return create_error_response(f"Not enough credits. Need {cost}. {suffix}", 402)

    # Build prompt
    checklist_items = "\n".join([
        f"{i+1}. {'[x]' if item.get('checked') else '[ ]'} {item['text']}"
        for i, item in enumerate(checklist["items"])
    ])

    # Prepare user data text for prompt
    if input_type == "image_base64":
        user_data_for_prompt = "[Image data provided]"
    elif input_type == "audio_base64":
        user_data_for_prompt = "[Audio data provided - transcribe and analyze the voice recording]"
    else:
        user_data_for_prompt = input_data

    prompt = FILL_CHECKLIST_PROMPT.format(
        checklist_items=checklist_items,
        user_data=user_data_for_prompt
    )

    # Model resolution: production A/B experiment (server-driven) + eval override precedence.
    model_id, model_arm = resolve_experiment_model(user_id, "analyze", "gemini-2.5-flash-lite", data)
    exp_meta = {"model_variant": model_arm, "model_id": model_id, "ai_flow": "analyze"}

    try:
        response = call_gemini(prompt, input_type, input_data, model_id=model_id)
        result = parse_gemini_json(response.text)
    except json.JSONDecodeError as e:
        logger.exception("analyze_and_fill_checklist: parse failed for user=%s", user_id[:8])
        refund_credits(user_id, cost, "gemini_parse_error", request_id)
        return create_error_response("Failed to parse AI response", 500)
    except Exception as e:
        logger.exception("analyze_and_fill_checklist: gemini call failed for user=%s", user_id[:8])
        refund_credits(user_id, cost, "gemini_error", request_id)
        return create_error_response("AI processing failed. Please try again.", 500)

    # Increment usage stats
    increment_usage(user_id, "analyze_and_fill_checklist", input_type)

    return create_success_response({
        **exp_meta,
        "filled_items": result.get("filled_items", []),
        "summary": result.get("summary", ""),
        "confidence": result.get("confidence", 0.8),
        "ai_credits": remaining
    })


# ============================================================================
# FUNCTION 3: Generate checklist from prompt + data
# ============================================================================




@functions_framework.http
def generate_checklist(request: Request):
    """
    Generate a new checklist from user prompt and optional data.

    Request body:
    {
        "user_id": "string",
        "is_premium": boolean (optional),
        "prompt": "string (user's description of what checklist they need)",
        "input_type": "text" | "url" | "image_base64" | "audio_base64" | "none" (optional),
        "input_data": "string (additional context data, base64 image, or base64 audio)" (optional)
    }

    Response:
    {
        "success": true,
        "checklist_name": "string",
        "items": [{"text": "string", "checked": false}],
        "summary": "string",
        "confidence": 0.0-1.0,
        "usage": {"count": number, "limit": number}
    }
    """
    # CORS preflight — browsers send OPTIONS before cross-origin POST
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    # Validate request
    data, error = validate_request(request)
    if error:
        return create_error_response(error)

    user_id = data["user_id"]
    is_premium = get_user_premium_status(user_id)
    user_prompt = data.get("prompt")
    input_type = data.get("input_type", "none")
    input_data = data.get("input_data", "")
    locale = (data.get("locale") or "en").strip().lower()
    output_language = "Russian" if locale == "ru" else "English"
    # Idempotency key: client sends a stable UUID per logical AI action (reused across HTTP
    # retries). Absent on old clients -> None -> non-deduped fallback. See reserve_credits.
    request_id = (data.get("request_id") or "").strip() or None

    # Validate required fields
    if not user_prompt:
        return create_error_response("prompt is required")

    # Check feature flag
    if not get_remote_config_value("feature_ai_analysis_enabled", True):
        return create_error_response("AI analysis is currently disabled", 503)

    # Check daily usage limit (before spending credits)
    usage_allowed, usage_error = check_usage_limit(user_id, is_premium)
    if not usage_allowed:
        return create_error_response(usage_error, 429)

    # Check input length (skip binary data in length calculation) — BEFORE reserving
    # credits, so a rejected (too-long) input never deducts credits.
    max_length = get_remote_config_value("ai_analysis_max_input_length", DEFAULT_MAX_INPUT_LENGTH)
    if input_type in ("image_base64", "audio_base64"):
        total_input = user_prompt  # Only check prompt length for binary inputs
    else:
        total_input = user_prompt + (input_data or "")
    if len(total_input) > max_length:
        return create_error_response(f"Input exceeds maximum length of {max_length} characters")

    # Reserve credits atomically (deduct before Gemini call)
    cost = get_credits_config()["action_cost"]
    remaining = reserve_credits(user_id, request_id)
    if remaining is None:
        suffix = "Refill at 12:00 CET." if is_premium else "Get premium for daily refill."
        return create_error_response(f"Not enough credits. Need {cost}. {suffix}", 402)

    # Build prompt
    if input_type == "image_base64":
        user_data_text = "[Image data provided - analyze the image for context]"
    elif input_type == "audio_base64":
        user_data_text = "[Audio data provided - transcribe and analyze the voice recording for context]"
    elif input_data and input_type not in ("image_base64", "audio_base64"):
        user_data_text = input_data
    else:
        user_data_text = "No additional data provided"

    prompt = GENERATE_CHECKLIST_PROMPT.format(
        user_prompt=user_prompt,
        user_data=user_data_text,
        output_language=output_language,
        max_folder_depth=MAX_FOLDER_DEPTH
    )

    # Model resolution: production A/B experiment (server-driven) + eval override precedence.
    model_id, model_arm = resolve_experiment_model(user_id, "generate", "gemini-2.5-flash-lite", data)
    exp_meta = {"model_variant": model_arm, "model_id": model_id, "ai_flow": "generate"}

    try:
        response = call_gemini(prompt, input_type, input_data, model_id=model_id)
        result = parse_gemini_json(response.text)
    except json.JSONDecodeError as e:
        logger.exception("generate_checklist: parse failed for user=%s", user_id[:8])
        refund_credits(user_id, cost, "gemini_parse_error", request_id)
        return create_error_response("Failed to parse AI response", 500)
    except Exception as e:
        logger.exception("generate_checklist: gemini call failed for user=%s", user_id[:8])
        refund_credits(user_id, cost, "gemini_error", request_id)
        return create_error_response("AI processing failed. Please try again.", 500)

    # Increment usage stats
    increment_usage(user_id, "generate_checklist", input_type)

    return create_success_response({
        **exp_meta,
        "checklist_name": result.get("checklist_name", "New Checklist"),
        "items": sanitize_generated_items(result.get("items", [])),
        "summary": result.get("summary", ""),
        "confidence": result.get("confidence", 0.8),
        "ai_credits": remaining
    })


# ============================================================================
# FUNCTION 4: Get user usage stats
# ============================================================================

@functions_framework.http
def get_usage_stats(request: Request):
    """
    Get user's AI usage statistics.

    Request body:
    {
        "user_id": "string",
        "is_premium": boolean (optional)
    }

    Response:
    {
        "success": true,
        "usage": {
            "today": number,
            "limit": number,
            "remaining": number,
            "requests": [...]
        }
    }
    """
    # CORS preflight — browsers send OPTIONS before cross-origin POST
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    data, error = validate_request(request)
    if error:
        return create_error_response(error)

    user_id = data["user_id"]
    is_premium = data.get("is_premium", False)

    usage = get_user_usage(user_id)
    limit = DEFAULT_DAILY_LIMIT_PREMIUM if is_premium else DEFAULT_DAILY_LIMIT_FREE

    return create_success_response({
        "usage": {
            "today": usage["count"],
            "limit": limit,
            "remaining": max(0, limit - usage["count"]),
            "requests": usage.get("requests", [])[-10:]  # Last 10 requests
        }
    })


# ============================================================================
# FUNCTION 5: Daily credits refill for premium users (scheduled)
# ============================================================================

@functions_framework.http
def refill_premium_credits(request: Request):
    """
    Refill credits for all premium users.

    This function should be called daily at 12:00 CET by Cloud Scheduler.

    Logic:
    - Find all users with is_premium = True
    - Verify each user's subscription via RevenueCat API
    - If subscription expired: set is_premium = False, skip refill
    - If RevenueCat unavailable: refill anyway (benefit of the doubt)
    - If subscription active and credits < cap: refill to cap
    - If credits >= cap: don't change (don't accumulate beyond cap)

    Request body (optional, for manual trigger):
    {
        "admin_key": "string (optional admin key for manual trigger)"
    }

    Response:
    {
        "success": true,
        "users_updated": number,
        "users_skipped": number,
        "users_expired": number,
        "credits_cap": number
    }
    """
    # This can be called by Cloud Scheduler (no body) or manually with admin key
    # For Cloud Scheduler invocation via HTTP, we allow it

    try:
        config = get_credits_config()
        credits_cap = config["premium_daily_credits_cap"]

        # Query all premium users
        users_ref = db.collection("users")
        premium_users = users_ref.where(filter=FieldFilter("is_premium", "==", True)).get()

        users_updated = 0
        users_skipped = 0
        users_expired = 0

        for user_doc in premium_users:
            # Verify subscription via Firestore (RC Extension) with REST fallback
            status = verify_premium(user_doc.id)

            if status == NOT_VERIFIED:
                # Subscription expired or cancelled — revoke premium
                user_doc.reference.update({
                    "is_premium": False,
                    "premium_expired_at": datetime.now(timezone.utc).isoformat(),
                    "updated_at": datetime.now(timezone.utc).isoformat()
                })
                users_expired += 1
                continue

            # UNAVAILABLE — give benefit of the doubt, refill anyway
            # VERIFIED — subscription confirmed, refill

            user_data = user_doc.to_dict()
            current_credits = user_data.get("ai_credits", 0)

            if current_credits < credits_cap:
                # Refill to cap
                user_doc.reference.update({
                    "ai_credits": credits_cap,
                    "credits_refilled_at": datetime.now(timezone.utc).isoformat(),
                    "updated_at": datetime.now(timezone.utc).isoformat()
                })
                users_updated += 1
            else:
                # Already at or above cap, skip
                users_skipped += 1

        # Log the refill operation
        db.collection("credits_refill_log").add({
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "users_updated": users_updated,
            "users_skipped": users_skipped,
            "users_expired": users_expired,
            "credits_cap": credits_cap
        })

        return create_success_response({
            "users_updated": users_updated,
            "users_skipped": users_skipped,
            "users_expired": users_expired,
            "credits_cap": credits_cap
        })

    except Exception as e:
        logger.exception("refill_premium_credits: failed (%s)", type(e).__name__)
        return create_error_response("Failed to refill credits", 500)


# ============================================================================
# PROMOTIONAL PUSH — re-engagement / win-back broadcast (send_promotions_batch)
# ============================================================================
# Server-side push for dormant users (report §5 audience segmentation, §6 measurement).
# Cloud Scheduler → HTTP, same pattern as refill_premium_credits. PROMOTIONAL layer only:
# premium + holdout users are unconditionally excluded. Consent = opt-OUT via the "Tips &
# Offers" notification channel (the OS drops the push if the user disabled it), so the server
# targets all token-holders — no server-side opt-in flag.


def assign_push_arm(user_id: str) -> str:
    """Copy A/B arm ("control" | "a" | "b") for [user_id] via the RC SERVER template.

    Mirrors assign_model_arm EXACTLY (same cached server template, same percent-condition
    mechanism, same fail-safe): the RC param `push_ab_arm` is evaluated against
    randomization_id = user_id. Deterministic per user, identical across campaigns, and any
    RC failure (load / evaluate / unknown arm) falls back to "control". The experiment
    (percent split + arm mapping) is managed entirely from the Firebase RC console.
    """
    template = _get_rc_server_template()
    if template is None:
        return "control"
    try:
        config = template.evaluate({"randomization_id": user_id})
        arm = config.get_string("push_ab_arm") or "control"
    except Exception as e:  # noqa: BLE001 — RC must never break a send
        print(f"[push_ab] RC evaluate failed: {type(e).__name__}: {e}")
        return "control"
    return arm if arm in push_promotions.PUSH_ARM_ALLOWLIST else "control"


def _get_push_copy_variants_json() -> str:
    """RC `push_copy_variants_json` (console-editable copy table). '' → in-code fallback.

    Not personalised — the same JSON for everyone — so the randomization_id is a constant.
    """
    template = _get_rc_server_template()
    if template is None:
        return ""
    try:
        config = template.evaluate({"randomization_id": "push_copy"})
        return config.get_string("push_copy_variants_json") or ""
    except Exception as e:  # noqa: BLE001 — RC must never break a send
        print(f"[push_ab] RC copy variants load failed: {type(e).__name__}: {e}")
        return ""


def _emit_amplitude_events(events: list) -> int:
    """POST `push_sent` events to Amplitude HTTP V2 (server-side CTR denominator).

    Non-fatal by design: a missing key or an HTTP error logs a warning and returns 0 — it
    must NEVER abort a real push send. Chunks to 100 events/request (well under the API's
    1000 events/sec cap). Returns the count accepted for upload.
    """
    if not events:
        return 0
    if not AMPLITUDE_SERVER_API_KEY:
        print(f"[push_ab] AMPLITUDE_SERVER_API_KEY unset — {len(events)} push_sent events "
              "NOT counted (CTR denominator missing). Configure the secret to measure opens.")
        return 0
    uploaded = 0
    for batch in push_promotions.chunked(events, _AMPLITUDE_EVENTS_PER_BATCH):
        try:
            resp = http_requests.post(
                AMPLITUDE_HTTP_ENDPOINT,
                json={"api_key": AMPLITUDE_SERVER_API_KEY, "events": batch},
                headers={"Content-Type": "application/json"},
                timeout=10,
            )
            if resp.status_code == 200:
                uploaded += len(batch)
            else:
                print(f"[push_ab] Amplitude upload non-200: {resp.status_code} {resp.text[:200]}")
        except Exception as e:  # noqa: BLE001 — analytics must never break the send
            print(f"[push_ab] Amplitude upload failed: {type(e).__name__}: {e}")
    return uploaded


@functions_framework.http
def send_promotions_batch(request: Request):
    """Send a batch of PROMOTIONAL re-engagement / win-back pushes to dormant users.

    Cloud Scheduler → HTTP (same pattern as refill_premium_credits). Parameterised by
    push_type + the inactivity window (days). PROMOTIONAL layer ONLY:
      * Premium users are UNCONDITIONALLY suppressed (they're converted — nothing to sell;
        compliance + measurement). This CF never sends the "everyone incl. premium" digest/
        release variant — that would be a separate FUNCTIONAL broadcast.
      * Holdout users (`pushHoldout == true`) are UNCONDITIONALLY skipped (retention control
        group — report §6.1).
      * Consent = opt-OUT via the "Tips & Offers" notification channel — the OS drops the push
        if the user disabled that channel, so all token-holders are targeted (no server-side
        opt-in flag). Chosen 2026-07-03 over an explicit promoOptIn soft-ask.
    Emits `push_sent` to Amplitude per delivered push (CTR denominator), cleans up dead FCM
    tokens, and stamps a per-user frequency cap. The A/B copy arm ("copy" experiment) comes
    from the RC server template (assign_push_arm).

    Request body (all optional):
      { "push_type": "reengagement"|"winback"|"digest"|"tip"|"release"|"upsell"  (default "reengagement"),
        "min_inactive_days": int   (default 3   — dormant AT LEAST this long),
        "max_inactive_days": int|null (default null — no lower bound; set for a window, e.g. win-back 14..30),
        "campaign_id": str         (default "{push_type}_{YYYYMMDD}"),
        "dry_run": bool            (default false — simulate: NO FCM / NO Amplitude / NO writes),
        "fcm_validate_only": bool  (default false — FCM dry_run: validates tokens, no delivery),
        "max_users": int           (default 5000 — cap Firestore reads per run),
        "promo_cooldown_hours": int (default 20 — per-user frequency cap ~1/day),
        "admin_key": str           (REQUIRED — must equal the PUSH_ADMIN_KEY secret, which
                                     MUST be set or the CF refuses to run; fail-closed) }
    """
    try:
        data = request.get_json(silent=True) or {}
    except Exception:  # noqa: BLE001 — tolerate a bodyless scheduler ping
        data = {}

    # Auth gate — FAIL-CLOSED. This CF sends REAL pushes to real users and deploys with
    # --allow-unauthenticated, so a missing/empty key must LOCK the endpoint, never open it.
    # (The old `if PUSH_ADMIN_KEY and ...` form was fail-open: an unset secret skipped the
    # guard entirely, leaving a public push-blast endpoint — notification-bomb / quota abuse.)
    if not PUSH_ADMIN_KEY:
        return create_error_response(
            "Server misconfigured: PUSH_ADMIN_KEY is not set — refusing to send", 503
        )
    if data.get("admin_key") != PUSH_ADMIN_KEY:
        return create_error_response("Unauthorized", 403)

    push_type = str(data.get("push_type") or "reengagement")
    if push_type not in push_promotions.PROMO_PUSH_TYPES:
        return create_error_response(
            f"Invalid push_type '{push_type}'. Allowed: {sorted(push_promotions.PROMO_PUSH_TYPES)}",
            400,
        )

    try:
        min_days = int(data.get("min_inactive_days", 3))
        max_days = data.get("max_inactive_days")
        max_users = int(data.get("max_users", 5000))
        cooldown_hours = int(data.get("promo_cooldown_hours", 20))
    except (TypeError, ValueError):
        return create_error_response("min_inactive_days / max_inactive_days / max_users / "
                                     "promo_cooldown_hours must be integers", 400)

    dry_run = bool(data.get("dry_run", False))
    fcm_validate_only = bool(data.get("fcm_validate_only", False))
    campaign_id = str(data.get("campaign_id")
                      or f"{push_type}_{datetime.now(timezone.utc):%Y%m%d}")

    now = datetime.now(timezone.utc)
    upper = now - timedelta(days=min_days)

    try:
        # --- Audience query: single-field range on lastActiveAt (Firestore-friendly). ---
        # We deliberately keep premium / holdout OUT of the query: Firestore cannot mix
        # `fcmToken != null` with a range on lastActiveAt, and an equality beside the range
        # would require a composite index. With a small, low-retention base the range read +
        # client-side filter (is_eligible_for_promo) is cheaper than the index; revisit (add a
        # composite index) if the dormant base grows large. Two bounds on the SAME field
        # (lastActiveAt) are allowed.
        base_query = db.collection("users").where(filter=FieldFilter("lastActiveAt", "<", upper))
        if max_days is not None:
            lower = now - timedelta(days=int(max_days))
            base_query = base_query.where(filter=FieldFilter("lastActiveAt", ">", lower))
        page_size = 500
        base_query = base_query.order_by("lastActiveAt").limit(page_size)

        variants_json = _get_push_copy_variants_json()

        # --- Collect eligible users, page by page. `max_users` bounds docs SCANNED
        # (the real Firestore read cost), not the eligible count — a large sparse dormant
        # base can't blow past the cap. Deepest-dormant first (order_by lastActiveAt asc).
        eligible = []  # list of (uid, token, arm, title, body)
        skip_counts: dict[str, int] = {}
        scanned = 0
        last_doc = None
        while scanned < max_users:
            page_q = base_query.start_after(last_doc) if last_doc is not None else base_query
            docs = list(page_q.get())
            if not docs:
                break
            for doc in docs:
                scanned += 1
                ud = doc.to_dict() or {}
                ok, reason = push_promotions.is_eligible_for_promo(ud, now, cooldown_hours)
                if not ok:
                    skip_counts[reason] = skip_counts.get(reason, 0) + 1
                    continue
                arm = assign_push_arm(doc.id)
                title, body = push_promotions.select_copy(push_type, arm, variants_json)
                eligible.append((doc.id, ud["fcmToken"], arm, title, body))
                if scanned >= max_users:
                    break
            last_doc = docs[-1]
            if len(docs) < page_size:
                break

        # --- Group by arm: payload is identical within an arm → one multicast per chunk. ---
        by_arm: dict[str, list] = {}
        for item in eligible:
            by_arm.setdefault(item[2], []).append(item)

        sent_count = 0
        failed_count = 0
        tokens_cleaned = 0
        amplitude_events: list = []
        arm_breakdown: dict[str, int] = {}

        for arm, group in by_arm.items():
            _, _, _, title, body = group[0]
            data_payload = push_promotions.build_data_payload(
                push_type, campaign_id, arm, title, body
            )
            # Chunk is bounded by the HTTP pool, not by FCM's 500 cap — see
            # _FCM_TOKENS_PER_MULTICAST for why the smaller number is the correct one.
            for chunk in push_promotions.chunked(group, _FCM_TOKENS_PER_MULTICAST):
                tokens = [g[1] for g in chunk]
                arm_breakdown[arm] = arm_breakdown.get(arm, 0) + len(tokens)
                if dry_run:
                    continue  # simulate only — no FCM, no Amplitude, no Firestore writes

                message = messaging.MulticastMessage(
                    data=data_payload,
                    tokens=tokens,
                    android=messaging.AndroidConfig(
                        priority="normal",       # promo is low-intrusion, not time-critical
                        ttl=timedelta(hours=24),  # a stale promo shouldn't arrive days later
                        collapse_key=campaign_id,  # don't stack multiple of the same campaign
                    ),
                )
                # HTTP v1 + Admin SDK (legacy FCM API retired 2024-06). dry_run here =
                # FCM's own validate-only pass (server round-trip, no device delivery).
                response = messaging.send_each_for_multicast(
                    message, dry_run=fcm_validate_only
                )

                batch_writer = db.batch()
                has_writes = False
                now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
                for i, send_resp in enumerate(response.responses):
                    uid, token = chunk[i][0], chunk[i][1]
                    if send_resp.success:
                        sent_count += 1
                        amplitude_events.append(push_promotions.build_amplitude_event(
                            uid, push_type, campaign_id, arm, now_ms, f"{campaign_id}:{uid}"))
                        if not fcm_validate_only:
                            # Stamp the frequency cap so the next run skips this user.
                            batch_writer.update(
                                db.collection("users").document(uid),
                                {"lastPromoSentAt": firestore.SERVER_TIMESTAMP,
                                 "lastPromoCampaignId": campaign_id},
                            )
                            has_writes = True
                    else:
                        failed_count += 1
                        kind = push_promotions.classify_send_error(send_resp.exception)
                        if kind == "unrecoverable":
                            # Dead token — remove it so we stop wasting FCM quota on it.
                            batch_writer.update(
                                db.collection("users").document(uid),
                                {"fcmToken": firestore.DELETE_FIELD,
                                 "fcmTokenInvalidatedAt": firestore.SERVER_TIMESTAMP},
                            )
                            has_writes = True
                            tokens_cleaned += 1
                        else:
                            print(f"[push] transient send error uid={uid}: "
                                  f"{type(send_resp.exception).__name__}: {send_resp.exception}")

                if has_writes:
                    try:
                        batch_writer.commit()
                    except Exception as e:  # noqa: BLE001 — a write race must not abort the run
                        print(f"[push] Firestore batch commit failed: {type(e).__name__}: {e}")

        amplitude_uploaded = 0 if dry_run else _emit_amplitude_events(amplitude_events)

        result = {
            "campaign_id": campaign_id,
            "push_type": push_type,
            "dry_run": dry_run,
            "fcm_validate_only": fcm_validate_only,
            "scanned": scanned,
            "eligible": len(eligible),
            "sent": sent_count,
            "failed": failed_count,
            "tokens_cleaned": tokens_cleaned,
            "amplitude_uploaded": amplitude_uploaded,
            "arm_breakdown": arm_breakdown,
            "skip_reasons": skip_counts,
        }
        if dry_run and eligible:
            s_uid, s_token, s_arm, s_title, s_body = eligible[0]
            result["sample_payload"] = push_promotions.build_data_payload(
                push_type, campaign_id, s_arm, s_title, s_body)

        # Audit log (mirrors credits_refill_log). Skipped on dry_run (nothing happened).
        if not dry_run:
            try:
                db.collection("promo_push_log").add(
                    {**result, "timestamp": datetime.now(timezone.utc).isoformat()})
            except Exception as e:  # noqa: BLE001
                print(f"[push] promo_push_log write failed: {type(e).__name__}: {e}")

        return create_success_response(result)

    except Exception as e:  # noqa: BLE001
        logger.exception("send_promotions_batch: failed (%s)", type(e).__name__)
        return create_error_response("Failed to send promotions", 500)


# ============================================================================
# FUNCTION 6: Restore credits after premium purchase
# ============================================================================

@functions_framework.http
def restore_credits_after_purchase(request: Request):
    """
    Instantly restore credits for a user after premium purchase.

    This function should be called by the client immediately after:
    - Successful premium subscription purchase
    - Successful purchase restore

    Logic:
    - Verify user exists
    - Mark user as premium (if not already)
    - Set credits to premium_daily_credits_cap

    Request body:
    {
        "user_id": "string",
        "revenuecat_customer_id": "string (optional, for verification)"
    }

    Response:
    {
        "success": true,
        "ai_credits": number,
        "is_premium": true,
        "message": "Credits restored"
    }
    """
    # CORS preflight — browsers send OPTIONS before cross-origin POST
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    data, error = validate_request(request)
    if error:
        return add_cors_headers(make_response(
            jsonify({"success": False, "error": error}), 400
        ))

    user_id = data["user_id"]

    try:
        # Get user from Firestore
        user_ref = db.collection("users").document(user_id)
        user_doc = user_ref.get()

        if not user_doc.exists:
            return add_cors_headers(make_response(
                jsonify({"success": False, "error": "User not found"}), 404
            ))

        prev = user_doc.to_dict() or {}
        prev_state = {
            "is_premium": prev.get("is_premium", False),
            "ai_credits": prev.get("ai_credits", 0),
        }

        # Verify via Firestore (RC Extension) with REST fallback
        status = verify_premium(user_id)
        if status == UNAVAILABLE:
            return add_cors_headers(make_response(
                jsonify({"success": False, "error": "Verification service temporarily unavailable. Please try again."}), 503
            ))
        if status == NOT_VERIFIED:
            return add_cors_headers(make_response(
                jsonify({"success": False, "error": "No active subscription found"}), 403
            ))

        # Get credits config
        config = get_credits_config()
        credits_cap = config["premium_daily_credits_cap"]

        now = datetime.now(timezone.utc).isoformat()
        now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)

        # Update user: set premium status and restore credits
        user_ref.update({
            "is_premium": True,
            "ai_credits": credits_cap,
            "premium_activated_at": now,
            "credits_restored_at": now,
            "updated_at": now
        })

        # Log the restore operation with before/after state for audit queries
        db.collection("credits_restore_log").add({
            "user_id": user_id,
            "amplitude_id": prev.get("amplitude_id"),
            "timestamp": now,
            "timestamp_ms": now_ms,
            "credits_restored": credits_cap,
            "trigger": "purchase",
            "source": "client_restore",
            "previous_state": prev_state,
            "new_state": {"is_premium": True, "ai_credits": credits_cap},
            "revenuecat_verification_result": status,
        })

        return add_cors_headers(make_response(jsonify({"success": True,
            "ai_credits": credits_cap,
            "is_premium": True,
            "message": "Credits restored successfully"
        })))

    except Exception as e:
        logger.exception("restore_credits_after_purchase: failed (%s)", type(e).__name__)
        # Body is byte-identical to the hand-rolled one it replaces — create_error_response emits
        # the same {"success": False, "error": ...} shape, so no client contract changes.
        return create_error_response("Failed to restore credits. Please try again.", 500)


# ============================================================================
# FUNCTION 7: Get credits config (for client to display correct info)
# ============================================================================

@functions_framework.http
def get_credits_info(request: Request):
    """
    Get current credits configuration for the client.

    This allows the app to display correct values without hardcoding.

    Request body:
    {
        "user_id": "string"
    }

    Response:
    {
        "success": true,
        "config": {
            "action_cost": number,
            "premium_daily_credits_cap": number,
            "refill_time": "12:00 CET"
        },
        "user_credits": number
    }
    """
    # CORS preflight — browsers send OPTIONS before cross-origin POST
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    data, error = validate_request(request)
    if error:
        return create_error_response(error)

    user_id = data["user_id"]

    config = get_credits_config()
    credits = get_user_credits(user_id)

    return create_success_response({
        "config": {
            "action_cost": config["action_cost"],
            "premium_daily_credits_cap": config["premium_daily_credits_cap"],
            "refill_time": "12:00 CET"
        },
        "user_credits": credits
    })


# ============================================================================
# FUNCTION 8: Firestore trigger — bridge rc_events → users/{userId} + audit log
# ============================================================================

# RevenueCat event types that grant or extend premium access.
_RC_GRANT_EVENT_TYPES = frozenset({
    "INITIAL_PURCHASE",
    "TRIAL_STARTED",
    "RENEWAL",
    "UNCANCELLATION",
    "PRODUCT_CHANGE",
    "TRANSFER",
    "NON_RENEWING_PURCHASE",
})

# RevenueCat event types that revoke premium access.
_RC_REVOKE_EVENT_TYPES = frozenset({
    "EXPIRATION",
    "CANCELLATION",
    "SUBSCRIPTION_PAUSED",
})


def _handle_rc_event_payload(data: dict, event_id: str | None) -> None:
    """
    Pure handler for a RevenueCat Firebase Extension webhook payload.

    Kept separate from the trigger decorator so we can unit-test the logic
    without the 2nd gen firestore_fn wiring. Responsibilities:

    1. Reconcile users/{app_user_id}.is_premium and ai_credits so the rest of
       the app sees a consistent premium state within seconds of a purchase
       or expiration — no client restore round-trip required for the happy path.
    2. Append a rich, query-friendly document to premium_events_log/{autoId}
       so we can answer audit questions without paging through raw webhook JSON.

    All writes are best-effort: a bad/partial payload is logged and skipped so
    one malformed event never blocks subsequent triggers.
    """
    if not data:
        return

    event_type = data.get("type")
    app_user_id = data.get("app_user_id")

    # Skip events without an app_user_id — we can't attribute them to a user,
    # and $RCAnonymousID:... events are uninteresting for our audit log.
    if not app_user_id or str(app_user_id).startswith("$RCAnonymousID"):
        return

    now_dt = datetime.now(timezone.utc)
    now_iso = now_dt.isoformat()
    now_ms = int(now_dt.timestamp() * 1000)

    user_ref = db.collection("users").document(app_user_id)
    user_doc = user_ref.get()
    prev = user_doc.to_dict() if user_doc.exists else {}
    prev_state = {
        "is_premium": prev.get("is_premium", False),
        "ai_credits": prev.get("ai_credits", 0),
    }

    new_state = dict(prev_state)

    try:
        if event_type in _RC_GRANT_EVENT_TYPES:
            credits_cap = get_credits_config()["premium_daily_credits_cap"]
            new_state = {"is_premium": True, "ai_credits": credits_cap}
            if user_doc.exists:
                user_ref.update({
                    "is_premium": True,
                    "ai_credits": credits_cap,
                    "premium_activated_at": now_iso,
                    "credits_restored_at": now_iso,
                    "updated_at": now_iso,
                })
        elif event_type in _RC_REVOKE_EVENT_TYPES:
            # Credits stay untouched on revoke — the user keeps what they have
            # until the next daily refill cycle, which will see is_premium=False.
            new_state = {"is_premium": False, "ai_credits": prev_state["ai_credits"]}
            if user_doc.exists:
                user_ref.update({
                    "is_premium": False,
                    "premium_expired_at": now_iso,
                    "updated_at": now_iso,
                })
    except Exception as e:
        # Reconciliation failed — still write the audit log so the event isn't lost.
        print(f"on_rc_event_created: reconciliation failed for {app_user_id}: {e}")

    log_entry = {
        "user_id": app_user_id,
        "amplitude_id": prev.get("amplitude_id"),
        "rc_event_id": event_id,
        "rc_event_type": event_type,
        "server_timestamp": now_iso,
        "server_timestamp_ms": now_ms,
        "event_timestamp_ms": data.get("event_timestamp_ms"),
        "purchased_at_ms": data.get("purchased_at_ms"),
        "expiration_at_ms": data.get("expiration_at_ms"),
        "product_id": data.get("product_id"),
        "entitlement_ids": data.get("entitlement_ids", []),
        "store": data.get("store"),
        "environment": data.get("environment"),
        "transaction_id": data.get("transaction_id"),
        "original_transaction_id": data.get("original_transaction_id"),
        "price": data.get("price"),
        "currency": data.get("currency"),
        "country_code": data.get("country_code"),
        "is_family_share": data.get("is_family_share"),
        "previous_state": prev_state,
        "new_state": new_state,
        "state_changed": prev_state != new_state,
        "source": f"webhook:{event_type}" if event_type else "webhook:unknown",
    }

    try:
        db.collection("premium_events_log").add(log_entry)
    except Exception as e:
        # Swallow — one lost audit row should never re-trigger and duplicate
        # the user-facing reconciliation above.
        print(f"on_rc_event_created: audit log write failed: {e}")


@firestore_fn.on_document_created(
    document="rc_events/{eventId}",
    region="us-central1",
)
def on_rc_event_created(event: firestore_fn.Event) -> None:
    """
    Firestore trigger wrapper — delegates to _handle_rc_event_payload so the
    business logic stays testable without the 2nd gen runtime.
    """
    snap = event.data
    if snap is None:
        return

    data = snap.to_dict() or {}

    event_id = None
    try:
        params = getattr(event, "params", None)
        if isinstance(params, dict):
            event_id = params.get("eventId")
    except Exception:
        pass

    _handle_rc_event_payload(data, event_id)


# ============================================================================
# FUNCTION: classify_chat_intent (Phase B — Layer 2 cheap classifier)
# ============================================================================
#
# Called by AiChatRepositoryImpl when local Layer 1 router returns
# confidence < 0.7. Routes the user phrase through gemini-2.5-flash-lite with
# structured JSON output. Cost: 1 credit per successful classification
# (deducted atomically before the AI call — refunded only manually if Gemini
# returns garbage; intentional simplicity for Phase B MVP).
# ============================================================================

CHAT_INTENT_COST = 1  # Layer 2 cost — much cheaper than analyze/generate (30 credits)
CHAT_INTENT_MAX_INPUT_LEN = 500  # 99th percentile chat command length is well under this

# Prompt schema kept in sync with ChatIntent + ToolCall sealed types in
# feature/aichat/api/domain/model/. Adding a new intent here requires updating
# both files atomically — otherwise classifier output won't map to a known
# ToolCall and ViewModel falls through to Unknown.



def reserve_chat_credit(user_id: str) -> int | None:
    """
    Atomically check-and-deduct 1 credit for a Layer 2 classification call.
    Mirrors reserve_credits() but uses a small per-call cost (CHAT_INTENT_COST)
    instead of action_cost (30) so chat stays affordable for free users.

    Returns the new remaining balance, or None if user document is missing or
    has insufficient credits. Caller MUST treat None as 402 Payment Required.
    """
    user_ref = db.collection("users").document(user_id)

    @firestore.transactional
    def txn(transaction):
        snapshot = user_ref.get(transaction=transaction)
        if not snapshot.exists:
            return None
        current = snapshot.get("ai_credits") or 0
        if current < CHAT_INTENT_COST:
            return None
        new_count = current - CHAT_INTENT_COST
        transaction.update(user_ref, {
            "ai_credits": new_count,
            "updated_at": datetime.now(timezone.utc).isoformat(),
        })
        return new_count

    return txn(db.transaction())


def refund_chat_credit(user_id: str, reason: str) -> bool:
    """
    Refund CHAT_INTENT_COST credits previously deducted by reserve_chat_credit.

    Called when a downstream Gemini call fails after the credit was already
    reserved. Inverse of reserve_chat_credit — increments balance in a single
    Firestore transaction and logs to credits_refund_log for audit.

    Best-effort: a failure here is swallowed so the original error (the reason
    we are refunding in the first place) is what the caller surfaces to the
    client. Returns True on success, False if user doc is missing or txn fails.
    """
    user_ref = db.collection("users").document(user_id)

    @firestore.transactional
    def txn(transaction):
        snapshot = user_ref.get(transaction=transaction)
        if not snapshot.exists:
            return False
        current = snapshot.get("ai_credits") or 0
        new_count = current + CHAT_INTENT_COST
        transaction.update(user_ref, {
            "ai_credits": new_count,
            "updated_at": datetime.now(timezone.utc).isoformat(),
        })
        return True

    try:
        ok = txn(db.transaction())
        if ok:
            try:
                db.collection("credits_refund_log").add({
                    "user_id": user_id,
                    "reason": reason,
                    "amount": CHAT_INTENT_COST,
                    "refunded_at": datetime.now(timezone.utc).isoformat(),
                })
            except Exception:
                pass
        return ok
    except Exception:
        return False


@functions_framework.http
def classify_chat_intent(request: Request):
    """
    Classify a chat command via Gemini 2.5 Flash-Lite with structured JSON output.

    Request body:
    {
        "user_id": "string",
        "text": "user phrase, max 500 chars",
        "locale": "ru" | "en" (informational; classifier handles mixed input)
    }

    Response:
    {
        "success": true,
        "intent": "create_item" | ... | "unknown",
        "entities": { ... },
        "confidence": 0.0–1.0,
        "credits_remaining": int  # new balance after this call's deduction
    }

    Error responses:
        400 — missing/invalid fields
        402 — insufficient credits
        500 — Gemini call or JSON parse failed (credit already deducted, see code comment)
    """
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    data, error = validate_request(request)
    if error is not None:
        return create_error_response(error, 400)

    text = (data.get("text") or "").strip()
    locale = (data.get("locale") or "en").strip().lower()
    if not text:
        return create_error_response("text is required", 400)
    if len(text) > CHAT_INTENT_MAX_INPUT_LEN:
        return create_error_response(
            f"text too long (max {CHAT_INTENT_MAX_INPUT_LEN} chars)", 400
        )
    if locale not in ("ru", "en"):
        # Default unknown locales to en — classifier handles mixed input anyway
        locale = "en"

    # Optional timezone offset (minutes from UTC). Client sends current device
    # offset so Gemini can resolve relative dates ("tomorrow", "in 3 hours")
    # into the user's local time. Clamp to the IANA range UTC-12 .. UTC+14.
    try:
        tz_offset_minutes = int(data.get("timezone_offset_minutes") or 0)
    except (TypeError, ValueError):
        tz_offset_minutes = 0
    tz_offset_minutes = max(-720, min(840, tz_offset_minutes))

    user_id = data["user_id"]

    # Model resolution: production A/B experiment (server-driven) + eval override precedence.
    model_id, model_arm = resolve_experiment_model(user_id, "classify_chat_intent", "gemini-2.5-flash-lite", data)
    exp_meta = {"model_variant": model_arm, "model_id": model_id, "ai_flow": "classify_chat_intent"}

    # Server is authoritative for credit accounting — client cannot bypass.
    # Deduct BEFORE the AI call so concurrent requests can't oversell.
    new_credits = reserve_chat_credit(user_id)
    if new_credits is None:
        return create_error_response("insufficient credits", 402)

    prompt = CLASSIFY_CHAT_INTENT_PROMPT.format(
        locale=locale,
        tz_offset=tz_offset_minutes,
        now_utc=datetime.now(timezone.utc).isoformat(timespec="seconds"),
        text=text,
    )

    try:
        response = call_gemini(prompt, "text", "", model_id=model_id)
        result = parse_gemini_json(response.text)

        # Light validation — never trust LLM output blindly
        intent = result.get("intent", "unknown")
        if intent not in {
            "create_item", "delete_item", "complete_item",
            "create_checklist", "set_reminder", "move_reminders",
            "find_items", "free_form", "unknown",
        }:
            intent = "unknown"

        entities = result.get("entities") or {}
        if not isinstance(entities, dict):
            entities = {}

        try:
            confidence = float(result.get("confidence", 0.0))
        except (TypeError, ValueError):
            confidence = 0.0
        confidence = max(0.0, min(1.0, confidence))

        # Log usage stats (separate from credit deduction — credits are the source of truth)
        try:
            increment_usage(user_id, "classify_chat_intent", "text")
        except Exception:
            # Usage logging is best-effort; never block the response on it
            pass

        return create_success_response({
            **exp_meta,
            "intent": intent,
            "entities": entities,
            "confidence": confidence,
            "credits_remaining": new_credits,
        })

    except Exception as e:
        logger.exception("classify_chat_intent: gemini call or JSON parse failed for user=%s", user_id[:8])
        # Gemini call or JSON parse failed AFTER reserve_chat_credit deducted 1.
        # Refund the credit so the user is not charged for our failure.
        # Best-effort — refund failure is swallowed; original error is surfaced.
        refund_chat_credit(user_id, reason=f"chat_classifier_gemini_failure: {type(e).__name__}")
        return create_error_response("Classification failed", 500)


# ============================================================================
# FUNCTION: transcribe_audio (mic voice input → text for the chat input field)
# ============================================================================
#
# Called by ChatViewModel after the user releases the mic button. The voice
# recording (AAC m4a, base64-encoded) is sent to Gemini 2.5 Flash-Lite, which
# returns the spoken text. The client places the transcript into the chat input
# field so the user can edit before sending. This is pure speech-to-text — no
# chat reasoning, no preview card, no Layer routing.
#
# Cost: 1 credit (same as Layer 2 classifier — cheap enough that free users can
# dictate routinely, expensive enough that abuse costs credits). Atomic
# Firestore deduction via reserve_chat_credit; refund on Gemini failure.
#
# Privacy: audio is sent directly to Gemini; nothing persisted server-side
# besides the standard usage counter. The audio file is deleted client-side
# after the response is received.
# ============================================================================

# Base64 expands raw bytes by ~4/3. Cap raw audio at 5MB (~5 min m4a @ 128 kbps)
# so the encoded payload stays well under Cloud Functions' 10MB request ceiling.
TRANSCRIBE_AUDIO_MAX_RAW_BYTES = 5 * 1024 * 1024
TRANSCRIBE_AUDIO_MAX_B64_CHARS = (TRANSCRIBE_AUDIO_MAX_RAW_BYTES * 4 // 3) + 16




@functions_framework.http
def transcribe_audio(request: Request):
    """
    Transcribe an audio clip (AAC m4a, base64-encoded) to spoken text.

    Request body:
    {
        "user_id": "string",
        "audio_base64": "base64-encoded audio (max ~6.7MB encoded / ~5MB raw)",
        "mime_type": "audio/m4a | audio/webm | audio/mp4 | ..." (optional, default audio/mp4),
        "locale": "ru" | "en" (informational; Gemini auto-detects)
    }

    Response:
    {
        "success": true,
        "transcript": "the spoken text, or empty string if silent",
        "credits_remaining": int
    }

    Error responses:
        400 — missing fields or audio too large
        402 — insufficient credits
        500 — Gemini call failed (credit refunded)
    """
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    data, error = validate_request(request)
    if error is not None:
        return create_error_response(error, 400)

    audio_b64 = (data.get("audio_base64") or "").strip()
    if not audio_b64:
        return create_error_response("audio_base64 is required", 400)
    if len(audio_b64) > TRANSCRIBE_AUDIO_MAX_B64_CHARS:
        return create_error_response(
            f"audio too large (max {TRANSCRIBE_AUDIO_MAX_RAW_BYTES // (1024 * 1024)} MB raw)", 400
        )

    locale = (data.get("locale") or "en").strip().lower()
    if locale not in ("ru", "en"):
        locale = "en"

    # Normalize the client-supplied MIME (browsers send "audio/webm;codecs=opus",
    # Android sends "audio/m4a", Safari sends "audio/mp4"). Gemini requires a
    # whitelisted MIME without codec parameters.
    client_mime = (data.get("mime_type") or "audio/mp4").strip()
    gemini_mime = normalize_audio_mime(client_mime)

    user_id = data["user_id"]

    # Server is authoritative for credit accounting — deduct BEFORE the AI call
    # so concurrent requests cannot oversell credits.
    new_credits = reserve_chat_credit(user_id)
    if new_credits is None:
        return create_error_response("insufficient credits", 402)

    try:
        response = call_gemini(TRANSCRIBE_AUDIO_PROMPT, "audio_base64", audio_b64, audio_mime_type=gemini_mime)
        transcript = (response.text or "").strip()

        # Defensive: strip outer quotes if Gemini wrapped the transcript despite
        # the prompt explicitly forbidding it. Mismatched quotes are left as-is.
        if len(transcript) >= 2 and transcript[0] == transcript[-1] and transcript[0] in ("\"", "'"):
            transcript = transcript[1:-1].strip()

        # Best-effort usage logging — never block the response on it.
        try:
            increment_usage(user_id, "transcribe_audio", "audio_base64")
        except Exception:
            pass

        return create_success_response({
            "transcript": transcript,
            "credits_remaining": new_credits,
        })

    except Exception as e:
        logger.exception("transcribe_audio: gemini call failed for user=%s", user_id[:8])
        # Gemini call failed AFTER reserve_chat_credit deducted 1.
        # Refund so the user is not charged for our failure.
        refund_chat_credit(user_id, reason=f"transcribe_audio_gemini_failure: {type(e).__name__}")
        return create_error_response("Transcription failed", 500)


# ============================================================================
# FUNCTION: chat_completion (Phase C.2 — Layer 3 full free-form reasoning)
# ============================================================================
#
# Called by AiChatRepositoryImpl when intent is FreeForm (open question,
# planning, summarisation). Routes the conversation through gemini-2.5-flash
# (NOT lite — Layer 3 needs better reasoning for open questions).
#
# Cost: 3 credits per successful completion (atomic Firestore deduction).
#
# Privacy: checklist content lives on-device (Room). Server never reads it
# from Firestore. The CLIENT decides what summary (names + counts) to send.
# Conversation history is sent as-is to Gemini. Privacy Policy MUST document
# this — see docs/security-playbook.md.
# ============================================================================

CHAT_COMPLETION_COST = 3                   # Layer 3 — Flash full model
CHAT_COMPLETION_MAX_MESSAGES = 12          # sliding window — oldest dropped
CHAT_COMPLETION_MAX_TOTAL_CHARS = 6000     # combined across all messages
CHAT_COMPLETION_MAX_CHECKLISTS = 8         # context items from client
# Recent-items context (shared by chat_completion + chat_agent). The CLIENT already bounds the
# payload (RECENT_ITEMS_PER_CHECKLIST=6, RECENT_ITEMS_TOTAL_BUDGET=30 in ChatViewModel); these are
# defense-in-depth caps so a malformed/oversized request can never bloat the prompt or token cost.
CHAT_CONTEXT_RECENT_ITEMS_PER_CHECKLIST = 6   # max recent item lines rendered per checklist
CHAT_CONTEXT_ITEM_TEXT_MAX_CHARS = 200        # clamp a single item's text length

# ----------------------------------------------------------------------------
# Feature catalog — single source of truth for "what the app can do".
# Injected into the Layer 3 system prompt so the model can answer
# "how do I X" without hallucinating. When you ship a NEW user-facing
# feature in the app, you MUST add a row here (RU + EN) before the next
# release — see docs/guidelines/ai-chat-feature-coverage.md.
# Keep each entry: 1 short title in bold + 1-3 lines of "what + UI path".
# Never include real user data (checklist names, item text, user IDs).
# ----------------------------------------------------------------------------








def reserve_chat_completion_credits(user_id: str) -> int | None:
    """
    Atomically deduct CHAT_COMPLETION_COST credits for one Layer 3 call.
    Returns the new balance, or None if user is missing or under-credited.
    """
    user_ref = db.collection("users").document(user_id)

    @firestore.transactional
    def txn(transaction):
        snapshot = user_ref.get(transaction=transaction)
        if not snapshot.exists:
            return None
        current = snapshot.get("ai_credits") or 0
        if current < CHAT_COMPLETION_COST:
            return None
        new_count = current - CHAT_COMPLETION_COST
        transaction.update(user_ref, {
            "ai_credits": new_count,
            "updated_at": datetime.now(timezone.utc).isoformat(),
        })
        return new_count

    return txn(db.transaction())


def refund_chat_completion_credits(user_id: str, reason: str) -> bool:
    """Best-effort refund of CHAT_COMPLETION_COST credits on Gemini failure."""
    user_ref = db.collection("users").document(user_id)

    @firestore.transactional
    def txn(transaction):
        snapshot = user_ref.get(transaction=transaction)
        if not snapshot.exists:
            return False
        current = snapshot.get("ai_credits") or 0
        transaction.update(user_ref, {
            "ai_credits": current + CHAT_COMPLETION_COST,
            "updated_at": datetime.now(timezone.utc).isoformat(),
        })
        return True

    try:
        ok = txn(db.transaction())
        if ok:
            try:
                db.collection("credits_refund_log").add({
                    "user_id": user_id,
                    "reason": reason,
                    "amount": CHAT_COMPLETION_COST,
                    "refunded_at": datetime.now(timezone.utc).isoformat(),
                })
            except Exception:
                pass
        return ok
    except Exception:
        return False


def _call_gemini_flash(prompt: str, model_id: str = None) -> str:
    """Call gemini-2.5-flash (NOT lite) for free-form reasoning.

    [model_id] defaults to gemini-2.5-flash. Callers may pass a test-override value
    already resolved via [resolve_model]; never pass an unvalidated client value here.
    """
    response = gemini_client.models.generate_content(model=model_id or "gemini-2.5-flash", contents=prompt)
    return (response.text or "").strip()


def _format_checklists_summary(items) -> str:
    """Render the optional client-provided checklists into a plain bullet list.

    Each checklist line is "- <name> (<total> items, <done> done)". When the client
    includes a `recentItems` slice (the most-recently-added tail of the list — the client
    bounds the size), the items are rendered as an indented sub-list so the model can answer
    "what did I add recently / find the task about X". Recency is positional (list order), not
    wall-clock: the client has no per-item timestamp, so we never claim an exact add-time.
    Defensive caps below mirror the client budget so a malformed/oversized payload can't bloat
    the prompt.
    """
    if not items or not isinstance(items, list):
        return "(no recent checklists provided)"
    lines = []
    for item in items[:CHAT_COMPLETION_MAX_CHECKLISTS]:
        if not isinstance(item, dict):
            continue
        name = (item.get("name") or "(unnamed)").strip() or "(unnamed)"
        total = int(item.get("totalItems") or 0)
        done = int(item.get("doneItems") or 0)
        lines.append(f"- {name} ({total} items, {done} done)")

        recent = item.get("recentItems")
        if isinstance(recent, list) and recent:
            # Server-side guard: cap per-list rendering even if the client sent more.
            for entry in recent[:CHAT_CONTEXT_RECENT_ITEMS_PER_CHECKLIST]:
                if not isinstance(entry, dict):
                    continue
                text = (entry.get("text") or "").strip()
                if not text:
                    continue
                # Clamp very long item text so one giant item can't dominate the prompt.
                text = text[:CHAT_CONTEXT_ITEM_TEXT_MAX_CHARS]
                mark = "x" if entry.get("checked") else " "
                lines.append(f"    - [{mark}] {text}")
    return "\n".join(lines) if lines else "(no recent checklists provided)"


# ── Response-language support (all-languages, additive / backward-compatible) ──────────
# The chat responds in the user's language. Two modes, both template-agnostic (the
# directive is appended to the already-formatted prompt, so it does not depend on the
# proprietary prompts_private.py template internals):
#   • Auto (default): `response_language` absent/blank → reply in the user's own message
#     language (Gemini auto-detects from the text). Works for EVERY language and for old
#     clients that never send the field — no client change required.
#   • Explicit override: `response_language` = a BCP-47 tag → always reply in that language.
# The directive is worded as highest-priority so it overrides any language note the private
# template may already inject via {locale}.
_RESPONSE_LANGUAGE_NAMES = {
    "en": "English", "hi": "Hindi", "es": "Spanish", "pt": "Portuguese",
    "de": "German", "fr": "French", "it": "Italian", "nl": "Dutch",
    "pl": "Polish", "tr": "Turkish", "ru": "Russian", "uk": "Ukrainian",
    "ar": "Arabic", "zh": "Chinese", "ja": "Japanese", "ko": "Korean",
    # tolerated beyond the client picker list — server stays permissive to any tag
    "id": "Indonesian", "vi": "Vietnamese", "th": "Thai", "fa": "Persian",
    "cs": "Czech", "sv": "Swedish", "ro": "Romanian", "el": "Greek",
    "he": "Hebrew", "hu": "Hungarian", "da": "Danish", "fi": "Finnish",
    "nb": "Norwegian", "no": "Norwegian", "sk": "Slovak", "bg": "Bulgarian",
}


def _normalise_response_language(raw):
    """Extract a clean BCP-47 primary subtag from the optional `response_language` field.

    Returns None (→ Auto) when absent, blank, or not a string. "es-419"/"zh_Hant" → "es"/"zh".
    """
    if not isinstance(raw, str):
        return None
    tag = raw.strip().lower()
    if not tag:
        return None
    return tag.split("-")[0].split("_")[0] or None


def _language_directive(response_language):
    """Final, authoritative language block appended to a chat prompt.

    `response_language` is a normalised primary subtag, or None for Auto.
    """
    if response_language:
        name = _RESPONSE_LANGUAGE_NAMES.get(response_language)
        target = name or f"the language with BCP-47 code '{response_language}'"
        return (
            "\n\n---\n"
            "LANGUAGE (highest priority — overrides any language note above): "
            f"Write your ENTIRE response in {target}, regardless of the language the "
            "user writes in. Only user-provided proper nouns and quoted text may stay "
            "in their original language."
        )
    return (
        "\n\n---\n"
        "LANGUAGE (highest priority — overrides any language note above): "
        "Write your ENTIRE response in the SAME language as the user's most recent "
        "message. Detect the language from the message text itself, not from any locale "
        "code. If the message is too short to tell, keep the prior conversation's language."
    )


@functions_framework.http
def chat_completion(request: Request):
    """
    Layer 3 — full chat completion via Gemini Flash for free-form questions.

    Request body:
    {
        "user_id": "string",
        "messages": [{"role": "user|assistant", "content": "..."}, ...],
        "locale": "ru" | "en",
        "timezone_offset_minutes": -720..840,
        "checklists_summary": [{"name": "...", "totalItems": N, "doneItems": N}, ...]
    }

    Response:
        200 → { "success": true, "content": "...", "credits_remaining": int }
        400 → invalid payload
        402 → insufficient credits
        500 → Gemini failure (credits refunded automatically)
    """
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    data, error = validate_request(request)
    if error is not None:
        return create_error_response(error, 400)

    user_id = (data.get("user_id") or "").strip()
    if not user_id:
        return create_error_response("user_id is required", 400)

    # Model resolution: production A/B experiment (server-driven) + eval override precedence.
    # NOTE: chat_completion is legacy/dead (Layer 3 is chat_agent); wired for consistency.
    model_id, model_arm = resolve_experiment_model(user_id, "chat_completion", "gemini-2.5-flash", data)
    exp_meta = {"model_variant": model_arm, "model_id": model_id, "ai_flow": "chat_completion"}

    messages_raw = data.get("messages")
    if not isinstance(messages_raw, list) or not messages_raw:
        return create_error_response("messages must be a non-empty list", 400)

    # Sliding window — keep last N exchanges.
    messages = messages_raw[-CHAT_COMPLETION_MAX_MESSAGES:]

    total_chars = 0
    normalised = []
    for m in messages:
        if not isinstance(m, dict):
            return create_error_response("each message must be an object", 400)
        role = (m.get("role") or "").strip().lower()
        content = m.get("content")
        if role not in ("user", "assistant"):
            return create_error_response("message.role must be 'user' or 'assistant'", 400)
        if not isinstance(content, str) or not content.strip():
            return create_error_response("message.content must be a non-empty string", 400)
        total_chars += len(content)
        if total_chars > CHAT_COMPLETION_MAX_TOTAL_CHARS:
            return create_error_response(
                f"messages exceed {CHAT_COMPLETION_MAX_TOTAL_CHARS} chars cap", 400
            )
        normalised.append({"role": role, "content": content})

    locale = (data.get("locale") or "en").strip().lower()
    if locale not in ("ru", "en"):
        locale = "en"

    # Optional + additive: explicit response-language override. Absent → Auto (reply in the
    # user's message language). Old clients never send it, so the legacy path is unchanged.
    response_language = _normalise_response_language(data.get("response_language"))

    try:
        tz_offset_minutes = int(data.get("timezone_offset_minutes") or 0)
    except (TypeError, ValueError):
        tz_offset_minutes = 0
    tz_offset_minutes = max(-720, min(840, tz_offset_minutes))

    checklists_raw = data.get("checklists_summary") or []
    checklists_summary_text = _format_checklists_summary(checklists_raw)

    new_credits = reserve_chat_completion_credits(user_id)
    if new_credits is None:
        return create_error_response("insufficient credits", 402)

    history_text = "\n".join(
        f"{'User' if m['role'] == 'user' else 'Assistant'}: {m['content']}"
        for m in normalised
    )

    features_block = FEATURE_CATALOG_RU if locale == "ru" else FEATURE_CATALOG_EN

    prompt = CHAT_COMPLETION_PROMPT_TEMPLATE.format(
        locale=locale,
        features=features_block,
        now_utc=datetime.now(timezone.utc).isoformat(timespec="seconds"),
        tz_offset=tz_offset_minutes,
        checklists_count=min(len(checklists_raw), CHAT_COMPLETION_MAX_CHECKLISTS),
        checklists_summary=checklists_summary_text,
        history=history_text,
    )
    prompt += _language_directive(response_language)

    try:
        content = _call_gemini_flash(prompt, model_id=model_id)
        if not content:
            raise ValueError("Gemini returned empty response")

        try:
            increment_usage(user_id, "chat_completion", "text")
        except Exception:
            pass

        return create_success_response({
            **exp_meta,
            "content": content,
            "credits_remaining": new_credits,
        })

    except Exception as e:
        # Log BEFORE refunding: if the refund itself throws, the original traceback would
        # otherwise be replaced by the refund's and the real cause would be lost.
        logger.exception("chat_completion: failed (%s)", type(e).__name__)
        refund_chat_completion_credits(
            user_id,
            reason=f"chat_completion_gemini_failure: {type(e).__name__}"
        )
        return create_error_response("Completion failed", 500)


# ============================================================================
# FUNCTION: chat_agent (Phase 2 — the agentic bridge / "next-step oracle")
# ============================================================================
#
# Turns Layer 3 into an AGENT that can perform real checklist actions. Because
# Cloud Functions are stateless and checklist data lives on-device (Room), the
# CLIENT is the only place tools can run. This endpoint is a thin, stateless
# "next-step oracle":
#
#   round N:  client POSTs the full structured transcript so far
#             server rebuilds Gemini `contents` and calls generate_content(tools=[...])
#             server returns either { type:"tool_calls", calls:[...] }  (the client
#             executes them via ToolCallDispatcher and loops) OR { type:"final" }.
#
# This fixes the two worst Amplitude bugs: (1) Layer 3 lying about actions it
# cannot perform, and (2) confirmations ("да добавь все") losing the proposal
# context — the agent now sees the whole conversation and acts on it.
#
# Credits (D2): flat CHAT_AGENT_COST (3) reserved ONCE per user TURN — i.e. only
# on the first round (transcript has no `tool` turn yet). Follow-up rounds in the
# same turn are free. Refund on Gemini failure (only if we reserved this round).
#
# Privacy (D3): when the model calls read_checklist/find_items the CLIENT returns
# item text as a function_response — checklist item text reaches Gemini. The
# client controls what each read tool returns. Documented in security-playbook.
# ============================================================================

CHAT_AGENT_COST = 3                     # flat per-turn cost (charged on round 1 only)
CHAT_AGENT_MODEL = "gemini-2.5-flash"   # stable model; thinking_budget=0 verified here
CHAT_AGENT_MAX_ROUNDS = 5               # server-side defense-in-depth (client caps too)
CHAT_AGENT_MAX_TRANSCRIPT_ENTRIES = 60  # hard cap on transcript size
CHAT_AGENT_MAX_TOTAL_CHARS = 12000      # combined chars across all transcript text
CHAT_AGENT_MAX_CHECKLISTS = 8           # context summary items from client
CHAT_AGENT_MAX_OPTIONS = 6              # present_options chip cap (mirrors client MAX_CHOICE_OPTIONS)
CHAT_AGENT_MIN_OPTIONS = 2             # present_options needs >=2 usable labels to render chips

# ⚠️ CHAT_AGENT_COST MUST stay == CHAT_COMPLETION_COST (both 3). chat_agent's no-request_id path
# reserves via reserve_chat_completion_credits (CHAT_COMPLETION_COST) — that IS today's production
# path, deliberately left byte-for-byte alone — while the request_id path reserves CHAT_AGENT_COST.
# Drifting the two would make a turn's price depend on whether the client sent a request_id.
# Locked by tests/test_chat_agent_turn_boundary.py::test_both_reserve_paths_charge_the_same_cost
# (a test, not an import-time assert: main.py hosts every endpoint, so a failed import here would
# take down all Cloud Functions instead of one flow).


def reserve_chat_agent_credits(
    user_id: str, request_id: str | None = None
) -> tuple[str, int | None]:
    """Reserve CHAT_AGENT_COST for one chat turn, idempotently when [request_id] is given.

    Reuses the generic credit_reservations machinery (reservation_decision + the
    "{user_id}__{request_id}" dedup doc) rather than adding a second, chat-only dedup scheme.
    Returns (action, balance) — see reserve_credits_with_action; only ("reserve", ...) may be
    refunded.
    """
    return reserve_credits_with_action(user_id, CHAT_AGENT_COST, request_id)


# Tool names the agent may emit. The client's ToolCallDispatcher MUST be able to
# execute every name here (server catalog == client capability). Phase 2 core set
# — closes findings #1/#2. Phase 3/5 extend this list as the dispatcher grows.
CHAT_AGENT_TOOL_NAMES = {
    "add_item", "add_items", "create_checklist", "complete_item",
    "delete_item", "clear_completed_items", "set_item_reminder",
    "find_items", "read_checklist", "rename_checklist",
    # move_item ships in the client BEFORE this line is deployed — the reverse order returns
    # `unknown_tool` function_responses for a tool the dispatcher cannot execute yet.
    "move_item",
}


def _build_chat_agent_tools(include_options: bool = False) -> "list[types.Tool]":
    """Build the Gemini function-declaration catalog (Phase 2 core).

    Built once at module load (two variants). Read-only tools (find_items, read_checklist)
    are auto-run by the client; mutating tools are batched into a plan-card the user confirms
    once; delete_item is destructive and always gets its own confirm.

    [include_options] appends present_options — gated behind the client's `supports_options`
    capability so older clients (which can't render type:"options") never receive it.
    """
    STR = types.Type.STRING
    OBJ = types.Type.OBJECT
    ARR = types.Type.ARRAY

    def s(type_, description=None, items=None):
        return types.Schema(type=type_, description=description, items=items)

    hint = s(STR, "Fuzzy, case-insensitive name of the target checklist. Omit to use the user's active (first) checklist.")

    declarations = [
        types.FunctionDeclaration(
            name="add_item",
            description="Add a SINGLE item to a checklist. Prefer add_items when adding several at once.",
            parameters=types.Schema(
                type=OBJ,
                properties={"checklist_hint": hint, "item_text": s(STR, "The item text to add.")},
                required=["item_text"],
            ),
        ),
        types.FunctionDeclaration(
            name="add_items",
            description="Add MULTIPLE items to one checklist in a single call.",
            parameters=types.Schema(
                type=OBJ,
                properties={
                    "checklist_hint": hint,
                    "item_texts": s(ARR, "Items to add, one string per element.", items=s(STR)),
                },
                required=["item_texts"],
            ),
        ),
        types.FunctionDeclaration(
            name="create_checklist",
            description=(
                "Create a new checklist. If the user names a TOPIC without explicit items "
                "(e.g. 'a checklist for learning to climb'), GENERATE sensible items yourself "
                "and pass them in initial_items — never create an empty list."
            ),
            parameters=types.Schema(
                type=OBJ,
                properties={
                    "name": s(STR, "The checklist name."),
                    "initial_items": s(ARR, "Items to pre-fill, one string per element.", items=s(STR)),
                },
                required=["name"],
            ),
        ),
        types.FunctionDeclaration(
            name="complete_item",
            description="Mark an existing item as done (checked).",
            parameters=types.Schema(
                type=OBJ,
                properties={"checklist_hint": hint, "item_text": s(STR, "Fuzzy text of the item to complete.")},
                required=["item_text"],
            ),
        ),
        types.FunctionDeclaration(
            name="delete_item",
            description="Delete an item from a checklist. Destructive — the client asks the user to confirm.",
            parameters=types.Schema(
                type=OBJ,
                properties={"checklist_hint": hint, "item_text": s(STR, "Fuzzy text of the item to delete.")},
                required=["item_text"],
            ),
        ),
        types.FunctionDeclaration(
            name="clear_completed_items",
            description=(
                "Remove ALL completed (checked) items from a checklist in one action. Use this for "
                "requests like 'delete completed', 'удали выполненные', 'clear checked items', "
                "'очисти сделанное'. This is a bulk operation — do NOT call delete_item with "
                "'completed'/'выполненные' as the item_text (that is not an item name). Omit "
                "checklist_hint to target the current/active checklist."
            ),
            parameters=types.Schema(
                type=OBJ,
                properties={"checklist_hint": hint},
                required=[],
            ),
        ),
        types.FunctionDeclaration(
            name="set_item_reminder",
            description="Set a one-shot reminder on an item. Resolve relative times to absolute ISO-8601 in the user's local time.",
            parameters=types.Schema(
                type=OBJ,
                properties={
                    "checklist_hint": hint,
                    "item_text": s(STR, "Fuzzy text of the item to remind about."),
                    "when_iso": s(STR, "Absolute reminder time as ISO-8601 (e.g. 2026-06-01T09:00:00) in the user's local time."),
                },
                required=["item_text", "when_iso"],
            ),
        ),
        types.FunctionDeclaration(
            name="rename_checklist",
            description="Rename an existing checklist.",
            parameters=types.Schema(
                type=OBJ,
                properties={
                    "checklist_hint": s(STR, "Fuzzy name of the checklist to rename."),
                    "new_name": s(STR, "The new checklist name."),
                },
                required=["checklist_hint", "new_name"],
            ),
        ),
        types.FunctionDeclaration(
            name="move_item",
            description=(
                "Move an existing item from one list to another, keeping its text and done state. "
                "Use this to file inbox tasks into projects. Omit from_checklist_hint to move OUT of "
                "the list the user is currently looking at. NEVER emulate a move with add_item + "
                "delete_item — that loses the item if only one half is approved. "
                'A result of {"status":"not_found","reason":"move_blocked"} means the item EXISTS '
                "but carries a reminder, a repeat or an attachment that a move would break: say so "
                "and leave it where it is — do NOT report it as missing and do NOT retry."
            ),
            parameters=types.Schema(
                type=OBJ,
                properties={
                    "item_text": s(STR, "Fuzzy text of the item to move."),
                    "to_checklist_hint": s(STR, "Fuzzy name of the destination checklist."),
                    "from_checklist_hint": hint,
                },
                required=["item_text", "to_checklist_hint"],
            ),
        ),
        types.FunctionDeclaration(
            name="find_items",
            description="READ-ONLY. Search the user's items by text across all checklists. Free to call; use it to ground answers.",
            parameters=types.Schema(
                type=OBJ,
                properties={"query": s(STR, "Substring to search for in item texts.")},
                required=["query"],
            ),
        ),
        types.FunctionDeclaration(
            name="read_checklist",
            description="READ-ONLY. Return the items of one checklist by name. Free to call; use it before answering questions about a list's contents.",
            parameters=types.Schema(
                type=OBJ,
                properties={"name": s(STR, "Fuzzy name of the checklist to read.")},
                required=["name"],
            ),
        ),
    ]
    if include_options:
        declarations.append(types.FunctionDeclaration(
            name="present_options",
            description=(
                "Offer the user 2-6 short tappable options instead of guessing or asking an "
                "open-ended question. Use when the request is ambiguous, when a clarification "
                "would help, or when proposing useful next steps. Do NOT use it for destructive "
                "confirmations (delete) — the client confirms those automatically. The client "
                "shows each option as a chip; tapping one sends that option's label back as the "
                "user's next message. Terminal for this turn — do not combine with other tools."
            ),
            parameters=types.Schema(
                type=OBJ,
                properties={
                    "prompt": s(STR, "Short question shown above the options, in the user's language."),
                    "options": s(ARR, "2-6 concise option labels in the user's language.", items=s(STR)),
                },
                required=["prompt", "options"],
            ),
        ))
    return [types.Tool(function_declarations=declarations)]


# Built once — function declarations are static and locale-independent. Two variants:
# the base set, and one with present_options for clients that advertise `supports_options`.
CHAT_AGENT_TOOLS = _build_chat_agent_tools(include_options=False)
CHAT_AGENT_TOOLS_WITH_OPTIONS = _build_chat_agent_tools(include_options=True)




SCREEN_SNAPSHOT_MAX_ITEMS = 20          # hard clamp; the client sends 15
SCREEN_SNAPSHOT_MAX_TEXT = 120


def _build_screen_context_block(screen: dict) -> str:
    """Turn the client's `context_screen` into the ONE prompt paragraph that describes the UI.

    Kept INSIDE the existing {context_block} placeholder so the proprietary template
    (prompts_private.py, gitignored) needs no new field — a template change could not be
    reviewed in this repo.

    The inbox and the day are the two screens the model is otherwise blind to:
    `checklists_summary` is built from the client's `projects`, which structurally excludes the
    system Inbox, and nothing has ever carried "what is scheduled today".
    """
    kind = (screen.get("kind") or "").strip()
    label = (screen.get("label") or "").strip()[:SCREEN_SNAPSHOT_MAX_TEXT]
    total = screen.get("total_items") or 0
    raw_items = screen.get("items") or []
    if not isinstance(raw_items, list):
        raw_items = []

    lines = []
    for it in raw_items[:SCREEN_SNAPSHOT_MAX_ITEMS]:
        if not isinstance(it, dict):
            continue
        text = (it.get("text") or "").strip()[:SCREEN_SNAPSHOT_MAX_TEXT]
        if not text:
            continue
        marks = []
        if it.get("checked"):
            marks.append("done")
        if it.get("list"):
            marks.append('in "%s"' % str(it["list"])[:SCREEN_SNAPSHOT_MAX_TEXT])
        if it.get("due"):
            marks.append("due %s" % str(it["due"])[:40])
        if it.get("has_reminder"):
            marks.append("has reminder")
        if it.get("has_attachment"):
            marks.append("has attachment")
        lines.append("- " + text + (" [%s]" % ", ".join(marks) if marks else ""))

    listing = "\n".join(lines) if lines else "(nothing on this screen right now)"
    # Truncation must be VISIBLE: without this the model asserts a count from a partial list.
    shown_note = (
        f"Showing {len(lines)} of {total} — say so instead of quoting a total you cannot see."
        if total > len(lines) else ""
    )

    if kind == "inbox":
        return (
            f'The user is looking at their INBOX ("{label}") — the quick-capture list. Its contents '
            "are below. The inbox is NOT part of the checklist summary above: it is deliberately "
            "excluded from the user's projects, so these items appear nowhere else in this prompt.\n"
            f"{listing}\n{shown_note}\n"
            "To act on an inbox item (complete / delete / set a reminder / clear completed) call the "
            "tool WITHOUT checklist_hint — the client targets the inbox itself. Do NOT pass "
            f'checklist_hint="{label}": no project has that name and the call will fail.\n'
            "To file an inbox task into a project, use move_item(item_text=..., to_checklist_hint="
            '"<project>"). NEVER emulate a move with add_item + delete_item: that loses the item and '
            "duplicates it if only one half is approved.\n"
            "An item marked [has reminder] or [has attachment] cannot be moved — say so and leave it."
        )
    if kind == "agenda":
        return (
            f"The user is on the CALENDAR screen. What is scheduled for TODAY ({label}):\n"
            f"{listing}\n{shown_note}\n"
            "This covers today ONLY. The screen also renders overdue items and later days, and "
            "they are NOT in this list — never answer 'nothing is overdue' or 'nothing is coming "
            "up' from it. Use find_items / read_checklist for anything outside today, or say you "
            "can only see today.\n"
            "These entries live in the lists named beside them — pass that name as checklist_hint "
            "when you act on one; an entry with no list name IS a whole checklist falling due. "
            "Only a bare 'add X' goes to the inbox: call add_item WITHOUT checklist_hint. For "
            "complete / delete / set_item_reminder ALWAYS pass the checklist_hint shown beside "
            "the entry — a hintless one on this screen does NOT default to anything.\n"
            "This is a snapshot taken when the turn started, and it is sent once per turn; your "
            "own tool results are authoritative for anything that has changed since."
        )
    if kind == "projects":
        return (
            "The user is looking at the PROJECTS list — the checklists summarised above, nothing "
            "more specific. Prefer read_checklist before answering about one list's contents."
        )
    if kind == "overview":
        return (
            "The user is looking at the OVERVIEW screen (progress and counts across their lists). "
            "Ground any figure in read_checklist / find_items rather than estimating."
        )
    return "The user is on the home screen — no specific checklist is open."


def _coerce_response_dict(result: Any) -> dict:
    """A Gemini function_response payload MUST be a dict. Wrap scalars/lists."""
    if isinstance(result, dict):
        return result
    return {"result": result}


# Gemini 3.x rejects a replayed function_call part that carries no `thought_signature`
# (400 INVALID_ARGUMENT). Two kinds of parts legitimately have none:
#   1. transcripts produced by Gemini 2.5 (control arm) — that model never emits one;
#   2. transcripts from app builds shipped before the signature round-trip existed.
# Google documents this exact placeholder for synthetic/legacy calls; it is the ONLY
# accepted value besides a real signature (an invented string 400s just the same).
# Keep it: it is what lets a stale client keep working on a 3.x arm without an app release.
_LEGACY_THOUGHT_SIGNATURE = b"skip_thought_signature_validator"


def _decode_thought_signature(raw) -> bytes:
    """Client-supplied base64 signature -> bytes; the documented placeholder when absent/garbage.

    Never raises: a malformed signature must degrade to the placeholder, not 500 the turn.
    """
    if raw:
        try:
            return base64.b64decode(raw)
        except Exception:
            logger.warning("chat_agent: undecodable thought_signature, using placeholder")
    return _LEGACY_THOUGHT_SIGNATURE


def _reconstruct_agent_contents(transcript: list) -> "list[types.Content]":
    """Rebuild Gemini `contents` from the client's structured transcript.

    user            -> Content(role=user,  parts=[text])
    model.tool_calls-> Content(role=model, parts=[function_call ...])
    tool.tool_results-> Content(role=user, parts=[function_response ...])   (role IS user)

    `thought_signature` rides on the PART, not on the FunctionCall, and Gemini 3.x
    requires it back on every replayed function_call part — see _LEGACY_THOUGHT_SIGNATURE.
    """
    contents: list[types.Content] = []
    for entry in transcript:
        if not isinstance(entry, dict):
            continue
        role = (entry.get("role") or "").strip().lower()

        if role == "user":
            text = (entry.get("text") or "").strip()
            if text:
                contents.append(types.Content(role="user", parts=[types.Part.from_text(text=text)]))

        elif role == "model":
            parts = []
            for tc in entry.get("tool_calls") or []:
                if not isinstance(tc, dict) or not tc.get("name"):
                    continue
                parts.append(types.Part(
                    function_call=types.FunctionCall(
                        id=tc.get("id"),
                        name=tc["name"],
                        args=dict(tc.get("args") or {}),
                    ),
                    thought_signature=_decode_thought_signature(tc.get("thought_signature")),
                ))
            if parts:
                contents.append(types.Content(role="model", parts=parts))
            else:
                # Assistant prose from conversation history (no tool calls in this turn).
                text = (entry.get("text") or "").strip()
                if text:
                    contents.append(types.Content(role="model", parts=[types.Part.from_text(text=text)]))

        elif role == "tool":
            parts = []
            for tr in entry.get("tool_results") or []:
                if not isinstance(tr, dict) or not tr.get("name"):
                    continue
                parts.append(types.Part(function_response=types.FunctionResponse(
                    id=tr.get("id"),
                    name=tr["name"],
                    response=_coerce_response_dict(tr.get("result")),
                )))
            if parts:
                contents.append(types.Content(role="user", parts=parts))

    return contents


def _serialize_function_calls(parts) -> list:
    """Extract function_call parts into the client-facing {id, name, args[, thought_signature]} list.

    `present_options` is excluded — it is a SERVER-TERMINAL tool intercepted upstream
    (the client renders chips, it is never dispatched via ToolCallDispatcher). Skipping
    it here means a malformed present_options call can't leak as an undispatchable tool.

    `thought_signature` is read off the PART (not the FunctionCall — the SDK hangs it one
    level up) and base64'd so it survives the JSON transcript the client stores and replays.
    Emitted only when the model produced one: 2.5 never does, and an absent key is what the
    reconstruct side reads as "legacy" — see _LEGACY_THOUGHT_SIGNATURE.
    """
    calls = []
    for i, part in enumerate(parts or []):
        fc = getattr(part, "function_call", None)
        if fc is None or not getattr(fc, "name", None) or fc.name == "present_options":
            continue
        call = {
            "id": fc.id or f"call_{i}",
            "name": fc.name,
            "args": dict(fc.args or {}),
        }
        signature = getattr(part, "thought_signature", None)
        if signature:
            call["thought_signature"] = base64.b64encode(signature).decode("ascii")
        calls.append(call)
    return calls


def _extract_present_options(parts) -> dict | None:
    """If the model called present_options, return {"prompt", "options"[]}; else None.

    present_options is server-terminal: the client renders the labels as tappable chips
    and sends the chosen label back as the next user message. chat_agent intercepts it
    before serializing the generic (client-dispatched) tool calls. Returns None when the
    call is absent or malformed (no prompt, or fewer than 2 usable labels) so the caller
    falls through to normal tool/final handling.
    """
    for part in parts or []:
        fc = getattr(part, "function_call", None)
        if fc is None or getattr(fc, "name", None) != "present_options":
            continue
        args = dict(fc.args or {})
        prompt = (str(args.get("prompt") or "")).strip()
        options: list[str] = []
        seen: set[str] = set()
        for raw in (args.get("options") or []):
            label = (str(raw) or "").strip()
            if not label or label.lower() in seen:
                continue
            seen.add(label.lower())
            options.append(label)
            if len(options) >= CHAT_AGENT_MAX_OPTIONS:
                break
        if prompt and len(options) >= CHAT_AGENT_MIN_OPTIONS:
            return {"prompt": prompt, "options": options}
        return None
    return None


def _salvage_present_options_prompt(parts) -> str:
    """The prompt of a present_options call that _extract_present_options rejected.

    A malformed present_options (no prompt, or fewer than CHAT_AGENT_MIN_OPTIONS usable labels)
    leaves nothing behind anywhere else: the extractor returns None, _serialize_function_calls
    skips it as server-terminal, and such a response carries no text part. So it used to reach
    the caller as "empty" — the transient-blip classification — and earn a retry against an
    unchanged input, which reproduces the same call and burns a second Gemini turn before
    degrading to "Sorry, I couldn't generate a response".

    Observed in prod 2026-07-31 with finish_reason=STOP: the model finished normally, the server
    just could not use a valid answer. Salvaging the prompt turns that into a real reply — a
    question without tappable chips still answers the user.

    Returns "" when there is no present_options call or it wrote no prompt: the salvage may only
    surface text the model actually produced, never invent one.
    """
    for part in parts or []:
        fc = getattr(part, "function_call", None)
        if fc is None or getattr(fc, "name", None) != "present_options":
            continue
        return (str((dict(fc.args or {})).get("prompt") or "")).strip()
    return ""


def _extract_final_text(response, parts) -> str:
    """Join text parts into the final assistant message."""
    text_parts = [p.text for p in (parts or []) if getattr(p, "text", None)]
    if text_parts:
        return "\n".join(text_parts).strip()
    # No function calls were present, so response.text is safe to read.
    return (getattr(response, "text", None) or "").strip()


def _interpret_agent_response(response):
    """Classify a chat_agent Gemini response into its single terminal outcome.

    Returns a (kind, payload) tuple, kind being one of:
      "options"    -> payload = {"prompt": str, "options": [...]}  (model called present_options)
      "tool_calls" -> payload = [ {id, name, args}, ... ]          (client-dispatched tool calls)
      "final"      -> payload = str                                (final assistant text)
      "empty"      -> payload = finish_reason | None               (no options, no tools, no text)

    "empty" is the transient safety-filter / empty-completion blip the caller retries once and
    then degrades gracefully on — see chat_agent. Extraction order mirrors the model's own
    precedence (present_options is server-terminal, then tool calls, then text).
    """
    candidate = (response.candidates or [None])[0]
    parts = candidate.content.parts if (candidate and candidate.content) else []

    present = _extract_present_options(parts)
    if present is not None:
        return ("options", present)

    tool_calls = _serialize_function_calls(parts)
    if tool_calls:
        return ("tool_calls", tool_calls)

    content = _extract_final_text(response, parts)
    if content:
        return ("final", content)

    # Before calling it empty: a present_options the extractor rejected is invisible to every
    # branch above, so it would be retried as a transient blip even though the input is
    # unchanged and the outcome deterministic. Surface its prompt instead when it wrote one.
    salvaged = _salvage_present_options_prompt(parts)
    if salvaged:
        return ("final", salvaged)

    return ("empty", getattr(candidate, "finish_reason", None))


@functions_framework.http
def chat_agent(request: Request):
    """
    Agentic chat — stateless ping-pong bridge (Phase 2).

    Request body:
    {
        "user_id": "string",
        "request_id": "uuid",                   # optional — stable per TURN across transport
                                                # retries; makes the reserve idempotent.
                                                # Omitted -> legacy non-deduped reserve.
        "locale": "ru" | "en",
        "timezone_offset_minutes": -720..840,
        "checklists_summary": [{"name": "...", "totalItems": N, "doneItems": N}, ...],
        "context_checklist": {"name": "..."},   # optional — the checklist the user is viewing
        "context_screen": {                     # optional — the v2 shell TAB the user is on.
            # Today's client only ever sends "inbox" or "agenda", and only on the FIRST round of a
            # turn (privacy: see ChatViewModel.buildScreenSnapshot). The "projects"/"overview"
            # branches of _build_screen_context_block are reachable only if a future client starts
            # sending them — tuning them changes nothing until it does.
            "kind": "inbox" | "agenda" | "projects" | "overview",
            "label": "...",                     # inbox name / formatted day
            "focused_date": "2026-08-07",       # agenda only
            "items": [{"text": "...", "checked": false, "list": "...", "due": "...",
                       "has_reminder": false, "has_attachment": false}, ...],
            "total_items": N                    # REAL total; items[] may be truncated
        },                                      # never sent together with context_checklist
        "transcript": [
            {"role": "user",  "text": "..."},
            {"role": "model", "tool_calls":   [{"id","name","args"}, ...]},
            {"role": "tool",  "tool_results": [{"id","name","result"}, ...]}
        ]
    }

    Response:
        200 -> {"success": true, "type": "tool_calls", "tool_calls": [...], "credits_remaining": int}
            -> {"success": true, "type": "final",      "content": "...",   "credits_remaining": int}
            -> {"success": true, "type": "options",     "prompt": "...", "options": ["...", ...], "credits_remaining": int}
                 (model called present_options — client renders tappable choice chips)
        400 -> invalid payload
        402 -> insufficient credits (first round only)
        500 -> Gemini failure (credits refunded if reserved this round)

    Credits: CHAT_AGENT_COST reserved ONCE per turn, on the first round only — i.e. when no
    `tool` turn has come back AFTER the last `user` entry. Subsequent rounds of the same turn
    reserve 0. The boundary is the CURRENT turn, not the whole transcript: a persisted
    transcript carries past turns' tool entries, and reading those as "mid-turn" would make
    every turn of the session free. Refund on Gemini failure only when this call actually
    deducted (never on an idempotent replay).
    """
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    data, error = validate_request(request)
    if error is not None:
        return create_error_response(error, 400)

    user_id = (data.get("user_id") or "").strip()
    if not user_id:
        return create_error_response("user_id is required", 400)

    # Model resolution: production A/B experiment (server-driven) + eval override precedence.
    model_id, model_arm = resolve_experiment_model(user_id, "chat_agent", CHAT_AGENT_MODEL, data)
    exp_meta = {"model_variant": model_arm, "model_id": model_id, "ai_flow": "chat_agent"}

    transcript = data.get("transcript")
    if not isinstance(transcript, list) or not transcript:
        return create_error_response("transcript must be a non-empty list", 400)
    if len(transcript) > CHAT_AGENT_MAX_TRANSCRIPT_ENTRIES:
        return create_error_response(
            f"transcript too long (max {CHAT_AGENT_MAX_TRANSCRIPT_ENTRIES} entries)", 400
        )

    # Validation + size guard — sum of all user text (tool results can be large but are
    # machine data, so we only cap human-authored text here).
    total_chars = 0
    for entry in transcript:
        if not isinstance(entry, dict):
            return create_error_response("each transcript entry must be an object", 400)
        role = (entry.get("role") or "").strip().lower()
        if role not in ("user", "model", "tool"):
            return create_error_response("transcript role must be user/model/tool", 400)
        if role == "user":
            total_chars += len(entry.get("text") or "")
    if total_chars > CHAT_AGENT_MAX_TOTAL_CHARS:
        return create_error_response(
            f"transcript text exceeds {CHAT_AGENT_MAX_TOTAL_CHARS} chars cap", 400
        )

    # Both numbers are scoped to the CURRENT turn — everything after the last role="user"
    # entry. Measuring them over the whole array is only equivalent while the transcript is
    # rebuilt from message text each turn (today's store clients); with a persisted transcript
    # past turns arrive WITH their tool entries, and whole-array readings break in two ways:
    # every turn of a session that ever ran a tool would look mid-turn => never charged, and
    # 5 tool rounds accumulated over a session's lifetime would trip the per-TURN round cap on
    # every new message. See chat_agent_logic.scan_current_turn.
    #
    # agent_round_count counts only model turns that requested tools. Prior assistant prose
    # (role="model" text, no tool_calls) is seeded as conversation context so the agent can
    # resolve referential confirmations ("да, добавь все") — it is not a round.
    is_first_round, agent_round_count = scan_current_turn(transcript)

    locale = (data.get("locale") or "en").strip().lower()
    if locale not in ("ru", "en"):
        locale = "en"

    # Optional + additive: explicit response-language override (see chat_completion).
    response_language = _normalise_response_language(data.get("response_language"))

    try:
        tz_offset_minutes = int(data.get("timezone_offset_minutes") or 0)
    except (TypeError, ValueError):
        tz_offset_minutes = 0
    tz_offset_minutes = max(-720, min(840, tz_offset_minutes))

    checklists_raw = data.get("checklists_summary") or []
    checklists_summary_text = _format_checklists_summary(checklists_raw)

    # Defense-in-depth round cap: count only the AGENTIC rounds (tool-call model turns)
    # of the current turn — conversational history seeded as plain model prose is excluded
    # (see the loop above). If we have hit the ceiling, return a graceful final WITHOUT
    # calling Gemini or charging (a cap is only reached on a later round, which never reserves).
    if agent_round_count >= CHAT_AGENT_MAX_ROUNDS:
        cap_msg = (
            "Я выполнил несколько шагов, но достиг лимита за один запрос. "
            "Напишите, что ещё нужно сделать."
            if locale == "ru" else
            "I completed several steps but reached the per-request limit. "
            "Tell me what else you need."
        )
        return create_success_response({
            **exp_meta,
            "type": "final",
            "content": cap_msg,
            "credits_remaining": get_user_credits(user_id),
        })

    # Credits (D2): charge the flat per-turn cost ONLY on the first round OF THIS TURN.
    #
    # request_id is OPTIONAL and additive. Store clients (Android vc67, web) do not send it and
    # keep the exact legacy path — same function, same cost, same 402. A client that DOES send a
    # stable id per turn gets a replay-safe reserve: retrying a dropped 200 no longer pays twice.
    request_id = (data.get("request_id") or "").strip() or None
    reserved_this_round = False
    if is_first_round:
        if request_id:
            action, new_credits = reserve_chat_agent_credits(user_id, request_id)
        else:
            new_credits = reserve_chat_completion_credits(user_id)
            action = "reserve" if new_credits is not None else "insufficient"
        if new_credits is None:
            return create_error_response("insufficient credits", 402)
        # Refund only what THIS invocation deducted: a replay deducted nothing, so refunding it
        # would return credits charged by the earlier call AND roll back its dedup doc.
        reserved_this_round = (action == "reserve")
        credits_remaining = new_credits
    else:
        credits_remaining = get_user_credits(user_id)

    # Optional: the checklist the user is currently viewing (detail screen). When present,
    # an omitted checklist_hint should resolve to THIS list, so "add milk" while viewing
    # "Groceries" lands in Groceries. Additive + optional → old store clients that never
    # send `context_checklist` keep the prior home-screen behaviour (no contract break).
    context_raw = data.get("context_checklist")
    context_name = ""
    if isinstance(context_raw, dict):
        context_name = (context_raw.get("name") or "").strip()

    # Optional: the v2 shell TAB the user is on, with the content of the screens the summary above
    # cannot carry (the system Inbox, the day). Additive + optional in exactly the same way as
    # `context_checklist`: clients that do not send it (control arm, older builds, non-tab routes)
    # fall straight through to the legacy else-branch. The client never sends both, and the
    # `if context_name` branch staying FIRST makes that belt-and-braces.
    screen_raw = data.get("context_screen")
    screen = screen_raw if isinstance(screen_raw, dict) else None

    if context_name:
        context_name = context_name[:120]  # clamp — guard against prompt bloat
        context_block = (
            f'Current checklist (the user is viewing it right now): "{context_name}".\n'
            "When the user asks to add / complete / delete / rename items or set reminders "
            "WITHOUT naming a different list, target THIS checklist — pass "
            f'checklist_hint="{context_name}".\n'
            "When the user asks what is missing / what to add / for a progress summary of "
            'this checklist WITHOUT naming another list, call read_checklist(name="'
            f'{context_name}") first — do NOT use find_items for these whole-list questions.'
        )
    elif screen:
        context_block = _build_screen_context_block(screen)
    else:
        context_block = "The user is on the home screen — no specific checklist is open."

    features_block = FEATURE_CATALOG_RU if locale == "ru" else FEATURE_CATALOG_EN
    system_instruction = CHAT_AGENT_SYSTEM_TEMPLATE.format(
        locale=locale,
        now_utc=datetime.now(timezone.utc).isoformat(timespec="seconds"),
        tz_offset=tz_offset_minutes,
        checklists_count=min(len(checklists_raw), CHAT_AGENT_MAX_CHECKLISTS),
        checklists_summary=checklists_summary_text,
        context_block=context_block,
        features=features_block,
    )
    system_instruction += _language_directive(response_language)

    # Capability gate: only clients that advertise `supports_options` can render type:"options",
    # so only they get the present_options tool. Absent/false → base tools (old-client safe).
    supports_options = data.get("supports_options") is True
    agent_tools = CHAT_AGENT_TOOLS_WITH_OPTIONS if supports_options else CHAT_AGENT_TOOLS

    try:
        contents = _reconstruct_agent_contents(transcript)
        if not contents:
            raise ValueError("transcript produced no usable contents")

        config = types.GenerateContentConfig(
            system_instruction=system_instruction,
            tools=agent_tools,
            # thinking_budget=0 keeps the stable 2.5 arm cheap (no thinking tokens); on 3.x
            # it is accepted for back-compat but does NOT suppress thought signatures — that
            # assumption held only for 2.5 and broke the 3.1 arm's tool loop in prod
            # (400: "Function call is missing a thought_signature"). Signatures are therefore
            # round-tripped through the transcript, not avoided. Do NOT also pass
            # thinking_level here: combining it with thinking_budget is a 400.
            thinking_config=types.ThinkingConfig(thinking_budget=0),
            # We do MANUAL function calling (the client executes tools); disable the
            # SDK's automatic loop so it never tries to invoke anything itself.
            automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
            temperature=0.4,
        )

        def _run_gemini():
            return gemini_client.models.generate_content(
                model=model_id,
                contents=contents,
                config=config,
            )

        response = _run_gemini()
        # present_options is server-terminal (choice chips), then client-dispatched tool calls,
        # then a final text reply — _interpret_agent_response applies that same precedence.
        kind, payload = _interpret_agent_response(response)

        # Empty candidate (no options, no tool calls, no text) is almost always a transient
        # safety-filter / empty-completion blip at temperature 0.4 — retry ONCE before giving up
        # rather than failing the whole turn. (docs/todos/2026-07-22-release-1.18.2-remaining-followups.md #2)
        if kind == "empty":
            logger.warning(
                "chat_agent: empty candidate (finish_reason=%s) user=%s — retrying once",
                payload, user_id[:8],
            )
            response = _run_gemini()
            kind, payload = _interpret_agent_response(response)

        # Still empty after the retry -> graceful degradation. Refund the turn (never bill a
        # non-answer) and return a friendly localized final instead of the old hard 500 the client
        # surfaced as "something went wrong". payload here is the retry's finish_reason.
        if kind == "empty":
            logger.warning(
                "chat_agent: still empty after retry (finish_reason=%s) user=%s — graceful degrade",
                payload, user_id[:8],
            )
            degraded_balance = credits_remaining
            if reserved_this_round:
                reason = "chat_agent_empty_gemini_after_retry"
                if request_id:
                    refund_credits(user_id, CHAT_AGENT_COST, reason, request_id)
                else:
                    refund_chat_completion_credits(user_id, reason=reason)
                # Post-refund balance computed LOCALLY (reserve == refund == CHAT_AGENT_COST,
                # enforced == CHAT_COMPLETION_COST) — NOT a Firestore read: a get_user_credits()
                # here sits inside the try whose except refunds again on reserved_this_round,
                # so a throw after the refund would double-refund. Disarm that except too.
                degraded_balance = credits_remaining + CHAT_AGENT_COST
                reserved_this_round = False
            degrade_msg = (
                "Извините, у меня не получилось сформировать ответ. Попробуйте переформулировать запрос."
                if locale == "ru" else
                "Sorry, I couldn't generate a response. Please try rephrasing your request."
            )
            return create_success_response({
                **exp_meta,
                "type": "final",
                "content": degrade_msg,
                "credits_remaining": degraded_balance,
            })

        # A real answer (options / tool_calls / final) — bill the turn once. All three served
        # outcomes count as one turn; only the empty-degrade path above skips the usage bump.
        try:
            increment_usage(user_id, "chat_agent", "text")
        except Exception:
            pass

        if kind == "options":
            return create_success_response({
                **exp_meta,
                "type": "options",
                "prompt": payload["prompt"],
                "options": payload["options"],
                "credits_remaining": credits_remaining,
            })

        if kind == "tool_calls":
            return create_success_response({
                **exp_meta,
                "type": "tool_calls",
                "tool_calls": payload,
                "credits_remaining": credits_remaining,
            })

        # kind == "final"
        return create_success_response({
            **exp_meta,
            "type": "final",
            "content": payload,
            "credits_remaining": credits_remaining,
        })

    except Exception as e:
        logger.exception("chat_agent: gemini agent step failed for user=%s", user_id[:8])
        # Refund only if WE freshly reserved on this round. Later rounds never charged, and a
        # replay was charged by an earlier invocation — neither has anything to give back.
        if reserved_this_round:
            reason = f"chat_agent_gemini_failure: {type(e).__name__}"
            if request_id:
                # Also drops the credit_reservations doc, so the client's retry re-reserves
                # cleanly instead of replaying a reservation we just rolled back (a free turn).
                refund_credits(user_id, CHAT_AGENT_COST, reason, request_id)
            else:
                refund_chat_completion_credits(user_id, reason=reason)
        return create_error_response("Agent step failed", 500)
