---
title: "Screenshot Harness for Instrumented Tests — MediaStore + Programmatic Navigation Pattern"
date: 2026-04-27
type: test-infrastructure
modules: [feature/debug, core/navigation, composeApp/androidTest]
keywords: [instrumented-test, screenshot-capture, MediaStore, testTag, ComposeTimeoutException, Throwable, UiAutomator, navigation-recovery]
project: Checklists
---

# Screenshot Harness: Full-App State Coverage via Instrumented Tests

## Problem / Context

Manual screenshot capture for Play Store listings, PRs, and marketing docs is a bottleneck:
- **Before:** Developer manually navigates each screen, captures with device screenshot tool (~15 min per version)
- **Risk:** Screenshots lag behind code, inconsistent quality, easy to miss edge states (empty states, limits hit, premium variants)
- **Goal:** Automate via single instrumented test that captures all screens in canonical states, with output in persistent storage

## Solution

Built a **debug-only ScreenCatalog screen** that acts as a hub for navigating to all app states. Instrumented test (`ScreenshotCatalogTest.kt`) sequences through states, waits for UI settlement (no loading spinners), and captures PNGs via `MediaStore.Images` (survives `pm clear`).

### Architecture

**Three layers:**

1. **ScreenCatalog Navigation Hub** (`feature/debug/ScreenCatalogScreen.kt`)
   - 24 semantic buttons, one per screen state (`catalog_onboarding`, `catalog_main_empty`, `catalog_templates`, etc.)
   - Disabled-state convention: `alpha = 0.38f, onClick = null` while seeding data
   - Seed summary text acts as synchronization barrier — test waits for this before tapping
   - Column(verticalScroll) instead of LazyColumn (see Gotchas below)

2. **Test Harness** (`ScreenshotCatalogTest.kt`)
   - Single method: `@Test fun captureAllScreens()`
   - 4 sequential phases:
     - Onboarding pager: swipes and captures 4 pages
     - Main state variants: empty, with data, free limit, premium
     - Catalog navigation: taps 20+ buttons via testTag, waits for anchor text, captures
     - CSAT flow: optional bottom sheet sequence
   - Helper utilities in `ScreenshotHelper.kt`

3. **Screenshot Storage** (MediaStore)
   - `MediaStore.Images.insertImage()` with `RELATIVE_PATH = "Pictures/GistiScreenshots"`
   - Survives `pm clear` (data wipe between test runs)
   - Retrievable via single `adb pull /sdcard/Pictures/GistiScreenshots/. ./screenshots/`

### Execution

```bash
./gradlew composeApp:installDebug composeApp:installDebugAndroidTest composeApp:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.antonchuraev.aichecklists.ScreenshotCatalogTest
```

Runtime: ~2m12s on Pixel_9 emulator (API 36.1). Produces 28 PNG screenshots.

### Key Helper Patterns

**`ScreenshotHelper.kt` — 5 utilities:**

```kotlin
// Wait for all loading states to clear (3-layer guard)
fun awaitNoLoading() {
    composeTestRule.waitForIdle()
    // Layer 2: no "loading" testTag visible
    composeTestRule.onAllNodesWithTag("loading").assertCountEquals(0)
    // Layer 3: no "Getting things ready..." toast
    composeTestRule.onAllNodesWithText("Getting things ready...").assertCountEquals(0)
}

// Capture via Compose bitmap (full composable tree)
fun captureScreenshot(name: String)

// Validate anchor text before capture (prevents spurious PNGs of wrong states)
fun captureFinal(name: String, anchor: String) {
    composeTestRule.onNodeWithText(anchor, substring = true).assertIsDisplayed()
    captureScreenshot(name)
}

// For native dialogs (Share sheet, etc.) — UiAutomator-based
fun captureSystemScreenshot(name: String)
```

**Test phases** (from iteration log):

```kotlin
// Phase 1: Onboarding pager (4 pages)
repeat(4) {
    captureFinal("onboarding_page${it + 1}", onboardingPageAnchors[it])
    if (it < 3) composeTestRule.onRoot().performSwipe(...)
}

// Phase 2: Main state variants (empty, with_data, free_limit, premium)
for (state in listOf(MainState.Empty, ...)) {
    composeTestRule.onNodeWithTag("catalog_main_$state").performClick()
    awaitNoLoading()
    captureFinal("main_$state", mainStateAnchors[state])
}

// Phase 3: Catalog buttons (templates, create, detail, etc.)
for ((tagName, anchorText) in catalogMap) {
    composeTestRule.onNodeWithTag(tagName).performClick()
    awaitNoLoading()
    captureFinal(tagName.removePrefix("catalog_"), anchorText)
    // Navigation recovery for back-stack-wiping routes
    if (isProblem(tagName)) navController.popBackStack()
    else composeTestRule.onRoot().performClick() // or onBackClick()
}
```

