package com.antonchuraev.homesearchchecklist.navigation

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Structural pins for the two chat-entry-point invariants that no composed tree can observe.
 *
 * Both are about wiring that lives in App.kt and V2ChatDockOverlay.kt and only shows up as WRONG
 * DATA in production analytics or in a chat answer about the wrong list — by which point the A/B
 * arm has already been measured. A UI test cannot reach either: `chatSheetContextId` is a
 * `rememberSaveable` local inside App.kt's composition, and the double-`ai_chat_opened` defect is a
 * property of which effect shape reports the dock's open transition.
 *
 * These read the SOURCE, so they are guards, not behaviour tests — kept deliberately narrow (a
 * handful of anchors each) and every extraction is size-checked, because a regex that silently stops
 * matching turns into "assert true".
 *
 * Run: ./gradlew :composeApp:testAndroidHostTest --tests "*V2ChatEntryPointSourceGuardTest*"
 */
class V2ChatEntryPointSourceGuardTest {

    // ── B7. The bar's AI tap clears the checklist context ────────────────────

    /**
     * Catches: a chat opened from the bar staying anchored to a checklist the user has already left.
     *
     * Scenario the pin encodes: open the chat from a checklist's detail screen (which seeds
     * `chatSheetContextId`), close it, go back to a tab, tap the bar's AI button. The context is a
     * `rememberSaveable` that SURVIVES the dock closing, so unless the bar's handler clears it first,
     * the next chat silently answers "what's missing?" about the abandoned list.
     *
     * Order matters and is asserted: clearing after `v2ChatDockOpen = true` is a race with the
     * overlay's open effect, which reads the context to seed the chat.
     */
    @Test
    fun v2ShellOnOpenChat_clearsTheChecklistContextBeforeOpeningTheDock() {
        val handler = extractV2OnOpenChatHandler()

        val contextIdCleared = handler.indexOf("chatSheetContextId = null")
        val contextLabelCleared = handler.indexOf("chatSheetContextLabel = null")
        val dockOpened = handler.indexOf("v2ChatDockOpen = true")

        assertTrue(
            contextIdCleared >= 0,
            "The v2 shell's onOpenChat must clear chatSheetContextId — the bar button belongs to no " +
                "checklist. Handler was:\n$handler",
        )
        assertTrue(
            contextLabelCleared >= 0,
            "The v2 shell's onOpenChat must clear chatSheetContextLabel too, or the chat shows a " +
                "context banner naming a list it is not anchored to. Handler was:\n$handler",
        )
        assertTrue(
            dockOpened >= 0,
            "Expected the handler to open the chat dock; the anchor moved. Handler was:\n$handler",
        )
        assertTrue(
            contextIdCleared < dockOpened && contextLabelCleared < dockOpened,
            "The context must be cleared BEFORE the dock opens — the overlay seeds the chat from it " +
                "on its open transition. Handler was:\n$handler",
        )
    }

    // ── B8. One `ai_chat_opened` per open ────────────────────────────────────

    /**
     * Catches the known doubling: reporting the dock's open state through a derived boolean.
     *
     * `LaunchedEffect(expanded) { onExpandedChanged(expanded) }` also emits its INITIAL value — the
     * dock mounts at Peek, so it reports `false` first. Two of the three entry points set the host's
     * chat-open flag BEFORE mounting the overlay, so that spurious `false` cleared the flag and the
     * following `true` re-set it: `ai_chat_opened` fired TWICE for one open on those paths and once
     * via the button. Chat engagement is this experiment's headline metric, so a per-entry-point
     * difference in open counts makes the A/B unreadable.
     *
     * The shape that is correct — and that this pins — is a one-shot: wait for the first transition
     * to Expanded and report it once, then report the close TERMINALLY from `onDispose` (BACK and
     * scrim taps dismiss without the dock settling anywhere).
     */
    @Test
    fun v2ChatDockOverlay_reportsTheOpenOnce_notThroughADerivedBooleanMirror() {
        val source = strippedSource("src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/navigation/V2ChatDockOverlay.kt")

        assertTrue(
            source.contains("snapshotFlow { dockState.targetValue }"),
            "The open transition must be observed as a one-shot over dockState.targetValue",
        )
        assertTrue(
            source.contains("first { it == DockAnchor.Expanded }"),
            "The one-shot must await the FIRST arrival at Expanded — anything re-triggerable " +
                "re-fires ai_chat_opened mid-session",
        )
        assertTrue(
            source.contains("onDispose") && source.contains("onExpandedChangedState.value(false)"),
            "The close must be reported terminally from onDispose, or BACK/scrim dismissals leave " +
                "the host believing the chat is still open and the NEXT open fires no analytics",
        )
        assertTrue(
            !source.contains("LaunchedEffect(expanded)"),
            "A LaunchedEffect keyed on a derived `expanded` boolean re-introduces the initial-value " +
                "emission that doubled ai_chat_opened",
        )
    }

    // ── The exit is interruptible, and it is announced when it STARTS ────────

