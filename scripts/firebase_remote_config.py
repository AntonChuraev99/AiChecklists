#!/usr/bin/env python3
"""
Firebase Remote Config management script.
Usage: python scripts/firebase_remote_config.py [get|set|publish]
"""

import json
import sys
from pathlib import Path

from google.oauth2 import service_account
from google.auth.transport.requests import Request
import requests

# Configuration
PROJECT_ID = "aichecklists-40230"
SERVICE_ACCOUNT_FILE = Path(__file__).parent.parent / "aichecklists-40230-firebase-adminsdk-fbsvc-acc76859ba.json"
REMOTE_CONFIG_URL = f"https://firebaseremoteconfig.googleapis.com/v1/projects/{PROJECT_ID}/remoteConfig"
SCOPES = ["https://www.googleapis.com/auth/firebase.remoteconfig"]

# Default Remote Config parameters for the app
DEFAULT_PARAMETERS = {
    "feature_ai_analysis_enabled": {
        "defaultValue": {"value": "true"},
        "description": "Enable AI analysis feature for generating checklists",
        "valueType": "BOOLEAN"
    },
    "feature_paywall_enabled": {
        "defaultValue": {"value": "false"},
        "description": "Enable paywall for premium features",
        "valueType": "BOOLEAN"
    },
    "max_checklist_items": {
        "defaultValue": {"value": "100"},
        "description": "Maximum number of items per checklist",
        "valueType": "NUMBER"
    },
    "ai_analysis_max_input_length": {
        "defaultValue": {"value": "10000"},
        "description": "Maximum input length for AI analysis (characters)",
        "valueType": "NUMBER"
    },
    "min_app_version": {
        "defaultValue": {"value": "1.0.0"},
        "description": "Minimum supported app version",
        "valueType": "STRING"
    },
    "maintenance_mode": {
        "defaultValue": {"value": "false"},
        "description": "Enable maintenance mode to show maintenance screen",
        "valueType": "BOOLEAN"
    }
}


def get_access_token():
    """Get OAuth2 access token using service account."""
    credentials = service_account.Credentials.from_service_account_file(
        SERVICE_ACCOUNT_FILE, scopes=SCOPES
    )
    credentials.refresh(Request())
    return credentials.token


def get_current_config():
    """Fetch current Remote Config from Firebase."""
    token = get_access_token()
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept-Encoding": "gzip",
    }
    response = requests.get(REMOTE_CONFIG_URL, headers=headers)
    if response.status_code == 200:
        return response.json(), response.headers.get("ETag")
    else:
        print(f"Error fetching config: {response.status_code}")
        print(response.text)
        return None, None


def publish_config(config, etag):
    """Publish new Remote Config to Firebase."""
    token = get_access_token()
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json; UTF-8",
        "If-Match": etag if etag else "*",
    }
    response = requests.put(REMOTE_CONFIG_URL, headers=headers, json=config)
    if response.status_code == 200:
        print("Remote Config published successfully!")
        return True
    else:
        print(f"Error publishing config: {response.status_code}")
        print(response.text)
        return False


def setup_default_config():
    """Set up default Remote Config parameters."""
    print("Fetching current Remote Config...")
    current_config, etag = get_current_config()

    if current_config is None:
        print("Creating new Remote Config...")
        current_config = {"parameters": {}}
        etag = "*"

    # Merge with existing parameters (keep existing, add new)
    existing_params = current_config.get("parameters", {})

    print("\nCurrent parameters:")
    for key in existing_params:
        print(f"  - {key}")

    print("\nAdding/updating parameters:")
    for key, value in DEFAULT_PARAMETERS.items():
        if key not in existing_params:
            print(f"  + Adding: {key}")
        else:
            print(f"  ~ Updating: {key}")
        existing_params[key] = value

    current_config["parameters"] = existing_params

    print("\nPublishing to Firebase...")
    return publish_config(current_config, etag)


def show_config():
    """Display current Remote Config."""
    config, _ = get_current_config()
    if config:
        print("\n=== Current Firebase Remote Config ===\n")
        params = config.get("parameters", {})
        if not params:
            print("No parameters configured.")
        else:
            for key, value in params.items():
                default = value.get("defaultValue", {}).get("value", "N/A")
                desc = value.get("description", "")
                vtype = value.get("valueType", "STRING")
                print(f"{key}:")
                print(f"  Value: {default}")
                print(f"  Type: {vtype}")
                if desc:
                    print(f"  Description: {desc}")
                print()


def main():
    if not SERVICE_ACCOUNT_FILE.exists():
        print(f"Error: Service account file not found: {SERVICE_ACCOUNT_FILE}")
        sys.exit(1)

    if len(sys.argv) < 2:
        print("Usage: python firebase_remote_config.py [get|setup]")
        print("  get   - Show current Remote Config")
        print("  setup - Set up default parameters")
        sys.exit(1)

    command = sys.argv[1].lower()

    if command == "get":
        show_config()
    elif command == "setup":
        setup_default_config()
    else:
        print(f"Unknown command: {command}")
        sys.exit(1)


if __name__ == "__main__":
    main()