## Why This Approach

### MediaStore over External Files

| Approach | Survival | Simplicity | Retrieval |
|---|---|---|---|
| `getExternalFilesDir()` | ❌ Wiped by `pm clear` | Simple (direct path) | `adb pull` |
| `MediaStore.Images.insertImage()` | ✅ Survives data wipe | Medium (ContentResolver) | `adb pull` |
| Internal app cache | ❌ Wiped by `pm clear` | Simple | Manual ContentProvider |

**Chosen:** MediaStore — guarantees artifact persistence across test reruns without device reset.

### Column(verticalScroll) over LazyColumn for Testable Screens

`LazyColumn` defers off-screen item composition — test framework cannot locate buttons that don't exist in the semantic tree:

```kotlin
// ❌ WRONG: Button not in tree, performClick() fails
LazyColumn {
    items(24) { index ->
        if (index < currentVisibleCount) Button(...)  // composable if visible
    }
}

// ✅ RIGHT: All buttons always composed, scroll handles visibility
Column(modifier = Modifier.verticalScroll(...)) {
    repeat(24) { index ->
        Button(...)  // always in tree, semantic framework can find and click
    }
}
```

For a debug-only 24-button grid, the scroll overhead is negligible. Benefit: test can `performScrollTo(...)` and `performClick()` any button without timing/visibility races.

### Programmatic Navigation over Volume Keys

UiAutomator `pressKeyCode(KEYCODE_VOLUME_UP/DOWN)` on API 36+ emulator: **unreliable**. System volume controller swallows events or triggers system UI instead of app. For test-only menu entries (DebugMenuDetector pattern), drive the composable directly:

```kotlin
// ❌ Unreliable on API 36+
UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    .pressKeyCode(KeyEvent.KEYCODE_VOLUME_UP)

// ✅ Works everywhere
val navigator = GlobalContext.get().get<AppNavigator>()
navigator.navigateToDebugMenu()  // Direct Koin access, instant
```

Requires `waitUntilAtLeast(1000)` delay to allow Koin to stabilize after app process startup.

### ComposeTimeoutException extends Throwable, not Exception

Critical gotcha:

```kotlin
// ❌ WRONG: Does NOT catch ComposeTimeoutException
try {
    composeTestRule.waitUntil(timeout = 5000) { ... }
} catch (e: Exception) {
    // Not caught — exception propagates, test fails
}

// ✅ RIGHT: Catch the superclass
try {
    composeTestRule.waitUntil(...) { ... }
} catch (e: Throwable) {
    // Catches all Compose exceptions
}
```

Reason: `ComposeTimeoutException extends Throwable` directly (not `RuntimeException`). This is by design in Compose test framework to signal framework-level issues, but it breaks common exception-handling patterns. **All test code** that waits on Compose operations must use `catch (e: Throwable)`.

### Koin 4.1.x Test Override Pattern — allowOverride(true) Required

When overriding a production binding from instrumented test (e.g., replacing `PaywallRepository` with a fake), Koin requires explicit opt-in:

```kotlin
// ❌ WRONG: Koin throws DefinitionOverrideException, test hangs at 60s "Instrumentation did not complete"
// GistiApplication.kt
startKoin {
    modules(appModule)
    // allowOverride defaults to FALSE
}

// ✅ RIGHT: Explicitly allow test overrides
// GistiApplication.kt
startKoin {
    modules(appModule)
    allowOverride(true)  // Required for test scenarios
}

// TestApplication.kt (androidTest)
override fun onCreate() {
    super.onCreate()  // Triggers startKoin above
    loadKoinModules {
        single<PaywallRepository> { FakePaywallRepository() }  // Override now succeeds
    }
}
```

**Why this matters:** Koin 4.1.x defaults `allowOverride(false)` as a guard against accidental duplicate definitions in production. Test code that tries to override without this flag triggers silent exception → test initialization fails → app never fully boots → Compose test framework times out waiting for idle UI → test fails at 60s with cryptic "Instrumentation did not complete: code 0". The actual `DefinitionOverrideException` is swallowed by Koin internals and never reaches logcat.

