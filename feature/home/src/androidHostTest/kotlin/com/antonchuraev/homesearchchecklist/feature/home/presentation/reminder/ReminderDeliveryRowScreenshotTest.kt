package com.antonchuraev.homesearchchecklist.feature.home.presentation.reminder

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderDeliveryRowUi
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetCallbacks
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetContent
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetState
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot matrix of the reminder sheet's "Where all reminders go" row
 * (`docs/designs/telegram-channel-banner-2026-11-21/spec.md` §8): every row state in the real sheet
 * body ([ReminderSheetContent], rendered inline — a `ModalBottomSheet` window captures unreliably
 * under Robolectric), at 411 × 1.0 EN light, the spec's RU 320 × 2.0 set, HI 320 × 1.5 dark and an
 * EN dark frame. `Null*` frames are the sheet without the row (must look exactly as before).
 *
 *   ./gradlew :feature:home:recordRoborazziAndroidHostTest --tests "*ReminderDeliveryRowScreenshotTest*"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReminderDeliveryRowScreenshotTest(
    private val name: String,
    private val qualifiers: String,
    private val fontScale: Float,
    private val dark: Boolean,
    private val fixture: Fixture,
) {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    enum class Fixture(
        val delivery: ReminderDeliveryRowUi?,
        val tab: ReminderTab = ReminderTab.ONCE,
        val expanded: Boolean = false,
        val fullScreen: Boolean = false,
    ) {
        NullOnce(null),
        NullRepeat(null, tab = ReminderTab.REPEAT),
        ConnectAndroid(ReminderDeliveryRowUi.Connect(web = false)),
        ConnectWeb(ReminderDeliveryRowUi.Connect(web = true)),
        Push(choice(ReminderChannel.Push)),
        Telegram(choice(ReminderChannel.Telegram)),
        Both(choice(ReminderChannel.Both)),
        WebOn(choice(ReminderChannel.Both, web = true)),
        WebOff(choice(ReminderChannel.Push, web = true)),
        BothExpanded(choice(ReminderChannel.Both), expanded = true),
        WebOnExpanded(choice(ReminderChannel.Telegram, web = true), expanded = true),
        WebOffExpanded(choice(ReminderChannel.Push, web = true), expanded = true),
        SavingCollapsed(choice(ReminderChannel.Push, saving = ReminderChannel.Telegram)),
        SavingExpanded(choice(ReminderChannel.Push, saving = ReminderChannel.Telegram), expanded = true),
        FailedCollapsed(choice(ReminderChannel.Push, failed = true)),
        FailedExpanded(choice(ReminderChannel.Push, failed = true), expanded = true),
        RepeatTelegram(choice(ReminderChannel.Telegram), tab = ReminderTab.REPEAT),
        RepeatSavingCollapsed(choice(ReminderChannel.Push, saving = ReminderChannel.Both), tab = ReminderTab.REPEAT),
        RepeatFailedCollapsed(choice(ReminderChannel.Push, failed = true), tab = ReminderTab.REPEAT),
        ItemFullScreenBoth(choice(ReminderChannel.Both), fullScreen = true),
    }

    @Test
    fun sheet() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        RuntimeEnvironment.setFontScale(fontScale)
        // Compose Resources resolve the language from the JVM default locale, not the qualifier.
        val lang = qualifiers.substringBefore('-').takeIf { it.length == 2 }
        val previous = java.util.Locale.getDefault()
        if (lang != null) java.util.Locale.setDefault(java.util.Locale(lang))
        try {
            composeTestRule.setContent {
                AppTheme(darkTheme = dark) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(MaterialTheme.colorScheme.surface)
                                .testTag(SHEET),
                        ) {
                            ReminderSheetContent(
                                state = ReminderSheetState(
                                    activeTab = fixture.tab,
                                    pendingRepeatConfig = PendingRepeatConfig(),
                                    showFullScreenOption = fixture.fullScreen,
                                    fullScreenEnabled = fixture.fullScreen,
                                    delivery = fixture.delivery,
                                ),
                                callbacks = NoopCallbacks,
                                initialDeliveryExpanded = fixture.expanded,
                            )
                        }
                    }
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(SHEET)
                .captureRoboImage("src/androidHostTest/roborazzi/reminder_delivery/$name.png")
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    companion object {
        private const val SHEET = "reminder_sheet_body"

        private fun choice(
            confirmed: ReminderChannel,
            saving: ReminderChannel? = null,
            failed: Boolean = false,
            web: Boolean = false,
        ) = ReminderDeliveryRowUi.Choice(confirmed = confirmed, saving = saving, failed = failed, web = web)

        private val NoopCallbacks = ReminderSheetCallbacks(
            onTabSelected = {},
            onPresetSelected = {},
            onCustomDateRequested = {},
            onRemoveReminder = {},
            onRepeatTypeSelected = {},
            onSmartPresetSelected = {},
            onRepeatIntervalChanged = {},
            onWeekDayToggled = {},
            onResetChecksToggled = {},
            onRepeatTimeChanged = { _, _ -> },
            onEndConditionClick = {},
            onEndConditionSelected = {},
            onDismissEndCondition = {},
            onSaveRepeat = {},
            onRemoveRepeat = {},
            onDismiss = {},
        )

        /** Tall windows: the capture is the sheet body only, so the height is just headroom. */
        private const val PIXEL = "w411dp-h1600dp-hdpi"
        private const val NARROW = "w320dp-h2600dp-mdpi"

        private fun row(name: String, q: String, fs: Float, dark: Boolean, f: Fixture): Array<Any> =
            arrayOf(name, q, fs, dark, f)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = buildList {
            // 1. Baseline (B1): every state × 411 × 1.0 EN light.
            for (f in Fixture.entries) add(row("${f.name}_411_light", PIXEL, 1.0f, false, f))
            // 2. RU light 320 × 2.0 (B2, B3, B7, B8, C2, C4) + B4 at 1.0.
            for (f in listOf(
                Fixture.Telegram, Fixture.ConnectAndroid, Fixture.SavingExpanded, Fixture.WebOffExpanded,
                Fixture.RepeatSavingCollapsed, Fixture.RepeatFailedCollapsed, Fixture.BothExpanded,
                Fixture.NullOnce,
            )) {
                add(row("${f.name}_ru_320_fs2.0_light", "ru-$NARROW", 2.0f, false, f))
            }
            add(row("WebOff_ru_320_fs1.0_light", "ru-$NARROW", 1.0f, false, Fixture.WebOff))
            // 3. HI dark 320 × 1.5 (B5, B6, C3).
            for (f in listOf(Fixture.ConnectWeb, Fixture.RepeatTelegram, Fixture.FailedExpanded)) {
                add(row("${f.name}_hi_320_fs1.5_dark", "hi-$NARROW", 1.5f, true, f))
            }
            // 4. EN dark 320 × 1.0 (C5) + 411 dark baseline.
            add(row("WebOnExpanded_320_dark", NARROW, 1.0f, true, Fixture.WebOnExpanded))
            add(row("BothExpanded_411_dark", PIXEL, 1.0f, true, Fixture.BothExpanded))
            add(row("ItemFullScreenBoth_411_dark", PIXEL, 1.0f, true, Fixture.ItemFullScreenBoth))
        }
    }
}
