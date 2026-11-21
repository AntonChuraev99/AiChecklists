#!/usr/bin/env bash
# Shared helpers for tests/device-flows/analyze-*.sh — sourced, not run directly.
# Base helpers follow ~/.claude/references/android-device-flow-template.sh.
# Selectors are text / content-desc only: this build has no testTagsAsResourceId.

set -Eeuo pipefail

: "${DEVICE:?DEVICE=<serial from adb devices>}"
: "${APP:?APP=<applicationId of the debug build>}"
WAIT_MS="${WAIT_MS:-10000}"
OUT="${OUT:-build/device-flows}"

export MSYS_NO_PATHCONV=1
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dstdout.encoding=UTF-8"

FLOW="$(basename "$0" .sh)"
CURRENT_STEP="start"

step() { CURRENT_STEP="$*"; printf '\n== %s\n' "$*" >&2; }

tap()       { android input tap "$1" --wait="${2:-$WAIT_MS}" --device="$DEVICE" >&2; }
type_into() { android input text "$2" "$1" --wait="$WAIT_MS" --device="$DEVICE" >&2; }
key()       { android input key "$1" --device="$DEVICE" >&2; }

_layout() { android layout --flat --no-idle --device="$DEVICE" 2>/dev/null || true; }

_wait_for() {
  local secs=$1 want=$2; shift 2
  local deadline=$((SECONDS + secs)) tree found
  while :; do
    tree="$(_layout)"
    found=0; grep -q "$@" <<<"$tree" && found=1
    [[ $found == "$want" ]] && return 0
    if (( SECONDS >= deadline )); then echo "condition not met in ${secs}s: grep $*" >&2; return 1; fi
    sleep 1
  done
}

_json_str() {
  local s=$1
  s=${s//\\/\\\\}; s=${s//\"/\\\"}
  s=${s//&/\\u0026}; s=${s//</\\u003c}; s=${s//>/\\u003e}; s=${s//=/\\u003d}; s=${s//\'/\\u0027}
  printf '%s' "$s"
}

expect()      { _wait_for "${2:-10}" 1 -E "$1"; }
expect_gone() { _wait_for "${2:-10}" 0 -E "$1"; }
expect_text() { _wait_for "${2:-10}" 1 -F "\"text\":\"$(_json_str "$1")\""; }
expect_desc() { _wait_for "${2:-10}" 1 -F "\"content-desc\":\"$(_json_str "$1")\""; }

# Optional tap: a one-off sheet may or may not be on screen. Never fails the flow.
tap_if_present() {
  if _layout | grep -qE "$1"; then android input tap "$2" --device="$DEVICE" >/dev/null 2>&1 || true; sleep 1; fi
}

launch() {
  adb -s "$DEVICE" shell am force-stop "$APP"
  adb -s "$DEVICE" shell monkey -p "$APP" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
}

# One-off sheets this app shows over Inbox / checklist detail (widget promo, CSAT, reminder offer).
dismiss_sheets() {
  tap_if_present '"text":"Not now"' 'text=/^Not now$/'
  tap_if_present '"text":"Not Now"' 'text=/^Not Now$/'
  tap_if_present '"content-desc":"Close sheet"' 'desc=/^Close sheet$/'
}

# Cold start -> Inbox. Credits node ("<n> credits") proves the user data loaded.
open_inbox() {
  launch
  for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
    sleep 1
    dismiss_sheets
    if _layout | grep -qE '"content-desc":"[0-9]+ credits"'; then return 0; fi
  done
  echo "Inbox with a credits counter did not appear (first session after install shows 'Get More Credits' — relaunch once)" >&2
  return 1
}

# Inbox -> inline "Add task" row -> "Or create a checklist from:" pill (Photo | PDF | Link | Voice).
open_analyze_door() {
  local pill=$1
  local i
  for i in 1 2 3 4; do
    if android input tap 'text=/^Add task$/' --device="$DEVICE" >/dev/null 2>&1; then break; fi
    adb -s "$DEVICE" shell input swipe 540 1600 540 800 300
    sleep 1
  done
  expect_text "Or create a checklist from:"
  tap "text=/^${pill}\$/"
  expect_text "AI Analysis"
}

# Credits number shown in Inbox / Analyze cost row, e.g. 80.
credits_now() { _layout | grep -oE '"text":"[0-9]+ credits"' | head -1 | grep -oE '[0-9]+'; }

on_fail() {
  local code=$?
  trap - ERR
  mkdir -p "$OUT"
  android layout --flat --no-idle --device="$DEVICE" --output="$OUT/$FLOW-fail-layout.json" >/dev/null 2>&1 || true
  android screen capture --device="$DEVICE" --output="$OUT/$FLOW-fail.png" >/dev/null 2>&1 || true
  if declare -F on_fail_extra >/dev/null; then on_fail_extra || true; fi
  echo "FAIL $FLOW at step: $CURRENT_STEP (exit $code); tree and frame — $OUT/$FLOW-fail-*" >&2
  exit "$code"
}
trap on_fail ERR
