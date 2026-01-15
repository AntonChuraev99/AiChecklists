#!/usr/bin/env python3
"""
Deploy Firebase Cloud Functions via Google Cloud API.
Uses the service account for authentication.
"""

import json
import os
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

from google.oauth2 import service_account
from google.auth.transport.requests import Request
import requests

# Configuration
PROJECT_ID = "aichecklists-40230"
REGION = "us-central1"
SERVICE_ACCOUNT_FILE = Path(__file__).parent.parent / "aichecklists-40230-firebase-adminsdk-fbsvc-acc76859ba.json"

# Cloud Functions API
FUNCTIONS_API = f"https://cloudfunctions.googleapis.com/v2/projects/{PROJECT_ID}/locations/{REGION}/functions"
STORAGE_API = "https://storage.googleapis.com/upload/storage/v1"
BUCKET_NAME = f"{PROJECT_ID}-functions-source"

SCOPES = [
    "https://www.googleapis.com/auth/cloud-platform",
    "https://www.googleapis.com/auth/cloudfunctions",
    "https://www.googleapis.com/auth/devstorage.full_control",
]

# Functions to deploy
FUNCTIONS = [
    {
        "name": "analyze_and_fill_checklist",
        "entry_point": "analyze_and_fill_checklist",
        "memory": "512M",
        "timeout": "60s",
    },
    {
        "name": "generate_checklist",
        "entry_point": "generate_checklist",
        "memory": "512M",
        "timeout": "60s",
    },
    {
        "name": "get_usage_stats",
        "entry_point": "get_usage_stats",
        "memory": "256M",
        "timeout": "30s",
    },
]


def get_access_token():
    """Get OAuth2 access token using service account."""
    credentials = service_account.Credentials.from_service_account_file(
        SERVICE_ACCOUNT_FILE, scopes=SCOPES
    )
    credentials.refresh(Request())
    return credentials.token


def create_source_zip():
    """Create a zip file of the function source code."""
    source_dir = Path(__file__).parent
    zip_path = tempfile.mktemp(suffix=".zip")

    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        # Add main.py
        zipf.write(source_dir / "main.py", "main.py")
        # Add requirements.txt
        zipf.write(source_dir / "requirements.txt", "requirements.txt")

    print(f"Created source zip: {zip_path}")
    return zip_path


def upload_source_to_gcs(zip_path: str, token: str) -> str:
    """Upload source zip to Cloud Storage and return the URL."""
    import time
    object_name = f"function-source-{int(time.time())}.zip"

    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    # First, try to create the bucket
    bucket_url = f"https://storage.googleapis.com/storage/v1/b?project={PROJECT_ID}"
    bucket_data = {
        "name": BUCKET_NAME,
        "location": REGION.split("-")[0].upper() + "-" + REGION.split("-")[1].upper(),  # US-CENTRAL1 -> US
    }

    print(f"Creating bucket {BUCKET_NAME}...")
    bucket_response = requests.post(bucket_url, headers=headers, json=bucket_data)
    if bucket_response.status_code == 200:
        print(f"  Bucket created: {BUCKET_NAME}")
    elif bucket_response.status_code == 409:
        print(f"  Bucket already exists: {BUCKET_NAME}")
    else:
        print(f"  Bucket creation response: {bucket_response.status_code}")
        # Try with simpler location
        bucket_data["location"] = "US"
        bucket_response = requests.post(bucket_url, headers=headers, json=bucket_data)
        if bucket_response.status_code in [200, 409]:
            print(f"  Bucket ready: {BUCKET_NAME}")
        else:
            print(f"  Warning: Could not create bucket: {bucket_response.text}")

    # Upload the zip file
    upload_url = f"{STORAGE_API}/b/{BUCKET_NAME}/o?uploadType=media&name={object_name}"

    with open(zip_path, 'rb') as f:
        response = requests.post(
            upload_url,
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/zip",
            },
            data=f.read()
        )

    if response.status_code not in [200, 201]:
        print(f"Failed to upload source: {response.status_code}")
        print(response.text)
        raise Exception("Failed to upload source code")

    source_url = f"gs://{BUCKET_NAME}/{object_name}"
    print(f"Uploaded source to: {source_url}")
    return source_url


def get_gemini_api_key():
    """Get Gemini API key from environment or local.properties."""
    # Try environment variable first
    api_key = os.environ.get("GEMINI_API_KEY")
    if api_key:
        return api_key

    # Try local.properties
    local_props = Path(__file__).parent.parent / "local.properties"
    if local_props.exists():
        with open(local_props) as f:
            for line in f:
                if line.startswith("GEMINI_API_KEY="):
                    return line.split("=", 1)[1].strip()

    return None


