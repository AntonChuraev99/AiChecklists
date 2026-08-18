package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
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
 * Visual proof that a button's label is rendered at FULL size and is never shrunk to fit.
 *
 * ## The defect these shots exist to close
 * [AppButton] pinned itself to `Modifier.height(AppDimens.ButtonHeight)` and handed its label
 * `maxLines = 1` plus `TextAutoSize.StepBased(labelLarge.fontSize / 2, …)`. Those three cannot all
 * hold: `Modifier.height` fixes min AND max so the box could not grow, `maxLines = 1` forbade a
 * second line, and the only remaining freedom was the font — with a floor at **half** the ramp,
 * about 7sp. The owner's report was exactly that, from the device: "внутри кнопок маленький текст,
 * как будто стоит autotextsize".
 *
 * Every sticky bottom CTA in the app funnels through this component — share, template preview,
 * analyze preview, paywall — so one fixed height shrank the label on all of them at once.
 *
 * ## What each shot has to show
 *  - the label is at the full `labelLarge` ramp, identical in size to the short-label button beside
 *    it — the failure mode is a label that is *smaller than its neighbour*, which is why every shot
 *    puts a short and a long label in the same frame;
 *  - a label too long for one line WRAPS to two and the button GROWS, rather than the text shrinking
 *    or an ellipsis appearing;
 *  - the icon, the spinner, the outlined variant's accent and the destructive variant all survive
 *    the change;
 *  - RU and HI at fontScale 1.5 on a 320dp window — the worst case in the app, and where the
 *    shrinking was most visible.
 *
 * NOT claimed by these PNGs: that any particular caller passes a sensible string. A label that needs
 * three lines is a copy bug and these shots will show it as an ellipsis, which is the intended
 * last-resort behaviour rather than a silently unreadable 7sp line.
 *
 * Record + inspect:
 *   ./gradlew :core:designsystem:recordRoborazziAndroidHostTest --tests "*AppButtonLabelScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppButtonLabelScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    /**
     * `Locale.setDefault` is JVM-global and Gradle reuses one JVM for the whole test task, so an
     * unrestored RU default would silently re-render every LATER test class in Russian.
     */
    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    /**
     * Real product copy, not lorem ipsum: these are the strings whose length actually ships. The
     * long one is the Templates screen's premium CTA, which swaps into the same slot as the short
     * "Create weekly" and is the widest label `AppButtonSecondary` is ever given.
     */
    private data class Copy(val short: String, val long: String, val destructive: String)

    private val english = Copy(
        short = "Save",
        long = "Unlock more with Premium",
        destructive = "Delete checklist",
    )
    private val russian = Copy(
        short = "Сохранить",
        long = "Разблокировать больше с Premium",
        destructive = "Удалить чек-лист",
    )
    private val hindi = Copy(
        short = "सहेजें",
        long = "प्रीमियम के साथ और अनलॉक करें",
        destructive = "चेकलिस्ट हटाएं",
    )

    private fun shoot(
        qualifiers: String,
        copy: Copy = english,
        dark: Boolean = false,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
    ) {
        // BOTH, and neither alone is enough — `setQualifiers` moves the Android resource
        // configuration, Compose Resources resolves off the JVM default locale. Here the strings are
        // passed in directly, but the locale still drives font fallback and text shaping (Devanagari
        // needs its own metrics), so it is set for the same reason.
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = dark) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppSurface.ground())
                            .padding(AppDimens.ScreenPaddingHorizontal),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                    ) {
                        Label("AppButton — short")
                        AppButton(
                            text = copy.short,
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Label("AppButton — long (same font size as above)")
                        AppButton(
                            text = copy.long,
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Label("AppButton — long + icon")
                        AppButton(
                            text = copy.long,
                            onClick = {},
                            icon = Icons.Default.Add,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Label("AppButton — loading (spinner survives)")
                        AppButton(
                            text = copy.long,
                            onClick = {},
                            loading = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Label("AppButtonSecondary — long")
                        AppButtonSecondary(
                            text = copy.long,
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Label("AppButtonDestructive")
                        AppButtonDestructive(
                            text = copy.destructive,
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Label("AppButton — disabled")
                        AppButton(
                            text = copy.short,
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Composable
    private fun Label(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // ── The reference frames ─────────────────────────────────────────────────

    @Test
    fun buttons_412dp_light() = shoot("w412dp-h891dp")

    @Test
    fun buttons_412dp_dark() = shoot("w412dp-h891dp", dark = true)

    // ── Type scale: where the shrinking used to happen ───────────────────────

    @Test
    fun buttons_412dp_light_fontScale13() = shoot("w412dp-h891dp", fontScale = 1.3f)

    @Test
    fun buttons_412dp_light_fontScale15() = shoot("w412dp-h891dp", fontScale = 1.5f)

    /** Narrowest supported phone at the largest text — the app's worst case for a CTA label. */
    @Test
    fun buttons_320dp_light_fontScale15() = shoot("w320dp-h568dp", fontScale = 1.5f)

    // ── Locales with the longest labels ──────────────────────────────────────

    @Test
    fun buttons_412dp_light_ru_fontScale15() =
        shoot("ru-rRU-w412dp-h891dp", copy = russian, fontScale = 1.5f, locale = Locale("ru", "RU"))

    @Test
    fun buttons_320dp_light_ru_fontScale15() =
        shoot("ru-rRU-w320dp-h568dp", copy = russian, fontScale = 1.5f, locale = Locale("ru", "RU"))

    @Test
    fun buttons_412dp_light_hi_fontScale15() =
        shoot("hi-rIN-w412dp-h891dp", copy = hindi, fontScale = 1.5f, locale = Locale("hi", "IN"))

    @Test
    fun buttons_320dp_light_hi_fontScale15() =
        shoot("hi-rIN-w320dp-h568dp", copy = hindi, fontScale = 1.5f, locale = Locale("hi", "IN"))

    @Test
    fun buttons_412dp_dark_ru_fontScale15() =
        shoot(
            "ru-rRU-w412dp-h891dp",
            copy = russian,
            dark = true,
            fontScale = 1.5f,
            locale = Locale("ru", "RU"),
        )
}
