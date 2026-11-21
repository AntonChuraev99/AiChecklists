#!/usr/bin/env bash
# flow:       analyze-second-visit
# checks:     after one successful Analyze, opening Analyze AGAIN through the same door in the same process shows
#             the input (URL field + Analyze button), not the "Analyzing your content" loader
# start:      any; script cold-starts the app. Spends 20 credits (1 AI generation); needs >= 30 shown.
# requires:   network; AI credits >= 30
# last-green: never — RED on 1.24.0 (83f437ce): second visit is stuck on the loader forever, no request sent.
#             Regression for that defect; goes to README index only after the fix makes it pass.
#
# Run from repo root: DEVICE=<serial> APP=com.antonchuraev.aichecklists bash tests/device-flows/analyze-second-visit.sh

source "$(dirname "$0")/_analyze-common.sh"

step "Open the app on Inbox"
open_inbox

step "First visit: Link door, analyze a URL"
open_analyze_door "Link"
type_into 'class=android.widget.EditText' "https://en.wikipedia.org/wiki/Checklist"
tap 'text=/^Analyze$/'
expect_gone '"text":"Analyzing your content"' 90
dismiss_sheets
expect '"text":"[0-9]+ left · 0 of [0-9]+ done"' 15

step "Back to Inbox (same process)"
key back
dismiss_sheets
expect '"content-desc":"[0-9]+ credits"' 10

step "Second visit through the same Link door"
open_analyze_door "Link"

step "Input is shown, not a stale loader"
expect_text "URL" 5
expect_text "Analyze" 5
expect_gone '"text":"Analyzing your content"' 5

echo "PASS $FLOW" >&2
