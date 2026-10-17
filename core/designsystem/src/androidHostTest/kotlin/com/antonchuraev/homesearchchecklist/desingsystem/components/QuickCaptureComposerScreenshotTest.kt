package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.capture_dock_ai_entry_title
import aichecklists.core.designsystem.generated.resources.due_planner_repeat_off
import aichecklists.core.designsystem.generated.resources.due_preset_in_1_hour
import aichecklists.core.designsystem.generated.resources.due_preset_next_week
import aichecklists.core.designsystem.generated.resources.due_preset_tomorrow
import aichecklists.core.designsystem.generated.resources.due_preset_tonight
import aichecklists.core.designsystem.generated.resources.due_preset_weekend
import aichecklists.core.designsystem.generated.resources.due_rail_no_date
import aichecklists.core.designsystem.generated.resources.due_tomorrow
import aichecklists.core.designsystem.generated.resources.inbox_quick_add_placeholder
import aichecklists.core.designsystem.generated.resources.item_create_chip_important
import aichecklists.core.designsystem.generated.resources.item_create_chip_in_1_hour
import aichecklists.core.designsystem.generated.resources.item_create_chip_pick_time
import aichecklists.core.designsystem.generated.resources.item_create_chip_repeat
import aichecklists.core.designsystem.generated.resources.item_create_chip_tomorrow_morning
import aichecklists.core.designsystem.generated.resources.item_create_chip_tonight
import aichecklists.core.designsystem.generated.resources.item_create_input_placeholder
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiItemCreateAction
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiSelectableChipRow
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiSelectableChipStyle
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiItemCreatePromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The capture dock's COMPOSER CARD (redesign B pass 2, variant B) on the matrix the spec names:
 * 320dp × fontScale 1.5 in EN and RU, Hindi, light and dark, the planner expanded at the narrowest
 * width, and the detail host's soft chips with a selection and a long task.
 *
 * What each frame has to show:
 *  - field, rail and "important" inside ONE cream card with an inset hairline; the sources outside it;
 *  - the focused card wears a 2dp `primary` ring;
 *  - no label is truncated — the rail scrolls, the source grid steps down a rung, a long placeholder
 *    wraps;
 *  - the planner at 320 × 1.5 is two columns (the computed rule), never a squeezed three;
 *  - the soft chips' selection is `primaryContainer` + a check, legible in both themes.
 *
 * Record + inspect:
 *   ./gradlew :core:designsystem:recordRoborazziAndroidHostTest --tests "*QuickCaptureComposerScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickCaptureComposerScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    /** `Locale.setDefault` is JVM-global; an unrestored RU/HI default would leak into later classes. */
    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun mount(
        qualifiers: String,
        fontScale: Float = 1f,
        dark: Boolean = false,
        locale: Locale = Locale.ENGLISH,
        content: @Composable () -> Unit,
    ) {
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = dark) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.BottomCenter,
                    ) { content() }
                }
            }
        }
    }

    private fun shoot(
        qualifiers: String,
        fontScale: Float = 1f,
        dark: Boolean = false,
        locale: Locale = Locale.ENGLISH,
        content: @Composable () -> Unit,
    ) {
        mount(qualifiers, fontScale, dark, locale, content)
        composeTestRule.onRoot().captureRoboImage()
    }

    // ── Inbox / Calendar shape: rail inside the card, sources outside ────────────────────────────

    @Test
    fun composer_320dp_fontScale15_en_empty() =
        shoot("w320dp-h640dp", fontScale = 1.5f) { InboxComposer() }

    @Test
    fun composer_320dp_fontScale15_ru_dateApplied() =
        shoot("w320dp-h640dp", fontScale = 1.5f, locale = Locale("ru")) {
            InboxComposer(hasDate = true, text = "Позвонить маме завтра")
        }

    @Test
    fun composer_360dp_hi_empty() =
        shoot("w360dp-h640dp", locale = Locale("hi")) { InboxComposer() }

    @Test
    fun composer_360dp_dark_hi_dateApplied() =
        shoot("w360dp-h640dp", dark = true, locale = Locale("hi")) { InboxComposer(hasDate = true) }

    /** The narrowest window, the largest stock text step, planner open: two columns, three rows. */
    @Test
    fun composer_320dp_fontScale15_expanded() =
        shoot("w320dp-h640dp", fontScale = 1.5f) { InboxComposer(hasDate = true, expanded = true) }

    // ── Detail shape: soft chips under the text row, a selection, a long task ────────────────────

    @Test
    fun composer_360dp_light_softChipsSelected_longText() =
        shoot("w360dp-h640dp") { DetailComposer(text = LongTask) }

    @Test
    fun composer_360dp_dark_softChipsSelected() =
        shoot("w360dp-h640dp", dark = true) { DetailComposer(text = "Buy coffee filters") }

    // ── Contract: the planner's column count follows the width ───────────────────────────────────

    /**
     * At 320dp × 1.5 three cells do not fit the card (spec risk 2), so the grid is TWO columns:
     * "Weekend" (the third offer) starts a new row under "Tonight".
     */
    @Test
    fun planner_atNarrowWidthAndLargeFont_isTwoColumns() {
        var tonight = ""
        var weekend = ""
        mount("w320dp-h640dp", fontScale = 1.5f) {
            tonight = stringResource(Res.string.due_preset_tonight)
            weekend = stringResource(Res.string.due_preset_weekend)
            InboxComposer(expanded = true)
        }
        composeTestRule.waitForIdle()
        val tonightCell = gridCellBounds(tonight)
        val weekendCell = gridCellBounds(weekend)
        assertTrue(
            "at 320dp × 1.5 the third offer must wrap to a second row " +
                "(Tonight bottom=${tonightCell.bottom}, Weekend top=${weekendCell.top})",
            weekendCell.top >= tonightCell.bottom,
        )
    }

    /** …and at 360dp × 1.0 they fit, so the first three offers share one row. */
    @Test
    fun planner_atPhoneWidthAndDefaultFont_isThreeColumns() {
        var tonight = ""
        var weekend = ""
        mount("w360dp-h640dp") {
            tonight = stringResource(Res.string.due_preset_tonight)
            weekend = stringResource(Res.string.due_preset_weekend)
            InboxComposer(expanded = true)
        }
        composeTestRule.waitForIdle()
        assertTrue(
            "at 360dp × 1.0 Tonight and Weekend must share the first row",
            gridCellBounds(tonight).top == gridCellBounds(weekend).top,
        )
    }

    /** The grid cell carrying [label]; with the planner open the rail holds no offers, so it is unique. */
    private fun gridCellBounds(label: String): DpRect {
        val nodes = composeTestRule.onAllNodesWithText(label)
        nodes.assertCountEquals(1)
        return nodes[0].getUnclippedBoundsInRoot()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────────────────────

    /** The Inbox / Calendar mount: rail + planner in `attributes`, headed sources in `belowInput`. */
    @Composable
    private fun InboxComposer(hasDate: Boolean = false, expanded: Boolean = false, text: String = "") {
        val tonight = stringResource(Res.string.due_preset_tonight)
        val tomorrow = stringResource(Res.string.due_preset_tomorrow)
        val weekend = stringResource(Res.string.due_preset_weekend)
        val applied = if (hasDate) DuePresetId.TOMORROW else null
        QuickCaptureDock(
            text = text,
            onTextChange = {},
            onAdd = {},
            placeholder = stringResource(Res.string.inbox_quick_add_placeholder),
            // As both hosts pass it (owner decision O3): no focus, no keyboard while the planner is open.
            inputSuspended = expanded,
            attributes = {
                Column {
                    DueRailRow(
                        leadLabel = if (hasDate) {
                            stringResource(Res.string.due_tomorrow, "09:00")
                        } else {
                            stringResource(Res.string.due_rail_no_date)
                        },
                        hasDate = hasDate,
                        expanded = expanded,
                        presets = listOf(
                            DuePresetChip(DuePresetId.TONIGHT, tonight),
                            DuePresetChip(DuePresetId.TOMORROW, tomorrow),
                            DuePresetChip(DuePresetId.WEEKEND, weekend),
                        ).filter { it.id != applied },
                        onLeadClick = {},
                        onClearDate = {},
                        onPresetClick = {},
                        horizontalPadding = AppDimens.SpacingSm,
                    )
                    DuePlannerPanel(
                        expanded = expanded,
                        cells = listOf(
                            DuePresetCell(DuePresetId.TONIGHT, tonight, "19:00"),
                            DuePresetCell(DuePresetId.TOMORROW, tomorrow, "09:00"),
                            DuePresetCell(DuePresetId.WEEKEND, weekend, "Sat 10:00"),
                            DuePresetCell(
                                DuePresetId.IN_1_HOUR,
                                stringResource(Res.string.due_preset_in_1_hour),
                                "14:20",
                            ),
                            DuePresetCell(
                                DuePresetId.NEXT_WEEK,
                                stringResource(Res.string.due_preset_next_week),
                                "Mon 09:00",
                            ),
                        ),
                        selectedPreset = applied,
                        timeValueLabel = "09:00",
                        repeatValueLabel = stringResource(Res.string.due_planner_repeat_off),
                        onPresetClick = {},
                        onPickDateClick = {},
                        onTimeClick = {},
                        onRepeatClick = {},
                        onDoneClick = {},
                        horizontalPadding = AppDimens.SpacingSm,
                    )
                }
            },
            trailingToggle = { ImportantStarToggle(selected = hasDate, onClick = {}) },
            belowInput = if (expanded) {
                // Folded while the planner is open, as both hosts do (`CollapsibleSourceRow`).
                null
            } else {
                {
                    SourceRowSection(
                        title = stringResource(Res.string.capture_dock_ai_entry_title),
                        onSelect = {},
                        style = SourceRowSectionStyle.Dock,
                    )
                }
            },
        )
    }

    /** The detail mount: the item-create chips in their soft style, "Tonight" and Important on. */
    @Composable
    private fun DetailComposer(text: String) {
        QuickCaptureDock(
            text = text,
            onTextChange = {},
            onAdd = {},
            placeholder = stringResource(Res.string.item_create_input_placeholder),
            attributes = {
                GistiSelectableChipRow(
                    contentPadding = PaddingValues(horizontal = AppDimens.SpacingSm),
                    style = GistiSelectableChipStyle.Soft,
                    chips = gistiItemCreatePromptChips(
                        in1HourLabel = stringResource(Res.string.item_create_chip_in_1_hour),
                        tomorrowMorningLabel = stringResource(Res.string.item_create_chip_tomorrow_morning),
                        tonightLabel = stringResource(Res.string.item_create_chip_tonight),
                        pickTimeLabel = stringResource(Res.string.item_create_chip_pick_time),
                        importantLabel = stringResource(Res.string.item_create_chip_important),
                        repeatLabel = stringResource(Res.string.item_create_chip_repeat),
                        selectedReminder = GistiItemCreateAction.REMIND_1H,
                        importantSelected = true,
                        repeatSelected = false,
                    ),
                    onChipClick = {},
                )
            },
        )
    }

    private companion object {
        /** Five lines' worth at 360dp: the field must stop at four and scroll, not grow the card. */
        const val LongTask = "Call the dentist about rescheduling next week's appointment, then " +
            "pick up the prescription, buy coffee filters and drop the parcel at the post office " +
            "before it closes"
    }
}
