#!/usr/bin/env bash
# flow:       analyze-offline-error
# checks:     Analyze with no network shows the friendly network error, hides the loader, keeps the typed text, charges nothing
# start:      any; script cold-starts the app, turns Wi-Fi and mobile data OFF for the Analyze tap and back ON at exit (also on failure)
# requires:   >= 30 credits shown (UI gate); spends 0 credits
# last-green: 2026-11-20 · 1.24.0 · emulator-5554
#
# Run from repo root: DEVICE=<serial> APP=com.antonchuraev.aichecklists bash tests/device-flows/analyze-offline-error.sh

source "$(dirname "$0")/_analyze-common.sh"

net_on() {
  adb -s "$DEVICE" shell svc wifi enable || true
  adb -s "$DEVICE" shell svc data enable || true
}
on_fail_extra() { net_on; }
trap net_on EXIT

TEXT="Plan for Saturday: buy flowers, pick up dry cleaning, call mom"

step "Open the app on Inbox"
open_inbox
BEFORE="$(credits_now || true)"

step "Inbox -> Add task -> Link door -> Text material"
open_analyze_door "Link"
tap 'desc=/^Change source: Link$/'
tap 'text=/^Text$/'
type_into 'class=android.widget.EditText' "$TEXT"
expect '"text":"Plan for Saturday: buy flowers'

step "Network off"
adb -s "$DEVICE" shell svc wifi disable
adb -s "$DEVICE" shell svc data disable
sleep 3

step "Tap Analyze offline"
tap 'text=/^Analyze$/'

step "Friendly network error, loader gone"
expect_text "Connection problem. Check your internet and try again" 60
expect_gone '"text":"Analyzing your content"' 5

step "Dismiss error: input kept, still on Analyze"
tap 'text=/^OK$/'
expect '"text":"Plan for Saturday: buy flowers'
expect_text "AI Analysis"

step "Credits unchanged"
AFTER="$(credits_now || true)"
echo "credits before=$BEFORE after=$AFTER" >&2
[[ -n "$AFTER" && "$AFTER" == "$BEFORE" ]]

echo "PASS $FLOW" >&2
