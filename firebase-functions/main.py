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

import base64
import json
import os
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

import firebase_admin
from firebase_admin import auth as firebase_auth, credentials, firestore
from flask import Request, jsonify, make_response
from google import genai
from google.genai import types
import functions_framework
from firebase_functions import firestore_fn  # 2nd gen Firestore trigger
import requests as http_requests  # avoid conflict with flask Request
from flask import request as flask_request  # global request context for CORS origin echo-back

import cors  # local module: CORS origin whitelist (unit-testable without firebase_admin)
from generated_items import MAX_FOLDER_DEPTH, sanitize_generated_items  # nested AI-item sanitizer (unit-testable)

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
MODEL_OVERRIDE_ALLOWLIST = {
    "gemini-2.5-flash-lite",
    "gemini-2.5-flash",
    "gemini-2.5-pro",
    "gemini-2.0-flash",
    "gemini-2.0-flash-lite",
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

# RevenueCat verification (V1 Secret key, NOT public key)
REVENUECAT_API_KEY = os.environ.get("REVENUECAT_API_KEY")

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
    except Exception:
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
        users = db.collection("users").where("google_uid", "==", firebase_uid).limit(1).get()
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


def reserve_credits(user_id: str) -> int | None:
    """
    Atomically check and deduct credits in a single Firestore transaction.
    Returns new remaining count, or None if insufficient credits.
    """
    config = get_credits_config()
    cost = config["action_cost"]
    user_ref = db.collection("users").document(user_id)

    @firestore.transactional
    def txn(transaction):
        snapshot = user_ref.get(transaction=transaction)
        if not snapshot.exists:
            return None
        current = snapshot.get("ai_credits") or 0
        if current < cost:
            return None
        new_count = current - cost
        transaction.update(user_ref, {
            "ai_credits": new_count,
            "updated_at": datetime.now(timezone.utc).isoformat()
        })
        return new_count

    return txn(db.transaction())


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
        existing_users = users_ref.where("device_id", "==", device_id).limit(1).get()

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
        return add_cors_headers(make_response(
            jsonify({"success": False, "error": f"Failed to register user: {str(e)}"}), 500
        ))


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
        existing = db.collection("users").where("google_uid", "==", firebase_uid).limit(1).get()
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
        return create_error_response(f"Failed to link Google account: {str(e)}", 500)


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

    # Reserve credits atomically (deduct before Gemini call)
    remaining = reserve_credits(user_id)
    if remaining is None:
        cost = get_credits_config()["action_cost"]
        suffix = "Refill at 12:00 CET." if is_premium else "Get premium for daily refill."
        return create_error_response(f"Not enough credits. Need {cost}. {suffix}", 402)

    # Check input length (skip for binary data types)
    if input_type not in ("image_base64", "audio_base64"):
        max_length = get_remote_config_value("ai_analysis_max_input_length", DEFAULT_MAX_INPUT_LENGTH)
        if len(input_data) > max_length:
            return create_error_response(f"Input data exceeds maximum length of {max_length} characters")

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

    try:
        response = call_gemini(prompt, input_type, input_data)
        result = parse_gemini_json(response.text)
    except json.JSONDecodeError:
        return create_error_response("Failed to parse AI response", 500)
    except Exception:
        return create_error_response("AI processing failed. Please try again.", 500)

    # Increment usage stats
    increment_usage(user_id, "analyze_and_fill_checklist", input_type)

    return create_success_response({
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

    # Reserve credits atomically (deduct before Gemini call)
    remaining = reserve_credits(user_id)
    if remaining is None:
        cost = get_credits_config()["action_cost"]
        suffix = "Refill at 12:00 CET." if is_premium else "Get premium for daily refill."
        return create_error_response(f"Not enough credits. Need {cost}. {suffix}", 402)

    # Check input length (skip binary data in length calculation)
    max_length = get_remote_config_value("ai_analysis_max_input_length", DEFAULT_MAX_INPUT_LENGTH)
    if input_type in ("image_base64", "audio_base64"):
        total_input = user_prompt  # Only check prompt length for binary inputs
    else:
        total_input = user_prompt + (input_data or "")
    if len(total_input) > max_length:
        return create_error_response(f"Input exceeds maximum length of {max_length} characters")

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

    try:
        response = call_gemini(prompt, input_type, input_data)
        result = parse_gemini_json(response.text)
    except json.JSONDecodeError:
        return create_error_response("Failed to parse AI response", 500)
    except Exception:
        return create_error_response("AI processing failed. Please try again.", 500)

    # Increment usage stats
    increment_usage(user_id, "generate_checklist", input_type)

    return create_success_response({
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
        premium_users = users_ref.where("is_premium", "==", True).get()

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
        return create_error_response(f"Failed to refill credits: {str(e)}", 500)


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
        return add_cors_headers(make_response(
            jsonify({"success": False, "error": "Failed to restore credits. Please try again."}), 500
        ))


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

    # Test-only model override (gated; see resolve_model). Prod default = flash-lite.
    model_id = resolve_model("gemini-2.5-flash-lite", data.get("model_override"), data.get("test_secret"))

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
            "intent": intent,
            "entities": entities,
            "confidence": confidence,
            "credits_remaining": new_credits,
        })

    except Exception as e:
        # Gemini call or JSON parse failed AFTER reserve_chat_credit deducted 1.
        # Refund the credit so the user is not charged for our failure.
        # Best-effort — refund failure is swallowed; original error is surfaced.
        refund_chat_credit(user_id, reason=f"chat_classifier_gemini_failure: {type(e).__name__}")
        return create_error_response(f"classification failed: {str(e)}", 500)


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
        # Gemini call failed AFTER reserve_chat_credit deducted 1.
        # Refund so the user is not charged for our failure.
        refund_chat_credit(user_id, reason=f"transcribe_audio_gemini_failure: {type(e).__name__}")
        return create_error_response(f"transcription failed: {str(e)}", 500)


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

    # Test-only model override (gated; see resolve_model). Prod default = flash.
    model_id = resolve_model("gemini-2.5-flash", data.get("model_override"), data.get("test_secret"))

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

    try:
        content = _call_gemini_flash(prompt, model_id=model_id)
        if not content:
            raise ValueError("Gemini returned empty response")

        try:
            increment_usage(user_id, "chat_completion", "text")
        except Exception:
            pass

        return create_success_response({
            "content": content,
            "credits_remaining": new_credits,
        })

    except Exception as e:
        refund_chat_completion_credits(
            user_id,
            reason=f"chat_completion_gemini_failure: {type(e).__name__}"
        )
        return create_error_response(f"completion failed: {str(e)}", 500)


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

# Tool names the agent may emit. The client's ToolCallDispatcher MUST be able to
# execute every name here (server catalog == client capability). Phase 2 core set
# — closes findings #1/#2. Phase 3/5 extend this list as the dispatcher grows.
CHAT_AGENT_TOOL_NAMES = {
    "add_item", "add_items", "create_checklist", "complete_item",
    "delete_item", "set_item_reminder", "find_items", "read_checklist",
    "rename_checklist",
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
                "Offer the user 2-4 short tappable options instead of guessing or asking an "
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
                    "options": s(ARR, "2-4 concise option labels in the user's language.", items=s(STR)),
                },
                required=["prompt", "options"],
            ),
        ))
    return [types.Tool(function_declarations=declarations)]


