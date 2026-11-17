package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayOptions
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.NotificationsBannerUi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
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
import java.util.Locale

/**
 * Report frames for the owner's layout-debug wave A on the Inbox (2026-11-16): the notifications-off
 * banner scrolls WITH the task list (it used to be pinned above the pager), and the header title is
 * one line.
 *
 * Forced Record into `build/ux-report/layout-debug` — never a golden, see [InboxVisualReportTest].
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*InboxLayoutDebugReportTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxLayoutDebugReportTest {

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
                },
            )
        }
    }

    @After
    fun restoreEnvironment() {
        Locale.setDefault(defaultLocale)
        stopKoin()
    }

    @Test
    fun banner_atRest_light() = capture("banner_rest_light", bannerState())

    @Test
    fun banner_atRest_dark() = capture("banner_rest_dark", bannerState(), dark = true)

    /** After a swipe up the banner must leave with the rows, not stay pinned over them. */
    @Test
    fun banner_scrolled_light() = capture("banner_scrolled_light", bannerState(), scroll = true)

    @Test
    fun longTitle_light() = capture("title_long_light", longTitleState())

    @Test
    fun longTitle_ru_fontScale150() = capture(
        "title_long_ru_fs150",
        longTitleState(title = "Поездка на выходные в горы со всей семьёй"),
        locale = Locale.forLanguageTag("ru-RU"),
        fontScale = 1.5f,
    )

    private fun capture(
        name: String,
        state: InboxScreenState,
        dark: Boolean = false,
        scroll: Boolean = false,
        locale: Locale = Locale.forLanguageTag("en-US"),
        fontScale: Float = 1f,
    ) {
        Locale.setDefault(locale)
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = dark) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        InboxScreen(
                            state = state,
                            contentBottomPadding = 0.dp,
                            onIntent = {},
                            snackbarHostState = SnackbarHostState(),
                            swallowRootBack = false,
                            createDockOpen = false,
                            onCreateDockDismiss = {},
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        if (scroll) {
            composeTestRule.onRoot().performTouchInput {
                swipeUp(startY = height * 0.8f, endY = height * 0.5f, durationMillis = 400)
            }
            composeTestRule.waitForIdle()
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/ux-report/layout-debug/$name.png",
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    private fun bannerState() = InboxScreenState.Content(
        pages = listOf(
            InboxPage(
                checklistId = 1L,
                title = "Inbox",
                isInbox = true,
                tasks = (1..14).map { InboxTask(item = ChecklistFillItem(text = "Task $it", checked = false)) },
            ),
        ),
        displayOptions = InboxDisplayOptions(),
        nowMillis = NOW,
        notificationsBanner = NotificationsBannerUi(reminderVariant = false, settingsRequired = false),
    )

    private fun longTitleState(title: String = "Weekend trip to the mountains with the whole family") =
        InboxScreenState.Content(
            pages = listOf(
                InboxPage(
                    checklistId = 2L,
                    title = title,
                    isInbox = false,
                    tasks = (1..4).map { InboxTask(item = ChecklistFillItem(text = "Task $it", checked = false)) },
                ),
            ),
            displayOptions = InboxDisplayOptions(),
            nowMillis = NOW,
        )

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
        /** 2026-08-13 12:00 UTC — a fixed clock; no row in these fixtures is dated. */
        const val NOW = 1_786_622_400_000L
    }
}
