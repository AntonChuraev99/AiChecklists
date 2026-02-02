"""
Firebase Cloud Functions for AI Checklists App.

Functions:
1. register_user - Register or retrieve user by device ID
2. analyze_and_fill_checklist - Auto-fill existing checklist based on user data
3. generate_checklist - Create new checklist from prompt + user data
4. get_usage_stats - Get user's AI usage statistics
5. refill_premium_credits - Daily credits refill for premium users (called by Cloud Scheduler at 12:00 CET)
6. get_credits_info - Get credits configuration and user's current credits

All AI calls go through these functions for usage control and monitoring.
Credits are deducted for all users (including premium). Premium users get daily refill to cap.
"""

import base64
import json
import os
import uuid
from datetime import datetime, timedelta
from typing import Any

import firebase_admin
from firebase_admin import credentials, firestore
from flask import Request, jsonify
import google.generativeai as genai
import functions_framework

# Initialize Firebase Admin
if not firebase_admin._apps:
    firebase_admin.initialize_app()

db = firestore.client()

# Configure Gemini
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")
if GEMINI_API_KEY:
    genai.configure(api_key=GEMINI_API_KEY)

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
    """Create standardized error response."""
    return jsonify({"success": False, "error": message}), status_code


def create_success_response(data: dict):
    """Create standardized success response."""
    return jsonify({"success": True, **data})


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


def check_credits_available(user_id: str, is_premium: bool = False) -> tuple[bool, int, str]:
    """
    Check if user has enough credits for an AI action.

    Premium users also use credits now (they get daily refills instead of unlimited).

    Returns: (is_allowed, remaining_credits, error_message)
    """
    config = get_credits_config()
    credits = get_user_credits(user_id)
    action_cost = config["action_cost"]

    if credits < action_cost:
        if is_premium:
            return False, credits, f"Not enough credits. You have {credits}, but need {action_cost}. Your credits will be refilled at 12:00 CET."
        else:
            return False, credits, f"Not enough credits. You have {credits}, but need {action_cost}. Purchase premium for daily credits refill."

    return True, credits, ""


