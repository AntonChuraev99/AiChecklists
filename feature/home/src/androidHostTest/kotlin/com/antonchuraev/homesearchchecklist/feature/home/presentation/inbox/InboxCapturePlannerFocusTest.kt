package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.due_planner_done
import aichecklists.core.designsystem.generated.resources.due_rail_no_date
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.DraftDueIntent
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.DraftDueUiState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.TaskDraft
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

/**
 * Owner decision O3 (redesign B pass 2): opening the due planner takes focus off the capture field —
 * so the keyboard goes down and the planner fits a 360×800 window — and Done hands it back.
 *
 * Asserted on FOCUS, not on the keyboard: Robolectric raises no IME, so
 * `SoftwareKeyboardController.hide()/show()` has nothing observable to act on. Focus is the half the
 * dock controls and the one the keyboard follows on a device; the IME itself is a device check.
 *
 * Through the real [InboxScreen]: the host owns the planner flag and passes it to the dock, and the
 * wiring between the two is exactly what a component-level test would not see.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*InboxCapturePlannerFocusTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxCapturePlannerFocusTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startKoinRoot() {
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
    fun stopKoinAfterTest() = stopKoin()

    @Test
    fun plannerOpen_takesFocusOffTheField_andDoneGivesItBack_keepingTheDraft() {
        var whenLabel = ""
        var doneLabel = ""
        // Hoisted OUT of setContent — see InboxAddTaskRowTest A3 for why a flag declared inside the
        // composition is thrown away on the first write.
        var plannerExpanded by mutableStateOf(false)

        composeTestRule.setContent {
            whenLabel = stringResource(Res.string.due_rail_no_date)
            doneLabel = stringResource(Res.string.due_planner_done)
            AppTheme(darkTheme = false) {
                InboxScreen(
                    state = InboxScreenState.Content(
                        pages = listOf(
                            InboxPage(checklistId = 1L, title = "Inbox", isInbox = true, tasks = emptyList()),
                        ),
                        draft = TaskDraft(text = DraftText),
                        due = DraftDueUiState(plannerExpanded = plannerExpanded),
                    ),
                    contentBottomPadding = 0.dp,
                    // Plays the ViewModel for the two intents under test, nothing else.
                    onIntent = { intent ->
                        if (intent is InboxIntent.OnDue) {
                            when (intent.due) {
                                DraftDueIntent.OnLeadClick -> plannerExpanded = !plannerExpanded
                                DraftDueIntent.OnPlannerCollapse -> plannerExpanded = false
                                else -> Unit
                            }
                        }
                    },
                    snackbarHostState = SnackbarHostState(),
                    swallowRootBack = false,
                    createDockOpen = true,
                    onCreateDockDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        val field = composeTestRule.onNode(hasSetTextAction())
        // Precondition: the dock raises the keyboard on mount, i.e. the field starts focused.
        field.assertIsFocused()

        composeTestRule.onNodeWithText(whenLabel).performClick()
        composeTestRule.waitForIdle()
        check(plannerExpanded) { "the lead chip must have opened the planner" }
        field.assertIsNotFocused()
        // Giving up focus must not cost the user what they typed.
        field.assertTextContains(DraftText)

        composeTestRule.onNodeWithText(doneLabel).performClick()
        composeTestRule.waitForIdle()
        check(!plannerExpanded) { "Done must have closed the planner" }
        field.assertIsFocused()
        field.assertTextContains(DraftText)
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
        const val DraftText = "Pay the invoice"
    }
}
