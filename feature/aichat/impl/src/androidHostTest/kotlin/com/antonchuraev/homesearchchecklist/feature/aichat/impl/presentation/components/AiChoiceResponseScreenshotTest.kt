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

    // -------------------------------------------------------------------------
    // Meta alignment (the D2 "which «Shopping»?" count) — the regression these
    // goldens exist for: in the full-width Column the counts MUST line up on one
    // right-hand column, independent of how long each label is.
    // -------------------------------------------------------------------------

    /**
     * The reported bug, verbatim: one block mixing a 2-line label, a medium one, two very short
     * ones and a numeric one. Before the fix each "• N" sat at `label width + half the chip`, so
     * the counts scattered diagonally down the block instead of forming a column.
     */
    @Test
    fun whichList_meta_mixedLabelLengths_light() {
        composeTestRule.setContent { WhichListMetaPreview(darkTheme = false, candidates = MIXED_META) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_meta_mixedLabelLengths_dark() {
        composeTestRule.setContent { WhichListMetaPreview(darkTheme = true, candidates = MIXED_META) }
        composeTestRule.onRoot().captureRoboImage()
    }

    /**
     * Name+count collision → `buildCandidateMetas` appends the day to EVERY candidate in the block
     * ("• 12 • 3 July"). The meta is at its widest here, which is exactly when a mis-measured label
     * weight would start eating it.
     */
    @Test
    fun whichList_meta_nameCollision_light() {
        composeTestRule.setContent { WhichListMetaPreview(darkTheme = false, candidates = COLLIDING_META) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_meta_nameCollision_dark() {
        composeTestRule.setContent { WhichListMetaPreview(darkTheme = true, candidates = COLLIDING_META) }
        composeTestRule.onRoot().captureRoboImage()
    }

    /**
     * KDoc invariant: when the name cannot fit, the LABEL ellipsises and the meta stays whole —
     * a clipped name with a visible count still answers "which one?", the reverse does not.
     */
    @Test
    fun whichList_meta_labelOverflows_light() {
        composeTestRule.setContent { WhichListMetaPreview(darkTheme = false, candidates = OVERFLOWING_META) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun whichList_meta_labelOverflows_dark() {
        composeTestRule.setContent { WhichListMetaPreview(darkTheme = true, candidates = OVERFLOWING_META) }
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

        /**
         * The device case that surfaced the bug: label lengths spanning 4…24 chars in ONE block.
         * A 2-line label, a medium one, two short RU ones and an all-digit one — the counts must
         * still share a single right edge.
         */
        val MIXED_META = listOf(
            "5-Day Paris Packing List" to "18",
            "Weekly groceries" to "9",
            "апки" to "16",
            "купить" to "8",
            "123123213" to "12",
        )

        /**
         * Two identical name+count pairs → the day is appended to every meta in the block (see
         * ChatViewModel.buildCandidateMetas): the format stays uniform, only the width grows.
         */
        val COLLIDING_META = listOf(
            "Покупки" to "12 • 3 July",
            "Покупки" to "12 • 15 July",
            "Работа" to "5 • 1 July",
        )

        /** A name that cannot fit in 2 lines beside its meta — the label must yield, not the count. */
        val OVERFLOWING_META = listOf(
            "Renovation of the upstairs bathroom and the guest bedroom closet" to "42",
            "Дом" to "7",
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

/**
 * Same block, but every chip carries a D2 meta (the item count, "• 18"). Separate composable
 * rather than an overload: `List<String>` and `List<Pair<String, String>>` erase to the same JVM
 * signature.
 *
 * @param candidates label to meta, in display order.
 */
@Composable
private fun WhichListMetaPreview(darkTheme: Boolean, candidates: List<Pair<String, String>>) {
    AppTheme(darkTheme = darkTheme) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            AiChoiceResponse(
                pending = whichListPending(
                    names = candidates.map { it.first },
                    metas = candidates.map { it.second },
                ),
                onSelect = {},
                onEditChange = {},
                onEditConfirm = {},
            )
        }
    }
}

/**
 * Builds a "which list?" [PendingChoice]: one Default Execute chip per name + a Dismiss escape.
 *
 * @param metas Optional per-name meta, positionally aligned with [names]. Null (the default) = no
 *  meta on any chip, which is the pre-D2 shape the count-free goldens above capture.
 */
private fun whichListPending(names: List<String>, metas: List<String>? = null): PendingChoice {
    val options = names.mapIndexed { index, name ->
        ChoiceOption(
            id = "candidate_$index",
            label = name,
            meta = metas?.getOrNull(index),
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
