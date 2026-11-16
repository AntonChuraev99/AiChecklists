package com.antonchuraev.homesearchchecklist.navigation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_add_task_row
import aichecklists.core.designsystem.generated.resources.nav_ask_pill_label
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourceTileRow
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxPage
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreenState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxTask
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Report frames for the empty-Inbox start card (concept C, owner 2026-11-16): the REAL [InboxScreen]
 * inside the REAL v2 shell, so "Add task stays above the bottom chrome" is judged against the actual
 * bar and not a stand-in.
 *
 * Forced Record into `build/ux-report/inbox-empty-start` — never compared by `verify*`, never enrolled
 * as goldens (same contract as `EmptyStateAddTaskReportTest` in feature/home).
 *
 * Run:
 *   ./gradlew :composeApp:testAndroidHostTest --tests "*InboxEmptyStartReportTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxEmptyStartReportTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    @Before
    fun startKoinWithNoopCollaborators() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single<AnalyticsTracker> { NoopAnalyticsTracker }
                    single<AppLogger> { NoopAppLogger }
                }
            )
        }
    }

    @After
    fun restore() {
        Locale.setDefault(defaultLocale)
        stopKoin()
    }

    // ── Phones, empty ────────────────────────────────────────────────────────
    @Test fun empty_411x914_light() = shoot("empty_411x914_light", w = 411, h = 914)
    @Test fun empty_411x914_dark() = shoot("empty_411x914_dark", w = 411, h = 914, dark = true)
    @Test fun empty_360x800_light() = shoot("empty_360x800_light", w = 360, h = 800)
    @Test fun empty_360x800_dark() = shoot("empty_360x800_dark", w = 360, h = 800, dark = true)
    @Test fun empty_320x568_fs10() = shoot("empty_320x568_fs10", w = 320, h = 568)
    @Test fun empty_320x568_fs13() = shoot("empty_320x568_fs13", w = 320, h = 568, fontScale = 1.3f)
    @Test fun empty_320x568_fs20() = shoot("empty_320x568_fs20", w = 320, h = 568, fontScale = 2f)
    @Test fun empty_320x568_fs13_ru() =
        shoot("empty_320x568_fs13_ru", w = 320, h = 568, fontScale = 1.3f, locale = RU, addTaskAboveChrome = true)
    @Test fun empty_320x568_fs13_ru_dark() =
        shoot("empty_320x568_fs13_ru_dark", w = 320, h = 568, fontScale = 1.3f, locale = RU, dark = true)
    @Test fun empty_320x568_fs10_aboveChrome() =
        shoot("empty_320x568_fs10_check", w = 320, h = 568, addTaskAboveChrome = true)
    @Test fun empty_360x800_aboveChrome() =
        shoot("empty_360x800_check", w = 360, h = 800, addTaskAboveChrome = true)
    @Test fun empty_320x568_fs20_ru() =
        shoot("empty_320x568_fs20_ru", w = 320, h = 568, fontScale = 2f, locale = RU)
    @Test fun empty_320x568_fs13_hi() =
        shoot("empty_320x568_fs13_hi", w = 320, h = 568, fontScale = 1.3f, locale = HI)
    @Test fun empty_320x568_fs20_hi_dark() =
        shoot("empty_320x568_fs20_hi_dark", w = 320, h = 568, fontScale = 2f, locale = HI, dark = true)
    @Test fun empty_411x914_fs13_ru() =
        shoot("empty_411x914_fs13_ru", w = 411, h = 914, fontScale = 1.3f, locale = RU)
    @Test fun empty_411x914_fs20_hi() =
        shoot("empty_411x914_fs20_hi", w = 411, h = 914, fontScale = 2f, locale = HI)

    // ── Rail / drawer: no Add task in the card ───────────────────────────────
    @Test fun empty_600x960_rail_light() =
        shoot("empty_600x960_rail_light", w = 600, h = 960, expectedAddTask = 0)
    @Test fun empty_840x900_drawer_dark() =
        shoot("empty_840x900_drawer_dark", w = 840, h = 900, dark = true, expectedAddTask = 0)

    // ── Dock open ────────────────────────────────────────────────────────────
    @Test fun empty_411x914_dockOpen() =
        shoot("empty_411x914_dockOpen", w = 411, h = 914, dockOpen = true, expectedAddTask = 0)

    // ── Sparse (1–2 tasks): tiles under the trailing row ─────────────────────
    @Test fun sparse1_411x914_light() =
        shoot("sparse1_411x914_light", w = 411, h = 914, tasks = listOf("Buy bread"))
    @Test fun sparse2_411x914_dark() =
        shoot("sparse2_411x914_dark", w = 411, h = 914, dark = true, tasks = listOf("Buy bread", "Call the vet"))
    @Test fun sparse1_320x568_fs13_ru() =
        shoot("sparse1_320x568_fs13_ru", w = 320, h = 568, fontScale = 1.3f, locale = RU, tasks = listOf("Купить хлеб"))

    // ── Each rung of the tile ladder, isolated ───────────────────────────────
    @Test fun tiles_rungs_en() = shootRungs("tiles_rungs_en", Locale.ENGLISH, 1f)
    @Test fun tiles_rungs_ru_fs20() = shootRungs("tiles_rungs_ru_fs20", RU, 2f)

    // ── Harness ──────────────────────────────────────────────────────────────

    private fun shoot(
        name: String,
        w: Int,
        h: Int,
        dark: Boolean = false,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
        tasks: List<String> = emptyList(),
        dockOpen: Boolean = false,
        expectedAddTask: Int = 1,
        addTaskAboveChrome: Boolean = false,
    ) {
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers("w${w}dp-h${h}dp-normal-long-notround-any-420dpi-keyshidden-nonav")
        var addTaskLabel = ""
        var pillLabel = ""
        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            pillLabel = stringResource(Res.string.nav_ask_pill_label).take(PILL_PREFIX)
            Themed(dark, fontScale) {
                V2NavigationShell(
                    selectedTab = V2Destination.Inbox,
                    onNavigate = {},
                    onOpenChat = {},
                    onOpenSettings = {},
                    onOpenUpdates = {},
                    showCreateFab = true,
                    onOpenCreate = {},
                    barVisible = true,
                    captureOpen = dockOpen,
                    overlayContent = null,
                    onOpenChatVoice = {},
                    onOpenChatAttach = {},
                    onStartVoiceCall = null,
                    content = {
                        InboxScreen(
                            state = InboxScreenState.Content(
                                pages = listOf(
                                    InboxPage(
                                        checklistId = 1L,
                                        title = "Inbox",
                                        isInbox = true,
                                        tasks = tasks.map {
                                            InboxTask(item = ChecklistFillItem(text = it, checked = false))
                                        },
                                    ),
                                ),
                                nowMillis = NOW,
                            ),
                            contentBottomPadding = 0.dp,
                            onIntent = {},
                            snackbarHostState = SnackbarHostState(),
                            swallowRootBack = false,
                            createDockOpen = dockOpen,
                            onCreateDockDismiss = {},
                        )
                    },
                )
            }
        }
        composeTestRule.waitForIdle()
        capture(name)
        assertEquals(
            "$name: \"Add task\" controls on the page",
            expectedAddTask,
            composeTestRule.onAllNodesWithText(addTaskLabel).fetchSemanticsNodes().size,
        )
        if (addTaskAboveChrome) {
            // The chrome plate starts SpacingMd+ above the Ask pill's own top edge (see the 320 shell
            // golden), so the pill top minus that is the visible top of the bottom chrome.
            val addBottom = composeTestRule.onAllNodesWithText(addTaskLabel)
                .fetchSemanticsNodes().single().boundsInRoot.bottom
            val pillTop = composeTestRule.onAllNodesWithText(pillLabel, substring = true)
                .fetchSemanticsNodes().minOf { it.boundsInRoot.top }
            val plateTop = pillTop - with(composeTestRule.density) { AppDimens.SpacingMd.toPx() }
            assertTrue(
                "$name: Add task (bottom=$addBottom) must end above the bottom chrome (top≈$plateTop)",
                addBottom <= plateTop,
            )
        }
    }

    /** [SourceTileRow] alone at three widths — rung 4 (vertical), rung 2 and rung 1 (horizontal). */
    private fun shootRungs(name: String, locale: Locale, fontScale: Float) {
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers("w420dp-h900dp-normal-long-notround-any-420dpi-keyshidden-nonav")
        composeTestRule.setContent {
            Themed(dark = false, fontScale = fontScale) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(AppDimens.SpacingLg),
                ) {
                    listOf(380, 240, 140).forEach { width ->
                        Text("${width}dp", style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.width(width.dp).background(AppSurface.card())) {
                            SourceTileRow(onSelect = {})
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        capture(name)
    }

    @Composable
    private fun Themed(dark: Boolean, fontScale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
            AppTheme(darkTheme = dark) { content() }
        }
    }

    private fun capture(name: String) {
        composeTestRule.onRoot().captureRoboImage(
            filePath = "$REPORT_DIR/$name.png",
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    private object NoopAppLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    private object NoopAnalyticsTracker : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    private companion object {
        const val REPORT_DIR = "build/ux-report/inbox-empty-start"
        const val NOW = 1_750_000_000_000L

        /** The pill label is ellipsized at 320dp; its first characters are always on screen. */
        const val PILL_PREFIX = 8
        val RU: Locale = Locale.forLanguageTag("ru")
        val HI: Locale = Locale.forLanguageTag("hi")
    }
}
