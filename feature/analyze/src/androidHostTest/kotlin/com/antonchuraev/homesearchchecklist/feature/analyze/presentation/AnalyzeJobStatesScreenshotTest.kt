package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analyze_details
import aichecklists.core.designsystem.generated.resources.analyze_error_input_too_large
import aichecklists.core.designsystem.generated.resources.analyze_error_interrupted
import aichecklists.core.designsystem.generated.resources.analyze_error_invalid_url
import aichecklists.core.designsystem.generated.resources.analyze_error_network
import aichecklists.core.designsystem.generated.resources.analyze_error_nothing_found
import aichecklists.core.designsystem.generated.resources.analyze_error_server
import aichecklists.core.designsystem.generated.resources.analyze_failed_snackbar
import aichecklists.core.designsystem.generated.resources.analyze_interrupted_snackbar
import aichecklists.core.designsystem.generated.resources.analyze_ready_fill_snackbar
import aichecklists.core.designsystem.generated.resources.analyze_ready_many_snackbar
import aichecklists.core.designsystem.generated.resources.analyze_ready_open
import aichecklists.core.designsystem.generated.resources.analyze_ready_review
import aichecklists.core.designsystem.generated.resources.analyze_ready_snackbar
import aichecklists.core.designsystem.generated.resources.analyze_ready_view
import aichecklists.core.designsystem.generated.resources.analyze_too_many_jobs
import aichecklists.core.designsystem.generated.resources.analyze_try_again
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Background-analysis states of the Analyze screen (DESIGN_SPEC 2026-11-20 screenshot matrix):
 * Waiting (+ slow subtitle, notification rationale, network + Cancel), Delivering, Failed per CTA,
 * the Editing form with the "in progress" strip / net balance / link error, the 4th-job dialog and
 * the root ready-snackbars. Light is the reference; dark frames only check that nothing breaks.
 *
 * The clock is frozen (`autoAdvance = false`): the spinners are infinite and the slow-subtitle
 * timer must not be skipped forward by the test clock.
 *
 *   ./gradlew :feature:analyze:recordRoborazziAndroidHostTest --tests "*AnalyzeJobStatesScreenshotTest*"
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalyzeJobStatesScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val koinRule = NoOpLoggerKoinRule()

    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private val ru = Locale("ru")
    private val hi = Locale("hi")

    private val waiting = AnalyzeScreenState(
        selectedInputType = InputDataType.PHOTO,
        jobPhase = AnalyzeJobPhase.Waiting(waitingForNetwork = false, canCancel = false),
        isAnalyzing = true,
    )
    private val networkWait = waiting.copy(
        jobPhase = AnalyzeJobPhase.Waiting(waitingForNetwork = true, canCancel = true),
    )

    // ── Waiting ─────────────────────────────────────────────────────────────

    @Test fun a_waiting_360() = shoot(PHONE) { Screen(waiting) }
    @Test fun a_waiting_320_fs15_ru() = shoot(SMALL, fontScale = 1.5f, locale = ru) { Screen(waiting) }
    @Test fun a_waiting_320_fs20_hi() = shoot(SMALL, fontScale = 2.0f, locale = hi) { Screen(waiting) }
    @Test fun a_waiting_840() = shoot(TABLET) { Screen(waiting) }
    @Test fun a_waiting_slow_360() = shoot(PHONE) { Screen(waiting, slowAfterMs = 0L) }
    @Test fun a_waiting_rationale_320_fs15() =
        shoot(SMALL, fontScale = 1.5f) { Screen(waiting.copy(askNotificationPermission = true)) }
    @Test fun a_waiting_rationale_320_fs15_ru() =
        shoot(SMALL, fontScale = 1.5f, locale = ru) { Screen(waiting.copy(askNotificationPermission = true)) }

    @Test fun b_network_cancel_320_fs15_ru() = shoot(SMALL, fontScale = 1.5f, locale = ru) { Screen(networkWait) }
    @Test fun b_network_cancel_360_dark() = shoot(PHONE, dark = true) { Screen(networkWait) }

    @Test fun c_delivering_360() =
        shoot(PHONE) { Screen(waiting.copy(jobPhase = AnalyzeJobPhase.Delivering)) }

    // ── Failed ──────────────────────────────────────────────────────────────

    @Test fun d_failed_nothingFound_320_fs15_ru() = shoot(SMALL, fontScale = 1.5f, locale = ru) {
        Failed(stringResource(Res.string.analyze_error_nothing_found), FailedCta.ChangeInput)
    }
    @Test fun d_failed_tooLarge_320_fs15_hi() = shoot(SMALL, fontScale = 1.5f, locale = hi) {
        Failed(stringResource(Res.string.analyze_error_input_too_large), FailedCta.ChangeInput)
    }
    @Test fun d_failed_network_320_fs15_ru() = shoot(SMALL, fontScale = 1.5f, locale = ru) {
        Failed(stringResource(Res.string.analyze_error_network), FailedCta.Retry)
    }
    @Test fun d_failed_server_360_dark() = shoot(PHONE, dark = true) {
        Failed(stringResource(Res.string.analyze_error_server), FailedCta.Retry)
    }
    @Test fun d_failed_credits_longServerText_320_fs15() = shoot(SMALL, fontScale = 1.5f) {
        Failed(
            "You've used all your AI credits for now. Upgrade to Premium to get more credits every " +
                "month, or come back later when your balance refills.",
            FailedCta.Dismiss,
        )
    }
    // Free user refused for credits (402): "Get Pro" primary + "OK" under it.
    @Test fun d_failed_upgrade_360() = shoot(PHONE) { Failed(SERVER_CREDITS_TEXT, FailedCta.Upgrade) }
    @Test fun d_failed_upgrade_320_fs15_ru() = shoot(SMALL, fontScale = 1.5f, locale = ru) {
        Failed(SERVER_CREDITS_TEXT, FailedCta.Upgrade)
    }
    @Test fun d_failed_upgrade_360_dark_hi() = shoot(PHONE, dark = true, locale = hi) {
        Failed(SERVER_CREDITS_TEXT, FailedCta.Upgrade)
    }
    @Test fun d_failed_interrupted_360() = shoot(PHONE) {
        Failed(stringResource(Res.string.analyze_error_interrupted), FailedCta.ChangeInput)
    }

    // ── Editing ─────────────────────────────────────────────────────────────

    private val editing = AnalyzeScreenState(
        selectedInputType = InputDataType.PHOTO,
        aiCredits = 60,
    )

    @Test fun e_editing_oneInProgress_costNull_320_fs15_ru() = shoot(SMALL, fontScale = 1.5f, locale = ru) {
        Screen(editing.copy(otherJobsInProgress = 1, creditsReservedByRunningJobs = 20))
    }
    @Test fun e_editing_threeInProgress_costNull_320_fs15_ru() = shoot(SMALL, fontScale = 1.5f, locale = ru) {
        Screen(editing.copy(otherJobsInProgress = 3, creditsReservedByRunningJobs = 60))
    }
    @Test fun e_editing_threeInProgress_360_dark() = shoot(PHONE, dark = true) {
        Screen(editing.copy(otherJobsInProgress = 3, aiActionCost = 20, creditsReservedByRunningJobs = 20))
    }
    @Test fun e_editing_reserveBelowCost_360() = shoot(PHONE) {
        // 30 credits, 20 held by a running job → 10 left < 20: error colour + barrier.
        Screen(editing.copy(aiCredits = 30, aiActionCost = 20, creditsReservedByRunningJobs = 20, otherJobsInProgress = 1))
    }
    @Test fun e_editing_balance100000_360() = shoot(PHONE) {
        Screen(editing.copy(aiCredits = 100_000, aiActionCost = 20))
    }
    @Test fun e_editing_linkError_320_fs15_ru() = shoot(SMALL, fontScale = 1.5f, locale = ru) {
        Screen(
            editing.copy(
                selectedInputType = InputDataType.WEB_LINK,
                inputUrl = "not a link",
                aiActionCost = 20,
                inputError = stringResource(Res.string.analyze_error_invalid_url),
            ),
        )
    }
    @Test fun e_editing_tooManyJobsDialog_360() = shoot(PHONE) {
        Screen(
            editing.copy(
                otherJobsInProgress = 3,
                error = stringResource(Res.string.analyze_too_many_jobs, 3),
            ),
        )
    }

    // ── Root snackbars (AppRootSnackbar, the renderer the app root SnackbarHost uses) ──

    // Taller than SMALL only so all five snackbars fit one frame (the host shows one at a time).
    @Test fun f_snack_320_fs15_ru() = shoot(SNACK_TALL, fontScale = 1.5f, locale = ru) { Snackbars() }
    @Test fun f_snack_320_fs15_hi() = shoot(SNACK_TALL, fontScale = 1.5f, locale = hi) { Snackbars() }
    @Test fun f_snack_360_en() = shoot(PHONE) { Snackbars() }

    @Composable
    private fun Screen(state: AnalyzeScreenState, slowAfterMs: Long = SLOW_WAITING_HINT_AFTER_MS) {
        AnalyzeScreenContent(screenState = state, onIntent = {}, slowWaitingHintAfterMs = slowAfterMs)
    }

    @Composable
    private fun Failed(message: String, cta: FailedCta) {
        Screen(
            AnalyzeScreenState(
                selectedInputType = InputDataType.PHOTO,
                jobPhase = AnalyzeJobPhase.Failed(message, cta),
                // The VM mirrors the message into `error`; the screen must NOT show the old dialog too.
                error = message,
            ),
        )
    }

    @Composable
    private fun Snackbars() {
        val fontScale = LocalDensity.current.fontScale
        val longName = "🏡 Moving to the new apartment — everything for the movers, keys and utilities 📦"
        val items = listOf(
            stringResource(Res.string.analyze_ready_snackbar, truncateForSnackbar(longName)) to
                stringResource(Res.string.analyze_ready_open),
            stringResource(Res.string.analyze_ready_fill_snackbar, truncateForSnackbar("Weekly groceries")) to
                stringResource(Res.string.analyze_ready_review),
            pluralStringResource(Res.plurals.analyze_ready_many_snackbar, 3, 3) to
                stringResource(Res.string.analyze_ready_view),
            stringResource(Res.string.analyze_failed_snackbar) to stringResource(Res.string.analyze_try_again),
            stringResource(Res.string.analyze_interrupted_snackbar) to stringResource(Res.string.analyze_details),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(AppDimens.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm, androidx.compose.ui.Alignment.Bottom),
        ) {
            items.forEach { (message, action) ->
                AppRootSnackbar(
                    FakeSnackbarData(
                        AnalysisReadySnackbarVisuals(message, action, snackbarActionOnNewLine(message, fontScale)),
                    ),
                )
            }
        }
    }

    private class FakeSnackbarData(override val visuals: AnalysisReadySnackbarVisuals) : SnackbarData {
        override fun performAction() = Unit
        override fun dismiss() = Unit
    }

    private fun shoot(
        qualifiers: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
        content: @Composable () -> Unit,
    ) {
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = dark) { content() }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(SPINNER_ADVANCE_MS)
        composeTestRule.waitForIdle()
        captureScreenRoboImage()
    }

    private companion object {
        const val PHONE = "w360dp-h800dp"
        const val SMALL = "w320dp-h640dp"
        const val TABLET = "w840dp-h900dp"
        const val SNACK_TALL = "w320dp-h1000dp"

        /** The credit gate keeps the server's own message (see userFacingMessage). */
        const val SERVER_CREDITS_TEXT = "Not enough AI credits for this analysis."

        /** Lets the frozen spinners draw a visible arc (frame 0 is a dot); far below the 15 s timer. */
        const val SPINNER_ADVANCE_MS = 400L
    }
}
