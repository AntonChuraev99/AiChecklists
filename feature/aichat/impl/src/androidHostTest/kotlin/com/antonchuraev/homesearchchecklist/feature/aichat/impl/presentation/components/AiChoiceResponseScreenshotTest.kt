package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatChoice
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceOption
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.PendingChoice
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM/Robolectric screenshot (golden) tests for the AI-chat [AiChoiceResponse] choice block,
 * focused on the adaptive "which list?" chip layout at 2…6 options plus a long-label fallback.
 *
 * Record goldens:
 *   ./gradlew :feature:aichat:impl:recordRoborazziAndroidHostTest
 *
 * Verify (CI):
 *   ./gradlew :feature:aichat:impl:verifyRoborazziAndroidHostTest
 *
 * Golden PNGs land in:
 *   feature/aichat/impl/src/androidHostTest/roborazzi/
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiChoiceResponseScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // -------------------------------------------------------------------------
    // Which-list choice — short labels, adaptive FlowRow, 2…6 options.
    // Each captures light + dark so wrapping is reviewable in both themes.
    // -------------------------------------------------------------------------

    @Test
    fun whichList_2options_light() {
        composeTestRule.setContent { WhichListPreview(darkTheme = false, names = TWO) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_2options_dark() {
        composeTestRule.setContent { WhichListPreview(darkTheme = true, names = TWO) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_3options_light() {
        composeTestRule.setContent { WhichListPreview(darkTheme = false, names = THREE) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_3options_dark() {
        composeTestRule.setContent { WhichListPreview(darkTheme = true, names = THREE) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_4options_light() {
        composeTestRule.setContent { WhichListPreview(darkTheme = false, names = FOUR) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_4options_dark() {
        composeTestRule.setContent { WhichListPreview(darkTheme = true, names = FOUR) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_5options_light() {
        composeTestRule.setContent { WhichListPreview(darkTheme = false, names = FIVE) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_5options_dark() {
        composeTestRule.setContent { WhichListPreview(darkTheme = true, names = FIVE) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_6options_light() {
        composeTestRule.setContent { WhichListPreview(darkTheme = false, names = SIX) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_6options_dark() {
        composeTestRule.setContent { WhichListPreview(darkTheme = true, names = SIX) }
        composeTestRule.onRoot().captureRoboImage()
    }

    // -------------------------------------------------------------------------
    // Long-label fallback — verifies the Column path kicks in (any label long).
    // -------------------------------------------------------------------------

    @Test
    fun whichList_longLabels_light() {
        composeTestRule.setContent { WhichListPreview(darkTheme = false, names = LONG) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_longLabels_dark() {
        composeTestRule.setContent { WhichListPreview(darkTheme = true, names = LONG) }
        composeTestRule.onRoot().captureRoboImage()
    }

    private companion object {
        val TWO = listOf("Покупки", "Работа")
        val THREE = TWO + "Дом"
        val FOUR = THREE + "Поездка"
        val FIVE = FOUR + "Спорт"
        val SIX = FIVE + "Книги"
        val LONG = listOf(
            "Weekly grocery shopping list",
            "Home renovation project tasks",
            "Q3 marketing campaign checklist",
        )
    }
}

// =============================================================================
// Stateless preview content — explicit darkTheme so Robolectric controls the
// theme deterministically (not isSystemInDarkTheme()). Width is constrained to a
// phone dock so FlowRow wrapping is realistic.
// =============================================================================

@Composable
private fun WhichListPreview(darkTheme: Boolean, names: List<String>) {
    AppTheme(darkTheme = darkTheme) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            AiChoiceResponse(
                pending = whichListPending(names),
                onSelect = {},
                onEditChange = {},
                onEditConfirm = {},
            )
        }
    }
}

/** Builds a "which list?" [PendingChoice]: one Default Execute chip per name + a Dismiss escape. */
private fun whichListPending(names: List<String>): PendingChoice {
    val options = names.mapIndexed { index, name ->
        ChoiceOption(
            id = "candidate_$index",
            label = name,
            role = ChoiceRole.Default,
            action = ChoiceAction.Execute(ToolCall.AddItem(checklistHint = name, itemText = "молоко")),
        )
    }
    val escape = ChoiceOption(
        id = "escape",
        label = "Отмена",
        role = ChoiceRole.Escape,
        action = ChoiceAction.Dismiss,
    )
    return PendingChoice(
        choice = ChatChoice(
            prompt = "В какой список?",
            options = options,
            escape = escape,
        ),
    )
}
