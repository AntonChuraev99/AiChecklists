# DISABLED 2026-07-27 — this script could delete every AI-chat Cloud Function.
#
# It ran `firebase deploy --only functions`, which reconciles production against the
# manifest in `functions.yaml`. That manifest is HAND-WRITTEN: the functions here use
# `@functions_framework.http`, not the Firebase Python SDK's `@https_fn.on_request`,
# so CLI discovery does not regenerate it. It lists only 10 of the 14 deployed
# functions — missing entirely:
#
#     chat_agent · classify_chat_intent · transcribe_audio · chat_completion
#     link_google_account
#
# Firebase CLI treats functions absent from the manifest as removed and offers to
# delete them, so one run could take out the flagship AI chat, intent routing, voice
# input and Google account linking in a single sweep.
#
# Canonical deploy path is `firebase-functions/deploy.sh` (per
# docs/cloud-functions-diagnostics.md): explicit per-function `gcloud functions deploy`,
# which can only touch the functions it names.
#
# Before re-enabling: `functions.yaml` must list all 14 functions, verified against
# `gcloud functions list`.

Write-Host "deploy.ps1 is disabled: it deploys from an incomplete manifest and can delete the AI-chat functions." -ForegroundColor Red
Write-Host "Use firebase-functions/deploy.sh instead (explicit per-function gcloud deploy)." -ForegroundColor Yellow
exit 1
