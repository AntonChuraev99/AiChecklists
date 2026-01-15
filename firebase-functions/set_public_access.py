#!/usr/bin/env python3
"""Set public access for Cloud Functions."""

from pathlib import Path
from google.oauth2 import service_account
from google.auth.transport.requests import Request
import requests

PROJECT_ID = "aichecklists-40230"
REGION = "us-central1"
SERVICE_ACCOUNT_FILE = Path(__file__).parent.parent / "aichecklists-40230-firebase-adminsdk-fbsvc-acc76859ba.json"

SCOPES = ["https://www.googleapis.com/auth/cloud-platform"]

FUNCTIONS = [
    "register_user",
    "analyze_and_fill_checklist",
    "generate_checklist",
    "get_usage_stats",
]


def get_access_token():
    credentials = service_account.Credentials.from_service_account_file(
        SERVICE_ACCOUNT_FILE, scopes=SCOPES
    )
    credentials.refresh(Request())
    return credentials.token


def set_invoker_permission(function_name: str, token: str):
    """Set Cloud Run invoker permission for allUsers."""
    # Get the Cloud Run service name (functions gen2 run on Cloud Run)
    service_name = function_name.replace("_", "-")

    # Set IAM policy on Cloud Run service
    run_url = f"https://run.googleapis.com/v2/projects/{PROJECT_ID}/locations/{REGION}/services/{service_name}:setIamPolicy"

    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }

    policy = {
        "policy": {
            "bindings": [
                {
                    "role": "roles/run.invoker",
                    "members": ["allUsers"]
                }
            ]
        }
    }

    print(f"Setting public access for {function_name}...")
    response = requests.post(run_url, headers=headers, json=policy)

    if response.status_code == 200:
        print(f"  [OK] Public access enabled")
        return True
    else:
        print(f"  [FAIL] {response.status_code}: {response.text[:200]}")
        return False


def main():
    print("Setting public access for Cloud Functions...")
    print("=" * 50)

    token = get_access_token()

    for func in FUNCTIONS:
        set_invoker_permission(func, token)

    print("\nDone!")


if __name__ == "__main__":
    main()
