package com.antonchuraev.homesearchchecklist.navigation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.nav_ai_chat
import aichecklists.core.designsystem.generated.resources.nav_chat_fab_content_description
import aichecklists.core.designsystem.generated.resources.nav_tab_overview
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v1 ("Classic view", the A/B CONTROL arm) must come out of this redesign unchanged.
 *
 * The two arms share every screen and, on purpose, NOT their shell: `V2NavigationShell` exists as a
 * separate file precisely so the control arm's chrome file needs no edit, "the cheapest available
 * proof that the baseline arm did not move" (its own KDoc). This test makes that proof executable
 * from both sides — what the control shell RENDERS, and what its source is allowed to reference.
 *
 * Run: ./gradlew :composeApp:testAndroidHostTest --tests "*ControlArmShellRegressionTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlArmShellRegressionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private companion object {
        const val CONTENT_MARKER = "CONTROL ARM SCREEN CONTENT"
    }

    /** The control drawer resolves an [AppLogger] with `koinInject()` for its link handling. */
    @Before
    fun startKoinWithNoopLogger() {
        stopKoin()
        startKoin { modules(module { single<AppLogger> { NoopLogger } }) }
    }

    @After
    fun stopKoinAfterTest() {
        stopKoin()
    }

    /**
     * Catches: v2 chrome leaking into the control arm — a bottom bar with v2 tabs, or the new
     * centred AI button, appearing for users who chose the classic layout.
     *
     * The `nav_ai_chat` assertion is the non-vacuity anchor: it proves the control drawer really
     * composed, so the two "must not exist" assertions below are answering about a rendered shell
     * rather than about an empty tree. It is `assertExists`, not `assertIsDisplayed`, because at
     * Compact the drawer is closed and parked off-screen.
     */
    @Test
    fun controlShell_compact_rendersItsOwnChrome_withNoV2Additions() {
        var v2ChatLabel = ""
        var v2OverviewTab = ""
        var v1DrawerChatLabel = ""
        composeTestRule.setContent {
            v2ChatLabel = stringResource(Res.string.nav_chat_fab_content_description)
            v2OverviewTab = stringResource(Res.string.nav_tab_overview)
            v1DrawerChatLabel = stringResource(Res.string.nav_ai_chat)
            AppTheme(darkTheme = false) {
                AdaptiveNavigationShell(
                    selectedDestination = DrawerDestination.Main,
                    onNavigate = {},
                    onRateApp = {},
                    onLeaveFeedback = {},
                    versionName = "1.18.7",
                    content = {
                        Box(modifier = Modifier.fillMaxSize()) { Text(CONTENT_MARKER) }
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(CONTENT_MARKER).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(v1DrawerChatLabel).fetchSemanticsNodes().let { nodes ->
            assertTrue(
                nodes.isNotEmpty(),
                "Precondition: the control arm's own drawer content must have composed " +
                    "(looked for its '$v1DrawerChatLabel' item)",
            )
        }

        assertEquals(
            0,
            composeTestRule.onAllNodesWithContentDescription(v2ChatLabel).fetchSemanticsNodes().size,
            "The control arm must not grow the v2 AI button",
        )
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText(v2OverviewTab).fetchSemanticsNodes().size,
            "The control arm must not grow the v2 bottom-bar destinations",
        )
    }

    /**
     * The textual half of the same proof: the control shell's file may not reach for v2 symbols.
     *
     * A shared helper "just for the AI button" is how the arms stop being comparable — the control
     * arm silently gains part of the treatment and the experiment measures a difference that is no
     * longer the one being tested.
     */
    @Test
    fun controlShellSource_referencesNoV2NavigationSymbols() {
        val file = resolveModuleFile(
            "src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/navigation/AdaptiveNavigationShell.kt"
        )
        val raw = file.readText()
        assertTrue(raw.length > 1_000, "${file.absolutePath} is suspiciously small (${raw.length} chars)")
        // LINE comments only. Stripping block comments is unsafe on this codebase: MIME-type string
        // literals contain a slash-star that opens a block comment which runs on and eats real code
        // (it removed 57% of App.kt in an earlier revision of these guards). Ratio check keeps it loud.
        val source = raw.replace(Regex("//[^\n]*"), " ")
        assertTrue(
            source.length > raw.length * 0.6,
            "Comment stripping mangled ${file.name} (${raw.length} → ${source.length} chars)",
        )

        // Non-vacuity: the stripped source still contains the shell it is supposed to describe.
        assertTrue(
            source.contains("fun AdaptiveNavigationShell("),
            "Stripped source no longer contains the control shell — the guard is reading the wrong file",
        )

        listOf("V2NavigationShell", "V2Destination", "V2ShellMetrics", "AppNavigationBar").forEach { symbol ->
            assertTrue(
                !source.contains(symbol),
                "The control arm's shell must not reference '$symbol' — the arms are only comparable " +
                    "while the baseline chrome stays textually untouched",
            )
        }
    }

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

    private object NoopLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
}
