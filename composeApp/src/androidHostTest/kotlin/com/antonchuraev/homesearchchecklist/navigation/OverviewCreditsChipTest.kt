package com.antonchuraev.homesearchchecklist.navigation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.credits_display
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.CreditsBadge
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium.CreditsBadgeProvider
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium.PremiumEntryPoint
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.CreditsChipSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertEquals

/**
 * The fourth v2 tab's paywall entry point. Siblings: `V2CreditsChipTest` in `:feature:home` covers
 * Inbox / Calendar / Projects; this tab lives here because its screen does.
 *
 * Overview is the tab that hosts the account row and the app's settings, so it is where a user who
 * went LOOKING for "where do I subscribe" ends up — which makes a missing chip here the most
 * expensive of the four, not the least.
 *
 * Run:
 *   ./gradlew :composeApp:testAndroidHostTest --tests "*OverviewCreditsChipTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverviewCreditsChipTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var entryPoint: RecordingEntryPoint

    @Before
    fun startKoinWithStubs() {
        stopKoin()
        entryPoint = RecordingEntryPoint()
        startKoin {
            modules(
                module {
                    single<AnalyticsTracker> { NoopAnalyticsTracker }
                    single<AppLogger> { NoopAppLogger }
                    single<PremiumEntryPoint> { entryPoint }
                    single<CreditsBadgeProvider> {
                        StubBadgeProvider(CreditsBadge(credits = 5, isPremium = false))
                    }
                }
            )
        }
    }

    @After
    fun stopKoinAfterTest() {
        stopKoin()
    }

    @Test
    fun overviewTab_drawsTheCreditsChip_andTapsOpenThePaywallAsTheOverviewSurface() {
        var chipLabel = ""
        composeTestRule.setContent {
            chipLabel = stringResource(Res.string.credits_display, 5)
            OverviewUnderTest(creditsSource = CreditsChipSource.V2_OVERVIEW)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(chipLabel).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(chipLabel).performClick()
        composeTestRule.waitForIdle()

        // Its OWN source — the four tabs are indistinguishable in the funnel otherwise, and
        // "indistinguishable" is how the whole entry point went missing for a release.
        assertEquals(listOf(false to "v2_overview_credits_chip"), entryPoint.opened)
    }

    @Test
    fun overviewTab_withoutASource_drawsNoChip() {
        var chipLabel = ""
        composeTestRule.setContent {
            chipLabel = stringResource(Res.string.credits_display, 5)
            OverviewUnderTest(creditsSource = null)
        }
        composeTestRule.waitForIdle()

        assertEquals(
            0,
            composeTestRule.onAllNodesWithContentDescription(chipLabel).fetchSemanticsNodes().size,
        )
    }

    // ── Harness ──────────────────────────────────────────────────────────────

    @Composable
    private fun OverviewUnderTest(creditsSource: String?) {
        AppTheme(darkTheme = false) {
            OverviewScreen(
                contentBottomPadding = 0.dp,
                onNavigate = {},
                onRateApp = {},
                onLeaveFeedback = {},
                versionName = "1.19.1",
                isGoogleLinked = false,
                googleEmail = null,
                googleDisplayName = null,
                onSignInClick = {},
                onSignOutClick = {},
                creditsSource = creditsSource,
            )
        }
    }

    private class RecordingEntryPoint : PremiumEntryPoint {
        val opened = mutableListOf<Pair<Boolean, String>>()
        override fun open(isPremium: Boolean, source: String) {
            opened += isPremium to source
        }
    }

    private class StubBadgeProvider(private val current: CreditsBadge) : CreditsBadgeProvider {
        override fun badge(): Flow<CreditsBadge> = flowOf(current)
        override fun currentBadge(): CreditsBadge = current
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
}
