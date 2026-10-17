package com.antonchuraev.homesearchchecklist.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentOpener
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayOptions
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxLayout
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxPage
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreenState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxTask
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.CreditsBadge
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium.CreditsBadgeProvider
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium.PremiumEntryPoint
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.CreditsChipSource
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
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
 * Redesign B step (b) — the REAL v2 Inbox inside the REAL v2 shell (plate with the Ask pill + mic),
 * across the DESIGN_SPEC_B §8(b) matrix: 360×660 (compare with the owner-approved B mock), 320×640,
 * 412×915, 840 rail, 1280 drawer, light/dark, fontScale 1.5, RU + HI, capture dock open, 13 pages,
 * empty Inbox, overdue present, compact layout, plan nudge, skeleton.
 *
 * REPORT shots (forced Record into `build/redesign-b/inbox/`), not goldens: they exist to be LOOKED
 * at, and the composeApp module keeps no Inbox goldens in git.
 *
 * Run:
 *   ./gradlew :composeApp:testAndroidHostTest --tests "*V2InboxRedesignScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2InboxRedesignScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()
    private val defaultTimeZone: java.util.TimeZone = java.util.TimeZone.getDefault()

    @Before
    fun setUp() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        stopKoin()
        startKoin {
            modules(
                module {
                    single<AnalyticsTracker> { NoopAnalytics }
                    single<AppLogger> { NoopLogger }
                    single { AttachmentOpener() }
                    single<PremiumEntryPoint> { PremiumEntryPoint { _, _ -> } }
                    single<CreditsBadgeProvider> { FixedBadge }
                },
            )
        }
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        java.util.TimeZone.setDefault(defaultTimeZone)
        stopKoin()
    }

    // ── Mock comparison + size sweep ─────────────────────────────────────────

    @Test fun inbox_360x660_light() = shoot("360x660_light", "w360dp-h660dp-xxhdpi")
    @Test fun inbox_360x660_dark() = shoot("360x660_dark", "w360dp-h660dp-xxhdpi", dark = true)
    @Test fun inbox_320x640_light() = shoot("320x640_light", "w320dp-h640dp-xxhdpi")
    @Test fun inbox_412x915_light() = shoot("412x915_light", "w412dp-h915dp-xxhdpi")
    @Test fun inbox_412x915_dark() = shoot("412x915_dark", "w412dp-h915dp-xxhdpi", dark = true)
    /** Rail = Medium (600–839dp); 840 itself already resolves Expanded, see the drawer cells. */
    @Test fun inbox_700_rail_light() = shoot("700_rail_light", "w700dp-h900dp-mdpi")
    @Test fun inbox_840_drawer_light() = shoot("840_drawer_light", "w840dp-h900dp-mdpi")
    @Test fun inbox_1280_drawer_light() = shoot("1280_drawer_light", "w1280dp-h900dp-mdpi")
    @Test fun inbox_1280_drawer_dark() = shoot("1280_drawer_dark", "w1280dp-h900dp-mdpi", dark = true)

    // ── Type × locale ────────────────────────────────────────────────────────

    @Test fun inbox_360_fs15_light() = shoot("360_fs15_light", "w360dp-h660dp-xxhdpi", fontScale = 1.5f)

    @Test
    fun inbox_360_ru_fs15() =
        shoot("360_ru_fs15", "ru-rRU-w360dp-h660dp-xxhdpi", fontScale = 1.5f, locale = RU)

    @Test fun inbox_360_hi() = shoot("360_hi", "hi-rIN-w360dp-h660dp-xxhdpi", locale = HI)

    @Test
    fun inbox_320_hi_fs15() =
        shoot("320_hi_fs15", "hi-rIN-w320dp-h640dp-xxhdpi", fontScale = 1.5f, locale = HI)

    // ── States ───────────────────────────────────────────────────────────────

    @Test
    fun inbox_360_captureOpen() = shoot("360_captureOpen", "w360dp-h660dp-xxhdpi", captureOpen = true)

    @Test
    fun inbox_360_13pages_lastSelected() = shoot(
        "360_13pages_last",
        "w360dp-h660dp-xxhdpi",
        state = mockState(extraProjects = 10).copy(selectedPage = 12),
        anchor = 13L,
    )

    @Test
    fun inbox_360_empty() = shoot(
        "360_empty",
        "w360dp-h660dp-xxhdpi",
        state = contentState(listOf(page(1L, "Inbox", isInbox = true, tasks = emptyList()))),
    )

    @Test
    fun inbox_360_overdue() = shoot(
        "360_overdue",
        "w360dp-h660dp-xxhdpi",
        state = contentState(
            listOf(
                page(
                    1L,
                    "Inbox",
                    isInbox = true,
                    tasks = listOf(
                        task("Pay the electricity bill", NOW - 26 * HOUR, important = true),
                        task("Return library books", NOW - 3 * HOUR),
                        task("Call the plumber", NOW + 7 * HOUR),
                        task("Buy a birthday gift"),
                    ),
                ),
                page(2L, "Groceries", isInbox = false, tasks = emptyList()),
            ),
        ),
    )

    @Test
    fun inbox_360_compactLayout() = shoot(
        "360_compact",
        "w360dp-h660dp-xxhdpi",
        state = mockState(layout = InboxLayout.COMPACT),
    )

    @Test
    fun inbox_360_dark_compactLayout() = shoot(
        "360_dark_compact",
        "w360dp-h660dp-xxhdpi",
        dark = true,
        state = mockState(layout = InboxLayout.COMPACT),
    )

    @Test
    fun inbox_360_planNudge() = shoot(
        "360_nudge",
        "w360dp-h740dp-xxhdpi",
        state = contentState(
            listOf(
                page(
                    1L,
                    "Inbox",
                    isInbox = true,
                    tasks = listOf(
                        task("Call the plumber", NOW + 7 * HOUR),
                        task("Buy a birthday gift"),
                        task("Send the weekly report"),
                        task("Fix the bike light"),
                    ),
                ),
                page(2L, "Groceries", isInbox = false, tasks = emptyList()),
            ),
        ),
        planDay = true,
    )

    @Test
    fun inbox_360_skeleton() =
        shoot("360_skeleton", "w360dp-h660dp-xxhdpi", state = InboxScreenState.Loading)

    // ── Harness ──────────────────────────────────────────────────────────────

    private fun shoot(
        name: String,
        qualifiers: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
        captureOpen: Boolean = false,
        state: InboxScreenState = mockState(),
        anchor: Long? = null,
        planDay: Boolean = false,
    ) {
        // BOTH: qualifiers move Android's configuration, Compose Resources read the JVM locale.
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        val compact = listOf("w700dp", "w840dp", "w1280dp").none { qualifiers.contains(it) }
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = dark) {
                    V2NavigationShell(
                        selectedTab = V2Destination.Inbox,
                        onNavigate = {},
                        onOpenChat = {},
                        onOpenSettings = {},
                        onOpenUpdates = {},
                        showCreateFab = true,
                        onOpenCreate = {},
                        captureOpen = captureOpen,
                        onOpenChatVoice = {},
                    ) {
                        InboxUnderTest(
                            state = state,
                            bottomPadding = if (compact) V2ShellMetrics.FabBandPadding else 0.dp,
                            captureOpen = captureOpen,
                            anchor = anchor,
                            planDay = planDay,
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "$OutDir/$name.png",
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    @Composable
    private fun InboxUnderTest(
        state: InboxScreenState,
        bottomPadding: androidx.compose.ui.unit.Dp,
        captureOpen: Boolean,
        anchor: Long?,
        planDay: Boolean,
    ) {
        InboxScreen(
            state = state,
            contentBottomPadding = bottomPadding,
            onIntent = {},
            snackbarHostState = SnackbarHostState(),
            swallowRootBack = false,
            createDockOpen = captureOpen,
            onCreateDockDismiss = {},
            anchorChecklistId = anchor,
            onPlanDayClick = if (planDay) ({}) else null,
            creditsSource = CreditsChipSource.V2_INBOX,
        )
    }

    /** The mock's Inbox: Today 1, Upcoming 2 (one Important), Anytime 2; Groceries + Weekend trip. */
    private fun mockState(
        extraProjects: Int = 0,
        layout: InboxLayout = InboxLayout.CARDS,
    ): InboxScreenState.Content {
        val projects = listOf(
            page(2L, "Groceries", isInbox = false, tasks = listOf(task("Olive oil"))),
            page(3L, "Weekend trip", isInbox = false, tasks = emptyList()),
        ) + (0 until extraProjects).map { i ->
            page(4L + i, ExtraProjectNames[i % ExtraProjectNames.size], isInbox = false, tasks = emptyList())
        }
        return contentState(
            listOf(
                page(
                    1L,
                    "Inbox",
                    isInbox = true,
                    tasks = listOf(
                        task("Call the plumber", NOW + 7 * HOUR),
                        task("Book train tickets", NOW + 24 * HOUR),
                        task("Renew passport", NOW + 48 * HOUR, important = true),
                        task("Buy a birthday gift"),
                        task("Send the weekly report"),
                    ),
                ),
            ) + projects,
            layout = layout,
        )
    }

    private fun contentState(
        pages: List<InboxPage>,
        layout: InboxLayout = InboxLayout.CARDS,
    ) = InboxScreenState.Content(
        pages = pages,
        displayOptions = InboxDisplayOptions(layout = layout),
        nowMillis = NOW,
    )

    private fun page(id: Long, title: String, isInbox: Boolean, tasks: List<InboxTask>) =
        InboxPage(checklistId = id, title = title, isInbox = isInbox, tasks = tasks)

    private fun task(text: String, dueAt: Long? = null, important: Boolean = false) = InboxTask(
        item = ChecklistFillItem(
            text = text,
            checked = false,
            priority = if (important) 1 else 0,
            templateItemId = "t-$text",
        ).withReminderAt(dueAt),
    )

    private object FixedBadge : CreditsBadgeProvider {
        private val badge = CreditsBadge(credits = 80, isPremium = false)
        override fun badge(): Flow<CreditsBadge> = flowOf(badge)
        override fun currentBadge(): CreditsBadge = badge
    }

    private object NoopLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    private object NoopAnalytics : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    private companion object {
        const val OutDir = "build/redesign-b/inbox"
        const val HOUR = 3_600_000L

        /** Friday 2026-10-16 10:00 UTC — the mock's "Friday, October 16". */
        val NOW: Long = LocalDateTime(2026, 10, 16, 10, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()

        val RU: Locale = Locale.forLanguageTag("ru-RU")
        val HI: Locale = Locale.forLanguageTag("hi-IN")

        val ExtraProjectNames = listOf(
            "Home", "Work", "Garden", "Car", "Kids", "Health", "Finances", "Reading", "Gifts", "Moving day",
        )
    }
}
