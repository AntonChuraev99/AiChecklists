package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.DraftDueIntent
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.DraftDueUiState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.TaskDraft
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxIntent
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxPage
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreenState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxTask
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Closing the capture dock collapses the planner — **including across a configuration change**.
 *
 * ## What it does and does NOT pin
 * The Inbox host tracks "the dock was open" so it can send `OnPlannerCollapse` on the CLOSING
 * transition only (an effect keyed on the flag alone fires on every mount and would report a state
 * change that did not happen — `InboxAddTaskRowTest` catches that half).
 *
 * ⚠️ It does **not** discriminate `remember` from `rememberSaveable` for that flag, and this was
 * checked rather than assumed: the test passes on both. A restore recreates the composition, which
 * relaunches `LaunchedEffect(captureDockRenders)` with the dock still open, so the flag is re-set to
 * true before anything can close it — it self-heals, and the "rotate and lose the collapse" scenario
 * is not reachable through this screen. The two hosts were aligned on `rememberSaveable` anyway, for
 * consistency; that alignment is deliberately NOT what this test claims to prove.
 *
 * What it does pin is the transition itself surviving a restore: key the effect on `Unit` instead of
 * on the dock's gate, or drop the intent, and this goes red.
 *
 * ## Why the assertion is on the INTENT
 * The screen is stateless — `plannerExpanded` lives in the host's `DraftDueController`. What this
 * screen owes the host on close is exactly one intent, so that is the observable.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*CaptureDockPlannerCollapseOnCloseTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h891dp-normal-long-notround-any-160dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureDockPlannerCollapseOnCloseTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** [InboxScreen] `koinInject()`s both of these directly; without a root the composition dies. */
    @Before
    fun startKoinRoot() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single<AnalyticsTracker> { NoopAnalytics }
                    single<AppLogger> { NoopLogger }
                }
            )
        }
    }

    @After
    fun stopKoinAfterTest() = stopKoin()

    @Test
    fun inbox_collapsesThePlanner_whenTheDockClosesAfterAStateRestore() {
        val intents = mutableListOf<InboxIntent>()
        var dockOpen by mutableStateOf(true)
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            AppTheme(darkTheme = false) {
                InboxScreen(
                    state = InboxScreenState.Content(
                        pages = listOf(
                            InboxPage(
                                checklistId = 1L,
                                title = "Inbox",
                                isInbox = true,
                                tasks = listOf(task("Renew the parking permit")),
                            ),
                        ),
                        draft = TaskDraft(text = "Pay the invoice"),
                        due = DraftDueUiState(plannerExpanded = true),
                        nowMillis = FixedNow,
                    ),
                    contentBottomPadding = 0.dp,
                    onIntent = { intents += it },
                    snackbarHostState = SnackbarHostState(),
                    swallowRootBack = false,
                    createDockOpen = dockOpen,
                    onCreateDockDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // Precondition: the dock is up and nothing has been collapsed yet — otherwise the assertion
        // below would pass on a screen that fires the intent on every mount, which is the OTHER
        // defect this effect is guarded against.
        assertTrue(
            "no collapse may be reported while the dock is still open — saw $intents",
            intents.none { it.isPlannerCollapse() },
        )

        // The rotation. Everything a plain `remember` held is gone here; `rememberSaveable` survives.
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        dockOpen = false
        composeTestRule.waitForIdle()

        assertEquals(
            "closing the dock after a restore must still collapse the planner — with a plain " +
                "`remember` the 'was open' flag is lost in the restore, the closing branch never " +
                "fires, and the next open arrives expanded over a fresh draft. Saw $intents",
            1,
            intents.count { it.isPlannerCollapse() },
        )
    }

    private fun InboxIntent.isPlannerCollapse(): Boolean =
        this is InboxIntent.OnDue && due == DraftDueIntent.OnPlannerCollapse

    private fun task(text: String) = InboxTask(
        item = ChecklistFillItem(
            text = text,
            checked = false,
            priority = 0,
            templateItemId = "template-$text",
        )
    )

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
        /** 2025-08-19 10:00 UTC — pinned so the rail's labels do not move with the wall clock. */
        const val FixedNow = 1_755_597_600_000L
    }
}
