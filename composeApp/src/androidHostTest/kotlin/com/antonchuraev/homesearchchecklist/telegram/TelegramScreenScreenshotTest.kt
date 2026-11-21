package com.antonchuraev.homesearchchecklist.telegram

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.telegram_reminders_save_failed
import aichecklists.core.designsystem.generated.resources.telegram_retry
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
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

    enum class Fixture(
        val state: TelegramScreenState,
        val dialog: Boolean = false,
        val web: Boolean = false,
        val saveFailedSnackbar: Boolean = false,
    ) {
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

        // ── Reminders section (reminders-addendum.md §8) ──
        RemPush(linked(ReminderChannel.Push)),
        RemTelegram(linked(ReminderChannel.Telegram)),
        RemBoth(linked(ReminderChannel.Both)),

        /** Saving a value different from the server one: spinner on `telegram`, no row selected. */
        RemSaving(linked(ReminderChannel.Push, saving = ReminderChannel.Telegram)),

        /** Saving the same value as the server one (latest-wins round trip). */
        RemSavingSame(linked(ReminderChannel.Both, saving = ReminderChannel.Both)),

        /** Reverted to the confirmed value + the Long failure snackbar with "Try again". */
        RemError(linked(ReminderChannel.Push), saveFailedSnackbar = true),
        RemDisconnecting(linked(ReminderChannel.Telegram, disconnecting = true)),
        RemDialogNote(linked(ReminderChannel.Both), dialog = true),
        RemDialogNoNote(linked(ReminderChannel.Push), dialog = true),
        WebOff(linked(ReminderChannel.Push), web = true),
        WebOn(linked(ReminderChannel.Both), web = true),
        WebSaving(linked(ReminderChannel.Push, saving = ReminderChannel.Both), web = true),
        WebDialogNote(linked(ReminderChannel.Telegram), dialog = true, web = true),
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
                    val snackbarHostState = remember { SnackbarHostState() }
                    if (fixture.saveFailedSnackbar) {
                        val text = stringResource(Res.string.telegram_reminders_save_failed)
                        val action = stringResource(Res.string.telegram_retry)
                        // Indefinite: a Long snackbar's timeout would race the capture.
                        LaunchedEffect(Unit) {
                            snackbarHostState.showSnackbar(text, action, duration = SnackbarDuration.Indefinite)
                        }
                    }
                    TelegramScreenLayout(
                        state = fixture.state,
                        snackbarHostState = snackbarHostState,
                        onBack = {},
                        onAction = {},
                        showDisconnectDialog = fixture.dialog,
                        onDisconnectDialogChange = {},
                        isWeb = fixture.web,
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

        private fun linked(
            channel: ReminderChannel,
            saving: ReminderChannel? = null,
            disconnecting: Boolean = false,
        ) = TelegramScreenState.Linked(
            username = LONG_USERNAME,
            firstName = "Anton",
            linkedAtMs = LINKED_AT,
            isDisconnecting = disconnecting,
            reminderChannel = channel,
            channelSaving = saving,
        )

        private val REMINDER_FIXTURES = Fixture.entries.filter { it.name.startsWith("Rem") || it.name.startsWith("Web") }

        private const val PIXEL = "w411dp-h891dp-xxhdpi"
        private const val NARROW = "w320dp-h640dp-xxhdpi"
        private const val NARROW_TALL = "w320dp-h1400dp-xxhdpi"
        private const val WEB = "w1024dp-h768dp-mdpi"

        /** Reminder frames show the whole Linked column; hdpi keeps the tall PNG readable. */
        private const val REM_TALL = "w320dp-h2200dp-hdpi"
        private const val REM_PIXEL_TALL = "w411dp-h1100dp-hdpi"

        private fun row(name: String, q: String, fs: Float, dark: Boolean, f: Fixture): Array<Any> =
            arrayOf(name, q, fs, dark, f)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = buildList {
            val phase1 = Fixture.entries - REMINDER_FIXTURES.toSet()
            // 1. Every state × light / dark × 411dp × EN.
            for (f in phase1) {
                add(row("${f.name}_411_light", PIXEL, 1.0f, false, f))
                add(row("${f.name}_411_dark", PIXEL, 1.0f, true, f))
            }
            // 2. Every state × light × 320dp × fontScale 1.5 × RU (spec §8 set 1, incl. dialog).
            for (f in phase1) add(row("${f.name}_ru_320_fs1.5_light", "ru-$NARROW", 1.5f, false, f))
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

            // 7. Reminders section (reminders-addendum.md §8 "Minimum frames"). Frame numbers = §8 list.
            // (1) Android RU light 320 × 2.0: idle push · saving · error + snackbar · disconnecting.
            for (f in listOf(Fixture.RemPush, Fixture.RemSaving, Fixture.RemError, Fixture.RemDisconnecting)) {
                add(row("${f.name}_ru_320_fs2.0_light", "ru-$REM_TALL", 2.0f, false, f))
            }
            add(row("RemDialogNote_ru_320_fs2.0_light", "ru-$NARROW", 2.0f, false, Fixture.RemDialogNote))
            // (2) Android HI dark 320 × 2.0: idle both + dialog with note.
            add(row("RemBoth_hi_320_fs2.0_dark", "hi-$REM_TALL", 2.0f, true, Fixture.RemBoth))
            add(row("RemDialogNote_hi_320_fs2.0_dark", "hi-$NARROW", 2.0f, true, Fixture.RemDialogNote))
            // (3) Web RU light 320 × 2.0: off · saving · on (+ the web dialog note).
            for (f in listOf(Fixture.WebOff, Fixture.WebSaving, Fixture.WebOn)) {
                add(row("${f.name}_ru_320_fs2.0_light", "ru-$REM_TALL", 2.0f, false, f))
            }
            add(row("WebDialogNote_ru_320_fs2.0_light", "ru-$NARROW", 2.0f, false, Fixture.WebDialogNote))
            // (4) Web EN dark 1024 (column capped at 600): off · on.
            add(row("WebOff_1024_dark", WEB, 1.0f, true, Fixture.WebOff))
            add(row("WebOn_1024_dark", WEB, 1.0f, true, Fixture.WebOn))
            // (5) Android EN light 411 × 1.0 baseline: every value, saving (same + different), dialogs.
            for (f in listOf(
                Fixture.RemPush, Fixture.RemTelegram, Fixture.RemBoth, Fixture.RemSaving,
                Fixture.RemSavingSame, Fixture.RemDialogNote, Fixture.RemDialogNoNote,
            )) {
                add(row("${f.name}_411_light", REM_PIXEL_TALL, 1.0f, false, f))
            }
            // (6) Section hidden (null) = Linked_411_light above, unchanged golden.
            // Extra: RU 320 × 1.5 light — the spec's base matrix width / scale.
            add(row("RemTelegram_ru_320_fs1.5_light", "ru-$REM_TALL", 1.5f, false, Fixture.RemTelegram))
        }
    }
}
