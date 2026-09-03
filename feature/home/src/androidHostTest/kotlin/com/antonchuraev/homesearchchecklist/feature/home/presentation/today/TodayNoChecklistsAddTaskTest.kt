package com.antonchuraev.homesearchchecklist.feature.home.presentation.today

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_add_task_row
import aichecklists.core.designsystem.generated.resources.main_create_checklist
import aichecklists.core.designsystem.generated.resources.today_no_checklists_title
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The Today body's `NoChecklists` placeholder speaks TASKS, and its button raises the capture dock.
 *
 * It was the last placeholder in the v2 shell still offering "Create Checklist" — a second noun on a
 * surface that is otherwise entirely about tasks (owner, 2026-08-19), and the tap behind it navigated
 * TWICE: the ViewModel pushed Templates while the host pushed CreateChecklist on the same click.
 *
 * Two halves, and both are asserted here because either one alone would let the defect back:
 *  - the placeholder offers the add-task action (label + the callback it actually calls);
 *  - "Create Checklist" is gone from it, so the removed second navigation cannot creep back under
 *    its old label.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*TodayNoChecklistsAddTaskTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayNoChecklistsAddTaskTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun noChecklists_offersAddTask_andNoLongerOffersCreateChecklist() {
        var addTaskTaps = 0
        var addTaskLabel = ""
        var createChecklistLabel = ""
        var title = ""

        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            createChecklistLabel = stringResource(Res.string.main_create_checklist)
            title = stringResource(Res.string.today_no_checklists_title)
            AppTheme(darkTheme = false) {
                TodayBody(
                    state = TodayScreenState.NoChecklists,
                    onReminderClick = { _, _ -> },
                    onRetry = {},
                    canCapture = true,
                    onAddTaskClick = { addTaskTaps++ },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(addTaskLabel).assertIsDisplayed().performClick()

        assertEquals(
            1,
            addTaskTaps,
            "The NoChecklists placeholder's button must fire the HOST's add-task callback — the one " +
                "that raises the capture dock and carries the source = \"inline_row\" analytics. Any " +
                "other wiring silently forks that funnel",
        )
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText(createChecklistLabel).fetchSemanticsNodes().size,
            "\"$createChecklistLabel\" must be gone from this placeholder: it named a checklist on a " +
                "surface that is about tasks, and its tap navigated twice (ViewModel → Templates, " +
                "host → CreateChecklist)",
        )
    }

    /**
     * A host with no capture affordance (the standalone Today route, the classic Calendar tab) passes
     * `onAddTaskClick = null`. The placeholder must then draw NO action rather than a button that
     * promises an input the host does not have — the same contract `Empty` and `AllDone` already keep.
     */
    @Test
    fun noChecklists_onAHostWithoutCapture_drawsNoAction() {
        var addTaskLabel = ""
        var createChecklistLabel = ""

        composeTestRule.setContent {
            addTaskLabel = stringResource(Res.string.inbox_add_task_row)
            createChecklistLabel = stringResource(Res.string.main_create_checklist)
            AppTheme(darkTheme = false) {
                TodayBody(
                    state = TodayScreenState.NoChecklists,
                    onReminderClick = { _, _ -> },
                    onRetry = {},
                    canCapture = false,
                    onAddTaskClick = null,
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(
            0,
            composeTestRule.onAllNodesWithText(addTaskLabel).fetchSemanticsNodes().size +
                composeTestRule.onAllNodesWithText(createChecklistLabel).fetchSemanticsNodes().size,
            "A host without a capture affordance must get a placeholder with no button at all",
        )
    }
}
