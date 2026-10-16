package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentOpener
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.PendingItemAttachment
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Redesign B, step (c): the checklist detail screen, v2 Standard view — header card with progress
 * ring, grouped rows with round checkboxes, "Ask Gisti about this list" row, grouped Completed
 * section and the bottom capture plate (resting + active). Plus two regression frames: v2 Weekly and
 * the v1 (control) arm, both of which this step must leave as they were.
 *
 * Drawn through the REAL public [ChecklistDetailContent] (the same entry the store-listing frames
 * use), on fake state — no ViewModel.
 *
 *   ./gradlew :feature:home:recordRoborazziAndroidHostTest --tests "*ChecklistDetailRedesignScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h660dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChecklistDetailRedesignScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.ENGLISH)
        stopKoin()
        startKoin {
            modules(
                module {
                    single<AppLogger> { NoopLogger }
                    single { AttachmentOpener() }
                },
            )
        }
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        RuntimeEnvironment.setFontScale(1f)
        stopKoin()
    }

    @Test
    fun detail_360_light() = shot(groceries())

    @Test
    fun detail_360_dark() = shot(groceries(), dark = true)

    @Test
    @Config(qualifiers = "w320dp-h640dp-port-xxhdpi")
    fun detail_320_ru_longTitle() {
        Locale.setDefault(Locale.forLanguageTag("ru"))
        shot(
            checklist(
                name = "Еженедельные покупки продуктов на всю семью",
                rows = listOf(
                    Row("Апельсиновый сок", false),
                    Row("Оливковое масло первого холодного отжима", false, important = true),
                    Row("Яйца", true),
                    Row("Молоко", true),
                ),
            ),
        )
    }

    @Test
    fun detail_360_fontScale15() {
        RuntimeEnvironment.setFontScale(1.5f)
        shot(groceries())
    }

    @Test
    fun detail_360_hi() {
        Locale.setDefault(Locale.forLanguageTag("hi"))
        shot(
            checklist(
                name = "साप्ताहिक किराने का सामान",
                rows = listOf(Row("संतरे का रस", false), Row("जैतून का तेल", false), Row("अंडे", true)),
            ),
        )
    }

    @Test
    fun detail_360_empty() = shot(checklist(name = "Weekend trip packing", rows = emptyList()))

    @Test
    fun detail_360_empty_dark() = shot(checklist(name = "Weekend trip packing", rows = emptyList()), dark = true)

    @Test
    fun detail_360_foldersLevel() = shot(foldersRoot())

    @Test
    fun detail_360_activePlate_chipsAndAttachments() = shot(
        groceries().copy(
            itemCreateMode = true,
            pendingItemInput = "Buy coffee filters",
            itemCreateImportant = true,
            itemCreatePendingAttachments = listOf(
                PendingItemAttachment("/tmp/receipt.pdf", "receipt.pdf", "application/pdf"),
                PendingItemAttachment("/tmp/list.txt", "list.txt", "text/plain"),
            ),
        ),
    )

    @Test
    fun detail_360_activePlate_dark() = shot(
        groceries().copy(itemCreateMode = true, pendingItemInput = ""),
        dark = true,
    )

    /** Swipe-to-delete caught mid-flight: the slab fills only what the row content has vacated. */
    @Test
    fun detail_360_swipeMidFlight() {
        // Pinned clock, pointer held down: an auto-advancing clock let the gesture settle back.
        composeTestRule.mainClock.autoAdvance = false
        setContent(groceries())
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onNodeWithText("Olive oil").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(-12f * density, 0f), delayMillis = 32) }
        }
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.onRoot().captureRoboImage()
    }

    /** Edit mode entered by a real long-press, then a row lifted and dragged (pointer still down). */
    @Test
    fun detail_360_editModeMidDrag() {
        composeTestRule.mainClock.autoAdvance = false
        setContent(groceries())
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onNodeWithText("Orange juice").performTouchInput { longClick() }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onNodeWithText("Orange juice").performTouchInput {
            down(center)
            advanceEventTime(800)
            repeat(6) { moveBy(Offset(0f, 12f * density)) }
        }
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.onRoot().captureRoboImage()
    }

    /** Regression: v2 Weekly keeps today's bar title/subtitle and per-day add — no plate, no header card. */
    @Test
    fun regression_v2Weekly_untouched() = shot(morningRoutine())

    /** Regression: the v1 (control) arm — no grouped rows, no header card, no plate. */
    @Test
    fun regression_v1Arm_untouched() = shot(groceries(), v2 = false)

    // ── harness ──────────────────────────────────────────────────────────────────────────────────

    private fun shot(state: ChecklistDetailState.Content, dark: Boolean = false, v2: Boolean = true) {
        setContent(state, dark, v2)
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun setContent(state: ChecklistDetailState.Content, dark: Boolean = false, v2: Boolean = true) {
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) {
                ChecklistDetailContent(
                    state = state,
                    onIntent = {},
                    useInlineAddRow = v2,
                    onOpenChat = if (v2) ({}) else null,
                    weeklyTodayWeekday = 3,
                )
            }
        }
    }

    private data class Row(val text: String, val checked: Boolean, val important: Boolean = false, val weekday: Int? = null)

    private fun checklist(
        name: String,
        rows: List<Row>,
        viewMode: ChecklistViewMode = ChecklistViewMode.Standard,
    ): ChecklistDetailState.Content {
        val template = rows.map { ChecklistItem(text = it.text, weekday = it.weekday) }
        val fill = rows.zip(template).map { (row, t) ->
            ChecklistFillItem(
                text = row.text,
                checked = row.checked,
                weekday = row.weekday,
                priority = if (row.important) 1 else 0,
                templateItemId = t.id,
            )
        }
        return ChecklistDetailState.Content(
            checklist = Checklist(id = 1L, name = name, items = template, viewMode = viewMode),
            defaultFill = ChecklistFill(id = 1L, checklistId = 1L, name = name, items = fill, isDefault = true),
            separateCompleted = true,
        )
    }

    private fun groceries() = checklist(
        name = "Weekly groceries",
        rows = listOf(
            Row("Eggs", true),
            Row("Milk", true),
            Row("Whole-grain bread", true),
            Row("Coffee beans", true),
            Row("Orange juice", false),
            Row("Olive oil", false, important = true),
        ),
    )

    private fun morningRoutine() = checklist(
        name = "Morning routine",
        viewMode = ChecklistViewMode.Weekly,
        rows = listOf(
            Row("Stretch for 10 minutes", true, weekday = 3),
            Row("Plan the day", true, weekday = 3),
            Row("Read 20 pages", false, weekday = 3),
            Row("Walk outside", false, weekday = 4),
        ),
    )

    private fun foldersRoot(): ChecklistDetailState.Content {
        val base = checklist(
            name = "Moving house",
            rows = listOf(Row("Book the van", false), Row("Change address", true)),
        )
        val leaves = base.defaultFill!!.items
        return base.copy(
            foldersEnabled = true,
            visibleFillItemIds = leaves.map { it.id }.toSet(),
            levelNodes = listOf(
                LevelNode.Folder(FolderUiModel(id = "f1", name = "Kitchen", checked = 3, total = 7)),
                LevelNode.Leaf(leaves[0].id),
                LevelNode.Folder(FolderUiModel(id = "f2", name = "Documents", checked = 0, total = 4, hasReminder = true)),
                LevelNode.Leaf(leaves[1].id),
            ),
        )
    }

    private object NoopLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
}
