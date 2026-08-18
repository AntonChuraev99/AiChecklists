package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
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
 * Visual proof for the Analyze screen's source picker — the block the owner reported as "огромные
 * карточки" that pushed the actual editor below the fold (2026-08-17).
 *
 * The frames answer three questions a build on a device answers slowly and one at a time:
 *  - does the picker still show all SIX materials, each with a readable label, after shrinking from
 *    six full-width cards to two rows of pills;
 *  - is the editor (the "Choose Photo" button and the selected-file card) visible WITHOUT scrolling
 *    at every supported width, text scale and locale — the whole point of the change;
 *  - is the selected material readable without relying on hue (fill inversion + the border
 *    disappearing), and does selecting one NOT change its column geometry.
 *
 * Record + inspect:
 *   ./gradlew :feature:analyze:recordRoborazziAndroidHostTest --tests "*AnalyzeSourcePickerScreenshotTest*"
 * Verify:
 *   ./gradlew :feature:analyze:verifyRoborazziAndroidHostTest --tests "*AnalyzeSourcePickerScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalyzeSourcePickerScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * `Locale.setDefault` is JVM-global and Gradle reuses one JVM per test task, so an unrestored RU
     * default would silently re-render every LATER test class in Russian.
     */
    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private val defaultLocale: Locale = Locale.getDefault()

    private fun shoot(
        qualifiers: String,
        fontScale: Float = 1f,
        dark: Boolean = false,
        locale: Locale = Locale.ENGLISH,
        content: @Composable () -> Unit,
    ) {
        // BOTH: `setQualifiers` moves the Android resource configuration Robolectric measures
        // against, while Compose Resources resolves values-ru / values-hi off the JVM default
        // locale. A qualifier-only shot renders English while claiming to be the RU frame.
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
                            // The real host is AppScaffold, which paints `background`. Without this
                            // the frame would be transparent-on-white and the pills' near-white fill
                            // would be judged against the wrong plane.
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.TopCenter,
                    ) { content() }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    // ── The reported defect and its fix, same state, same size ───────────────

    /** 412dp, a photo already picked: the editor must be on screen with no scrolling. */
    @Test
    fun analyze_photoSelected_412dp() = shoot("w412dp-h891dp") { Content(photoPicked()) }

    /** The same at 320dp — the narrowest supported phone. */
    @Test
    fun analyze_photoSelected_320dp() = shoot("w320dp-h568dp") { Content(photoPicked()) }

    /** Nothing chosen yet: the short heading is visible and no pill is filled. */
    @Test
    fun analyze_nothingSelected_320dp() = shoot("w320dp-h568dp") { Content(AnalyzeScreenState()) }

    /** Nothing chosen, dark theme — the outlined pills must not dissolve into the page. */
    @Test
    fun analyze_nothingSelected_412dp_dark() =
        shoot("w412dp-h891dp", dark = true) { Content(AnalyzeScreenState()) }

    /** The worst measured combination: narrowest width × large text × the longest locale. */
    @Test
    fun analyze_nothingSelected_320dp_ru_fontScale15() =
        shoot("w320dp-h568dp", fontScale = 1.5f, locale = Locale("ru")) {
            Content(AnalyzeScreenState())
        }

    /** Devanagari at a large scale: matras sit above AND below the baseline. */
    @Test
    fun analyze_nothingSelected_412dp_hi_fontScale15() =
        shoot("w412dp-h891dp", fontScale = 1.5f, locale = Locale("hi")) {
            Content(AnalyzeScreenState())
        }

    /** A tablet: the top rung of the ladder, six abreast. Without it a 3×2 phone frame cannot be
     *  told apart from a component that can only ever produce 3×2. */
    @Test
    fun analyze_nothingSelected_600dp() = shoot("w600dp-h800dp") { Content(AnalyzeScreenState()) }

    /** Raw text selected — the editor is a multi-line field rather than a button. */
    @Test
    fun analyze_rawTextSelected_412dp() = shoot("w412dp-h891dp") {
        Content(AnalyzeScreenState(selectedInputType = InputDataType.RAW_TEXT, inputText = "Buy milk, eggs, bread"))
    }

    /** Voice selected: the tallest editor of the six, and the one that used to be furthest below
     *  the fold. */
    @Test
    fun analyze_voiceSelected_320dp() = shoot("w320dp-h568dp") {
        Content(AnalyzeScreenState(selectedInputType = InputDataType.VOICE))
    }

    // ── The picker's own two shapes, isolated from the screen ────────────────

    /** Expanded WITH a selection — the state a user reaches by tapping the collapsed control. */
    @Test
    fun picker_expanded_photoSelected_412dp() = shoot("w412dp-h891dp") {
        PickerOnly(selected = InputDataType.PHOTO, expanded = true)
    }

    /** The same in dark: the inversion runs the other way (light capsule among dark ones). */
    @Test
    fun picker_expanded_photoSelected_412dp_dark() = shoot("w412dp-h891dp", dark = true) {
        PickerOnly(selected = InputDataType.PHOTO, expanded = true)
    }

    /**
     * Every material's collapsed control, stacked. The frame answers one question that a single
     * example cannot: does the LONGEST label still fit beside the chevron in a content-width pill.
     */
    @Test
    fun picker_collapsed_allMaterials_412dp() = shoot("w412dp-h891dp") { AllCollapsed() }

    /** The same in the longest locale at a large text scale. */
    @Test
    fun picker_collapsed_allMaterials_320dp_ru_fontScale15() =
        shoot("w320dp-h568dp", fontScale = 1.5f, locale = Locale("ru")) { AllCollapsed() }

    @Composable
    private fun PickerOnly(selected: InputDataType?, expanded: Boolean) {
        AnalyzeSourcePicker(
            selectedType = selected,
            expanded = expanded,
            onExpandedChange = {},
            onTypeSelected = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.ScreenPaddingHorizontal),
        )
    }

    @Composable
    private fun AllCollapsed() {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            modifier = Modifier.padding(AppDimens.ScreenPaddingHorizontal),
        ) {
            InputDataType.entries.forEach { PickerOnly(selected = it, expanded = false) }
        }
    }

    private fun photoPicked() = AnalyzeScreenState(
        selectedInputType = InputDataType.PHOTO,
        selectedFilePath = "/tmp/receipt-2026-08-17.jpg",
        selectedFileName = "receipt-2026-08-17.jpg",
    )

    /**
     * The real screen body, driven by a fabricated state.
     *
     * [AnalyzeContent] rather than `AnalyzeScreen`: the screen resolves its ViewModel through Koin
     * and would need the whole graph up to render a layout question. What the screen adds on top —
     * the top bar and the bottom Analyze button — is gated on `selectedInputType != null` and is
     * untouched by this change.
     */
    @Composable
    private fun Content(state: AnalyzeScreenState) {
        AnalyzeContent(screenState = state, onIntent = {})
    }
}