**Diagnosis:** If test hangs at 60s (Orchestrator timeout) after seemingly unrelated change, check:
1. Did you add `loadKoinModules` in test `Application.onCreate()`?
2. Is `allowOverride(true)` present in product `GistiApplication.startKoin{}`?
3. If no to either → that's likely the hang cause.

### Never Use Runtime.exec() in TestWatcher.failed()

TestWatcher callback runs inside the Orchestrator's failure reporting path. Heavy work (subprocess, I/O) inside it can hang:

```kotlin
// ❌ WRONG: Hangs and masks the actual test failure
override fun failed(e: Throwable?, description: Description?) {
    Runtime.getRuntime().exec("logcat -d -t 1000")
        .inputStream.readBytes()  // Blocks in callback → "Instrumentation did not complete"
}

// ✅ RIGHT: If needed, use a background task or skip
override fun failed(e: Throwable?, description: Description?) {
    // Log the actual exception synchronously
    Log.e("Test", description?.methodName, e)
    // Schedule logcat dump in a Future, don't wait for it
}
```

The Orchestrator has strict timeouts for callback completion. Even a 100ms I/O block can exceed the limit and kill the entire test run with a cryptic "code 0" message that hides the real failure.

### MediaStore Orphan Files from Prior Test Runs

`MediaStore.Images.delete()` filtered by `RELATIVE_PATH` may leave artifacts from failed/retry test runs:

```kotlin
// ❌ GOTCHA: If test failed 3 times, device has files at different paths
adb shell ls /sdcard/Pictures/GistiScreenshots/
// Output:
// 19_paywall.png (from run 1, failed)
// 19_paywall (1).png (from run 2, failed)
// 19_paywall (2).png (from run 3, success — this is the fresh one)

// Pull script grabs exact name match → pulls old file, not latest
adb pull /sdcard/Pictures/GistiScreenshots/19_paywall.png  // Gets run 1 artifact ❌

// ✅ RIGHT: Clear MediaStore before test via shell (guarantees fresh state)
adb shell "find /sdcard/Pictures/GistiScreenshots -delete"
./gradlew composeApp:connectedAndroidTest ...
adb pull /sdcard/Pictures/GistiScreenshots/.
```

**Or, verify freshness via mtime:**

```bash
TEST_START_TIME=$(date +%s)
./gradlew composeApp:connectedAndroidTest ...
adb shell stat /sdcard/Pictures/GistiScreenshots/19_paywall.png | grep Modify
# Check that mtime is > TEST_START_TIME
```

Why this matters: During debugging of Paywall screenshot (showing "Unable to load" AlertDialog instead of rendered paywall), the test output seemed unchanged across multiple runs — but the artifact was actually from an older run before the fix was applied. The shell cleanup ensures all artifacts are post-test.

### Navigation Recovery for Back-Stack-Wiping Routes

Routes decorated with `popUpTo { inclusive = true }` (e.g., `navigateToOnboarding()`) wipe the back-stack — `pressBack()` doesn't recover to the ScreenCatalog. Mitigation pattern:

```kotlin
// Tapped "Onboarding" button
composeTestRule.onNodeWithTag("catalog_onboarding").performClick()
awaitNoLoading()
captureScreenshot("onboarding_flow")

// Back navigation special handling
val recovered = try {
    composeTestRule.onRoot().performKeyInput { pressImeAction() }
    true
} catch (e: Throwable) {
    false  // Back failed, route wiped stack
}

if (!recovered) {
    // Re-enter ScreenCatalog via direct navigation
    nav.navigateToScreenCatalog()
}
```

Some routes are skipped entirely in the test if they're already captured by earlier phases (e.g., `catalog_onboarding` skipped because Onboarding is captured in Phase 1).

## Examples

### Test Output: 28 PNG Screenshots

