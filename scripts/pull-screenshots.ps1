#!/usr/bin/env pwsh
# Pull PNG screenshots produced by ScreenshotCatalogTest from the connected
# emulator/device into a local .\screenshots directory.
#
# Screenshots are stored in the shared MediaStore directory that survives
# Test Orchestrator's "pm clear <package>":
#   /sdcard/Pictures/GistiScreenshots/
#
# Usage:
#   .\scripts\pull-screenshots.ps1                       # default: .\screenshots
#   .\scripts\pull-screenshots.ps1 -OutputDir out\demo   # custom output
#
# Prerequisites:
#   - adb on PATH
#   - Pixel_9 (or other emulator/device) running
#   - Test was executed: .\gradlew.bat composeApp:connectedAndroidTest `
#       --tests com.antonchuraev.aichecklists.ScreenshotCatalogTest

param(
    [string]$OutputDir = ".\screenshots"
)

$ErrorActionPreference = "Stop"

$RemoteDir = "/sdcard/Pictures/GistiScreenshots"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Error "adb not found on PATH."
    exit 1
}

$devices = adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
if (-not $devices) {
    Write-Error "No device/emulator connected. Run 'adb devices' to verify."
    exit 1
}

if (Test-Path $OutputDir) { Remove-Item $OutputDir -Recurse -Force }
New-Item -ItemType Directory -Path $OutputDir | Out-Null

Write-Host "Pulling screenshots from $RemoteDir to $OutputDir ..."
adb pull "$RemoteDir/." $OutputDir | Out-Null

$pngs = Get-ChildItem -Path $OutputDir -Filter *.png | Sort-Object Name
Write-Host "Done. $($pngs.Count) PNG file(s) in $OutputDir"
$pngs | ForEach-Object { Write-Host "  $($_.Name)" }
