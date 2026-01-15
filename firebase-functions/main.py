"""
Firebase Cloud Functions for AI Checklists App.

Two main functions:
1. analyze_and_fill_checklist - Auto-fill existing checklist based on user data
2. generate_checklist - Create new checklist from prompt + user data

All AI calls go through these functions for usage control and monitoring.
"""

import json
import os
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
# FUNCTION 1: Auto-fill existing checklist
# ============================================================================

FILL_CHECKLIST_PROMPT = """You are an AI assistant that helps fill checklists based on provided data.

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
        "input_type": "text" | "url" | "image_base64",
        "input_data": "string (text content, URL, or base64 image)"
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

    # Check usage limit
    allowed, limit_message = check_usage_limit(user_id, is_premium)
    if not allowed:
        return create_error_response(limit_message, 429)

    # Check input length
    max_length = get_remote_config_value("ai_analysis_max_input_length", DEFAULT_MAX_INPUT_LENGTH)
    if len(input_data) > max_length:
        return create_error_response(f"Input data exceeds maximum length of {max_length} characters")

    # Build prompt
    checklist_items = "\n".join([
        f"{i+1}. {'[x]' if item.get('checked') else '[ ]'} {item['text']}"
        for i, item in enumerate(checklist["items"])
    ])

    prompt = FILL_CHECKLIST_PROMPT.format(
        checklist_items=checklist_items,
        user_data=input_data if input_type != "image_base64" else "[Image data provided]"
    )

    try:
        # Call Gemini API
        model = genai.GenerativeModel("gemini-1.5-flash")

        if input_type == "image_base64":
            import base64
            image_bytes = base64.b64decode(input_data)
            response = model.generate_content([
                prompt,
                {"mime_type": "image/jpeg", "data": image_bytes}
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

        # Increment usage
        usage = increment_usage(user_id, "analyze_and_fill_checklist", input_type)
        limit = DEFAULT_DAILY_LIMIT_PREMIUM if is_premium else DEFAULT_DAILY_LIMIT_FREE

        return create_success_response({
            "filled_items": result.get("filled_items", []),
            "summary": result.get("summary", ""),
            "confidence": result.get("confidence", 0.8),
            "usage": {"count": usage["count"], "limit": limit}
        })

    except json.JSONDecodeError as e:
        return create_error_response(f"Failed to parse AI response: {str(e)}", 500)
    except Exception as e:
        return create_error_response(f"AI analysis failed: {str(e)}", 500)


# ============================================================================
# FUNCTION 2: Generate checklist from prompt + data
# ============================================================================

GENERATE_CHECKLIST_PROMPT = """You are an AI assistant that creates checklists based on user requirements.

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
        "input_type": "text" | "url" | "image_base64" | "none" (optional),
        "input_data": "string (additional context data)" (optional)
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

    # Check usage limit
    allowed, limit_message = check_usage_limit(user_id, is_premium)
    if not allowed:
        return create_error_response(limit_message, 429)

    # Check input length
    max_length = get_remote_config_value("ai_analysis_max_input_length", DEFAULT_MAX_INPUT_LENGTH)
    total_input = user_prompt + (input_data or "")
    if len(total_input) > max_length:
        return create_error_response(f"Input exceeds maximum length of {max_length} characters")

    # Build prompt
    user_data_text = input_data if input_data and input_type != "image_base64" else "No additional data provided"
    if input_type == "image_base64":
        user_data_text = "[Image data provided - analyze the image for context]"

    prompt = GENERATE_CHECKLIST_PROMPT.format(
        user_prompt=user_prompt,
        user_data=user_data_text
    )

    try:
        # Call Gemini API
        model = genai.GenerativeModel("gemini-1.5-flash")

        if input_type == "image_base64" and input_data:
            import base64
            image_bytes = base64.b64decode(input_data)
            response = model.generate_content([
                prompt,
                {"mime_type": "image/jpeg", "data": image_bytes}
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

        # Increment usage
        usage = increment_usage(user_id, "generate_checklist", input_type)
        limit = DEFAULT_DAILY_LIMIT_PREMIUM if is_premium else DEFAULT_DAILY_LIMIT_FREE

        return create_success_response({
            "checklist_name": result.get("checklist_name", "New Checklist"),
            "items": result.get("items", []),
            "summary": result.get("summary", ""),
            "confidence": result.get("confidence", 0.8),
            "usage": {"count": usage["count"], "limit": limit}
        })

    except json.JSONDecodeError as e:
        return create_error_response(f"Failed to parse AI response: {str(e)}", 500)
    except Exception as e:
        return create_error_response(f"AI generation failed: {str(e)}", 500)


# ============================================================================
# FUNCTION 3: Get user usage stats
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
