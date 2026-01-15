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

echo ""
echo "Deployment complete!"
echo ""
echo "Function URLs:"
echo "  analyze_and_fill_checklist: https://$REGION-$PROJECT_ID.cloudfunctions.net/analyze_and_fill_checklist"
echo "  generate_checklist: https://$REGION-$PROJECT_ID.cloudfunctions.net/generate_checklist"
echo "  get_usage_stats: https://$REGION-$PROJECT_ID.cloudfunctions.net/get_usage_stats"
