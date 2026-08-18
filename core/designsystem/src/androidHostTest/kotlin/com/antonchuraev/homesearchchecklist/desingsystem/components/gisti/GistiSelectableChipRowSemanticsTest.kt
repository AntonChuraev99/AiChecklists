package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The item-create chips must ANNOUNCE which one is active, not only paint it.
 *
 * `Surface(onClick = …)` reports `Role.Button` and nothing else, so before
 * `SelectablePromptChipItem` set `semantics { this.selected = … }` a screen-reader user heard the
 * four reminder presets as four identical buttons with no way to tell which one was on — the blue
 * fill was a sighted-only cue. The state is now carried on two channels and neither is optional:
 * the screenshot suite covers the fill, this covers the announcement.
 *
 * It is a real composition rather than a factory assertion because the property is set in the ITEM,
 * not in the chip data: `GistiSelectableChip.selected` could keep flowing through the row while the
 * `semantics` block was deleted, and every data-level test would stay green.
 *
 * Run:
 *   ./gradlew :core:designsystem:testAndroidHostTest --tests "*GistiSelectableChipRowSemanticsTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GistiSelectableChipRowSemanticsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectableChipRow_announcesTheActiveReminderAndTheActiveToggle() {
        showChips(selectedReminder = GistiItemCreateAction.REMIND_TONIGHT, importantSelected = true)

        composeTestRule.onNodeWithText(TONIGHT).assertIsSelected()
        composeTestRule.onNodeWithText(IMPORTANT).assertIsSelected()
    }

    /**
     * The negative half, and the half that actually fails when the property is dropped: a missing
     * `selected` reads as "not selected" to the semantics tree, so a suite that only ever asserted
     * [assertIsSelected] on the ONE active chip would still catch it — but a suite that only asserted
     * the positive on a row where everything happened to be selected would not. Both directions are
     * pinned in one frame.
     */
    @Test
    fun selectableChipRow_announcesTheRestAsNotSelected() {
        showChips(selectedReminder = GistiItemCreateAction.REMIND_TONIGHT, importantSelected = true)

        composeTestRule.onNodeWithText(IN_1_HOUR).assertIsNotSelected()
        composeTestRule.onNodeWithText(TOMORROW_MORNING).assertIsNotSelected()
        composeTestRule.onNodeWithText(PICK_TIME).assertIsNotSelected()
        composeTestRule.onNodeWithText(REPEAT).assertIsNotSelected()
    }

    /**
     * A WIDE window on purpose. The row is a `LazyRow`, so on a phone-width viewport the last chips
     * are never composed and `onNodeWithText` fails on "no node" rather than on the state under test
     * — a red that says nothing about semantics.
     */
    private fun showChips(
        selectedReminder: GistiItemCreateAction?,
        importantSelected: Boolean = false,
        repeatSelected: Boolean = false,
    ) {
        RuntimeEnvironment.setQualifiers("w1600dp-h800dp")
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                GistiSelectableChipRow(
                    chips = gistiItemCreatePromptChips(
                        in1HourLabel = IN_1_HOUR,
                        tomorrowMorningLabel = TOMORROW_MORNING,
                        tonightLabel = TONIGHT,
                        pickTimeLabel = PICK_TIME,
                        importantLabel = IMPORTANT,
                        repeatLabel = REPEAT,
                        selectedReminder = selectedReminder,
                        importantSelected = importantSelected,
                        repeatSelected = repeatSelected,
                    ),
                    onChipClick = {},
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val IN_1_HOUR = "In 1 hour"
        const val TOMORROW_MORNING = "Tomorrow morning"
        const val TONIGHT = "Tonight"
        const val PICK_TIME = "Pick time…"
        const val IMPORTANT = "Important"
        const val REPEAT = "Repeat"
    }
}
