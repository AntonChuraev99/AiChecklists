package com.antonchuraev.homesearchchecklist.aichat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the chat's message-key round-trip: the ViewModel and the dispatcher never hold copy, they
 * emit a string-resource KEY, and each chat surface resolves it through its own hand-written map.
 *
 * ```
 * ViewModel  ──ShowAssistantMessage("chat_dispatch_added_to")──►  App.kt / ChatRoute.kt
 *                                                                 currentMessages[key] ?: key   ← !!
 * ```
 *
 * The `?: key` fallback is why this test exists in a build that already has 459 green ones. A key
 * that no map carries does not throw, does not fail to compile and does not fail a ViewModel test
 * (those assert the KEY — correctly, that IS the ViewModel's output). It ships: the user reads
 * "chat_choice_dismissed_message" in the chat. D1 lost five keys exactly this way and only a live
 * run on :9090 caught it. Two maps, two surfaces, no compiler on either side.
 *
 * ─── Why the sources are parsed as TEXT ──────────────────────────────────────
 * Both maps are built inside @Composable functions from `stringResource(...)` calls, so they exist
 * only during composition: no unit test can call them, and a Compose-runtime test would assert the
 * resolution of the keys it was itself given. What must be checked is a property of the SOURCE —
 * "every key some producer can emit appears in both consumers" — so the source is what is read.
 *
 * The extraction is deliberately anchored to the emit/lookup syntax rather than to a bare
 * `"chat_*"` literal, and every extracted set is size-checked: a regex that silently stops matching
 * would otherwise turn each assertion below into `emptySet ⊆ anything` — green and worthless.
 */
class ChatMessageKeyResolutionTest {

    // ── Sources under inspection ─────────────────────────────────────────────

    private val viewModelSource = source(
        "feature/aichat/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/" +
            "feature/aichat/impl/presentation/ChatViewModel.kt",
    )
    private val dispatcherSource = source(
        "composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/aichat/ToolCallDispatcherImpl.kt",
    )
    private val appSource = source("composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/App.kt")
    private val chatRouteSource = source(
        "feature/aichat/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/" +
            "feature/aichat/impl/presentation/ChatRoute.kt",
    )

    // ── Producers: every key that can reach a surface ────────────────────────

    /** `ShowSnackbar("chat_x")` / `ShowAssistantMessage(messageKey = "chat_x", …)`. */
    private val viewModelKeys: Set<String> =
        Regex("""Show(?:Snackbar|AssistantMessage)\(\s*(?:messageKey\s*=\s*)?"(chat_[a-z0-9_]+)"""")
            .findAll(viewModelSource).map { it.groupValues[1] }.toSet()

    /** `DispatchOutcome.Success("chat_x", …)` / `.NotFound("chat_x")` — relayed verbatim by the VM. */
    private val dispatcherKeys: Set<String> =
        Regex("""DispatchOutcome\.(?:Success|NotFound)\(\s*(?:messageKey\s*=\s*)?"(chat_[a-z0-9_]+)"""")
            .findAll(dispatcherSource).map { it.groupValues[1] }.toSet()

    // ── Consumers: the two hand-written resolution maps ──────────────────────

    private val appMapKeys: Set<String> = mapKeys(appSource)
    private val chatRouteMapKeys: Set<String> = mapKeys(chatRouteSource)

    /**
     * The two surfaces must resolve the SAME keys. Nothing in the build ties them together: the dock
     * (App.kt) and the full screen (ChatRoute.kt) each keep their own copy of the map, and a key
     * added to one is silently missing from the other — the exact shape of the D1 regression.
     */
    @Test
    fun messageKeyMaps_appDockAndChatRoute_carryIdenticalKeySets() {
        assertTrue(appMapKeys.size >= 40, "App.kt map extraction found only ${appMapKeys.size} keys — regex stale")
        assertTrue(
            chatRouteMapKeys.size >= 40,
            "ChatRoute.kt map extraction found only ${chatRouteMapKeys.size} keys — regex stale",
        )
        assertEquals(
            emptySet(),
            chatRouteMapKeys - appMapKeys,
            "keys resolved on the full chat screen but NOT in the App.kt dock map — they render as the raw key " +
                "in the dock",
        )
        assertEquals(
            emptySet(),
            appMapKeys - chatRouteMapKeys,
            "keys resolved in the App.kt dock but NOT in the ChatRoute.kt map — they render as the raw key " +
                "on the full chat screen",
        )
    }

    /**
     * Every key a producer can emit must be resolvable on BOTH surfaces. Unresolved, it reaches the
     * user as its own name.
     */
    @Test
    fun messageKeyMaps_resolveEveryKeyTheChatCanEmit() {
        assertTrue(viewModelKeys.size >= 15, "ViewModel key extraction found only ${viewModelKeys.size} — regex stale")
        assertTrue(
            dispatcherKeys.size >= 20,
            "dispatcher key extraction found only ${dispatcherKeys.size} — regex stale",
        )
        val emitted = viewModelKeys + dispatcherKeys

        assertEquals(
            emptySet(),
            emitted - appMapKeys,
            "emitted by the chat but absent from the App.kt dock map → shown to the user as the raw key",
        )
        assertEquals(
            emptySet(),
            emitted - chatRouteMapKeys,
            "emitted by the chat but absent from the ChatRoute.kt map → shown to the user as the raw key",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** `"chat_x" to sm_x` — one entry of a surface's resolution map. */
    private fun mapKeys(source: String): Set<String> =
        Regex(""""(chat_[a-z0-9_]+)"\s+to\s+""").findAll(source).map { it.groupValues[1] }.toSet()

    private fun source(relativePath: String): String {
        val file = File(repoRoot(), relativePath)
        assertTrue(file.isFile, "source not found: ${file.absolutePath} — was the file moved?")
        return file.readText()
    }

    /** Walks up from the test's working directory to the Gradle root (module dir is not guaranteed). */
    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("repo root (settings.gradle.kts) not found above ${File(".").absolutePath}")
    }
}