def deduct_user_credits(user_id: str) -> int:
    """
    Deduct credits for an AI action.
    Returns the new remaining credits count.
    """
    try:
        user_ref = db.collection("users").document(user_id)
        user_data = get_user_data(user_id)

        if user_data is None:
            return 0

        config = get_credits_config()
        current = user_data.get("ai_credits", 0)
        new_count = max(0, current - config["action_cost"])

        user_ref.update({
            "ai_credits": new_count,
            "updated_at": datetime.utcnow().isoformat()
        })

        return new_count
    except Exception:
        return 0


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
    if request.method != "POST":
        return create_error_response("Only POST method is allowed")

    try:
        data = request.get_json()
    except Exception:
        return create_error_response("Invalid JSON body")

    if not data:
        return create_error_response("Request body is required")

    device_id = data.get("device_id")
    if not device_id or not isinstance(device_id, str) or len(device_id) < 10:
        return create_error_response("Valid device_id is required (min 10 characters)")

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
            return create_success_response({
                "user_id": user_doc.id,
                "is_new_user": False,
                "is_premium": user_data.get("is_premium", False),
                "ai_credits": user_data.get("ai_credits", 0),
                "created_at": user_data.get("created_at", "")
            })

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

        return create_success_response({
            "user_id": new_user_id,
            "is_new_user": True,
            "is_premium": False,
            "ai_credits": initial_credits,
            "created_at": now
        })

    except Exception as e:
        return create_error_response(f"Failed to register user: {str(e)}", 500)


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
    # Validate request
    data, error = validate_request(request)
    if error:
        return create_error_response(error)

    user_id = data["user_id"]
    is_premium = data.get("is_premium", False)
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

    # Check if user has enough credits
    allowed, remaining, credits_error = check_credits_available(user_id, is_premium)
    if not allowed:
        return create_error_response(credits_error, 402)  # 402 Payment Required

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
        # Call Gemini API
        model = genai.GenerativeModel("gemini-2.0-flash-lite")

        if input_type == "image_base64":
            image_bytes = base64.b64decode(input_data)
            response = model.generate_content([
                prompt,
                {"mime_type": "image/jpeg", "data": image_bytes}
            ])
        elif input_type == "audio_base64":
            audio_bytes = base64.b64decode(input_data)
            response = model.generate_content([
                prompt,
                {"mime_type": "audio/mp4", "data": audio_bytes}
            ])
        else:
            response = model.generate_content(prompt)

        # Parse response
        response_text = response.text
        # Extract JSON from response
        if "```json" in response_text:
            response_text = response_text.split("```json")[1].split("```")[0]
        elif "```" in response_text:
            response_text = response_text.split("```")[1].split("```")[0]

        result = json.loads(response_text.strip())

        # Deduct credits for all users (premium users also use credits now)
        new_remaining = deduct_user_credits(user_id)

        # Increment usage stats
        usage = increment_usage(user_id, "analyze_and_fill_checklist", input_type)

        return create_success_response({
            "filled_items": result.get("filled_items", []),
            "summary": result.get("summary", ""),
            "confidence": result.get("confidence", 0.8),
            "ai_credits": new_remaining
        })

    except json.JSONDecodeError as e:
        return create_error_response(f"Failed to parse AI response: {str(e)}", 500)
    except Exception as e:
        return create_error_response(f"AI analysis failed: {str(e)}", 500)


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
    # Validate request
    data, error = validate_request(request)
    if error:
        return create_error_response(error)

    user_id = data["user_id"]
    is_premium = data.get("is_premium", False)
    user_prompt = data.get("prompt")
    input_type = data.get("input_type", "none")
    input_data = data.get("input_data", "")

    # Validate required fields
    if not user_prompt:
        return create_error_response("prompt is required")

    # Check feature flag
    if not get_remote_config_value("feature_ai_analysis_enabled", True):
        return create_error_response("AI analysis is currently disabled", 503)

    # Check if user has enough credits
    allowed, remaining, credits_error = check_credits_available(user_id, is_premium)
    if not allowed:
        return create_error_response(credits_error, 402)  # 402 Payment Required

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
        # Call Gemini API
        model = genai.GenerativeModel("gemini-2.0-flash-lite")

        if input_type == "image_base64" and input_data:
            image_bytes = base64.b64decode(input_data)
            response = model.generate_content([
                prompt,
                {"mime_type": "image/jpeg", "data": image_bytes}
            ])
        elif input_type == "audio_base64" and input_data:
            audio_bytes = base64.b64decode(input_data)
            response = model.generate_content([
                prompt,
                {"mime_type": "audio/mp4", "data": audio_bytes}
            ])
        else:
            response = model.generate_content(prompt)

        # Parse response
        response_text = response.text
        # Extract JSON from response
        if "```json" in response_text:
            response_text = response_text.split("```json")[1].split("```")[0]
        elif "```" in response_text:
            response_text = response_text.split("```")[1].split("```")[0]

        result = json.loads(response_text.strip())

        # Deduct credits for all users (premium users also use credits now)
        new_remaining = deduct_user_credits(user_id)

        # Increment usage stats
        usage = increment_usage(user_id, "generate_checklist", input_type)

        return create_success_response({
            "checklist_name": result.get("checklist_name", "New Checklist"),
            "items": result.get("items", []),
            "summary": result.get("summary", ""),
            "confidence": result.get("confidence", 0.8),
            "ai_credits": new_remaining
        })

    except json.JSONDecodeError as e:
        return create_error_response(f"Failed to parse AI response: {str(e)}", 500)
    except Exception as e:
        return create_error_response(f"AI generation failed: {str(e)}", 500)


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
    - For each user, if their credits < premium_daily_credits_cap, set to cap
    - If credits >= cap, don't change (don't accumulate beyond cap)

    Request body (optional, for manual trigger):
    {
        "admin_key": "string (optional admin key for manual trigger)"
    }

    Response:
    {
        "success": true,
        "users_updated": number,
        "users_skipped": number,
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

        for user_doc in premium_users:
            user_data = user_doc.to_dict()
            current_credits = user_data.get("ai_credits", 0)

            if current_credits < credits_cap:
                # Refill to cap
                user_doc.reference.update({
                    "ai_credits": credits_cap,
                    "credits_refilled_at": datetime.utcnow().isoformat(),
                    "updated_at": datetime.utcnow().isoformat()
                })
                users_updated += 1
            else:
                # Already at or above cap, skip
                users_skipped += 1

        # Log the refill operation
        db.collection("credits_refill_log").add({
            "timestamp": datetime.utcnow().isoformat(),
            "users_updated": users_updated,
            "users_skipped": users_skipped,
            "credits_cap": credits_cap
        })

        return create_success_response({
            "users_updated": users_updated,
            "users_skipped": users_skipped,
            "credits_cap": credits_cap
        })

    except Exception as e:
        return create_error_response(f"Failed to refill credits: {str(e)}", 500)


# ============================================================================
# FUNCTION 6: Get credits config (for client to display correct info)
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
