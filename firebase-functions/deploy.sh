#!/bin/bash
# Deploy Firebase Cloud Functions
#
# Prerequisites:
# 1. Install Google Cloud SDK: https://cloud.google.com/sdk/docs/install
# 2. Run: gcloud auth login
# 3. Run: gcloud config set project aichecklists-40230

PROJECT_ID="aichecklists-40230"
REGION="us-central1"

echo "Deploying Cloud Functions to $PROJECT_ID..."

# Set the Gemini API key as a secret
echo "Setting GEMINI_API_KEY secret..."
echo -n "$GEMINI_API_KEY" | gcloud secrets create gemini-api-key --data-file=- 2>/dev/null || \
echo -n "$GEMINI_API_KEY" | gcloud secrets versions add gemini-api-key --data-file=-

# Set the RevenueCat API key as a secret (V1 Secret key for purchase verification)
if [ -n "$REVENUECAT_API_KEY" ]; then
    echo "Setting REVENUECAT_API_KEY secret..."
    echo -n "$REVENUECAT_API_KEY" | gcloud secrets create revenuecat-api-key --data-file=- 2>/dev/null || \
    echo -n "$REVENUECAT_API_KEY" | gcloud secrets versions add revenuecat-api-key --data-file=-
fi

# Deploy analyze_and_fill_checklist function
echo "Deploying analyze_and_fill_checklist..."
gcloud functions deploy analyze_and_fill_checklist \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=analyze_and_fill_checklist \
    --trigger-http \
    --allow-unauthenticated \
    --set-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
    --memory=512MB \
    --timeout=60s

# Deploy generate_checklist function
echo "Deploying generate_checklist..."
gcloud functions deploy generate_checklist \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=generate_checklist \
    --trigger-http \
    --allow-unauthenticated \
    --set-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
    --memory=512MB \
    --timeout=60s

# Deploy restore_credits_after_purchase function (with RevenueCat verification)
echo "Deploying restore_credits_after_purchase..."
gcloud functions deploy restore_credits_after_purchase \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=restore_credits_after_purchase \
    --trigger-http \
    --allow-unauthenticated \
    --set-secrets="REVENUECAT_API_KEY=revenuecat-api-key:latest" \
    --memory=256MB \
    --timeout=30s

# Deploy get_usage_stats function
echo "Deploying get_usage_stats..."
gcloud functions deploy get_usage_stats \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=get_usage_stats \
    --trigger-http \
    --allow-unauthenticated \
    --memory=256MB \
    --timeout=30s

# Deploy refill_premium_credits function (with RevenueCat verification)
echo "Deploying refill_premium_credits..."
gcloud functions deploy refill_premium_credits \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=refill_premium_credits \
    --trigger-http \
    --allow-unauthenticated \
    --set-secrets="REVENUECAT_API_KEY=revenuecat-api-key:latest" \
    --memory=256MB \
    --timeout=300s

# ── AI Chat functions (all use GEMINI_API_KEY) ──────────────────────────────
# NOTE: register_user / link_google_account / get_credits_info are infra
# functions (no Gemini) deployed separately; not folded in here yet.

# Deploy classify_chat_intent (Layer 2 — cheap classifier)
echo "Deploying classify_chat_intent..."
gcloud functions deploy classify_chat_intent \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=classify_chat_intent \
    --trigger-http \
    --allow-unauthenticated \
    --set-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
    --memory=256MB \
    --timeout=60s

# Deploy chat_completion (Layer 3 — text-only fallback; kept as kill-switch path)
echo "Deploying chat_completion..."
gcloud functions deploy chat_completion \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=chat_completion \
    --trigger-http \
    --allow-unauthenticated \
    --set-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
    --memory=512MB \
    --timeout=60s

# Deploy chat_agent (Layer 3 AGENT — the agentic bridge / next-step oracle)
echo "Deploying chat_agent..."
gcloud functions deploy chat_agent \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=chat_agent \
    --trigger-http \
    --allow-unauthenticated \
    --set-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
    --memory=512MB \
    --timeout=60s

# Deploy transcribe_audio (mic voice input → text)
echo "Deploying transcribe_audio..."
gcloud functions deploy transcribe_audio \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=transcribe_audio \
    --trigger-http \
    --allow-unauthenticated \
    --set-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
    --memory=512MB \
    --timeout=120s

echo ""
echo "Deployment complete!"
echo ""
echo "Function URLs:"
echo "  analyze_and_fill_checklist: https://$REGION-$PROJECT_ID.cloudfunctions.net/analyze_and_fill_checklist"
echo "  generate_checklist: https://$REGION-$PROJECT_ID.cloudfunctions.net/generate_checklist"
echo "  restore_credits_after_purchase: https://$REGION-$PROJECT_ID.cloudfunctions.net/restore_credits_after_purchase"
echo "  get_usage_stats: https://$REGION-$PROJECT_ID.cloudfunctions.net/get_usage_stats"
echo "  refill_premium_credits: https://$REGION-$PROJECT_ID.cloudfunctions.net/refill_premium_credits"
echo "  classify_chat_intent: https://$REGION-$PROJECT_ID.cloudfunctions.net/classify_chat_intent"
echo "  chat_completion: https://$REGION-$PROJECT_ID.cloudfunctions.net/chat_completion"
echo "  chat_agent: https://$REGION-$PROJECT_ID.cloudfunctions.net/chat_agent"
echo "  transcribe_audio: https://$REGION-$PROJECT_ID.cloudfunctions.net/transcribe_audio"