    /**
     * Two properties of the dock's exit that no composed test in this module can reach — the dock's
     * drag anchors are published from deep inside `GistiGlassChatDock`, so there is no tree here in
     * which a swipe can be simulated at all. Pinned structurally, deliberately narrow, and honest
     * about being a placeholder for a device run.
     *
     * 1. **The latch is reversible.** It used to be one-way: `closing` flipped true and a 160ms fade
     *    ran to completion no matter what. Catch the dock on its way down and drag it back up and the
     *    panel followed the finger while the fade kept going underneath — the dock disappeared from
     *    under the hand that was saving it, and the host was told to unmount. A gesture reversible in
     *    every other direction has to be reversible here.
     * 2. **The host hears about the exit at its START.** The raised AI button is the other half of the
     *    transition; it can only grow back WHILE the dock fades if it is told when the fade begins.
     *    Reporting only at the end is what left the middle of the bar empty for 160ms.
     */
    @Test
    fun v2ChatDockOverlay_exitIsInterruptible_andIsReportedWhenItStarts() {
        val source = strippedSource("src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/navigation/V2ChatDockOverlay.kt")

        assertTrue(
            source.contains("closing = false"),
            "The exit latch must be able to go back to false, or a dock caught mid-exit is dismissed " +
                "under the finger that caught it",
        )
        assertTrue(
            source.contains("onClosingChangedState.value(true)") &&
                source.contains("onClosingChangedState.value(false)"),
            "The host must be told when the exit starts AND when it is interrupted — the AI button's " +
                "return rides on the first and has to be undone by the second",
        )

        val exitStarted = source.indexOf("onClosingChangedState.value(true)")
        val hostUnmounts = source.indexOf("onDismissRequestState.value()")
        assertTrue(
            hostUnmounts > exitStarted,
            "The exit must be announced BEFORE the host is asked to unmount, not alongside it — " +
                "reporting only at the end is the sequential hand-off this replaced " +
                "(start=$exitStarted, unmount=$hostUnmounts)",
        )
    }

    // ── Source access ────────────────────────────────────────────────────────

    /**
     * The `onOpenChat = { ... }` argument of the `showV2Shell -> V2NavigationShell(` call.
     *
     * Bounded by the next named argument rather than by brace matching: App.kt's handlers are heavily
     * commented and a naive brace counter trips over braces inside strings. The size check below is
     * the part that matters — an extraction that quietly returns nothing would make every assertion
     * above pass against an empty haystack.
     */
    private fun extractV2OnOpenChatHandler(): String {
        val source = strippedSource("src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/App.kt")

        val shellCall = source.indexOf("showV2Shell -> V2NavigationShell(")
        assertTrue(shellCall >= 0, "Could not find the v2 shell call site in App.kt — anchor moved")

        val handlerStart = source.indexOf("onOpenChat = {", shellCall)
        assertTrue(handlerStart in (shellCall + 1)..(shellCall + 4_000), "Could not find onOpenChat in the v2 shell call")

        val handlerEnd = source.indexOf("showCreateFab", handlerStart)
        assertTrue(handlerEnd > handlerStart, "Could not find the argument that follows onOpenChat")

        val handler = source.substring(handlerStart, handlerEnd)
        assertTrue(
            handler.length in 40..4_000,
            "Extracted onOpenChat handler looks wrong (${handler.length} chars) — the guard would " +
                "be asserting against a broken extraction",
        )
        return handler
    }

    /**
     * Source with LINE comments removed, so a guard can never match the `//` note that explains the
     * very anti-pattern it forbids.
     *
     * Block comments are deliberately left in place. Stripping them looks safer and is not: App.kt
     * contains MIME-type string literals such as the one for images, whose slash-star opens a block
     * comment that runs to the next star-slash and swallowed 57% of the file
     * (165_950 to 70_668 chars) — taking the anchor this guard looks for with it, so the test failed
     * with "anchor moved" against source that was perfectly fine. The ratio check below is what
     * makes that class of accident loud instead of silent.
     *
     * (Kotlin block comments also NEST, so writing the pattern out literally here would open a
     * comment inside this KDoc and break the file — hence the prose.)
     */
    private fun strippedSource(relativePath: String): String {
        val file = resolveModuleFile(relativePath)
        val raw = file.readText()
        assertTrue(raw.length > 1_000, "${file.absolutePath} is suspiciously small (${raw.length} chars)")
        val stripped = raw.replace(Regex("//[^\n]*"), " ")
        assertTrue(
            stripped.length > raw.length * 0.6,
            "Comment stripping removed ${raw.length - stripped.length} of ${raw.length} chars of " +
                "${file.name} — the guard would be asserting against mangled source",
        )
        return stripped
    }

    /**
     * Resolves a path relative to the composeApp module, whichever directory the test runner is
     * started from. Fails with the paths it tried rather than returning a missing file.
     */
    private fun resolveModuleFile(relativePath: String): File {
        val candidates = buildList {
            add(File(relativePath))
            add(File("composeApp", relativePath))
            var dir: File? = File("").absoluteFile
            repeat(4) {
                dir = dir?.parentFile
                dir?.let {
                    add(File(it, relativePath))
                    add(File(it, "composeApp/$relativePath"))
                }
            }
        }
        val found = candidates.firstOrNull { it.isFile }
        assertTrue(
            found != null,
            "Source not found. Working dir=${File("").absolutePath}; tried=${candidates.map { it.absolutePath }}",
        )
        return found!!
    }
}
