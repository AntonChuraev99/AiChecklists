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
    --update-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
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
    --update-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
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
    --update-secrets="REVENUECAT_API_KEY=revenuecat-api-key:latest" \
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
    --update-secrets="REVENUECAT_API_KEY=revenuecat-api-key:latest" \
    --memory=256MB \
    --timeout=300s

# Set the promo-push secrets (Amplitude server key + optional admin key)
if [ -n "$AMPLITUDE_SERVER_API_KEY" ]; then
    echo "Setting AMPLITUDE_SERVER_API_KEY secret (amplitude-server-key)..."
    echo -n "$AMPLITUDE_SERVER_API_KEY" | gcloud secrets create amplitude-server-key --data-file=- 2>/dev/null || \
    echo -n "$AMPLITUDE_SERVER_API_KEY" | gcloud secrets versions add amplitude-server-key --data-file=-
fi
if [ -n "$PUSH_ADMIN_KEY" ]; then
    echo "Setting PUSH_ADMIN_KEY secret (push-admin-key)..."
    echo -n "$PUSH_ADMIN_KEY" | gcloud secrets create push-admin-key --data-file=- 2>/dev/null || \
    echo -n "$PUSH_ADMIN_KEY" | gcloud secrets versions add push-admin-key --data-file=-
fi

# Deploy send_promotions_batch (promotional re-engagement / win-back push sender).
# NOTE: sends REAL pushes to REAL users when triggered — the first prod trigger is a
# separate gate (user confirmation + Google Play Policy 4.1 audit). Deploying the code is
# safe; it does nothing until Cloud Scheduler (or a manual call) invokes it.
echo "Deploying send_promotions_batch..."
gcloud functions deploy send_promotions_batch \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=send_promotions_batch \
    --trigger-http \
    --allow-unauthenticated \
    --update-secrets="AMPLITUDE_SERVER_API_KEY=amplitude-server-key:latest,PUSH_ADMIN_KEY=push-admin-key:latest" \
    --memory=512MB \
    --timeout=540s

# Deploy register_push_token (Admin SDK merge-write of an FCM token into the
# credit-doc — extends promo-push reach to anonymous users, who only write their
# user doc through the CF layer). No secrets — Admin SDK / Firestore only.
echo "Deploying register_push_token..."
gcloud functions deploy register_push_token \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=register_push_token \
    --trigger-http \
    --allow-unauthenticated \
    --memory=256MB \
    --timeout=30s

# ── Infra functions (no Gemini) ─────────────────────────────────────────────
# NOTE: get_credits_info is still deployed separately; not folded in here yet.
#
# ⚠️ MEMORY = 512MB, not the 256MB default. Every function in this codebase imports the
# SAME main.py module graph (firebase_admin + google-genai + flask + requests), so even a
# "small" infra function pays the full import-time RSS. At 256MB the container sits close
# to the ceiling and Cloud Run has killed it with "Memory limit of 244 MiB exceeded" —
# observed on link_google_account (2026-07-27), register_user (2026-07-06) and
# classify_chat_intent (2026-07-18). All three OOMs landed OUTSIDE a request (no request
# log in the window), so no user saw a 5xx yet — this is the latent-risk fix, not an
# incident fix. Cost delta is ~0: these are low-volume (8 / 210 / 25 calls a week) and the
# extra GiB-s stays inside the Cloud Run free tier.
echo "Deploying register_user..."
gcloud functions deploy register_user \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=register_user \
    --trigger-http \
    --allow-unauthenticated \
    --memory=512MB \
    --timeout=30s

echo "Deploying link_google_account..."
gcloud functions deploy link_google_account \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=link_google_account \
    --trigger-http \
    --allow-unauthenticated \
    --memory=512MB \
    --timeout=30s

# ── AI Chat functions (all use GEMINI_API_KEY) ──────────────────────────────

# Deploy classify_chat_intent (Layer 2 — cheap classifier)
# 512MB (was 256MB): OOM-killed at the 244 MiB ceiling on 2026-07-18 — see the note above.
echo "Deploying classify_chat_intent..."
gcloud functions deploy classify_chat_intent \
    --gen2 \
    --runtime=python312 \
    --region=$REGION \
    --source=. \
    --entry-point=classify_chat_intent \
    --trigger-http \
    --allow-unauthenticated \
    --update-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
    --memory=512MB \
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
    --update-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
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
    --update-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
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
    --update-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
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
echo "  send_promotions_batch: https://$REGION-$PROJECT_ID.cloudfunctions.net/send_promotions_batch"
echo "  register_push_token: https://$REGION-$PROJECT_ID.cloudfunctions.net/register_push_token"

# ============================================================================
# Cloud Scheduler — promotional push (CONFIG ONLY, DO NOT auto-run).
# ============================================================================
# Creating a scheduler job = shipping REAL recurring pushes → a deliberate gate (user
# confirmation + Play Policy 4.1 audit). These commands are documented here, commented
# out on purpose. `refill_premium_credits` is the existing Scheduler→HTTP precedent.
#
# Frequency cap: at most ~1 promo/day/user (report §7). Schedule the two dormant tiers on
# DIFFERENT days so no user gets both in one day; the per-user cooldown (promo_cooldown_hours)
# is the server-side backstop. Times are UTC; pick a slot inside users' active window.
#
# Re-engagement (dormant 3–7 days), daily 14:00 UTC:
#   gcloud scheduler jobs create http promo-reengagement \
#     --location=$REGION --schedule="0 14 * * *" --time-zone="UTC" \
#     --uri="https://$REGION-$PROJECT_ID.cloudfunctions.net/send_promotions_batch" \
#     --http-method=POST --headers="Content-Type=application/json" \
#     --message-body='{"push_type":"reengagement","min_inactive_days":3,"max_inactive_days":7,"admin_key":"REPLACE_WITH_PUSH_ADMIN_KEY"}'
#
# Win-back (dormant 14–30 days), weekly Wed 14:00 UTC:
#   gcloud scheduler jobs create http promo-winback \
#     --location=$REGION --schedule="0 14 * * 3" --time-zone="UTC" \
#     --uri="https://$REGION-$PROJECT_ID.cloudfunctions.net/send_promotions_batch" \
#     --http-method=POST --headers="Content-Type=application/json" \
#     --message-body='{"push_type":"winback","min_inactive_days":14,"max_inactive_days":30,"admin_key":"REPLACE_WITH_PUSH_ADMIN_KEY"}'
#
# Smoke test WITHOUT sending (dry_run — computes audience + sample payload only):
#   curl -X POST "https://$REGION-$PROJECT_ID.cloudfunctions.net/send_promotions_batch" \
#     -H "Content-Type: application/json" \
#     -d '{"push_type":"reengagement","min_inactive_days":3,"dry_run":true,"admin_key":"..."}'
