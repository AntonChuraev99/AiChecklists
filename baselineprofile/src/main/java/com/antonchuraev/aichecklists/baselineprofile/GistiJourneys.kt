package com.antonchuraev.aichecklists.baselineprofile

import android.graphics.Rect
import android.os.Build
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/*
 * Critical user journeys shared by BaselineProfileGenerator and the Macrobenchmarks.
 *
 * Selectors use visible TEXT / content-description only: production code exposes no testTag as
 * resource-id (testTagsAsResourceId), and these tests must not require prod changes. They assume
 * the English UI. Every step past "reach Inbox" is best-effort: a missing optional element is
 * logged and skipped, so profile collection still produces a profile instead of failing.
 */

private const val TAG = "GistiJourney"

/** applicationId of :androidApp, used when the `targetAppId` runner argument is absent. */
private const val DEFAULT_TARGET_APP_ID = "com.antonchuraev.aichecklists"

private const val UI_TIMEOUT_MS = 5_000L
private const val SHORT_TIMEOUT_MS = 1_500L
private const val IDLE_TIMEOUT_MS = 2_000L
private const val MAX_DISMISS_ROUNDS = 8

private const val TAB_INBOX = "Inbox"
private const val TAB_CALENDAR = "Calendar"
private const val TAB_PROJECTS = "Projects"
private const val TAB_OVERVIEW = "Overview"
private const val ASK_PILL = "Ask Gisti or add a task"

/** Empty-Inbox primary button: opens the capture dock with the keyboard. NOT a task row. */
private const val ADD_TASK = "Add task"

/** First row of ItemDetailsSheet — proves the task sheet (not the capture dock) opened. */
private const val TASK_SHEET_MARKER = "Reminder"

/**
 * Task the generator creates on a fresh install: the generation run installs a clean APK (no
 * projects, empty Inbox), so without it there is no task row to open the item sheet on. Added via
 * the capture dock = local Room write, no network, no AI credits. `input text` needs %s for spaces.
 */
private const val SEED_TASK = "Profile seed task"
private const val SEED_TASK_INPUT = "Profile%sseed%stask"

/** Promo sheets that may cover the Inbox on any launch (e.g. widget promo "Not now"). */
private val PROMO_DISMISS: Pattern = Pattern.compile("(?i)^(not now|got it)$")

/** Onboarding steps on a fresh install (arm chosen by Remote Config). */
private val ONBOARDING_ADVANCE: Pattern = Pattern.compile("(?i)^(skip|continue|get started)$")

/** Inbox subtitle, e.g. "3 tasks" / "1 task". The project chip row sits right under it. */
private val TASKS_SUBTITLE: Pattern = Pattern.compile("(?i)^\\d+ tasks?$")

fun targetAppId(): String =
    InstrumentationRegistry.getArguments().getString("targetAppId") ?: DEFAULT_TARGET_APP_ID

/** Avoids the POST_NOTIFICATIONS system dialog covering the bottom bar after start. */
fun MacrobenchmarkScope.grantNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
    }
}

/** Startup journey: cold/warm start to the Inbox. Optional first-run UI is handled, never fatal. */
fun MacrobenchmarkScope.startToInbox() {
    pressHome()
    grantNotificationPermission()
    startActivityAndWait()
    reachInbox()
}

/** Waits for the Inbox, clearing promo sheets and onboarding on the way. Returns false on timeout. */
fun MacrobenchmarkScope.reachInbox(): Boolean {
    repeat(MAX_DISMISS_ROUNDS) {
        device.findObject(By.text(PROMO_DISMISS))?.let { button ->
            button.click()
            device.waitForIdle(IDLE_TIMEOUT_MS)
            return@repeat
        }
        if (bottomTab(TAB_INBOX, SHORT_TIMEOUT_MS) != null) return true

        val advance = device.findObject(By.text(ONBOARDING_ADVANCE))
            ?: device.findObject(By.desc("Close"))
        if (advance != null) {
            advance.click()
            device.waitForIdle(IDLE_TIMEOUT_MS)
        } else if (device.wait(Until.hasObject(By.text(TAB_INBOX)), UI_TIMEOUT_MS) != true) {
            // Unknown screen (e.g. paywall without a visible close): BACK, relaunch if it exited.
            device.pressBack()
            ensureInApp()
        }
    }
    val reached = bottomTab(TAB_INBOX, UI_TIMEOUT_MS) != null
    if (!reached) Log.w(TAG, "Inbox not reached after $MAX_DISMISS_ROUNDS rounds")
    return reached
}