def deploy_function(function_config: dict, source_url: str, token: str, gemini_key: str):
    """Deploy a single Cloud Function."""
    function_name = function_config["name"]
    full_name = f"projects/{PROJECT_ID}/locations/{REGION}/functions/{function_name}"

    print(f"\nDeploying {function_name}...")

    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }

    # Function configuration for Gen2 (uses default compute service account)
    function_body = {
        "name": full_name,
        "environment": "GEN_2",
        "buildConfig": {
            "runtime": "python312",
            "entryPoint": function_config["entry_point"],
            "source": {
                "storageSource": {
                    "bucket": BUCKET_NAME,
                    "object": source_url.split("/")[-1],
                }
            },
            "environmentVariables": {
                "GEMINI_API_KEY": gemini_key,
            },
        },
        "serviceConfig": {
            "availableMemory": function_config["memory"],
            "timeoutSeconds": int(function_config["timeout"].rstrip("s")),
            "environmentVariables": {
                "GEMINI_API_KEY": gemini_key,
            },
            "ingressSettings": "ALLOW_ALL",
            "allTrafficOnLatestRevision": True,
        },
    }

    # Check if function exists
    check_url = f"{FUNCTIONS_API}/{function_name}"
    check_response = requests.get(check_url, headers=headers)

    if check_response.status_code == 200:
        # Update existing function
        print(f"  Updating existing function...")
        response = requests.patch(
            f"{check_url}?updateMask=buildConfig,serviceConfig",
            headers=headers,
            json=function_body
        )
    else:
        # Create new function
        print(f"  Creating new function...")
        response = requests.post(
            f"{FUNCTIONS_API}?functionId={function_name}",
            headers=headers,
            json=function_body
        )

    if response.status_code in [200, 201]:
        print(f"  [OK] Deployment initiated for {function_name}")
        operation = response.json()
        return operation.get("name")
    else:
        print(f"  [FAIL] Failed to deploy {function_name}: {response.status_code}")
        print(f"    {response.text}")
        return None


def wait_for_operation(operation_name: str, token: str):
    """Wait for a long-running operation to complete."""
    import time

    headers = {"Authorization": f"Bearer {token}"}
    operation_url = f"https://cloudfunctions.googleapis.com/v2/{operation_name}"

    print(f"  Waiting for deployment to complete...")

    for i in range(60):  # Wait up to 5 minutes
        response = requests.get(operation_url, headers=headers)
        if response.status_code == 200:
            operation = response.json()
            if operation.get("done"):
                if "error" in operation:
                    print(f"  [FAIL] Deployment failed: {operation['error']}")
                    return False
                print(f"  [OK] Deployment complete!")
                return True
        time.sleep(5)
        print(".", end="", flush=True)

    print(f"\n  [WARN] Deployment timed out (may still be in progress)")
    return False


def set_iam_policy(function_name: str, token: str):
    """Allow unauthenticated access to the function."""
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }

    full_name = f"projects/{PROJECT_ID}/locations/{REGION}/functions/{function_name}"
    iam_url = f"https://cloudfunctions.googleapis.com/v2/{full_name}:setIamPolicy"

    policy = {
        "policy": {
            "bindings": [
                {
                    "role": "roles/cloudfunctions.invoker",
                    "members": ["allUsers"]
                }
            ]
        }
    }

    response = requests.post(iam_url, headers=headers, json=policy)
    if response.status_code == 200:
        print(f"  [OK] IAM policy set for {function_name}")
    else:
        print(f"  [WARN] Could not set IAM policy: {response.status_code}")


def main():
    print("=" * 60)
    print("Firebase Cloud Functions Deployment")
    print("=" * 60)

    # Check service account file
    if not SERVICE_ACCOUNT_FILE.exists():
        print(f"Error: Service account file not found: {SERVICE_ACCOUNT_FILE}")
        sys.exit(1)

    # Get Gemini API key
    gemini_key = get_gemini_api_key()
    if not gemini_key:
        print("Error: GEMINI_API_KEY not found.")
        print("Set it in environment variable or local.properties")
        sys.exit(1)
    print(f"[OK] Gemini API key found")

    # Get access token
    print("\nAuthenticating with Google Cloud...")
    token = get_access_token()
    print("[OK] Authenticated successfully")

    # Create and upload source
    print("\nPreparing source code...")
    zip_path = create_source_zip()
    source_url = upload_source_to_gcs(zip_path, token)

    # Clean up zip file
    os.unlink(zip_path)

    # Deploy each function
    operations = []
    for func in FUNCTIONS:
        op = deploy_function(func, source_url, token, gemini_key)
        if op:
            operations.append((func["name"], op))

    # Wait for all deployments
    print("\n" + "=" * 60)
    print("Waiting for deployments to complete...")
    print("=" * 60)

    for func_name, op in operations:
        print(f"\n{func_name}:")
        if wait_for_operation(op, token):
            set_iam_policy(func_name, token)

    # Print function URLs
    print("\n" + "=" * 60)
    print("Deployment Summary")
    print("=" * 60)
    print("\nFunction URLs:")
    for func in FUNCTIONS:
        url = f"https://{REGION}-{PROJECT_ID}.cloudfunctions.net/{func['name']}"
        print(f"  {func['name']}: {url}")

    print("\n[OK] Deployment complete!")


if __name__ == "__main__":
    main()
