#!/usr/bin/env bash
# Pull PNG screenshots produced by ScreenshotCatalogTest from the connected
# emulator/device into a local ./screenshots directory.
#
# Screenshots are stored in the shared MediaStore directory that survives
# Test Orchestrator's "pm clear <package>":
#   /sdcard/Pictures/GistiScreenshots/
#
# Usage:
#   ./scripts/pull-screenshots.sh                    # default output: ./screenshots
#   ./scripts/pull-screenshots.sh out/my-screens     # custom output dir
#
# Prerequisites:
#   - adb on PATH
#   - Pixel_9 (or other emulator/device) running
#   - Test was executed: ./gradlew composeApp:connectedAndroidTest \
#       --tests com.antonchuraev.aichecklists.ScreenshotCatalogTest

set -euo pipefail

REMOTE_DIR="/sdcard/Pictures/GistiScreenshots"
LOCAL_DIR="${1:-./screenshots}"

if ! command -v adb >/dev/null 2>&1; then
    echo "ERROR: adb not found on PATH." >&2
    exit 1
fi

DEVICE_COUNT=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "ERROR: no device/emulator connected. Run 'adb devices' to verify." >&2
    exit 1
fi

rm -rf "$LOCAL_DIR"
mkdir -p "$LOCAL_DIR"

echo "Pulling screenshots from $REMOTE_DIR to $LOCAL_DIR ..."
adb pull "$REMOTE_DIR/." "$LOCAL_DIR" >/dev/null

PNG_COUNT=$(find "$LOCAL_DIR" -maxdepth 1 -name '*.png' | wc -l | tr -d ' ')
echo "Done. $PNG_COUNT PNG file(s) in $LOCAL_DIR"
ls -1 "$LOCAL_DIR" | sort