/**
 * Post-startup journey (NOT part of the Startup Profile), starting on the Inbox page (a cold start
 * lands on it, often EMPTY): capture dock (empty-Inbox "Add task") → BACK ×2 → project chip →
 * fling the list → open a task sheet → BACK → open the chat dock (nothing typed/sent: no network,
 * no credits) → BACK ×2 → Calendar, Projects, Overview, Inbox tabs.
 *
 * [seedInbox] = false skips the capture-dock step: a benchmark seeds in its setupBlock via
 * [seedInboxIfEmpty], so the one-off seed (dock + IME + Room write) never lands in measured frames
 * of only one iteration / one compilation mode. The profile generator keeps the default (true): it
 * wants the capture-dock code in the profile.
 */
fun MacrobenchmarkScope.openActionsJourney(seedInbox: Boolean = true) {
    if (seedInbox) seedInboxIfEmpty()
    openSecondProjectChip()
    flingMainList()
    openAndCloseTaskSheet()
    openAndCloseChatDock()
    listOf(TAB_CALENDAR, TAB_PROJECTS, TAB_OVERVIEW, TAB_INBOX).forEach(::switchTab)
}

/**
 * Empty Inbox only: "Add task" opens the capture dock + keyboard; types and submits [SEED_TASK]
 * (IME action) so the task-sheet step has a row. BACK hides the keyboard, BACK closes the dock.
 * No-op when the Inbox already has tasks (no "Add task" button). Expects to start on the Inbox.
 */
fun MacrobenchmarkScope.seedInboxIfEmpty() {
    val addTask = device.findObject(By.pkg(packageName).text(ADD_TASK)) ?: return // Inbox has tasks
    addTask.click()
    device.waitForIdle(IDLE_TIMEOUT_MS)
    device.executeShellCommand("input text $SEED_TASK_INPUT")
    device.waitForIdle(IDLE_TIMEOUT_MS)
    // ENTER inserts a newline (multi-line field); the round "+" submits. It has no
    // contentDescription, so it is found by geometry: the right-most clickable node on the
    // input field's row.
    val field = device.findObject(By.pkg(packageName).clazz("android.widget.EditText"))
    val submit = field?.let { input ->
        val row = input.visibleBounds
        device.findObjects(By.pkg(packageName).clickable(true))
            .filter { it.visibleBounds.centerY() in row.top..row.bottom && it.visibleBounds.left > row.centerX() }
            .maxByOrNull { it.visibleBounds.left }
    }
    if (submit == null) {
        Log.w(TAG, "Capture dock submit button not found")
    } else {
        submit.click()
        device.waitForIdle(IDLE_TIMEOUT_MS)
    }
    // The dock stays open after a submit (ready for the next task): BACK until the bar is back.
    repeat(3) {
        if (bottomTab(TAB_INBOX, SHORT_TIMEOUT_MS) != null && !device.hasObject(By.clazz("android.widget.EditText"))) return@repeat
        device.pressBack()
        device.waitForIdle(IDLE_TIMEOUT_MS)
    }
    ensureInApp()
}

private fun MacrobenchmarkScope.openSecondProjectChip() {
    val subtitle = device.wait(Until.findObject(By.text(TASKS_SUBTITLE)), UI_TIMEOUT_MS)
    if (subtitle == null) {
        Log.w(TAG, "'N tasks' subtitle not found, skipping project chip")
        return
    }
    val subtitleBottom = subtitle.visibleBounds.bottom
    // Chip row = the TEXT nodes in the band right under the subtitle (first = "Inbox", second = the
    // first project). Neither By.scrollable nor By.clickable: on the V2 Inbox the row is not exposed
    // as a scrollable container and the chips not as clickable nodes (both observed on the emulator).
    val band = device.displayHeight / 10
    val chips = device.findObjects(By.pkg(packageName).text(Pattern.compile(".+")))
        .filter { it.visibleBounds.top >= subtitleBottom - 4 && it.visibleBounds.top <= subtitleBottom + band }
        .sortedBy { it.visibleBounds.left }
    val chip = chips.getOrNull(1) ?: chips.firstOrNull()
    if (chip == null) {
        Log.w(TAG, "Project chip row not found")
        return
    }
    chip.click()
    device.waitForIdle(IDLE_TIMEOUT_MS)
}

