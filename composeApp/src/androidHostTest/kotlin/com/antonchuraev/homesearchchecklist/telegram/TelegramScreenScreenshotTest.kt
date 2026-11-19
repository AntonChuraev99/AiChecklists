package com.antonchuraev.homesearchchecklist.telegram

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot matrix for [TelegramScreen] (`docs/designs/telegram-screen-2026-11-19/spec.md` §8):
 * every state + the Disconnect dialog, light / dark at 411dp, the spec's RU 320dp × 1.5 set,
 * fontScale 2.0, HI dark, a 32-char username, a null username and a 1024dp wide frame.
 *
 *   ./gradlew :composeApp:recordRoborazziAndroidHostTest --tests "*TelegramScreenScreenshotTest*"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TelegramScreenScreenshotTest(
    private val name: String,
    private val qualifiers: String,
    private val fontScale: Float,
    private val dark: Boolean,
    private val fixture: Fixture,
) {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    enum class Fixture(val state: TelegramScreenState, val dialog: Boolean = false) {
        Loading(TelegramScreenState.Loading),
        SignedOut(TelegramScreenState.SignedOut()),
        SignedOutSigningIn(TelegramScreenState.SignedOut(isSigningIn = true)),
        NotLinked(TelegramScreenState.NotLinked()),
        NotLinkedConnecting(TelegramScreenState.NotLinked(isConnecting = true)),
        Pending(TelegramScreenState.Pending()),
        PendingChecking(TelegramScreenState.Pending(isChecking = true)),
        Linked(TelegramScreenState.Linked("gisti_fan", "Anton", LINKED_AT)),
        LinkedLongName(TelegramScreenState.Linked(LONG_USERNAME, "Anton", LINKED_AT)),
        LinkedNoUsername(TelegramScreenState.Linked(null, null, LINKED_AT)),
        LinkedFirstNameNoDate(TelegramScreenState.Linked(null, "Anton", null)),
        LinkedDisconnecting(TelegramScreenState.Linked("gisti_fan", "Anton", LINKED_AT, isDisconnecting = true)),
        ErrorNetwork(TelegramScreenState.Error(TelegramErrorReason.Network)),
        ErrorAccount(TelegramScreenState.Error(TelegramErrorReason.Account)),
        Dialog(TelegramScreenState.Linked("gisti_fan", "Anton", LINKED_AT), dialog = true),
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun screen() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        RuntimeEnvironment.setFontScale(fontScale)
        // Compose Resources resolve the language from the JVM default locale, not the qualifier.
        val lang = qualifiers.substringBefore('-').takeIf { it.length == 2 }
        val previousLocale = java.util.Locale.getDefault()
        val previousZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        if (lang != null) java.util.Locale.setDefault(java.util.Locale(lang))
        try {
            composeTestRule.setContent {
                AppTheme(darkTheme = dark) {
                    TelegramScreenLayout(
                        state = fixture.state,
                        snackbarHostState = remember { SnackbarHostState() },
                        onBack = {},
                        onAction = {},
                        showDisconnectDialog = fixture.dialog,
                        onDisconnectDialogChange = {},
                    )
                }
            }
            composeTestRule.waitForIdle()
            captureScreenRoboImage("src/androidHostTest/roborazzi/telegram/$name.png")
        } finally {
            java.util.Locale.setDefault(previousLocale)
            java.util.TimeZone.setDefault(previousZone)
        }
    }

    companion object {
        /** 2025-11-19 12:00 UTC. */
        const val LINKED_AT = 1_763_553_600_000L

        /** 32 characters — Telegram's username maximum. */
        const val LONG_USERNAME = "anton_churaev_long_username_x32a"

        private const val PIXEL = "w411dp-h891dp-xxhdpi"
        private const val NARROW = "w320dp-h640dp-xxhdpi"
        private const val NARROW_TALL = "w320dp-h1400dp-xxhdpi"
        private const val WEB = "w1024dp-h768dp-mdpi"

        private fun row(name: String, q: String, fs: Float, dark: Boolean, f: Fixture): Array<Any> =
            arrayOf(name, q, fs, dark, f)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = buildList {
            // 1. Every state × light / dark × 411dp × EN.
            for (f in Fixture.entries) {
                add(row("${f.name}_411_light", PIXEL, 1.0f, false, f))
                add(row("${f.name}_411_dark", PIXEL, 1.0f, true, f))
            }
            // 2. Every state × light × 320dp × fontScale 1.5 × RU (spec §8 set 1, incl. dialog).
            for (f in Fixture.entries) add(row("${f.name}_ru_320_fs1.5_light", "ru-$NARROW", 1.5f, false, f))
            add(row("Dialog_ru_320_fs1.5_dark", "ru-$NARROW", 1.5f, true, Fixture.Dialog))
            // 3. Whole column of the tall states (below-the-fold content) at RU 320 × 1.5.
            add(row("Linked_ru_320_fs1.5_tall", "ru-$NARROW_TALL", 1.5f, false, Fixture.Linked))
            add(row("NotLinked_ru_320_fs1.5_tall", "ru-$NARROW_TALL", 1.5f, false, Fixture.NotLinked))
            // 4. fontScale 2.0 (EN) on the narrowest width.
            for (f in listOf(Fixture.NotLinked, Fixture.Linked, Fixture.LinkedLongName, Fixture.Pending)) {
                add(row("${f.name}_320_fs2.0_tall", NARROW_TALL, 2.0f, false, f))
            }
            // 5. HI, dark, 320 × 1.5 (Devanagari shaping + longest error).
            for (f in listOf(Fixture.Linked, Fixture.Pending, Fixture.ErrorAccount)) {
                add(row("${f.name}_hi_320_fs1.5_dark", "hi-$NARROW_TALL", 1.5f, true, f))
            }
            // 6. Wide (web Expanded): column capped at 600dp.
            add(row("NotLinked_1024_light", WEB, 1.0f, false, Fixture.NotLinked))
            add(row("Linked_1024_light", WEB, 1.0f, false, Fixture.Linked))
        }
    }
}
