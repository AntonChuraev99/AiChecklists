#!/usr/bin/env bash
# flow:       analyze-text-create
# checks:     Analyze from pasted text creates a NEW checklist and opens its detail screen; loader gone
# start:      any; script cold-starts the app. Needs >= 30 credits shown (UI gate) — spends 20 (1 AI generation).
# requires:   network; AI credits >= 30 on the device's anonymous user
# last-green: 2026-11-20 · 1.24.0 · emulator-5554
#
# Run from repo root: DEVICE=<serial> APP=com.antonchuraev.aichecklists bash tests/device-flows/analyze-text-create.sh

source "$(dirname "$0")/_analyze-common.sh"

TEXT="Plan for Saturday: buy flowers, pick up dry cleaning, call mom, fix bike tire, prepare dinner for 6 people"

step "Open the app on Inbox"
open_inbox

step "Inbox -> Add task -> Link door -> AI Analysis"
open_analyze_door "Link"

step "Switch the material to Text"
tap 'desc=/^Change source: Link$/'
tap 'text=/^Text$/'
expect_text "Your text"

step "Type the text"
type_into 'class=android.widget.EditText' "$TEXT"
expect '"text":"Plan for Saturday: buy flowers'
expect '"text":"This action costs [0-9]+ credits"'

step "Tap Analyze"
tap 'text=/^Analyze$/'

step "Loader disappears and the new checklist detail opens"
expect_gone '"text":"Analyzing your content"' 90
dismiss_sheets
expect '"text":"[0-9]+ left · 0 of [0-9]+ done"' 15
expect_desc "Set Reminder"

echo "PASS $FLOW" >&2
