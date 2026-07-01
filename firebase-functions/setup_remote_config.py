"""
Script to set up remote config for AI Credits system in Firestore.

Run this script after deploying functions to configure initial values.
You can also run it later to update values without redeploying.

Usage:
    cd firebase-functions
    python setup_remote_config.py

To change values, edit the CONFIG dictionary below and run again.
"""

import firebase_admin
from firebase_admin import credentials, firestore

# ============================================================================
# CONFIGURATION - Edit these values as needed
# ============================================================================

CONFIG = {
    # Credits given to new users on registration
    "initial_ai_credits": 100,

    # Cost per AI action (analyze_and_fill_checklist, generate_checklist)
    "ai_action_cost": 30,

    # Premium users daily credits cap (refilled to this amount at 12:00 CET)
    "premium_daily_credits_cap": 300,

    # Feature flags
    "feature_ai_analysis_enabled": True,

    # Input limits
    "ai_analysis_max_input_length": 10000,

    # NOTE: the AI-model A/B experiment does NOT live here anymore. It moved to a
    # Firebase Remote Config SERVER template (namespace firebase-server): params
    # `ai_model_arm` + `ai_model_<flow>` gated by a "User in random percentage"
    # condition on randomization_id. Managed in the Firebase console (Remote Config →
    # template type "Server") with native versioning/rollback. See assign_model_arm
    # in main.py. Stop/adjust = console (condition %), not this script.
}

# ============================================================================
# Script logic - No need to edit below
# ============================================================================

def main():
    # Initialize Firebase Admin with project ID
    if not firebase_admin._apps:
        firebase_admin.initialize_app(options={
            'projectId': 'aichecklists-40230'
        })

    db = firestore.client()

    # Create or update the remote_config document
    doc_ref = db.collection("remote_config").document("current")

    # Check if document exists
    doc = doc_ref.get()
    if doc.exists:
        print("Updating existing remote_config/current document...")
        doc_ref.update(CONFIG)
    else:
        print("Creating new remote_config/current document...")
        doc_ref.set(CONFIG)

    print("\nRemote config updated successfully!")
    print("\nCurrent configuration:")
    for key, value in CONFIG.items():
        print(f"  {key}: {value}")

    print("\n" + "="*60)
    print("To change values:")
    print("1. Edit CONFIG dictionary in this script")
    print("2. Run: python setup_remote_config.py")
    print("="*60)


if __name__ == "__main__":
    main()
