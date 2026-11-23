package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppNavBarItem
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppNavigationBar
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.NotificationsBannerUi
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Screenshot matrix of the Inbox Telegram promo (`docs/designs/telegram-channel-banner-2026-11-21/
 * spec.md` §8): the real Inbox screen with the nav bar under it, so the fold check is honest (A2:
 * 320 × 640 × 2.0 RU — the first task must be above the fold), plus the card alone for the web body.
 *
 *   ./gradlew :feature:home:recordRoborazziAndroidHostTest --tests "*TelegramPromoBannerScreenshotTest*"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TelegramPromoBannerScreenshotTest(
    private val name: String,
    private val qualifiers: String,
    private val fontScale: Float,
    private val dark: Boolean,
    private val fixture: Fixture,
) {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    enum class Fixture {
        /** Promo on the Inbox page, above the first task. */
        Visible,

        /** Both flags set: the notifications-off banner wins, the promo is not drawn. */
        NotificationsWin,

        /** Empty Inbox: the VM never shows the promo there (rule 7) — the slot is empty. */
        HiddenEmpty,

        /** The card alone, web body (`telegram_promo_body_web`). */
        CardWeb,

        /** The card alone, Android body. */
        CardAndroid,
    }

    private val defaultLocale: Locale = Locale.getDefault()

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
    fun restore() {
        Locale.setDefault(defaultLocale)
        stopKoin()
    }

    @Test
    fun banner() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        RuntimeEnvironment.setFontScale(fontScale)
        val lang = qualifiers.substringBefore('-').takeIf { it.length == 2 }
        if (lang != null) Locale.setDefault(Locale(lang))
        val ru = lang == "ru"
        val hi = lang == "hi"
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) {
                when (fixture) {
                    Fixture.CardWeb, Fixture.CardAndroid -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = AppDimens.SpacingLg),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg),
                    ) {
                        TelegramPromoBanner(
                            isWeb = fixture == Fixture.CardWeb,
                            onOpenTelegram = {},
                            onDismiss = {},
                            modifier = Modifier
                                .adaptiveContentWidth()
                                .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                        )
                    }
                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            InboxScreen(
                                state = contentState(
                                    tasks = if (fixture == Fixture.HiddenEmpty) emptyList() else tasks(ru, hi),
                                    promo = fixture != Fixture.HiddenEmpty,
                                    notifications = fixture == Fixture.NotificationsWin,
                                ),
                                contentBottomPadding = 0.dp,
                                onIntent = {},
                                snackbarHostState = SnackbarHostState(),
                                swallowRootBack = false,
                                createDockOpen = false,
                                onCreateDockDismiss = {},
                                onPlanDayClick = null,
                            )
                        }
                        AppNavigationBar(items = NAV_ITEMS, selectedItemId = "inbox", onItemSelected = {})
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/androidHostTest/roborazzi/telegram_promo/$name.png")
    }

    private fun contentState(tasks: List<ChecklistFillItem>, promo: Boolean, notifications: Boolean) =
        InboxScreenState.Content(
            pages = listOf(
                InboxPage(checklistId = 1L, title = "Inbox", isInbox = true, tasks = tasks.map { InboxTask(item = it) }),
                InboxPage(checklistId = 2L, title = "Work", isInbox = false, tasks = emptyList()),
            ),
            notificationsBanner = if (notifications) {
                NotificationsBannerUi(reminderVariant = false, settingsRequired = false)
            } else {
                null
            },
            telegramPromoVisible = promo,
            // Pinned "today": the Inbox header prints the date, so the real clock made every golden
            // go stale at midnight.
            nowMillis = FIXED_NOW_MILLIS,
        )

    /** The longest title first, so the fold check measures the worst first row. */
    private fun tasks(ru: Boolean, hi: Boolean): List<ChecklistFillItem> = when {
        ru -> listOf("Купить подарок Анне на день рождения", "Позвонить стоматологу", "Оплатить электричество")
        hi -> listOf("अन्ना के जन्मदिन का तोहफ़ा खरीदें", "डेंटिस्ट को फ़ोन करें", "बिजली का बिल भरें")
        else -> listOf("Buy a birthday gift for Anna", "Call the dentist", "Pay the electricity bill")
    }.map { ChecklistFillItem(text = it, checked = false) }

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

    companion object {
        private val NAV_ITEMS = listOf(
            AppNavBarItem("inbox", "Inbox", Icons.Filled.Inbox, Icons.Outlined.Inbox),
            AppNavBarItem("today", "Today", Icons.Filled.WbSunny, Icons.Outlined.WbSunny),
            AppNavBarItem("calendar", "Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
        )

        /**
         * Monday, Nov 23 2026, noon in the JVM's own zone — the header formats with
         * `TimeZone.currentSystemDefault()`, so building the instant in that zone keeps the printed
         * date the same on every machine.
         */
        private val FIXED_NOW_MILLIS: Long = LocalDateTime(2026, 11, 23, 12, 0)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

        private const val PHONE = "w320dp-h640dp-xhdpi"
        private const val PIXEL = "w411dp-h891dp-xhdpi"
        private const val WEB = "w1024dp-h768dp-mdpi"

        private fun row(name: String, q: String, fs: Float, dark: Boolean, f: Fixture): Array<Any> =
            arrayOf(name, q, fs, dark, f)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = buildList {
            add(row("Visible_320_fs1.0_light", PHONE, 1.0f, false, Fixture.Visible)) // A1
            add(row("Visible_ru_320_fs2.0_light", "ru-$PHONE", 2.0f, false, Fixture.Visible)) // A2 (fold)
            add(row("Visible_ru_320_fs1.5_light", "ru-$PHONE", 1.5f, false, Fixture.Visible))
            add(row("Visible_hi_320_fs1.5_dark", "hi-$PHONE", 1.5f, true, Fixture.Visible)) // A3
            add(row("Visible_ru_320_fs1.0_light", "ru-$PHONE", 1.0f, false, Fixture.Visible))
            add(row("Visible_411_fs1.0_light", PIXEL, 1.0f, false, Fixture.Visible))
            add(row("Visible_411_fs1.0_dark", PIXEL, 1.0f, true, Fixture.Visible))
            add(row("Visible_1024_fs1.0_light", WEB, 1.0f, false, Fixture.Visible)) // A6 width
            add(row("NotificationsWin_320_fs1.0_light", PHONE, 1.0f, false, Fixture.NotificationsWin)) // A5
            add(row("HiddenEmpty_320_fs1.0_light", PHONE, 1.0f, false, Fixture.HiddenEmpty))
            // The card alone: web body (A4) and the large-font forms.
            add(row("CardWeb_ru_320_fs1.0_light", "ru-$PHONE", 1.0f, false, Fixture.CardWeb)) // A4
            add(row("CardWeb_hi_320_fs1.0_dark", "hi-$PHONE", 1.0f, true, Fixture.CardWeb))
            add(row("CardWeb_1024_fs1.0_light", WEB, 1.0f, false, Fixture.CardWeb)) // A6 web body
            add(row("CardAndroid_ru_320_fs2.0_light", "ru-$PHONE", 2.0f, false, Fixture.CardAndroid))
            add(row("CardAndroid_hi_320_fs2.0_dark", "hi-$PHONE", 2.0f, true, Fixture.CardAndroid))
        }
    }
}
