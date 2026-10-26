package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayOptions
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The system Inbox's title follows the CURRENT UI language, not the one its row was created in.
 *
 * The Inbox name is written to the database once, at creation (or arrives by sync from another
 * device). A row created under Russian kept "Входящие" in the header and the page chip after the user
 * switched the app to English. The page here carries exactly that stored name; the locale qualifier
 * decides what must be on screen.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*InboxTitleLocaleTest*"
 *   ./gradlew :feature:home:recordRoborazziAndroidHostTest --tests "*InboxTitleLocaleTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxTitleLocaleTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startKoinWithNoopCollaborators() {
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
    fun stopKoinAfterTest() {
        stopKoin()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h800dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun englishUi_inboxStoredInRussian_showsInbox() {
        showInboxStoredAsRussian()

        // Header + page chip, both localized; the project chip keeps its own name.
        assertEquals(
            2,
            composeTestRule.onAllNodesWithText("Inbox").fetchSemanticsNodes().size,
            "the header and the Inbox chip must both read the English name",
        )
        assertTrue(
            composeTestRule.onAllNodesWithText(STORED_RU_NAME).fetchSemanticsNodes().isEmpty(),
            "the stored Russian name must not reach an English UI",
        )
        composeTestRule.onNodeWithText(PROJECT_NAME).assertIsDisplayed()

        composeTestRule.onRoot().captureRoboImage("src/androidHostTest/roborazzi/InboxTitleLocale_en.png")
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun russianUi_inboxStoredInRussian_showsVhodyashchie() {
        showInboxStoredAsRussian()

        assertEquals(
            2,
            composeTestRule.onAllNodesWithText(STORED_RU_NAME).fetchSemanticsNodes().size,
            "under Russian the header and the chip read the Russian name",
        )
        composeTestRule.onNodeWithText(PROJECT_NAME).assertIsDisplayed()

        composeTestRule.onRoot().captureRoboImage("src/androidHostTest/roborazzi/InboxTitleLocale_ru.png")
    }

    private fun showInboxStoredAsRussian() {
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                InboxScreen(
                    state = InboxScreenState.Content(
                        pages = listOf(
                            InboxPage(
                                checklistId = 1L,
                                title = STORED_RU_NAME,
                                isInbox = true,
                                tasks = listOf(task("Buy bread")),
                            ),
                            InboxPage(
                                checklistId = 2L,
                                title = PROJECT_NAME,
                                isInbox = false,
                                tasks = emptyList(),
                            ),
                        ),
                        displayOptions = InboxDisplayOptions(),
                        nowMillis = NOW,
                    ),
                    contentBottomPadding = 0.dp,
                    onIntent = {},
                    snackbarHostState = SnackbarHostState(),
                    swallowRootBack = false,
                    createDockOpen = false,
                    onCreateDockDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun task(text: String) =
        InboxTask(item = ChecklistFillItem(text = text, checked = false))

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
        /** What a row created under the Russian UI holds in `checklists.name`. */
        const val STORED_RU_NAME = "Входящие"
        const val PROJECT_NAME = "Home"

        /** Pinned so section grouping does not depend on when the test runs. */
        const val NOW = 1_750_000_000_000L
    }
}