# Built once — function declarations are static and locale-independent. Two variants:
# the base set, and one with present_options for clients that advertise `supports_options`.
CHAT_AGENT_TOOLS = _build_chat_agent_tools(include_options=False)
CHAT_AGENT_TOOLS_WITH_OPTIONS = _build_chat_agent_tools(include_options=True)




def _coerce_response_dict(result: Any) -> dict:
    """A Gemini function_response payload MUST be a dict. Wrap scalars/lists."""
    if isinstance(result, dict):
        return result
    return {"result": result}


def _reconstruct_agent_contents(transcript: list) -> "list[types.Content]":
    """Rebuild Gemini `contents` from the client's structured transcript.

    user            -> Content(role=user,  parts=[text])
    model.tool_calls-> Content(role=model, parts=[function_call ...])
    tool.tool_results-> Content(role=user, parts=[function_response ...])   (role IS user)
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
                parts.append(types.Part(function_call=types.FunctionCall(
                    id=tc.get("id"),
                    name=tc["name"],
                    args=dict(tc.get("args") or {}),
                )))
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
    """Extract function_call parts into the client-facing {id, name, args} list.

    `present_options` is excluded — it is a SERVER-TERMINAL tool intercepted upstream
    (the client renders chips, it is never dispatched via ToolCallDispatcher). Skipping
    it here means a malformed present_options call can't leak as an undispatchable tool.
    """
    calls = []
    for i, part in enumerate(parts or []):
        fc = getattr(part, "function_call", None)
        if fc is None or not getattr(fc, "name", None) or fc.name == "present_options":
            continue
        calls.append({
            "id": fc.id or f"call_{i}",
            "name": fc.name,
            "args": dict(fc.args or {}),
        })
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
            if len(options) >= 4:
                break
        if prompt and len(options) >= 2:
            return {"prompt": prompt, "options": options}
        return None
    return None


def _extract_final_text(response, parts) -> str:
    """Join text parts into the final assistant message."""
    text_parts = [p.text for p in (parts or []) if getattr(p, "text", None)]
    if text_parts:
        return "\n".join(text_parts).strip()
    # No function calls were present, so response.text is safe to read.
    return (getattr(response, "text", None) or "").strip()


@functions_framework.http
def chat_agent(request: Request):
    """
    Agentic chat — stateless ping-pong bridge (Phase 2).

    Request body:
    {
        "user_id": "string",
        "locale": "ru" | "en",
        "timezone_offset_minutes": -720..840,
        "checklists_summary": [{"name": "...", "totalItems": N, "doneItems": N}, ...],
        "context_checklist": {"name": "..."},   # optional — the checklist the user is viewing
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

    Credits: CHAT_AGENT_COST reserved ONCE per turn, on the first round only
    (transcript has no `tool` turn yet). Subsequent rounds reserve 0.
    """
    if request.method == "OPTIONS":
        return cors_preflight_ok()

    data, error = validate_request(request)
    if error is not None:
        return create_error_response(error, 400)

    user_id = (data.get("user_id") or "").strip()
    if not user_id:
        return create_error_response("user_id is required", 400)

    # Test-only model override (gated; see resolve_model). Prod default = CHAT_AGENT_MODEL.
    model_id = resolve_model(CHAT_AGENT_MODEL, data.get("model_override"), data.get("test_secret"))

    transcript = data.get("transcript")
    if not isinstance(transcript, list) or not transcript:
        return create_error_response("transcript must be a non-empty list", 400)
    if len(transcript) > CHAT_AGENT_MAX_TRANSCRIPT_ENTRIES:
        return create_error_response(
            f"transcript too long (max {CHAT_AGENT_MAX_TRANSCRIPT_ENTRIES} entries)", 400
        )

    # Size guard — sum of all user/model text (tool results can be large but are
    # machine data, so we only cap human-authored text here).
    total_chars = 0
    has_tool_turn = False
    # Counts only AGENTIC rounds (model turns that requested tools), NOT conversational
    # history seeded as plain model prose — see the per-request cap below for why.
    agent_round_count = 0
    for entry in transcript:
        if not isinstance(entry, dict):
            return create_error_response("each transcript entry must be an object", 400)
        role = (entry.get("role") or "").strip().lower()
        if role not in ("user", "model", "tool"):
            return create_error_response("transcript role must be user/model/tool", 400)
        if role == "user":
            total_chars += len(entry.get("text") or "")
        elif role == "model":
            # Only model turns that requested tools (have a tool_calls list) are real
            # agentic rounds. The client also seeds prior assistant prose as role="model"
            # text (ModelText, no tool_calls) so the agent has conversation context for
            # referential confirmations ("да, добавь все"). Counting that history would
            # trip the cap on every new message once a chat has CHAT_AGENT_MAX_ROUNDS+
            # assistant replies — returning the round-limit message before Gemini is even
            # called. So count only tool-call rounds here.
            if entry.get("tool_calls"):
                agent_round_count += 1
        elif role == "tool":
            has_tool_turn = True
    if total_chars > CHAT_AGENT_MAX_TOTAL_CHARS:
        return create_error_response(
            f"transcript text exceeds {CHAT_AGENT_MAX_TOTAL_CHARS} chars cap", 400
        )

    locale = (data.get("locale") or "en").strip().lower()
    if locale not in ("ru", "en"):
        locale = "en"

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
            "type": "final",
            "content": cap_msg,
            "credits_remaining": get_user_credits(user_id),
        })

    # Credits (D2): charge the flat per-turn cost ONLY on the first round.
    is_first_round = not has_tool_turn
    reserved_this_round = False
    if is_first_round:
        new_credits = reserve_chat_completion_credits(user_id)
        if new_credits is None:
            return create_error_response("insufficient credits", 402)
        reserved_this_round = True
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
            # thinking_budget=0 → no thought_signature blobs to round-trip through
            # the stateless transcript (see plan §6). Verified on the stable model.
            thinking_config=types.ThinkingConfig(thinking_budget=0),
            # We do MANUAL function calling (the client executes tools); disable the
            # SDK's automatic loop so it never tries to invoke anything itself.
            automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
            temperature=0.4,
        )

        response = gemini_client.models.generate_content(
            model=model_id,
            contents=contents,
            config=config,
        )

        candidate = (response.candidates or [None])[0]
        parts = candidate.content.parts if (candidate and candidate.content) else []

        # present_options is server-terminal: if the model offered the user a choice, return
        # it as type:"options" (the client renders chips) BEFORE serializing client-dispatched
        # tool calls. Mirrors the tool_calls / final usage bump.
        present = _extract_present_options(parts)
        if present is not None:
            try:
                increment_usage(user_id, "chat_agent", "text")
            except Exception:
                pass
            return create_success_response({
                "type": "options",
                "prompt": present["prompt"],
                "options": present["options"],
                "credits_remaining": credits_remaining,
            })

        tool_calls = _serialize_function_calls(parts)

        try:
            increment_usage(user_id, "chat_agent", "text")
        except Exception:
            pass

        if tool_calls:
            return create_success_response({
                "type": "tool_calls",
                "tool_calls": tool_calls,
                "credits_remaining": credits_remaining,
            })

        content = _extract_final_text(response, parts)
        if not content:
            raise ValueError("Gemini returned neither tool calls nor text")

        return create_success_response({
            "type": "final",
            "content": content,
            "credits_remaining": credits_remaining,
        })

    except Exception as e:
        # Refund only if WE reserved on this round (first round). Later rounds
        # never charged, so there is nothing to refund.
        if reserved_this_round:
            refund_chat_completion_credits(
                user_id, reason=f"chat_agent_gemini_failure: {type(e).__name__}"
            )
        return create_error_response(f"agent step failed: {str(e)}", 500)
