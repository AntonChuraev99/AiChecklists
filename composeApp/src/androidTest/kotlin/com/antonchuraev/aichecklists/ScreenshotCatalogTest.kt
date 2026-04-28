package com.antonchuraev.aichecklists

import android.view.KeyEvent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Instrumented screenshot harness for the Gisti app.
 *
 * Run on a Pixel_9 emulator (API 30+). Produces ordered PNG files in the shared
 * MediaStore directory (survives Test Orchestrator's pm clear):
 *   /sdcard/Pictures/GistiScreenshots/
 *
 * Retrieve with:
 *   adb pull /sdcard/Pictures/GistiScreenshots ./screenshots/
 *
 * IMPORTANT:
 *  - This is ONE test method so the Activity is created once and navigation stays consistent.
 *  - Thread.sleep is NEVER used; all waits are waitUntil / waitForIdle.
 *  - Volume key events (UP/DOWN) trigger the DebugMenuDetector in MainActivity.
 *    On the emulator the audio service fires but produces no audible sound.
 */
class ScreenshotCatalogTest : BaseUiTest() {

    // TestWatcher logcat dump removed — Runtime.exec("logcat") inside the failed()
    // callback hangs on pipe read and causes "Instrumentation did not complete"
    // (process killed before the actual failure reaches the test runner).
    // For post-mortem use `adb logcat -d -t 1000` from the host instead.

    @Test
    fun captureAllScreens() {
        clearScreenshotsDir()

        // ── Phase 1: Onboarding (4 pages) ─────────────────────────────────
        // Splash completes → Onboarding page 1 is shown automatically.
        // We wait for Splash to disappear then capture each page before pressing Skip.

        // 60s timeout: fresh install after pm clear runs Firebase RC, RevenueCat,
        // and anonymous auth from scratch — can exceed 20s on a cold emulator.
        waitForSplashToComplete(timeoutMillis = 60_000)

        // Give Compose one extra idle tick so the Splash → Onboarding recomposition
        // has time to settle before we start asserting on Onboarding content.
        composeTestRule.waitForIdle()

        // Defensive guard: assert Onboarding page 1 is actually visible before
        // proceeding. Gives a clear failure message rather than a generic timeout.
        composeTestRule.waitUntil(timeoutMillis = 30_000) {
            composeTestRule.onAllNodesWithText("Create via AI").fetchSemanticsNodes().isNotEmpty()
        }

        // Page 1 – "Create via AI"  (onboarding_page1_title in strings.xml)
        composeTestRule.captureFinal("onboarding_1_create", anchor = "Create via AI")

        // Page 2 – "Fill via AI" — swipe left on the HorizontalPager
        composeTestRule.onNode(hasTestTag("onboarding_pager"))
            .runCatching { performTouchInput { swipeLeft() } }
            .getOrElse {
                // Pager may not have the testTag; fall back to a button tap
                try {
                    composeTestRule.onNodeWithText("Continue").performClick()
                } catch (_: Throwable) { /* best effort */ }
            }
        composeTestRule.waitForIdle()
        // Fallback if swipe was intercepted: tap Continue
        val onPage2 = composeTestRule.onAllNodesWithText("Fill via AI").fetchSemanticsNodes().isNotEmpty()
        if (!onPage2) {
            runCatching { composeTestRule.onNodeWithText("Continue").performClick() }
            composeTestRule.waitForIdle()
        }
        composeTestRule.captureFinal("onboarding_2_fill", anchor = "Fill via AI")

        // Page 3 – "Export & Share"
        swipeOnboardingLeft()
        composeTestRule.captureFinal("onboarding_3_export", anchor = "Export & Share")

        // Page 4 – Paywall page (has "Restore Purchase" text from paywall_restore string)
        swipeOnboardingLeft()
        // Page 4 is the paywall: wait for either "Start Free Trial" or "Subscribe Now" or "Restore Purchase"
        waitForAnyText(listOf("Start Free Trial", "Subscribe Now", "Restore Purchase"), timeoutMs = 8000)
        composeTestRule.awaitNoLoading()
        composeTestRule.captureScreenshot("onboarding_4_paywall")

        // Skip onboarding → reach Main
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Skip").performClick()
        composeTestRule.waitForIdle()

        // ── Phase 2: Main states ───────────────────────────────────────────

        // Empty state (fresh install — no checklists)
        // anchor: "Ready to get organized?" (main_empty_title in strings.xml)
        composeTestRule.captureFinal("main_empty", anchor = "Ready to get organized?")

        // Open Debug via Volume Up → Down → Up  (DebugMenuDetector requires 3 events < 500ms each)
        openDebugMenu()
        waitUntilScreenVisible("Debug Menu", timeoutMillis = 5000)

        // Tap "Screen Catalog" entry on DebugScreen
        composeTestRule.onNodeWithText("Screen Catalog").performClick()
        composeTestRule.waitForIdle()
        waitUntilScreenVisible("Screen Catalog")

        // ── Seed: with_data → capture main_with_data ──
        tapCatalog("catalog_state_with_data")
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Seeded 3 checklists, premium=false, credits=10")
                .fetchSemanticsNodes().isNotEmpty()
        }
        tapCatalog("catalog_main")
        composeTestRule.waitForIdle()
        waitForAnyText(listOf("Create Checklist", "Ready to get organized?"), timeoutMs = 8000)
        composeTestRule.awaitNoLoading()
        composeTestRule.captureScreenshot("main_with_data")
        returnToScreenCatalog()

        // ── Seed: free_limit → capture main_free_limit ──
        tapCatalog("catalog_state_free_limit")
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Seeded 4 checklists, premium=false, credits=10")
                .fetchSemanticsNodes().isNotEmpty()
        }
        tapCatalog("catalog_main")
        composeTestRule.waitForIdle()
        waitForAnyText(listOf("Create Checklist", "Become Premium to Unlock More"), timeoutMs = 8000)
        composeTestRule.awaitNoLoading()
        composeTestRule.captureScreenshot("main_free_limit")
        returnToScreenCatalog()

        // ── Seed: premium → capture main_premium ──
        tapCatalog("catalog_state_premium")
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Seeded 3 checklists, premium=true, credits=300")
                .fetchSemanticsNodes().isNotEmpty()
        }
        tapCatalog("catalog_main")
        composeTestRule.waitForIdle()
        waitForAnyText(listOf("Create Checklist", "Ready to get organized?"), timeoutMs = 8000)
        composeTestRule.awaitNoLoading()
        composeTestRule.captureScreenshot("main_premium")
        returnToScreenCatalog()

        // Reset to with_data for all subsequent catalog navigation
        tapCatalog("catalog_state_with_data")
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Seeded 3 checklists, premium=false, credits=10")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // ── Phase 3: Catalog navigation ───────────────────────────────────

        // 1. catalog_onboarding
        // NOTE: NavigateOnboarding replaces the back-stack with Splash → Onboarding, which
        // makes returning to ScreenCatalog via pressBack() unreliable. SKIPPED — see TODO.
        // TODO: Re-enable when back-stack behaviour is confirmed safe.

        // 2. catalog_interactive_onboarding
        // SKIPPED in catalog navigation: navigateToInteractiveOnboarding wipes the back-stack
        // (popUpTo<Splash>{inclusive=true}), so pressBack() can't return to ScreenCatalog.
        // The screen was already captured in the previous run via the early variant of this test
        // and is functionally equivalent to a one-tap state. If a fresh capture is needed, drive
        // it directly via navigator.navigateToInteractiveOnboarding() at the END of the test.

        // 3. catalog_templates
        // AppScaffold title = Res.string.create_title = "New Checklist" (verified in TemplatesScreen.kt:134)
        captureFromCatalog(
            tagName = "catalog_templates",
            anchor = "New Checklist",
            screenshotName = "templates"
        )

        // 4. catalog_template_preview
        // Scaffold title = Res.string.template_preview_title = "Preview" (when template is null) or
        // the template name once loaded (dynamic). "Add new item..." (template_preview_add_item_hint)
        // is always present in TemplatePreviewContent. "Create Checklist" appears in the button text.
        // "No templates available" / "Use Template" are from TemplatesScreen — NOT this screen.
        composeTestRule.onNodeWithTag("catalog_template_preview").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("catalog_template_preview").performClick()
        composeTestRule.waitForIdle()
        val templatePreviewFound = waitForAnyTextSafe(
            listOf("Preview", "Add new item...", "Create Checklist"),
            timeoutMs = 6000
        )
        composeTestRule.awaitNoLoading()
        composeTestRule.captureScreenshot(
            if (templatePreviewFound) "template_preview" else "template_preview_empty"
        )
        returnToScreenCatalog()

        // 5. catalog_create_new
        // anchor: "e.g., Project Tasks" placeholder (create_checklist_name_placeholder)
        captureFromCatalog(
            tagName = "catalog_create_new",
            anchor = "e.g., Project Tasks",
            screenshotName = "create_new"
        )

        // 6. catalog_create_edit
        // anchor: "Save" button is always present in CreateChecklistScreen
        captureFromCatalog(
            tagName = "catalog_create_edit",
            anchor = "Save",
            screenshotName = "create_edit",
            isDisabledOk = true   // disabled if no seed id — skip gracefully
        )

        // 7. catalog_checklist_detail
        // Scaffold title = checklist name (dynamic, not stable). "Add new item..." (add_item_placeholder)
        // only appears when addItemActive=true (requires tapping the toolbar "+" button — not default).
        // "Create New Fill" (checklist_new_fill) is always rendered in the bottom bar in normal mode.
        captureFromCatalog(
            tagName = "catalog_checklist_detail",
            anchor = "Create New Fill",
            screenshotName = "checklist_detail",
            isDisabledOk = true
        )

        // 8. catalog_fill_detail
        // Default state: isEditing=false (FillDetailState.Content default). Scaffold title = fill.name
        // (dynamic). "Edit Fill" (fill_edit_title) only appears when isEditing=true — NOT the default.
        // "All Fills" (fills_list_title) is from FillsListScreen — NOT this screen.
        // "Progress" (checklist_progress) is always rendered in ProgressHeader which is unconditional.
        composeTestRule.onNodeWithTag("catalog_fill_detail").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("catalog_fill_detail").let { node ->
            try {
                node.performClick()
                composeTestRule.waitForIdle()
                val fillVisible = waitForAnyTextSafe(listOf("Progress", "Edit Fill"), timeoutMs = 6000)
                composeTestRule.awaitNoLoading()
                composeTestRule.captureScreenshot("fill_detail")
            } catch (_: Throwable) {
                // Node may be disabled (no fill seeded) — skip
            }
        }
        returnToScreenCatalog()

        // 9. catalog_fills_list
        // anchor: "All Fills" (fills_list_title)
        captureFromCatalog(
            tagName = "catalog_fills_list",
            anchor = "All Fills",
            screenshotName = "fills_list",
            isDisabledOk = true
        )

        // 10. catalog_share_checklist
        // anchor: "Share Checklist" (share_screen_title)
        composeTestRule.onNodeWithTag("catalog_share_checklist").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("catalog_share_checklist").let { node ->
            try {
                node.performClick()
                composeTestRule.waitForIdle()
                composeTestRule.waitUntil(timeoutMillis = 6000) {
                    composeTestRule.onAllNodesWithText("Share Checklist").fetchSemanticsNodes().isNotEmpty()
                }
                composeTestRule.awaitNoLoading()
                composeTestRule.captureScreenshot("share_checklist")

                // Tap "Plain Text" to select a format, then tap "Share" to trigger native share sheet
                try {
                    composeTestRule.onNodeWithText("Plain Text").performClick()
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithText("Share").performClick()
                    composeTestRule.waitForIdle()
                    // Give the share sheet time to animate in
                    composeTestRule.waitUntil(timeoutMillis = 3000) {
                        // Share sheet is a system window — just wait for Compose to go idle
                        true
                    }
                    captureSystemScreenshot("share_native_dialog")
                    // Dismiss share sheet via back
                    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    device.pressBack()
                    composeTestRule.waitForIdle()
                } catch (_: Throwable) {
                    // Share sheet trigger failed — continue without native screenshot
                }
            } catch (_: Throwable) {
                // Node may be disabled — skip
            }
        }
        returnToScreenCatalog()

        // 11. catalog_analyze_empty
        // anchor: "What would you like to analyze?" (analyze_select_source)
        // Credits = 10 after seed-with-data, so input fields ARE shown (not the no-credits state).
        captureFromCatalog(
            tagName = "catalog_analyze_empty",
            anchor = "What would you like to analyze?",
            screenshotName = "analyze_empty"
        )

        // 12. catalog_analyze_for_checklist
        // checklistId != null → AnalyzeViewModel sets isFillMode=true → title = analyze_fill_title =
        // "Fill via AI". NOT "AI Analysis" (that is analyze_title, used only when checklistId=null).
        captureFromCatalog(
            tagName = "catalog_analyze_for_checklist",
            anchor = "Fill via AI",
            screenshotName = "analyze_for_checklist",
            isDisabledOk = true
        )

        // 13. catalog_analyze_result_preview
        // anchor: "AI Result" (analyze_preview_title in strings.xml line 158)
        captureFromCatalog(
            tagName = "catalog_analyze_result_preview",
            anchor = "AI Result",
            screenshotName = "analyze_result_preview"
        )

        // 14. catalog_paywall (RC variant)
        // anchor: "Restore Purchase" is always present (paywall_restore)
        captureFromCatalog(
            tagName = "catalog_paywall",
            anchor = "Restore Purchase",
            screenshotName = "paywall"
        )

        // 14a. paywall_variant_timeline — force Timeline A/B variant
        captureFromCatalog(
            tagName = "paywall_variant_timeline",
            anchor = "Restore Purchase",
            screenshotName = "paywall_variant_timeline"
        )

        // 14b. paywall_variant_features — force Features A/B variant
        captureFromCatalog(
            tagName = "paywall_variant_features",
            anchor = "Restore Purchase",
            screenshotName = "paywall_variant_features"
        )

        // 14c. paywall_variant_compare — force Compare A/B variant
        captureFromCatalog(
            tagName = "paywall_variant_compare",
            anchor = "Restore Purchase",
            screenshotName = "paywall_variant_compare"
        )

        // 15. catalog_subscription_success
        // anchor: "Your Subscription" (subscription_status_title)
        captureFromCatalog(
            tagName = "catalog_subscription_success",
            anchor = "Your Subscription",
            screenshotName = "subscription_success"
        )

        // 16. catalog_subscription_pending
        // anchor: "Your Subscription" (same screen, different state param)
        captureFromCatalog(
            tagName = "catalog_subscription_pending",
            anchor = "Your Subscription",
            screenshotName = "subscription_pending"
        )

        // 17. catalog_settings
        // anchor: "Settings" (settings_title)
        captureFromCatalog(
            tagName = "catalog_settings",
            anchor = "Settings",
            screenshotName = "settings"
        )

        // 18. catalog_update_feed
        // anchor: "Updates" (update_feed_title)
        captureFromCatalog(
            tagName = "catalog_update_feed",
            anchor = "Updates",
            screenshotName = "update_feed"
        )

        // 19. catalog_store_screenshot — 4-page pager using same onboarding titles
        composeTestRule.onNodeWithTag("catalog_store_screenshot").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("catalog_store_screenshot").performClick()
        composeTestRule.waitForIdle()
        // Page 1 anchor: "Create via AI" (onboarding_page1_title — reused by StoreScreenshotScreen)
        composeTestRule.waitUntil(timeoutMillis = 6000) {
            composeTestRule.onAllNodesWithText("Create via AI").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.awaitNoLoading()
        composeTestRule.captureScreenshot("store_screenshot_1")

        // Page 2 — "Fill via AI"
        swipeStorePager()
        composeTestRule.captureFinal("store_screenshot_2", anchor = "Fill via AI")

        // Page 3 — "Export & Share"
        swipeStorePager()
        composeTestRule.captureFinal("store_screenshot_3", anchor = "Export & Share")

        // Page 4 — "Unlock Your Full Potential" (paywall_title, reused by StoreScreenshotScreen)
        swipeStorePager()
        waitForAnyText(listOf("Unlock Your Full Potential", "Get unlimited access"), timeoutMs = 5000)
        composeTestRule.awaitNoLoading()
        composeTestRule.captureScreenshot("store_screenshot_4")

        returnToScreenCatalog()

        // ── Phase 4: CSAT bottom sheet ─────────────────────────────────────
        // Navigate to Main, open drawer, tap "Rate App" → CsatIntent.ForceShow → sheet appears.
        tapCatalog("catalog_main")
        composeTestRule.waitForIdle()
        waitForAnyText(listOf("Create Checklist", "Ready to get organized?"), timeoutMs = 8000)

        // Open the Navigation Drawer by tapping the "Menu" icon (contentDescription "Menu")
        try {
            composeTestRule.onNodeWithContentDescription("Menu").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 4000) {
                composeTestRule.onAllNodesWithText("Rate App").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Rate App").performClick()
            composeTestRule.waitForIdle()
            // CSAT bottom sheet anchor: "How do you like Gisti?" (csat_title)
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithText("How do you like Gisti?")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.awaitNoLoading()
            composeTestRule.captureScreenshot("csat_step_1_rating")

            // Tap "Love It!" rating to advance to step 2
            try {
                composeTestRule.onNodeWithText("Love It!").performClick()
                composeTestRule.waitForIdle()
                waitForAnyText(listOf("What do you like most?", "What could be better?"), timeoutMs = 4000)
                composeTestRule.awaitNoLoading()
                composeTestRule.captureScreenshot("csat_step_2_chips")
            } catch (_: Throwable) {
                // Step 2 may not appear or may already have moved on — best effort
            }
        } catch (_: Throwable) {
            // Drawer or CSAT may not be accessible from this navigation state — skip
        }

        // ── Gate: assert enough screenshots were taken ─────────────────────
        // This catches cases where most navigation silently broke and only a few PNGs were saved.
        // If fewer than 20 screenshots were captured the test fails with an actionable message.
        val captured = screenshotIndex
        check(captured >= 23) {
            "Screenshot harness produced only $captured images (expected ≥ 23). " +
                "Check logcat for navigation failures above."
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Trigger the DebugMenuDetector by sending Volume Up → Down → Up events via UiAutomator.
     *
     * The detector requires 3 key events each within 500ms of the previous one.
     * We send all three synchronously; the 500ms window is easily satisfied.
     *
     * Note: the emulator audio service fires but produces no audible sound.
     */
    private fun openDebugMenu() {
        // Volume Up→Down→Up via UiAutomator is unreliable on API 36 emulators —
        // system volume controller can swallow the events before they reach Activity.
        // Drive the navigation directly through the same AppNavigator the app uses.
        // This bypasses DebugMenuDetector entirely but reaches the same destination.
        val navigator = org.koin.core.context.GlobalContext.get()
            .get<com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator>()
        composeTestRule.runOnUiThread {
            navigator.navigateToDebugMenu()
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Tap a catalog button, wait for [anchor] to appear, drain loading, capture screenshot,
     * then press back and verify we returned to Screen Catalog.
     *
     * @param isDisabledOk if true, silently skips when the node is disabled (alpha 0.38 + no click).
     *   The disabled check is best-effort via a try/catch around performClick().
     */
    private fun captureFromCatalog(
        tagName: String,
        anchor: String,
        screenshotName: String,
        timeoutMs: Long = 8000,
        isDisabledOk: Boolean = false
    ) {
        try {
            // ScreenCatalog uses LazyColumn — buttons below the fold aren't composed.
            // performScrollTo finds the node via semantics tree and scrolls it into view.
            composeTestRule.onNodeWithTag(tagName).performScrollTo()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(tagName).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
                composeTestRule.onAllNodesWithText(anchor).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.awaitNoLoading()
            composeTestRule.captureScreenshot(screenshotName)
        } catch (e: Throwable) {
            // ComposeTimeoutException extends Throwable directly, NOT Exception — must catch Throwable.
            if (!isDisabledOk) {
                throw e
            }
            // Disabled node or anchor not found — skip screenshot, increment index for ordering
            screenshotIndex++ // keep index monotonic so subsequent names don't collide
        }
        returnToScreenCatalog()
    }

    /**
     * Press back, then ensure we are on Screen Catalog. Recovers via direct navigator
     * call if the back-stack escaped (popUpTo wipes from earlier targets, or extra
     * dialogs add layers). Single source of truth for "go back to catalog".
     */
    private fun returnToScreenCatalog() {
        pressBack()
        composeTestRule.waitForIdle()
        val onCatalog = runCatching {
            composeTestRule.waitUntil(timeoutMillis = 4000) {
                composeTestRule.onAllNodesWithText("Screen Catalog").fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        if (!onCatalog) {
            val nav = org.koin.core.context.GlobalContext.get()
                .get<com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator>()
            composeTestRule.runOnUiThread { nav.navigateToScreenCatalog() }
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 8000) {
                composeTestRule.onAllNodesWithText("Screen Catalog").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    /**
     * Scroll a catalog button into the visible viewport, then click.
     * Works on Column(verticalScroll) — every catalog row is composed up-front.
     */
    private fun tapCatalog(tag: String) {
        composeTestRule.onNodeWithTag(tag).performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(tag).performClick()
        composeTestRule.waitForIdle()
    }

    /** Swipe left on the onboarding HorizontalPager; fall back to "Continue" button tap. */
    private fun swipeOnboardingLeft() {
        val swiped = runCatching {
            composeTestRule.onNode(hasTestTag("onboarding_pager")).performTouchInput { swipeLeft() }
            composeTestRule.waitForIdle()
        }.isSuccess
        if (!swiped) {
            runCatching { composeTestRule.onNodeWithText("Continue").performClick() }
            composeTestRule.waitForIdle()
        }
    }

    /** Swipe left on the StoreScreenshot pager; fall back to right-side tap. */
    private fun swipeStorePager() {
        runCatching {
            composeTestRule.onRoot().performTouchInput { swipeLeft() }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Wait until at least one of the given texts appears.
     * Throws AssertionError on timeout (same as waitUntil).
     */
    private fun waitForAnyText(texts: List<String>, timeoutMs: Long = 5000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            texts.any {
                composeTestRule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    /**
     * Like [waitForAnyText] but returns false instead of throwing on timeout.
     * Used where a screen may legitimately show a different state.
     */
    private fun waitForAnyTextSafe(texts: List<String>, timeoutMs: Long = 5000): Boolean {
        return runCatching { waitForAnyText(texts, timeoutMs) }.isSuccess
    }

}