```
01_onboarding_page1.png        (Create via AI page)
02_onboarding_page2.png        (Fill via AI page)
03_onboarding_page3.png        (Export & Share page)
04_onboarding_page4.png        (Trial offer page)
05_main_empty.png              (Fresh-install main)
06_main_with_data.png          (Main with checklists)
07_main_free_limit.png         (3 checklists, limit hit)
08_main_premium.png            (Premium user variant)
09_templates.png               (Template gallery)
10_template_preview.png        (Template detail)
11_create_new.png              (New checklist form)
12_create_edit.png             (Edit existing checklist)
13_checklist_detail.png        (Detail with items)
14_fill_detail.png             (Fill with notes)
15_fills_list.png              (All fills for checklist)
16_share_checklist.png         (Export options)
17_analyze_empty.png           (Analyze with no input)
18_analyze_result_preview.png  (AI-generated results)
19_paywall_trial.png           (3-day free trial offer)
20_paywall_purchase.png        (Subscription options)
21_subscription_success.png    (Purchase confirmed)
22_subscription_pending.png    (Awaiting processing)
23_settings.png                (Preferences & theme)
24_update_feed.png             (Release notes & posts)
25_store_screenshot_1.png      (Marketing page: Create)
26_store_screenshot_2.png      (Marketing page: Fill)
27_store_screenshot_3.png      (Marketing page: Export)
28_store_screenshot_4.png      (Marketing page: Trial)
```

### Running the Test

```bash
# One-liner (install + build test APK + run on connected device/emulator)
./gradlew composeApp:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.antonchuraev.aichecklists.ScreenshotCatalogTest

# Retrieve screenshots
adb pull /sdcard/Pictures/GistiScreenshots/. ./screenshots/

# Or use the provided script
./scripts/pull-screenshots.sh
```

### Handling Missing States

If a state cannot be reached (template not found, API not responding), the test doesn't fail — it logs and continues:

```kotlin
try {
    captureFinal("template_preview", "Template Detail")
} catch (e: ComposeTimeoutException) {
    Log.w("ScreenshotTest", "Template preview failed (RC not fetched in test env) — skipping")
}
```

This allows the harness to run in environments where Remote Config hasn't been loaded (test emulator startup is quick).

## Related Files

- `core/navigation/api/.../AppNavRoute.kt` — `object ScreenCatalog : AppNavRoute`
- `core/navigation/api/.../AppNavigator.kt` — `navigateToScreenCatalog()`, `navigateToSettings()`
- `feature/debug/.../ScreenCatalogScreen.kt` — 24-button hub
- `feature/debug/.../ScreenCatalogViewModel.kt` — state sequencing
- `composeApp/src/androidTest/.../ScreenshotCatalogTest.kt` — single test method
- `composeApp/src/androidTest/.../ScreenshotHelper.kt` — capture utilities
- `composeApp/src/commonMain/.../App.kt` — route registration (`if (isDebug)`)
- `core/designsystem/.../strings.xml` — 54 new `debug_catalog_*` keys
- `scripts/pull-screenshots.sh` — retrieval helper (`.sh` + `.ps1`)

## Lessons & Gotchas

| Gotcha | Impact | Solution |
|--------|--------|----------|
| **Koin 4.1.x test override requires `allowOverride(true)`** | `loadKoinModules` in test silently fails → app hangs → 60s timeout | Add `allowOverride(true)` to `startKoin{}` in product app; test override then succeeds |
| **MediaStore orphans from prior test runs** | Pull script retrieves stale artifact from failed run, not latest successful capture | Pre-test cleanup: `adb shell "find /sdcard/Pictures/GistiScreenshots -delete"` or verify mtime |
| **MediaStore artifacts wiped by `pm clear`** | All test outputs lost between runs | Use `MediaStore.Images.insertImage()` with `RELATIVE_PATH` |
| **ComposeTimeoutException not caught by `catch (Exception)`** | Tests silently fail with masked errors | Always `catch (e: Throwable)` in Compose test code |
| **LazyColumn off-screen items not in semantic tree** | `performClick()` on hidden buttons fails with `NoMatchingViewException` | Use `Column(verticalScroll)` for testable screens |
| **Volume key input unreliable on API 36+** | UiAutomator presses ignored or trigger system UI | Drive target composable directly via `AppNavigator.navigate()` |
| **Runtime.exec() inside TestWatcher.failed()** | Hangs and masks actual test failure with "code 0" | Avoid blocking I/O in callback; use background tasks |
| **Back-stack-wiping routes** | `pressBack()` escapes the screen, loses recovery | Use direct `navigate()` call, or skip re-entry for problematic routes |

## Testing

Verified on:
- **Pixel_9 emulator, API 36.1** — full green run, 28 PNGs captured, zero loading spinners visible
- **Runtime:** 2m12s including install, build, run
- **Idempotent:** No device setup required beyond stock AVD

## Compound Effect

- **Avoided 4+ manual screenshot cycles** — automation replaces manual per-version screenshots
- **Early validation of test environment** (timeout, file permissions, navigation model) prevented 6+ failed attempts
- **Programmatic navigation pivot** (from unreliable volume keys) reduced iteration count by 2x
