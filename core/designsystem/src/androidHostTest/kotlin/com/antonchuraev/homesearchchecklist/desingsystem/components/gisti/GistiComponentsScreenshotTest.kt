package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM/Robolectric screenshot (golden) tests for Gisti design-system components.
 *
 * Record goldens:
 *   ./gradlew :core:designsystem:recordRoborazziAndroidHostTest
 *
 * Verify (CI):
 *   ./gradlew :core:designsystem:verifyRoborazziAndroidHostTest
 *
 * Golden PNGs land in:
 *   core/designsystem/src/androidHostTest/roborazzi/
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GistiComponentsScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // -------------------------------------------------------------------------
    // SparkleTile
    // -------------------------------------------------------------------------

    @Test
    fun sparkleTile_light() {
        composeTestRule.setContent { SparkleTilePreviewContent(darkTheme = false) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun sparkleTile_dark() {
        composeTestRule.setContent { SparkleTilePreviewContent(darkTheme = true) }
        composeTestRule.onRoot().captureRoboImage()
    }

    // -------------------------------------------------------------------------
    // AskGistiBar
    // -------------------------------------------------------------------------

    @Test
    fun askGistiBar_light() {
        composeTestRule.setContent { AskGistiBarPreviewContent(darkTheme = false) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun askGistiBar_dark() {
        composeTestRule.setContent { AskGistiBarPreviewContent(darkTheme = true) }
        composeTestRule.onRoot().captureRoboImage()
    }

    // -------------------------------------------------------------------------
    // GistiPromptChips
    // -------------------------------------------------------------------------

    @Test
    fun gistiPromptChips_light() {
        composeTestRule.setContent { GistiPromptChipsPreviewContent(darkTheme = false) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun gistiPromptChips_dark() {
        composeTestRule.setContent { GistiPromptChipsPreviewContent(darkTheme = true) }
        composeTestRule.onRoot().captureRoboImage()
    }

    // -------------------------------------------------------------------------
    // AppGradientButton
    // -------------------------------------------------------------------------

    @Test
    fun appGradientButton_light() {
        composeTestRule.setContent { AppGradientButtonPreviewContent(darkTheme = false) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun appGradientButton_dark() {
        composeTestRule.setContent { AppGradientButtonPreviewContent(darkTheme = true) }
        composeTestRule.onRoot().captureRoboImage()
    }

    // -------------------------------------------------------------------------
    // GistiAvatarTile
    // -------------------------------------------------------------------------

    @Test
    fun gistiAvatarTile_light() {
        composeTestRule.setContent { GistiAvatarTilePreviewContent(darkTheme = false) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun gistiAvatarTile_dark() {
        composeTestRule.setContent { GistiAvatarTilePreviewContent(darkTheme = true) }
        composeTestRule.onRoot().captureRoboImage()
    }

    // -------------------------------------------------------------------------
    // ChecklistListCard
    // -------------------------------------------------------------------------

    @Test
    fun checklistListCard_light() {
        composeTestRule.setContent { ChecklistListCardPreviewContent(darkTheme = false) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun checklistListCard_dark() {
        composeTestRule.setContent { ChecklistListCardPreviewContent(darkTheme = true) }
        composeTestRule.onRoot().captureRoboImage()
    }

    // -------------------------------------------------------------------------
    // TodaySummaryCard
    // -------------------------------------------------------------------------

    @Test
    fun todaySummaryCard_light() {
        composeTestRule.setContent { TodaySummaryCardPreviewContent(darkTheme = false) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun todaySummaryCard_dark() {
        composeTestRule.setContent { TodaySummaryCardPreviewContent(darkTheme = true) }
        composeTestRule.onRoot().captureRoboImage()
    }

    // -------------------------------------------------------------------------
    // CalmUpgradeHint
    // -------------------------------------------------------------------------

    @Test
    fun calmUpgradeHint_light() {
        composeTestRule.setContent { CalmUpgradeHintPreviewContent(darkTheme = false) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun calmUpgradeHint_dark() {
        composeTestRule.setContent { CalmUpgradeHintPreviewContent(darkTheme = true) }
        composeTestRule.onRoot().captureRoboImage()
    }
}

// =============================================================================
// Stateless preview content helpers — explicit darkTheme param so Robolectric
// controls the theme deterministically (not isSystemInDarkTheme()).
// =============================================================================

@Composable
private fun SparkleTilePreviewContent(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            SparkleTile()
        }
    }
}

@Composable
private fun AskGistiBarPreviewContent(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AskGistiBar(
                placeholder = "Ask Gisti to add, remind, or plan…",
                onClick = {},
                onMicClick = {},
                micContentDescription = "Voice input",
            )
            AskGistiBar(
                placeholder = "Ask Gisti to add, remind, or plan…",
                onClick = {},
                onMicClick = {},
                micContentDescription = "Voice input",
                value = "Remind me to buy milk tomorrow",
            )
        }
    }
}

@Composable
private fun GistiPromptChipsPreviewContent(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            GistiPromptChips(
                chips = listOf(
                    GistiPromptChip(emoji = "📷", label = "Photo → list", action = GistiQuickAction.PHOTO),
                    GistiPromptChip(emoji = "🔔", label = "Remind me…", action = GistiQuickAction.REMIND),
                    GistiPromptChip(emoji = "🔗", label = "Link → list", action = GistiQuickAction.LINK),
                ),
                onChipClick = {},
            )
        }
    }
}

@Composable
private fun AppGradientButtonPreviewContent(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppGradientButton(
                text = "Fill via AI",
                onClick = {},
                icon = Icons.Filled.AutoAwesome,
            )
            AppGradientButton(
                text = "Fill via AI",
                onClick = {},
                icon = Icons.Filled.AutoAwesome,
                enabled = false,
            )
        }
    }
}

@Composable
private fun GistiAvatarTilePreviewContent(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GistiAvatarTile(seed = 0L, label = "Groceries")
            GistiAvatarTile(seed = 1L, label = "Paris trip")
            GistiAvatarTile(seed = 2L, label = "Work tasks")
            GistiAvatarTile(seed = 3L, label = "Fitness")
            GistiAvatarTile(seed = 4L, label = "Reading")
            GistiAvatarTile(seed = 5L, label = "Budget")
        }
    }
}

@Composable
private fun ChecklistListCardPreviewContent(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChecklistListCard(
                name = "Groceries",
                checkedItems = 2,
                totalItems = 8,
                seed = 0L,
                editedLabel = "edited 2h ago",
            )
            ChecklistListCard(
                name = "Paris trip",
                checkedItems = 5,
                totalItems = 5,
                seed = 1L,
                editedLabel = "edited yesterday",
            )
            ChecklistListCard(
                name = "New project",
                checkedItems = 0,
                totalItems = 0,
                seed = 2L,
            )
        }
    }
}

@Composable
private fun TodaySummaryCardPreviewContent(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TodaySummaryCard(
                title = "Today",
                subtitle = "6 to do · stay on track",
                onClick = {},
            )
            TodaySummaryCard(
                title = "Today",
                subtitle = "All done! Great job 🎉",
                onClick = {},
            )
        }
    }
}

@Composable
private fun CalmUpgradeHintPreviewContent(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalmUpgradeHint(
                text = "3 of 4 free lists used",
                actionLabel = "Go Premium",
                onActionClick = {},
            )
            CalmUpgradeHint(
                text = "10 AI credits left today",
                actionLabel = "Get more",
                onActionClick = {},
            )
        }
    }
}
