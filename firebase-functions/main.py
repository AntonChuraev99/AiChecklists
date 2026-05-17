"""
Firebase Cloud Functions for AI Checklists App.

Functions:
1. register_user - Register or retrieve user by device ID
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
from firebase_admin import credentials, firestore
from flask import Request, jsonify, make_response
import google.generativeai as genai
import functions_framework
from firebase_functions import firestore_fn  # 2nd gen Firestore trigger
import requests as http_requests  # avoid conflict with flask Request

# Initialize Firebase Admin
if not firebase_admin._apps:
    firebase_admin.initialize_app()

db = firestore.client()

# Configure Gemini
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")
if GEMINI_API_KEY:
    genai.configure(api_key=GEMINI_API_KEY)

# RevenueCat verification (V1 Secret key, NOT public key)
REVENUECAT_API_KEY = os.environ.get("REVENUECAT_API_KEY")

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


_CORS_HEADERS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Max-Age": "3600",
}


def add_cors_headers(response):
    """Add CORS headers to a Flask response so browsers can call the function from any origin."""
    for key, value in _CORS_HEADERS.items():
        response.headers[key] = value
    return response


def cors_preflight_ok():
    """Return 204 No Content for CORS preflight OPTIONS requests."""
    return add_cors_headers(make_response("", 204))


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

def call_gemini(prompt: str, input_type: str, input_data: str):
    """Call Gemini API with appropriate content type."""
    model = genai.GenerativeModel("gemini-2.5-flash-lite")
    if input_type == "image_base64" and input_data:
        return model.generate_content([
            prompt, {"mime_type": "image/jpeg", "data": base64.b64decode(input_data)}
        ])
    if input_type == "audio_base64" and input_data:
        return model.generate_content([
            prompt, {"mime_type": "audio/mp4", "data": base64.b64decode(input_data)}
        ])
    return model.generate_content(prompt)


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
# FUNCTION 2: Auto-fill existing checklist
# ============================================================================

FILL_CHECKLIST_PROMPT = """You are an AI assistant that helps fill checklists based on provided data.

LANGUAGE: Detect the language of USER DATA. The "note" and "summary" fields MUST be in that detected language.
- Match checklist items SEMANTICALLY across languages (e.g., Russian item "Проверить окна" matches English input "windows look good")
- If USER DATA has no text, use the language of checklist items

The user has a checklist with the following items that need to be filled:
{checklist_items}

Based on the following data provided by the user, determine which items should be checked (completed) and add relevant notes:

USER DATA:
{user_data}

For each checklist item, analyze if the data indicates it should be checked off, and extract any relevant details as notes.

Respond in JSON format:
{{
  "filled_items": [
    {{
      "index": 0,
      "text": "original item text",
      "checked": true/false,
      "note": "relevant note from data or null"
    }}
  ],
  "summary": "brief summary of what was found",
  "confidence": 0.0-1.0
}}

Be precise and only mark items as checked if the data clearly supports it."""


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

GENERATE_CHECKLIST_PROMPT = """You are an AI assistant that creates checklists based on user requirements.

LANGUAGE: Detect the language of USER DATA. ALL output (checklist_name, items, summary) MUST be in that detected language.
- If USER DATA has mixed languages, use the DOMINANT language (>50% of content)
- If USER DATA is empty or non-textual, use the language of USER PROMPT
- If both are non-textual, use English as fallback

USER PROMPT:
{user_prompt}

USER DATA:
{user_data}

Based on the prompt and data, create a comprehensive checklist. Extract actionable items and organize them logically.

Respond in JSON format:
{{
  "checklist_name": "suggested name for the checklist",
  "items": [
    {{"text": "item description", "checked": false}},
    ...
  ],
  "summary": "brief description of what this checklist covers",
  "confidence": 0.0-1.0
}}

Create practical, actionable checklist items. Keep items concise but clear."""


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
        user_data=user_data_text
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
        "items": result.get("items", []),
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
CLASSIFY_CHAT_INTENT_PROMPT = """You are an intent classifier for a checklist app named Gisti.

Detect what the user wants to do. Return ONLY a JSON object (no markdown, no commentary).

Schema:
{
  "intent": one of [
    "create_item",        # add a new item to a checklist
    "delete_item",        # remove an item from a checklist
    "complete_item",      # mark an item as done
    "create_checklist",   # create a new checklist
    "set_reminder",       # set a reminder on an item
    "move_reminders",     # bulk move all reminders from one day to another
    "find_items",         # search items across checklists
    "free_form",          # open question, planning, summarisation
    "unknown"             # cannot determine intent
  ],
  "entities": {
    "itemText": "the item name (for create_item/delete_item/complete_item/set_reminder) or null",
    "checklistHint": "the target checklist name hint (e.g. \\"shopping\\") or null",
    "checklistName": "name for the new checklist (only for create_checklist) or null",
    "dateIso": "ISO-8601 date for set_reminder or null",
    "query": "search query for find_items or null"
  },
  "confidence": 0.0-1.0
}

Rules:
- Match the user's language. Russian, English and mixed input are all valid.
- "В чек-лист X добавь Y" or "Add Y to X" both mean intent=create_item, itemText=Y, checklistHint=X.
- "Перенеси все напоминания с понедельника на среду" → move_reminders, dateIso=null (server resolves dates).
- If a phrase is genuinely ambiguous, prefer "unknown" with confidence < 0.5 over guessing.
- If the user asks a free-form question (planning, summary), set intent=free_form.

User locale: {locale}
User input: "{text}"
"""


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

    user_id = data["user_id"]

    # Server is authoritative for credit accounting — client cannot bypass.
    # Deduct BEFORE the AI call so concurrent requests can't oversell.
    new_credits = reserve_chat_credit(user_id)
    if new_credits is None:
        return create_error_response("insufficient credits", 402)

    prompt = CLASSIFY_CHAT_INTENT_PROMPT.format(locale=locale, text=text)

    try:
        response = call_gemini(prompt, "text", "")
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
        # Credit was already deducted in reserve_chat_credit. We accept this loss
        # for Phase B MVP simplicity — Gemini failures are rare and a single
        # credit refund is not worth the complexity of a compensating transaction.
        # Pending: docs/todos/2026-05-17-ai-chat-layer-2-cloud-classifier.md
        return create_error_response(f"classification failed: {str(e)}", 500)
