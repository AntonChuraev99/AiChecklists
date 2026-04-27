# Scripts

## `pull-screenshots.sh` / `pull-screenshots.ps1`

Pulls all screenshots produced by `ScreenshotCatalogTest` from a running emulator/device.

### Full run (boot emulator → run test → pull)

```bash
# 1. Start the emulator (Pixel_9 recommended; the test was tuned for it)
$ANDROID_SDK/emulator/emulator -avd Pixel_9 &

# 2. Wait for boot, then run only the screenshot test (skips other E2E suites)
./gradlew composeApp:connectedAndroidTest \
    --tests com.antonchuraev.aichecklists.ScreenshotCatalogTest

# 3. Pull all PNGs into ./screenshots
./scripts/pull-screenshots.sh
```

On Windows / PowerShell:

```powershell
& "$env:ANDROID_SDK\emulator\emulator.exe" -avd Pixel_9
.\gradlew.bat composeApp:connectedAndroidTest `
    --tests com.antonchuraev.aichecklists.ScreenshotCatalogTest
.\scripts\pull-screenshots.ps1
```

### What you get

~30 PNG files named `01_*.png` to `32_*.png` (zero-padded for sort order):
onboarding pages, four Main states (empty / with_data / free_limit / premium),
every screen reachable from the Debug → Screen Catalog menu, plus CSAT bottom
sheet and the native Share sheet.

On test failure a `_logcat.txt` file is also written to the same directory
(last 1 000 logcat lines) — pulled together with the PNGs for post-mortem.

### Where the device stores them

`/sdcard/Pictures/GistiScreenshots/`

This is the **shared MediaStore** location — it lives outside the app's private
storage and is **not** wiped by Test Orchestrator's `pm clear <package>`. Screenshots
survive even if the test fails mid-run.

```
adb pull /sdcard/Pictures/GistiScreenshots ./screenshots/
```