private fun MacrobenchmarkScope.mainList(): UiObject2? =
    device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.area() }

private fun MacrobenchmarkScope.flingMainList() {
    val list = mainList()
    if (list == null) {
        Log.w(TAG, "No scrollable list to fling")
        return
    }
    // Keep the gesture away from the system gesture areas at the screen edges.
    list.setGestureMargin(device.displayWidth / 5)
    list.fling(Direction.DOWN)
    device.waitForIdle(IDLE_TIMEOUT_MS)
    list.fling(Direction.UP)
    device.waitForIdle(IDLE_TIMEOUT_MS)
}

private fun MacrobenchmarkScope.openAndCloseTaskSheet() {
    // The seed task (fresh install) first; otherwise a full-width clickable row of the main list.
    // Task rows span (almost) the full width; chips and icon buttons do not.
    val minRowWidth = device.displayWidth / 2
    val row = device.findObject(By.pkg(packageName).textStartsWith(SEED_TASK))
        ?: mainList()?.findObjects(By.clickable(true))
            ?.filter { it.visibleBounds.width() >= minRowWidth }
            ?.firstNotNullOfOrNull { it.findObject(By.text(Pattern.compile(".+"))) }
            ?.takeIf { it.text != ADD_TASK } // empty page: the wide "Add task" button is not a task row
    if (row != null) {
        // Click the title text, not the row start (checkbox) — must not complete a task.
        row.click()
    } else {
        // V2 Inbox rows do not expose their text to UiAutomator (observed: absent from the dump).
        // Fallback by geometry: the first row sits right under the "N tasks" subtitle; tap its
        // text area (60% of the width), never the checkbox on the left.
        val subtitle = device.findObject(By.text(TASKS_SUBTITLE))
        if (subtitle == null || subtitle.text.startsWith("0 ")) {
            Log.w(TAG, "No task row found")
            return
        }
        device.click(device.displayWidth * 6 / 10, subtitle.visibleBounds.bottom + device.displayHeight / 20)
    }
    device.waitForIdle(IDLE_TIMEOUT_MS)
    if (!device.wait(Until.hasObject(By.text(TASK_SHEET_MARKER)), UI_TIMEOUT_MS)) {
        Log.w(TAG, "Task sheet did not open after tapping '${row?.text ?: "first row (geometry)"}'")
    }
    device.pressBack()
    device.waitForIdle(IDLE_TIMEOUT_MS)
    ensureInApp()
}

private fun MacrobenchmarkScope.openAndCloseChatDock() {
    val pill = device.wait(Until.findObject(By.text(ASK_PILL)), UI_TIMEOUT_MS)
    if (pill == null) {
        Log.w(TAG, "Ask pill not found")
        return
    }
    pill.click()
    device.waitForIdle(IDLE_TIMEOUT_MS)
    device.pressBack() // hides the keyboard
    device.waitForIdle(IDLE_TIMEOUT_MS)
    device.pressBack() // closes the dock
    device.waitForIdle(IDLE_TIMEOUT_MS)
    ensureInApp()
}

private fun MacrobenchmarkScope.switchTab(label: String) {
    val tab = bottomTab(label, UI_TIMEOUT_MS)
    if (tab == null) {
        Log.w(TAG, "Tab '$label' not found")
        return
    }
    tab.click()
    device.waitForIdle(IDLE_TIMEOUT_MS)
}

/**
 * The bottom-bar item for [label]. The same text is also a screen title, so the lowest match on
 * screen is the tab. Compose puts the text in a child node; clicking its center hits the item.
 */
private fun MacrobenchmarkScope.bottomTab(label: String, timeoutMs: Long): UiObject2? {
    if (device.wait(Until.hasObject(By.pkg(packageName).text(label)), timeoutMs) != true) return null
    return device.findObjects(By.pkg(packageName).text(label))
        .maxByOrNull { it.visibleBounds.centerY() }
}

/** A BACK on the root screen finishes the activity; bring the app back so the journey continues. */
private fun MacrobenchmarkScope.ensureInApp() {
    if (device.currentPackageName != packageName) {
        Log.w(TAG, "Left the app, relaunching")
        startActivityAndWait()
        reachInbox()
    }
}

private fun Rect.area(): Int = width() * height()
